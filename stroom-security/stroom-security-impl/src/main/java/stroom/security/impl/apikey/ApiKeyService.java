package stroom.security.impl.apikey;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.HttpHeaders;
import org.apache.commons.codec.digest.DigestUtils;
import stroom.cache.api.CacheManager;
import stroom.cache.api.StroomCache;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.common.impl.JwtUtil;
import stroom.security.impl.AuthenticationConfig;
import stroom.security.impl.BasicUserIdentity;
import stroom.security.impl.HashedApiKeyParts;
import stroom.security.impl.UserCache;
import stroom.security.shared.*;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.PermissionException;
import stroom.util.shared.UserRef;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

@Singleton // Has a cache
public class ApiKeyService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ApiKeyService.class);

    private static final String CACHE_NAME = "API Key cache";
    private static final int MAX_CREATION_ATTEMPTS = 100;

    private final ApiKeyDao apiKeyDao;
    private final SecurityContext securityContext;
    private final ApiKeyGenerator apiKeyGenerator;
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

    public ApiKeyResultPage find(final FindApiKeyCriteria criteria) {
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
            final String hash = computeApiKeyHash(apiKeyStr);
            final Optional<HashedApiKey> optApiKey = apiKeyDao.fetchValidApiKeyByHash(hash);

            LOGGER.debug("Found API key {} matching hash '{}'", optApiKey, hash);

            if (optApiKey.isEmpty()) {
                LOGGER.debug("No valid API keys found matching hash {}", hash);
                return Optional.empty();
            } else {
                final HashedApiKey apiKey = optApiKey.get();
                final Optional<User> optionalUser = Optional.ofNullable(apiKey.getOwner()).flatMap(userCache::getByRef);
                final Optional<UserIdentity> optUserIdentity = optionalUser
                        .map(User::asRef)
                        .map(BasicUserIdentity::new);
                LOGGER.debug("optUserIdentity: {}", optUserIdentity);
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
                } catch (DuplicateHashException e) {
                    LOGGER.debug("Duplicate hash on attempt {}, going round again", attempts);
                } catch (DuplicatePrefixException e) {
                    LOGGER.debug("Duplicate prefix on attempt {}, going round again", attempts);
                }
            } while (attempts < MAX_CREATION_ATTEMPTS);

            throw new RuntimeException(LogUtil.message("Unable to create API key with unique prefix and hash " +
                    "after {} attempts", attempts));
        });
    }

    private CreateHashedApiKeyResponse createNewApiKey(final CreateHashedApiKeyRequest createHashedApiKeyRequest)
            throws DuplicateHashException, DuplicatePrefixException {
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
        Objects.requireNonNull(apiKeyStr);

        return DigestUtils
                .sha3_256Hex(apiKeyStr.trim())
                .trim();
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


    // --------------------------------------------------------------------------------


    public static class DuplicateHashException extends Exception {

        public DuplicateHashException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }


    // --------------------------------------------------------------------------------


    public static class DuplicatePrefixException extends Exception {

        public DuplicatePrefixException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }
}
