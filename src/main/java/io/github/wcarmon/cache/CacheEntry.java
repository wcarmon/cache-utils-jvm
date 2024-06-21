package io.github.wcarmon.cache;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.ScheduledFuture;
import org.jetbrains.annotations.Nullable;

/**
 * An Item in the cache
 *
 * @param value       value of the cache entry
 * @param expiration optional result for cleanup based on TTL
 * @param <V>         value type
 */
record CacheEntry<V>(V value, @Nullable ScheduledFuture<?> expiration) {

    CacheEntry {
        requireNonNull(value, "value is required and null.");
    }

    public static <V> CacheEntry<V> of(V value) {
        requireNonNull(value, "value is required and null.");

        return new CacheEntry<>(value, null);
    }
}
