package dev.ujhhgtg.wekit.features.items.contacts

import android.view.View
import android.widget.TextView
import dev.ujhhgtg.reflekt.reflekt
import dev.ujhhgtg.wekit.features.api.ui.WeChatMessageViewApi
import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.features.core.SwitchFeature
import dev.ujhhgtg.wekit.utils.HookParam

@Feature(
    name = "解除群成员昵称长度限制",
    categories = ["聊天"],
    description = "移除微信为群成员昵称设置的固定最大宽度, 让昵称尽可能使用消息区域的可用空间"
)
object RemoveGroupMemberNicknameLengthLimit : SwitchFeature(),
    WeChatMessageViewApi.ICreateViewListener {

    override fun onEnable() {
        WeChatMessageViewApi.addListener(this)
    }

    override fun onDisable() {
        WeChatMessageViewApi.removeListener(this)
    }

    override fun onCreateView(param: HookParam, view: View) {
        val msgInfo = WeChatMessageViewApi.getMsgInfoFromParam(param)
        if (!msgInfo.isInGroupChat) return
        if (msgInfo.isSend != 0) return

        val textView = view.tag.reflekt()
            .firstField { name = "userTV"; superclass() }
            .get() as? TextView ?: return

        // WeChat's shared userTV style caps the label at 240dp. Reset only that cap;
        // the parent layout still limits the label to the actual available screen width.
        textView.maxWidth = Int.MAX_VALUE
    }
}
