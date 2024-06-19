package io.github.wcarmon.cache;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import lombok.Builder;
import org.jetbrains.annotations.Nullable;

/**
 * A Read-Through, Refresh-Ahead cache.
 * Thread-safe, Asynchronous refresh.
 *
 * @param <K> cache key type (like a key in java.util.Map)
 * @param <V> cache value type (like a value in java.util.Map)
 */
public final class ReadThruRefreshAheadCache<K, V> {

    private static final Consumer<Object> NO_OP = ignored -> {
    };

    private final ConcurrentMap<K, V> cache;
    private final ExecutorService executorService;
    private final BiConsumer<? super K, ? super V> onAfterRefresh;
    private final Consumer<? super K> onBeforeRefresh;
    private final Consumer<? super K> onCacheHit;
    private final Consumer<? super K> onCacheMiss;
    private final Function<? super K, ? extends V> valueLoader;

    // TODO: delombok
    @Builder
    public ReadThruRefreshAheadCache(
            int capacity,
            Function<K, V> valueLoader,
            ExecutorService executorService,
            @Nullable Consumer<K> onBeforeRefresh,
            @Nullable Consumer<K> onCacheHit,
            @Nullable Consumer<K> onCacheMiss,
            @Nullable BiConsumer<K, V> onAfterRefresh) {

        requireNonNull(executorService, "executorService is required and null.");
        requireNonNull(valueLoader, "valueLoader is required and null.");

        this.executorService = executorService;
        this.onBeforeRefresh = requireNonNullElse(onBeforeRefresh, NO_OP);
        this.onCacheHit = requireNonNullElse(onCacheHit, NO_OP);
        this.onCacheMiss = requireNonNullElse(onCacheMiss, NO_OP);
        this.valueLoader = valueLoader;

        if (onAfterRefresh == null) {
            this.onAfterRefresh = (ignored0, ignored1) -> {
            };
        } else {
            this.onAfterRefresh = onAfterRefresh;
        }

        cache = new ConcurrentHashMap<>(capacity);
    }

    private static void requireNonBlankKey(Object key) {
        requireNonNull(key, "key is required and null.");

        if (key instanceof String s && s.isBlank()) {
            throw new IllegalArgumentException("key is required");
        }
    }

    /**
     * Removes all entries
     */
    public void clear() {
        cache.clear();
    }

    /**
     * @param key
     * @return
     */
    public boolean containsKey(K key) {
        requireNonBlankKey(key);

        return cache.containsKey(key);
    }

    /**
     * @param key
     * @param bypassCache TODO
     * @return V or null if unavailable in both cache and datasource
     */
    public V get(K key, boolean bypassCache) {
        requireNonBlankKey(key);

        throw new RuntimeException("TODO: implement get");
    }

    /**
     * @param key
     * @return V or null if unavailable in both cache and datasource
     */
    public V get(K key) {
        requireNonBlankKey(key);

        return get(key, false);
    }

    /**
     * TODO
     *
     * @return TODO
     */
    public boolean isEmpty() {
        return cache.isEmpty();
    }

    /**
     * insert entry (when absent) or replace entry (when present)
     *
     * @param key
     * @param value
     */
    public void put(K key, V value) {
        requireNonNull(key, "key is required and null.");
        requireNonNull(value, "value is required and null.");

        cache.put(key, value);
    }

    /**
     * TODO
     *
     * @param m TODO
     */
    public void putAll(Map<? extends K, ? extends V> m) {
        cache.putAll(m);
    }

    /**
     * TODO
     *
     * @param key
     * @return TODO
     */
    @Nullable
    public V remove(K key) {
        requireNonBlankKey(key);

        return cache.remove(key);
    }

    /**
     * TODO
     *
     * @return TODO
     */
    public int size() {
        return cache.size();
    }
}
