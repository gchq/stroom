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

package stroom.proxy.app;

import stroom.dictionary.api.WordListProvider;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapper;
import stroom.receive.common.ReceiveDataConfig;
import stroom.receive.common.ReceiveDataRuleSetService;
import stroom.receive.common.WordListProviderFactory;
import stroom.receive.rules.shared.HashedReceiveDataRules;
import stroom.receive.rules.shared.ReceiptCheckMode;
import stroom.receive.rules.shared.ReceiveDataRules;
import stroom.security.api.CommonSecurityContext;
import stroom.security.api.HashFunction;
import stroom.security.api.HashFunctionFactory;
import stroom.security.shared.AppPermission;
import stroom.security.shared.AppPermissionSet;
import stroom.security.shared.HashAlgorithm;
import stroom.util.concurrent.CachedValue;
import stroom.util.io.PathCreator;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.time.TimeUtils;

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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Calls the resource on the downstream (in data flow terms) to get
 */
@Singleton
public class RemoteReceiveDataRuleSetServiceImpl implements ReceiveDataRuleSetService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RemoteReceiveDataRuleSetServiceImpl.class);
    private static final OpenOption[] WRITE_OPEN_OPTIONS = new OpenOption[]{
            StandardOpenOption.WRITE,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING};
    private static final OpenOption[] READ_OPEN_OPTIONS = new OpenOption[]{StandardOpenOption.READ};
    private static final AppPermissionSet REQUIRED_PERMISSION_SET = AppPermissionSet.oneOf(
            AppPermission.FETCH_HASHED_RECEIPT_POLICY_RULES,
            AppPermission.STROOM_PROXY);
    // Pkg private for testing
    static final String FILE_NAME = "receive-data-rules.json";

    private final ReceiveDataRuleSetClient receiveDataRuleSetClient;
    private final Provider<ProxyConfig> proxyConfigProvider;
    private final Provider<CommonSecurityContext> commonSecurityContextProvider;
    private final Provider<ReceiveDataConfig> receiveDataConfigProvider;
    private final PathCreator pathCreator;
    private final HashFunctionFactory hashFunctionFactory;
    private final WordListProviderFactory wordListProviderFactory;

    private final CachedValue<RuleState, Void> cachedRuleState;
    private final AtomicBoolean isInitialised = new AtomicBoolean(false);
    private final Duration noFetchIntervalAfterFailure = Duration.ofSeconds(30);

    private Instant earliestNextFetchTime = Instant.EPOCH;

    @Inject
    public RemoteReceiveDataRuleSetServiceImpl(
            final ReceiveDataRuleSetClient receiveDataRuleSetClient,
            final Provider<CommonSecurityContext> commonSecurityContextProvider,
            final Provider<ProxyReceiptPolicyConfig> proxyReceiptPolicyConfigProvider,
            final Provider<ProxyConfig> proxyConfigProvider,
            final Provider<ReceiveDataConfig> receiveDataConfigProvider,
            final PathCreator pathCreator,
            final HashFunctionFactory hashFunctionFactory,
            final WordListProviderFactory wordListProviderFactory) {

        this.receiveDataRuleSetClient = receiveDataRuleSetClient;
        this.commonSecurityContextProvider = commonSecurityContextProvider;
        this.proxyConfigProvider = proxyConfigProvider;
        this.receiveDataConfigProvider = receiveDataConfigProvider;
        this.pathCreator = pathCreator;
        this.hashFunctionFactory = hashFunctionFactory;
        this.cachedRuleState = CachedValue.builder()
                .withMaxCheckInterval(proxyReceiptPolicyConfigProvider.get()
                        .getSyncFrequency()
                        .getDuration())
                .withoutStateSupplier()
                .withValueSupplier(this::createCachedRuleState)
                .build();
        this.wordListProviderFactory = wordListProviderFactory;

        // No point trying to fetch rules if we are not using them
        if (receiveDataConfigProvider.get().getReceiptCheckMode() == ReceiptCheckMode.RECEIPT_POLICY) {
            // Eagerly init the rules
            cachedRuleState.getValue();
        }
    }

    @Override
    public ReceiveDataRules getReceiveDataRules() {
        throw new UnsupportedOperationException("getReceiveDataRules() not supported on proxy");
    }

    @Override
    public ReceiveDataRules updateReceiveDataRules(final ReceiveDataRules receiveDataRules) {
        throw new UnsupportedOperationException("updateReceiveDataRules() not supported on proxy");
    }

    /**
     * This will be called by an upstream proxy (in datafeed flow terms) where this proxy
     * is the middle man in a chain of proxies with a stroom at the very end.
     */
    @Override
    public HashedReceiveDataRules getHashedReceiveDataRules() {
        return commonSecurityContextProvider.get().secureResult(REQUIRED_PERMISSION_SET, () ->
                cachedRuleState.getValueAsync()
                        .hashedReceiveDataRules());
    }

    @Override
    public BundledRules getBundledRules() {
        return commonSecurityContextProvider.get().secureResult(REQUIRED_PERMISSION_SET, () ->
                NullSafe.get(
                        cachedRuleState.getValueAsync(),
                        RuleState::bundledRules));
    }

    private synchronized RuleState createCachedRuleState(final RuleState currRuleState) {
        // This may run async so needs to run as the proc user
        return commonSecurityContextProvider.get().asProcessingUserResult(() -> {
            return LOGGER.logDurationIfDebugEnabled(() -> {
                final HashedReceiveDataRules hashedReceiveDataRules = fetchRulesFromRemote(
                        NullSafe.get(currRuleState, RuleState::hashedReceiveDataRules));

                if (hashedReceiveDataRules != null) {
                    final AttributeMapper attributeMapper = buildAttributeMapper(hashedReceiveDataRules);
                    final WordListProvider wordListProvider = wordListProviderFactory.create(
                            hashedReceiveDataRules.getUuidToFlattenedDictMap());
                    final BundledRules bundledRules = new BundledRules(
                            hashedReceiveDataRules.getReceiveDataRules(),
                            wordListProvider,
                            attributeMapper);
                    return new RuleState(hashedReceiveDataRules, bundledRules);
                } else {
                    // A null return will mean a receive-all filter
                    return currRuleState;
                }
            }, "createRuleBundle");
        });
    }

    private AttributeMapper buildAttributeMapper(final HashedReceiveDataRules hashedReceiveDataRules) {
        // If no fields are hashed then there will be no hashAlgorithm
        final HashAlgorithm hashAlgorithm = hashedReceiveDataRules.getHashAlgorithm();
        final AttributeMapper attributeMapper;
        if (hashAlgorithm != null) {
            final HashFunction hashFunction = hashFunctionFactory.getHashFunction(hashAlgorithm);
            attributeMapper = new AttributeMapHasher(
                    hashFunction,
                    hashedReceiveDataRules.getFieldNameToSaltMap());
        } else {
            attributeMapper = AttributeMapper.IDENTITY;
        }
        return attributeMapper;
    }

    private synchronized HashedReceiveDataRules fetchRulesFromRemote(
            final HashedReceiveDataRules currHashedReceiveDataRules) {
        // Even if our value is older than our frequency, don't hold up the processing.
        // Later calls will get the updated value.
        Optional<HashedReceiveDataRules> optHashedReceiveDataRules = Optional.empty();

        // Don't fetch from the remote if we have just failed to avoid spamming the remote
        // if it is down.
        if (Instant.now().isAfter(earliestNextFetchTime)) {
            optHashedReceiveDataRules = receiveDataRuleSetClient.getHashedReceiveDataRules();
            if (optHashedReceiveDataRules.isEmpty()) {
                earliestNextFetchTime = Instant.now().plus(noFetchIntervalAfterFailure);
                LOGGER.warn("Failed to get rules from remote '{}', will not try again for: {}. " +
                            "Is the remote down? Will try to use previous rules or read them from disk.",
                        receiveDataRuleSetClient.getFullUrl(),
                        TimeUtils.durationUntil(earliestNextFetchTime));
            }
        } else {
            LOGGER.debug(() -> LogUtil.message(
                    "fetchRulesFromRemote() - Not hitting remote, earliestFetchTime: {}, min time to next fetch: {}",
                    earliestNextFetchTime, TimeUtils.durationUntil(earliestNextFetchTime)));
        }

        if (optHashedReceiveDataRules.isPresent()) {
            final HashedReceiveDataRules hashedReceiveDataRules = optHashedReceiveDataRules.get();
            LOGGER.debug("fetchRulesFromRemote() - got hashedReceiveDataRules {} from remote", hashedReceiveDataRules);
            // Update our value on disk in so if proxy reboots and upstream is
            // not available, we have the latest.
            writeToDisk(hashedReceiveDataRules);
            isInitialised.set(true);
        } else {
            // Couldn't get a value from the remote
            //noinspection StatementWithEmptyBody
            if (isInitialised.get()) {
                // We should have the previous value in memory so the caller can fallback to that.
            } else {
                // We don't have a prev value in mem, so try to get one from disk
                LOGGER.info("Unable to obtain receipt rules from remote, so attempting to read them from disk");
                optHashedReceiveDataRules = readFromDisk();
                optHashedReceiveDataRules.ifPresent(ignored ->
                        isInitialised.set(true));
            }
        }

        // If the remote is down on boot, and we don't have the rules on disk then we
        // have to return empty. The caller will use the configured fallback action for all data.
        // we can hit the remote successfully.
        LOGGER.debug("fetchRulesFromRemote() - returning {}", optHashedReceiveDataRules);
        return optHashedReceiveDataRules.orElse(null);
    }

//    private HashedReceiveDataRules getRemoteHashedReceiveDataRules(
//            final HashedReceiveDataRules currHashedReceiveDataRules) {
//
//        Optional<HashedReceiveDataRules> optHashedReceiveDataRules = Optional.empty();
//
//        final ContentSyncConfig contentSyncConfig = contentSyncConfigProvider.get();
//        final String url = contentSyncConfig.getReceiveDataRulesUrl();
//        if (NullSafe.isNonBlankString(url)) {
//            try {
//                final WebTarget webTarget = jerseyClientFactory.createWebTarget(JerseyClientName.CONTENT_SYNC, url)
//                        .path(GET_FEED_STATUS_PATH);
//                try (Response response = getResponse(contentSyncConfig, webTarget)) {
//                    final StatusType statusInfo = response.getStatusInfo();
//                    if (statusInfo.getStatusCode() != Status.OK.getStatusCode()) {
//                        LOGGER.error("Error fetching receive data rules using url '{}', got response {} - {}",
//                                url, statusInfo.getStatusCode(), statusInfo.getReasonPhrase());
//                    } else {
//                        optHashedReceiveDataRules = Optional.ofNullable(
//                                response.readEntity(HashedReceiveDataRules.class));
//                        // Update our value on disk in so if proxy reboots and upstream is
//                        // not available, we have the latest.
//                        optHashedReceiveDataRules.ifPresent(this::writeToDisk);
//                    }
//                }
//            } catch (Throwable e) {
//                LOGGER.error("Error fetching receive data rules using url '{}': {}",
//                        url, LogUtil.exceptionMessage(e), e);
//            }
//        }
//
//        // Couldn't get a value from the remote, so try to get one from disk if this is our first time
//        if (optHashedReceiveDataRules.isEmpty()
//            && isInitialised.compareAndSet(false, true)) {
//            optHashedReceiveDataRules = readFromDisk();
//        }
//
//        // Fall back on the last held value, which may be null if there is no file
//        return optHashedReceiveDataRules.orElse(currHashedReceiveDataRules);
//    }

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

    private Optional<HashedReceiveDataRules> readFromDisk() {
        final Path jsonFile = getJsonFilePath();
        if (Files.exists(jsonFile)) {
            try (final InputStream inputStream = Files.newInputStream(jsonFile, READ_OPEN_OPTIONS)) {
                LOGGER.debug("readFromDisk() - Reading receipt policy rules from file '{}'", jsonFile);
                final HashedReceiveDataRules hashedReceiveDataRules = JsonUtil.getMapper().readValue(
                        inputStream, HashedReceiveDataRules.class);
                if (hashedReceiveDataRules != null) {
                    LOGGER.debug("Read last known receipt policy rules from file '{}' with snapshot time {}",
                            jsonFile, Instant.ofEpochMilli(hashedReceiveDataRules.getSnapshotTimeEpochMs()));
                    LOGGER.debug("readFromDisk() - Read hashedReceiveDataRules from file {}\n{}",
                            jsonFile, hashedReceiveDataRules);
                    return Optional.of(hashedReceiveDataRules);
                } else {
                    LOGGER.error("Null hashedReceiveDataRules from file '{}'", jsonFile);
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

    private void writeToDisk(final HashedReceiveDataRules hashedReceiveDataRules) {
        if (hashedReceiveDataRules != null) {
            // The salts will change every time we get a new HashedReceiveDataRules from
            // the remote, so the object on disk will always be different. As long as
            // they both have the same snapshot time (i.e. the doc last update time) then
            // we can leave the disk version as is to save the write.
            final boolean isWriteRequired = readFromDisk()
                    .map(rulesOnDisk -> !Objects.equals(
                            hashedReceiveDataRules.getSnapshotTimeEpochMs(),
                            rulesOnDisk.getSnapshotTimeEpochMs()))
                    .orElse(true);

            if (isWriteRequired) {
                final Path jsonFile = getJsonFilePath();
                final ObjectMapper mapper = JsonUtil.getMapper();
                try (final OutputStream outputStream = Files.newOutputStream(jsonFile, WRITE_OPEN_OPTIONS)) {

                    LOGGER.debug("writeToDisk() - Writing hashedReceiveDataRules to file {}\n{}",
                            jsonFile, hashedReceiveDataRules);
                    mapper.writeValue(outputStream, hashedReceiveDataRules);
                    LOGGER.info("Written receipt policy rules with snapshot time {} to file '{}'",
                            Instant.ofEpochMilli(hashedReceiveDataRules.getSnapshotTimeEpochMs()), jsonFile);
                } catch (final IOException e) {
                    LOGGER.error("Error writing to file " + jsonFile
                                 + ": " + LogUtil.exceptionMessage(e), e);
                    // Swallow and carry on
                }
            } else {
                LOGGER.debug("writeToDisk() - No write required");
            }
        }
    }


    // --------------------------------------------------------------------------------


    private record RuleState(HashedReceiveDataRules hashedReceiveDataRules,
                             BundledRules bundledRules) {

    }


    // --------------------------------------------------------------------------------


    private static final class AttributeMapHasher implements AttributeMapper {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AttributeMapHasher.class);

        private final HashFunction hashFunction;
        private final Map<String, String> fieldNameToSaltMap;

        private AttributeMapHasher(final HashFunction hashFunction,
                                   final Map<String, String> fieldNameToSaltMap) {
            this.hashFunction = hashFunction;
            this.fieldNameToSaltMap = NullSafe.map(fieldNameToSaltMap);
        }

        /**
         * Return an {@link AttributeMap} instance containing all entries from attributeMap.
         * Those values that need to be hashed will have been hashed.
         * If any values are hashed, a new {@link AttributeMap} will be returned.
         *
         * @param attributeMap Will not be modified, but may be returned unchanged.
         */
        public AttributeMap mapAttributes(final AttributeMap attributeMap) {
            if (requiresMapping(attributeMap)) {
                // Make a new attrMap with all existing values
                final AttributeMap newAttrMap = new AttributeMap(attributeMap);
                LOGGER.logDurationIfDebugEnabled(
                        () -> {
                            // Now obfuscate the ones that need obfuscating
                            // We end up with something a bit like
                            //  Feed => MY_FEED
                            //  Feed___!hashed! => c33025dd3916685a0b999d1c13fcdc3f
                            // We have to have a suffixed version because the expr tree may contain
                            // a mix of hashed and non-hashed values for the same field.
                            fieldNameToSaltMap.forEach((fieldName, salt) -> {
                                final String suffixedFieldName = HashedReceiveDataRules.markFieldAsHashed(fieldName);
                                final String unHashedVal = newAttrMap.get(fieldName);
                                final String hashedVal = NullSafe.get(
                                        unHashedVal,
                                        val -> hashFunction.hash(val, salt));
                                newAttrMap.put(suffixedFieldName, hashedVal);
                            });
                        },
                        "Hash attributeMap values");
                return newAttrMap;
            } else {
                // Nothing to hash so return the supplied attributeMap unchanged
                return attributeMap;
            }
        }

        private boolean requiresMapping(final AttributeMap attributeMap) {
            if (!NullSafe.isEmptyMap(attributeMap) && !fieldNameToSaltMap.isEmpty()) {
                for (final String fieldName : fieldNameToSaltMap.keySet()) {
                    if (attributeMap.containsKey(fieldName)) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            final AttributeMapHasher that = (AttributeMapHasher) obj;
            return Objects.equals(this.hashFunction, that.hashFunction) &&
                   Objects.equals(this.fieldNameToSaltMap, that.fieldNameToSaltMap);
        }

        @Override
        public int hashCode() {
            return Objects.hash(hashFunction, fieldNameToSaltMap);
        }

        @Override
        public String toString() {
            return "AttributeMapHasher[" +
                   "hashFunction=" + hashFunction + ", " +
                   "fieldNameToSaltMap=" + fieldNameToSaltMap + ']';
        }

    }
}
