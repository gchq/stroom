package stroom.proxy.app.security;

import stroom.proxy.app.AbstractDownstreamClient;
import stroom.proxy.app.DownstreamHostConfig;
import stroom.proxy.app.ProxyConfig;
import stroom.security.api.HashFunction;
import stroom.security.api.HashFunctionFactory;
import stroom.security.api.UserIdentityFactory;
import stroom.security.common.impl.ApiKeyGenerator;
import stroom.security.shared.AppPermission;
import stroom.security.shared.AppPermissionSet;
import stroom.security.shared.HashAlgorithm;
import stroom.security.shared.VerifyApiKeyRequest;
import stroom.util.io.PathCreator;
import stroom.util.jersey.JerseyClientFactory;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserDesc;
import stroom.util.time.DatedValue;
import stroom.util.time.TimeUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.Response.StatusType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Singleton
public class ProxyApiKeyServiceImpl extends AbstractDownstreamClient implements ProxyApiKeyService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProxyApiKeyServiceImpl.class);

    private static final HashAlgorithm HASH_ALGORITHM = HashAlgorithm.SHA2_512;
    private static final OpenOption[] READ_OPEN_OPTIONS = new OpenOption[]{StandardOpenOption.READ};
    private static final OpenOption[] WRITE_OPEN_OPTIONS = new OpenOption[]{
            StandardOpenOption.WRITE,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING};
    private static final AppPermissionSet KEY_CHECK_PERM_SET = AppPermissionSet.oneOf(
            AppPermission.STROOM_PROXY,
            AppPermission.VERIFY_API_KEY);

    static final String FILE_NAME = "verified-api-keys.json";

    private static final Duration MAX_CACHED_VALUE_AGE = Duration.ofMinutes(10);
    private static final Duration MAX_VERIFIED_KEY_AGE_IN_FILE = Duration.ofDays(30);
    private static final Duration NO_FETCH_INTERVAL_AFTER_FAILURE = Duration.ofSeconds(30);

    private final ApiKeyGenerator apiKeyGenerator;
    private final Provider<DownstreamHostConfig> downstreamHostConfigProvider;
    private final Provider<ProxyConfig> proxyConfigProvider;
    private final Provider<ProxySecurityContext> proxySecurityContextProvider;
    // Use a map rather than a cache as we should not be dealing with many
    // and any that are not the right format will be ignored
    private final Map<VerifyApiKeyRequest, DatedValue<VerifiedApiKey>> verifiedKeysMap;
    private final HashFunctionFactory hashFunctionFactory;
    private final PathCreator pathCreator;
    private volatile VerifiedApiKeys verifiedApiKeysFromFile;
    private volatile Instant earliestNextFetchTime = Instant.EPOCH;

    @Inject
    public ProxyApiKeyServiceImpl(final JerseyClientFactory jerseyClientFactory,
                                  final UserIdentityFactory userIdentityFactory,
                                  final Provider<DownstreamHostConfig> downstreamHostConfigProvider,
                                  final ApiKeyGenerator apiKeyGenerator,
                                  final Provider<ProxyConfig> proxyConfigProvider,
//                                  final CacheManager cacheManager,
                                  final Provider<ProxySecurityContext> proxySecurityContextProvider,
                                  final HashFunctionFactory hashFunctionFactory,
                                  final PathCreator pathCreator) {
        super(jerseyClientFactory, userIdentityFactory, downstreamHostConfigProvider);
        this.apiKeyGenerator = apiKeyGenerator;
        this.downstreamHostConfigProvider = downstreamHostConfigProvider;
        this.proxyConfigProvider = proxyConfigProvider;
        this.proxySecurityContextProvider = proxySecurityContextProvider;
        this.hashFunctionFactory = hashFunctionFactory;
        this.pathCreator = pathCreator;
        // Hopefully this should not contain many items as in most cases
        // we won't have many upstream proxies hitting us
//        this.verifiedKeysCache = cacheManager.createLoadingCache(
//                CACHE_NAME,
//                () -> downstreamHostConfigProvider.get().getVerifiedApiKeysCache(),
//                this::doVerifyApiKey);

        verifiedKeysMap = new ConcurrentHashMap<>();
        verifiedApiKeysFromFile = readFromDisk()
                .orElse(null);
    }

    @Override
    protected Optional<String> getConfiguredUrl() {
        return NullSafe.nonBlank(downstreamHostConfigProvider.get().getApiKeyVerificationUrl());
    }

    @Override
    protected String getDefaultPath() {
        return DownstreamHostConfig.DEFAULT_API_KEY_VERIFICATION_URL_PATH;
    }

    @Override
    public Optional<UserDesc> verifyApiKey(final VerifyApiKeyRequest request) {
        return proxySecurityContextProvider.get().secureResult(KEY_CHECK_PERM_SET, () -> {
            final String apiKey = request.getApiKey();
            if (apiKeyGenerator.isApiKey(apiKey)) {
                DatedValue<VerifiedApiKey> datedVerifiedApiKey = verifiedKeysMap.get(request);
                if (isTooOld(datedVerifiedApiKey)) {
                    synchronized (this) {
                        datedVerifiedApiKey = verifiedKeysMap.get(request);
                        if (isTooOld(datedVerifiedApiKey)) {
                            // Try to hit the downstream to verify it
                            final VerifiedApiKey verifiedApiKey = doVerifyApiKey(request)
                                    .orElse(null);
                            final DatedValue<VerifiedApiKey> newDatedVerifiedApiKey = verifiedApiKey != null
                                    ? DatedValue.create(verifiedApiKey.getLastVerified(), verifiedApiKey)
                                    : DatedValue.create(null);
                            LOGGER.debug("verifyApiKey() - Putting new verified API key {}", newDatedVerifiedApiKey);
                            // Cache the outcome
                            verifiedKeysMap.put(request, newDatedVerifiedApiKey);
                            updateFile(verifiedApiKey, request);
                            return Optional.ofNullable(verifiedApiKey)
                                    .map(VerifiedApiKey::getUserDesc);
                        } else {
                            LOGGER.debug("verifyApiKey() - Found cached value {}", datedVerifiedApiKey);
                            return Optional.ofNullable(datedVerifiedApiKey.getValue())
                                    .map(VerifiedApiKey::getUserDesc);
                        }
                    }
                } else {
                    LOGGER.debug("verifyApiKey() - Found cached value {}", datedVerifiedApiKey);
                    return Optional.ofNullable(datedVerifiedApiKey.getValue())
                            .map(VerifiedApiKey::getUserDesc);
                }
            } else {
                LOGGER.debug("verifyApiKey() - Doesn't look like an API key {}", apiKey);
                return Optional.empty();
            }
        });
    }

    private boolean isTooOld(final DatedValue<VerifiedApiKey> datedVerifiedApiKey) {
        final boolean isTooOld = datedVerifiedApiKey == null
                                 || datedVerifiedApiKey.isOlderThan(MAX_CACHED_VALUE_AGE);
        LOGGER.debug(() -> LogUtil.message("isTooOld() - isTooOld: {}, age: {}, instant: {}",
                isTooOld,
                NullSafe.get(datedVerifiedApiKey, DatedValue::getAge, Duration::toString),
                NullSafe.get(datedVerifiedApiKey, DatedValue::getInstant, Instant::toString)));
        return isTooOld;
    }

    private synchronized Optional<VerifiedApiKey> doVerifyApiKey(final VerifyApiKeyRequest request) {
        final String apiKey = request.getApiKey();
        // We can locally rule out any API keys that are invalid based on the format.
        final boolean looksLikeApiKey = apiKeyGenerator.isApiKey(apiKey);
        if (looksLikeApiKey) {
            VerifiedApiKey verifiedApiKey;
            // We don't want to spam the downstream if it is down
            if (Instant.now().isAfter(earliestNextFetchTime)) {
                // Looks like an API key so call the verifyApiKey endpoint on the remote to
                // see if it is an actual enabled key belonging to someone.
                try {
                    final Optional<UserDesc> optUserDesc = fetchApiKeyValidity(request);
                    verifiedApiKey = optUserDesc.map(userDesc ->
                                    createVerifiedApiKey(request, HASH_ALGORITHM, userDesc))
                            .orElse(null);
                } catch (Exception e) {
                    earliestNextFetchTime = Instant.now().plus(NO_FETCH_INTERVAL_AFTER_FAILURE);
                    LOGGER.errorAndDebug(e, "Error verifying API key using URL {}: {}",
                            getFullUrl(), LogUtil.exceptionMessage(e), e);
                    // See if we can verify based on what we had in the file.
                    verifiedApiKey = verifyLocally(request)
                            .orElse(null);
                }
            } else {
                LOGGER.debug(() -> LogUtil.message(
                        "doVerifyApiKey() - Not hitting remote, earliestFetchTime: {}, min time to next fetch: {}",
                        earliestNextFetchTime, TimeUtils.durationUntil(earliestNextFetchTime)));
                verifiedApiKey = verifyLocally(request)
                        .orElse(null);
            }
            return Optional.ofNullable(verifiedApiKey);
        } else {
            // Doesn't look like an api key so no point hitting the downstream
            LOGGER.debug("doVerifyApiKey() - Api key '{}' does not look like an API key", apiKey);
            return Optional.empty();
        }
    }

    private synchronized void updateFile(final VerifiedApiKey verifiedApiKey, final VerifyApiKeyRequest request) {
        final Set<VerifiedApiKey> verifiedApiKeySet = NullSafe.set(
                NullSafe.get(verifiedApiKeysFromFile, VerifiedApiKeys::getVerifiedApiKeys));
        final Set<VerifiedApiKey> newVerifiedApiKeySet = new HashSet<>();
        for (final VerifiedApiKey aVerifiedApiKey : verifiedApiKeySet) {
            // Just compare the prefix and perms as the hash algo may differ
            if (ApiKeyGenerator.prefixesMatch(request.getApiKey(), aVerifiedApiKey.getPrefix())
                && Objects.equals(request.getRequiredAppPermissions(), aVerifiedApiKey.getRequiredAppPermissions())) {
                // This one matches our request so leave it out as we are either
                // replacing it below, or removing it.
            } else {
                LOGGER.debug("updateFile() - adding {}", aVerifiedApiKey);
                newVerifiedApiKeySet.add(aVerifiedApiKey);
            }
        }
        // Add our new one
        if (verifiedApiKey != null) {
            LOGGER.debug("updateFile() - adding {}", verifiedApiKey);
            newVerifiedApiKeySet.add(verifiedApiKey);
        }

        final VerifiedApiKeys verifiedApiKeys = new VerifiedApiKeys(
                newVerifiedApiKeySet,
                Instant.now().toEpochMilli());
        writeToDisk(verifiedApiKeys);
    }

    private Optional<VerifiedApiKey> verifyLocally(final VerifyApiKeyRequest request) {
        if (verifiedApiKeysFromFile != null) {
            final Optional<VerifiedApiKey> optVerifiedApiKey = verifiedApiKeysFromFile.getVerifiedApiKeys().stream()
                    .filter(verifiedApiKey -> {
                        // Have to compare them using the same hashing algo used in the file
                        final HashAlgorithm hashAlgorithm = verifiedApiKey.getHashAlgorithm();
                        final HashFunction hashFunction = hashFunctionFactory.getHashFunction(hashAlgorithm);
                        final String hashedApiKey = hashFunction.hash(request.getApiKey());

                        final boolean areEqual = Objects.equals(hashedApiKey, verifiedApiKey.getHashedApiKey())
                                                 && ApiKeyGenerator.prefixesMatch(request.getApiKey(),
                                verifiedApiKey.getPrefix());
                        LOGGER.debug("verifyLocally() - comparing (areEqual: {}):\n  {}\n  {}",
                                areEqual, verifiedApiKey, request);
                        return areEqual;
                    })
                    .filter(verifiedApiKey ->
                            !verifiedApiKey.isOlderThan(MAX_VERIFIED_KEY_AGE_IN_FILE)) // Can't trust ancient ones
                    .findAny();

            LOGGER.debug("verifyLocally() - optVerifiedApiKey: {}", optVerifiedApiKey);
            return optVerifiedApiKey;
        } else {
            return Optional.empty();
        }
    }

    private VerifiedApiKey createVerifiedApiKey(final VerifyApiKeyRequest request,
                                                final HashAlgorithm hashAlgorithm,
                                                final UserDesc userDesc) {
        final String apiKey = request.getApiKey();
        final String prefix = ApiKeyGenerator.extractPrefixPart(apiKey);
        final String hash = hashFunctionFactory.getHashFunction(hashAlgorithm)
                .hash(apiKey);
        return new VerifiedApiKey(
                hashAlgorithm,
                prefix,
                hash,
                request.getRequiredAppPermissions(),
                Instant.now().toEpochMilli(),
                userDesc);
    }

    private Optional<UserDesc> fetchApiKeyValidity(final VerifyApiKeyRequest request) {
        final String url = getFullUrl();
        Optional<UserDesc> optUserDesc = Optional.empty();
        if (NullSafe.isNonBlankString(url)) {
            try (Response response = getResponse(builder -> builder.post(Entity.json(request)))) {
                final StatusType statusInfo = response.getStatusInfo();
                if (statusInfo.getStatusCode() == Status.OK.getStatusCode()) {
                    if (response.hasEntity()) {
                        optUserDesc = Optional.ofNullable(response.readEntity(UserDesc.class));
                        LOGGER.debug("fetchApiKeyValidity() - optUserDesc: {}, request: {}", optUserDesc, request);
                    } else {
                        LOGGER.debug("fetchApiKeyValidity() - No response entity from {}", url);
                    }
                } else {
                    LOGGER.error("Error fetching API Key validity using url '{}', " +
                                 "got response {} - {}",
                            url, statusInfo.getStatusCode(), statusInfo.getReasonPhrase());
                }
            } catch (NotFoundException e) {
                LOGGER.debug("fetchApiKeyValidity() - Not found exception");
            }
        } else {
            LOGGER.warn("No url configured for API key verification.");
        }
        return optUserDesc;
    }

    private Path getJsonFilePath() {
        final String contentDir = proxyConfigProvider.get().getContentDir();
        if (NullSafe.isBlankString(contentDir)) {
            throw new IllegalStateException("contentDir is blank");
        }
        final Path jsonFile = pathCreator.toAppPath(contentDir)
                .resolve(FILE_NAME);
        final Path parent = jsonFile.getParent();
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new UncheckedIOException(LogUtil.message("Error ensuring dir {} exists: {}",
                    parent, LogUtil.exceptionMessage(e)), e);
        }
        return jsonFile;
    }

    private synchronized Optional<VerifiedApiKeys> readFromDisk() {
        final Path jsonFile = getJsonFilePath();
        if (Files.exists(jsonFile)) {
            try (InputStream inputStream = Files.newInputStream(jsonFile, READ_OPEN_OPTIONS)) {
                LOGGER.debug("readFromDisk() - Reading receipt policy rules from file '{}'", jsonFile);
                final VerifiedApiKeys verifiedApiKeys = JsonUtil.getMapper().readValue(
                        inputStream, VerifiedApiKeys.class);
                if (verifiedApiKeys != null) {
                    LOGGER.info("Read last known receipt policy rules from file '{}' with snapshot time {}",
                            jsonFile, Instant.ofEpochMilli(verifiedApiKeys.getSnapshotTimeEpochMs()));
                    LOGGER.debug(() -> LogUtil.message("readFromDisk() - Read verifiedApiKeys from file {}\n{}",
                            jsonFile, NullSafe.get(verifiedApiKeys, VerifiedApiKeys::getVerifiedApiKeys)
                                    .stream()
                                    .map(VerifiedApiKey::toString)
                                    .collect(Collectors.joining("\n"))));
                    return Optional.of(verifiedApiKeys);
                } else {
                    LOGGER.error("Null verifiedApiKeys from file '{}'", jsonFile);
                    return Optional.empty();
                }
            } catch (Exception e) {
                final String exMsg = LogUtil.exceptionMessage(e);
                LOGGER.errorAndDebug(e, "Error reading persisted receipt policy rules from file '{}': {}",
                        jsonFile, exMsg);
                // Swallow and carry on
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    private synchronized void writeToDisk(final VerifiedApiKeys verifiedApiKeys) {
        if (verifiedApiKeys != null) {
            final Path jsonFile = getJsonFilePath();
            final ObjectMapper mapper = JsonUtil.getMapper();
            try (OutputStream outputStream = Files.newOutputStream(jsonFile, WRITE_OPEN_OPTIONS)) {

                LOGGER.debug("writeToDisk() - Writing verifiedApiKeys to file {}\n{}",
                        jsonFile, verifiedApiKeys);
                mapper.writeValue(outputStream, verifiedApiKeys);
                LOGGER.info("Written verifiedApiKeys with snapshot time {} to file '{}'",
                        Instant.ofEpochMilli(verifiedApiKeys.getSnapshotTimeEpochMs()), jsonFile);
                verifiedApiKeysFromFile = verifiedApiKeys;
            } catch (IOException e) {
                LOGGER.error("Error writing to file " + jsonFile
                             + ": " + LogUtil.exceptionMessage(e), e);
                // Swallow and carry on
            }
        }
    }


    // --------------------------------------------------------------------------------


    @JsonPropertyOrder(alphabetic = true)
    @JsonInclude(Include.NON_NULL)
    private static class VerifiedApiKeys {

        @JsonProperty
        private final Set<VerifiedApiKey> verifiedApiKeys;
        @JsonProperty
        private final long snapshotTimeEpochMs;

        @JsonCreator
        VerifiedApiKeys(@JsonProperty("verifiedApiKeys") final Set<VerifiedApiKey> verifiedApiKeys,
                        @JsonProperty("snapshotTimeEpochMs") final long snapshotTimeEpochMs) {
            this.verifiedApiKeys = NullSafe.unmodifialbeSet(verifiedApiKeys);
            this.snapshotTimeEpochMs = snapshotTimeEpochMs;
        }

        public Set<VerifiedApiKey> getVerifiedApiKeys() {
            return verifiedApiKeys;
        }

        public long getSnapshotTimeEpochMs() {
            return snapshotTimeEpochMs;
        }

        @JsonIgnore
        public Instant getSnapshotTime() {
            return java.time.Instant.ofEpochMilli(snapshotTimeEpochMs);
        }

        public boolean contains(final VerifiedApiKey verifiedApiKey) {
            return verifiedApiKeys.contains(verifiedApiKey);
        }

        @Override
        public String toString() {
            return "VerifiedApiKeys{" +
                   "snapshotTimeEpochMs=" + java.time.Instant.ofEpochMilli(snapshotTimeEpochMs) +
                   ", verifiedApiKeys='" + verifiedApiKeys + '\'' +
                   '}';
        }
    }


    // --------------------------------------------------------------------------------


    @JsonPropertyOrder(alphabetic = true)
    @JsonInclude(Include.NON_NULL)
    private static class VerifiedApiKey {

        @JsonProperty
        private final HashAlgorithm hashAlgorithm;
        @JsonProperty
        private final String prefix;
        @JsonProperty
        private final String hashedApiKey;
        @JsonProperty
        private final AppPermissionSet requiredAppPermissions;
        @JsonProperty
        private final long lastVerifiedEpochMs;
        @JsonProperty
        private final UserDesc userDesc;

        @JsonCreator
        VerifiedApiKey(@JsonProperty("hashAlgorithm") final HashAlgorithm hashAlgorithm,
                       @JsonProperty("prefix") final String prefix,
                       @JsonProperty("hashedApiKey") final String hashedApiKey,
                       @JsonProperty("requiredAppPermissions") final AppPermissionSet requiredAppPermissions,
                       @JsonProperty("lastVerifiedEpochMs") final long lastVerifiedEpochMs,
                       @JsonProperty("userDesc") final UserDesc userDesc) {
            this.hashAlgorithm = Objects.requireNonNull(hashAlgorithm);
            this.prefix = Objects.requireNonNull(prefix);
            this.hashedApiKey = Objects.requireNonNull(hashedApiKey);
            this.requiredAppPermissions = NullSafe.requireNonNullElseGet(
                    requiredAppPermissions, AppPermissionSet::empty);
            this.lastVerifiedEpochMs = lastVerifiedEpochMs;
            this.userDesc = userDesc;
        }

        public HashAlgorithm getHashAlgorithm() {
            return hashAlgorithm;
        }

        public String getPrefix() {
            return prefix;
        }

        public String getHashedApiKey() {
            return hashedApiKey;
        }

        public AppPermissionSet getRequiredAppPermissions() {
            return requiredAppPermissions;
        }

        public long getLastVerifiedEpochMs() {
            return lastVerifiedEpochMs;
        }

        public UserDesc getUserDesc() {
            return userDesc;
        }

        @JsonIgnore
        public Instant getLastVerified() {
            return java.time.Instant.ofEpochMilli(lastVerifiedEpochMs);
        }

        @JsonIgnore
        public boolean isOlderThan(final Duration age) {
            return getLastVerified().isBefore(java.time.Instant.now().minus(age));
        }

        // Don't include lastVerifiedEpochMs
        @Override
        public boolean equals(final Object object) {
            if (this == object) {
                return true;
            }
            if (object == null || getClass() != object.getClass()) {
                return false;
            }
            final VerifiedApiKey that = (VerifiedApiKey) object;
            return hashAlgorithm == that.hashAlgorithm
                   && Objects.equals(prefix, that.prefix)
                   && Objects.equals(hashedApiKey, that.hashedApiKey)
                   && Objects.equals(requiredAppPermissions, that.requiredAppPermissions)
                   && Objects.equals(userDesc, that.userDesc);
        }

        // Don't include lastVerifiedEpochMs
        @Override
        public int hashCode() {
            return Objects.hash(hashAlgorithm, prefix, hashedApiKey, requiredAppPermissions, userDesc);
        }

        @Override
        public String toString() {
            return "VerifiedApiKey{" +
                   "hashAlgorithm=" + hashAlgorithm +
                   ", prefix='" + prefix + '\'' +
                   ", hashedApiKey='" + hashedApiKey + '\'' +
                   ", requiredAppPermissions=" + requiredAppPermissions +
                   ", lastVerifiedEpochMs=" + java.time.Instant.ofEpochMilli(lastVerifiedEpochMs) +
                   ", userDesc=" + userDesc +
                   '}';
        }
    }
}
