package dev.ujhhgtg.wekit.features.api.core

import android.annotation.SuppressLint
import dev.ujhhgtg.reflekt.utils.makeAccessible
import dev.ujhhgtg.wekit.features.core.ApiFeature
import dev.ujhhgtg.wekit.features.core.Feature
import java.lang.reflect.Method

@Feature(name = "Unsafe 服务", categories = ["API"], description = "提供调用 sun.misc.Unsafe 功能的能力")
object WeUnsafeApi : ApiFeature() {

    private lateinit var theUnsafe: Any
    private lateinit var mAllocateInstance: Method

    @SuppressLint("DiscouragedPrivateApi")
    override fun onEnable() {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe")
        theUnsafe = theUnsafeField.makeAccessible().get(null)!!
        mAllocateInstance = unsafeClass.getMethod(
            "allocateInstance",
            Class::class.java
        )
    }

    fun allocateInstance(clazz: Class<*>): Any? = mAllocateInstance.invoke(theUnsafe, clazz)
}
