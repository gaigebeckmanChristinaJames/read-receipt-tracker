@file:Suppress("unused")

package dev.ujhhgtg.reflekt.spec

import dev.ujhhgtg.reflekt.utils.toModifierSet
import java.lang.reflect.Field
import kotlin.reflect.KClass

class FieldSpec : Spec() {
    var name: String? = null
    var type: Any? = null
    var modifiers: Int? = null
    var superclass: Boolean = false

    private var namePredicate: ((String) -> Boolean)? = null
    private var typePredicate: ((Class<*>) -> Boolean)? = null
    private var modifiersMaskPredicate: ((Set<Int>) -> Boolean)? = null

    fun name(value: String) {
        name = value
    }

    fun name(predicate: (String) -> Boolean) {
        namePredicate = predicate
        hasRuntimeCondition = true
    }

    fun type(type: KClass<*>) {
        this.type = type.java
    }

    fun type(type: Class<*>) {
        this.type = type
    }

    fun type(predicate: (Class<*>) -> Boolean) {
        typePredicate = predicate
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

    fun matches(field: Field): Boolean {
        if (name != null && field.name != name) return false
        if (namePredicate != null && !namePredicate!!(field.name)) return false
        val rt = type.typeInputToClass()
        if (rt != null && !rt.typeMatches(field.type)) return false
        if (typePredicate != null && !typePredicate!!(field.type)) return false
        val m = modifiers
        if (m != null && (field.modifiers and m) != m) return false
        if (modifiersMaskPredicate != null && !modifiersMaskPredicate!!(field.modifiers.toModifierSet())) return false
        return true
    }

    override fun staticCacheKeyParts(): List<Any?> =
        listOf(name, type.typeInputToClass(), modifiers, superclass)
}
