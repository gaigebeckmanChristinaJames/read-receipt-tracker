@file:Suppress("unused")

package dev.ujhhgtg.reflekt.reflected

import dev.ujhhgtg.reflekt.utils.makeAccessible
import java.lang.reflect.Method

open class BaseReflectedMethod(open val self: Method) {
    val name: String get() = self.name
    val declaringClass: Class<*> get() = self.declaringClass
    val returnType: Class<*> get() = self.returnType
    val parameterTypes: Array<Class<*>> get() = self.parameterTypes
    val modifiers: Int get() = self.modifiers
    val annotations: Array<Annotation> get() = self.annotations
    val declaredAnnotations: Array<Annotation> get() = self.declaredAnnotations

    fun getAnnotation(annotationClass: Class<out Annotation>): Annotation? =
        self.getAnnotation(annotationClass)

    override fun toString(): String = self.toString()
    override fun hashCode(): Int = self.hashCode()
    override fun equals(other: Any?): Boolean =
        other is BaseReflectedMethod && self == other.self
}

open class ReflectedMethod<T>(override val self: Method) : BaseReflectedMethod(self) {

    fun invoke(instance: T?, vararg args: Any?): Any? {
        self.makeAccessible()
        return self.invoke(instance, *args)
    }

    fun invokeStatic(vararg args: Any?): Any? {
        self.makeAccessible()
        return self.invoke(null, *args)
    }
}

class InstanceReflectedMethod<T : Any>(
    private val instance: T,
    override val self: Method
) : BaseReflectedMethod(self) {

    fun invoke(vararg args: Any?): Any? {
        self.makeAccessible()
        return self.invoke(instance, *args)
    }
}
