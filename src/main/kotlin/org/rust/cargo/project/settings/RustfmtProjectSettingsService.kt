/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.TestOnly
import org.rust.cargo.toolchain.RustChannel

interface RustfmtProjectSettingsService {

    data class State(
        var additionalArguments: String = "",
        var channel: RustChannel = RustChannel.DEFAULT,
        var envs: Map<String, String> = emptyMap(),
        var useRustfmt: Boolean = false,
        var runRustfmtOnSave: Boolean = false
    )

    fun modify(action: (State) -> Unit)

    @TestOnly
    fun modifyTemporary(parentDisposable: Disposable, action: (State) -> Unit)

    /**
     * Returns current state of the service.
     * Note, result is a copy of service state, so you need to set modified state back to apply changes
     */
    var settingsState: State

    val additionalArguments: String
    val channel: RustChannel
    val envs: Map<String, String>
    val useRustfmt: Boolean
    val runRustfmtOnSave: Boolean
}

val Project.rustfmtSettings: RustfmtProjectSettingsService get() = service()
