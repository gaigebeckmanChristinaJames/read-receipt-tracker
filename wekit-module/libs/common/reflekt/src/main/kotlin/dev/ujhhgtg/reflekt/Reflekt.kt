@file:Suppress("unused", "NOTHING_TO_INLINE")

package dev.ujhhgtg.reflekt

import dev.ujhhgtg.reflekt.reflected.ReflectedConstructor
import dev.ujhhgtg.reflekt.reflected.ReflectedField
import dev.ujhhgtg.reflekt.reflected.ReflectedMethod
import dev.ujhhgtg.reflekt.spec.ConstructorSpec
import dev.ujhhgtg.reflekt.spec.FieldSpec
import dev.ujhhgtg.reflekt.spec.MethodSpec
import kotlin.reflect.KClass

inline fun <T : Any> KClass<T>.reflekt(): Reflect<T> = Reflect(java)
inline fun <T> Class<T>.reflekt(): Reflect<T> = Reflect(this)

@JvmName("reflektInstance")
inline fun <T : Any> T.reflekt(): InstanceReflect<T> = InstanceReflect(this)

// ========== Quick entry points — Class<T> ==========

inline fun <T> Class<T>.firstMethod(noinline config: MethodSpec.() -> Unit): ReflectedMethod<T> = this@firstMethod.reflekt().firstMethod(config)
inline fun <T> Class<T>.firstMethodOrNull(noinline config: MethodSpec.() -> Unit): ReflectedMethod<T>? = this@firstMethodOrNull.reflekt().firstMethodOrNull(config)
inline fun <T> Class<T>.lastMethod(noinline config: MethodSpec.() -> Unit): ReflectedMethod<T> = this@lastMethod.reflekt().lastMethod(config)
inline fun <T> Class<T>.lastMethodOrNull(noinline config: MethodSpec.() -> Unit): ReflectedMethod<T>? = this@lastMethodOrNull.reflekt().lastMethodOrNull(config)
inline fun <T> Class<T>.methods(noinline config: MethodSpec.() -> Unit): List<ReflectedMethod<T>> = this@methods.reflekt().methods(config)

inline fun <T> Class<T>.firstField(noinline config: FieldSpec.() -> Unit): ReflectedField<T> = this@firstField.reflekt().firstField(config)
inline fun <T> Class<T>.firstFieldOrNull(noinline config: FieldSpec.() -> Unit): ReflectedField<T>? = this@firstFieldOrNull.reflekt().firstFieldOrNull(config)
inline fun <T> Class<T>.lastField(noinline config: FieldSpec.() -> Unit): ReflectedField<T> = this@lastField.reflekt().lastField(config)
inline fun <T> Class<T>.lastFieldOrNull(noinline config: FieldSpec.() -> Unit): ReflectedField<T>? = this@lastFieldOrNull.reflekt().lastFieldOrNull(config)
inline fun <T> Class<T>.fields(noinline config: FieldSpec.() -> Unit): List<ReflectedField<T>> = this@fields.reflekt().fields(config)

inline fun <T> Class<T>.firstConstructor(noinline config: ConstructorSpec.() -> Unit): ReflectedConstructor<T> = this@firstConstructor.reflekt().firstConstructor(config)
inline fun <T> Class<T>.firstConstructorOrNull(noinline config: ConstructorSpec.() -> Unit): ReflectedConstructor<T>? = this@firstConstructorOrNull.reflekt().firstConstructorOrNull(config)
inline fun <T> Class<T>.lastConstructor(noinline config: ConstructorSpec.() -> Unit): ReflectedConstructor<T> = this@lastConstructor.reflekt().lastConstructor(config)
inline fun <T> Class<T>.lastConstructorOrNull(noinline config: ConstructorSpec.() -> Unit): ReflectedConstructor<T>? = this@lastConstructorOrNull.reflekt().lastConstructorOrNull(config)
inline fun <T> Class<T>.constructors(noinline config: ConstructorSpec.() -> Unit): List<ReflectedConstructor<T>> = this@constructors.reflekt().constructors(config)

// ========== Quick entry points — KClass<T> ==========

inline fun <T : Any> KClass<T>.firstMethod(noinline config: MethodSpec.() -> Unit): ReflectedMethod<T> = java.firstMethod(config)
inline fun <T : Any> KClass<T>.firstMethodOrNull(noinline config: MethodSpec.() -> Unit): ReflectedMethod<T>? = java.firstMethodOrNull(config)
inline fun <T : Any> KClass<T>.lastMethod(noinline config: MethodSpec.() -> Unit): ReflectedMethod<T> = java.lastMethod(config)
inline fun <T : Any> KClass<T>.lastMethodOrNull(noinline config: MethodSpec.() -> Unit): ReflectedMethod<T>? = java.lastMethodOrNull(config)
inline fun <T : Any> KClass<T>.methods(noinline config: MethodSpec.() -> Unit): List<ReflectedMethod<T>> = java.methods(config)

inline fun <T : Any> KClass<T>.firstField(noinline config: FieldSpec.() -> Unit): ReflectedField<T> = java.firstField(config)
inline fun <T : Any> KClass<T>.firstFieldOrNull(noinline config: FieldSpec.() -> Unit): ReflectedField<T>? = java.firstFieldOrNull(config)
inline fun <T : Any> KClass<T>.lastField(noinline config: FieldSpec.() -> Unit): ReflectedField<T> = java.lastField(config)
inline fun <T : Any> KClass<T>.lastFieldOrNull(noinline config: FieldSpec.() -> Unit): ReflectedField<T>? = java.lastFieldOrNull(config)
inline fun <T : Any> KClass<T>.fields(noinline config: FieldSpec.() -> Unit): List<ReflectedField<T>> = java.fields(config)

inline fun <T : Any> KClass<T>.firstConstructor(noinline config: ConstructorSpec.() -> Unit): ReflectedConstructor<T> = java.firstConstructor(config)
inline fun <T : Any> KClass<T>.firstConstructorOrNull(noinline config: ConstructorSpec.() -> Unit): ReflectedConstructor<T>? = java.firstConstructorOrNull(config)
inline fun <T : Any> KClass<T>.lastConstructor(noinline config: ConstructorSpec.() -> Unit): ReflectedConstructor<T> = java.lastConstructor(config)
inline fun <T : Any> KClass<T>.lastConstructorOrNull(noinline config: ConstructorSpec.() -> Unit): ReflectedConstructor<T>? = java.lastConstructorOrNull(config)
inline fun <T : Any> KClass<T>.constructors(noinline config: ConstructorSpec.() -> Unit): List<ReflectedConstructor<T>> = java.constructors(config)
