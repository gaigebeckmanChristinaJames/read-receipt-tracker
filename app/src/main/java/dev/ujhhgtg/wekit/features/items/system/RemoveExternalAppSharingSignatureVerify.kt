package dev.ujhhgtg.wekit.features.items.system

import dev.ujhhgtg.wekit.dexkit.abc.IResolveDex
import dev.ujhhgtg.wekit.dexkit.dsl.dexMethod
import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.features.core.SwitchFeature

@Feature(name = "移除分享签名校验", categories = ["系统与隐私"], description = "移除第三方应用分享到微信的签名校验")
object RemoveExternalAppSharingSignatureVerify : SwitchFeature(), IResolveDex {

    private val methodSignCheck by dexMethod {
        searchPackages("com.tencent.mm.pluginsdk.model.app")
        matcher {
            usingEqStrings("checkAppSignature get local signature failed")
        }
    }

    override fun onEnable() {
        methodSignCheck.hookBefore {
            result = true
        }
    }
}
