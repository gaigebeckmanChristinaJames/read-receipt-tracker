@file:Suppress("unused")

package dev.ujhhgtg.reflekt.spec

import dev.ujhhgtg.reflekt.utils.toModifierSet
import java.lang.reflect.Method
import kotlin.reflect.KClass

class MethodSpec : Spec() {

    var name: String? = null
    var parameters: List<*>? = null
    var parameterCount: Int? = null
    var returnType: Any? = null
    var modifiers: Int? = null
    var superclass: Boolean = false

    private var namePredicate: ((String) -> Boolean)? = null
    private var parametersPredicate: ((List<Class<*>>) -> Boolean)? = null
    private var parameterCountPredicate: ((Int) -> Boolean)? = null
    private var returnTypePredicate: ((Class<*>) -> Boolean)? = null
    private var modifiersMaskPredicate: ((Set<Int>) -> Boolean)? = null

    fun name(value: String) {
        name = value
    }

    fun name(predicate: (String) -> Boolean) {
        namePredicate = predicate
        hasRuntimeCondition = true
    }

    fun parameters() {
        parameters = emptyList<Class<*>>()
    }

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

    fun returnType(type: KClass<*>) {
        returnType = type.java
    }

    fun returnType(type: Class<*>) {
        returnType = type
    }

    fun returnType(predicate: (Class<*>) -> Boolean) {
        returnTypePredicate = predicate
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

    private fun safe(block: () -> Boolean): Boolean =
        runCatching(block).getOrElse { false }

    fun matches(method: Method): Boolean {

        if (name != null && method.name != name) return false

        if (namePredicate != null && !safe { namePredicate!!(method.name) }) return false

        if (parameters != null) {
            val specParams = parameters!!
            val actual = method.parameterTypes.toList()

            if (specParams.size != actual.size) return false

            if (specParams.zip(actual).any { (spec, actualType) ->
                    spec !== AnyType &&
                            !safe {
                                spec.typeInputToClass()!!
                                    .typeMatches(actualType)
                            }
                }
            ) return false
        }

        if (parametersPredicate != null &&
            !safe { parametersPredicate!!(method.parameterTypes.toList()) }
        ) return false

        if (parameterCount != null && method.parameterCount != parameterCount) return false

        if (parameterCountPredicate != null &&
            !safe { parameterCountPredicate!!(method.parameterCount) }
        ) return false

        val rt = returnType.typeInputToClass()
        if (rt != null && !safe { rt.typeMatches(method.returnType) }) return false

        if (returnTypePredicate != null &&
            !safe { returnTypePredicate!!(method.returnType) }
        ) return false

        val m = modifiers
        if (m != null && method.modifiers and m != m) return false
        if (modifiersMaskPredicate != null &&
            !safe { modifiersMaskPredicate!!(method.modifiers.toModifierSet()) }
        ) return false
        return true
    }

    override fun staticCacheKeyParts(): List<Any?> =
        listOf(name, parameters?.typeInputToCacheKeyParameters(), parameterCount, returnType.typeInputToClass(), modifiers, superclass)
}
