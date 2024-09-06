package stroom.security.impl.apikey;

import stroom.cache.api.CacheManager;
import stroom.cache.api.StroomCache;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.common.impl.JwtUtil;
import stroom.security.impl.AuthenticationConfig;
import stroom.security.impl.BasicUserIdentity;
import stroom.security.impl.HashedApiKeyParts;
import stroom.security.shared.ApiKeyResultPage;
import stroom.security.shared.CreateHashedApiKeyRequest;
import stroom.security.shared.CreateHashedApiKeyResponse;
import stroom.security.shared.FindApiKeyCriteria;
import stroom.security.shared.HashedApiKey;
import stroom.security.shared.PermissionNames;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.PermissionException;
import stroom.util.shared.UserName;
import stroom.util.string.Base58;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.HttpHeaders;
import org.apache.commons.codec.digest.DigestUtils;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.bouncycastle.crypto.params.Argon2Parameters.Builder;
import org.mindrot.jbcrypt.BCrypt;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton // Has a cache
public class ApiKeyService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ApiKeyService.class);
    private static final String CACHE_NAME = "API Key cache";
    private static final int MAX_CREATION_ATTEMPTS = 100;
    private static final Map<HashAlgorithm, ApiKeyHasher> API_KEY_HASHER_MAP = Stream.of(
                    new Sha3256ApiKeyHasher(),
                    new BCryptApiKeyHasher(),
                    new Argon2ApiKeyHasher())
            .collect(Collectors.toMap(ApiKeyHasher::getType, Function.identity()));

    static {
        // Make sure all enum values have an associated impl
        final Set<HashAlgorithm> keySet = API_KEY_HASHER_MAP.keySet();
        for (final HashAlgorithm hashAlgorithm : HashAlgorithm.values()) {
            if (!keySet.contains(hashAlgorithm)) {
                throw new RuntimeException("No ApiKeyHasher implementation defined for algorithm " + hashAlgorithm);
            }
        }
    }

    private final ApiKeyDao apiKeyDao;
    private final SecurityContext securityContext;
    private final ApiKeyGenerator apiKeyGenerator;
    // The full apiKeyStr to an Authenticated UserIdentity
    // Short life cache to reduce hashing time/cost
    private final StroomCache<String, Optional<UserIdentity>> apiKeyToAuthenticatedUserCache;
    private final Provider<AuthenticationConfig> authenticationConfigProvider;

    @Inject
    public ApiKeyService(final ApiKeyDao apiKeyDao,
                         final SecurityContext securityContext,
                         final ApiKeyGenerator apiKeyGenerator,
                         final CacheManager cacheManager,
                         final Provider<AuthenticationConfig> authenticationConfigProvider) {
        this.apiKeyDao = apiKeyDao;
        this.securityContext = securityContext;
        this.apiKeyGenerator = apiKeyGenerator;
        this.authenticationConfigProvider = authenticationConfigProvider;

        apiKeyToAuthenticatedUserCache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> authenticationConfigProvider.get().getApiKeyCache(),
                this::doFetchVerifiedIdentity);
    }

    public ApiKeyResultPage find(final FindApiKeyCriteria criteria) {
        return securityContext.secureResult(PermissionNames.MANAGE_API_KEYS, () -> {
            checkAdditionalPerms(criteria.getOwner());
            return apiKeyDao.find(criteria);
        });
    }

    /**
     * Fetch the verified {@link UserIdentity} for the passed API key.
     * If the hash of the API key matches one in the database and that key is enabled
     * and not expired then the {@link UserIdentity} will be returned, else an empty {@link Optional}
     * is returned.
     */
    public Optional<UserIdentity> fetchVerifiedIdentity(final HttpServletRequest request) {
        final String authHeaderVal = NullSafe.get(
                request.getHeader(HttpHeaders.AUTHORIZATION),
                header -> header.replace(JwtUtil.BEARER_PREFIX, ""));

        // We need to do a basic check to see if it looks like an API key else we will fill the cache with
        // JWT tokens mapped to empty Optionals.
        if (!NullSafe.isBlankString(authHeaderVal)
                && authHeaderVal.startsWith(ApiKeyGenerator.API_KEY_STATIC_PREFIX)) {
            return fetchVerifiedIdentity(authHeaderVal);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Fetch the verified {@link UserIdentity} for the passed API key.
     * If the hash of the API key matches one in the database and that key is enabled
     * and not expired then the {@link UserIdentity} will be returned, else an empty {@link Optional}
     * is returned.
     */
    public Optional<UserIdentity> fetchVerifiedIdentity(final String apiKeyStr) {
        if (NullSafe.isBlankString(apiKeyStr)) {
            return Optional.empty();
        } else {
            return apiKeyToAuthenticatedUserCache.get(apiKeyStr);
        }
    }

    private Optional<UserIdentity> doFetchVerifiedIdentity(final String apiKeyStr) {
        // This has to be unsecured as we are trying to authenticate
        if (!apiKeyGenerator.isApiKey(apiKeyStr)) {
            LOGGER.debug("apiKey '{}' is not an API key", apiKeyStr);
            return Optional.empty();
        } else {
            final String prefix = ApiKeyGenerator.extractPrefixPart(apiKeyStr);

            final List<HashedApiKey> apiKeys = apiKeyDao.fetchValidApiKeysByPrefix(prefix);

            LOGGER.debug("Found {} API key(s) matching prefix: '{}'",
                    NullSafe.size(apiKeys), prefix);

            if (apiKeys.isEmpty()) {
                LOGGER.debug("No valid API keys found matching prefix '{}'", prefix);
                return Optional.empty();
            } else {
                Optional<UserIdentity> optUserIdentity = Optional.empty();

                // In 99.99% of the time there will be only one apiKey fetched
                for (final HashedApiKey apiKey : apiKeys) {
                    // TODO change to get the hash algo from the HashedApiKey
                    final boolean isHashMatch = verifyApiKeyHash(
                            apiKeyStr,
                            apiKey.getApiKeyHash(),
                            HashAlgorithm.SHA3_256);
                    if (isHashMatch) {
                        optUserIdentity = Optional.of(apiKey.getOwner())
                                .map(BasicUserIdentity::new);
                        LOGGER.debug("optUserIdentity: {}", optUserIdentity);
                        break;
                    }
                }
                return optUserIdentity;
            }
        }
    }

    public Optional<HashedApiKey> fetch(final int id) {
        return securityContext.secureResult(PermissionNames.MANAGE_API_KEYS, () -> {
            final Optional<HashedApiKey> optApiKey = apiKeyDao.fetch(id);

            optApiKey.ifPresent(apiKey ->
                    checkAdditionalPerms(apiKey.getOwner()));
            return optApiKey;
        });
    }

    public CreateHashedApiKeyResponse create(final CreateHashedApiKeyRequest createHashedApiKeyRequest) {
        Objects.requireNonNull(createHashedApiKeyRequest);

        return securityContext.secureResult(PermissionNames.MANAGE_API_KEYS, () -> {
            checkAdditionalPerms(createHashedApiKeyRequest.getOwner());
            // We want both a unique prefix and hash so keep generating new keys till we
            // get one that is unique on both
            int attempts = 0;
            do {
                attempts++;
                try {
                    return createNewApiKey(createHashedApiKeyRequest);
                } catch (DuplicateApiKeyException e) {
                    LOGGER.debug("Duplicate hash/prefix on attempt {}, going round again", attempts);
                }
            } while (attempts < MAX_CREATION_ATTEMPTS);

            throw new RuntimeException(LogUtil.message("Unable to create API key with unique prefix and hash " +
                    "after {} attempts", attempts));
        });
    }

    private CreateHashedApiKeyResponse createNewApiKey(final CreateHashedApiKeyRequest createHashedApiKeyRequest)
            throws DuplicateApiKeyException {
        LOGGER.debug(() -> LogUtil.message("Attempting to create new API key for {}",
                createHashedApiKeyRequest.getOwner()));
        Objects.requireNonNull(createHashedApiKeyRequest.getName(), "name cannot be null");
        Objects.requireNonNull(createHashedApiKeyRequest.getOwner(), "owner cannot be null");

        final CreateHashedApiKeyRequest request = ensureExpireTimeEpochMs(createHashedApiKeyRequest);

        final String apiKeyStr = apiKeyGenerator.generateRandomApiKey();
        final String apiKeyHash = computeApiKeyHash(apiKeyStr);
        final HashedApiKeyParts hashedApiKeyParts = new HashedApiKeyParts(
                apiKeyHash,
                ApiKeyGenerator.extractPrefixPart(apiKeyStr));

        final HashedApiKey hashedApiKey = apiKeyDao.create(
                request,
                hashedApiKeyParts);

        LOGGER.debug(() -> LogUtil.message("Created new API key for {}", createHashedApiKeyRequest.getOwner()));
        return new CreateHashedApiKeyResponse(apiKeyStr, hashedApiKey);
    }

    private CreateHashedApiKeyRequest ensureExpireTimeEpochMs(final CreateHashedApiKeyRequest request) {
        final AuthenticationConfig authenticationConfig = authenticationConfigProvider.get();
        final Duration maxApiKeyExpiryAge = authenticationConfig.getMaxApiKeyExpiryAge()
                .getDuration();

        final Long expireTimeEpochMs = request.getExpireTimeMs();
        final Instant now = Instant.now();
        final long maxExpireTimeEpochMs = now
                .plus(maxApiKeyExpiryAge)
                .plusSeconds(60) // Add 60s to allow for time elapsed since req was created
                .toEpochMilli();

        if (expireTimeEpochMs != null) {
            if (expireTimeEpochMs < now.toEpochMilli()) {
                throw new RuntimeException(LogUtil.message("Requested key expireTime {} is in the past.",
                        Instant.ofEpochMilli(expireTimeEpochMs)));
            }
            if (expireTimeEpochMs > maxExpireTimeEpochMs) {
                throw new RuntimeException(LogUtil.message("Requested key expireTime {} ({}) is after the configured " +
                                "maximum expireTime {} ({})",
                        Instant.ofEpochMilli(expireTimeEpochMs),
                        Duration.ofMillis(expireTimeEpochMs - now.toEpochMilli()),
                        Instant.ofEpochMilli(maxExpireTimeEpochMs),
                        maxApiKeyExpiryAge));
            }
            return request;
        } else {
            final long expireTimeEpochMs2 = Instant.now()
                    .plus(maxApiKeyExpiryAge)
                    .toEpochMilli();
            return CreateHashedApiKeyRequest.builder(request)
                    .withExpireTimeMs(expireTimeEpochMs2)
                    .build();
        }
    }

    public HashedApiKey update(final HashedApiKey apiKey) {
        return securityContext.secureResult(PermissionNames.MANAGE_API_KEYS, () -> {
            checkAdditionalPerms(apiKey.getOwner());
            final HashedApiKey apiKeyBefore = apiKeyDao.fetch(apiKey.getId())
                    .orElseThrow(() -> new RuntimeException("API Key not found with ID " + apiKey.getId()));
            if (apiKeyBefore.getEnabled() != apiKey.getEnabled()) {
                // Enabled state has changed so invalidate the cache
                invalidateApiKeyCacheEntry(apiKeyBefore);
            }
            return apiKeyDao.update(apiKey);
        });
    }

    public boolean delete(final int id) {
        return securityContext.secureResult(PermissionNames.MANAGE_API_KEYS, () -> {
            final Optional<HashedApiKey> optApiKey = fetch(id);
            if (optApiKey.isPresent()) {
                final HashedApiKey apiKey = optApiKey.get();
                checkAdditionalPerms(apiKey.getOwner());
                final boolean didDelete = apiKeyDao.delete(id);
                invalidateApiKeyCacheEntry(apiKey);
                return didDelete;
            } else {
                LOGGER.debug("Nothing to delete");
                return false;
            }
        });
    }

    private void invalidateApiKeyCacheEntry(final HashedApiKey apiKey) {
        if (apiKey != null) {
            final String apiKeyPrefix = apiKey.getApiKeyPrefix();
            // It's possible there are >1 entries with the same prefix, but that is lottery odds
            // so just invalidate any that match, and they can be re-loaded if needed.
            // We can't invalidate by key as we don't store the api key str.
            apiKeyToAuthenticatedUserCache.invalidateEntries((apiKeyStr, optUser) ->
                    apiKeyStr.startsWith(apiKeyPrefix));
        }
    }

    public int deleteBatch(final Collection<Integer> ids) {
        return securityContext.secureResult(PermissionNames.MANAGE_API_KEYS, () -> {
            // Would be quicker to delete en-mass, but deleting multiple won't happen that often
            // and this deals with cache invalidation and extra perm checks
            final int deleteCount = NullSafe.stream(ids)
                    .mapToInt(id -> {
                        final boolean didDelete = delete(id);
                        return didDelete
                                ? 1
                                : 0;
                    })
                    .sum();
            return deleteCount;
        });
    }

    String computeApiKeyHash(final String apiKeyStr) {
        return computeApiKeyHash(apiKeyStr, HashAlgorithm.SHA3_256);
    }

    String computeApiKeyHash(final String apiKeyStr, final HashAlgorithm hashAlgorithm) {
        Objects.requireNonNull(apiKeyStr);
        Objects.requireNonNull(hashAlgorithm);
        final ApiKeyHasher apiKeyHasher = getApiKeyHasher(hashAlgorithm);
        return apiKeyHasher.hash(apiKeyStr.trim());
    }

    private static ApiKeyHasher getApiKeyHasher(final HashAlgorithm hashAlgorithm) {
        final ApiKeyHasher apiKeyHasher = API_KEY_HASHER_MAP.get(hashAlgorithm);
        Objects.requireNonNull(apiKeyHasher, () -> "No ApiKeyHasher implementation for algorithm " + hashAlgorithm);
        return apiKeyHasher;
    }

    boolean verifyApiKeyHash(final String apiKeyStr, final String hash, final HashAlgorithm hashAlgorithm) {
        Objects.requireNonNull(apiKeyStr);
        Objects.requireNonNull(hash);
        Objects.requireNonNull(hashAlgorithm);
        final ApiKeyHasher apiKeyHasher = getApiKeyHasher(hashAlgorithm);
        return apiKeyHasher.verify(apiKeyStr, hash);
    }

    private void checkAdditionalPerms(final UserName owner) {

        if (!securityContext.isAdmin()) {
            if (owner == null
                    || (!Objects.equals(securityContext.getSubjectId(), owner.getSubjectId())
                    && !Objects.equals(securityContext.getUserUuid(), owner.getUuid()))) {
                // logged-in user is not the same as the owner of the key(s) so more perms needed
                if (!securityContext.hasAppPermission(PermissionNames.MANAGE_USERS_PERMISSION)) {
                    throw new PermissionException(
                            securityContext.getUserIdentityForAudit(),
                            LogUtil.message("'{}' permission is additionally required to manage " +
                                    "the API keys of other users.", PermissionNames.MANAGE_USERS_PERMISSION));
                }
            }
        }
    }


    // --------------------------------------------------------------------------------


    /**
     * Thrown when an API key is created that has the same prefix + hash as another key.
     */
    public static class DuplicateApiKeyException extends Exception {

        public DuplicateApiKeyException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }


    // --------------------------------------------------------------------------------


    public enum HashAlgorithm {
        SHA3_256("SHA3-256"),
        BCRYPT("BCrypt"),
        ARGON_2("Argon2");

        private final String displayName;

        HashAlgorithm(final String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private interface ApiKeyHasher {

        String hash(String apiKeyStr);

        default boolean verify(String apiKeyStr, String hash) {
            final String computedHash = hash(Objects.requireNonNull(apiKeyStr));
            return Objects.equals(Objects.requireNonNull(hash), computedHash);
        }

        HashAlgorithm getType();
    }

    private static class Sha3256ApiKeyHasher implements ApiKeyHasher {

        @Override
        public String hash(final String apiKeyStr) {
            final String sha256 = DigestUtils.sha3_256Hex(apiKeyStr.trim())
                    .trim();
            return sha256;
        }

        @Override
        public HashAlgorithm getType() {
            return HashAlgorithm.SHA3_256;
        }
    }

    private static class BCryptApiKeyHasher implements ApiKeyHasher {

        @Override
        public String hash(final String apiKeyStr) {
            return BCrypt.hashpw(Objects.requireNonNull(apiKeyStr), BCrypt.gensalt());
        }

        @Override
        public boolean verify(final String apiKeyStr, final String hash) {
            if (apiKeyStr == null) {
                return false;
            } else {
                return BCrypt.checkpw(apiKeyStr, hash);
            }
        }

        @Override
        public HashAlgorithm getType() {
            return HashAlgorithm.BCRYPT;
        }
    }

    private static class Argon2ApiKeyHasher implements ApiKeyHasher {

        // 48, 2, 65_536, 1 => ~90ms per hash
        private static final int HASH_LENGTH = 48;
        private static final int ITERATIONS = 2;
        private static final int MEMORY_KB = 65_536;
        private static final int PARALLELISM = 1;

        private final Argon2Parameters argon2Parameters;

        public Argon2ApiKeyHasher() {
            // No salt given the length of api keys being hashed
            this.argon2Parameters = new Builder(Argon2Parameters.ARGON2_id)
                    .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                    .withIterations(ITERATIONS)
                    .withMemoryAsKB(MEMORY_KB)
                    .withParallelism(PARALLELISM)
                    .build();
        }

        @Override
        public String hash(final String apiKeyStr) {
            Objects.requireNonNull(apiKeyStr);
            Argon2BytesGenerator generate = new Argon2BytesGenerator();
            generate.init(argon2Parameters);
            byte[] result = new byte[HASH_LENGTH];
            generate.generateBytes(
                    apiKeyStr.trim().getBytes(StandardCharsets.UTF_8),
                    result,
                    0,
                    result.length);

            // Base58 is a bit less nasty than base64 and widely supported in other languages
            // due to use in bitcoin.
            return Base58.encode(result);
        }

        @Override
        public HashAlgorithm getType() {
            return HashAlgorithm.ARGON_2;
        }
    }
}
