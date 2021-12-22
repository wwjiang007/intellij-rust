/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project

import com.intellij.testFramework.LightPlatformTestCase
import org.intellij.lang.annotations.Language
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import org.rust.cargo.project.settings.impl.RustfmtProjectSettingsServiceImpl
import org.rust.cargo.toolchain.RustChannel
import org.rust.openapiext.elementFromXmlString
import org.rust.openapiext.toXmlString

@RunWith(JUnit38ClassRunner::class) // TODO: drop the annotation when issue with Gradle test scanning go away
class RustfmtProjectSettingsServiceTest : LightPlatformTestCase() {
    fun `test serialization`() {
        val service = RustfmtProjectSettingsServiceImpl()

        @Language("XML")
        val text = """
            <RustfmtProjectSettings>
              <option name="additionalArguments" value="--unstable-features" />
              <option name="channel" value="nightly" />
              <option name="envs">
                <map>
                  <entry key="ABC" value="123" />
                </map>
              </option>
              <option name="runRustfmtOnSave" value="true" />
              <option name="useRustfmt" value="true" />
            </RustfmtProjectSettings>
        """.trimIndent()
        service.loadState(elementFromXmlString(text))

        val actual = service.state.toXmlString()
        assertEquals(text, actual)

        assertEquals("--unstable-features", service.additionalArguments)
        assertEquals(RustChannel.NIGHTLY, service.channel)
        assertEquals(mapOf("ABC" to "123"), service.envs)
        assertEquals(true, service.useRustfmt)
        assertEquals(true, service.runRustfmtOnSave)
    }
}
