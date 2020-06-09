/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move

import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.DummyHolder
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.contextOfType
import com.intellij.psi.util.parentOfType
import com.intellij.usageView.UsageInfo
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.ide.inspections.import.insertUseItem
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

sealed class RsMoveUsage(open val element: RsElement) : UsageInfo(element)

class RsModDeclUsage(override val element: RsModDeclItem, val file: RsFile) : RsMoveUsage(element)

class RsPathUsage(
    override val element: RsPath,
    private val rsReference: PsiReference,
    val target: RsQualifiedNamedElement
) : RsMoveUsage(element) {
    lateinit var referenceInfo: RsMoveReferenceInfo

    override fun getReference(): PsiReference = rsReference
}

class RsMoveReferenceInfo(
    // `pathOldOriginal` is real path (from user files)
    // `pathOld` is our helper path (created with `RsCodeFragmentFactory`), which is more convenient to work with
    //
    // In most cases `pathOld` equals to `pathOldOriginal`, but there are two corner cases:
    // 1) Paths with type arguments: `mod1::mod2::Struct1::<T, R>`
    //                                ^~~~~~~~~~~~~~~~~~~~~~~~~~^ pathOldOriginal
    //                                ^~~~~~~~~~~~~~~~~~^ pathOld
    // 2) Paths to nullary enum variants in bindings
    //    Unfortunately they are parsed as `RsPatIdent`:
    //        match none_or_some {
    //            None => ...
    //            ^~~^ pathOldOriginal, pathOld
    //
    // mutable because it can be inside moved elements (if reference is outside), so after move we have to change it
    var pathOld: RsPath,
    var pathOldOriginal: RsElement,  // `RsPath` or `RsPatIdent`
    // null means no accessible path found
    val pathNewAccessible: RsPath?,
    // fallback path to use when `pathNew == null` (if user choose "Continue" in conflicts view)
    val pathNewFallback: RsPath?,
    // == `pathOld.reference.resolve()`
    // mutable because it can be inside moved elements, so after move we have to change it
    var target: RsQualifiedNamedElement,
    val forceReplaceDirectly: Boolean = false
) {
    val pathNew: RsPath? get() = pathNewAccessible ?: pathNewFallback
    val isInsideUseDirective: Boolean get() = pathOldOriginal.parentOfType<RsUseItem>() != null
}

fun String.toRsPath(psiFactory: RsPsiFactory): RsPath? =
    psiFactory.tryCreatePath(this)

// todo изменить `....toRsPath(codeFragmentFactory, path)`
//            на `....toRsPath(codeFragmentFactory, path.context)`
fun String.toRsPath(codeFragmentFactory: RsCodeFragmentFactory, context: RsElement): RsPath? =
    codeFragmentFactory.createPath(this, context)

fun String.toRsPathInEmptyTmpMod(codeFragmentFactory: RsCodeFragmentFactory, context: RsMod): RsPath? =
    codeFragmentFactory.createPathInEmptyTmpMod(this, context)

fun RsPath.isAbsolute(): Boolean {
    if (text.startsWith("::")) return true
    if (startsWithSuper()) return false

    check(containingFile !is DummyHolder)
    val basePathTarget = basePath().reference?.resolve() as? RsMod ?: return false
    return basePathTarget.isCrateRoot
}

fun RsPath.startsWithSuper(): Boolean = basePath().text == "super"

fun RsPath.startsWithSelf(): Boolean {
    val basePathText = basePath().text
    return basePathText == "self" || basePathText == "Self"
}

// Path is simple if target of all subpaths is `RsMod`
// (target of whole path could be `RsMod` or `RsItem`)
// These paths are simple:
// * `mod1::mod2::Struct1`
// * `mod1::mod2::func1`
// * `mod1::mod2::mod3` (when `parent` is not `RsPath`)
// These are not:
// * `Struct1::func1`
// * `Vec::<i32>::new()`
// * `Self::Item1`
fun isSimplePath(path: RsPath): Boolean {
    // todo don't ignore `self::`, only `Self::` ?
    if (path.startsWithSelf()) return false
    val target = path.reference?.resolve() ?: return false
    if (target is RsMod && path.parent is RsPath) return false

    val subpaths = generateSequence(path.path) { it.path }
    return subpaths.all { it.reference?.resolve() is RsMod }
}

// Creates `pathOld` from `pathOldOriginal`
// See comment for `RsMoveReferenceInfo#pathOld` for details
fun convertFromPathOriginal(pathOriginal: RsElement, codeFragmentFactory: RsCodeFragmentFactory): RsPath =
    when (pathOriginal) {
        is RsPath -> pathOriginal.removeTypeArguments(codeFragmentFactory)
        is RsPatIdent -> {
            val context = pathOriginal.context as? RsElement ?: pathOriginal
            codeFragmentFactory.createPath(pathOriginal.text, context)!!
        }
        else -> error("unexpected pathOriginal: $pathOriginal, text=${pathOriginal.text}")
    }

// Converts `mod1::mod2::Struct1::<T>` to `mod1::mod2::Struct1`
// Because it is much nicer to work with path when it does not have type arguments
// Original path will be stored as `pathOldOriginal` in `RsMoveReferenceInfo`
// And we will convert path back to path with type arguments in `RsMoveCommonProcessor.updateMovedItemVisibility`
fun RsPath.removeTypeArguments(codeFragmentFactory: RsCodeFragmentFactory): RsPath {
    if (typeArgumentList == null) return this

    val pathCopy = copy() as RsPath
    pathCopy.typeArgumentList?.delete()

    val context = context as? RsElement ?: this
    return codeFragmentFactory.createPath(pathCopy.text, context) ?: /* todo log error */ this
}

fun RsPath?.resolvesToAndAccessible(target: RsQualifiedNamedElement): Boolean {
    if (this == null) return false
    if (isInsideMetaItem(target)) return false
    check(containingFile !is DummyHolder)
    check(target.containingFile !is DummyHolder)
    val reference = reference ?: return false
    if (!reference.isReferenceTo(target)) return false

    for (subpath in generateSequence(this) { it.path }) {
        val subpathTarget = subpath.reference?.resolve() as? RsVisible ?: continue
        if (!subpathTarget.isVisibleFrom(containingMod)) return false
    }
    return true
}

// target == `this.reference.resolve()`
// (but path can be dangling, so we have to pass it as argument)
fun RsPath.isInsideMetaItem(target: RsQualifiedNamedElement): Boolean {
    // this is basically a hack for paths in #[derive]
    // because proper implementation is complicated: https://github.com/intellij-rust/intellij-rust/issues/5446
    // it doesn't fail when deriving something from prelude, e.g. Display or Debug
    // and it adds import for derived trait in other cases (like serde Serialize)
    return contextOfType<RsMetaItem>() != null && target.containingCargoPackage?.origin != PackageOrigin.STDLIB
}

fun RsElement.isInsideMovedElements(elementsToMove: List<ElementToMove>): Boolean {
    // todo just log error
    check(containingFile !is RsCodeFragment)
    return elementsToMove.any {
        when (it) {
            is ItemToMove -> PsiTreeUtil.isAncestor(it.item, this, false)
            is ModToMove -> containingMod.superMods.contains(it.mod)
        }
    }
}

inline fun <reified T : RsElement> movedElementsShallowDescendantsOfType(elementsToMove: List<ElementToMove>): List<T> =
    movedElementsShallowDescendantsOfType(elementsToMove, T::class.java)

fun <T : RsElement> movedElementsShallowDescendantsOfType(elementsToMove: List<ElementToMove>, aClass: Class<T>): List<T> {
    return elementsToMove.flatMap { elementToMove ->
        val element = when (elementToMove) {
            is ItemToMove -> elementToMove.item
            is ModToMove -> {
                if (elementToMove.mod is RsFile) return@flatMap emptyList<T>()
                elementToMove.mod
            }
        }
        PsiTreeUtil.findChildrenOfAnyType(element, false, aClass)
    }
}

inline fun <reified T : RsElement> movedElementsDeepDescendantsOfType(elementsToMove: List<ElementToMove>): Sequence<T> =
    movedElementsDeepDescendantsOfType(elementsToMove, T::class.java)

fun <T : RsElement> movedElementsDeepDescendantsOfType(elementsToMove: List<ElementToMove>, aClass: Class<T>): Sequence<T> {
    return elementsToMove.asSequence()
        .flatMap { elementToMove ->
            when (elementToMove) {
                is ItemToMove -> PsiTreeUtil.findChildrenOfAnyType(elementToMove.item, false, aClass).asSequence()
                is ModToMove -> {
                    val mod = elementToMove.mod
                    val childModules = mod.childModules
                        .filter { it.containingFile != mod.containingFile }
                        .map { ModToMove(it) }
                    val childModulesDescendants = movedElementsDeepDescendantsOfType(childModules, aClass)
                    val selfDescendants = PsiTreeUtil.findChildrenOfAnyType(mod, false, aClass).asSequence()
                    selfDescendants + childModulesDescendants
                }
            }
        }
}

// updates `pub(in path)` visibility modifier
// `path` must be a parent module of the item whose visibility is being declared,
// so we replace `path` with common parent module of `newParent` and old `path`
fun RsVisRestriction.updateScopeIfNecessary(psiFactory: RsPsiFactory, newParent: RsMod) {
    if (crateRoot == newParent.crateRoot) {
        // todo pass `oldScope` as parameter?
        val oldScope = path.reference?.resolve() as? RsMod ?: return
        val newScope = commonParentMod(oldScope, newParent) ?: return
        val newScopePath = newScope.crateRelativePath ?: return
        val newVisRestriction = psiFactory.createVisRestriction("crate$newScopePath")
        replace(newVisRestriction)
    } else {
        // RsVisibility has text `pub(in path)`
        // after removing RsVisRestriction it will be changed to `pub`
        // todo add test
        delete()
    }
}

// todo добавить тест когда context.containingMod == targetMod
fun addImport(psiFactory: RsPsiFactory, context: RsElement, usePath: String) {
    if (!usePath.contains("::")) return
    val blockScope = context.ancestors.find { it is RsBlock && it.childOfType<RsUseItem>() != null } as RsBlock?
    val scope = blockScope ?: context.containingMod
    scope.insertUseItem(psiFactory, usePath)
}

fun RsAbstractable.getTrait(): RsTraitItem? {
    return when (val owner = owner) {
        is RsAbstractableOwner.Trait -> owner.trait
        is RsAbstractableOwner.Impl -> owner.impl.traitRef?.resolveToTrait()
        else -> null
    }
}
