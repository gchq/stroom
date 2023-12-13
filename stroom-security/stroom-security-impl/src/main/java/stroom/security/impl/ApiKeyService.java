package stroom.security.impl;

import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
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
import stroom.util.string.Base58;
import stroom.util.string.StringUtil;

import org.apache.commons.codec.digest.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton // Due to shared SecureRandom
public class ApiKeyService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ApiKeyService.class);

    // Stands for stroom-api-key
    static final String API_KEY_TYPE = "sak";
    static final String API_KEY_SEPARATOR = "_";
    private static final Pattern API_KEY_SEPARATOR_PATTERN = Pattern.compile(API_KEY_SEPARATOR, Pattern.LITERAL);
    public static final int API_KEY_RANDOM_CODE_LENGTH = 96;
    public static final int API_KEY_SALT_LENGTH = 48;
    public static final int TRUNCATED_HASH_LENGTH = 7;
    public static final int API_KEY_TOTAL_LENGTH =
            API_KEY_TYPE.length()
                    + (API_KEY_SEPARATOR.length() * 2)
                    + TRUNCATED_HASH_LENGTH
                    + API_KEY_RANDOM_CODE_LENGTH;

    private static final String BASE_58_ALPHABET = new String(Base58.ALPHABET);
    private static final String BASE_58_CHAR_CLASS = "[" + BASE_58_ALPHABET + "]";

    // A regex pattern that will match a full api key
    private static final Pattern API_KEY_PATTERN = Pattern.compile(
            "^"
                    + Pattern.quote(API_KEY_TYPE + API_KEY_SEPARATOR)
                    + BASE_58_CHAR_CLASS + "{" + TRUNCATED_HASH_LENGTH + "}"
                    + Pattern.quote(API_KEY_SEPARATOR)
                    + BASE_58_CHAR_CLASS + "{" + API_KEY_RANDOM_CODE_LENGTH + "}"
                    + "$");
    private static final Predicate<String> API_KEY_MATCH_PREDICATE = API_KEY_PATTERN.asMatchPredicate();

    private final ApiKeyDao apiKeyDao;
    private final SecureRandom secureRandom;
    private final SecurityContext securityContext;

    @Inject
    public ApiKeyService(final ApiKeyDao apiKeyDao,
                         final SecurityContext securityContext) {
        this.apiKeyDao = apiKeyDao;
        this.securityContext = securityContext;
        this.secureRandom = new SecureRandom();
        LOGGER.debug("API_KEY_PATTERN: '{}'", API_KEY_PATTERN);
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
        // This has to be unsecured as we are trying to authenticate
        if (!isApiKey(apiKeyStr)) {
            LOGGER.debug("apiKey '{}' is not an API key", apiKeyStr);
            return Optional.empty();
        } else {
            final String apiKeyPrefix = extractPrefixPart(apiKeyStr);
            // Each key has its own salt, and we need the salt to hash the incoming key
            // to compare the hash against the ones in the DB. Therefore, use the key prefix
            // to find potential key records (likely will hit only one) then test the hash for each.
            // TODO may want to consider having a cache of apiKeyStr => Optional<UserIdentity> with a
            //  TTL of 30s or so. This would save all the hashing and db lookups
            final List<ApiKey> validApiKeys = apiKeyDao.fetchValidApiKeysByPrefix(apiKeyPrefix)
                    .stream()
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
                throw new RuntimeException("Found multiple api keys that match on hash " + ids);
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
            String apiKeyStr;
            HashedApiKeyParts hashedApiKeyParts;
            // It is possible that we generate a key with a hash-clash, so keep trying.
            int attempts = 0;
            do {
                try {
                    final String salt = StringUtil.createRandomCode(
                            secureRandom, API_KEY_SALT_LENGTH, StringUtil.ALLOWED_CHARS_BASE_58_STYLE);
                    apiKeyStr = generateRandomApiKey();
                    final String saltedApiKeyHash = computeApiKeyHash(apiKeyStr, salt);

                    hashedApiKeyParts = new HashedApiKeyParts(
                            saltedApiKeyHash, salt, extractPrefixPart(apiKeyStr));

                    final ApiKey hashedApiKey = apiKeyDao.create(
                            createApiKeyRequest,
                            hashedApiKeyParts);

                    return new CreateApiKeyResponse(apiKeyStr, hashedApiKey);
                } catch (DuplicateHashException e) {
                    LOGGER.debug("Duplicate hash on attempt {}, going round again", attempts);
                }
                attempts++;
            } while (attempts <= 5);

            throw new RuntimeException(LogUtil.message("Unable to create api key after {} attempts", attempts));
        });
    }

    public ApiKey update(final ApiKey apiKey) {
        return securityContext.secureResult(PermissionNames.MANAGE_API_KEYS, () -> {
            checkAdditionalPerms(apiKey.getOwner());
            return apiKeyDao.update(apiKey);
        });
    }

    public boolean delete(final int id) {
        return securityContext.secureResult(PermissionNames.MANAGE_API_KEYS, () -> {
            final Optional<ApiKey> optApiKey = fetch(id);
            if (optApiKey.isPresent()) {
                checkAdditionalPerms(optApiKey.get().getOwner());
                return apiKeyDao.delete(id);
            } else {
                LOGGER.debug("Nothing to delete");
                return false;
            }
        });
    }

    /**
     * Generate a random API key of the form
     * <pre>{@code sak_<random code>_<hash>}</pre>
     * where:
     * <p>
     * {@code <random code>} is a string of random characters using the Base58 character set.
     * </p>
     * <p>
     * {@code <hash>} is the SHA1 hash of {@code <random code>} encoded in Base58 and truncated to the first
     * seven characters.
     * </p>
     *
     * <p>The following cyberchef recipe takes a full API key as input and highlights the hash part if it is valid</p>
     * <pre>
     * Register('sak_.*_(.*)$',true,false,false)
     * Regular_expression('User defined','sak_(.*)_.*$',true,true,false,false,false,false,'List capture groups')
     * SHA1(80)
     * To_Base58('123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz')
     * Regular_expression('User defined','^.{7}',true,true,false,false,false,false,'List matches')
     * Regular_expression('User defined','^$R0$',true,true,false,false,false,false,'Highlight matches')
     * </pre>
     */
    String generateRandomApiKey() {
        // This is the meat of the API key. A random string of chars using the
        // base58 character set. THis is so we have a nice simple set of readable chars
        // without visually similar ones like '0OIl'
        final String randomCode = StringUtil.createRandomCode(
                secureRandom,
                API_KEY_RANDOM_CODE_LENGTH,
                StringUtil.ALLOWED_CHARS_BASE_58_STYLE);

        // Generate a short hash of the randomCode. This is not THE hash. It just acts as a
        // checksum for the random code part of the key so you can reasonably confidently
        // tell if a string is a stroom api key or not.
        final String randomCodeHash = computeTruncatedHash(randomCode);
        return String.join(API_KEY_SEPARATOR, API_KEY_TYPE, randomCodeHash, randomCode);
    }

    private static String computeTruncatedHash(final String randomStr) {
        // Now get a sha1 hash of our random string, encoded in base58 and truncated to 7 chars.
        // This part acts as a checksum of the random code part and provides a means to verify that
        // something that looks like a stroom API key is actually one, e.g. for spotting stroom API keys
        // left in the clear. We may never use this checksum, but you never know.
        // See https://github.blog/2021-04-05-behind-githubs-new-authentication-token-formats/
        // for the idea behind this.
        // This is the bitcoin style base58, not flickr or other flavours.
        final String sha1 = DigestUtils.sha1Hex(randomStr);
        return Base58.encode(sha1.getBytes(StandardCharsets.UTF_8))
                .substring(0, TRUNCATED_HASH_LENGTH);
    }

    /**
     * Asserts that the passed string matches the format of a stroom API key and that the hash part of
     * the key matches the hash computed from the random code part.
     * Note, this method is NOT to be used for authenticating
     * an API key, for that see {@link ApiKeyService#fetchVerifiedIdentity(String)}. It simply verifies
     * that a string is very likely to be a stroom API key (whether valid/invalid, present/absent, enabled/disabled).
     * It can be used to see if a string passed in the {@code Authorization: Bearer ...} header is a stroom
     * API key before looking in the database to check its validity.
     *
     * @return True if the hash part of the API key matches a computed hash of the random
     * code part of the API key and the string matches the pattern for an API key.
     */
    boolean isApiKey(final String apiKey) {
        LOGGER.debug("apiKey: '{}'", apiKey);
        if (NullSafe.isBlankString(apiKey)) {
            return false;
        } else {
            final String trimmedApiKey = apiKey.trim();
            if (trimmedApiKey.length() != API_KEY_TOTAL_LENGTH) {
                LOGGER.debug(() -> LogUtil.message("Invalid length: {}", trimmedApiKey.length()));
                return false;
            }
            if (!API_KEY_MATCH_PREDICATE.test(trimmedApiKey)) {
                LOGGER.debug("Doesn't match pattern");
                return false;
            }
            final String[] parts = API_KEY_SEPARATOR_PATTERN.split(apiKey.trim());
            if (parts.length != 3) {
                LOGGER.debug("Incorrect number of parts: '{}', parts:", (Object) parts);
                return false;
            }
            final String hashPart = parts[1];
            final String codePart = parts[2];
            final String computedHash = computeTruncatedHash(codePart);
            if (!Objects.equals(hashPart, computedHash)) {
                LOGGER.debug("Hashes don't match, hashPart: '{}', computedHash: '{}'", hashPart, computedHash);
                return false;
            }
            return true;
        }
    }

    /**
     * For a key like
     * <pre>{@code
     * sak_3kePJfQ_sjYvkpd9dWJinLYzYLingrpWLAtGKsbPXgZYphfQC4PqkweotXHTnnRabxRZacc6Rt9MqncxtGAykzXebGQX9X7nhTHZMys8
     * }</pre>
     * return
     * <pre>{@code
     * sak_3kePJfQ_
     * }</pre>
     * This is to allow a user to identify their key. This prefix part may not be unique as it is only 7 chars of the
     * hash, however it probably is, so is good enough.
     */
    private String extractPrefixPart(final String apiKey) {
        Objects.requireNonNull(apiKey);
        final String[] parts = API_KEY_SEPARATOR_PATTERN.split(apiKey.trim());
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid key format, expecting three parts.");
        }
        return parts[0] + API_KEY_SEPARATOR + parts[1] + API_KEY_SEPARATOR;
    }

    String computeApiKeyHash(final String apiKey, final String salt) {
        Objects.requireNonNull(apiKey);
        Objects.requireNonNull(salt);
        final String input = salt.trim() + apiKey.trim();
        final String sha256 = DigestUtils.sha3_256Hex(input).trim();
        return sha256;
    }

    private void checkAdditionalPerms(final UserName owner) {

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


    // --------------------------------------------------------------------------------


    public static class DuplicateHashException extends Exception {

        public DuplicateHashException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }
}
