package io.github.wcarmon.cache;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;

/**
 * A Read-Through, Refresh-Ahead cache.
 * Thread-safe, Asynchronous refresh.
 *
 * @param <K> cache key type (like a key in java.util.Map)
 * @param <V> cache value type (like a value in java.util.Map)
 */
public final class ReadThruRefreshAheadCache<K, V> {

    private static final Consumer<Object> NO_OP = ignored -> {};

    private final ConcurrentMap<K, V> cache;

    /** Executes background tasks like refreshing entries. */
    private final ExecutorService executorService;

    /** Executes after any insert, update, or remove. */
    private final Runnable onAfterChange;

    /** Executes before an entry refresh. */
    private final Consumer<? super K> onBeforeRefresh;

    /** Executes after this::get finds a matching entry in this::cache */
    private final Consumer<? super K> onCacheHit;

    /** Executes after this::get fails to find a matching entry in in this::cache */
    private final Consumer<? super K> onCacheMiss;

    /** Executes after failing to load value from valueLoader */
    private final @Nullable BiConsumer<? super K, ? super Exception> onValueLoadException;

    private final boolean removeEntryWhenValueLoaderReturnsNull;

    /** Given a key, retrieves a value from a slower data store */
    private final Function<? super K, ? extends V> valueLoader;

    private ReadThruRefreshAheadCache(
            int capacity,
            Function<? super K, ? extends V> valueLoader,
            ExecutorService executorService,
            boolean removeEntryWhenValueLoaderReturnsNull,
            @Nullable BiConsumer<? super K, ? super Exception> onValueLoadException,
            @Nullable Consumer<K> onBeforeRefresh,
            @Nullable Consumer<K> onCacheHit,
            @Nullable Consumer<K> onCacheMiss,
            @Nullable Runnable onAfterChange) {

        requireNonNull(executorService, "executorService is required and null.");
        requireNonNull(onValueLoadException, "onValueLoadException is required and null.");
        requireNonNull(valueLoader, "valueLoader is required and null.");

        this.executorService = executorService;
        this.onBeforeRefresh = requireNonNullElse(onBeforeRefresh, NO_OP);
        this.onCacheHit = requireNonNullElse(onCacheHit, NO_OP);
        this.onCacheMiss = requireNonNullElse(onCacheMiss, NO_OP);
        this.onValueLoadException = onValueLoadException;
        this.removeEntryWhenValueLoaderReturnsNull = removeEntryWhenValueLoaderReturnsNull;
        this.valueLoader = valueLoader;

        if (onAfterChange == null) {
            this.onAfterChange = () -> {};
        } else {
            this.onAfterChange = onAfterChange;
        }

        cache = new ConcurrentHashMap<>(capacity);
    }

    public static <K, V> ReadThruRefreshAheadCacheBuilder<K, V> builder() {
        return new ReadThruRefreshAheadCacheBuilder<>();
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
     * <p>
     * All valueLoader exceptions propagated thru this::onValueLoadException (both sync and async)
     *
     * @param key         - id for value to retrieve value from local cache or from valueLoader
     * @param bypassCache true: ignore values in cache, go straight to valueLoader, refresh cache
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

        // -- Got a cached value and cache is permitted
        if (!bypassCache && valueInCache != null) {
            refreshLater(key);
            return valueInCache;
        }

        // -- Attempt to load
        final V value;
        try {
            value = valueLoader.apply(key);

        } catch (Exception ex) {
            onValueLoadException.accept(key, ex);
            return null;
        }

        // -- Cache "good" values
        if (value != null) {
            final V old = cache.put(key, value);
            if (!Objects.equals(old, value)) {
                onAfterChange.run();
            }

            return value;
        }

        // -- Invariant: value == null

        if (removeEntryWhenValueLoaderReturnsNull) {
            final V old = cache.remove(key);
            if (old != null) {
                onAfterChange.run();
            }
        }

        return null;
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
            onValueLoadException.accept(key, ex);
            return;
        }

        if (value != null) {
            cache.put(key, value);
            onAfterChange.run();
            return;
        }

        if (removeEntryWhenValueLoaderReturnsNull) {
            final V old = cache.remove(key);
            if (old == null) {
                return;
            }

            onAfterChange.run();
        }
    }

    public static class ReadThruRefreshAheadCacheBuilder<K, V> {

        private int capacity;
        private ExecutorService executorService;
        private @Nullable Runnable onAfterChange;
        private @Nullable Consumer<K> onBeforeRefresh;
        private @Nullable Consumer<K> onCacheHit;
        private @Nullable Consumer<K> onCacheMiss;
        private @Nullable BiConsumer<K, Exception> onValueLoadException;
        private boolean removeEntryWhenValueLoaderReturnsNull;
        private Function<K, V> valueLoader;

        ReadThruRefreshAheadCacheBuilder() {}

        public ReadThruRefreshAheadCache<K, V> build() {
            return new ReadThruRefreshAheadCache<>(
                    this.capacity,
                    this.valueLoader,
                    this.executorService,
                    this.removeEntryWhenValueLoaderReturnsNull,
                    this.onValueLoadException,
                    this.onBeforeRefresh,
                    this.onCacheHit,
                    this.onCacheMiss,
                    this.onAfterChange);
        }

        public ReadThruRefreshAheadCacheBuilder<K, V> capacity(int capacity) {
            this.capacity = capacity;
            return this;
        }

        public ReadThruRefreshAheadCacheBuilder<K, V> executorService(
                ExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }

        public ReadThruRefreshAheadCacheBuilder<K, V> onAfterChange(
                @Nullable Runnable onAfterChange) {
            this.onAfterChange = onAfterChange;
            return this;
        }

        public ReadThruRefreshAheadCacheBuilder<K, V> onBeforeRefresh(
                @Nullable Consumer<K> onBeforeRefresh) {
            this.onBeforeRefresh = onBeforeRefresh;
            return this;
        }

        public ReadThruRefreshAheadCacheBuilder<K, V> onCacheHit(@Nullable Consumer<K> onCacheHit) {
            this.onCacheHit = onCacheHit;
            return this;
        }

        public ReadThruRefreshAheadCacheBuilder<K, V> onCacheMiss(
                @Nullable Consumer<K> onCacheMiss) {
            this.onCacheMiss = onCacheMiss;
            return this;
        }

        public ReadThruRefreshAheadCacheBuilder<K, V> onValueLoadException(
                @Nullable BiConsumer<K, Exception> onValueLoadException) {
            this.onValueLoadException = onValueLoadException;
            return this;
        }

        public ReadThruRefreshAheadCacheBuilder<K, V> removeEntryWhenValueLoaderReturnsNull(
                boolean removeEntryWhenValueLoaderReturnsNull) {
            this.removeEntryWhenValueLoaderReturnsNull = removeEntryWhenValueLoaderReturnsNull;
            return this;
        }

        public String toString() {
            return "ReadThruRefreshAheadCache.ReadThruRefreshAheadCacheBuilder(capacity="
                    + this.capacity
                    + ", valueLoader="
                    + this.valueLoader
                    + ", executorService="
                    + this.executorService
                    + ", removeEntryWhenValueLoaderReturnsNull="
                    + this.removeEntryWhenValueLoaderReturnsNull
                    + ", onValueLoadException="
                    + this.onValueLoadException
                    + ", onBeforeRefresh="
                    + this.onBeforeRefresh
                    + ", onCacheHit="
                    + this.onCacheHit
                    + ", onCacheMiss="
                    + this.onCacheMiss
                    + ", onAfterChange="
                    + this.onAfterChange
                    + ")";
        }

        public ReadThruRefreshAheadCacheBuilder<K, V> valueLoader(Function<K, V> valueLoader) {
            this.valueLoader = valueLoader;
            return this;
        }
    }
}
