package dev.ujhhgtg.wekit.features.items.system

import dev.ujhhgtg.wekit.dexkit.abc.IResolveDex
import dev.ujhhgtg.wekit.dexkit.dsl.dexMethod
import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.features.core.SwitchFeature

@Feature(name = "禁用「转发截图」提示", categories = ["系统与隐私"], description = "你在教我做事?")
object DisableShareScreenshotToast : SwitchFeature(), IResolveDex {

    private val methodDisplayToast by dexMethod {
        searchPackages("com.tencent.mm.ui.feature.api.screenshot")
        matcher {
            usingEqStrings("MicroMsg.ScreenShotShareService", "showShareTongue, shareTongue already showing, reset onClick & countDown")
        }
    }

    override fun onEnable() {
        methodDisplayToast.hookBefore {
            result = null
        }
    }
}
