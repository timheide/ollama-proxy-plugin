package de.timheide.ollamaproxyplugin.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "de.timheide.ollamaproxyplugin.settings.OllamaSettingsState",
    storages = [Storage("OllamaSettingsPlugin.xml")]
)
class OllamaSettingsState : PersistentStateComponent<OllamaSettingsState> {
    var apiKey: String = ""
    var port: Int = 11434

    override fun getState(): OllamaSettingsState = this

    override fun loadState(state: OllamaSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        val instance: OllamaSettingsState
            get() = ApplicationManager.getApplication().getService(OllamaSettingsState::class.java)
    }
}
