package dev.ujhhgtg.reflekt

import java.util.concurrent.ConcurrentHashMap

internal object ReflectionCache {
    private val cache = ConcurrentHashMap<CacheKey, Any>()
    private data object NullSentinel

    @Suppress("UNCHECKED_CAST")
    fun <V> getOrPut(key: CacheKey, factory: () -> V): V {
        val result = cache.getOrPut(key) {
            factory() ?: NullSentinel
        }
        return if (result === NullSentinel) null as V else result as V
    }

    fun clear() = cache.clear()
}

internal data class CacheKey(
    val clazz: Class<*>,
    val mode: CacheMode,
    val specValues: List<Any?>
)

internal enum class CacheMode {
    METHOD_FIRST, METHODS, METHOD_LAST,
    FIELD_FIRST, FIELDS, FIELD_LAST,
    CONSTRUCTOR_FIRST, CONSTRUCTORS, CONSTRUCTOR_LAST
}
