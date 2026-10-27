## Code Review

You are reviewing the following code submitted as part of a task to implement an item cache in a highly concurrent application. The anticipated load includes: thousands of reads per second, hundreds of writes per second, tens of concurrent threads.
Your objective is to identify and explain the issues in the implementation that must be addressed before deploying the code to production. Please provide a clear explanation of each issue and its potential impact on production behaviour.

```kotlin
import java.util.concurrent.ConcurrentHashMap

class SimpleCache<K, V> {
    private val cache = ConcurrentHashMap<K, CacheEntry<V>>()
    private val ttlMs = 60000 // 1 minute
    
    data class CacheEntry<V>(val value: V, val timestamp: Long)
    
    fun put(key: K, value: V) {
        cache[key] = CacheEntry(value, System.currentTimeMillis())
    }
    
    fun get(key: K): V? {
        val entry = cache[key]
        if (entry != null) {
            if (System.currentTimeMillis() - entry.timestamp < ttlMs) {
                return entry.value
            }
        }
        return null
    }
    
    fun size(): Int {
        return cache.size
    }
}
```

---

## Review

> **Verdict: not production-ready.** The `ConcurrentHashMap` choice is sound and the read path is lock-free, but the cache has no eviction and no bounds, so it leaks memory whenever the key set changes. Issues are ordered by severity.

### 🔴 Blocking

**1. No eviction of expired entries**
- `get` returns `null` for stale entries but never removes them, and nothing sweeps the map.
- *Impact:* with hundreds of writes/sec and a changing key set the map grows until the heap is exhausted.
- *Fix:* remove on expired read (`cache.remove(key, entry)`) plus periodic cleanup, or use Caffeine with `expireAfterWrite`.

**2. No capacity bound**
- *Impact:* a burst of unique keys inside one minute can exhaust memory even with TTL cleanup.
- *Fix:* add a maximum size with an eviction policy (LRU/LFU).

**3. Wall-clock time used for TTL**
- `System.currentTimeMillis()` moves with NTP corrections and manual clock changes.
- *Impact:* a backward jump of X seconds keeps every entry alive X seconds longer; a forward jump expires everything at once.
- *Fix:* use the monotonic `System.nanoTime()`.

### 🟠 Should fix before deploy

**4. `size()` is misleading**
- Counts expired entries, so any metric or capacity decision based on it is wrong. On `ConcurrentHashMap` it is also only an estimate under concurrent writes.

**5. Nullable keys/values crash at runtime**
- `K` and `V` have no `: Any` upper bound, but `ConcurrentHashMap` rejects nulls, so `put(null, v)` or `put(k, null)` throws `NullPointerException`.
- *Fix:* declare `SimpleCache<K : Any, V : Any>`.
- *Trade-off:* this also forbids caching `null` as a value; use a sentinel or `Optional` if "known missing" must be cached.

**6. No stampede protection**
- *Impact:* when a hot entry expires, every concurrent reader misses at once and all recompute the value.
- *Fix:* provide `getOrLoad(key) { ... }` that stores a `CompletableFuture<V>` per key, so one thread loads and the rest wait on it, or use Caffeine's `LoadingCache`. Plain `computeIfAbsent` is not enough: expired entries are still in the map so it returns the stale one, and a slow loader inside it blocks the whole hash bin.

### 🟡 Best practices

**7. Hardcoded TTL** — `ttlMs` is a fixed `Int` literal with no constructor parameter, so it cannot be tuned per use case or in tests.

**8. Missing API surface** — no `remove`, `clear`, or `invalidate`, so callers cannot evict stale data after writing to the source of truth and may serve outdated values for up to a minute.

**9. No observability** — no hit/miss/eviction counters, so cache effectiveness and memory pressure cannot be monitored in production.
