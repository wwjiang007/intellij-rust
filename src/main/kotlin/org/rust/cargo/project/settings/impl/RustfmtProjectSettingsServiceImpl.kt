/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.settings.impl

import com.intellij.configurationStore.serializeObjectInto
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.util.Disposer
import com.intellij.util.xmlb.XmlSerializer.deserializeInto
import org.jdom.Element
import org.jetbrains.annotations.TestOnly
import org.rust.cargo.project.settings.RustfmtProjectSettingsService
import org.rust.cargo.project.settings.RustfmtProjectSettingsService.State
import org.rust.cargo.toolchain.RustChannel

private const val serviceName: String = "RustfmtProjectSettings"

@com.intellij.openapi.components.State(name = serviceName, storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class RustfmtProjectSettingsServiceImpl : PersistentStateComponent<Element>, RustfmtProjectSettingsService {
    @Volatile
    private var _state: State = State()

    override var settingsState: State
        get() = _state.copy()
        set(newState) {
            if (_state != newState) {
                _state = newState.copy()
            }
        }

    override val additionalArguments: String get() = _state.additionalArguments
    override val channel: RustChannel get() = _state.channel
    override val envs: Map<String, String> get() = _state.envs
    override val useRustfmt: Boolean get() = _state.useRustfmt
    override val runRustfmtOnSave: Boolean get() = _state.runRustfmtOnSave

    override fun getState(): Element {
        val element = Element(serviceName)
        serializeObjectInto(_state, element)
        return element
    }

    override fun loadState(element: Element) {
        val rawState = element.clone()
        deserializeInto(_state, rawState)
    }

    override fun modify(action: (State) -> Unit) {
        settingsState = settingsState.also(action)
    }

    @TestOnly
    override fun modifyTemporary(parentDisposable: Disposable, action: (State) -> Unit) {
        val oldState = settingsState
        settingsState = oldState.also(action)
        Disposer.register(parentDisposable) {
            _state = oldState
        }
    }
}
