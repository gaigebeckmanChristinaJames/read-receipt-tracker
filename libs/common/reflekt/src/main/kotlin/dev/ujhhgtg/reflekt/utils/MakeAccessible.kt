@file:Suppress("NOTHING_TO_INLINE")

package dev.ujhhgtg.reflekt.utils

import java.lang.reflect.AccessibleObject

fun <T : AccessibleObject> T.makeAccessible(): T {
    fun doAccessible() {
        runCatching {
            @Suppress("DEPRECATION")
            if (!this.isAccessible) this.isAccessible = true
        }
    }

    if (!isTrySetAccessibleSupported) {
        doAccessible()
        return this
    }

    runCatching {
        trySetAccessible()
    }.onFailure {
        isTrySetAccessibleSupported = false
        doAccessible()
    }

    return this
}

@Volatile
var isTrySetAccessibleSupported = true
