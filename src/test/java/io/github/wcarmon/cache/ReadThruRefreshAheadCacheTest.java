package io.github.wcarmon.cache;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.BeforeEach;

class ReadThruRefreshAheadCacheTest {

    ReadThruRefreshAheadCache subject;
    ExecutorService executorService;

    @BeforeEach
    void setUp() {

        executorService = Executors.newFixedThreadPool(3);

        subject = ReadThruRefreshAheadCache.builder()
                .capacity(64)
                .executorService(executorService)
//                .valueLoader()
//        TODO: Consumer<K> onBeforeRefresh,
//        TODO: BiConsumer<K, V> onAfterRefresh,
//        TODO: Consumer<K> onCacheHit,
//        TODO: Consumer<K> onCacheMiss,
//        TODO: Consumer<K> onAfterChange,
                .build();
    }

    // TODO: value absent + !bypassCache

    // TODO: value absent + bypassCache

    // TODO: value present + !bypassCache

    // TODO: value present + bypassCache

    // TODO: BiConsumer<? super K, ? super V> onAfterRefresh

    // TODO: Consumer<? super K> onBeforeRefresh

    // TODO: Consumer<? super K> onCacheHit

    // TODO: Consumer<? super K> onCacheMiss

    // TODO: onAfterChange

    // TODO: clear

    // TODO: containsKey

    // TODO: get (bypass)

    // TODO: isEmpty

    // TODO: put

    // TODO: putAll

    // TODO: remove

    // TODO: size
}