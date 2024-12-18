package stroom.receive.common;

import stroom.docref.HasDisplayValue;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.security.api.UserIdentity;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.string.Base58;

import com.google.common.base.Strings;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.bouncycastle.crypto.params.Argon2Parameters.Builder;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    private final Map<CacheKey, DataFeedKey> cacheKeyTodataFeedKeyMap = new ConcurrentHashMap<>();
    private final Map<String, DataFeedKey> subjectIdToDataFeedKeyMap = new ConcurrentHashMap<>();

    // TODO replace with cache
    private final Map<String, Pattern> feedPatternCache = new HashMap<>();

    // TODO replace with cache
    // Cache of the un-hashed key to validated DataFeedKey, to save us the hashing cost
    private final Map<String, Optional<DataFeedKey>> keyToDataFeedKeyMap = new HashMap<>();


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
                .flatMap((String key2) -> lookupKey(key2, attributeMap));

        optDataFeedKey.ifPresent(dataFeedKey -> {

            validateDataFeedKeyExpiry(dataFeedKey, attributeMap);

            // Add the user identity to the attributeMap so that we can use it for request filtering
            // later
            NullSafe.consume(dataFeedKey.getSubjectId(), id ->
                    attributeMap.put(StandardHeaderArguments.UPLOAD_USER_ID, id));
            NullSafe.consume(dataFeedKey.getDisplayName(), username ->
                    attributeMap.put(StandardHeaderArguments.UPLOAD_USERNAME, username));
        });

        // Remove authorization header from attributes as it should not be stored or forwarded on.
        attributeMap.remove(AUTHORIZATION_HEADER);

        return optDataFeedKey;
    }

    private void addDataFeedKeys(final DataFeedKeys dataFeedKeys) {
        if (dataFeedKeys != null) {
            dataFeedKeys.getDataFeedKeys()
                    .forEach(this::addDataFeedKey);
        }
    }

    private void addDataFeedKey(final DataFeedKey dataFeedKey) {
        if (dataFeedKey != null) {
            final String hash = dataFeedKey.getHash();
            final String hashAlgorithmName = dataFeedKey.getHashAlgorithm();
            final DataFeedKeyHashAlgorithm hashAlgorithm = DataFeedKeyHashAlgorithm.fromDisplayValue(
                    hashAlgorithmName);
            CacheKey cacheKey = new CacheKey(hashAlgorithm, hash);
            cacheKeyTodataFeedKeyMap.put(cacheKey, dataFeedKey);
            subjectIdToDataFeedKeyMap.put(dataFeedKey.getSubjectId(), dataFeedKey);
        }
    }

    private void evictExpiredKeys() {
        cacheKeyTodataFeedKeyMap.entrySet().removeIf(entry ->
                entry.getValue().isExpired());
        subjectIdToDataFeedKeyMap.entrySet().removeIf(entry ->
                entry.getValue().isExpired());
    }

    private void validateDataFeedKeyExpiry(final DataFeedKey dataFeedKey,
                                           final AttributeMap attributeMap) {
        if (dataFeedKey.isExpired()) {
            throw new StroomStreamException(
                    StroomStatusCode.DATA_FEED_KEY_NOT_AUTHENTICATED, attributeMap);
        }
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
            return Optional.empty();
        }
    }

    private Optional<DataFeedKey> lookupKey(final String key,
                                            final AttributeMap attributeMap) {

        // Try the cache first to save on the hashing cost.
        Optional<DataFeedKey> optDataFeedKey = keyToDataFeedKeyMap.get(key);
        if (optDataFeedKey.isPresent()) {
            final DataFeedKey dataFeedKey = optDataFeedKey.get();
            validateDataFeedKeyExpiry(dataFeedKey, attributeMap);
            return Optional.of(dataFeedKey);
        } else {
            optDataFeedKey = getCacheKey(key)
                    .map(cacheKey -> {
                        Objects.requireNonNull(cacheKey);
                        final DataFeedKey dataFeedKey = cacheKeyTodataFeedKeyMap.get(cacheKey);
                        LOGGER.debug("Lookup of cacheKey {}, found {}", cacheKey, dataFeedKey);
                        return dataFeedKey;
                    });
            // Cache it to save hashing next time
            keyToDataFeedKeyMap.put(key, optDataFeedKey);
            return optDataFeedKey;
        }
    }

    private Optional<String> getAttribute(final AttributeMap attributeMap, final String header) {
        return Optional.ofNullable(attributeMap.get(header))
                .filter(str -> !NullSafe.isNonBlankString(str));
    }

    @Override
    public boolean filter(final AttributeMap attributeMap, final UserIdentity userIdentity) {
        final String feedName = getAttribute(attributeMap, StandardHeaderArguments.FEED)
                .orElseThrow(() ->
                        new StroomStreamException(StroomStatusCode.FEED_MUST_BE_SPECIFIED, attributeMap));

        final DataFeedKey dataFeedKey = subjectIdToDataFeedKeyMap.get(userIdentity.getSubjectId());
        Objects.requireNonNull(dataFeedKey, "dataFeedKey should not be null at this point");

        final List<String> feedRegexPatterns = dataFeedKey.getFeedRegexPatterns();
        if (NullSafe.hasItems(feedRegexPatterns)) {
            for (final String feedRegexPattern : feedRegexPatterns) {
                final Pattern pattern = feedPatternCache.get(feedRegexPattern);
                if (pattern.matcher(feedName).matches()) {
                    return true;
                }
            }
            LOGGER.debug(() -> LogUtil.message("No match on feedName '{}' with patterns [{}]",
                    feedName,
                    feedRegexPatterns.stream()
                            .map(pattern -> "'" + pattern + "'")
                            .collect(Collectors.joining(", "))));
            throw new StroomStreamException(StroomStatusCode.INVALID_FEED_NAME, attributeMap);
        } else {
            LOGGER.debug("No feed patterns to match on, allowing it to continue");
            return true;
        }
    }

    @Override
    public Optional<UserIdentity> authenticate(final HttpServletRequest request,
                                               final AttributeMap attributeMap) {
        return getDataFeedKey(request, attributeMap)
                .map(DataFeedKeyUserIdentity::new);
    }

    // --------------------------------------------------------------------------------


    private record CacheKey(DataFeedKeyHashAlgorithm dataFeedKeyHashAlgorithm,
                            String hash) {

    }


    // --------------------------------------------------------------------------------


    public enum DataFeedKeyHashAlgorithm implements HasDisplayValue {
        BCRYPT("BCrypt", 0),
        ARGON2("Argon2", 1),
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
            return null;
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


    interface DataFeedKeyHasher {

        String hash(String dataFeedKey);

//        default boolean verify(String apiKeyStr, String hash) {
//            final String computedHash = hash(Objects.requireNonNull(apiKeyStr));
//            return Objects.equals(Objects.requireNonNull(hash), computedHash);
//        }

        DataFeedKeyHashAlgorithm getAlgorithm();
    }


    // --------------------------------------------------------------------------------


    private static class Argon2DataFeedKeyHasher implements DataFeedKeyHasher {

        // WARNING!!!
        // Do not change any of these otherwise it will break hash verification of existing
        // keys. If you want to tune it, make a new ApiKeyHasher impl with a new getType()
        // 48, 2, 65_536, 1 => ~90ms per hash
        private static final int HASH_LENGTH = 48;
        private static final int ITERATIONS = 2;
        private static final int MEMORY_KB = 65_536;
        private static final int PARALLELISM = 1;

        private final Argon2Parameters argon2Parameters;

        public Argon2DataFeedKeyHasher() {
            // No salt given the length of api keys being hashed
            this.argon2Parameters = new Builder(Argon2Parameters.ARGON2_id)
                    .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                    .withIterations(ITERATIONS)
                    .withMemoryAsKB(MEMORY_KB)
                    .withParallelism(PARALLELISM)
                    .build();
        }

        @Override
        public String hash(final String dataFeedKey) {
            Objects.requireNonNull(dataFeedKey);
            Argon2BytesGenerator generate = new Argon2BytesGenerator();
            generate.init(argon2Parameters);
            byte[] result = new byte[HASH_LENGTH];
            generate.generateBytes(
                    dataFeedKey.trim().getBytes(StandardCharsets.UTF_8),
                    result,
                    0,
                    result.length);

            // Base58 is a bit less nasty than base64 and widely supported in other languages
            // due to use in bitcoin.
            return Base58.encode(result);
        }

        @Override
        public DataFeedKeyHashAlgorithm getAlgorithm() {
            return DataFeedKeyHashAlgorithm.ARGON2;
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
