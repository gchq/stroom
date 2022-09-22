package stroom.cache.impl;

import stroom.cache.api.ICache;
import stroom.cache.shared.CacheInfo;
import stroom.util.cache.CacheConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.PropertyPath;

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

abstract class AbstractICache<K, V> implements ICache<K, V> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractICache.class);

    protected volatile Cache<K, V> cache = null;
    protected volatile Caffeine<K, V> cacheBuilder = null;
    private final String name;
    private final Supplier<CacheConfig> cacheConfigSupplier;
    protected final BiConsumer<K, V> removalNotificationConsumer;
    // This is not re-entrant so need to be careful we don't try to lock twice
    private StampedLock stampedLock = new StampedLock();

    public AbstractICache(final String name,
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

    abstract Cache<K, V> createCacheFromBuilder();

    void doWithCacheUnderWriteLock(final Consumer<Cache<K, V>> work) {
        Objects.requireNonNull(work);

        final long stamp;
        try {
            stamp = stampedLock.writeLockInterruptibly();
            try {
                work.accept(cache);
            } finally {
                stampedLock.unlockWrite(stamp);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted waiting for write lock on cache " + name);
        }
    }

    void doWithCacheUnderReadLock(final Consumer<Cache<K, V>> work) {
        Objects.requireNonNull(work);
        final long stamp;
        try {
            stamp = stampedLock.readLockInterruptibly();
            try {
                work.accept(cache);
            } finally {
                stampedLock.unlockRead(stamp);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted waiting for read lock on cache " + name);
        }
    }

    <T> T getWithCacheUnderReadLock(final Function<Cache<K, V>, T> work) {
        Objects.requireNonNull(work);
        final long stamp;
        try {
            stamp = stampedLock.readLockInterruptibly();
            try {
                return work.apply(cache);
            } finally {
                stampedLock.unlockRead(stamp);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted waiting for read lock on cache " + name);
        }
    }

    /**
     * Use the cache to get something. work may be called multiple times so must be
     * idempotent. Attempts to get T without any locking. If anything else has
     * obtained an exclusive lock in that time it will retry under a read lock.
     *
     * @param work MUST be idempotent
     */
    <T> T getWithCacheUnderReadLockOptimistically(final Function<Cache<K, V>, T> work) {
        Objects.requireNonNull(work);
        T result = null;
        final long stamp = stampedLock.tryOptimisticRead();

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
                // Another thread got an exclusive lock since we got the stamp so retry under
                // a read lock, blocking if anything holds an exclusive lock.
                result = getWithCacheUnderReadLock(work);
            }
        }
        return result;
    }

    /**
     * Use the cache to do something. work may be called multiple times so must be
     * idempotent. Attempts to perform work without any locking. If anything else has
     * obtained an exclusive lock in that time it will retry under a read lock.
     *
     * @param work MUST be idempotent
     */
    void doWithCacheUnderReadLockOptimistically(final Consumer<Cache<K, V>> work) {
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
                // Another thread got an exclusive lock since we got the stamp so retry under
                // a read lock, blocking if anything holds an exclusive lock.
                doWithCacheUnderReadLock(work);
            }
        }
    }

    void doUnderWriteLock(final Runnable work) {
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

    public void rebuild() {
        LOGGER.trace(() -> buildMessage("rebuild"));

        final CacheConfig cacheConfig = cacheConfigSupplier.get();
        final Caffeine cacheBuilder = Caffeine.newBuilder();
        cacheBuilder.recordStats();

        if (cacheConfig.getMaximumSize() != null) {
            cacheBuilder.maximumSize(cacheConfig.getMaximumSize());
        }
        if (cacheConfig.getExpireAfterAccess() != null) {
            cacheBuilder.expireAfterAccess(cacheConfig.getExpireAfterAccess().getDuration());
        }
        if (cacheConfig.getExpireAfterWrite() != null) {
            cacheBuilder.expireAfterWrite(cacheConfig.getExpireAfterWrite().getDuration());
        }
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
            cacheBuilder.removalListener(removalListener);
        }

        doUnderWriteLock(() -> {
            if (cache != null) {
                CacheUtil.clear(cache);
            }

            LOGGER.debug("Assigning new cache and builder instances");
            this.cacheBuilder = cacheBuilder;

            // Now create and set the cache
            this.cache = createCacheFromBuilder();
        });
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public V get(final K key) {
        LOGGER.trace(() -> buildMessage("get", key));
        return getWithCacheUnderReadLockOptimistically(cache ->
                cache.getIfPresent(key));
    }

    @Override
    public V get(final K key, final Function<K, V> valueProvider) {
        LOGGER.trace(() -> buildMessage("get", key));
        return getWithCacheUnderReadLockOptimistically(cache ->
                cache.get(key, valueProvider));
    }

    @Override
    public void put(final K key, final V value) {
        LOGGER.trace(() -> buildMessage("put", key));

        // A read lock as we are not modifying the cache reference, just the innards
        // of the cache
        doWithCacheUnderReadLockOptimistically(cache ->
                cache.put(key, value));
    }

    @Override
    public Optional<V> getOptional(final K key) {
        LOGGER.trace(() -> buildMessage("getOptional", key));
        return getWithCacheUnderReadLockOptimistically(cache ->
                Optional.ofNullable(cache.getIfPresent(key)));
    }


    @Override
    public boolean containsKey(final K key) {
        LOGGER.trace(() -> buildMessage("containsKey", key));
        // Can't use asMap().containsKey() as that will call the loadfunc
        return getWithCacheUnderReadLockOptimistically(cache ->
                cache.asMap().containsKey(key));
    }

    @Override
    public Set<K> keySet() {
        return getWithCacheUnderReadLockOptimistically(cache ->
                new HashSet<>(cache.asMap().keySet()));
    }

    @Override
    public List<V> values() {
        return getWithCacheUnderReadLockOptimistically(cache ->
                new ArrayList<>(cache.asMap().values()));
    }

    @Override
    public void forEach(final BiConsumer<K, V> entryConsumer) {
        LOGGER.trace(() -> buildMessage("invalidateEntries"));
        Objects.requireNonNull(entryConsumer);
        doWithCacheUnderReadLock(cache ->
                cache.asMap().forEach(entryConsumer));
    }

    @Override
    public void invalidate(final K key) {
        LOGGER.trace(() -> buildMessage("invalidate", key));
        // TODO: 16/09/2022 Assumes invalidate is idempotent????
        doWithCacheUnderReadLockOptimistically(cache ->
                cache.invalidate(key));
    }

    @Override
    public void invalidateEntries(final BiPredicate<K, V> entryPredicate) {
        LOGGER.trace(() -> buildMessage("invalidateEntries"));
        Objects.requireNonNull(entryPredicate);

        // TODO: 16/09/2022 Assumes invalidate is idempotent????
        doWithCacheUnderReadLockOptimistically(cache -> {
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
        invalidate(key);
        doWithCacheUnderReadLock(Cache::cleanUp);
    }

    @Override
    public void evictExpiredElements() {
        LOGGER.trace(() -> buildMessage("evictExpiredElements"));
        doWithCacheUnderReadLock(Cache::cleanUp);
    }

    @Override
    public long size() {
        return getWithCacheUnderReadLockOptimistically(Cache::estimatedSize);
    }

    @Override
    public void clear() {
        LOGGER.trace(() -> buildMessage("clear"));

        // Read lock as we are not changing the reference to the cache
        doWithCacheUnderReadLockOptimistically(CacheUtil::clear);
    }

    @Override
    public CacheInfo getCacheInfo() {
        final PropertyPath basePropertyPath = getBasePropertyPath();

        return getWithCacheUnderReadLockOptimistically(cache -> {
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
