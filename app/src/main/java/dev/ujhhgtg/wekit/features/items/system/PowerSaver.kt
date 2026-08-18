package dev.ujhhgtg.wekit.features.items.system

import android.os.PowerManager
import dev.ujhhgtg.reflekt.reflekt
import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.features.core.SwitchFeature

@Feature(name = "省电模式", categories = ["系统与隐私"], description = "通过一些措施, 减少微信耗电量")
object PowerSaver : SwitchFeature() {

    override fun onEnable() {
        PowerManager.WakeLock::class.reflekt().apply {
            methods {
                name = "acquire"
            }.forEach {
                it.hookBefore { result = null }
            }

            firstMethod {
                name = "release"
                parameterCount = 1
            }.hookBefore { result = null }
        }
    }
}
