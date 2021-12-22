/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.configurable

import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.components.Label
import com.intellij.ui.layout.*
import org.rust.cargo.project.settings.rustfmtSettings
import org.rust.cargo.toolchain.RustChannel
import javax.swing.JComponent

class RustfmtConfigurable(private val project: Project) : BoundConfigurable("Rustfmt") {
    private val state = project.rustfmtSettings.settingsState

    private val additionalArguments = RawCommandLineEditor()

    private val channelLabel = Label("C&hannel:")
    private val channel = ComboBox<RustChannel>().apply {
        RustChannel.values()
            .sortedBy { it.index }
            .forEach { addItem(it) }
    }

    private val environmentVariables = EnvironmentVariablesComponent()

    override fun createPanel(): DialogPanel = panel {
        blockRow {
            labeledRow("&Additional arguments:", additionalArguments) {
                additionalArguments(pushX, growX)
                    .comment("Additional arguments to pass to <b>rustfmt</b> or <b>cargo fmt</b> command")
                    .withBinding(
                        componentGet = { it.text },
                        componentSet = { component, value -> component.text = value },
                        modelBinding = state::additionalArguments.toBinding()
                    )

                channelLabel.labelFor = channel
                channelLabel()
                channel().withBinding(
                    componentGet = { it.item },
                    componentSet = { component, value -> component.item = value },
                    modelBinding = state::channel.toBinding()
                )
            }

            row(environmentVariables.label) {
                environmentVariables(growX)
                    .withBinding(
                        componentGet = { it.envs },
                        componentSet = { component, value -> component.envs = value },
                        modelBinding = state::envs.toBinding()
                    )
            }
        }

        row { checkBox("Use rustfmt instead of built-in formatter", state::useRustfmt) }
        row { checkBox("Run rustfmt on Save", state::runRustfmtOnSave) }
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        super.apply()
        project.rustfmtSettings.settingsState = state
    }

    private fun LayoutBuilder.labeledRow(labelText: String, component: JComponent, init: Row.() -> Unit) {
        val label = Label(labelText)
        label.labelFor = component
        row(label) { init() }
    }
}
