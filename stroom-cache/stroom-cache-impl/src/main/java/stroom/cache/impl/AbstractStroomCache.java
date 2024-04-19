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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;

abstract class AbstractStroomCache<K, V> implements StroomCache<K, V> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractStroomCache.class);

    private final String name;
    private final Supplier<CacheConfig> cacheConfigSupplier;
    private final BiConsumer<K, V> removalNotificationConsumer;
    private final AtomicInteger reachedSizeLimitCount = new AtomicInteger();

    protected volatile CacheHolder<K, V> cacheHolder = null;

    public AbstractStroomCache(final String name,
                               final Supplier<CacheConfig> cacheConfigSupplier,
                               final BiConsumer<K, V> removalNotificationConsumer) {

        Objects.requireNonNull(name);
        Objects.requireNonNull(cacheConfigSupplier);

        LOGGER.debug(() -> LogUtil.message("Creating cache {} from config {} ({}), " +
                        "(has removalNotificationConsumer: {})",
                name,
                NullSafe.getOrElseGet(
                        cacheConfigSupplier.get(),
                        CacheConfig::getBasePath,
                        PropertyPath::blank),
                cacheConfigSupplier.get(),
                removalNotificationConsumer != null));

        // Note: when a rebuild happens, both the old and new cache instances will have the
        // same removalNotificationConsumer so it may get called more than expected
        this.removalNotificationConsumer = removalNotificationConsumer;
        this.name = name;
        this.cacheConfigSupplier = cacheConfigSupplier;
    }

    /**
     * Subclasses should implement this method to add to the cacheBuilder as required
     * and return a cache built from cacheBuilder. The builder already has a
     * removalNotificationConsumer if applicable and recordStats has been set.
     */
    abstract Cache<K, V> createCacheFromBuilder(final Caffeine<K, V> cacheBuilder);

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public synchronized void rebuild() {
        LOGGER.trace(() -> buildMessage("rebuild"));

        final CacheHolder<K, V> existingCacheHolder = this.cacheHolder;
        final CacheConfig newCacheConfig = cacheConfigSupplier.get();

        if (existingCacheHolder != null
                && Objects.equals(existingCacheHolder.getCacheConfig(), newCacheConfig)) {
            LOGGER.info("Clearing cache '{}' (Property path: '{}'). No config changed.",
                    name, getBasePropertyPath());
            CacheUtil.clear(getCache());
        } else {
            if (existingCacheHolder != null) {
                // Don't log initial cache creation, only explicit rebuilds
                LOGGER.info("Rebuilding cache '{}' from config (Property path: '{}'). newCacheConfig: {}.",
                        name, getBasePropertyPath(), newCacheConfig);
            }
            final Caffeine newCacheBuilder = Caffeine.newBuilder();
            newCacheBuilder.recordStats();

            NullSafe.consume(newCacheConfig.getMaximumSize(), newCacheBuilder::maximumSize);
            NullSafe.consume(
                    newCacheConfig.getExpireAfterAccess(),
                    StroomDuration::getDuration,
                    newCacheBuilder::expireAfterAccess);
            NullSafe.consume(
                    newCacheConfig.getExpireAfterWrite(),
                    StroomDuration::getDuration,
                    newCacheBuilder::expireAfterWrite);
            NullSafe.consume(
                    newCacheConfig.getRefreshAfterWrite(),
                    StroomDuration::getDuration,
                    newCacheBuilder::refreshAfterWrite);

            if (removalNotificationConsumer != null) {
                final RemovalListener<K, V> removalListener = createRemovalListener();
                newCacheBuilder.removalListener(removalListener);
            }

            final Cache<K, V> newCache = createCacheFromBuilder(newCacheBuilder);

            final CacheHolder<K, V> newCacheHolder = new CacheHolder<>(newCache, newCacheBuilder, newCacheConfig);
            LOGGER.debug("Assigning new cacheHolder instance");
            this.cacheHolder = newCacheHolder;
        }
    }

    protected Cache<K, V> getCache() {
        return cacheHolder.getCache();
    }

    private Caffeine<K, V> getCacheBuilder() {
        return cacheHolder.getCacheBuilder();
    }

    private RemovalListener<K, V> createRemovalListener() {
        // Wrap the supplied removalNotificationConsumer with a RemovalListener that
        // logs the reason for the removal
        return (key, value, cause) -> {
            final Supplier<String> messageSupplier = () -> "Removal notification for cache '" +
                    name +
                    "' (key=" +
                    key +
                    ", value=" +
                    value +
                    ", cause=" +
                    cause + ")";

            if (cause == RemovalCause.SIZE) {
                reachedSizeLimitCount.incrementAndGet();
                LOGGER.debug(() -> "Cache reached size limit '" + name + "'");
            }
            LOGGER.trace(messageSupplier);
            removalNotificationConsumer.accept(key, value);
        };
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public V get(final K key) {
        LOGGER.trace(() -> buildMessage("get", key));
        return getCache().getIfPresent(key);
    }

    @Override
    public V get(final K key, final Function<K, V> valueProvider) {
        LOGGER.trace(() -> buildMessage("get", key));
        return getCache().get(key, valueProvider);
    }

    @Override
    public void put(final K key, final V value) {
        LOGGER.trace(() -> buildMessage("put", key));

        // A read lock as we are not modifying the cache reference, just the innards
        // of the cache
        getCache().put(key, value);
    }

    @Override
    public Optional<V> getIfPresent(final K key) {
        LOGGER.trace(() -> buildMessage("getOptional", key));
        return Optional.ofNullable(getCache().getIfPresent(key));
    }


    @Override
    public boolean containsKey(final K key) {
        LOGGER.trace(() -> buildMessage("containsKey", key));
        // Can't use asMap().containsKey() as that will call the loadfunc
        return getCache().asMap().containsKey(key);
    }

    @Override
    public Set<K> keySet() {
        // Defensive copy
        return Collections.unmodifiableSet(getCache().asMap().keySet());
    }

    @Override
    public Collection<V> values() {
        // Defensive copy
        return Collections.unmodifiableCollection(getCache().asMap().values());
    }

    @Override
    public Map<K, V> asMap() {
        return Collections.unmodifiableMap(getCache().asMap());
    }

    @Override
    public void forEach(final BiConsumer<K, V> entryConsumer) {
        LOGGER.trace(() -> buildMessage("invalidateEntries"));
        Objects.requireNonNull(entryConsumer);
        // Use a full read lock as we don't really know what consumer will do,
        // so we don't really want it running twice.
        getCache().asMap().forEach(entryConsumer);
    }

    @Override
    public void invalidate(final K key) {
        LOGGER.trace(() -> buildMessage("invalidate", key));
        getCache().invalidate(key);
    }

    @Override
    public void invalidateEntries(final BiPredicate<K, V> entryPredicate) {
        LOGGER.trace(() -> buildMessage("invalidateEntries"));
        Objects.requireNonNull(entryPredicate);

        final Cache<K, V> cache = getCache();
        final ConcurrentMap<K, V> cacheAsMap = cache.asMap();

        final List<K> keysToRemove = cacheAsMap.entrySet()
                .stream()
                .filter(entry -> {
                    LOGGER.trace(() ->
                            buildMessage(
                                    "invalidateEntries",
                                    entry.getKey(),
                                    "ID: " + System.identityHashCode(getCache())));
                    return entryPredicate.test(entry.getKey(), entry.getValue());
                })
                .map(entry -> {
                    LOGGER.trace(() -> buildMessage("invalidateEntries", entry.getKey()));
                    return entry.getKey();
                })
                .toList();

        // Doing the remove on the cacheAsMap means if rebuild was called we are still operating on
        // the old cache
        keysToRemove.forEach(cacheAsMap::remove);
    }

    @Override
    public void remove(final K key) {
        LOGGER.trace(() -> buildMessage("remove", key));
        // Get local copy in case rebuild is called
        final Cache<K, V> cache = getCache();
        cache.invalidate(key);
        cache.cleanUp();
    }

    @Override
    public void evictExpiredElements() {
        LOGGER.trace(() -> buildMessage("evictExpiredElements"));
        getCache().cleanUp();
    }

    @Override
    public long size() {
        return getCache().estimatedSize();
    }

    @Override
    public void clear() {
        LOGGER.trace(() -> buildMessage("clear"));

        // Read lock as we are not changing the reference to the cache.
        // The cache has its own concurrency protection at the entry level.
        CacheUtil.clear(getCache());
    }

    @Override
    public CacheInfo getCacheInfo() {
        final PropertyPath basePropertyPath = getBasePropertyPath();

        final Map<String, String> map = new HashMap<>();
        // Local copy in case rebuild is called
        final CacheHolder<K, V> localCacheHolder = this.cacheHolder;
        final Cache<K, V> cache = localCacheHolder.getCache();
        map.put("Entries", String.valueOf(cache.estimatedSize()));
        // The lock covers cacheBuilder too
        addEntries(map, getCacheBuilder().toString());
        addEntries(map, cache.stats().toString());

        map.forEach((k, v) -> {
            if (k.startsWith("Expire") || k.equals("TotalLoadTime")) {
                convertNanosToDuration(map, k, v);
            }
        });

        // We don't make use of Weighers in the cache so the weight stats are meaningless
        map.remove("EvictionWeight");

        // Let users know how many times we have evicted items for hitting the size limit.
        map.put("Reached Size Limit Count", Integer.toString(reachedSizeLimitCount.get()));

        return new CacheInfo(name, basePropertyPath, map);
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
                    if (!key.isEmpty()) {
                        final char[] chars = key.toCharArray();
                        chars[0] = Character.toUpperCase(chars[0]);
                        key = new String(chars);
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

    String buildMessage(final String methodName, final K key, final String msg) {
        return methodName + "() - cache: '" + name + "', key: " + key + " - " + msg;
    }

    @Override
    public PropertyPath getBasePropertyPath() {
        // Don't need to do this under lock
        return NullSafe.getOrElseGet(
                cacheConfigSupplier.get(),
                CacheConfig::getBasePath,
                PropertyPath::blank);
    }

    @Override
    public String toString() {
        return "AbstractICache{" +
                "name='" + name + "', " +
                "basePath='" + getBasePropertyPath() + '\'' +
                '}';
    }
}
