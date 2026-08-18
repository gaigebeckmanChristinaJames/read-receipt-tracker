@file:Suppress("unused")

package dev.ujhhgtg.reflekt.reflected

import java.lang.reflect.Constructor

open class ReflectedConstructor<T>(open val self: Constructor<T>) {
    fun newInstance(vararg args: Any?): T = self.newInstance(*args)

    val name: String get() = self.name
    val declaringClass: Class<*> get() = self.declaringClass
    val parameterTypes: Array<Class<*>> get() = self.parameterTypes
    val modifiers: Int get() = self.modifiers
    val annotations: Array<Annotation> get() = self.annotations
    val declaredAnnotations: Array<Annotation> get() = self.declaredAnnotations

    fun getAnnotation(annotationClass: Class<out Annotation>): Annotation? =
        self.getAnnotation(annotationClass)

    override fun toString(): String = self.toString()
    override fun hashCode(): Int = self.hashCode()
    override fun equals(other: Any?): Boolean =
        other is ReflectedConstructor<*> && self == other.self
}

class InstanceReflectedConstructor<T : Any>(
    private val instance: T,
    override val self: Constructor<T>
) : ReflectedConstructor<T>(self) {
    val instanceClass: Class<*> get() = instance::class.java
}
