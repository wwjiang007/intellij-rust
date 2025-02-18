/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.parser

import com.intellij.psi.PsiFile

/**
 * Tests parser recovery (`pin` and `recoverWhile` attributes from `rust.bnf`)
 * by constructing PSI trees from syntactically invalid files.
 */
class RsPartialParsingTestCase : RsParsingTestCaseBase("partial") {

    fun `test items`() = doTest(true)
    fun `test fn`() = doTest(true)
    fun `test fn_type`() = doTest(true)
    fun `test use item`() = doTest(true)
    fun `test shifts`() = doTest(true)
    fun `test patterns`() = doTest(true)
    fun `test struct def`() = doTest(true)
    fun `test impl body`() = doTest(true)
    fun `test trait body`() = doTest(true)
    fun `test match expr`() = doTest(true)
    fun `test struct expr fields`() = doTest(true)
    fun `test types`() = doTest(true)
    fun `test no lifetime bounds in generic args`() = doTest(true)
    fun `test require commas`() = doTest(true)
    fun `test macros`() = doTest(true)
    fun `test exprs`() = doTest(true)
    fun `test bounds`() = doTest(true)
    fun `test paths`() = doTest(true)
    fun `test const generics`() = doTest(true)
    fun `test let`() = doTest(true)
    fun `test reserved keywords`() = doTest(true)

    override fun checkResult(targetDataName: String, file: PsiFile) {
        check(hasError(file)) {
            "Invalid file was parsed successfully: ${file.name}"
        }
        super.checkResult(targetDataName, file)
    }

}
