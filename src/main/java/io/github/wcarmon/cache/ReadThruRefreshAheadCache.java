package io.github.wcarmon.cache;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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

    private static final Consumer<Object> NO_OP = ignored -> {
    };

    private final ConcurrentMap<K, CacheEntry<V>> cache;

    /** Executes background tasks like refreshing entries and entry expiration */
    private final ScheduledExecutorService executorService;

    /**
     * Executes after any insert, update, or remove.
     * invoked with the oldValue (possibly null) and the newValue (possibly null).
     */
    private final BiConsumer<? super V, ? super V> onAfterChange;

    /** Executes before an entry refresh. */
    private final Consumer<? super K> onBeforeRefresh;

    /** Executes after this::get finds a matching entry in this::cache */
    private final Consumer<? super K> onCacheHit;

    /** Executes after this::get fails to find a matching entry in in this::cache */
    private final Consumer<? super K> onCacheMiss;

    /** Executes after failing to load value from valueLoader */
    private final BiConsumer<? super K, ? super Exception> onValueLoadException;

    /** If the valueLoader returns null, remove the associated entry */
    private final boolean removeEntryWhenValueLoaderReturnsNull;

    /** Time to live for entries */
    @Nullable
    private final Duration ttl;

    /** Given a key, retrieves a value from a slower data store */
    private final Function<? super K, ? extends V> valueLoader;

    private ReadThruRefreshAheadCache(
            int capacity,
            boolean removeEntryWhenValueLoaderReturnsNull,
            @Nullable Duration ttl,
            Function<? super K, ? extends V> valueLoader,
            ScheduledExecutorService executorService,
            BiConsumer<? super K, ? super Exception> onValueLoadException,
            @Nullable Consumer<K> onBeforeRefresh,
            @Nullable Consumer<K> onCacheHit,
            @Nullable Consumer<K> onCacheMiss,
            @Nullable BiConsumer<? super V, ? super V> onAfterChange) {

        requireNonNull(executorService, "executorService is required and null.");
        requireNonNull(onValueLoadException, "onValueLoadException is required and null.");
        requireNonNull(valueLoader, "valueLoader is required and null.");

        if (ttl != null && ttl.toMillis() <= 0L) {
            throw new IllegalArgumentException("ttl must be positive or null");
        }

        this.executorService = executorService;
        this.onBeforeRefresh = requireNonNullElse(onBeforeRefresh, NO_OP);
        this.onCacheHit = requireNonNullElse(onCacheHit, NO_OP);
        this.onCacheMiss = requireNonNullElse(onCacheMiss, NO_OP);
        this.onValueLoadException = onValueLoadException;
        this.removeEntryWhenValueLoaderReturnsNull = removeEntryWhenValueLoaderReturnsNull;
        this.valueLoader = valueLoader;
        this.ttl = ttl;

        if (onAfterChange == null) {
            this.onAfterChange = (oldValue, newValue) -> {
            };
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
            throw new IllegalArgumentException("key is required and blank");
        }
    }

    /**
     * Removes all entries
     */
    public void clear() {
        cache.clear();
    }

    /**
     * Test if key is present in cache
     *
     * @param key - unique id for entry
     * @return true when entry is present, else false
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

        final CacheEntry<V> valueInCache = cache.get(key);
        if (valueInCache == null) {
            onCacheMiss.accept(key);
        } else {
            onCacheHit.accept(key);
        }

        // -- Got a cached value and cache is permitted
        if (!bypassCache && valueInCache != null) {
            refreshLater(key);
            return valueInCache.value();
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
            putInternal(key, value);

            return value;
        }

        // -- Invariant: value == null

        if (removeEntryWhenValueLoaderReturnsNull) {
            removeInternal(key);
        }

        return null;
    }

    /**
     * Attempt to retrieve the value for the given key.
     *
     * @param key - unique id for entry
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
     * @param key   - unique id for entry
     * @param value
     */
    public void put(K key, V value) {
        requireNonNull(key, "key is required and null.");
        requireNonNull(value, "value is required and null.");

        putInternal(key, value);
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

        return removeInternal(key);
    }

    /**
     * Count stored entries
     *
     * @return snapshot of the cache entry count
     */
    public int size() {
        return cache.size();
    }

    private void putInternal(K key, V value) {
        requireNonNull(key, "key is required and null.");
        requireNonNull(value, "value is required and null.");

        final CacheEntry<V> old =
                cache.put(key, new CacheEntry<>(value, scheduleExpiration(key)));

        if (old != null && old.expiration() != null) {
            old.expiration().cancel(false);
        }

        final V oldValue = old == null ? null : old.value();
        if (!Objects.equals(oldValue, value)) {
            onAfterChange.accept(oldValue, value);
        }
    }

    private void refreshLater(K key) {
        requireNonBlankKey(key);

        executorService.submit(() -> refreshNow(key));
    }

    private void refreshNow(K key) {
        requireNonNull(key, "key is required and null.");

        onBeforeRefresh.accept(key);

        final V value;
        try {
            value = valueLoader.apply(key);

        } catch (Exception ex) {
            onValueLoadException.accept(key, ex);
            return;
        }

        if (value != null) {
            putInternal(key, value);
            return;
        }

        if (removeEntryWhenValueLoaderReturnsNull) {
            removeInternal(key);
        }
    }

    @Nullable
    private V removeInternal(K key) {
        requireNonBlankKey(key);

        final CacheEntry<V> old = cache.remove(key);
        if (old == null) {
            return null;
        }

        if (old.expiration() != null) {
            old.expiration().cancel(false);
        }

        // -- Invariant: oldValue != null

        final V oldValue = old.value();
        onAfterChange.accept(oldValue, null);
        return oldValue;
    }

    @Nullable
    private ScheduledFuture<?> scheduleExpiration(K key) {
        requireNonBlankKey(key);

        if (ttl == null) {
            return null;
        }

        return executorService.schedule(
                () -> remove(key),
                ttl.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    public static class ReadThruRefreshAheadCacheBuilder<K, V> {

        private int capacity;
        private ScheduledExecutorService executorService;
        private @Nullable BiConsumer<? super V, ? super V> onAfterChange;
        private @Nullable Consumer<K> onBeforeRefresh;
        private @Nullable Consumer<K> onCacheHit;
        private @Nullable Consumer<K> onCacheMiss;
        private @Nullable BiConsumer<? super K, ? super Exception> onValueLoadException;
        private boolean removeEntryWhenValueLoaderReturnsNull;
        private @Nullable Duration ttl;
        private Function<? super K, ? extends V> valueLoader;

        ReadThruRefreshAheadCacheBuilder() {
        }

        public ReadThruRefreshAheadCache<K, V> build() {
            return new ReadThruRefreshAheadCache<>(
                    this.capacity,
                    this.removeEntryWhenValueLoaderReturnsNull,
                    this.ttl,
                    this.valueLoader,
                    this.executorService,
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
                ScheduledExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }

        public ReadThruRefreshAheadCacheBuilder<K, V> onAfterChange(
                @Nullable BiConsumer<? super V, ? super V> onAfterChange) {
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
                @Nullable BiConsumer<? super K, ? super Exception> onValueLoadException) {
            this.onValueLoadException = onValueLoadException;
            return this;
        }

        public ReadThruRefreshAheadCacheBuilder<K, V> removeEntryWhenValueLoaderReturnsNull(
                boolean removeEntryWhenValueLoaderReturnsNull) {
            this.removeEntryWhenValueLoaderReturnsNull = removeEntryWhenValueLoaderReturnsNull;
            return this;
        }

        public ReadThruRefreshAheadCacheBuilder<K, V> ttl(@Nullable Duration ttl) {
            this.ttl = ttl;
            return this;
        }

        public ReadThruRefreshAheadCacheBuilder<K, V> valueLoader(Function<? super K, ? extends V> valueLoader) {
            this.valueLoader = valueLoader;
            return this;
        }
    }
}
