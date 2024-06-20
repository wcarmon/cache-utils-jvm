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
import static org.mockito.Mockito.timeout;
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
import org.mockito.verification.VerificationMode;

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
        assertTrue(subject.isEmpty());
    }

    @Test
    void testValueInCache_bypassCache_valueLoaderReturnsNonNull() {
        // -- Arrange
        final String k = "theKey";

        // -- Act
        final Integer got = subject.get(k, true);

        // -- Assert: output
        // TODO

        // -- Assert: callbacks
        // TODO

        // -- Assert: state
        // TODO
    }

    @Test
    void testValueInCache_bypassCache_valueLoaderReturnsNull() {
        // -- Arrange
        final String k = "theKey";

        // -- Act
        final Integer got = subject.get(k, true);

        // -- Assert: output
        // TODO

        // -- Assert: callbacks
        // TODO

        // -- Assert: state
        // TODO
    }

    @Test
    @Timeout(value = 3, unit = SECONDS)
    void testValueInCache_valueLoaderReturnsNonNull() {
        // -- Arrange
        final String k = "theKey";

        when(mockValueLoader.apply(eq(k)))
                .thenReturn(8);

        subject.put(k, 7);
        assumeFalse(subject.isEmpty());
        assumeTrue(subject.containsKey(k));

        // -- Act
        final Integer got = subject.get(k, false);

        // -- Assert: output
        assertEquals(7, got);


        // -- Assert: callbacks
        final VerificationMode vMode = timeout(1000L).times(1);
        verify(mockOnAfterBackgroundRefresh, vMode).accept(eq(k), eq(8));
        verify(mockOnAfterChange, vMode).run();
        verify(mockOnBeforeRefresh, vMode).accept(eq(k));
        verify(mockOnCacheHit, vMode).accept(eq(k));
        verify(mockValueLoader, vMode).apply(eq(k));

        verifyNoInteractions(mockOnCacheMiss);
        verifyNoInteractions(mockOnRefreshFailure);


        // -- Assert: state
        assertEquals(1, subject.size());
        assertFalse(subject.isEmpty());
        assertTrue(subject.containsKey(k));
    }

    @Test
    void testValueInCache_valueLoaderReturnsNull() {

        // -- Arrange
        final String k = "theKey";

        when(mockValueLoader.apply(eq(k)))
                .thenReturn(null);

        subject.put(k, 4);
        assumeFalse(subject.isEmpty());
        assumeTrue(subject.containsKey(k));


        // -- Act
        final Integer got = subject.get(k, false);

        // -- Assert: output
        assertEquals(4, got);


        // -- Assert: callbacks
        final VerificationMode vMode = timeout(1000L).times(1);
        // TODO: decide which callbacks should execute


        // -- Assert: state
        // TODO: decide if you should clear out the entry or not
    }

    @Test
    void testValueNotInCache_bypassCache_valueLoaderReturnsNonNull() {
        // -- Arrange
        final String k = "theKey";

        // -- Act
        final Integer got = subject.get(k, true);

        // -- Assert: output
        // TODO

        // -- Assert: callbacks
        // TODO

        // -- Assert: state
        // TODO
    }

    @Test
    void testValueNotInCache_bypassCache_valueLoaderReturnsNull() {
        // -- Arrange
        final String k = "theKey";

        // -- Act
        final Integer got = subject.get(k, true);

        // -- Assert: output
        // TODO

        // -- Assert: callbacks
        // TODO

        // -- Assert: state
        // TODO
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

        // -- Assert: output
        assertEquals(3, got);


        // -- Assert: callbacks
        final VerificationMode vMode = timeout(1000L).times(1);

        verify(mockOnAfterChange, vMode).run();
        verify(mockOnCacheMiss, vMode).accept(eq(k));
        verify(mockValueLoader, vMode).apply(eq(k));

        verifyNoInteractions(mockOnAfterBackgroundRefresh);
        verifyNoInteractions(mockOnBeforeRefresh);
        verifyNoInteractions(mockOnCacheHit);
        verifyNoInteractions(mockOnRefreshFailure);


        // -- Assert: state
        assertEquals(1, subject.size());
        assertFalse(subject.isEmpty());
        assertTrue(subject.containsKey(k));
    }

    @Test
    void testValueNotInCache_valueLoaderReturnsNull() {

        // -- Arrange
        final String k = "theKey";

        when(mockValueLoader.apply(eq(k)))
                .thenReturn(null);

        assumeFalse(subject.containsKey(k));

        // -- Act
        final Integer got = subject.get(k, false);

        // -- Assert: output
        assertNull(got);


        // -- Assert: callbacks
        final VerificationMode vMode = timeout(1000L).times(1);
        verify(mockOnCacheMiss, vMode).accept(eq(k));
        verify(mockValueLoader, vMode).apply(eq(k));

        verifyNoInteractions(mockOnAfterBackgroundRefresh);
        verifyNoInteractions(mockOnAfterChange);
        verifyNoInteractions(mockOnBeforeRefresh);
        verifyNoInteractions(mockOnCacheHit);
        verifyNoInteractions(mockOnRefreshFailure);

        // -- Assert: state
        assertFalse(subject.containsKey(k));
        assertTrue(subject.isEmpty());
    }

    // TODO: get (bypass)

    // TODO: BiConsumer<? super K, ? super V> onAfterRefresh

    // TODO: Consumer<? super K> onBeforeRefresh

    // TODO: Consumer<? super K> onCacheHit

    // TODO: Consumer<? super K> onCacheMiss

    // TODO: onAfterChange

    // TODO: onRefreshFailure

    // TODO: avoid duplicate refresh for key in brief period
}