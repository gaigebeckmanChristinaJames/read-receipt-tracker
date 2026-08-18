@file:Suppress("unused")

package dev.ujhhgtg.reflekt.spec

import dev.ujhhgtg.reflekt.utils.toClass
import kotlin.reflect.KClass

abstract class Spec {
    var hasRuntimeCondition: Boolean = false
        protected set

    val isCacheable: Boolean get() = !hasRuntimeCondition

    abstract fun staticCacheKeyParts(): List<Any?>
}

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "RemoveRedundantQualifierName")
fun Class<*>.typeMatches(other: Class<*>): Boolean {
    if (this == other) return true
    return when (this) {
        Boolean::class.java -> other == java.lang.Boolean::class.java
        Byte::class.java -> other == java.lang.Byte::class.java
        Char::class.java -> other == java.lang.Character::class.java
        Short::class.java -> other == java.lang.Short::class.java
        Int::class.java -> other == java.lang.Integer::class.java
        Long::class.java -> other == java.lang.Long::class.java
        Float::class.java -> other == java.lang.Float::class.java
        Double::class.java -> other == java.lang.Double::class.java
        else -> false
    } || when (other) {
        Boolean::class.java -> this == java.lang.Boolean::class.java
        Byte::class.java -> this == java.lang.Byte::class.java
        Char::class.java -> this == java.lang.Character::class.java
        Short::class.java -> this == java.lang.Short::class.java
        Int::class.java -> this == java.lang.Integer::class.java
        Long::class.java -> this == java.lang.Long::class.java
        Float::class.java -> this == java.lang.Float::class.java
        Double::class.java -> this == java.lang.Double::class.java
        else -> false
    }
}

internal fun Any?.typeInputToClass(): Class<*>? = when (this) {
    is Class<*> -> this
    is KClass<*> -> this.java
    is String -> this.toClass()
    null -> null
    else -> throw IllegalArgumentException(
        "Expected Class<*> or KClass<*> or String but got ${this.let { it::class.simpleName }}"
    )
}

internal fun Iterable<*>.typeInputToClassList(): List<Class<*>> = map {
    when (it) {
        is Class<*> -> it
        is KClass<*> -> it.java
        is String -> it.toClass()
        else -> throw IllegalArgumentException(
            "Expected Class<*> or KClass<*> or String element but got ${it?.let { it::class.simpleName }}"
        )
    }
}

internal fun Iterable<*>.typeInputToCacheKeyParameters(): List<Any?> = map {
    when {
        it is Class<*> -> it
        it is KClass<*> -> it.java
        it is String -> it.toClass()
        it === AnyType -> AnyType
        else -> throw IllegalArgumentException(
            "Expected Class<*>, KClass<*>, String, or AnyType but got ${it?.let { it::class.simpleName }}"
        )
    }
}
