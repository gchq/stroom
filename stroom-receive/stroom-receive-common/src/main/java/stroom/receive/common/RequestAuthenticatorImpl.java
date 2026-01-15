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

package stroom.receive.common;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.security.api.UserIdentity;
import stroom.security.api.UserIdentityFactory;
import stroom.util.concurrent.CachedValue;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class RequestAuthenticatorImpl implements RequestAuthenticator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RequestAuthenticatorImpl.class);

    private final UserIdentityFactory userIdentityFactory;
    private final Provider<ReceiveDataConfig> receiveDataConfigProvider;
    // Inject this so we can mock it for testing
    private final Provider<DataFeedKeyService> dataFeedKeyServiceProvider;
    private final Provider<OidcTokenAuthenticator> oidcTokenAuthenticatorProvider;
    private final Provider<CertificateAuthenticator> certificateAuthenticatorProvider;
    private final Provider<AllowUnauthenticatedAuthenticator> allowUnauthenticatedAuthenticatorProvider;

    private final CachedValue<AuthenticatorFilter, ConfigState> cachedAuthenticationFilter;

    @Inject
    public RequestAuthenticatorImpl(
            final UserIdentityFactory userIdentityFactory,
            final Provider<ReceiveDataConfig> receiveDataConfigProvider,
            final Provider<DataFeedKeyService> dataFeedKeyServiceProvider,
            final Provider<OidcTokenAuthenticator> oidcTokenAuthenticatorProvider,
            final Provider<CertificateAuthenticator> certificateAuthenticatorProvider,
            final Provider<AllowUnauthenticatedAuthenticator> allowUnauthenticatedAuthenticatorProvider) {

        this.userIdentityFactory = userIdentityFactory;
        this.receiveDataConfigProvider = receiveDataConfigProvider;

        // Every 60s, see if config has changed and if so create a new filter
        this.cachedAuthenticationFilter = CachedValue.builder()
                .withMaxCheckIntervalSeconds(60)
                .withStateSupplier(() -> ConfigState.fromConfig(receiveDataConfigProvider.get()))
                .withValueFunction(this::createFilter)
                .build();
        this.dataFeedKeyServiceProvider = dataFeedKeyServiceProvider;
        this.oidcTokenAuthenticatorProvider = oidcTokenAuthenticatorProvider;
        this.certificateAuthenticatorProvider = certificateAuthenticatorProvider;
        this.allowUnauthenticatedAuthenticatorProvider = allowUnauthenticatedAuthenticatorProvider;
    }

    @Override
    public UserIdentity authenticate(final HttpServletRequest request,
                                     final AttributeMap attributeMap) {
        try {
            final AuthenticatorFilter filter = cachedAuthenticationFilter.getValue();
            LOGGER.debug(() -> "Using filter: " + filter.getClass().getName());
            final Optional<UserIdentity> optUserIdentity = filter.authenticate(request, attributeMap);

            final ConfigState configState = cachedAuthenticationFilter.getState();
            final Set<AuthenticationType> enabledAuthenticationTypes = configState.enabledAuthenticationTypes;
            final boolean isAuthRequired = configState.isAuthenticationRequired;

            final int authMechanismCount = enabledAuthenticationTypes.size();

            if (optUserIdentity.isEmpty() && isAuthRequired) {
                if (authMechanismCount == 1) {
                    final AuthenticationType authenticationType = enabledAuthenticationTypes.stream()
                            .findFirst()
                            .orElseThrow();
                    switch (authenticationType) {
                        case DATA_FEED_KEY -> throw new StroomStreamException(
                                StroomStatusCode.CLIENT_DATA_FEED_KEY_REQUIRED, attributeMap);
                        case TOKEN ->
                                throw new StroomStreamException(StroomStatusCode.CLIENT_TOKEN_REQUIRED, attributeMap);
                        case CERTIFICATE ->
                                throw new StroomStreamException(StroomStatusCode.CLIENT_CERTIFICATE_REQUIRED,
                                        attributeMap);
                        default -> {
                            LOGGER.error("Unexpected type {}", authenticationType);
                            throw new StroomStreamException(StroomStatusCode.UNKNOWN_ERROR, attributeMap,
                                    "Unknown authentication type " + authenticationType);
                        }
                    }
                } else if (authMechanismCount == 2
                           && enabledAuthenticationTypes.contains(AuthenticationType.CERTIFICATE)
                           && enabledAuthenticationTypes.contains(AuthenticationType.TOKEN)) {
                    // This code was added a while ago, but is it not sustainable to have one code
                    // for each combination of auth mechanisms. Leaving this here just because we already have it
                    throw new StroomStreamException(StroomStatusCode.CLIENT_TOKEN_OR_CERT_REQUIRED, attributeMap);
                } else {
                    final String mechanismsStr = enabledAuthenticationTypes.stream()
                            .map(AuthenticationType::getDisplayValue)
                            .collect(Collectors.joining(", "));
                    final String msg = "You must use one of the following authentication mechanisms ["
                                       + mechanismsStr + "]";
                    throw new StroomStreamException(StroomStatusCode.AUTHENTICATION_REQUIRED, attributeMap, msg);
                }
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
        } catch (final RuntimeException e) {
            // To help diagnose auth errors. Prob don't want to log to ERROR as there
            // may be lots of legitimate auth errors that we don't care about
            LOGGER.debug("Error authenticating request {}: {}",
                    request.getRequestURI(), e.getMessage(), e);
            throw e;
        }
    }

    private String getMechanismNameOrNull(final boolean isEnabled, final String name) {
        return isEnabled
                ? name
                : null;
    }

    /**
     * Create a combined filter that takes into account the currently configured
     * auth mechanisms
     *
     * @param configState
     * @return
     */
    private AuthenticatorFilter createFilter(final ConfigState configState) {
        final List<AuthenticatorFilter> filters = new ArrayList<>();

        // We want to do this in a consistent order and to prefer say token over cert
        if (configState.isEnabled(AuthenticationType.DATA_FEED_KEY)) {
            filters.add(dataFeedKeyServiceProvider.get());
        }

        if (configState.isEnabled(AuthenticationType.TOKEN)) {
            filters.add(oidcTokenAuthenticatorProvider.get());
        }

        if (configState.isEnabled(AuthenticationType.CERTIFICATE)) {
            filters.add(certificateAuthenticatorProvider.get());
        }

        // If auth is not required then add a fallback filter to provide an UnauthenticatedUserIdentity
        // rather than returning an empty optional
        if (!configState.isAuthenticationRequired) {
            filters.add(allowUnauthenticatedAuthenticatorProvider.get());
        }

        return AuthenticatorFilter.wrap(filters);
    }

    private void processAttributes(final AttributeMap attributeMap,
                                   final Optional<UserIdentity> optUserIdentity) {
        if (attributeMap != null) {
            // Add the user identified in the token (if present) to the attribute map.
            // Use both ID and username as the ID will likely be a nasty UUID while the
            // username will be more useful for a human to read.
            // Set them to null if we have no identity to prevent clients from setting these
            // headers themselves.
            final String uploadUserId = optUserIdentity.map(UserIdentity::subjectId)
                    .filter(NullSafe::isNonBlankString)
                    .orElse(null);
            final String uploadUsername = optUserIdentity.map(UserIdentity::getDisplayName)
                    .filter(NullSafe::isNonBlankString)
                    .orElse(null);

            attributeMap.put(StandardHeaderArguments.UPLOAD_USER_ID, uploadUserId);
            attributeMap.put(StandardHeaderArguments.UPLOAD_USERNAME, uploadUsername);

            // Remove authorization header from attributes as it should not be stored or
            // forwarded on.
            NullSafe.consume(attributeMap, userIdentityFactory::removeAuthEntries);
        }
    }


    // --------------------------------------------------------------------------------


    private record ConfigState(
            boolean isAuthenticationRequired,
            Set<AuthenticationType> enabledAuthenticationTypes) {

        public static ConfigState fromConfig(final ReceiveDataConfig receiveDataConfig) {

            return new ConfigState(
                    receiveDataConfig.isAuthenticationRequired(),
                    NullSafe.mutableEnumSet(AuthenticationType.class,
                            receiveDataConfig.getEnabledAuthenticationTypes()));
        }

        public boolean isEnabled(final AuthenticationType authenticationType) {
            return enabledAuthenticationTypes.contains(authenticationType);
        }
    }
}
