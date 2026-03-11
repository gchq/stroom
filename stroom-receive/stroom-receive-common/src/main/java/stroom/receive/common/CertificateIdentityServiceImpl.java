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


import stroom.cache.api.CacheManager;
import stroom.meta.api.AttributeMap;
import stroom.proxy.StroomStatusCode;
import stroom.receive.common.DataFeedIdentity.IdentityStatus;
import stroom.security.api.UserIdentity;
import stroom.util.PredicateUtil;
import stroom.util.cert.CertificateExtractor;
import stroom.util.logging.LogUtil;
import stroom.util.shared.string.CIKey;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Predicate;

@Singleton
public class CertificateIdentityServiceImpl implements CertificateIdentityService, AuthenticatorFilter {

    private final CertificateExtractor certificateExtractor;
    private final CertificateAuthenticator certificateAuthenticator;
    private final Provider<ReceiveDataConfig> receiveDataConfigProvider;
    private final Map<String, Set<CachedIdentity>> dnToIdentityMap;

    @Inject
    public CertificateIdentityServiceImpl(final CertificateAuthenticator certificateAuthenticator,
                                          final CacheManager cacheManager,
                                          final CertificateExtractor certificateExtractor,
                                          final Provider<ReceiveDataConfig> receiveDataConfigProvider) {
        this.certificateAuthenticator = certificateAuthenticator;
        this.certificateExtractor = certificateExtractor;
//        this.cacheManager = cacheManager;
        this.receiveDataConfigProvider = receiveDataConfigProvider;
        this.dnToIdentityMap = new ConcurrentHashMap<>();
    }

    @Override
    public synchronized IdentityStatus addCertificateIdentity(final CertificateIdentity certificateIdentity,
                                                              final Path sourceFile) {
        Objects.requireNonNull(certificateIdentity);
        Objects.requireNonNull(sourceFile);
        final CachedIdentity cachedIdentity = new CachedIdentity(certificateIdentity, sourceFile);
        final String certificateDn = cachedIdentity.certificateIdentity.getCertificateDn();
        final boolean success = dnToIdentityMap.computeIfAbsent(
                        certificateDn,
                        ignored -> ConcurrentHashMap.newKeySet())
                .add(cachedIdentity);
        if (success) {
            return IdentityStatus.ADDED;
        } else {
            return IdentityStatus.DUPLICATE;
        }
    }

    @Override
    public synchronized void removeKeysForFile(final Path sourceFile) {
        Objects.requireNonNull(sourceFile);

        LOGGER.info("Evicting dataFeedKeys for sourceFile {}", sourceFile);
        final LongAdder counter = new LongAdder();
        final Predicate<CachedIdentity> sourceFilePredicate = PredicateUtil.countingPredicate(
                counter, cachedKey ->
                        Objects.equals(sourceFile, cachedKey.sourceFile()));

        dnToIdentityMap.forEach((dn, identitySet) -> {
            identitySet.removeIf(sourceFilePredicate);
        });
        LOGGER.debug("Removed {} dnToIdentityMap sub-entries", counter);
        LOGGER.debug(() -> LogUtil.message("Total cached keys: {}", dnToIdentityMap.values()
                .stream()
                .mapToInt(Set::size)
                .sum()));
    }

    @Override
    public Optional<UserIdentity> authenticate(final HttpServletRequest request,
                                               final AttributeMap attributeMap) {
        try {
            final Optional<UserIdentity> optUserIdentity = certificateExtractor.getDN(request)
                    .flatMap(dn -> {
                        final Set<CachedIdentity> identities = dnToIdentityMap.get(dn);
                        final long nowMs = System.currentTimeMillis();
                        return identities.stream()
                                .filter(identity ->
                                        nowMs < identity.certificateIdentity.getExpiryDateEpochMs())
                                .findFirst();
                    })
                    .map(cachedIdentity -> {
                        final CIKey keyOwnerMetaKey = getOwnerMetaKey(receiveDataConfigProvider.get());
                        final Map<CIKey, String> streamMetaData = cachedIdentity.certificateIdentity()
                                .getCIStreamMetaData();
                        final String keyOwner = streamMetaData.get(keyOwnerMetaKey);
                        Objects.requireNonNull(keyOwner);
                        attributeMap.putAll(cachedIdentity.certificateIdentity.getStreamMetaData());
                        return new DataFeedUserIdentity(keyOwner);
                    });
            LOGGER.debug("Returning optUserIdentity: {}", optUserIdentity);
            return optUserIdentity;
        } catch (final StroomStreamException e) {
            throw e;
        } catch (final Exception e) {
            throw new StroomStreamException(
                    StroomStatusCode.CLIENT_CERTIFICATE_DN_NOT_AUTHENTICATED, attributeMap, e.getMessage());
        }
    }

    private CIKey getOwnerMetaKey(final ReceiveDataConfig receiveDataConfig) {
        return CIKey.of(receiveDataConfig.getDataFeedKeyOwnerMetaKey());
    }


    // --------------------------------------------------------------------------------


    private record CachedIdentity(CertificateIdentity certificateIdentity,
                                  Path sourceFile) {

    }
}
