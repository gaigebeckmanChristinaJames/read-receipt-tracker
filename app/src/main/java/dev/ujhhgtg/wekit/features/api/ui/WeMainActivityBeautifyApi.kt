package dev.ujhhgtg.wekit.features.api.ui

import dev.ujhhgtg.wekit.dexkit.abc.IResolveDex
import dev.ujhhgtg.wekit.dexkit.dsl.dexMethod
import dev.ujhhgtg.wekit.features.core.ApiFeature
import dev.ujhhgtg.wekit.features.core.Feature

@Feature(name = "微信主屏幕美化服务", categories = ["API"], description = "提供美化微信主屏幕的能力")
object WeMainActivityBeautifyApi : ApiFeature(), IResolveDex {

    val methodDoOnCreate by dexMethod {
        matcher {
            declaredClass = "com.tencent.mm.ui.MainTabUI"
            usingEqStrings("MicroMsg.LauncherUI.MainTabUI", "doOnCreate")
        }
    }
}
