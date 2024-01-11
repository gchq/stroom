package stroom.security.impl.apikey;

import stroom.cache.api.CacheManager;
import stroom.cache.api.StroomCache;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.impl.AuthenticationConfig;
import stroom.security.impl.BasicUserIdentity;
import stroom.security.impl.HashedApiKeyParts;
import stroom.security.shared.ApiKey;
import stroom.security.shared.ApiKeyResultPage;
import stroom.security.shared.CreateApiKeyRequest;
import stroom.security.shared.CreateApiKeyResponse;
import stroom.security.shared.FindApiKeyCriteria;
import stroom.security.shared.PermissionNames;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.PermissionException;
import stroom.util.shared.UserName;

import org.apache.commons.codec.digest.DigestUtils;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton // Has a cache
public class ApiKeyService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ApiKeyService.class);

    private static final int API_KEY_SALT_LENGTH = 48;
    private static final String CACHE_NAME = "API Key cache";

    private final ApiKeyDao apiKeyDao;
    private final SecurityContext securityContext;
    private final ApiKeyGenerator apiKeyGenerator;
    private final StroomCache<String, Optional<UserIdentity>> apiKeyToAuthenticatedUserCache;

    @Inject
    public ApiKeyService(final ApiKeyDao apiKeyDao,
                         final SecurityContext securityContext,
                         final ApiKeyGenerator apiKeyGenerator,
                         final CacheManager cacheManager,
                         final Provider<AuthenticationConfig> authenticationConfigProvider) {
        this.apiKeyDao = apiKeyDao;
        this.securityContext = securityContext;
        this.apiKeyGenerator = apiKeyGenerator;

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
            final String apiKeyPrefix = ApiKeyGenerator.extractPrefixPart(apiKeyStr);
            // Each key has its own salt, and we need the salt to hash the incoming key
            // to compare the hash against the ones in the DB. Therefore, use the key prefix
            // to find potential key records (likely will hit only one) then test the salted hash for each.
            final List<ApiKey> apiKeys = apiKeyDao.fetchValidApiKeysByPrefix(apiKeyPrefix);

            LOGGER.debug(() -> LogUtil.message("Found {} API keys matching prefix '{}'",
                    apiKeys.size(), apiKeyPrefix));

            final List<ApiKey> validApiKeys = apiKeys.stream()
                    .filter(apiKey -> {
                        final String saltedApiKeyHash = computeApiKeyHash(apiKeyStr, apiKey.getApiKeySalt());
                        return apiKey.getApiKeyHash().equals(saltedApiKeyHash);
                    })
                    .toList();

            if (validApiKeys.isEmpty()) {
                LOGGER.debug("No valid keys found matching prefix {}", apiKeyPrefix);
                return Optional.empty();
            } else if (validApiKeys.size() > 1) {
                final Set<Integer> ids = validApiKeys.stream()
                        .map(ApiKey::getId)
                        .collect(Collectors.toSet());
                throw new RuntimeException("Found multiple api keys that match on hash. IDs: " + ids);
            } else {
                final ApiKey apiKey = validApiKeys.get(0);
                final Optional<UserIdentity> optUserIdentity = Optional.of(apiKey.getOwner())
                        .map(BasicUserIdentity::new);
                LOGGER.debug("optUserIdentity: {}", optUserIdentity);
                return optUserIdentity;
            }
        }
    }

    public Optional<ApiKey> fetch(final int id) {
        return securityContext.secureResult(PermissionNames.MANAGE_API_KEYS, () -> {
            final Optional<ApiKey> optApiKey = apiKeyDao.fetch(id);

            optApiKey.ifPresent(apiKey ->
                    checkAdditionalPerms(apiKey.getOwner()));
            return optApiKey;
        });
    }

    public CreateApiKeyResponse create(final CreateApiKeyRequest createApiKeyRequest) {
        Objects.requireNonNull(createApiKeyRequest);

        return securityContext.secureResult(PermissionNames.MANAGE_API_KEYS, () -> {
            checkAdditionalPerms(createApiKeyRequest.getOwner());
            // It is possible that we generate a key with a hash-clash, so keep trying.
            int attempts = 0;
            do {
                try {
                    return createNewApiKey(createApiKeyRequest);
                } catch (DuplicateHashException e) {
                    LOGGER.debug("Duplicate hash on attempt {}, going round again", attempts);
                }
                attempts++;
            } while (attempts <= 5);

            throw new RuntimeException(LogUtil.message("Unable to create api key after {} attempts", attempts));
        });
    }

    private CreateApiKeyResponse createNewApiKey(final CreateApiKeyRequest createApiKeyRequest)
            throws DuplicateHashException {
        LOGGER.debug(() -> LogUtil.message("Attempting to create new API key for {}",
                createApiKeyRequest.getOwner()));
        final String salt = apiKeyGenerator.createRandomBase58String(API_KEY_SALT_LENGTH);
        final String apiKeyStr = apiKeyGenerator.generateRandomApiKey();
        final String saltedApiKeyHash = computeApiKeyHash(apiKeyStr, salt);
        final HashedApiKeyParts hashedApiKeyParts = new HashedApiKeyParts(
                saltedApiKeyHash, salt, ApiKeyGenerator.extractPrefixPart(apiKeyStr));

        final ApiKey hashedApiKey = apiKeyDao.create(
                createApiKeyRequest,
                hashedApiKeyParts);

        LOGGER.debug(() -> LogUtil.message("Created new API key for {}", createApiKeyRequest.getOwner()));
        return new CreateApiKeyResponse(apiKeyStr, hashedApiKey);
    }

    public ApiKey update(final ApiKey apiKey) {
        return securityContext.secureResult(PermissionNames.MANAGE_API_KEYS, () -> {
            checkAdditionalPerms(apiKey.getOwner());
            final ApiKey apiKeyBefore = apiKeyDao.fetch(apiKey.getId())
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
            final Optional<ApiKey> optApiKey = fetch(id);
            if (optApiKey.isPresent()) {
                final ApiKey apiKey = optApiKey.get();
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

    private void invalidateApiKeyCacheEntry(final ApiKey apiKey) {
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

    String computeApiKeyHash(final String apiKey, final String salt) {
        Objects.requireNonNull(apiKey);
        Objects.requireNonNull(salt);
        final String input = salt.trim() + apiKey.trim();
        final String sha256 = DigestUtils.sha3_256Hex(input)
                .trim();
        return sha256;
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


    public static class DuplicateHashException extends Exception {

        public DuplicateHashException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }
}
