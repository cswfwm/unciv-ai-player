package com.unciv.ui.screens.worldscreen.view

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.llm.LlmDecisionGranularity
import com.unciv.logic.llm.LlmSettingsHolder
import com.unciv.logic.llm.LlmTurnPlanner
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onClick
import com.unciv.ui.popups.hasOpenPopups
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread

/**
 * Collapsible panel on the right edge of the world screen that drives the LLM player proxy.
 *
 * The panel never ends the turn nor uploads to a multiplayer server: it only runs the AI for the
 * turn currently held locally, so the player keeps control of passing the turn.
 */
class LlmPanel(private val worldScreen: WorldScreen) : Table() {

    private val settings = worldScreen.game.settings.llm

    private val statusLabel = Label("", BaseScreen.skin)
    private val logLabel = Label("", BaseScreen.skin)
    private var expanded = true
    private var busy = false

    init {
        background = BaseScreen.skinStrings.getUiBackground(
            "WorldScreen/LlmPanel", tintColor = Color.CHARCOAL)
        pad(8f)
        defaults().left().padBottom(4f)
        build()
        refreshPosition()
    }

    fun refreshPosition() {
        setPosition(worldScreen.stage.width - width - 8f, 8f)
    }

    private fun build() {
        clear()

        val header = Table()
        header.add(Label("AI", BaseScreen.skin).apply { color = Color.GOLD }).padRight(8f)
        val collapse = (if (expanded) "\u25C0" else "\u25B6").toTextButton()
        collapse.onClick {
            expanded = !expanded
            build()
            refreshPosition()
        }
        header.add(collapse).right().expandX()
        add(header).growX().row()

        if (!expanded) {
            pack()
            return
        }

        statusLabel.setText(statusText())
        add(statusLabel).growX().row()

        val runButton = "AI 操作本回合".toTextButton()
        runButton.isDisabled = busy
        runButton.onClick { runAiTurn() }
        add(runButton).growX().row()

        val granularityButton = (if (settings.granularityOrDefault() == LlmDecisionGranularity.Direction)
            "粒度：定方向" else "粒度：逐项").toTextButton()
        granularityButton.onClick {
            settings.granularity =
                if (settings.granularityOrDefault() == LlmDecisionGranularity.Direction)
                    LlmDecisionGranularity.PerItem.name
                else
                    LlmDecisionGranularity.Direction.name
            build()
            refreshPosition()
        }
        add(granularityButton).growX().row()

        val revealButton = (if (settings.revealMap) "全图视野：开" else "全图视野：关").toTextButton()
        revealButton.onClick {
            LlmSettingsHolder.setRevealMap(!settings.revealMap)
            worldScreen.shouldUpdate = true
            build()
            refreshPosition()
        }
        add(revealButton).growX().row()

        logLabel.setAlignment(Align.topLeft)
        logLabel.wrap = true
        add(logLabel).growX().width(220f).row()

        pack()
    }

    /** Runs the AI for the current turn if the player enabled automatic operation. Called from
     *  WorldScreen.update, which is always on the GL thread. */
    fun maybeRunAutomaticTurn() {
        if (!settings.autoPlayEachTurn || busy || !settings.isConfigured()) return
        if (!worldScreen.isPlayersTurn || worldScreen.hasOpenPopups()) return
        runAiTurn()
    }

    private fun statusText(): String = when {        !settings.isConfigured() -> "未配置接口"
        busy -> "运行中…"
        else -> "已连接 ${settings.model}"
    }

    private fun runAiTurn() {
        if (busy || !settings.isConfigured()) return
        busy = true
        build()
        refreshPosition()
        val civ = worldScreen.selectedGameView.civView.getCiv()
        Concurrency.run("LlmTurn") {
            val outcome = LlmTurnPlanner.planTurn(civ, settings)
            launchOnGLThread {
                busy = false
                logLabel.setText(if (outcome.fallbackUsed)
                    "回退内置 AI：${outcome.error ?: ""}"
                else
                    outcome.applied.take(6).joinToString("\n"))
                build()
                refreshPosition()
                worldScreen.shouldUpdate = true
            }
        }
    }
}
