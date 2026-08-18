@file:Suppress("unused")

package dev.ujhhgtg.reflekt.spec

import dev.ujhhgtg.reflekt.utils.toModifierSet
import java.lang.reflect.Constructor
import kotlin.reflect.KClass

class ConstructorSpec : Spec() {
    var parameters: List<*>? = null
    var parameterCount: Int? = null
    var modifiers: Int? = null
    var superclass: Boolean = false

    private var parametersPredicate: ((List<Class<*>>) -> Boolean)? = null
    private var parameterCountPredicate: ((Int) -> Boolean)? = null
    private var modifiersMaskPredicate: ((Set<Int>) -> Boolean)? = null

    fun parameters(vararg types: KClass<*>) {
        parameters = types.map { it.java }
    }

    fun parameters(vararg types: Class<*>) {
        parameters = types.toList()
    }

    fun parameters(vararg elements: Any) {
        parameters = elements.toList()
    }

    fun parameters(predicate: (List<Class<*>>) -> Boolean) {
        parametersPredicate = predicate
        hasRuntimeCondition = true
    }

    fun parameterCount(count: Int) {
        parameterCount = count
    }

    fun parameterCount(predicate: (Int) -> Boolean) {
        parameterCountPredicate = predicate
        hasRuntimeCondition = true
    }

    fun modifiers(vararg flags: Int) {
        modifiers = flags.fold(0) { acc, flag -> acc or flag }
    }

    fun modifiers(predicate: (Set<Int>) -> Boolean) {
        modifiersMaskPredicate = predicate
        hasRuntimeCondition = true
    }

    fun superclass() {
        superclass = true
    }

    fun superclass(value: Boolean) {
        superclass = value
    }

    fun matches(constructor: Constructor<*>): Boolean {
        if (parameters != null) {
            val specParams = parameters!!
            val actual = constructor.parameterTypes.toList()
            if (specParams.size != actual.size) return false
            if (specParams.zip(actual).any { (spec, actualType) ->
                spec !== AnyType && !spec.typeInputToClass()!!.typeMatches(actualType)
            }) return false
        }
        if (parametersPredicate != null && !parametersPredicate!!(constructor.parameterTypes.toList())) return false
        if (parameterCount != null && constructor.parameterCount != parameterCount) return false
        if (parameterCountPredicate != null && !parameterCountPredicate!!(constructor.parameterCount)) return false
        val m = modifiers
        if (m != null && (constructor.modifiers and m) != m) return false
        if (modifiersMaskPredicate != null && !modifiersMaskPredicate!!(constructor.modifiers.toModifierSet())) return false
        return true
    }

    override fun staticCacheKeyParts(): List<Any?> =
        listOf(parameters?.typeInputToCacheKeyParameters(), parameterCount, modifiers, superclass)
}
