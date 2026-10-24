package edu.cst3492.taide.service;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ServiceResourceCacheTest {
    private static final class Resource implements AutoCloseable {
        int closes;
        @Override public void close() { closes++; }
    }

    @Test void reusesCurrentRevisionAndClosesAfterLastRequest() throws Exception {
        var cache = new ServiceResourceCache<Resource>();
        Resource old = new Resource();
        var first = cache.acquire(1, () -> old);
        var second = cache.acquire(1, () -> { fail("same configuration must reuse resources"); return null; });
        assertSame(old, second.value());
        Resource replacement = new Resource();
        var third = cache.acquire(2, () -> replacement);
        assertEquals(0, old.closes, "changing settings must not close an in-flight request");
        first.close();
        assertEquals(0, old.closes);
        second.close();
        second.close();
        assertEquals(1, old.closes);
        third.close();
        cache.close();
        assertEquals(1, replacement.closes);
    }

    @Test void rebuildInvalidatesSameRevisionWithoutInterruptingReaders() throws Exception {
        var cache = new ServiceResourceCache<Resource>();
        Resource old = new Resource();
        var before = cache.acquire(1, () -> old);
        cache.invalidate();
        Resource fresh = new Resource();
        try (var after = cache.acquire(1, () -> fresh)) { assertSame(fresh, after.value()); }
        assertEquals(0, old.closes);
        before.close();
        assertEquals(1, old.closes);
        cache.close();
        assertEquals(1, fresh.closes);
    }

    @Test void failedRefreshKeepsOldResourceOwnedAndShutdownRejectsRequests() throws Exception {
        var cache = new ServiceResourceCache<Resource>();
        Resource old = new Resource();
        try (var ignored = cache.acquire(1, () -> old)) { }
        assertThrows(TaideException.class, () -> cache.acquire(2, () -> { throw new TaideException("missing model"); }));
        assertEquals(0, old.closes);
        cache.close();
        cache.close();
        assertEquals(1, old.closes);
        AtomicInteger creations = new AtomicInteger();
        assertThrows(TaideException.class, () -> cache.acquire(2, () -> { creations.incrementAndGet(); return new Resource(); }));
        assertEquals(0, creations.get());
    }
}
