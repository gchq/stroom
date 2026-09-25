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
import stroom.docref.DocRef;
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
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.entityevent.EntityEventData;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserDesc;
import stroom.util.shared.UserRef;
import stroom.util.string.Base58;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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
@EntityEventHandler(type = ApiKeyService.ENTITY_TYPE, action = {
        EntityAction.UPDATE,
        EntityAction.DELETE})
public class ApiKeyService implements Clearable, EntityEvent.Handler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ApiKeyService.class);
    private static final AppPermissionSet REQUIRED_PERMISSION_SET = AppPermissionSet.oneOf(
            AppPermission.VERIFY_API_KEY,
            AppPermission.STROOM_PROXY);
    private static final long EXPIRE_SOON_THRESHOLD_MS = Duration.ofDays(30).toMillis();
    public static final String ENTITY_TYPE = "API_KEY";
    private static final DocRef EVENT_DOCREF = new DocRef(ENTITY_TYPE, ENTITY_TYPE, ENTITY_TYPE);

    private static final String CACHE_NAME = "API Key Cache";
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
    private final StroomCache<String, Optional<ApiKeyAndIdentity>> apiKeyToAuthenticatedUserCache;
    private final Provider<AuthenticationConfig> authenticationConfigProvider;
    private final UserCache userCache;
    private final EntityEventBus entityEventBus;

    @Inject
    public ApiKeyService(final ApiKeyDao apiKeyDao,
                         final SecurityContext securityContext,
                         final ApiKeyGenerator apiKeyGenerator,
                         final CacheManager cacheManager,
                         final Provider<AuthenticationConfig> authenticationConfigProvider,
                         final UserCache userCache,
                         final EntityEventBus entityEventBus) {
        this.apiKeyDao = apiKeyDao;
        this.securityContext = securityContext;
        this.apiKeyGenerator = apiKeyGenerator;
        this.authenticationConfigProvider = authenticationConfigProvider;
        this.userCache = userCache;
        this.entityEventBus = entityEventBus;

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
        // Trimmed so the cache is keyed on the canonical form of the key, and so the hash
        // verification in doFetchVerifiedIdentity() sees the same string that isApiKey() and
        // extractPrefixPart() do, both of which trim. Whitespace is never part of an API key.
        final String token = NullSafe.get(
                request.getHeader(HttpHeaders.AUTHORIZATION),
                header -> header.replace(JwtUtil.BEARER_PREFIX, ""),
                String::trim);

        return fetchVerifiedIdentity(token);
    }

    Optional<UserIdentity> fetchVerifiedIdentity(final String apiKeyStr) {
        // We need to do a basic check to see if it looks like an API key else we will fill the cache with
        // JWT tokens mapped to empty Optionals.
        // We should only be throwing if apiKeyStr looks like an API key but is invalid in some other way
        // so other auth mechanisms can try to authenticate it
        final String trimmedApiKey = NullSafe.trim(apiKeyStr);
        if (NullSafe.isNonBlankString(trimmedApiKey)
            && trimmedApiKey.startsWith(ApiKeyGenerator.API_KEY_STATIC_PREFIX)) {

            final Optional<ApiKeyAndIdentity> optApiKeyAndIdentity = apiKeyToAuthenticatedUserCache.get(trimmedApiKey);

            if (optApiKeyAndIdentity.isEmpty() && apiKeyGenerator.isApiKey(trimmedApiKey)) {
                // Stops the next filter from trying to authenticate it
                throw new AuthenticationException("API key failed authentication");
            } else if (optApiKeyAndIdentity.isPresent()) {
                // API key may have expired since being added to the cache
                // For the enabled state we have to rely on the entity events evicting from the cache
                final HashedApiKey apiKey = optApiKeyAndIdentity.get().hashedApiKey;
                if (apiKey.isExpired()) {
                    throw new AuthenticationException("API key has expired");
                }
            }
            return optApiKeyAndIdentity.map(ApiKeyAndIdentity::userIdentity);
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

//    /**
//     * Fetch the verified {@link UserIdentity} for the passed API key.
//     * If the hash of the API key matches one in the database and that key is enabled
//     * and not expired then the {@link UserIdentity} will be returned, else an empty {@link Optional}
//     * is returned.
//     */
//    Optional<UserIdentity> fetchVerifiedIdentity(final String apiKeyStr) {
//        if (NullSafe.isBlankString(apiKeyStr)) {
//            return Optional.empty();
//        } else {
//            // See the note on trimming in fetchVerifiedIdentity(HttpServletRequest)
//            return apiKeyToAuthenticatedUserCache.get(apiKeyStr.trim())
//                    .map(ApiKeyAndIdentity::userIdentity);
//        }
//    }

    private Optional<ApiKeyAndIdentity> doFetchVerifiedIdentity(final String apiKeyStr) {
        // This has to be unsecured as we are trying to authenticate
        if (!apiKeyGenerator.isApiKey(apiKeyStr)) {
            LOGGER.debug("apiKey is not an API key");
            return Optional.empty();
        } else {
            final String prefix = ApiKeyGenerator.extractPrefixPart(apiKeyStr);

            // Keys may be disabled or expired, so we need to check the enabled and expiry state
            final List<HashedApiKey> apiKeys = apiKeyDao.fetchApiKeysByPrefix(prefix);

            if (apiKeys.isEmpty()) {
                LOGGER.debug("No valid API keys found matching prefix '{}'", prefix);
                return Optional.empty();
            } else {
                Optional<ApiKeyAndIdentity> optApiKeyAndIdentity = Optional.empty();
                // In most cases, there will be only one apiKey fetched for a given prefix
                // as the chance of a prefix clash is ~1:1,000,000. If there are multiple,
                // then we just test each one using its algorithm to see if the stored hash
                // matches the hash of the passed api key.
                for (final HashedApiKey apiKey : apiKeys) {
                    final boolean isHashMatch = verifyApiKeyHash(
                            apiKeyStr,
                            apiKey.getApiKeyHash(),
                            apiKey.getHashAlgorithm());
                    LOGGER.debug("doFetchVerifiedIdentity() - prefix: '{}', apiKey: {}, isHashMatch: {}",
                            prefix, apiKey, isHashMatch);
                    if (isHashMatch) {
                        // As we are caching this result, the warnings will not spam the logs.
                        if (!apiKey.getEnabled()) {
                            LOGGER.warn("An attempt was made to use a disabled API key, prefix: '{}'",
                                    apiKey.getApiKeyPrefix());
                        } else if (apiKey.isExpired()) {
                            LOGGER.warn("An attempt was made to use an API key that has expired, " +
                                        "prefix: '{}', expiry: {}. A new API Key should be created and distributed.",
                                    apiKey.getApiKeyPrefix(),
                                    NullSafe.get(apiKey.getExpireTimeMs(), Instant::ofEpochMilli));
                        } else {
                            if (apiKey.willExpireSoon(EXPIRE_SOON_THRESHOLD_MS)) {
                                LOGGER.warn("An attempt was made to use an API key that will expire soon, " +
                                            "prefix: '{}', expiry: {}, remaining: {}. " +
                                            "A new API Key should be created and distributed.",
                                        apiKey.getApiKeyPrefix(),
                                        NullSafe.get(apiKey.getExpireTimeMs(), Instant::ofEpochMilli),
                                        Duration.between(
                                                Instant.now(),
                                                Instant.ofEpochMilli(apiKey.getExpireTimeMs())));
                            }

                            final Optional<User> optionalUser = Optional.ofNullable(apiKey.getOwner())
                                    .flatMap(userCache::getByRef);
                            optApiKeyAndIdentity = optionalUser
                                    .map(user -> {
                                        verifyEnabledOrThrow(user);
                                        return user.asRef();
                                    })
                                    .map(BasicUserIdentity::new)
                                    .map(identity ->
                                            new ApiKeyAndIdentity(apiKey, identity));
                            LOGGER.debug("optUserIdentity: {}", optApiKeyAndIdentity);
                            break;
                        }
                    }
                }
                LOGGER.debug("Found {} API key(s) matching prefix: '{}', matched identity: {}",
                        apiKeys.size(), prefix, optApiKeyAndIdentity);
                return optApiKeyAndIdentity;
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
            final HashedApiKey updatedApiKey = apiKeyDao.update(apiKey);
            // Users can only really change the enabled state
            fireEvent(EntityAction.UPDATE, updatedApiKey);
            return updatedApiKey;
        });
    }

    public boolean delete(final int id) {
        return securityContext.secureResult(AppPermission.MANAGE_API_KEYS, () -> {
            final Optional<HashedApiKey> optApiKey = fetch(id);
            if (optApiKey.isPresent()) {
                final HashedApiKey apiKey = optApiKey.get();
                checkAdditionalPerms(apiKey.getOwner());
                final boolean didDelete = apiKeyDao.delete(id);
                fireEvent(EntityAction.DELETE, apiKey);
                return didDelete;
            } else {
                LOGGER.debug("Nothing to delete");
                return false;
            }
        });
    }

    private void fireEvent(final EntityAction entityAction, final HashedApiKey updatedApiKey) {
        // Enabled state has changed so invalidate the cache
        EntityEvent.fire(
                entityEventBus,
                EVENT_DOCREF,
                entityAction,
                new ApiKeyEntityEventData(updatedApiKey.getApiKeyPrefix()));
    }

    private void invalidateApiKeyCacheEntry(final HashedApiKey apiKey) {
        if (apiKey != null) {
            invalidateApiKeyCacheEntry(apiKey.getApiKeyPrefix());
        }
    }

    private void invalidateApiKeyCacheEntry(final String apiKeyPrefix) {
        if (apiKeyPrefix != null) {
            LOGGER.debug("invalidateApiKeyCacheEntry() - apiKeyPrefix: {}", apiKeyPrefix);
            apiKeyToAuthenticatedUserCache.invalidateEntries((apiKeyStr, ignored) ->
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

    @Override
    public void clear() {
        apiKeyToAuthenticatedUserCache.clear();
    }

    @Override
    public void onChange(final EntityEvent event) {
        final EntityAction action = NullSafe.get(event, EntityEvent::getAction);
        if (action == EntityAction.DELETE || action == EntityAction.UPDATE) {
            final ApiKeyEntityEventData apiKeyEntityEventData = event.getDataObject(ApiKeyEntityEventData.class);
            if (apiKeyEntityEventData != null) {
                invalidateApiKeyCacheEntry(apiKeyEntityEventData.getApiKeyPrefix());
            } else {
                // No info, so have to clear the whole cache
                apiKeyToAuthenticatedUserCache.clear();
            }
        }
    }


    // --------------------------------------------------------------------------------


    private record ApiKeyAndIdentity(HashedApiKey hashedApiKey, UserIdentity userIdentity) {

        private ApiKeyAndIdentity {
            Objects.requireNonNull(hashedApiKey);
            Objects.requireNonNull(userIdentity);
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
            this.argon2Parameters = new Builder(org.bouncycastle.crypto.params.Argon2Parameters.ARGON2_id)
                    .withVersion(org.bouncycastle.crypto.params.Argon2Parameters.ARGON2_VERSION_13)
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


    // --------------------------------------------------------------------------------


    private static final class ApiKeyEntityEventData implements EntityEventData {

        @JsonProperty
        private final String apiKeyPrefix;

        @JsonCreator
        private ApiKeyEntityEventData(@JsonProperty("apiKeyPrefix") final String apiKeyPrefix) {
            this.apiKeyPrefix = Objects.requireNonNull(apiKeyPrefix);
        }

        public String getApiKeyPrefix() {
            return apiKeyPrefix;
        }

        @Override
        public String toString() {
            return "ApiKeyEntityEventData{" +
                   "apiKeyPrefix='" + apiKeyPrefix + '\'' +
                   '}';
        }
    }
}
