/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.target

import com.intellij.execution.target.getRuntimeType
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.panel
import com.intellij.ui.layout.titledRow

class RsLanguageRuntimeConfigurable(val config: RsLanguageRuntimeConfiguration) :
    BoundConfigurable(config.displayName, config.getRuntimeType().helpTopic) {

    override fun createPanel(): DialogPanel = panel {
        titledRow()

        row("Rustc executable:") {
            textField(config::rustcPath)
        }
        row("Rustc version:") {
            textField(config::rustcVersion).enabled(false)
        }

        row("Cargo executable:") {
            textField(config::cargoPath)
        }
        row("Cargo version:") {
            textField(config::cargoVersion).enabled(false)
        }

        row("Additional build parameters:") {
            textField(config::localBuildArgs)
                .comment(
                    "Additional arguments to pass to <b>cargo build</b> command " +
                        "in case of <b>Build on target</b> option is disabled"
                )
        }
    }
}
