package net.corda.cache.caffeine

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache

/**
 * Caffeine cache factory that allows extra functionality to be injected to our caches.
 */
interface CacheFactory {
    /**
     * Build a Caffeine Cache
     *
     * @param name Cache name, used for logging and metrics.
     * @param caffeine Caffeine cache builder.
     */
    fun <K, V> build(name: String, caffeine: Caffeine<in K, in V>): Cache<K, V>

    /**
     * Build a Caffeine Loading Cache
     *
     * @param name Cache name, used for logging and metrics.
     * @param caffeine Caffeine cache builder.
     * @param loader Cache loading function
     */
    fun <K, V> build(name: String, caffeine: Caffeine<in K, in V>, loader: CacheLoader<K, V>): LoadingCache<K, V>
}
