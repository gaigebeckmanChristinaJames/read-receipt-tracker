@file:Suppress("unused", "MemberVisibilityCanBePrivate", "UNCHECKED_CAST", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")
@file:JvmName("ClassUtils")

package dev.ujhhgtg.reflekt.utils


import java.lang.reflect.Constructor
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass


/** Structured key for [createInstance] constructor cache. */
private data class CreateInstanceConstructorCacheKey(
    val targetClass: Class<*>,
    val parameterTypes: List<CreateInstanceArgumentType>,
    val isPublic: Boolean
)

/** Argument type descriptor for [createInstance] constructor cache. */
private sealed interface CreateInstanceArgumentType {

    /** Null argument marker that preserves position in constructor cache keys. */
    data object Null : CreateInstanceArgumentType

    /** Runtime argument type marker used by constructor cache keys. */
    data class Runtime(val type: Class<*>) : CreateInstanceArgumentType
}

/** Cache for [createInstance] function to store constructors for faster access. */
private val createInstanceConstructorsCache = ConcurrentHashMap<CreateInstanceConstructorCacheKey, Constructor<*>>()

/**
 * Provide a [ClassLoader] for reflection operations.
 */
object ReflectionClassLoader {

    /**
     * The [ClassLoader] used for reflection operations.
     *
     * If null, it will be determined according to the default behavior of the current JVM.
     */
    var value: ClassLoader? = null
}

@Suppress("UNCHECKED_CAST")
@JvmName("create")
fun String.toClass(loader: ClassLoader? = null, initialize: Boolean = false): Class<Any> {
    val createLoader = loader ?: ReflectionClassLoader.value

    return ((if (createLoader != null)
        Class.forName(this, initialize, createLoader)
    else Class.forName(this)) ?: error("JVM class not resolved: $this")) as Class<Any>
}

@JvmName("createTyped")
fun <T : Any> String.toClass(loader: ClassLoader? = null, initialize: Boolean = false) =
    toClass(loader, initialize) as? Class<T>? ?: error("JVM class type cast failed: $this")

@JvmName("createOrNull")
fun String.toClassOrNull(loader: ClassLoader? = null, initialize: Boolean = false) = runCatching {
    toClass(loader, initialize)
}.getOrNull()

@JvmName("createOrNullTyped")
fun <T : Any> String.toClassOrNull(loader: ClassLoader? = null, initialize: Boolean = false) =
    toClassOrNull(loader, initialize) as? Class<T>?

/**
 * Create an instance of [Class] with the given arguments.
 *
 * - Note: If you give a null argument, it will be treated as an any type value for the constructor parameter,
 *   but if all arguments are null, it will throw an [IllegalStateException].
 * @receiver the [Class] to be instantiated.
 * @param args the arguments to be passed to the constructor.
 * @param isPublic whether to only consider public constructors, default is true.
 * @return [T]
 * @throws NoSuchMethodException if no suitable constructor is found.
 * @throws IllegalStateException if all arguments are null.
 */
fun <T : Any> Class<T>.createInstance(vararg args: Any?, isPublic: Boolean = true): T {
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "RemoveRedundantQualifierName")
    fun Class<*>.wrap() = when (this) {
        java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
        java.lang.Byte.TYPE -> java.lang.Byte::class.java
        java.lang.Character.TYPE -> java.lang.Character::class.java
        java.lang.Short.TYPE -> java.lang.Short::class.java
        java.lang.Integer.TYPE -> java.lang.Integer::class.java
        java.lang.Long.TYPE -> java.lang.Long::class.java
        java.lang.Float.TYPE -> java.lang.Float::class.java
        java.lang.Double.TYPE -> java.lang.Double::class.java
        java.lang.Void.TYPE -> java.lang.Void::class.java
        else -> this
    }

    fun filterConstructor() = declaredConstructors.asSequence()
        .filter { !isPublic || it.isPublic }
        .filter { it.parameterTypes.size == args.size }.firstOrNull {
            it.parameterTypes.zip(args).all { (type, arg) ->
                val isBoxed = arg == null && !type.isPrimitive
                isBoxed || arg?.javaClass?.isSubclassOf(type.wrap()) == true
            }
        }

    fun Constructor<*>?.create() = this?.newInstance(*args) as? T? ?: throw NoSuchMethodError(
        "Could not find a suitable constructor for $this with arguments: ${args.joinToString().ifBlank { "(empty)" }}."
    )

    // If all arguments are null, throw an exception.
    if (args.isNotEmpty() && args.all { it == null })
        error("Not allowed to create an instance with all null arguments for $this.")

    val constructorKey = CreateInstanceConstructorCacheKey(
        targetClass = this,
        parameterTypes = args.map {
            it?.javaClass?.let(CreateInstanceArgumentType::Runtime) ?: CreateInstanceArgumentType.Null
        },
        isPublic = isPublic
    )

    return createInstanceConstructorsCache[constructorKey]?.create() ?: run {
        val constructor = filterConstructor()?.also {
            createInstanceConstructorsCache[constructorKey] = it
        }
        constructor.create()
    }
}

fun <T : Any> KClass<T>.createInstance(vararg args: Any?, isPublic: Boolean = true) =
    java.createInstance(*args, isPublic = isPublic)

fun ClassLoader.loadClassOrNull(name: String) = runCatching { loadClass(name) as? Class<Any>? }.getOrNull()

fun ClassLoader.hasClass(name: String) = loadClassOrNull(name) != null

infix fun <T : Any> Class<T>.isSubclassOf(superclass: Class<*>) = superclass.isAssignableFrom(this)

infix fun <T : Any> KClass<T>.isSubclassOf(superclass: KClass<*>) = java isSubclassOf superclass.java

infix fun <T : Any> KClass<T>.isSubclassOf(superclass: Class<*>) = java isSubclassOf superclass

infix fun <T : Any> Class<T>.isSubclassOf(superclass: KClass<*>) = this isSubclassOf superclass.java

infix fun <T : Any> Class<T>.isNotSubclassOf(superclass: Class<*>) = !isSubclassOf(superclass)

infix fun <T : Any> Class<T>.isNotSubclassOf(superclass: KClass<*>) = this isNotSubclassOf superclass.java

val Class<*>.isBuiltin get() = this.isPrimitive || this.name.startsWith("java.") || this.name.startsWith("kotlin.") || this.name.startsWith("android.")
