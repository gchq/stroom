package stroom.cache.impl;

import stroom.cache.api.StroomCache;
import stroom.cache.shared.CacheInfo;
import stroom.util.NullSafe;
import stroom.util.cache.CacheConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.PropertyPath;
import stroom.util.time.StroomDuration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.StampedLock;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

abstract class AbstractStroomCache<K, V> implements StroomCache<K, V> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractStroomCache.class);

    private final String name;
    private final Supplier<CacheConfig> cacheConfigSupplier;
    private final BiConsumer<K, V> removalNotificationConsumer;

    // This is not re-entrant so need to be careful we don't try to lock twice
    private final StampedLock stampedLock = new StampedLock();

    // These two must be changed under the protection of stampedLock. Exclusive access,
    // i.e. a write-lock is only needed for operations that change the reference attached
    // to these variables.
    protected volatile Cache<K, V> cache = null;
    protected volatile Caffeine<K, V> cacheBuilder = null;

    public AbstractStroomCache(final String name,
                               final Supplier<CacheConfig> cacheConfigSupplier,
                               final BiConsumer<K, V> removalNotificationConsumer) {

        LOGGER.debug(() -> LogUtil.message("Creating cache {} from config {} ({}), " +
                        "(has removalNotificationConsumer: {})",
                name,
                getBasePropertyPath(),
                cacheConfigSupplier.get(),
                removalNotificationConsumer != null));

        this.removalNotificationConsumer = removalNotificationConsumer;
        this.name = name;
        this.cacheConfigSupplier = cacheConfigSupplier;
    }

    abstract Cache<K, V> createCacheFromBuilder(final Caffeine<K, V> cacheBuilder);

    public void rebuild() {
        LOGGER.trace(() -> buildMessage("rebuild"));

        // Now swap out the existing cache/builder under an exclusive lock
        doUnderWriteLock(() -> {
            final CacheConfig cacheConfig = cacheConfigSupplier.get();

            if (cache != null) {
                // Don't log initial cache creation, only explicit rebuilds
                LOGGER.info("Clearing and rebuilding cache '{}' (Property path: '{}') with config: {}",
                        name, getBasePropertyPath(), cacheConfig);
                CacheUtil.clear(cache);
            }

            final Caffeine newCacheBuilder = Caffeine.newBuilder();
            newCacheBuilder.recordStats();

            NullSafe.consume(cacheConfig.getMaximumSize(), newCacheBuilder::maximumSize);
            NullSafe.consume(
                    cacheConfig.getExpireAfterAccess(),
                    StroomDuration::getDuration,
                    newCacheBuilder::expireAfterAccess);
            NullSafe.consume(
                    cacheConfig.getExpireAfterWrite(),
                    StroomDuration::getDuration,
                    newCacheBuilder::expireAfterWrite);

            if (removalNotificationConsumer != null) {
                final RemovalListener<K, V> removalListener = (key, value, cause) -> {
                    final Supplier<String> messageSupplier = () -> "Removal notification for cache '" +
                            name +
                            "' (key=" +
                            key +
                            ", value=" +
                            value +
                            ", cause=" +
                            cause + ")";

                    if (cause == RemovalCause.SIZE) {
                        LOGGER.warn(() -> "Cache reached size limit '" + name + "'");
                        LOGGER.debug(messageSupplier);
                    } else {
                        LOGGER.trace(messageSupplier);
                    }
                    removalNotificationConsumer.accept(key, value);
                };
                newCacheBuilder.removalListener(removalListener);
            }

            final Cache<K, V> newCache = createCacheFromBuilder(newCacheBuilder);

            LOGGER.debug("Assigning new cache and builder instances");
            this.cacheBuilder = newCacheBuilder;

            // Now create and set the cache
            this.cache =  newCache;
        });
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public V get(final K key) {
        LOGGER.trace(() -> buildMessage("get", key));
        return getWithCacheUnderOptimisticReadLock(cache ->
                cache.getIfPresent(key));
    }

    @Override
    public V get(final K key, final Function<K, V> valueProvider) {
        LOGGER.trace(() -> buildMessage("get", key));
        return getWithCacheUnderOptimisticReadLock(cache ->
                cache.get(key, valueProvider));
    }

    @Override
    public void put(final K key, final V value) {
        LOGGER.trace(() -> buildMessage("put", key));

        // A read lock as we are not modifying the cache reference, just the innards
        // of the cache
        doWithCacheUnderOptimisticReadLock(cache ->
                cache.put(key, value));
    }

    @Override
    public Optional<V> getOptional(final K key) {
        LOGGER.trace(() -> buildMessage("getOptional", key));
        return getWithCacheUnderOptimisticReadLock(cache ->
                Optional.ofNullable(cache.getIfPresent(key)));
    }


    @Override
    public boolean containsKey(final K key) {
        LOGGER.trace(() -> buildMessage("containsKey", key));
        // Can't use asMap().containsKey() as that will call the loadfunc
        return getWithCacheUnderOptimisticReadLock(cache ->
                cache.asMap().containsKey(key));
    }

    @Override
    public Set<K> keySet() {
        return getWithCacheUnderOptimisticReadLock(cache ->
                new HashSet<>(cache.asMap().keySet()));
    }

    @Override
    public List<V> values() {
        return getWithCacheUnderOptimisticReadLock(cache ->
                new ArrayList<>(cache.asMap().values()));
    }

    @Override
    public void forEach(final BiConsumer<K, V> entryConsumer) {
        LOGGER.trace(() -> buildMessage("invalidateEntries"));
        Objects.requireNonNull(entryConsumer);
        // Use a full read lock as we don't really know what consumer will do,
        // so we don't really want it running twice.
        doWithCacheUnderReadLock(cache ->
                cache.asMap().forEach(entryConsumer));
    }

    @Override
    public void invalidate(final K key) {
        LOGGER.trace(() -> buildMessage("invalidate", key));
        doWithCacheUnderOptimisticReadLock(cache ->
                cache.invalidate(key));
    }

    @Override
    public void invalidateEntries(final BiPredicate<K, V> entryPredicate) {
        LOGGER.trace(() -> buildMessage("invalidateEntries"));
        Objects.requireNonNull(entryPredicate);

        doWithCacheUnderOptimisticReadLock(cache -> {
            cache.asMap()
                    .entrySet()
                    .stream()
                    .filter(entry -> entryPredicate.test(entry.getKey(), entry.getValue()))
                    .forEach(entry -> {
                        LOGGER.trace(() -> buildMessage("invalidateEntries", entry.getKey()));
                        cache.invalidate(entry.getKey());
                    });
        });
    }

    @Override
    public void remove(final K key) {
        LOGGER.trace(() -> buildMessage("remove", key));
        doWithCacheUnderOptimisticReadLock(cache -> {
            cache.invalidate(key);
            cache.cleanUp();
        });
    }

    @Override
    public void evictExpiredElements() {
        LOGGER.trace(() -> buildMessage("evictExpiredElements"));
        doWithCacheUnderOptimisticReadLock(Cache::cleanUp);
    }

    @Override
    public long size() {
        return getWithCacheUnderOptimisticReadLock(Cache::estimatedSize);
    }

    @Override
    public void clear() {
        LOGGER.trace(() -> buildMessage("clear"));

        // Read lock as we are not changing the reference to the cache.
        // The cache has its own concurrency protection at the entry level.
        doWithCacheUnderOptimisticReadLock(CacheUtil::clear);
    }

    @Override
    public CacheInfo getCacheInfo() {
        final PropertyPath basePropertyPath = getBasePropertyPath();

        return getWithCacheUnderOptimisticReadLock(cache -> {
            final Map<String, String> map = new HashMap<>();
            map.put("Entries", String.valueOf(cache.estimatedSize()));
            // The lock covers cacheBuilder too
            addEntries(map, cacheBuilder.toString());
            addEntries(map, cache.stats().toString());

            map.forEach((k, v) -> {
                if (k.startsWith("Expire") || k.equals("TotalLoadTime")) {
                    convertNanosToDuration(map, k, v);
                }
            });

            // We don't make use of Weighers in the cache so the weight stats are meaningless
            map.remove("EvictionWeight");

            return new CacheInfo(name, basePropertyPath, map);
        });
    }

    void doWithCacheUnderWriteLock(final Consumer<Cache<K, V>> work) {
        Objects.requireNonNull(work);

        final long stamp;
        try {
            LOGGER.trace("Getting write lock");
            stamp = stampedLock.writeLockInterruptibly();
            LOGGER.trace("Got write lock with stamp {}", stamp);
            try {
                work.accept(cache);
            } finally {
                LOGGER.trace("Releasing write lock with stamp {}", stamp);
                stampedLock.unlockWrite(stamp);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted waiting for write lock on cache " + name);
        }
    }

    private void doWithCacheUnderReadLock(final Consumer<Cache<K, V>> work) {
        Objects.requireNonNull(work);
        final long stamp;
        try {
            LOGGER.trace("Getting read lock");
            stamp = stampedLock.readLockInterruptibly();
            LOGGER.trace("Got read lock with stamp {}", stamp);
            try {
                work.accept(cache);
            } finally {
                LOGGER.trace("Releasing read lock with stamp {}", stamp);
                stampedLock.unlockRead(stamp);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted waiting for read lock on cache " + name);
        }
    }

    private <T> T getWithCacheUnderReadLock(final Function<Cache<K, V>, T> work) {
        Objects.requireNonNull(work);
        final long stamp;
        try {
            LOGGER.trace("Getting read lock");
            stamp = stampedLock.readLockInterruptibly();
            LOGGER.trace("Got read lock with stamp {}", stamp);
            try {
                return work.apply(cache);
            } finally {
                LOGGER.trace("Releasing read lock with stamp {}", stamp);
                stampedLock.unlockRead(stamp);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted waiting for read lock on cache " + name);
        }
    }

    /**
     * Use the cache to get something. work may be called multiple times so must be
     * idempotent. Initially attempts to perform work without any locking.
     * If anything else has obtained an exclusive lock in that time it will retry
     * under a full read lock.
     *
     * @param work MUST be idempotent
     */
    <T> T getWithCacheUnderOptimisticReadLock(final Function<Cache<K, V>, T> work) {
        Objects.requireNonNull(work);
        T result = null;
        final long stamp = stampedLock.tryOptimisticRead();
        LOGGER.trace("Got optimistic stamp {}", stamp);

        if (stamp == 0) {
            // Another thread holds an exclusive lock, so block and wait for a read lock
            result = getWithCacheUnderReadLock(work);
        } else {
            try {
                result = work.apply(cache);
            } catch (Exception e) {
                // e.g. cache may have been cleared while we used it
                LOGGER.debug("Error performing work under optimistic lock on cache "
                        + name + ": " + e.getMessage(), e);
            }

            if (!stampedLock.validate(stamp)) {
                LOGGER.trace("Stamp {} is invalid, use read lock instead", stamp);
                // Another thread got an exclusive lock since we got the stamp so retry under
                // a read lock, blocking if anything holds an exclusive lock.
                result = getWithCacheUnderReadLock(work);
            } else {
                LOGGER.trace("Optimistic action succeeded");
            }
        }
        return result;
    }

    /**
     * Use the cache to do something. work may be called multiple times so must be
     * idempotent. Initially attempts to perform work without any locking.
     * If anything else has obtained an exclusive lock in that time it will retry
     * under a full read lock.
     *
     * @param work MUST be idempotent
     */
    private void doWithCacheUnderOptimisticReadLock(final Consumer<Cache<K, V>> work) {
        Objects.requireNonNull(work);
        final long stamp = stampedLock.tryOptimisticRead();

        if (stamp == 0) {
            // Another thread holds an exclusive lock, so block and wait for a read lock
            doWithCacheUnderReadLock(work);
        } else {
            try {
                work.accept(cache);
            } catch (Exception e) {
                // e.g. cache may have been cleared while we used it
                LOGGER.debug("Error performing work under optimistic lock on cache "
                        + name + ": " + e.getMessage(), e);
            }

            if (!stampedLock.validate(stamp)) {
                LOGGER.trace("Stamp {} is invalid, use read lock instead", stamp);
                // Another thread got an exclusive lock since we got the stamp so retry under
                // a read lock, blocking if anything holds an exclusive lock.
                doWithCacheUnderReadLock(work);
            } else {
                LOGGER.trace("Optimistic action succeeded");
            }
        }
    }

    private void doUnderWriteLock(final Runnable work) {
        final long stamp;
        try {
            stamp = stampedLock.writeLockInterruptibly();
            try {
                work.run();
            } finally {
                stampedLock.unlockWrite(stamp);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted waiting for write lock on cache " + name);
        }
    }

    private void convertNanosToDuration(final Map<String, String> map,
                                        final String k,
                                        final String v) {
        try {
            final long nanos = Long.parseLong(v);
            map.put(k, ModelStringUtil.formatDurationString(
                    nanos / 1_000_000, true));

        } catch (final RuntimeException e) {
            // Ignore.
        }
    }

    private void addEntries(final Map<String, String> map, String string) {
        if (string != null && !string.isEmpty()) {

            string = string.replaceAll("^[^{]*\\{", "");
            string = string.replaceAll("}.*", "");

            Arrays.stream(string.split(",")).forEach(pair -> {
                final String[] kv = pair.split("=");
                if (kv.length == 2) {
                    String key = kv[0].replaceAll("\\W", "");
                    String value = kv[1].replaceAll("\\D", "");
                    if (key.length() > 0) {
                        final char[] chars = key.toCharArray();
                        chars[0] = Character.toUpperCase(chars[0]);
                        key = new String(chars, 0, chars.length);
                    }

                    map.put(key, value);
                }
            });
        }
    }

    String buildMessage(final String methodName) {
        return methodName + "() - cache: '" + name + "'";
    }

    String buildMessage(final String methodName, final K key) {
        return methodName + "() - cache: '" + name + "', key: " + key;
    }

    @Override
    public PropertyPath getBasePropertyPath() {
        // Don't need to do this under lock
        return cacheConfigSupplier.get().getBasePath();
    }

    @Override
    public String toString() {
        return "AbstractICache{" +
                "name='" + name + "', " +
                "basePath='" + getBasePropertyPath() + '\'' +
                '}';
    }
}
