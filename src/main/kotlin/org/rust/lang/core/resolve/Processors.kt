/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.util.SmartList
import org.rust.lang.core.completion.RsCompletionContext
import org.rust.lang.core.completion.collectVariantsForEnumCompletion
import org.rust.lang.core.completion.createLookupElement
import org.rust.lang.core.psi.RsDebuggerExpressionCodeFragment
import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ref.MethodResolveVariant
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.BoundElementWithVisibility
import org.rust.lang.core.types.Substitution
import org.rust.lang.core.types.emptySubstitution
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.ty.stripReferences

/**
 * ScopeEntry is some PsiElement visible in some code scope.
 *
 * [ScopeEntry] handles the two case:
 *   * aliases (that's why we need a [name] property)
 *   * lazy resolving of actual elements (that's why [element] can return `null`)
 */
interface ScopeEntry {
    val name: String
    val element: RsElement?
    val subst: Substitution get() = emptySubstitution
    val isInitialized: Boolean get() = true
}

/**
 * This special event allows to transmit "out of band" information
 * to the resolve processor
 */
enum class ScopeEvent : ScopeEntry {
    /**
     * Communicate to the resolve processor that we are about to process wildcard imports.
     * This is basically a hack to make winapi 0.2 work in a reasonable amount of time.
     */
    STAR_IMPORTS;

    override val element: RsElement? get() = null
}

typealias RsProcessor<T> = (T) -> Boolean

interface RsResolveProcessorBase<in T : ScopeEntry> {
    /**
     * Return `true` to stop further processing,
     * return `false` to continue search
     */
    operator fun invoke(entry: T): Boolean

    /**
     * Indicates that processor is interested only in [ScopeEntry]s with specified [name].
     * Improves performance for Resolve2.
     * `null` in completion
     */
    val name: String?
}

typealias RsResolveProcessor = RsResolveProcessorBase<ScopeEntry>

fun createProcessor(name: String? = null, processor: (ScopeEntry) -> Boolean): RsResolveProcessor =
    createProcessorGeneric(name, processor)

fun <T : ScopeEntry> createProcessorGeneric(
    name: String? = null,
    processor: (T) -> Boolean
): RsResolveProcessorBase<T> {
    return object : RsResolveProcessorBase<T> {
        override fun invoke(entry: T): Boolean = processor(entry)
        override val name: String? = name
        override fun toString(): String = "Processor(name=$name)"
    }
}

typealias RsMethodResolveProcessor = RsResolveProcessorBase<MethodResolveVariant>

fun collectPathResolveVariants(
    path: RsPath,
    f: (RsResolveProcessor) -> Unit
): List<BoundElementWithVisibility<RsElement>> {
    val referenceName = path.referenceName ?: return emptyList()
    val result = SmartList<BoundElementWithVisibility<RsElement>>()
    val processor = createProcessor(referenceName) { e ->
        if ((e == ScopeEvent.STAR_IMPORTS) && result.isNotEmpty()) {
            return@createProcessor true
        }

        if (e.name == referenceName) {
            val element = e.element ?: return@createProcessor false
            if (element !is RsDocAndAttributeOwner || element.existsAfterExpansionSelf) {
                val boundElement = BoundElement(element, e.subst)
                val visibilityStatus = e.getVisibilityStatusFrom(path)
                if (visibilityStatus != VisibilityStatus.CfgDisabled) {
                    val isVisible = visibilityStatus == VisibilityStatus.Visible
                    result += BoundElementWithVisibility(boundElement, isVisible)
                }
            }
        }
        false
    }
    f(processor)
    return result
}

fun collectResolveVariants(referenceName: String?, f: (RsResolveProcessor) -> Unit): List<RsElement> {
    if (referenceName == null) return emptyList()
    val result = SmartList<RsElement>()
    val processor = createProcessor(referenceName) { e ->
        if (e == ScopeEvent.STAR_IMPORTS && result.isNotEmpty()) return@createProcessor true

        if (e.name == referenceName) {
            val element = e.element ?: return@createProcessor false
            if (element !is RsDocAndAttributeOwner || element.existsAfterExpansionSelf) {
                result += element
            }
        }
        false
    }
    f(processor)
    return result
}

fun <T : ScopeEntry> collectResolveVariantsAsScopeEntries(
    referenceName: String?,
    f: (RsResolveProcessorBase<T>) -> Unit
): List<T> {
    if (referenceName == null) return emptyList()
    val result = mutableListOf<T>()
    val processor = createProcessorGeneric<T>(referenceName) { e ->
        if ((e == ScopeEvent.STAR_IMPORTS) && result.isNotEmpty()) {
            return@createProcessorGeneric true
        }

        if (e.name == referenceName) {
            // de-lazying. See `RsResolveProcessor.lazy`
            val element = e.element ?: return@createProcessorGeneric false
            if (element !is RsDocAndAttributeOwner || element.existsAfterExpansionSelf) {
                result += e
            }
        }
        false
    }
    f(processor)
    return result
}

fun pickFirstResolveVariant(referenceName: String?, f: (RsResolveProcessor) -> Unit): RsElement? =
    pickFirstResolveEntry(referenceName, f)?.element

fun pickFirstResolveEntry(referenceName: String?, f: (RsResolveProcessor) -> Unit): ScopeEntry? {
    if (referenceName == null) return null
    var result: ScopeEntry? = null
    val processor = createProcessor(referenceName) { e ->
        if (e.name == referenceName) {
            val element = e.element
            if (element != null && (element !is RsDocAndAttributeOwner || element.existsAfterExpansionSelf)) {
                result = e
                return@createProcessor true
            }
        }
        false
    }
    f(processor)
    return result
}

fun collectCompletionVariants(
    result: CompletionResultSet,
    context: RsCompletionContext,
    f: (RsResolveProcessor) -> Unit
) {
    val processor = createProcessor { e ->
        val element = e.element ?: return@createProcessor false

        if (element is RsEnumItem
            && (context.expectedTy?.stripReferences() as? TyAdt)?.item == (element.declaredType as? TyAdt)?.item) {
            val variants = collectVariantsForEnumCompletion(element, context, e.subst)
            result.addAllElements(variants)
        }

        result.addElement(createLookupElement(
            scopeEntry = e,
            context = context
        ))

        false
    }
    f(processor)
}

data class SimpleScopeEntry(
    override val name: String,
    override val element: RsElement,
    override val subst: Substitution = emptySubstitution
) : ScopeEntry

data class ScopeEntryWithVisibility(
    override val name: String,
    override val element: RsElement,
    /** Given a [RsElement] (usually [RsPath]) checks if this item is visible in `containingMod` of that element */
    val visibilityFilter: (RsElement) -> VisibilityStatus,
    override val subst: Substitution = emptySubstitution,
) : ScopeEntry

fun ScopeEntry.getVisibilityStatusFrom(context: RsElement): VisibilityStatus =
    if (this is ScopeEntryWithVisibility) {
        visibilityFilter(context)
    } else {
        VisibilityStatus.Visible
    }

fun ScopeEntry.isVisibleFrom(context: RsElement): Boolean =
    getVisibilityStatusFrom(context) == VisibilityStatus.Visible

enum class VisibilityStatus {
    Visible,
    Invisible,
    CfgDisabled,
}

interface AssocItemScopeEntryBase<out T : RsAbstractable> : ScopeEntry {
    override val element: T
    val selfTy: Ty
    val source: TraitImplSource
}

data class AssocItemScopeEntry(
    override val name: String,
    override val element: RsAbstractable,
    override val subst: Substitution = emptySubstitution,
    override val selfTy: Ty,
    override val source: TraitImplSource
) : AssocItemScopeEntryBase<RsAbstractable>

private class LazyScopeEntry(
    override val name: String,
    private val thunk: Lazy<RsElement?>
) : ScopeEntry {
    override val element: RsElement? by thunk

    override val isInitialized: Boolean
        get() = thunk.isInitialized()

    override fun toString(): String = "LazyScopeEntry($name, $element)"
}


operator fun RsResolveProcessor.invoke(name: String, e: RsElement): Boolean =
    this(SimpleScopeEntry(name, e))

operator fun RsResolveProcessor.invoke(
    name: String,
    e: RsElement,
    visibilityFilter: (RsElement) -> VisibilityStatus
): Boolean = this(ScopeEntryWithVisibility(name, e, visibilityFilter))

fun RsResolveProcessor.lazy(name: String, e: () -> RsElement?): Boolean =
    this(LazyScopeEntry(name, lazy(LazyThreadSafetyMode.PUBLICATION, e)))

operator fun RsResolveProcessor.invoke(e: RsNamedElement): Boolean {
    val name = e.name ?: return false
    return this(name, e)
}

operator fun RsResolveProcessor.invoke(e: BoundElement<RsNamedElement>): Boolean {
    val name = e.element.name ?: return false
    return this(SimpleScopeEntry(name, e.element, e.subst))
}

fun processAll(elements: List<RsNamedElement>, processor: RsResolveProcessor): Boolean {
    return elements.any { processor(it) }
}

fun processAllScopeEntries(elements: List<ScopeEntry>, processor: RsResolveProcessor): Boolean {
    return elements.any { processor(it) }
}

fun processAllWithSubst(
    elements: Collection<RsNamedElement>,
    subst: Substitution,
    processor: RsResolveProcessor
): Boolean {
    for (e in elements) {
        if (processor(BoundElement(e, subst))) return true
    }
    return false
}

fun filterNotCfgDisabledItemsAndTestFunctions(processor: RsResolveProcessor): RsResolveProcessor {
    return createProcessor(processor.name) { e ->
        val element = e.element ?: return@createProcessor false
        if (element is RsFunction && element.isTest) return@createProcessor false
        if (element is RsDocAndAttributeOwner && !element.existsAfterExpansionSelf) return@createProcessor false

        processor(e)
    }
}

fun filterCompletionVariantsByVisibility(context: RsElement, processor: RsResolveProcessor): RsResolveProcessor {
    // Do not filter out private items in debugger
    if (context.containingFile is RsDebuggerExpressionCodeFragment) {
        return processor
    }
    val mod = context.containingMod
    return createProcessor(processor.name) {
        val element = it.element
        if (element is RsVisible && !element.isVisibleFrom(mod)) return@createProcessor false
        if (!it.isVisibleFrom(context)) return@createProcessor false

        val isHidden = element is RsOuterAttributeOwner && element.queryAttributes.isDocHidden &&
            element.containingMod != mod
        if (isHidden) return@createProcessor false

        processor(it)
    }
}
