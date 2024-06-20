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

    /**
     * Executes background tasks like refreshing entries.
     */
    private final ExecutorService executorService;

    /** Executes after a successful refresh. */
    private final BiConsumer<? super K, ? super V> onAfterBackgroundRefresh;

    /**
     * Executes after any insert, update, or remove.
     */
    private final Runnable onAfterChange;

    /** Executes before an entry refresh. */
    private final Consumer<? super K> onBeforeRefresh;

    /** Executes after this::get finds a matching entry in this::cache */
    private final Consumer<? super K> onCacheHit;

    /** Executes after this::get fails to find a matching entry in in this::cache */
    private final Consumer<? super K> onCacheMiss;

    /** Executes after failing to refresh an entry */
    private final BiConsumer<K, Exception> onRefreshFailure;

    /** Given a key, retrieves a value from a slower data store */
    private final Function<? super K, ? extends V> valueLoader;

    // TODO: delombok
    @Builder
    private ReadThruRefreshAheadCache(
            int capacity,
            Function<K, V> valueLoader,
            ExecutorService executorService,
            @Nullable Consumer<K> onBeforeRefresh,
            @Nullable BiConsumer<K, V> onAfterBackgroundRefresh,
            @Nullable BiConsumer<K, Exception> onRefreshFailure,
            @Nullable Consumer<K> onCacheHit,
            @Nullable Consumer<K> onCacheMiss,
            @Nullable Runnable onAfterChange) {

        requireNonNull(executorService, "executorService is required and null.");
        requireNonNull(valueLoader, "valueLoader is required and null.");

        this.executorService = executorService;
        this.onBeforeRefresh = requireNonNullElse(onBeforeRefresh, NO_OP);
        this.onCacheHit = requireNonNullElse(onCacheHit, NO_OP);
        this.onCacheMiss = requireNonNullElse(onCacheMiss, NO_OP);
        this.valueLoader = valueLoader;

        if (onAfterBackgroundRefresh == null) {
            this.onAfterBackgroundRefresh = (ignored0, ignored1) -> {
            };
        } else {
            this.onAfterBackgroundRefresh = onAfterBackgroundRefresh;
        }

        if (onAfterChange == null) {
            this.onAfterChange = () -> {
            };
        } else {
            this.onAfterChange = onAfterChange;
        }

        if (onRefreshFailure == null) {
            this.onRefreshFailure = (ignored0, ignored1) -> {
            };
        } else {
            this.onRefreshFailure = onRefreshFailure;
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
     * Case #1: When value absent from cache ...
     * 1. Loads value in foreground
     * 2. Stores non-null result in cache
     * <p>
     * Case #2: When value present in cache ...
     * 1. Returns local non-null value
     * 2. Refreshes value in background
     *
     * @param key
     * @param bypassCache TODO
     * @return V or null if unavailable in both cache and valueLoader
     */
    @Nullable
    public V get(K key, boolean bypassCache) {
        requireNonBlankKey(key);

        final V valueInCache = cache.get(key);
        if (valueInCache == null) {
            onCacheMiss.accept(key);
        } else {
            onCacheHit.accept(key);
        }

        if (!bypassCache && valueInCache != null) {
            refreshLater(key);
            return valueInCache;
        }

        final V value = valueLoader.apply(key);
        if (value != null) {
            cache.put(key, value);
            onAfterChange.run();
        }

        return value;
    }

    /**
     * @param key
     * @return V or null if unavailable in both cache and datasource
     */
    @Nullable
    public V get(K key) {
        requireNonBlankKey(key);

        return get(key, false);
    }

    /**
     * checks if cache contains entries
     *
     * @return true when cache has zero entries
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
     * Efficiently stores all passed entries
     *
     * @param map entries to store in the cache
     */
    public void putAll(Map<? extends K, ? extends V> map) {
        cache.putAll(map);
    }

    /**
     * Removes entry (when present)
     *
     * @param key - key whose cache entry will be removed
     * @return previous value associated with key, or null if absent
     */
    @Nullable
    public V remove(K key) {
        requireNonBlankKey(key);

        return cache.remove(key);
    }

    /**
     * Count stored entries
     *
     * @return snapshot of the cache entry count
     */
    public int size() {
        return cache.size();
    }

    private void refreshLater(K key) {
        requireNonBlankKey(key);

        executorService.submit(() -> refreshNow(key));
    }

    private void refreshNow(K key) {
        onBeforeRefresh.accept(key);

        final V value;
        try {
            value = valueLoader.apply(key);

        } catch (Exception ex) {
            onRefreshFailure.accept(key, ex);
            return;
        }

        if (value == null) {
            // -- No state change for null value
            return;
        }

        cache.put(key, value);
        onAfterChange.run();

        onAfterBackgroundRefresh.accept(key, value);
    }
}
