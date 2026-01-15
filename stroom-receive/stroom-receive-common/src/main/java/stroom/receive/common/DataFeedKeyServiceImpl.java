/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.receive.common;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.meta.api.AttributeMap;
import stroom.proxy.StroomStatusCode;
import stroom.security.api.UserIdentity;
import stroom.util.PredicateUtil;
import stroom.util.collections.CollectionUtil;
import stroom.util.collections.CollectionUtil.DuplicateMode;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.string.CIKey;
import stroom.util.sysinfo.HasSystemInfo;
import stroom.util.sysinfo.SystemInfoResult;

import io.dropwizard.lifecycle.Managed;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Singleton
public class DataFeedKeyServiceImpl implements DataFeedKeyService, Managed, HasSystemInfo {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DataFeedKeyServiceImpl.class);
    private static final String CACHE_NAME = "Authenticated Data Feed Key Cache";

    static final String AUTHORIZATION_HEADER = "Authorization";
    static final String BEARER_PREFIX = "Bearer ";
    // The meat of the key is random Base58 chars
    static final int DATA_FEED_KEY_RANDOM_PART_LENGTH = 128;
    private static final String DATA_FEED_KEY_PREFIX = "sdk_";
    private static final Pattern DATA_FEED_KEY_PATTERN = Pattern.compile(
            "^" + DATA_FEED_KEY_PREFIX + "[A-HJ-NP-Za-km-z1-9]{" + DATA_FEED_KEY_RANDOM_PART_LENGTH + "}$");
    private static final Comparator<CachedHashedDataFeedKey> HASHED_DATA_FEED_KEY_COMPARATOR =
            Comparator.comparingLong(CachedHashedDataFeedKey::getExpiryDateEpochMs)
                    .reversed();

    // Holds ALL the keys read from the data feed key files, entries are evicted when
    // the DataFeedKey has passed its expiry date. List<CachedHashedDataFeedKey> to
    // allow for hash clashes
//    private final Map<CacheKey, List<CachedHashedDataFeedKey>> cacheKeyToDataFeedKeyMap = new ConcurrentHashMap<>();
    // The owner will likely have >1 CachedHashedDataFeedKey due to the overlap of keys when
    // new keys are being supplied, but not many. Use CIKey so we are not fussy on case.
    private final Map<CIKey, Set<CachedHashedDataFeedKey>> keyOwnerToDataFeedKeyMap = new ConcurrentHashMap<>();

    // Cache of the un-hashed key + owner to verified CachedHashedDataFeedKeys
    // If the un-hashed key is in this map, then it means we have hashed it, checked it against
    // cacheKeyToDataFeedKeyMap and found it to be valid (at the time of checking).
    // This cache saves us the hashing cost on every data receipt, which can be expensive.
    private final LoadingStroomCache<UnHashedCacheKey, Set<CachedHashedDataFeedKey>> unHashedKeyToDataFeedKeyCache;

    private final Provider<ReceiveDataConfig> receiveDataConfigProvider;
    private final Map<DataFeedKeyHashAlgorithm, DataFeedKeyHasher> hashFunctionMap;

    private final Timer timer;

    @Inject
    DataFeedKeyServiceImpl(final Provider<ReceiveDataConfig> receiveDataConfigProvider,
                           final Set<DataFeedKeyHasher> dataFeedKeyHashers,
                           final CacheManager cacheManager) {
        this.receiveDataConfigProvider = receiveDataConfigProvider;
        this.hashFunctionMap = buildHashFunctionMap(dataFeedKeyHashers);
        this.unHashedKeyToDataFeedKeyCache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> receiveDataConfigProvider.get().getAuthenticatedDataFeedKeyCache(),
                this::createHashedDataFeedKey);
        this.timer = new Timer("DataFeedKeyTimer");
    }

    private Map<DataFeedKeyHashAlgorithm, DataFeedKeyHasher> buildHashFunctionMap(
            final Set<DataFeedKeyHasher> dataFeedKeyHashers) {

        return CollectionUtil.enumMapBy(
                DataFeedKeyHashAlgorithm.class,
                DataFeedKeyHasher::getAlgorithm,
                DuplicateMode.THROW,
                dataFeedKeyHashers);
    }

    private Optional<HashedDataFeedKey> getDataFeedKey(final HttpServletRequest request,
                                                       final AttributeMap attributeMap,
                                                       final ReceiveDataConfig receiveDataConfig) {
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
                        lookupAndValidateKey(key2, attributeMap, receiveDataConfig));

        return optDataFeedKey;
    }

//    @Override
//    public Optional<HashedDataFeedKey> getLatestDataFeedKey(final String accountId) {
//        if (accountId == null) {
//            return Optional.empty();
//        } else {
//            return Optional.ofNullable(accountIdToDataFeedKeyMap.get(accountId))
//                    .flatMap(cachedKeys -> cachedKeys.stream()
//                            .max(Comparator.comparing(CachedHashedDataFeedKey::getExpiryDate)))
//                    .map(CachedHashedDataFeedKey::getDataFeedKey);
//        }
//    }

//    @Override
//    public Optional<HashedDataFeedKey> getDataFeedKey(final String subjectId) {
//        return Optional.ofNullable(subjectIdToDataFeedKeyMap.get(subjectId))
//                .map(CachedHashedDataFeedKey::getDataFeedKey);
//    }

    @Override
    public synchronized int addDataFeedKeys(final HashedDataFeedKeys hashedDataFeedKeys,
                                            final Path sourceFile) {
        final AtomicInteger addedCount = new AtomicInteger();
        if (NullSafe.hasItems(hashedDataFeedKeys.getDataFeedKeys())) {
            LOGGER.debug(() -> LogUtil.message("Adding {} dataFeedKeys",
                    hashedDataFeedKeys.getDataFeedKeys().size()));

            final AtomicInteger invalidCount = new AtomicInteger();
            final AtomicInteger dupCount = new AtomicInteger();
            final CIKey keyOwnerMetaKey = getOwnerMetaKey(receiveDataConfigProvider.get());

            hashedDataFeedKeys.getDataFeedKeys()
                    .stream()
                    .filter(Objects::nonNull)
                    .map(dataFeedKey ->
                            new CachedHashedDataFeedKey(dataFeedKey, sourceFile))
                    .filter(dataFeedKey ->
                            isValidDataFeedKey(dataFeedKey, keyOwnerMetaKey, invalidCount))
                    .forEach(cachedHashedDataFeedKey -> {
                        addDataFeedKey(cachedHashedDataFeedKey, keyOwnerMetaKey, addedCount, dupCount);
                    });

            LOGGER.debug(() -> LogUtil.message(
                    "Added: {}, ignored {} data feed keys (invalid: {}, duplicate: {}), file: {}",
                    addedCount,
                    invalidCount.get() + dupCount.get(),
                    invalidCount.get(),
                    dupCount.get(),
                    sourceFile));
        }
        LOGGER.debug(() -> LogUtil.message("Total cached keys: {}", keyOwnerToDataFeedKeyMap.values()
                .stream()
                .mapToInt(Set::size)
                .sum()));
        return addedCount.get();
    }

    private boolean isValidDataFeedKey(final CachedHashedDataFeedKey dataFeedKey,
                                       final CIKey ownerMetaKey,
                                       final AtomicInteger invalidCount) {

        if (dataFeedKey.isExpired()) {
            LOGGER.debug("Ignoring expired Data Feed Key in sourceFile: {}", dataFeedKey.getSourceFile());
            invalidCount.incrementAndGet();
            return false;
        }
        final String value = dataFeedKey.getStreamMetaValue(ownerMetaKey);
        final boolean hasOwner = NullSafe.isNonBlankString(value);
        if (!hasOwner) {
            invalidCount.incrementAndGet();
            LOGGER.warn("Ignoring Data Feed Key found with no value for owner key '{}' in sourceFile: {}",
                    ownerMetaKey, dataFeedKey.getSourceFile());
            return false;
        }
        return true;
    }

    private synchronized void addDataFeedKey(final CachedHashedDataFeedKey cachedHashedDataFeedKey,
                                             final CIKey keyOwnerMetaKey,
                                             final AtomicInteger addedCount,
                                             final AtomicInteger dupCount) {
        if (cachedHashedDataFeedKey != null) {
            final String keyOwner = cachedHashedDataFeedKey.getStreamMetaValue(keyOwnerMetaKey);
            // Use CopyOnWriteArrayList as write are very infrequent
            final boolean success = keyOwnerToDataFeedKeyMap.computeIfAbsent(
                            CIKey.of(keyOwner),
                            k -> ConcurrentHashMap.newKeySet())
                    .add(cachedHashedDataFeedKey);
            if (success) {
                addedCount.incrementAndGet();
            } else {
                dupCount.incrementAndGet();
            }
        }
    }

    @Override
    public synchronized void evictExpired() {
        LOGGER.debug("Evicting expired dataFeedKeys");
        final LongAdder counter = new LongAdder();
        final Predicate<CachedHashedDataFeedKey> isExpiredPredicate = PredicateUtil.countingPredicate(
                counter,
                CachedHashedDataFeedKey::isExpired);

//        counter.set(0);
//        cacheKeyToDataFeedKeyMap.entrySet().removeIf(
//                entry -> {
//                    final List<CachedHashedDataFeedKey> hashedKeys = entry.getValue();
//                    hashedKeys.removeIf(isExpiredPredicate);
//                    // If we have removed the last one then remove the whole entry
//                    return hashedKeys.isEmpty();
//                });
//        LOGGER.debug("Removed {} CachedHashedDataFeedKeys from cacheKeyToDataFeedKeyMap", counter);
//        if (counter.get() > 0) {
//            LOGGER.info("Evicted {} expired data feed keys", counter);
//        }

        counter.reset();
        keyOwnerToDataFeedKeyMap.forEach((keyOwner, cachedHashedDataFeedKeys) ->
                cachedHashedDataFeedKeys.removeIf(isExpiredPredicate));
        LOGGER.debug("Removed {} cachedHashedDataFeedKey items from keyOwnerToDataFeedKeyMap", counter);
        if (counter.longValue() > 0) {
            LOGGER.info("Evicted {} expired data feed keys", counter);
        }
//
//        counter.set(0);

        // In all likelihood, there will only be one item in the list per un-hashed key
        // so just invalidate the whole entry
        unHashedKeyToDataFeedKeyCache.invalidateEntries(PredicateUtil.countingBiPredicate(
                counter,
                (unHashedKey, hashedKeys) ->
                        hashedKeys.stream()
                                .anyMatch(CachedHashedDataFeedKey::isExpired)));

        LOGGER.debug("Removed {} unHashedKeyToDataFeedKeyCache entries", counter);
    }

    @Override
    public synchronized void removeKeysForFile(final Path sourceFile) {
        if (sourceFile != null) {
            LOGGER.info("Evicting dataFeedKeys for sourceFile {}", sourceFile);
            final LongAdder counter = new LongAdder();
            final Predicate<CachedHashedDataFeedKey> sourceFilePredicate = PredicateUtil.countingPredicate(
                    counter, cachedKey ->
                            Objects.equals(sourceFile, cachedKey.getSourceFile()));

            // In all likelihood, there will only be one item in the list per un-hashed key
            // so just invalidate the whole entry
            unHashedKeyToDataFeedKeyCache.invalidateEntries(PredicateUtil.countingBiPredicate(
                    counter,
                    (unHashedKey, dataFeedKeys) ->
                            dataFeedKeys.stream()
                                    .anyMatch(cachedHashedDataFeedKey ->
                                            Objects.equals(sourceFile, cachedHashedDataFeedKey.getSourceFile()))));
            LOGGER.debug("Removed {} unHashedKeyToDataFeedKeyCache entries", counter);
//            counter.set(0);
//            cacheKeyToDataFeedKeyMap.entrySet().removeIf(
//                    entry -> {
//                        final List<CachedHashedDataFeedKey> hashedKeys = entry.getValue();
//                        hashedKeys.removeIf(sourceFilePredicate);
//                        // If we have removed the last one then remove the whole entry
//                        return hashedKeys.isEmpty();
//                    });
//            LOGGER.debug("Removed {} CachedHashedDataFeedKeys from cacheKeyToDataFeedKeyMap", counter);
//            LOGGER.info("Evicted {} dataFeedKeys for sourceFile {}", counter, sourceFile);
            counter.reset();
            keyOwnerToDataFeedKeyMap.forEach((keyOwner, cachedHashedDataFeedKeys) -> {
                cachedHashedDataFeedKeys.removeIf(sourceFilePredicate);
            });
            LOGGER.debug("Removed {} subjectIdToDataFeedKeyMap entries", counter);
            LOGGER.info("Evicted {} dataFeedKeys for sourceFile {}", counter, sourceFile);
        }
        LOGGER.debug(() -> LogUtil.message("Total cached keys: {}", keyOwnerToDataFeedKeyMap.values()
                .stream()
                .mapToInt(Set::size)
                .sum()));
    }

    private boolean validateDataFeedKeyExpiry(final HashedDataFeedKey hashedDataFeedKey,
                                              final AttributeMap attributeMap) {
        if (hashedDataFeedKey.isExpired()) {
            throw new StroomStreamException(
                    StroomStatusCode.DATA_FEED_KEY_EXPIRED, attributeMap);
        }
        return true;
    }

    private String extractUniqueIdFromKey(final String key) {
        // sdk_123_......
        return key.substring(4, 7);
    }

//    private Optional<CacheKey> getCacheKey(final String unHashedKey) {
//        Objects.requireNonNull(unHashedKey);
//        if (DATA_FEED_KEY_PATTERN.matcher(unHashedKey).matches()) {
//            final String uniqueId = extractUniqueIdFromKey(unHashedKey);
//            final DataFeedKeyHashAlgorithm hashAlgorithm = DataFeedKeyHashAlgorithm.fromUniqueId(uniqueId);
//
//            Objects.requireNonNull(hashAlgorithm, () ->
//                    LogUtil.message("Hash algorithm not found for uniqueId '{}'", uniqueId));
//
//            final DataFeedKeyHasher hasher = hashFunctionMap.get(hashAlgorithm);
//            Objects.requireNonNull(hasher, () -> LogUtil.message("No hasher found for {}", hashAlgorithm));
//            final String hash = hasher.hash(unHashedKey);
//            return Optional.of(new CacheKey(hashAlgorithm, hash));
//        } else {
//            LOGGER.debug("key '{}' does not look like a not a datafeed key", unHashedKey);
//            return Optional.empty();
//        }
//    }

    private boolean looksLikeADatafeedKey(final String unHashedKey, final AttributeMap attributeMap) {
        if (NullSafe.isBlankString(unHashedKey)) {
            LOGGER.debug("Blank unHashedKey, attributeMap: {}", attributeMap);
            return false;
        } else if (!unHashedKey.startsWith(DATA_FEED_KEY_PREFIX)) {
            // Do a simple prefix check first as we will get more keys that are not datafeed keys
            LOGGER.debug("unHashedKey '{}' doesn't start with prefix '{}', attributeMap: {}",
                    unHashedKey, DATA_FEED_KEY_PREFIX, attributeMap);
            return false;
        } else if (!DATA_FEED_KEY_PATTERN.matcher(unHashedKey).matches()) {
            LOGGER.debug("unHashedCacheKey '{}' is not a data feed key, pattern: '{}', attributeMap: {}",
                    unHashedKey, DATA_FEED_KEY_PATTERN, attributeMap);
            return false;
        } else {
            LOGGER.debug("unHashedCacheKey '{}' looks like a data feed key, pattern: '{}', attributeMap: {}",
                    unHashedKey, DATA_FEED_KEY_PATTERN, attributeMap);
            return true;
        }
    }

    /**
     * @return A populated {@link Optional} if the key is known to use and is valid, else empty.
     */
    private Optional<HashedDataFeedKey> lookupAndValidateKey(final String unHashedKey,
                                                             final AttributeMap attributeMap,
                                                             final ReceiveDataConfig receiveDataConfig) {
        if (!looksLikeADatafeedKey(unHashedKey, attributeMap)) {
            return Optional.empty();
        }

        // If we get here we are dealing with a key that looks like a data feed key,
        // so we can validate it as such

        final CIKey keyOwnerMetaKey = getOwnerMetaKey(receiveDataConfig);
        final String keyOwner = NullSafe.get(
                attributeMap,
                map -> map.get(keyOwnerMetaKey.get()),
                String::trim);
        if (NullSafe.isBlankString(keyOwner)) {
            LOGGER.debug("Blank keyOwner, attributeMap: {}", attributeMap);
            throw new StroomStreamException(
                    StroomStatusCode.DATA_FEED_KEY_NOT_AUTHENTICATED,
                    attributeMap,
                    "Mandatory header '" + keyOwnerMetaKey +
                    "' must be provided to authenticate with a data feed key.");
        }

        // We cache already authenticated un-hashed keys to save the hashing cost.
        // Cache get will trigger the hashing and checking if not found
        final Set<CachedHashedDataFeedKey> dataFeedKeys = unHashedKeyToDataFeedKeyCache.get(
                new UnHashedCacheKey(unHashedKey, CIKey.ofDynamicKey(keyOwner)));

        if (NullSafe.isEmptyCollection(dataFeedKeys)) {
            LOGGER.debug("Unknown data feed key {}, attributeMap: {}", unHashedKey, attributeMap);
            // We need to throw here because we know it looks like a DFK, so
            // we don't want to pass it on to the next filter.
            throw new StroomStreamException(StroomStatusCode.DATA_FEED_KEY_NOT_AUTHENTICATED, attributeMap);
        }

        final Predicate<CachedHashedDataFeedKey> filter = createKeyOwnerFilter(keyOwnerMetaKey, keyOwner);

        // In the event that we have more than one key for an accountId, get the one with
        // the latest expire time.
        final List<HashedDataFeedKey> filteredKeys = dataFeedKeys.stream()
                .filter(filter)
                .sorted(HASHED_DATA_FEED_KEY_COMPARATOR)
                .map(CachedHashedDataFeedKey::getDataFeedKey)
                .toList();

        if (NullSafe.isEmptyCollection(filteredKeys)) {
            // Got a match on the un-hashed key, but is a bad accountId.
            // Stops someone using their own DFK to authenticate to someone else's accountId.
            LOGGER.debug("No un-expired keys matching data feed key {}, keyOwnerMetaKey: {}, " +
                         "keyOwner: {}, dataFeedKeys: {}",
                    unHashedKey, keyOwnerMetaKey, keyOwner, dataFeedKeys);
            if (DATA_FEED_KEY_PATTERN.matcher(unHashedKey).matches()) {
                // It looks like a DFK, but we go no match, so we can throw
                throw new StroomStreamException(StroomStatusCode.DATA_FEED_KEY_NOT_AUTHENTICATED, attributeMap);
            } else {
                // Doesn't look like a DFK so let the next filter have a go
                return Optional.empty();
            }
        } else {
            // Very, very unlikely to have >1 item, but you never know
            final Optional<HashedDataFeedKey> optValidKey = filteredKeys.stream()
                    .filter(Predicate.not(HashedDataFeedKey::isExpired))
                    .findFirst();

            if (optValidKey.isEmpty()) {
                throw new StroomStreamException(StroomStatusCode.DATA_FEED_KEY_EXPIRED, attributeMap);
            }
            return optValidKey;
        }
    }

    private static Predicate<CachedHashedDataFeedKey> createKeyOwnerFilter(final CIKey keyOwnerMetaKey,
                                                                           final String ownerFromAttrMap) {
        final Predicate<CachedHashedDataFeedKey> filter;
        if (CIKey.isNonBlank(keyOwnerMetaKey)) {
            filter = (final CachedHashedDataFeedKey key) -> {
                final String ownerFromKey = key.getStreamMetaValue(keyOwnerMetaKey);
                final boolean result = Objects.equals(ownerFromKey, ownerFromAttrMap);
                LOGGER.debug("keyOwnerMetaKey: {}, ownerFromAttrMap: {}, ownerFromKey: {}, result: {}",
                        keyOwnerMetaKey, ownerFromAttrMap, ownerFromKey, result);
                return result;
            };
        } else {
            filter = key -> true;
        }
        return filter;
    }

//    private List<CachedHashedDataFeedKey> createHashedDataFeedKey(final String keyOwner) {
//        if (NullSafe.isBlankString(keyOwner)) {
//            return Collections.emptyList();
//        } else {
//            keyOwnerToDataFeedKeyMap.get(CIKey.of(keyOwner));
//        }
//
//    }

    /**
     * Loading function for an un-hashed key and its keyOwner.
     */
    private Set<CachedHashedDataFeedKey> createHashedDataFeedKey(final UnHashedCacheKey unHashedCacheKey) {
        Objects.requireNonNull(unHashedCacheKey);
        if (!DATA_FEED_KEY_PATTERN.matcher(unHashedCacheKey.unHashedKey).matches()) {
            LOGGER.debug("key '{}' does not look like a not a datafeed key", unHashedCacheKey.unHashedKey);
            return Collections.emptySet();
        }

        final Set<CachedHashedDataFeedKey> matchingKeys;
        synchronized (this) {
            // Likely to only be 1-2 items per owner
            final Set<CachedHashedDataFeedKey> ownersKeys = keyOwnerToDataFeedKeyMap.get(unHashedCacheKey.keyOwner);
            matchingKeys = NullSafe.stream(ownersKeys)
                    .filter(cachedHashedDataFeedKey ->
                            verifyKey(unHashedCacheKey.unHashedKey, cachedHashedDataFeedKey))
                    .collect(Collectors.toCollection(ConcurrentHashMap::newKeySet));
        }
        LOGGER.debug("unHashedCacheKey: {}, matchingKeys: {}", unHashedCacheKey, matchingKeys);
        return matchingKeys;
    }

    private boolean verifyKey(final String unHashedKey, final CachedHashedDataFeedKey cachedHashedDataFeedKey) {
        final String hash = cachedHashedDataFeedKey.getHash();
        final String salt = cachedHashedDataFeedKey.getSalt();
        final DataFeedKeyHasher hasher;
        try {
            final DataFeedKeyHashAlgorithm hashAlgorithmId = cachedHashedDataFeedKey.getHashAlgorithm();
            hasher = hashFunctionMap.get(hashAlgorithmId);
        } catch (final Exception e) {
            throw new RuntimeException("Unknown hash algorithm " + cachedHashedDataFeedKey.getHashAlgorithm());
        }

        final boolean isValid = hasher.verify(unHashedKey, hash, salt);
        LOGGER.debug("verifyKey() - unHashedKey: {}, hash: {}, salt: {}, isValid: {}",
                unHashedKey, hash, salt, isValid);
        return isValid;
    }

    /**
     * @return An optional containing a non-blank attribute value, else empty.
     */
    private Optional<String> getAttribute(final AttributeMap attributeMap, final CIKey header) {
        if (header == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(attributeMap.get(header.get()))
                .filter(StringUtils::isNotBlank);
    }

    private Optional<String> getAttribute(final AttributeMap attributeMap, final String header) {
        return Optional.ofNullable(attributeMap.get(header))
                .filter(StringUtils::isNotBlank);
    }

    private Optional<String> getRequestHeader(final HttpServletRequest request, final CIKey header) {
        if (header == null) {
            return Optional.empty();
        }
        final String value = request.getHeader(header.get());
        return Optional.ofNullable(value)
                .filter(NullSafe::isNonBlankString);
    }

    private Optional<String> getRequestHeader(final HttpServletRequest request, final String header) {
        final String value = request.getHeader(header);
        return Optional.ofNullable(value)
                .filter(NullSafe::isNonBlankString);
    }

    @Override
    public Optional<UserIdentity> authenticate(final HttpServletRequest request,
                                               final AttributeMap attributeMap) {
        try {
            final ReceiveDataConfig receiveDataConfig = receiveDataConfigProvider.get();
            final Optional<UserIdentity> optUserIdentity = getDataFeedKey(request, attributeMap, receiveDataConfig)
                    .map(dataFeedKey -> {
                        final CIKey keyOwnerMetaKey = getOwnerMetaKey(receiveDataConfig);
                        final String keyOwner = dataFeedKey.getStreamMetaValue(keyOwnerMetaKey);
                        // Ensure the stream attributes from the data feed key are set in the attributeMap so
                        // that the AttributeMapFilters have access to them and any attributes that are static
                        // to this key are applied to all streams that use it, e.g. aws account number.
                        // Entries from the data feed key trump what is in the headers
                        attributeMap.putAll(dataFeedKey.getAttributeMap());
                        return new DataFeedKeyUserIdentity(keyOwner);
                    });
            LOGGER.debug("Returning {}, attributeMap: {}", optUserIdentity, attributeMap);
            return optUserIdentity;
        } catch (final StroomStreamException e) {
            throw e;
        } catch (final Exception e) {
            throw new StroomStreamException(
                    StroomStatusCode.DATA_FEED_KEY_NOT_AUTHENTICATED, attributeMap, e.getMessage());
        }
    }

    @Override
    public void start() throws Exception {
        final TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    evictExpired();
                } catch (final Exception e) {
                    LOGGER.error("Error running entry eviction timerTask: {}", e.getMessage(), e);
                }
            }
        };
        LOGGER.info("Starting cache eviction timer");
        timer.scheduleAtFixedRate(timerTask, 0, Duration.ofMinutes(1).toMillis());
    }

    @Override
    public void stop() throws Exception {
        LOGGER.info("Shutting down entry eviction timer");
        try {
            timer.cancel();
        } catch (final Exception e) {
            LOGGER.error("Error shutting down the timer: {}", LogUtil.exceptionMessage(e), e);
        }
    }

    @Override
    public SystemInfoResult getSystemInfo() {
        // sourcePath => accountId => Map
        final Map<String, Map<String, List<Map<String, String>>>> map = new HashMap<>();
        final String keyOwnerMetaKey = receiveDataConfigProvider.get().getDataFeedKeyOwnerMetaKey();
        keyOwnerToDataFeedKeyMap.values()
                .forEach(dataFeedKeys -> {
                    for (final CachedHashedDataFeedKey dataFeedKey : dataFeedKeys) {
                        final String path = dataFeedKey.getSourceFile().toAbsolutePath().normalize().toString();
                        final String keyOwner = Objects.requireNonNullElse(
                                dataFeedKey.getStreamMetaValue(keyOwnerMetaKey),
                                "null");
                        final List<Map<String, String>> keysForAccountId = map.computeIfAbsent(path,
                                        k -> new HashMap<>())
                                .computeIfAbsent(keyOwner, k -> new ArrayList<>());

                        final String remaining = Duration.between(
                                Instant.now(),
                                dataFeedKey.getExpiryDate()).toString();
                        final Map<String, String> leafMap = Map.of(
                                "expiry", dataFeedKey.getExpiryDate().toString(),
                                "remaining", remaining,
                                "algorithm", dataFeedKey.getHashAlgorithm().toString());
                        keysForAccountId.add(leafMap);
                    }
                });
        return SystemInfoResult.builder(this)
                .addDetail("sourceFiles", map)
                .build();
    }

    private CIKey getOwnerMetaKey(final ReceiveDataConfig receiveDataConfig) {
        return CIKey.of(receiveDataConfig.getDataFeedKeyOwnerMetaKey());
    }

    // --------------------------------------------------------------------------------


//    private record CacheKey(DataFeedKeyHashAlgorithm dataFeedKeyHashAlgorithm,
//                            String hash) {
//
//    }


    // --------------------------------------------------------------------------------


    private record UnHashedCacheKey(String unHashedKey, CIKey keyOwner) {

    }
}
