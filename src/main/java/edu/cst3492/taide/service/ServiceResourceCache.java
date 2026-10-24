package edu.cst3492.taide.service;

/** Keeps an in-flight request alive while retiring resources after a settings/index change. */
final class ServiceResourceCache<T extends AutoCloseable> implements AutoCloseable {
    @FunctionalInterface interface Factory<T> { T create() throws TaideException; }
    private Entry current;
    private boolean closed;

    private final class Entry {
        final long revision;
        final T value;
        int users;
        boolean retired;
        Entry(long revision, T value) { this.revision = revision; this.value = value; }
    }

    final class Lease implements AutoCloseable {
        private Entry entry;
        private Lease(Entry entry) { this.entry = entry; }
        T value() { return entry.value; }
        @Override public void close() {
            synchronized (ServiceResourceCache.this) {
                if (entry == null) return;
                entry.users--;
                releaseRetired(entry);
                entry = null;
            }
        }
    }

    synchronized Lease acquire(long revision, Factory<T> factory) throws TaideException {
        if (closed) throw new TaideException("TAIDE project has been closed.");
        if (current == null || current.revision != revision) {
            // Build first: a failed refresh must not leak the previous resource.
            T value = factory.create();
            retireCurrent();
            current = new Entry(revision, value);
        }
        current.users++;
        return new Lease(current);
    }

    synchronized void invalidate() { retireCurrent(); }

    @Override public synchronized void close() {
        closed = true;
        retireCurrent();
    }

    private void retireCurrent() {
        if (current == null) return;
        current.retired = true;
        releaseRetired(current);
        current = null;
    }

    private void releaseRetired(Entry entry) {
        if (entry.retired && entry.users == 0) {
            try { entry.value.close(); }
            catch (Exception ignored) { /* Native cleanup must not replace the request result. */ }
        }
    }
}
