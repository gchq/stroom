package stroom.receive.common;

import stroom.meta.api.AttributeMap;
import stroom.proxy.StroomStatusCode;
import stroom.security.api.UserIdentity;
import stroom.util.NullSafe;
import stroom.util.PredicateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Singleton
public class DataFeedKeyServiceImpl implements DataFeedKeyService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DataFeedKeyServiceImpl.class);

    private static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern DATA_FEED_KEY_PATTERN = Pattern.compile(
            "^sdk_[0-9]{3}_[A-HJ-NP-Za-km-z1-9]{128}$");

    private final Provider<ReceiveDataConfig> receiveDataConfigProvider;

    // Holds all the keys read from the data feed key files, entries are evicted when
    // the DataFeedKey has passed its expiry date.
    private final Map<CacheKey, CachedHashedDataFeedKey> cacheKeyToDataFeedKeyMap = new ConcurrentHashMap<>();
    private final Map<String, CachedHashedDataFeedKey> subjectIdToDataFeedKeyMap = new ConcurrentHashMap<>();

    // TODO replace with cache
    // Cache of the un-hashed key to validated DataFeedKey, to save us the hashing cost
    private final Map<String, Optional<HashedDataFeedKey>> keyToDataFeedKeyMap = new ConcurrentHashMap<>();

    private final Map<DataFeedKeyHashAlgorithm, DataFeedKeyHasher> hashFunctionMap = new EnumMap<>(
            DataFeedKeyHashAlgorithm.class);

    @Inject
    public DataFeedKeyServiceImpl(final Provider<ReceiveDataConfig> receiveDataConfigProvider) {
        this.receiveDataConfigProvider = receiveDataConfigProvider;

        hashFunctionMap.put(DataFeedKeyHashAlgorithm.ARGON2, new Argon2DataFeedKeyHasher());
//        hashFunctionMap.put(DataFeedKeyHashAlgorithm.BCRYPT, new BCryptApiKeyHasher());
    }

    @Override
    public Optional<HashedDataFeedKey> getDataFeedKey(final HttpServletRequest request,
                                                      final AttributeMap attributeMap) {
        Objects.requireNonNull(request);
        final Optional<HashedDataFeedKey> optDataFeedKey = getRequestHeader(request, AUTHORIZATION_HEADER)
                .map(str -> {
                    final String key;
                    if (str.startsWith(BEARER_PREFIX)) {
                        // This chops out 'Bearer' so we get just the token.
                        key = str.substring(BEARER_PREFIX.length());
                    } else {
                        key = str;
                    }
                    LOGGER.debug(() ->
                            "Found Authorization in request:\n" + str);
                    return key;
                })
                .flatMap((String key2) ->
                        lookupKey(key2, attributeMap));

        optDataFeedKey.ifPresent(dataFeedKey ->
                validateDataFeedKeyExpiry(dataFeedKey, attributeMap));

        return optDataFeedKey;
    }

    @Override
    public Optional<HashedDataFeedKey> getDataFeedKey(final String subjectId) {
        return Optional.ofNullable(subjectIdToDataFeedKeyMap.get(subjectId))
                .map(CachedHashedDataFeedKey::getDataFeedKey);
    }

    @Override
    public void addDataFeedKeys(final HashedDataFeedKeys hashedDataFeedKeys,
                                final Path sourceFile) {
        if (NullSafe.hasItems(hashedDataFeedKeys)) {
            LOGGER.debug(() -> LogUtil.message("Adding {} dataFeedKeys",
                    hashedDataFeedKeys.getDataFeedKeys().size()));

            hashedDataFeedKeys.getDataFeedKeys()
                    .stream()
                    .map(dataFeedKey -> new CachedHashedDataFeedKey(dataFeedKey, sourceFile))
                    .forEach(this::addDataFeedKey);
        }
    }

    private void addDataFeedKey(final CachedHashedDataFeedKey cachedHashedDataFeedKey) {
        if (cachedHashedDataFeedKey != null) {
            final String hash = cachedHashedDataFeedKey.getHash();
            final String hashAlgorithmId = cachedHashedDataFeedKey.getHashAlgorithmId();
            final DataFeedKeyHashAlgorithm hashAlgorithm = DataFeedKeyHashAlgorithm.fromUniqueId(hashAlgorithmId);
            final CacheKey cacheKey = new CacheKey(hashAlgorithm, hash);
            cacheKeyToDataFeedKeyMap.put(cacheKey, cachedHashedDataFeedKey);
            subjectIdToDataFeedKeyMap.put(cachedHashedDataFeedKey.getSubjectId(), cachedHashedDataFeedKey);
        }
    }

    @Override
    public void evictExpired() {
        LOGGER.debug("Evicting expired dataFeedKeys");
        final AtomicInteger counter = new AtomicInteger();
        final Predicate<Entry<?, CachedHashedDataFeedKey>> removeIfPredicate = entry ->
                entry.getValue().isExpired();

        counter.set(0);
        cacheKeyToDataFeedKeyMap.entrySet().removeIf(
                PredicateUtil.countingPredicate(counter, removeIfPredicate));
        LOGGER.debug("Removed {} cacheKeyToDataFeedKeyMap entries", counter);

        counter.set(0);
        subjectIdToDataFeedKeyMap.entrySet().removeIf(
                PredicateUtil.countingPredicate(counter, removeIfPredicate));
        LOGGER.debug("Removed {} subjectIdToDataFeedKeyMap entries", counter);

        counter.set(0);
        keyToDataFeedKeyMap.entrySet().removeIf(PredicateUtil.countingPredicate(counter, entry ->
                entry.getValue().filter(HashedDataFeedKey::isExpired).isPresent()));
        LOGGER.debug("Removed {} keyToDataFeedKeyMap entries", counter);
    }

    @Override
    public void removeKeysForFile(final Path sourceFile) {
        if (sourceFile != null) {
            LOGGER.debug("Evicting dataFeedKeys for sourceFile {}", sourceFile);
            final AtomicInteger counter = new AtomicInteger();
            final Predicate<Entry<?, CachedHashedDataFeedKey>> removeIfPredicate = entry -> {
                final boolean doRemove = Objects.equals(
                        sourceFile, entry.getValue().getSourceFile());
                if (doRemove) {
                    counter.incrementAndGet();
                }
                return doRemove;
            };

            cacheKeyToDataFeedKeyMap.entrySet().removeIf(removeIfPredicate);
            LOGGER.debug("Removed {} cacheKeyToDataFeedKeyMap entries", counter);
            counter.set(0);
            subjectIdToDataFeedKeyMap.entrySet().removeIf(removeIfPredicate);
            LOGGER.debug("Removed {} subjectIdToDataFeedKeyMap entries", counter);
        }
    }

    private boolean validateDataFeedKeyExpiry(final HashedDataFeedKey hashedDataFeedKey,
                                              final AttributeMap attributeMap) {
        if (hashedDataFeedKey.isExpired()) {
            throw new StroomStreamException(
                    StroomStatusCode.DATA_FEED_KEY_NOT_AUTHENTICATED, attributeMap);
        }
        return true;
    }

    private String extractUniqueIdFromKey(final String key) {
        // sdk_123_......
        return key.substring(4, 7);
    }

    private Optional<CacheKey> getCacheKey(final String key) {
        Objects.requireNonNull(key);
        if (DATA_FEED_KEY_PATTERN.matcher(key).matches()) {
            final String uniqueId = extractUniqueIdFromKey(key);
            final DataFeedKeyHashAlgorithm hashAlgorithm = DataFeedKeyHashAlgorithm.fromUniqueId(uniqueId);

            Objects.requireNonNull(hashAlgorithm, () ->
                    LogUtil.message("Hash algorithm not found for uniqueId '{}'", uniqueId));

            final DataFeedKeyHasher hasher = hashFunctionMap.get(hashAlgorithm);
            Objects.requireNonNull(hasher, () -> LogUtil.message("No hasher found for {}", hashAlgorithm));
            final String hash = hasher.hash(key);
            return Optional.of(new CacheKey(hashAlgorithm, hash));
        } else {
            LOGGER.debug("key '{}' does not look like a not a datafeed key", key);
            return Optional.empty();
        }
    }

    private Optional<HashedDataFeedKey> lookupKey(final String key,
                                                  final AttributeMap attributeMap) {

        // Try the cache first to save on the hashing cost.
        Optional<HashedDataFeedKey> optDataFeedKey = keyToDataFeedKeyMap.get(key);
        if (optDataFeedKey == null) {
            // Not in cache,
            optDataFeedKey = getCacheKey(key)
                    .map(cacheKey -> {
                        Objects.requireNonNull(cacheKey);
                        final CachedHashedDataFeedKey dataFeedKey = cacheKeyToDataFeedKeyMap.get(cacheKey);
                        LOGGER.debug("Lookup of cacheKey {}, found {}", cacheKey, dataFeedKey);
                        return dataFeedKey;
                    })
                    .map(CachedHashedDataFeedKey::getDataFeedKey);
            // Cache it to save hashing next time
            keyToDataFeedKeyMap.put(key, optDataFeedKey);
            return optDataFeedKey;
        } else {
            return optDataFeedKey
                    .filter(dataFeedKey ->
                            validateDataFeedKeyExpiry(dataFeedKey, attributeMap));
        }
    }

    /**
     * @return An optional containing a non-blank attribute value, else empty.
     */
    private Optional<String> getAttribute(final AttributeMap attributeMap, final String header) {
        return Optional.ofNullable(attributeMap.get(header))
                .filter(str -> !StringUtils.isNotBlank(str));
    }

    private Optional<String> getRequestHeader(final HttpServletRequest request, final String header) {
        final String value = request.getHeader(header);
        final Optional<String> optValue = Optional.ofNullable(value)
                .filter(NullSafe::isNonBlankString);
        return optValue;
    }

    @Override
    public Optional<UserIdentity> authenticate(final HttpServletRequest request,
                                               final AttributeMap attributeMap) {
        try {
            final Optional<UserIdentity> optUserIdentity = getDataFeedKey(request, attributeMap)
                    .map(dataFeedKey -> {
                        // Ensure the stream attributes from the data feed key are set in the attributeMap so
                        // that the AttributeMapFilters have access to them and any attributes that are static
                        // to this key are applied to all streams that use it, e.g. aws account number.
                        final Map<String, String> streamMeta = NullSafe.map(dataFeedKey.getStreamMetaData());
                        // Entries from the data feed key trump what is in the headers
                        attributeMap.putAll(streamMeta);

                        return new DataFeedKeyUserIdentity(dataFeedKey);
                    });
            LOGGER.debug("Returning {}", optUserIdentity);
            return optUserIdentity;
        } catch (StroomStreamException e) {
            throw e;
        } catch (Exception e) {
            throw new StroomStreamException(
                    StroomStatusCode.DATA_FEED_KEY_NOT_AUTHENTICATED, attributeMap, e.getMessage());
        }
    }


    // --------------------------------------------------------------------------------


    private record CacheKey(DataFeedKeyHashAlgorithm dataFeedKeyHashAlgorithm,
                            String hash) {

    }
}
