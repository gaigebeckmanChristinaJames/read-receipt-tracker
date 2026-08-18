package dev.ujhhgtg.wekit.ui.utils

import android.content.Context
import android.view.ContextThemeWrapper
import dev.ujhhgtg.wekit.loader.utils.ResourcesInjector
import dev.ujhhgtg.wekit.utils.reflection.ClassLoaders

class CommonContextWrapper(val base: Context) : ContextThemeWrapper(base, base.theme) {

    init {
        ResourcesInjector.injectModuleRes(resources)
    }

    override fun getClassLoader(): ClassLoader = ClassLoaders.MODULE
}
