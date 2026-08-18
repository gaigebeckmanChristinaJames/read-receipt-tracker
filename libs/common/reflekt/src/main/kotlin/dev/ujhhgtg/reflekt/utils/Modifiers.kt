package dev.ujhhgtg.reflekt.utils

import java.lang.reflect.Member
import java.lang.reflect.Modifier

inline val Member.isPublic get() = Modifier.isPublic(modifiers)
inline val Member.isFinal get() = Modifier.isFinal(modifiers)
inline val Member.isPrivate get() = Modifier.isPrivate(modifiers)
inline val Member.isStatic get() = Modifier.isStatic(modifiers)
inline val Member.isAbstract get() = Modifier.isAbstract(modifiers)

object Modifiers {
    const val PUBLIC = Modifier.PUBLIC
    const val PRIVATE = Modifier.PRIVATE
    const val PROTECTED = Modifier.PROTECTED
    const val STATIC = Modifier.STATIC
    const val FINAL = Modifier.FINAL
    const val SYNCHRONIZED = Modifier.SYNCHRONIZED
    const val VOLATILE = Modifier.VOLATILE
    const val TRANSIENT = Modifier.TRANSIENT
    const val NATIVE = Modifier.NATIVE
    const val INTERFACE = Modifier.INTERFACE
    const val ABSTRACT = Modifier.ABSTRACT
    const val STRICT = Modifier.STRICT

    internal val KNOWN = intArrayOf(
        Modifier.PUBLIC, Modifier.PRIVATE, Modifier.PROTECTED,
        Modifier.STATIC, Modifier.FINAL, Modifier.SYNCHRONIZED,
        Modifier.VOLATILE, Modifier.TRANSIENT, Modifier.NATIVE,
        Modifier.INTERFACE, Modifier.ABSTRACT, Modifier.STRICT
    )
}

internal fun Int.toModifierSet(): Set<Int> {
    val set = mutableSetOf<Int>()
    for (flag in Modifiers.KNOWN) {
        if (this and flag != 0) set.add(flag)
    }
    return set
}
