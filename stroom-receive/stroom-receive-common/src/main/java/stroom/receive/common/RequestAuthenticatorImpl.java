/*
 * Copyright 2024 Crown Copyright
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
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.security.api.UserIdentity;
import stroom.security.api.UserIdentityFactory;
import stroom.util.NullSafe;
import stroom.util.cert.CertificateExtractor;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.string.CIKey;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class RequestAuthenticatorImpl implements RequestAuthenticator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RequestAuthenticatorImpl.class);

    private final UserIdentityFactory userIdentityFactory;
    private final Provider<ReceiveDataConfig> receiveDataConfigProvider;
    // Inject this so we can mock it for testing
    private final CertificateExtractor certificateExtractor;

    @Inject
    public RequestAuthenticatorImpl(final UserIdentityFactory userIdentityFactory,
                                    final Provider<ReceiveDataConfig> receiveDataConfigProvider,
                                    final CertificateExtractor certificateExtractor) {
        this.userIdentityFactory = userIdentityFactory;
        this.receiveDataConfigProvider = receiveDataConfigProvider;
        this.certificateExtractor = certificateExtractor;
    }

    @Override
    public UserIdentity authenticate(final HttpServletRequest request,
                                     final AttributeMap attributeMap) {
        try {
            Optional<UserIdentity> optUserIdentity = Optional.empty();
            final ReceiveDataConfig receiveDataConfig = receiveDataConfigProvider.get();
            final boolean isAuthRequired = receiveDataConfig.isAuthenticationRequired();
            final boolean isTokenAuthEnabled = receiveDataConfig.isTokenAuthenticationEnabled();
            final boolean isCertAuthEnabled = receiveDataConfig.isCertificateAuthenticationEnabled();

            // Try tokens first in preference
            final boolean foundToken = userIdentityFactory.hasAuthenticationToken(request);
            if (isTokenAuthEnabled) {
                if (foundToken) {
                    try {
                        optUserIdentity = userIdentityFactory.getApiUserIdentity(request);
                        if (optUserIdentity.isEmpty() && isAuthRequired && !isCertAuthEnabled) {
                            throw new StroomStreamException(StroomStatusCode.CLIENT_TOKEN_NOT_AUTHENTICATED,
                                    attributeMap);
                        }
                    } catch (Exception e) {
                        throw new StroomStreamException(
                                StroomStatusCode.CLIENT_TOKEN_NOT_AUTHENTICATED, attributeMap, e.getMessage());
                    }
                } else {
                    // No token found
                    if (isAuthRequired && !isCertAuthEnabled) {
                        throw new StroomStreamException(StroomStatusCode.CLIENT_TOKEN_REQUIRED, attributeMap);
                    }
                }
            } else if (LOGGER.isDebugEnabled() && foundToken) {
                LOGGER.debug("Request has token but token authentication is not enabled. {}", attributeMap);
            }

            // Now try certs if
            final Optional<String> optCertCommonName = certificateExtractor.getCN(request);

            if (isCertAuthEnabled) {
                if (optUserIdentity.isEmpty()) {
                    if (optCertCommonName.isPresent()) {
                        // Not much we can do with the cert. The user won't exist in stroom, so as long
                        // as the cert is trusted, we can get the CN and use that as the identity.
                        // Debatable whether this identity should be created in the UserIdentityFactory
                        optUserIdentity = optCertCommonName.map(CertificateUserIdentity::new);
                    } else {
                        // No cert found
                        if (isAuthRequired) {
                            if (isTokenAuthEnabled) {
                                throw new StroomStreamException(
                                        StroomStatusCode.CLIENT_TOKEN_OR_CERT_REQUIRED, attributeMap);
                            } else {
                                throw new StroomStreamException(
                                        StroomStatusCode.CLIENT_CERTIFICATE_REQUIRED, attributeMap);
                            }
                        }
                    }
                }
            } else if (LOGGER.isDebugEnabled() && optCertCommonName.isPresent()) {
                // Cert auth not enabled
                LOGGER.debug("Request has certificate but certificate authentication is not enabled. {}", attributeMap);
            }

            if (optUserIdentity.isEmpty() && !isAuthRequired) {
                // Debatable whether this identity should be created in the UserIdentityFactory
                optUserIdentity = Optional.of(UnauthenticatedUserIdentity.getInstance());
            }

            // Add identity attrs and remove auth ones
            processAttributes(attributeMap, optUserIdentity);

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Authenticated user as {} of type {}",
                        optUserIdentity.map(Objects::toString)
                                .orElse("EMPTY"),
                        optUserIdentity.map(usr -> usr.getClass().getName())
                                .orElse("EMPTY"));
            }

            return optUserIdentity.orElseThrow(() -> {
                LOGGER.error("optUserIdentity should not be empty here. request: {}, attributeMap: {}",
                        request.getRequestURI(), attributeMap);
                return new StroomStreamException(StroomStatusCode.UNKNOWN_ERROR, attributeMap);
            });
        } catch (RuntimeException e) {
            // To help diagnose auth errors. Prob don't want to log to ERROR as there
            // may be lots of legitimate auth errors that we don't care about
            LOGGER.debug("Error authenticating request {}: {}",
                    request.getRequestURI(), e.getMessage(), e);
            throw e;
        }
    }

    private void processAttributes(final AttributeMap attributeMap,
                                   final Optional<UserIdentity> optUserIdentity) {
        if (attributeMap != null) {
            // Add the user identified in the token (if present) to the attribute map.
            // Use both ID and username as the ID will likely be a nasty UUID while the username will be more
            // useful for a human to read.
            optUserIdentity.ifPresent(userIdentity -> {
                NullSafe.consume(userIdentity.getSubjectId(), id ->
                        attributeMap.put(StandardHeaderArguments.UPLOAD_USER_ID, id));
                NullSafe.consume(userIdentity.getDisplayName(), username ->
                        attributeMap.put(StandardHeaderArguments.UPLOAD_USERNAME, username));
            });

            // Remove authorization header from attributes as it should not be stored or forwarded on.
            removeAuthorisationEntries(attributeMap);
        }
    }

    @Override
    public boolean hasAuthenticationToken(final HttpServletRequest request) {
        return userIdentityFactory.hasAuthenticationToken(request);
    }

    private void removeAuthorisationEntries(final Map<CIKey, String> headers) {
        NullSafe.consume(headers, userIdentityFactory::removeAuthEntries);
    }

    @Override
    public Map<String, String> getAuthHeaders(final UserIdentity userIdentity) {
        return userIdentityFactory.getAuthHeaders(userIdentity);
    }

    @Override
    public Map<String, String> getServiceUserAuthHeaders() {
        return userIdentityFactory.getServiceUserAuthHeaders();
    }
}
