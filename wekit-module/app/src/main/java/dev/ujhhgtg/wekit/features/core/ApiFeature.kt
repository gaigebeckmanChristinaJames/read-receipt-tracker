package dev.ujhhgtg.wekit.features.core

import dev.ujhhgtg.wekit.utils.TargetProcesses

abstract class ApiFeature : BaseFeature() {

    override fun startup() {
        if (!TargetProcesses.isInMain) return
        enable()
    }
}
