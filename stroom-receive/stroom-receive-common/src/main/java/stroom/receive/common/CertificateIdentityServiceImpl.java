/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.receive.common;


import stroom.meta.api.AttributeMap;
import stroom.proxy.StroomStatusCode;
import stroom.receive.common.DataFeedIdentity.IdentityStatus;
import stroom.security.api.UserIdentity;
import stroom.util.PredicateUtil;
import stroom.util.PredicateUtil.CountingPredicate;
import stroom.util.cert.CertificateExtractor;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.string.CIKey;
import stroom.util.sysinfo.HasSystemInfo;
import stroom.util.sysinfo.SystemInfoResult;

import io.dropwizard.lifecycle.Managed;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@Singleton
public class CertificateIdentityServiceImpl
        implements CertificateIdentityService, AuthenticatorFilter, Managed, HasSystemInfo {

    private final CertificateExtractor certificateExtractor;
    private final Provider<ReceiveDataConfig> receiveDataConfigProvider;
    private final Map<CacheKey, Set<CachedIdentity>> identityMap;
    private final Timer timer;

    @Inject
    public CertificateIdentityServiceImpl(final CertificateExtractor certificateExtractor,
                                          final Provider<ReceiveDataConfig> receiveDataConfigProvider) {
        this.certificateExtractor = certificateExtractor;
        this.receiveDataConfigProvider = receiveDataConfigProvider;
        this.identityMap = new ConcurrentHashMap<>();
        this.timer = new Timer("CertificateIdentityEvictionTimer");
    }

    @Override
    public synchronized IdentityStatus addCertificateIdentity(final CertificateIdentity certificateIdentity,
                                                              final Path sourceFile) {
        Objects.requireNonNull(certificateIdentity);
        Objects.requireNonNull(sourceFile);
        final CachedIdentity cachedIdentity = new CachedIdentity(certificateIdentity, sourceFile);
        final CIKey keyOwnerMetaKey = getOwnerMetaKey(receiveDataConfigProvider.get());
        final boolean isValid = isValidCertificateIdentity(cachedIdentity, keyOwnerMetaKey);
        if (isValid) {
            final String certificateDn = certificateIdentity.getCertificateDn();
            final String owner = certificateIdentity.getStreamMetaValue(keyOwnerMetaKey);
            final CacheKey cacheKey = new CacheKey(CIKey.ofDynamicKey(owner), certificateDn);
            final boolean success = identityMap.computeIfAbsent(
                            cacheKey,
                            ignored -> ConcurrentHashMap.newKeySet())
                    .add(cachedIdentity);
            return success
                    ? IdentityStatus.ADDED
                    : IdentityStatus.DUPLICATE;
        } else {
            return IdentityStatus.INVALID;
        }
    }

    private boolean isValidCertificateIdentity(final CachedIdentity cachedIdentity,
                                               final CIKey ownerMetaKey) {

        LOGGER.debug("isValidCertificateIdentity() - cachedIdentity: {}", cachedIdentity);

        final CertificateIdentity certificateIdentity = cachedIdentity.certificateIdentity;
        if (certificateIdentity.isExpired()) {
            LOGGER.debug(() -> LogUtil.message(
                    "Ignoring expired certificate Key in sourceFile: {}, expiry: {}",
                    cachedIdentity.sourceFile, certificateIdentity.getExpiryDate()));
            return false;
        }
        final String value = certificateIdentity.getStreamMetaValue(ownerMetaKey);
        if (NullSafe.isBlankString(value)) {
            LOGGER.warn("Ignoring certificate identity found with no value for owner key '{}' in sourceFile: {}",
                    ownerMetaKey, cachedIdentity.sourceFile);
            return false;
        }
        if (NullSafe.isBlankString(certificateIdentity.getCertificateDn())) {
            LOGGER.warn("Ignoring Data Feed Key found with no certificateDn property in sourceFile: {}",
                    cachedIdentity.sourceFile);
            return false;
        }
        return true;
    }

    @Override
    public synchronized void removeKeysForFile(final Path sourceFile) {
        Objects.requireNonNull(sourceFile);

        LOGGER.info("Evicting certificate identities for sourceFile {}", sourceFile);
        final CountingPredicate<CachedIdentity> sourceFilePredicate = PredicateUtil.countingPredicate(
                cachedKey ->
                        Objects.equals(sourceFile, cachedKey.sourceFile()));

        identityMap.forEach((dn, identitySet) ->
                identitySet.removeIf(sourceFilePredicate));

        if (sourceFilePredicate.intValue() > 0) {
            removeEmptyEntries();
        }

        LOGGER.debug("Removed {} identityMap sub-entries", sourceFilePredicate);
        LOGGER.debug(() -> LogUtil.message("Total cached keys: {}", identityMap.values()
                .stream()
                .mapToInt(Set::size)
                .sum()));
    }

    private void removeEmptyEntries() {
        // Remove entries with empty sub-sets
        final Predicate<Entry<CacheKey, Set<CachedIdentity>>> isEmptyPredicate = PredicateUtil.countingPredicate(
                entry ->
                        NullSafe.isEmptyCollection(entry.getValue()));

        identityMap.entrySet().removeIf(isEmptyPredicate);
        LOGGER.debug("removeEmptyEntries() - Removed: {}", isEmptyPredicate);
    }

    public synchronized void evictExpired() {
        LOGGER.debug("Evicting expired certificate identities");
        final CountingPredicate<CachedIdentity> isExpiredPredicate = PredicateUtil.countingPredicate(CachedIdentity::isExpired);
        identityMap.forEach((dn, identitySet) ->
                identitySet.removeIf(isExpiredPredicate));
        if (isExpiredPredicate.intValue() > 0) {
            removeEmptyEntries();
        }
        LOGGER.debug("Removed {} expired identities", isExpiredPredicate);
    }

    @Override
    public Optional<UserIdentity> authenticate(final HttpServletRequest request,
                                               final AttributeMap attributeMap) {
        LOGGER.debug("authenticate() - request: {}, attributeMap: {}", request, attributeMap);
        try {
            final CIKey keyOwnerMetaKey = getOwnerMetaKey(receiveDataConfigProvider.get());
            final String keyOwnerFromHeaders = attributeMap.get(keyOwnerMetaKey.get());
            if (NullSafe.isNonBlankString(keyOwnerFromHeaders)) {
                final Optional<UserIdentity> optUserIdentity = certificateExtractor.getDN(request)
                        .map(dn -> {
                            final CacheKey cacheKey = new CacheKey(CIKey.ofDynamicKey(keyOwnerFromHeaders), dn);
                            return identityMap.get(cacheKey);
                        })
                        .filter(NullSafe::hasItems)
                        .flatMap(identities -> {
                            final long nowMs = System.currentTimeMillis();
                            return identities.stream()
                                    .filter(identity ->
                                            nowMs < identity.certificateIdentity.getExpiryDateEpochMs())
                                    .findFirst();
                        })
                        .map(cachedIdentity -> {
                            final Map<CIKey, String> streamMetaData = cachedIdentity.certificateIdentity()
                                    .getCIStreamMetaData();
                            // Don't need to check the keyOwner against keyOwnerFromHeaders as
                            // we built the map, so the key will match the value
                            final String keyOwner = streamMetaData.get(keyOwnerMetaKey);
                            attributeMap.putAll(cachedIdentity.certificateIdentity.getStreamMetaData());
                            return keyOwner;
                        })
                        .map(DataFeedUserIdentity::new);
                LOGGER.debug("authenticate() - Returning optUserIdentity: {}", optUserIdentity);
                return optUserIdentity;
            } else {
                LOGGER.debug("authenticate() - Blank keyOwnerFromHeaders");
                return Optional.empty();
            }
        } catch (final StroomStreamException e) {
            throw e;
        } catch (final Exception e) {
            throw new StroomStreamException(
                    StroomStatusCode.CLIENT_CERTIFICATE_DN_NOT_AUTHENTICATED, attributeMap, e.getMessage());
        }
    }

    @Override
    public SystemInfoResult getSystemInfo() {
        // sourcePath => accountId => Map
        final Map<String, Map<String, List<Map<String, String>>>> map = new HashMap<>();
        final String keyOwnerMetaKey = receiveDataConfigProvider.get().getDataFeedOwnerMetaKey();
        identityMap.values()
                .forEach(cachedIdentities -> {
                    for (final CachedIdentity cachedIdentity : cachedIdentities) {
                        final String path = cachedIdentity.sourceFile().toAbsolutePath().normalize().toString();
                        final CertificateIdentity certificateIdentity = cachedIdentity.certificateIdentity();
                        final String keyOwner = Objects.requireNonNullElse(
                                certificateIdentity.getStreamMetaValue(keyOwnerMetaKey),
                                "null");
                        final List<Map<String, String>> keysForAccountId = map.computeIfAbsent(path,
                                        k -> new HashMap<>())
                                .computeIfAbsent(keyOwner, k -> new ArrayList<>());

                        final String remaining = Duration.between(
                                Instant.now(),
                                certificateIdentity.getExpiryDate()).toString();
                        final Map<String, String> leafMap = Map.of(
                                "expiry", certificateIdentity.getExpiryDate().toString(),
                                "remaining", remaining);
                        keysForAccountId.add(leafMap);
                    }
                });
        return SystemInfoResult.builder(this)
                .addDetail("sourceFiles", map)
                .build();
    }

    private CIKey getOwnerMetaKey(final ReceiveDataConfig receiveDataConfig) {
        final String key = receiveDataConfig.getDataFeedOwnerMetaKey();
        if (NullSafe.isNonBlankString(key)) {
            return CIKey.of(receiveDataConfig.getDataFeedOwnerMetaKey());
        } else {
            throw new RuntimeException("dataFeedOwnerMetaKey is null or blank");
        }
    }

    @Override
    public void start() throws Exception {
        final TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    evictExpired();
                } catch (final Exception e) {
                    LOGGER.error("Error running entry eviction timerTask: {}", e.getMessage(), e);
                }
            }
        };
        LOGGER.info("Starting cache eviction timer");
        timer.scheduleAtFixedRate(timerTask, 0, Duration.ofMinutes(1).toMillis());
    }

    @Override
    public void stop() throws Exception {
        LOGGER.info("Shutting down entry eviction timer");
        try {
            timer.cancel();
        } catch (final Exception e) {
            LOGGER.error("Error shutting down the timer: {}", LogUtil.exceptionMessage(e), e);
        }
    }


    // --------------------------------------------------------------------------------


    private record CachedIdentity(CertificateIdentity certificateIdentity,
                                  Path sourceFile) {

        private CachedIdentity {
            Objects.requireNonNull(certificateIdentity);
            Objects.requireNonNull(sourceFile);
        }

        public boolean isExpired() {
            return certificateIdentity.isExpired();
        }
    }


    // --------------------------------------------------------------------------------


    private record CacheKey(CIKey keyOwner,
                            String certificateDn) {

        private CacheKey {
            Objects.requireNonNull(keyOwner);
            Objects.requireNonNull(certificateDn);
        }
    }
}
