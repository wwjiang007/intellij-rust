/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.configurable

import com.intellij.ide.DataManager
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.panel

class RustfmtConfigurable(project: Project) : RsConfigurableBase(project, "Rustfmt") {
    override fun createPanel(): DialogPanel = panel {
        row {
            checkBox("Use rustfmt instead of built-in formatter", state::useRustfmt)
                .comment("Note: rustfmt can be used only to format a whole file")
        }
        row { label("").constraints(pushY) }
        row {
            link("Configure Actions on Save...") {
                DataManager.getInstance().dataContextFromFocusAsync.onSuccess { context ->
                    val settings = Settings.KEY.getData(context)
                    settings?.select(settings.find("actions.on.save"))
                }
            }
        }
    }
}
