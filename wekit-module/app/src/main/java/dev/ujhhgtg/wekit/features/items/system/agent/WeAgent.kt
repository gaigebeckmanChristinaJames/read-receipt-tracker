package dev.ujhhgtg.wekit.features.items.system.agent

import android.content.Intent
import androidx.activity.ComponentActivity
import dev.ujhhgtg.wekit.activity.agent.WeAgentSettingsActivity
import dev.ujhhgtg.wekit.agent.data.WeAgentSettings
import dev.ujhhgtg.wekit.features.api.agent.WeAgentService
import dev.ujhhgtg.wekit.features.core.ClickableFeature
import dev.ujhhgtg.wekit.features.core.Feature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * User-facing WeAgent entry (§0). The toggle mounts/tears down the system-overlay floating ball
 * ([WeAgentOverlayController]); tapping the row opens the full [WeAgentSettingsActivity].
 *
 * All detailed configuration (model providers, MCP servers, tool permissions, prompts, workspaces,
 * skills, global settings) lives in that Activity — not inline here.
 */
@Feature(
    name = "WeAgent",
    categories = ["系统与隐私"],
    description = "内置 AI Agent: 悬浮窗对话、工具调用、MCP、技能。需要为微信授予悬浮窗权限。点击进入设置。",
)
object WeAgent : ClickableFeature() {

    override fun onEnable() {
        WeAgentService.init()
        MainScope().launch(Dispatchers.Main) {
            // Apply the overlay mode before mounting so the initial attach is gated.
            WeAgentOverlayController.setMode(WeAgentSettings.overlayMode())
            // Mount the overlay on the main thread (WindowManager requirement).
            WeAgentOverlayController.show()
        }
    }

    override fun onDisable() {
        MainScope().launch(Dispatchers.Main) {
            WeAgentOverlayController.hide()
        }
    }

    override fun onClick(context: ComponentActivity) {
        WeAgentService.init()
        context.startActivity(
            Intent(context, WeAgentSettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
