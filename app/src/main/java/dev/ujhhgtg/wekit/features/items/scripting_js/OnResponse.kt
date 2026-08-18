package dev.ujhhgtg.wekit.features.items.scripting_js

import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.features.core.SwitchFeature

@Feature(name = "触发器：收到响应 (JS)", categories = ["脚本 (JS)"], description = "收到响应时是否执行 onResponse()")
object OnResponse : SwitchFeature()
