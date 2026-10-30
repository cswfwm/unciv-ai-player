package com.unciv.ui.popups.options

import com.unciv.logic.llm.LlmClient
import com.unciv.logic.llm.LlmSettingsHolder
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.UncivTextField
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread

/**
 * Options page for the LLM player proxy: relay endpoint, key, model, and the behaviour switches
 * that are also mirrored on the in-game panel.
 */
internal class LlmTab(
    optionsPopup: OptionsPopup
) : OptionsPopupTab(optionsPopup) {

    private val resultLabel = "".toLabel()

    override fun lateInitialize() {
        top()

        addHeader("AI player")

        addCheckbox("Enable AI panel", settings.llm::enabled, updateWorld = true) {
            LlmSettingsHolder.loadFromSettings()
        }

        addTextSetting("Relay endpoint", settings.llm.baseUrl) { settings.llm.baseUrl = it }
        addTextSetting("API key", settings.llm.apiKey) { settings.llm.apiKey = it }
        addTextSetting("Model", settings.llm.model) { settings.llm.model = it }

        addCheckbox("AI operates every turn", settings.llm::autoPlayEachTurn)

        addCheckbox("Reveal the whole map", settings.llm::revealMap, updateWorld = true) {
            LlmSettingsHolder.loadFromSettings()
        }

        addWrapped {
            add("Test connection".toTextButton().apply {
                onClick { testConnection() }
            })
        }

        add(resultLabel).colspan(2).left().row()

        super.lateInitialize()
    }

    /** Label plus text field plus an explicit save button, mirroring the custom mod prefix row. */
    private fun addTextSetting(label: String, initial: String, save: (String) -> Unit) {
        val field = UncivTextField(label, initial)
        field.maxLength = 500
        val button = "Enter".toTextButton()
        button.isEnabled = false
        field.onChange { button.isEnabled = field.text != initial }
        button.onClick {
            save(field.text.trim())
            button.isEnabled = false
        }
        addWrapped {
            add(label.toLabel())
            add(field).growX().padRight(5f)
            add(button)
        }
    }

    private fun testConnection() {
        resultLabel.setText("Testing...")
        val snapshot = settings.llm
        Concurrency.run("LlmTest") {
            val result = LlmClient.complete(snapshot, "Reply with the single word OK.", "ping")
            launchOnGLThread {
                resultLabel.setText(if (result.isSuccess) "OK" else "Failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }
}
