package io.github.wcarmon.cache;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 20, unit = SECONDS)
class ReadThruRefreshAheadCacheTest {

    ExecutorService executorService;
    CountDownLatch latch;
    BiConsumer<String, Integer> mockOnAfterBackgroundRefresh;
    Runnable mockOnAfterChange;
    Consumer<String> mockOnBeforeRefresh;
    Consumer<String> mockOnCacheHit;
    Consumer<String> mockOnCacheMiss;
    BiConsumer<String, Exception> mockOnRefreshFailure;
    Function<String, Integer> mockValueLoader;
    ReadThruRefreshAheadCache<String, Integer> subject;

    @BeforeEach
    void setUp() {

        latch = new CountDownLatch(1);
        executorService = Executors.newFixedThreadPool(3);
        mockOnAfterBackgroundRefresh = mock(BiConsumer.class);
        mockOnAfterChange = mock(Runnable.class);
        mockOnBeforeRefresh = mock(Consumer.class);
        mockOnCacheHit = mock(Consumer.class);
        mockOnCacheMiss = mock(Consumer.class);
        mockOnRefreshFailure = mock(BiConsumer.class);
        mockValueLoader = mock(Function.class);

        subject = ReadThruRefreshAheadCache.<String, Integer>builder()
                .capacity(64)
                .executorService(executorService)
                .onAfterBackgroundRefresh(mockOnAfterBackgroundRefresh)
                .onAfterChange(mockOnAfterChange)
                .onBeforeRefresh(mockOnBeforeRefresh)
                .onCacheHit(mockOnCacheHit)
                .onCacheMiss(mockOnCacheMiss)
                .onRefreshFailure(mockOnRefreshFailure)
                .valueLoader(mockValueLoader)
                .build();
    }

    @Test
    void testClear() {

        // -- Arrange
        assumeTrue(subject.isEmpty());
        assumeTrue(0 == subject.size());

        subject.put("key", 8);

        assumeFalse(subject.isEmpty());
        assumeFalse(0 == subject.size());

        // -- Act
        subject.clear();

        // -- Assert
        assertEquals(0, subject.size());
    }

    @Test
    void testGetAbsentValueBypassCache() {
        // TODO: value absent + bypassCache
    }

    @Test
    void testGetAbsentValueUseCache() {
        // TODO: value absent + !bypassCache

    }

    @Test
    void testGetPresentValueBypassCache() {
        // TODO: value present + bypassCache
    }

    @Test
    void testGetPresentValueUseCache() {
        // TODO: value present + !bypassCache

    }

    @Test
    void testValueInCache_valueLoaderReturnsNonNull() {
        // TODO:
    }

    @Test
    void testValueInCache_valueLoaderReturnsNull() {

        // -- Arrange
//        final String k = "theKey";
//        assumeFalse(subject.containsKey(k));

//        // -- Act
//        final var got = subject.get(k);
//
//        // -- Assert
//        assertNull(got);
    }

    @Test
    void testValueNotInCache_valueLoaderReturnsNonNull() {
        // -- Arrange
        final String k = "theKey";
        when(mockValueLoader.apply(eq(k)))
                .thenReturn(3);

        assumeTrue(subject.isEmpty());
        assumeFalse(subject.containsKey(k));

        // -- Act
        final Integer got = subject.get(k, false);

        // -- Assert
        assertEquals(3, got);

        assertEquals(1, subject.size());
        assertFalse(subject.isEmpty());
        assertTrue(subject.containsKey(k));


        // -- Assert callbacks
        verify(mockOnAfterChange).run();
        verify(mockOnCacheMiss).accept(eq(k));
        verify(mockValueLoader, times(1)).apply(eq(k));

        verifyNoInteractions(mockOnAfterBackgroundRefresh);
        verifyNoInteractions(mockOnBeforeRefresh);
        verifyNoInteractions(mockOnCacheHit);
        verifyNoInteractions(mockOnRefreshFailure);
    }

    @Test
    void testValueNotInCache_valueLoaderReturnsNull() {

        // -- Arrange
        final String k = "theKey";
        assumeFalse(subject.containsKey(k));

        // -- Act
        final Integer got = subject.get(k);

        // -- Assert
        assertNull(got);
        // TODO: assert state of cache after refresh
    }

    // TODO: get (bypass)

    // TODO: put

    // TODO: putAll

    // TODO: remove

    // TODO: BiConsumer<? super K, ? super V> onAfterRefresh

    // TODO: Consumer<? super K> onBeforeRefresh

    // TODO: Consumer<? super K> onCacheHit

    // TODO: Consumer<? super K> onCacheMiss

    // TODO: onAfterChange

    // TODO: onRefreshFailure

    // TODO: avoid duplicate refresh for key in brief period
}