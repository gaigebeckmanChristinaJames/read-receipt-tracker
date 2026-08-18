package dev.ujhhgtg.wekit.features.items.chat_input_bar_menu

import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Voice_chat
import dev.ujhhgtg.wekit.features.api.ui.WeChatInputBarMenuApi
import dev.ujhhgtg.wekit.features.api.ui.WeCurrentConversationApi
import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.features.core.SwitchFeature
import dev.ujhhgtg.wekit.features.items.chat.panel.selectAndSendVoice

@Feature(
    name = "发送语音文件",
    categories = ["聊天"],
    description = "在聊天输入栏长按菜单中添加「发送语音文件」功能"
)
object SendVoiceFile : SwitchFeature() {

    private val provider = WeChatInputBarMenuApi.IActionItemsProvider {
        listOf(
            WeChatInputBarMenuApi.ActionItem(
                id = "send_voice_file",
                icon = MaterialSymbols.Outlined.Voice_chat,
                label = "发送语音文件",
                onClick = { context, _ ->
                    selectAndSendVoice(context, WeCurrentConversationApi.value)
                }
            )
        )
    }

    override fun onEnable() {
        WeChatInputBarMenuApi.addProvider(provider)
    }

    override fun onDisable() {
        WeChatInputBarMenuApi.removeProvider(provider)
    }
}
