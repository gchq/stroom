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

package stroom.security.impl.apikey;

import stroom.cache.api.CacheManager;
import stroom.cache.api.StroomCache;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.api.exception.AuthenticationException;
import stroom.security.common.impl.ApiKeyGenerator;
import stroom.security.common.impl.JwtUtil;
import stroom.security.impl.AuthenticationConfig;
import stroom.security.impl.BasicUserIdentity;
import stroom.security.impl.HashedApiKeyParts;
import stroom.security.impl.UserCache;
import stroom.security.shared.AppPermission;
import stroom.security.shared.AppPermissionSet;
import stroom.security.shared.CreateHashedApiKeyRequest;
import stroom.security.shared.CreateHashedApiKeyResponse;
import stroom.security.shared.FindApiKeyCriteria;
import stroom.security.shared.HashAlgorithm;
import stroom.security.shared.HashedApiKey;
import stroom.security.shared.User;
import stroom.security.shared.VerifyApiKeyRequest;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserDesc;
import stroom.util.shared.UserRef;
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
    private static final AppPermissionSet REQUIRED_PERMISSION_SET = AppPermissionSet.oneOf(
            AppPermission.VERIFY_API_KEY,
            AppPermission.STROOM_PROXY);

    private static final String CACHE_NAME = "API Key cache";
    private static final int MAX_CREATION_ATTEMPTS = 100;
    private static final Map<HashAlgorithm, ApiKeyHasher> API_KEY_HASHER_MAP = Stream.of(
                    new ShaThree256ApiKeyHasher(),
                    new ShaTwo256ApiKeyHasher(),
                    new BCryptApiKeyHasher(),
                    new Argon2ApiKeyHasher(),
                    new ShaTwo512ApiKeyHasher())
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
    private final UserCache userCache;

    @Inject
    public ApiKeyService(final ApiKeyDao apiKeyDao,
                         final SecurityContext securityContext,
                         final ApiKeyGenerator apiKeyGenerator,
                         final CacheManager cacheManager,
                         final Provider<AuthenticationConfig> authenticationConfigProvider,
                         final UserCache userCache) {
        this.apiKeyDao = apiKeyDao;
        this.securityContext = securityContext;
        this.apiKeyGenerator = apiKeyGenerator;
        this.authenticationConfigProvider = authenticationConfigProvider;
        this.userCache = userCache;

        apiKeyToAuthenticatedUserCache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> authenticationConfigProvider.get().getApiKeyCache(),
                this::doFetchVerifiedIdentity);
    }

    public ResultPage<HashedApiKey> find(final FindApiKeyCriteria criteria) {
        return securityContext.secureResult(AppPermission.MANAGE_API_KEYS, () -> {
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
        final String token = NullSafe.get(
                request.getHeader(HttpHeaders.AUTHORIZATION),
                header -> header.replace(JwtUtil.BEARER_PREFIX, ""));

        // We need to do a basic check to see if it looks like an API key else we will fill the cache with
        // JWT tokens mapped to empty Optionals.
        if (!NullSafe.isBlankString(token)
            && token.startsWith(ApiKeyGenerator.API_KEY_STATIC_PREFIX)) {

            final Optional<UserIdentity> optUserIdentity = apiKeyToAuthenticatedUserCache.get(token);

            if (optUserIdentity.isEmpty() && apiKeyGenerator.isApiKey(token)) {
                // Stops the next filter from trying to authenticate it
                throw new AuthenticationException("API key failed authentication");
            }
            return optUserIdentity;
        } else {
            return Optional.empty();
        }
    }

    public Optional<UserDesc> verifyApiKey(final VerifyApiKeyRequest request) {
        return securityContext.secureResult(REQUIRED_PERMISSION_SET, () -> {
            final Optional<UserDesc> optUserDesc = fetchVerifiedIdentity(request.getApiKey())
                    .filter(userIdentity -> {
                        final AppPermissionSet requiredAppPermissions = request.getRequiredAppPermissions();
                        if (AppPermissionSet.isEmpty(requiredAppPermissions)) {
                            return true;
                        } else {
                            return securityContext.hasAppPermissions(userIdentity, requiredAppPermissions);
                        }
                    })
                    .map(UserIdentity::asUserDesc);
            LOGGER.debug("verifyApiKey() - request: {}, optUserDesc: {}", request, optUserDesc);
            return optUserDesc;
        });
    }

    /**
     * Fetch the verified {@link UserIdentity} for the passed API key.
     * If the hash of the API key matches one in the database and that key is enabled
     * and not expired then the {@link UserIdentity} will be returned, else an empty {@link Optional}
     * is returned.
     */
    Optional<UserIdentity> fetchVerifiedIdentity(final String apiKeyStr) {
        if (NullSafe.isBlankString(apiKeyStr)) {
            return Optional.empty();
        } else {
            return apiKeyToAuthenticatedUserCache.get(apiKeyStr);
        }
    }

    private Optional<UserIdentity> doFetchVerifiedIdentity(final String apiKeyStr) {
        // This has to be unsecured as we are trying to authenticate
        if (!apiKeyGenerator.isApiKey(apiKeyStr)) {
            LOGGER.debug("apiKey is not an API key");
            return Optional.empty();
        } else {
            final String prefix = ApiKeyGenerator.extractPrefixPart(apiKeyStr);

            final List<HashedApiKey> apiKeys = apiKeyDao.fetchValidApiKeysByPrefix(prefix);

            if (apiKeys.isEmpty()) {
                LOGGER.debug("No valid API keys found matching prefix '{}'", prefix);
                return Optional.empty();
            } else {
                Optional<UserIdentity> optUserIdentity = Optional.empty();
                // In most cases, there will be only one apiKey fetched for a given prefix
                // as the chance of a prefix clash is ~1:1,000,000. If there are multiple,
                // then we just test each one using its algorithm to see if the stored hash
                // matches the hash of the passed api key.
                for (final HashedApiKey apiKey : apiKeys) {
                    final boolean isHashMatch = verifyApiKeyHash(
                            apiKeyStr,
                            apiKey.getApiKeyHash(),
                            apiKey.getHashAlgorithm());
                    if (isHashMatch) {
                        final Optional<User> optionalUser = Optional.ofNullable(apiKey.getOwner())
                                .flatMap(userCache::getByRef);
                        optUserIdentity = optionalUser
                                .map(user -> {
                                    verifyEnabledOrThrow(user);
                                    return user.asRef();
                                })
                                .map(BasicUserIdentity::new);
                        LOGGER.debug("optUserIdentity: {}", optUserIdentity);
                        break;
                    }
                }
                LOGGER.debug("Found {} valid API key(s) matching prefix: '{}', matched identity: {}",
                        apiKeys.size(), prefix, optUserIdentity);
                return optUserIdentity;
            }
        }
    }

    public Optional<HashedApiKey> fetch(final int id) {
        return securityContext.secureResult(AppPermission.MANAGE_API_KEYS, () -> {
            final Optional<HashedApiKey> optApiKey = apiKeyDao.fetch(id);

            optApiKey.ifPresent(apiKey ->
                    checkAdditionalPerms(apiKey.getOwner()));
            return optApiKey;
        });
    }

    public CreateHashedApiKeyResponse create(final CreateHashedApiKeyRequest createHashedApiKeyRequest) {
        Objects.requireNonNull(createHashedApiKeyRequest);

        return securityContext.secureResult(AppPermission.MANAGE_API_KEYS, () -> {
            checkAdditionalPerms(createHashedApiKeyRequest.getOwner());
            // We want both a unique prefix and hash so keep generating new keys till we
            // get one that is unique on both
            int attempts = 0;
            do {
                attempts++;
                try {
                    return createNewApiKey(createHashedApiKeyRequest);
                } catch (final DuplicateApiKeyException e) {
                    LOGGER.debug("Duplicate hash on attempt {}, going round again", attempts);
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
        final HashAlgorithm hashAlgorithm = Objects.requireNonNull(
                createHashedApiKeyRequest.getHashAlgorithm());
        final String apiKeyHash = computeApiKeyHash(apiKeyStr, hashAlgorithm);
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
        return securityContext.secureResult(AppPermission.MANAGE_API_KEYS, () -> {
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
        return securityContext.secureResult(AppPermission.MANAGE_API_KEYS, () -> {
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
        return securityContext.secureResult(AppPermission.MANAGE_API_KEYS, () -> {
            // Would be quicker to delete en-mass, but deleting multiple won't happen that often
            // and this deals with cache invalidation and extra perm checks
            return NullSafe.stream(ids)
                    .mapToInt(id -> {
                        final boolean didDelete = delete(id);
                        return didDelete
                                ? 1
                                : 0;
                    })
                    .sum();
        });
    }

    String computeApiKeyHash(final String apiKeyStr) {
        return computeApiKeyHash(apiKeyStr, HashAlgorithm.DEFAULT);
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

    boolean verifyApiKeyHash(final String apiKeyStr,
                             final String hash,
                             final HashAlgorithm hashAlgorithm) {
        Objects.requireNonNull(apiKeyStr);
        Objects.requireNonNull(hash);
        Objects.requireNonNull(hashAlgorithm);
        final ApiKeyHasher apiKeyHasher = getApiKeyHasher(hashAlgorithm);
        try {
            return apiKeyHasher.verify(apiKeyStr, hash);
        } catch (final Exception e) {
            LOGGER.debug("Error verifying hash '{}' with algorithm: {}", hash, hashAlgorithm, e);
            // Swallow it.
            // Bcrypt for example, includes details of the salt in the key, so if the key is rubbish
            // then it won't be able to extract the salt to gen the hash and will throw. If it throws
            // then it can't be a valid key.
            return false;
        }
    }

    private void checkAdditionalPerms(final UserRef owner) {
        if (!securityContext.isAdmin()) {
            if (owner == null || !Objects.equals(securityContext.getUserRef(), owner)) {
                // logged-in user is not the same as the owner of the key(s) so more perms needed
                if (!securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)) {
                    throw new PermissionException(
                            securityContext.getUserRef(),
                            LogUtil.message("'{}' permission is additionally required to manage " +
                                            "the API keys of other users.", AppPermission.MANAGE_USERS_PERMISSION));
                }
            }
        }
    }

    /**
     * @param user The user to check
     * @throws AuthenticationException if user is disabled.
     */
    private void verifyEnabledOrThrow(final User user) {
        if (!user.isEnabled()) {
            LOGGER.warn("Disabled user '{}' attempted API key authentication. {}",
                    user.getDisplayName(), user);
            throw new AuthenticationException(LogUtil.message("User '{}' is disabled.",
                    user.getDisplayName()));
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


    /**
     * These hashers were written before {@link stroom.security.api.HashFunction} and differ
     * slightly (even though they both share the same, so they can stay here just for api key use.
     */
    private interface ApiKeyHasher {

        String hash(String apiKeyStr);

        default boolean verify(final String apiKeyStr, final String hash) {
            final String computedHash = hash(Objects.requireNonNull(apiKeyStr));
            return Objects.equals(Objects.requireNonNull(hash), computedHash);
        }

        HashAlgorithm getType();
    }


    // --------------------------------------------------------------------------------


    private static class ShaThree256ApiKeyHasher implements ApiKeyHasher {

        @Override
        public String hash(final String apiKeyStr) {
            return DigestUtils.sha3_256Hex(apiKeyStr.trim())
                    .trim();
        }

        @Override
        public HashAlgorithm getType() {
            return HashAlgorithm.SHA3_256;
        }
    }


    // --------------------------------------------------------------------------------


    private static class ShaTwo256ApiKeyHasher implements ApiKeyHasher {

        @Override
        public String hash(final String apiKeyStr) {
            return DigestUtils.sha256Hex(apiKeyStr.trim())
                    .trim();
        }

        @Override
        public HashAlgorithm getType() {
            return HashAlgorithm.SHA2_256;
        }
    }


    // --------------------------------------------------------------------------------


    private static class ShaTwo512ApiKeyHasher implements ApiKeyHasher {

        @Override
        public String hash(final String value) {
            return DigestUtils.sha512Hex(value);
        }

        @Override
        public HashAlgorithm getType() {
            return HashAlgorithm.SHA2_512;
        }
    }


    // --------------------------------------------------------------------------------


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


    // --------------------------------------------------------------------------------


    private static class Argon2ApiKeyHasher implements ApiKeyHasher {

        // WARNING!!!
        // Do not change any of these otherwise it will break hash verification of existing
        // keys. If you want to tune it, make a new ApiKeyHasher impl with a new getType()
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
            final Argon2BytesGenerator generate = new Argon2BytesGenerator();
            generate.init(argon2Parameters);
            final byte[] result = new byte[HASH_LENGTH];
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
