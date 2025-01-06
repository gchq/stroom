package stroom.receive.common;

import stroom.docref.HasDisplayValue;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.security.api.UserIdentity;
import stroom.util.NullSafe;
import stroom.util.PredicateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.google.common.base.Strings;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;

@Singleton
public class DataFeedKeyServiceImpl
        implements DataFeedKeyService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DataFeedKeyServiceImpl.class);

    private static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern DATA_FEED_KEY_PATTERN = Pattern.compile(
            "^sdk_[0-9]{3}_[A-HJ-NP-Za-km-z1-9]{128}$");

    private final Provider<ReceiveDataConfig> receiveDataConfigProvider;

    // Holds all the keys read from the data feed key files, entries are evicted when
    // the DataFeedKey has passed its expiry date.
    private final Map<CacheKey, CachedDataFeedKey> cacheKeyToDataFeedKeyMap = new ConcurrentHashMap<>();
    private final Map<String, CachedDataFeedKey> subjectIdToDataFeedKeyMap = new ConcurrentHashMap<>();

    // TODO replace with cache
    private final Map<String, Pattern> feedPatternCache = new ConcurrentHashMap<>();

    // TODO replace with cache
    // Cache of the un-hashed key to validated DataFeedKey, to save us the hashing cost
    private final Map<String, Optional<DataFeedKey>> keyToDataFeedKeyMap = new ConcurrentHashMap<>();

    private final Map<DataFeedKeyHashAlgorithm, DataFeedKeyHasher> hashFunctionMap = new EnumMap<>(
            DataFeedKeyHashAlgorithm.class);

    @Inject
    public DataFeedKeyServiceImpl(final Provider<ReceiveDataConfig> receiveDataConfigProvider) {
        this.receiveDataConfigProvider = receiveDataConfigProvider;

        hashFunctionMap.put(DataFeedKeyHashAlgorithm.ARGON2, new Argon2DataFeedKeyHasher());
//        hashFunctionMap.put(DataFeedKeyHashAlgorithm.BCRYPT, new BCryptApiKeyHasher());
    }

    @Override
    public Optional<DataFeedKey> getDataFeedKey(final HttpServletRequest request,
                                                final AttributeMap attributeMap) {
        Objects.requireNonNull(request);
        final Optional<DataFeedKey> optDataFeedKey = getAttribute(attributeMap, AUTHORIZATION_HEADER)
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
    public Optional<DataFeedKey> getDataFeedKey(final String subjectId) {
        return Optional.ofNullable(subjectIdToDataFeedKeyMap.get(subjectId))
                .map(CachedDataFeedKey::getDataFeedKey);
    }

    @Override
    public void addDataFeedKeys(final DataFeedKeys dataFeedKeys,
                                final Path sourceFile) {
        if (NullSafe.hasItems(dataFeedKeys)) {
            LOGGER.debug(() -> LogUtil.message("Adding {} dataFeedKeys",
                    dataFeedKeys.getDataFeedKeys().size()));

            dataFeedKeys.getDataFeedKeys()
                    .stream()
                    .map(dataFeedKey -> new CachedDataFeedKey(dataFeedKey, sourceFile))
                    .forEach(this::addDataFeedKey);
        }
    }

    private void addDataFeedKey(final CachedDataFeedKey cachedDataFeedKey) {
        if (cachedDataFeedKey != null) {
            final String hash = cachedDataFeedKey.getHash();
            final String hashAlgorithmId = cachedDataFeedKey.getHashAlgorithmId();
            final DataFeedKeyHashAlgorithm hashAlgorithm = DataFeedKeyHashAlgorithm.fromUniqueId(hashAlgorithmId);
            final CacheKey cacheKey = new CacheKey(hashAlgorithm, hash);
            cacheKeyToDataFeedKeyMap.put(cacheKey, cachedDataFeedKey);
            subjectIdToDataFeedKeyMap.put(cachedDataFeedKey.getSubjectId(), cachedDataFeedKey);
        }
    }

    @Override
    public void evictExpired() {
        LOGGER.debug("Evicting expired dataFeedKeys");
        final AtomicInteger counter = new AtomicInteger();
        final Set<String> patternsToRemove = new HashSet<>();
        final Predicate<Entry<?, CachedDataFeedKey>> removeIfPredicate = entry -> {
            final CachedDataFeedKey cachedDataFeedKey = entry.getValue();
            patternsToRemove.addAll(NullSafe.list(cachedDataFeedKey.getFeedRegexPatterns()));
            return entry.getValue().isExpired();
        };

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
                entry.getValue().filter(DataFeedKey::isExpired).isPresent()));
        LOGGER.debug("Removed {} keyToDataFeedKeyMap entries", counter);

        // Remove unused patterns. It's possible a pattern is used by >1 DFK, but
        // it is not that much bother to recompile a pattern if needed. Easier than
        // working out which ones are not actually needed any more.
        counter.set(0);
        feedPatternCache.entrySet().removeIf(PredicateUtil.countingPredicate(counter, entry -> {
            final String pattern = entry.getKey();
            return patternsToRemove.contains(pattern);
        }));
    }

    @Override
    public void removeKeysForFile(final Path sourceFile) {
        if (sourceFile != null) {
            LOGGER.debug("Evicting dataFeedKeys for sourceFile {}", sourceFile);
            final AtomicInteger counter = new AtomicInteger();
            final Predicate<Entry<?, CachedDataFeedKey>> removeIfPredicate = entry -> {
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

    private boolean validateDataFeedKeyExpiry(final DataFeedKey dataFeedKey,
                                              final AttributeMap attributeMap) {
        if (dataFeedKey.isExpired()) {
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

    private Optional<DataFeedKey> lookupKey(final String key,
                                            final AttributeMap attributeMap) {

        // Try the cache first to save on the hashing cost.
        Optional<DataFeedKey> optDataFeedKey = keyToDataFeedKeyMap.get(key);
        if (optDataFeedKey == null) {
            // Not in cache,
            optDataFeedKey = getCacheKey(key)
                    .map(cacheKey -> {
                        Objects.requireNonNull(cacheKey);
                        final CachedDataFeedKey dataFeedKey = cacheKeyToDataFeedKeyMap.get(cacheKey);
                        LOGGER.debug("Lookup of cacheKey {}, found {}", cacheKey, dataFeedKey);
                        return dataFeedKey;
                    })
                    .map(CachedDataFeedKey::getDataFeedKey);
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

    @Override
    public boolean filter(final AttributeMap attributeMap, final UserIdentity userIdentity) {
        final String feedName = getAttribute(attributeMap, StandardHeaderArguments.FEED)
                .orElseThrow(() ->
                        new StroomStreamException(StroomStatusCode.FEED_MUST_BE_SPECIFIED, attributeMap));

        final CachedDataFeedKey dataFeedKey = subjectIdToDataFeedKeyMap.get(userIdentity.getSubjectId());

        Objects.requireNonNull(dataFeedKey, "dataFeedKey should not be null at this point");

        final List<String> feedRegexPatterns = dataFeedKey.getFeedRegexPatterns();
        if (NullSafe.hasItems(feedRegexPatterns)) {
            boolean isFeedNameValid = false;
            for (final String feedRegexPattern : feedRegexPatterns) {
                final Pattern pattern = feedPatternCache.computeIfAbsent(feedRegexPattern, Pattern::compile);
                if (pattern.matcher(feedName).matches()) {
                    // Feed matches one of the regexes, so we need to auto-create it (if enabled)
//                    ensureFeed(feedName);
                    isFeedNameValid = true;
                } else {
                    LOGGER.debug("feedName: '{}' does not match pattern: '{}'", feedName, feedRegexPattern);
                }
            }
            if (!isFeedNameValid) {
                LOGGER.debug(() -> LogUtil.message("No match on feedName '{}' with patterns [{}]",
                        feedName,
                        feedRegexPatterns.stream()
                                .map(pattern -> "'" + pattern + "'")
                                .collect(Collectors.joining(", "))));
                throw new StroomStreamException(StroomStatusCode.INVALID_FEED_NAME, attributeMap);
            } else {
                return true;
            }
        } else {
            LOGGER.debug("No feed patterns to match on, allowing it to continue");
            return true;
        }
    }

//    private void ensureFeed(final String feedName) {
//        final ReceiveDataConfig receiveDataConfig = receiveDataConfigProvider.get();
//        final AutoContentCreationConfig autoContentCreationConfig = receiveDataConfig.getAutoContentCreationConfig();
//        if (autoContentCreationConfig.isEnabled()) {
//
//        }
//
//    }

    @Override
    public Optional<UserIdentity> authenticate(final HttpServletRequest request,
                                               final AttributeMap attributeMap) {
        try {
            final Optional<UserIdentity> optUserIdentity = getDataFeedKey(request, attributeMap)
                    .map(dataFeedKey -> {
                        // Ensure the stream attributes from the data feed key are set in the attributeMap so
                        // that the AttributeMapFilters have access to them.
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


    // --------------------------------------------------------------------------------


    public enum DataFeedKeyHashAlgorithm implements HasDisplayValue {
        ARGON2("Argon2", 0),
//        BCRYPT("BCrypt", 0),
        ;

        private static final DataFeedKeyHashAlgorithm[] sparseArray;
        private static final Map<String, DataFeedKeyHashAlgorithm> nameToValueMap = Arrays.stream(values())
                .collect(Collectors.toMap(DataFeedKeyHashAlgorithm::getDisplayValue, Function.identity()));


        static {
            final DataFeedKeyHashAlgorithm[] values = DataFeedKeyHashAlgorithm.values();
            final int maxPrimitive = Arrays.stream(values)
                    .mapToInt(dataFeedKeyHashAlgorithm -> dataFeedKeyHashAlgorithm.uniqueId)
                    .max()
                    .orElseThrow(() -> new RuntimeException("Empty values array supplied"));
            sparseArray = new DataFeedKeyHashAlgorithm[maxPrimitive + 1];
            for (final DataFeedKeyHashAlgorithm value : values) {
                sparseArray[value.uniqueId] = value;
            }
        }

        private final String displayValue;
        private final int uniqueId;

        DataFeedKeyHashAlgorithm(final String displayValue, final int uniqueId) {
            if (uniqueId < 0) {
                throw new IllegalArgumentException("Min uniqueId is 0");
            }
            if (uniqueId > 999) {
                throw new IllegalArgumentException("Max uniqueId is 999");
            }
            this.displayValue = displayValue;
            this.uniqueId = uniqueId;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }

        /**
         * @return A 3 digit, zero padded number.
         */
        public String getUniqueId() {
            return Strings.padStart(String.valueOf(uniqueId), 3, '0');
        }

        public static DataFeedKeyHashAlgorithm fromDisplayValue(final String displayValue) {
            if (displayValue == null) {
                return null;
            } else if (NullSafe.isBlankString(displayValue)) {
                throw new IllegalArgumentException("Blank displayValue");
            } else {
                final DataFeedKeyHashAlgorithm hashAlgorithm = nameToValueMap.get(displayValue);
                if (hashAlgorithm == null) {
                    throw new IllegalArgumentException("Unknown displayValue " + displayValue);
                }
                return hashAlgorithm;
            }
        }

        public static DataFeedKeyHashAlgorithm fromUniqueId(final String uniqueId) {
            if (uniqueId == null) {
                return null;
            } else if (uniqueId.isBlank()) {
                throw new IllegalArgumentException("Blank uniqueId");
            } else {
                final int intVal = Integer.parseInt(uniqueId);
                DataFeedKeyHashAlgorithm dataFeedKeyHashAlgorithm;
                try {
                    dataFeedKeyHashAlgorithm = sparseArray[intVal];
                    if (dataFeedKeyHashAlgorithm == null) {
                        throw new IllegalArgumentException("Unknown uniqueId " + uniqueId);
                    }
                    return dataFeedKeyHashAlgorithm;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        @Override
        public String toString() {
            return "DataFeedKeyHashAlgorithm{" +
                    "displayValue='" + displayValue + '\'' +
                    ", uniqueId=" + uniqueId +
                    '}';
        }
    }


    // --------------------------------------------------------------------------------


//    private static class BCryptApiKeyHasher implements DataFeedKeyHasher {
//
//        @Override
//        public String hash(final String apiKeyStr) {
//            return BCrypt.hashpw(Objects.requireNonNull(apiKeyStr), BCrypt.gensalt());
//        }
//
//        @Override
//        public boolean verify(final String apiKeyStr, final String hash) {
//            if (apiKeyStr == null) {
//                return false;
//            } else {
//                return BCrypt.checkpw(apiKeyStr, hash);
//            }
//        }
//
//        @Override
//        public DataFeedKeyHashAlgorithm getAlgorithm() {
//            return DataFeedKeyHashAlgorithm.BCRYPT;
//        }
//    }
}
