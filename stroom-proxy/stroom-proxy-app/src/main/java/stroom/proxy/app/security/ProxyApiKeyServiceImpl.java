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

package stroom.proxy.app.security;

import stroom.proxy.app.DownstreamHostConfig;
import stroom.proxy.app.ProxyConfig;
import stroom.security.api.CommonSecurityContext;
import stroom.security.api.HashFunction;
import stroom.security.api.HashFunctionFactory;
import stroom.security.api.UserIdentity;
import stroom.security.common.impl.ApiKeyGenerator;
import stroom.security.shared.AppPermission;
import stroom.security.shared.AppPermissionSet;
import stroom.security.shared.HashAlgorithm;
import stroom.security.shared.VerifyApiKeyRequest;
import stroom.util.io.PathCreator;
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
public class ProxyApiKeyServiceImpl implements ProxyApiKeyService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProxyApiKeyServiceImpl.class);

    private static final OpenOption[] READ_OPEN_OPTIONS = new OpenOption[]{StandardOpenOption.READ};
    private static final OpenOption[] WRITE_OPEN_OPTIONS = new OpenOption[]{
            StandardOpenOption.WRITE,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING};
    private static final AppPermissionSet KEY_CHECK_PERM_SET = AppPermissionSet.oneOf(
            AppPermission.STROOM_PROXY,
            AppPermission.VERIFY_API_KEY);

    static final String FILE_NAME = "verified-api-keys.json";

    private final ApiKeyGenerator apiKeyGenerator;
    private final Provider<DownstreamHostConfig> downstreamHostConfigProvider;
    private final Provider<ProxyConfig> proxyConfigProvider;
    private final Provider<CommonSecurityContext> proxySecurityContextProvider;
    private final Provider<ProxyApiKeyCheckClient> proxyApiKeyCheckClientProvider;
    // Use a map rather than a cache as we should not be dealing with many
    // and any that are not the right format will be ignored
    private final Map<VerifyApiKeyRequest, DatedValue<VerifiedApiKey>> verifiedKeysMap;
    private final HashFunctionFactory hashFunctionFactory;
    private final PathCreator pathCreator;
    private volatile VerifiedApiKeys verifiedApiKeysFromFile;
    private volatile Instant earliestNextFetchTime = Instant.EPOCH;

    @Inject
    public ProxyApiKeyServiceImpl(final Provider<DownstreamHostConfig> downstreamHostConfigProvider,
                                  final ApiKeyGenerator apiKeyGenerator,
                                  final Provider<ProxyConfig> proxyConfigProvider,
                                  final Provider<CommonSecurityContext> proxySecurityContextProvider,
                                  final Provider<ProxyApiKeyCheckClient> proxyApiKeyCheckClientProvider,
                                  final HashFunctionFactory hashFunctionFactory,
                                  final PathCreator pathCreator) {
        this.apiKeyGenerator = apiKeyGenerator;
        this.proxyConfigProvider = proxyConfigProvider;
        this.proxySecurityContextProvider = proxySecurityContextProvider;
        this.proxyApiKeyCheckClientProvider = proxyApiKeyCheckClientProvider;
        this.hashFunctionFactory = hashFunctionFactory;
        this.pathCreator = pathCreator;
        this.downstreamHostConfigProvider = downstreamHostConfigProvider;

        verifiedKeysMap = new ConcurrentHashMap<>();
        verifiedApiKeysFromFile = readFromDisk()
                .orElse(null);
    }

    @Override
    public Optional<UserDesc> verifyApiKey(final VerifyApiKeyRequest request) {
        LOGGER.debug("verifyApiKey() - request: {}", request);
        try {
            return proxySecurityContextProvider.get().secureResult(KEY_CHECK_PERM_SET, () -> {
                final String apiKey = request.getApiKey();
                if (apiKeyGenerator.isApiKey(apiKey)) {
                    DatedValue<VerifiedApiKey> datedVerifiedApiKey = verifiedKeysMap.get(request);
                    if (isTooOld(datedVerifiedApiKey)) {
                        synchronized (this) {
                            datedVerifiedApiKey = verifiedKeysMap.get(request);
                            if (isTooOld(datedVerifiedApiKey)) {
                                // Remove just in case
                                verifiedKeysMap.remove(request);
                                // Try to hit the downstream to verify it
                                final VerifiedApiKey verifiedApiKey = doApiKeyVerification(request)
                                        .orElse(null);
                                final DatedValue<VerifiedApiKey> newDatedVerifiedApiKey = verifiedApiKey != null
                                        ? DatedValue.create(verifiedApiKey.getLastVerified(), verifiedApiKey)
                                        : DatedValue.create(null);
                                LOGGER.debug("verifyApiKey() - Putting new verified API key {}",
                                        newDatedVerifiedApiKey);
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
                    LOGGER.debug("verifyApiKey() - Doesn't look like an API key '{}'", apiKey);
                    return Optional.empty();
                }
            });
        } catch (final Exception e) {
            LOGGER.debug(() -> LogUtil.message("verifyApiKey() - Failed security check, request: {}, {}",
                    request, LogUtil.exceptionMessage(e)));
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<UserIdentity> verifyIdentity(final String apiKey) {
        final Optional<UserIdentity> optIdentity = verifyApiKey(new VerifyApiKeyRequest(apiKey))
                .map(userDesc -> new ApiKeyUserIdentity(apiKey, userDesc));

        LOGGER.debug(() -> LogUtil.message("verifyIdentity() - apiKey: {}, optIdentity: {}",
                NullSafe.subString(apiKey, 0, 15), optIdentity));
        return optIdentity;
    }

    private boolean isTooOld(final DatedValue<VerifiedApiKey> datedVerifiedApiKey) {
        final boolean isTooOld = datedVerifiedApiKey == null
                                 || datedVerifiedApiKey.hasNullValue()
                                 || datedVerifiedApiKey.isOlderThan(
                downstreamHostConfigProvider.get().getMaxCachedKeyAge().getDuration());
        LOGGER.debug(() -> LogUtil.message("isTooOld() - isTooOld: {}, age: {}, instant: {}",
                isTooOld,
                NullSafe.get(datedVerifiedApiKey, DatedValue::getAge, Duration::toString),
                NullSafe.get(datedVerifiedApiKey, DatedValue::getInstant, Instant::toString)));
        return isTooOld;
    }

    private synchronized Optional<VerifiedApiKey> doApiKeyVerification(final VerifyApiKeyRequest request) {
        final String apiKey = request.getApiKey();
        // We can locally rule out any API keys that are invalid based on the format.
        final boolean looksLikeApiKey = apiKeyGenerator.isApiKey(apiKey);
        if (looksLikeApiKey) {
            VerifiedApiKey verifiedApiKey;
            // We don't want to spam the downstream if it is down
            if (Instant.now().isAfter(earliestNextFetchTime)) {
                // Looks like an API key so call the verifyApiKey endpoint on the remote to
                // see if it is an actual enabled key belonging to someone.
                final ProxyApiKeyCheckClient proxyApiKeyCheckClient = proxyApiKeyCheckClientProvider.get();
                try {
                    final Optional<UserDesc> optUserDesc = proxyApiKeyCheckClient.fetchApiKeyValidity(request);
                    verifiedApiKey = optUserDesc.map(userDesc ->
                                    createVerifiedApiKey(request, userDesc))
                            .orElse(null);
                } catch (final Exception e) {
                    final Duration noFetchIntervalAfterFailure = downstreamHostConfigProvider.get()
                            .getNoFetchIntervalAfterFailure()
                            .getDuration();
                    earliestNextFetchTime = Instant.now().plus(noFetchIntervalAfterFailure);
                    LOGGER.errorAndDebug(e, "Error verifying API key using URL {}: {}",
                            proxyApiKeyCheckClient.getFullUrl(), LogUtil.exceptionMessage(e), e);
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
            final boolean isMatch = ApiKeyGenerator.prefixesMatch(request.getApiKey(), aVerifiedApiKey.getPrefix())
                                    && Objects.equals(
                    request.getRequiredAppPermissions(),
                    aVerifiedApiKey.getRequiredAppPermissions());

            // Ignore matching ones as we are either replacing it below, or removing it.
            if (!isMatch) {
                LOGGER.debug("updateFile() - adding {}", aVerifiedApiKey);
                newVerifiedApiKeySet.add(aVerifiedApiKey);
            }
        }
        // Add our new one, or if it is null effectively remove it
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
            final Duration maxPersistedKeyAge = downstreamHostConfigProvider.get().getMaxPersistedKeyAge()
                    .getDuration();
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
                            !verifiedApiKey.isOlderThan(maxPersistedKeyAge)) // Can't trust ancient ones
                    .findAny();

            LOGGER.debug("verifyLocally() - optVerifiedApiKey: {}", optVerifiedApiKey);
            return optVerifiedApiKey;
        } else {
            return Optional.empty();
        }
    }

    private VerifiedApiKey createVerifiedApiKey(final VerifyApiKeyRequest request,
                                                final UserDesc userDesc) {
        final String apiKey = request.getApiKey();
        final String prefix = ApiKeyGenerator.extractPrefixPart(apiKey);
        final HashAlgorithm hashAlgorithm = downstreamHostConfigProvider.get().getPersistedKeysHashAlgorithm();
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
        } catch (final IOException e) {
            throw new UncheckedIOException(LogUtil.message("Error ensuring dir {} exists: {}",
                    parent, LogUtil.exceptionMessage(e)), e);
        }
        return jsonFile;
    }

    private synchronized Optional<VerifiedApiKeys> readFromDisk() {
        final Path jsonFile = getJsonFilePath();
        if (Files.exists(jsonFile)) {
            try (final InputStream inputStream = Files.newInputStream(jsonFile, READ_OPEN_OPTIONS)) {
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
            } catch (final Exception e) {
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
            try (final OutputStream outputStream = Files.newOutputStream(jsonFile, WRITE_OPEN_OPTIONS)) {

                LOGGER.debug("writeToDisk() - Writing verifiedApiKeys to file {}\n{}",
                        jsonFile, verifiedApiKeys);
                mapper.writeValue(outputStream, verifiedApiKeys);
                LOGGER.info("Written verifiedApiKeys with snapshot time {} to file '{}'",
                        Instant.ofEpochMilli(verifiedApiKeys.getSnapshotTimeEpochMs()), jsonFile);
                verifiedApiKeysFromFile = verifiedApiKeys;
            } catch (final IOException e) {
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
