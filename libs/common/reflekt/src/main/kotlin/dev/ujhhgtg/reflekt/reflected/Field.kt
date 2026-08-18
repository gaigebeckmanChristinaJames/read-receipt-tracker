@file:Suppress("unused")

package dev.ujhhgtg.reflekt.reflected

import dev.ujhhgtg.reflekt.utils.makeAccessible
import java.lang.reflect.Field

open class BaseReflectedField(open val self: Field) {
    val name: String get() = self.name
    val type: Class<*> get() = self.type
    val modifiers: Int get() = self.modifiers
    val declaringClass: Class<*> get() = self.declaringClass
    val annotations: Array<Annotation> get() = self.annotations
    val declaredAnnotations: Array<Annotation> get() = self.declaredAnnotations

    fun getAnnotation(annotationClass: Class<out Annotation>): Annotation? =
        self.getAnnotation(annotationClass)

    override fun toString(): String = self.toString()
    override fun hashCode(): Int = self.hashCode()
    override fun equals(other: Any?): Boolean =
        other is BaseReflectedField && self == other.self
}

open class ReflectedField<T>(override val self: Field) : BaseReflectedField(self) {

    fun get(instance: T): Any? {
        self.makeAccessible()
        return self.get(instance)
    }

    fun getStatic(): Any? {
        self.makeAccessible()
        return self.get(null)
    }

    fun set(instance: T?, value: Any?) {
        self.makeAccessible()
        self.set(instance, value)
    }

    fun setStatic(value: Any?) {
        self.makeAccessible()
        self.set(null, value)
    }
}

class InstanceReflectedField<T : Any>(
    private val instance: T,
    override val self: Field
) : BaseReflectedField(self) {

    fun get(): Any? {
        self.makeAccessible()
        return self.get(instance)
    }

    fun get(instance: T): Any? {
        self.makeAccessible()
        return self.get(instance)
    }

    fun set(value: Any?) {
        self.makeAccessible()
        self.set(instance, value)
    }

    fun set(instance: T?, value: Any?) {
        self.makeAccessible()
        self.set(instance, value)
    }

    fun erase(): ReflectedField<T> {
        return ReflectedField(self)
    }

    fun of(instance: T): InstanceReflectedField<T> {
        return InstanceReflectedField(instance, self)
    }
}
