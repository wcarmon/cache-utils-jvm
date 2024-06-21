package io.github.wcarmon.cache;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.verification.VerificationMode;

@Timeout(value = 20L, unit = SECONDS)
class ReadThruRefreshAheadCacheTest {

    ScheduledExecutorService executorService;
    CountDownLatch latch;
    Runnable mockOnAfterChange;
    Consumer<String> mockOnBeforeRefresh;
    Consumer<String> mockOnCacheHit;
    Consumer<String> mockOnCacheMiss;
    BiConsumer<String, Exception> mockOnValueLoadException;
    Function<String, Integer> mockValueLoader;
    ReadThruRefreshAheadCache<String, Integer> subject;

    @BeforeEach
    void setUp() {
        latch = new CountDownLatch(1);
        executorService = Executors.newScheduledThreadPool(2);
        mockOnAfterChange = mock(Runnable.class);
        mockOnBeforeRefresh = mock(Consumer.class);
        mockOnCacheHit = mock(Consumer.class);
        mockOnCacheMiss = mock(Consumer.class);
        mockOnValueLoadException = mock(BiConsumer.class);
        mockValueLoader = mock(Function.class);

        subject = buildSubject(true);
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
    void testFailingValueLoader_valueInCache() {
        // -- Arrange
        final String k = "theKey";

        final RuntimeException ex = new RuntimeException("oooo nooooo");
        doThrow(ex).when(mockValueLoader).apply(eq(k));

        subject.put(k, 3);
        assumeTrue(subject.containsKey(k));

        // -- Act
        final Integer got = subject.get(k, false);

        // -- Assert: output
        assertEquals(3, got);

        // -- Assert: callbacks
        final VerificationMode vMode = timeout(1000L).times(1);
        verify(mockOnBeforeRefresh, vMode).accept(eq(k));
        verify(mockOnCacheHit, vMode).accept(eq(k));
        verify(mockOnValueLoadException, vMode).accept(eq(k), eq(ex));
        verify(mockValueLoader, vMode).apply(eq(k));

        verifyNoInteractions(mockOnAfterChange);
        verifyNoInteractions(mockOnCacheMiss);

        // -- Assert: state
        assertTrue(subject.containsKey(k));
        assertFalse(subject.isEmpty());
        assertEquals(3, getInternalCacheEntry(k));
    }

    @Test
    void testFailingValueLoader_valueNotInCache() {
        // -- Arrange
        final String k = "theKey";

        final RuntimeException ex = new RuntimeException("oooo nooooo");
        doThrow(ex).when(mockValueLoader).apply(eq(k));

        // -- Act
        final Integer got = subject.get(k, false);

        // -- Assert: output
        assertNull(got);

        // -- Assert: callbacks
        final VerificationMode vMode = timeout(1000L).times(1);
        verify(mockOnCacheMiss, vMode).accept(eq(k));
        verify(mockOnValueLoadException, vMode).accept(eq(k), eq(ex));
        verify(mockValueLoader, vMode).apply(eq(k));

        verifyNoInteractions(mockOnAfterChange);
        verifyNoInteractions(mockOnBeforeRefresh);
        verifyNoInteractions(mockOnCacheHit);

        // -- Assert: state
        assertFalse(subject.containsKey(k));
        assertTrue(subject.isEmpty());
    }

    @Test
    @Timeout(value = 3, unit = SECONDS)
    void testValueInCache_bypassCache_valueLoaderReturnsNonNull() {
        // -- Arrange
        final String k = "theKey";

        when(mockValueLoader.apply(eq(k))).thenReturn(9);

        subject.put(k, 2);
        assumeTrue(subject.containsKey(k));

        // -- Act
        final Integer got = subject.get(k, true);

        // -- Assert: output
        assertEquals(9, got);

        // -- Assert: callbacks
        final VerificationMode vMode = timeout(1000L).times(1);
        verify(mockOnAfterChange, vMode).run();
        verify(mockOnCacheHit, vMode).accept(eq(k));
        verify(mockValueLoader, vMode).apply(eq(k));

        verifyNoInteractions(mockOnBeforeRefresh);
        verifyNoInteractions(mockOnCacheMiss);
        verifyNoInteractions(mockOnValueLoadException);

        // -- Assert: state
        assertTrue(subject.containsKey(k));
        assertEquals(9, getInternalCacheEntry(k));
    }

    @ParameterizedTest
    @Timeout(value = 3, unit = SECONDS)
    @ValueSource(booleans = {true, false})
    void testValueInCache_bypassCache_valueLoaderReturnsNull(
            boolean removeEntryWhenValueLoaderReturnsNull) {

        // -- Arrange

        // -- reconfigure subject
        subject = buildSubject(removeEntryWhenValueLoaderReturnsNull);

        final String k = "theKey";

        when(mockValueLoader.apply(eq(k))).thenReturn(null);

        subject.put(k, 2);
        assumeTrue(subject.containsKey(k));

        // -- Act
        final Integer got = subject.get(k, true);

        // -- Assert: output
        assertNull(got);

        // -- Assert: callbacks
        final VerificationMode vMode = timeout(1000L).times(1);
        verify(mockOnCacheHit, vMode).accept(eq(k));
        verify(mockValueLoader, vMode).apply(eq(k));

        if (removeEntryWhenValueLoaderReturnsNull) {
            verify(mockOnAfterChange, vMode).run();
        } else {
            verifyNoInteractions(mockOnAfterChange);
        }

        verifyNoInteractions(mockOnBeforeRefresh);
        verifyNoInteractions(mockOnCacheMiss);
        verifyNoInteractions(mockOnValueLoadException);

        // -- Assert: state
        if (removeEntryWhenValueLoaderReturnsNull) {
            assertFalse(subject.containsKey(k));

        } else {
            assertTrue(subject.containsKey(k));
            assertEquals(2, getInternalCacheEntry(k));
        }
    }

    @Test
    @Timeout(value = 3, unit = SECONDS)
    void testValueInCache_valueLoaderReturnsNonNull() {
        // -- Arrange
        final String k = "theKey";

        when(mockValueLoader.apply(eq(k))).thenReturn(8);

        subject.put(k, 7);
        assumeFalse(subject.isEmpty());
        assumeTrue(subject.containsKey(k));

        // -- Act
        final Integer got = subject.get(k, false);

        // -- Assert: output
        assertEquals(7, got);

        // -- Assert: callbacks
        final VerificationMode vMode = timeout(1000L).times(1);
        verify(mockOnAfterChange, vMode).run();
        verify(mockOnBeforeRefresh, vMode).accept(eq(k));
        verify(mockOnCacheHit, vMode).accept(eq(k));
        verify(mockValueLoader, vMode).apply(eq(k));

        verifyNoInteractions(mockOnCacheMiss);
        verifyNoInteractions(mockOnValueLoadException);

        // -- Assert: state
        assertEquals(1, subject.size());
        assertFalse(subject.isEmpty());
        assertTrue(subject.containsKey(k));
    }

    @ParameterizedTest
    @Timeout(value = 3, unit = SECONDS)
    @ValueSource(booleans = {true, false})
    void testValueInCache_valueLoaderReturnsNull(boolean removeEntryWhenValueLoaderReturnsNull) {

        // -- Arrange

        // -- reconfigure subject
        subject = buildSubject(removeEntryWhenValueLoaderReturnsNull);

        final String k = "theKey";

        when(mockValueLoader.apply(eq(k))).thenReturn(null);

        subject.put(k, 4);
        assumeFalse(subject.isEmpty());
        assumeTrue(subject.containsKey(k));

        // -- Act
        final Integer got = subject.get(k, false);

        // -- Assert: output
        assertEquals(4, got);

        // -- Assert: callbacks
        final VerificationMode vMode = timeout(1000L).times(1);
        verify(mockOnBeforeRefresh, vMode).accept(eq(k));
        verify(mockOnCacheHit, vMode).accept(eq(k));
        verify(mockValueLoader, vMode).apply(eq(k));

        verifyNoInteractions(mockOnCacheMiss);
        verifyNoInteractions(mockOnValueLoadException);

        if (removeEntryWhenValueLoaderReturnsNull) {
            verify(mockOnAfterChange, vMode).run();
        } else {
            verifyNoInteractions(mockOnAfterChange);
        }

        // -- Assert: state
        if (removeEntryWhenValueLoaderReturnsNull) {
            assertFalse(subject.containsKey(k));

        } else {
            assertTrue(subject.containsKey(k));
            assertEquals(4, getInternalCacheEntry(k));
        }
    }

    @Test
    @Timeout(value = 3, unit = SECONDS)
    void testValueNotInCache_bypassCache_valueLoaderReturnsNonNull() {
        // -- Arrange
        final String k = "theKey";

        when(mockValueLoader.apply(eq(k))).thenReturn(6);

        // -- Act
        final Integer got = subject.get(k, true);

        // -- Assert: output
        assertEquals(6, got);

        // -- Assert: callbacks
        final VerificationMode vMode = timeout(1000L).times(1);
        verify(mockOnAfterChange, vMode).run();
        verify(mockOnCacheMiss, vMode).accept(eq(k));
        verify(mockValueLoader, vMode).apply(eq(k));

        verifyNoInteractions(mockOnBeforeRefresh);
        verifyNoInteractions(mockOnCacheHit);
        verifyNoInteractions(mockOnValueLoadException);

        // -- Assert: state
        assertFalse(subject.isEmpty());
        assertTrue(subject.containsKey(k));

        assertEquals(6, getInternalCacheEntry(k));
    }

    @Test
    @Timeout(value = 3, unit = SECONDS)
    void testValueNotInCache_bypassCache_valueLoaderReturnsNull() {
        // -- Arrange
        final String k = "theKey";

        when(mockValueLoader.apply(eq(k))).thenReturn(null);

        // -- Act
        final Integer got = subject.get(k, true);

        // -- Assert: output
        assertNull(got);

        // -- Assert: callbacks
        final VerificationMode vMode = timeout(1000L).times(1);
        verify(mockOnCacheMiss, vMode).accept(eq(k));
        verify(mockValueLoader, vMode).apply(eq(k));

        verifyNoInteractions(mockOnAfterChange);
        verifyNoInteractions(mockOnBeforeRefresh);
        verifyNoInteractions(mockOnCacheHit);
        verifyNoInteractions(mockOnValueLoadException);

        // -- Assert: state
        assertTrue(subject.isEmpty());
        assertFalse(subject.containsKey(k));
    }

    @Test
    @Timeout(value = 3, unit = SECONDS)
    void testValueNotInCache_valueLoaderReturnsNonNull() {
        // -- Arrange
        final String k = "theKey";
        when(mockValueLoader.apply(eq(k))).thenReturn(3);

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

        verifyNoInteractions(mockOnBeforeRefresh);
        verifyNoInteractions(mockOnCacheHit);
        verifyNoInteractions(mockOnValueLoadException);

        // -- Assert: state
        assertEquals(1, subject.size());
        assertFalse(subject.isEmpty());
        assertTrue(subject.containsKey(k));
    }

    @Test
    @Timeout(value = 3, unit = SECONDS)
    void testValueNotInCache_valueLoaderReturnsNull() {

        // -- Arrange
        final String k = "theKey";

        when(mockValueLoader.apply(eq(k))).thenReturn(null);

        assumeFalse(subject.containsKey(k));

        // -- Act
        final Integer got = subject.get(k, false);

        // -- Assert: output
        assertNull(got);

        // -- Assert: callbacks
        final VerificationMode vMode = timeout(1000L).times(1);
        verify(mockOnCacheMiss, vMode).accept(eq(k));
        verify(mockValueLoader, vMode).apply(eq(k));

        verifyNoInteractions(mockOnAfterChange);
        verifyNoInteractions(mockOnBeforeRefresh);
        verifyNoInteractions(mockOnCacheHit);
        verifyNoInteractions(mockOnValueLoadException);

        // -- Assert: state
        assertFalse(subject.containsKey(k));
        assertTrue(subject.isEmpty());
    }

    private ReadThruRefreshAheadCache<String, Integer> buildSubject(
            boolean removeEntryWhenValueLoaderReturnsNull) {
        return ReadThruRefreshAheadCache.<String, Integer>builder()
                .capacity(64)
                .executorService(executorService)
                .onAfterChange(mockOnAfterChange)
                .onBeforeRefresh(mockOnBeforeRefresh)
                .onCacheHit(mockOnCacheHit)
                .onCacheMiss(mockOnCacheMiss)
                .onValueLoadException(mockOnValueLoadException)
                .removeEntryWhenValueLoaderReturnsNull(removeEntryWhenValueLoaderReturnsNull)
                .valueLoader(mockValueLoader)
                .build();
    }

    @Nullable
    private Integer getInternalCacheEntry(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key is required");
        }

        try {
            final Field cacheField = ReadThruRefreshAheadCache.class.getDeclaredField("cache");
            cacheField.setAccessible(true);
            final Map<String, CacheEntry<Integer>> mapEntry =
                    (Map<String, CacheEntry<Integer>>) cacheField.get(subject);

            final CacheEntry<Integer> cacheEntry = mapEntry.get(key);
            if (cacheEntry == null) {
                return null;
            }

            return cacheEntry.value();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // TODO: avoid duplicate refresh for key in brief period

    // TODO: test TTL: entry removed after TTL
    // TODO: test TTL: updated entry retained at first TTL period, then removed after second TTL
    // period
}
