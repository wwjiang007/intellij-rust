/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.utils.import

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.rust.ide.search.RsWithMacrosProjectScope
import org.rust.lang.core.parser.RustParserUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.Namespace
import org.rust.lang.core.resolve.namespaces
import org.rust.lang.core.resolve2.CrateDefMap
import org.rust.lang.core.resolve2.ModData
import org.rust.lang.core.resolve2.RsModInfoBase
import org.rust.lang.core.resolve2.getModInfo

@Suppress("DataClassPrivateConstructor")
data class ImportContext private constructor(
    val project: Project,
    val mod: RsMod,
    val superMods: LinkedHashSet<RsMod>,
    val scope: GlobalSearchScope,
    val pathParsingMode: RustParserUtil.PathParsingMode,
    val attributes: RsFile.Attributes,
    val namespaceFilter: (RsQualifiedNamedElement) -> Boolean
) {
    companion object {
        fun from(project: Project, path: RsPath, isCompletion: Boolean): ImportContext = ImportContext(
            project = project,
            mod = path.containingMod,
            superMods = LinkedHashSet(path.containingMod.superMods),
            scope = RsWithMacrosProjectScope(project),
            pathParsingMode = path.pathParsingMode,
            attributes = path.stdlibAttributes,
            namespaceFilter = path.namespaceFilter(isCompletion)
        )

        fun from(project: Project, element: RsElement): ImportContext = ImportContext(
            project = project,
            mod = element.containingMod,
            superMods = LinkedHashSet(element.containingMod.superMods),
            scope = RsWithMacrosProjectScope(project),
            pathParsingMode = RustParserUtil.PathParsingMode.TYPE,
            attributes = element.stdlibAttributes,
            namespaceFilter = { true }
        )
    }
}

class ImportContext2 private constructor(
    /** Info of mod in which auto-import or completion is called */
    val rootInfo: RsModInfoBase.RsModInfo,
    /** Mod in which auto-import or completion is called */
    val rootMod: RsMod,
    val type: Type,

    val pathInfo: PathInfo?,
) {
    val project: Project get() = rootInfo.project
    val rootModData: ModData get() = rootInfo.modData
    val rootDefMap: CrateDefMap get() = rootInfo.defMap

    companion object {
        fun from(path: RsPath, type: Type = Type.AUTO_IMPORT): ImportContext2? =
            from(path, type, PathInfo.from  (path, type == Type.COMPLETION))

        fun from(context: RsElement, type: Type = Type.AUTO_IMPORT, pathInfo: PathInfo? = null): ImportContext2? {
            val rootMod = context.containingMod
            val info = getModInfo(rootMod) as? RsModInfoBase.RsModInfo ?: return null
            return ImportContext2(info, rootMod, type, pathInfo)
        }
    }

    enum class Type {
        AUTO_IMPORT,
        COMPLETION,
        OTHER,
    }

    class PathInfo(
        val parentPathText: String?,
        val pathParsingMode: RustParserUtil.PathParsingMode,
        val namespaceFilter: (RsQualifiedNamedElement) -> Boolean,
    ) {
        companion object {
            fun from(path: RsPath, isCompletion: Boolean): PathInfo = PathInfo(
                parentPathText = (path.parent as? RsPath)?.text,
                pathParsingMode = path.pathParsingMode,
                namespaceFilter = path.namespaceFilter(isCompletion),
            )
        }
    }
}

private fun RsPath.namespaceFilter(isCompletion: Boolean): (RsQualifiedNamedElement) -> Boolean = when (context) {
    is RsTypeReference -> { e ->
        when (e) {
            is RsEnumItem,
            is RsStructItem,
            is RsTraitItem,
            is RsTypeAlias,
            is RsMacroDefinitionBase -> true
            else -> false
        }
    }
    is RsPathExpr -> { e ->
        when (e) {
            is RsEnumItem -> isCompletion
            // TODO: take into account fields type
            is RsFieldsOwner,
            is RsConstant,
            is RsFunction,
            is RsTypeAlias,
            is RsMacroDefinitionBase -> true
            else -> false
        }
    }
    is RsTraitRef -> { e -> e is RsTraitItem }
    is RsStructLiteral -> { e ->
        e is RsFieldsOwner && e.blockFields != null || e is RsTypeAlias
    }
    is RsPatBinding -> { e ->
        when (e) {
            is RsEnumItem,
            is RsEnumVariant,
            is RsStructItem,
            is RsTypeAlias,
            is RsConstant,
            is RsFunction -> true
            else -> false
        }
    }
    is RsPath -> { e -> Namespace.Types in e.namespaces }
    is RsMacroCall -> { e -> Namespace.Macros in e.namespaces }
    else -> { _ -> true }
}

private val RsPath.pathParsingMode: RustParserUtil.PathParsingMode
    get() = when (parent) {
        is RsPathExpr,
        is RsStructLiteral,
        is RsPatStruct,
        is RsPatTupleStruct -> RustParserUtil.PathParsingMode.VALUE
        else -> RustParserUtil.PathParsingMode.TYPE
    }
