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

package stroom.security.common.impl;

import stroom.security.api.exception.AuthenticationException;
import stroom.security.openid.api.AbstractOpenIdConfig;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.openid.api.OpenIdConfigurationResponse;
import stroom.security.openid.api.OpenIdConfigurationResponse.Builder;
import stroom.util.HasHealthCheck;
import stroom.util.jersey.JerseyClientFactory;
import stroom.util.jersey.JerseyClientName;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import com.codahale.metrics.health.HealthCheck;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Combines the values from {@link AbstractOpenIdConfig} with values obtained from the external
 * Open ID Connect IDP server.
 */
@Singleton
public class ExternalIdpConfigurationProvider
        implements IdpConfigurationProvider, HasHealthCheck {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ExternalIdpConfigurationProvider.class);
    private static final long MAX_SLEEP_TIME_MS = 30_000;

    private final JerseyClientFactory jerseyClientFactory;
    // Important to use AbstractOpenIdConfig here and not OpenIdConfiguration as the former
    // is bound to the yaml config, the latter is what we are presenting this as.
    private final Provider<AbstractOpenIdConfig> localOpenIdConfigProvider;

    private volatile String lastConfigurationEndpoint;
    private volatile OpenIdConfigurationResponse openIdConfigurationResp;

    @Inject
    public ExternalIdpConfigurationProvider(
            final Provider<AbstractOpenIdConfig> localOpenIdConfigProvider,
            final JerseyClientFactory jerseyClientFactory) {
        this.localOpenIdConfigProvider = localOpenIdConfigProvider;
        this.jerseyClientFactory = jerseyClientFactory;
    }

    @Override
    public OpenIdConfigurationResponse getConfigurationResponse() {
        final AbstractOpenIdConfig abstractOpenIdConfig = localOpenIdConfigProvider.get();
        final String configurationEndpoint = abstractOpenIdConfig.getOpenIdConfigurationEndpoint();

        if (isFetchRequired(configurationEndpoint)) {
            updateOpenIdConfigurationResponse(abstractOpenIdConfig);
        }

        return openIdConfigurationResp;
    }

    @Override
    public HealthCheck.Result getHealth() {
        final HealthCheck.ResultBuilder resultBuilder = HealthCheck.Result.builder();
        final AbstractOpenIdConfig abstractOpenIdConfig = localOpenIdConfigProvider.get();
        final String configurationEndpoint = abstractOpenIdConfig.getOpenIdConfigurationEndpoint();
        final IdpType idpType = abstractOpenIdConfig.getIdentityProviderType();
        LOGGER.debug("Checking health, idpType: {}, configurationEndpoint: {}", idpType, configurationEndpoint);

        if (!IdpType.EXTERNAL_IDP.equals(idpType)) {
            resultBuilder
                    .healthy()
                    .withMessage("Not using external IDP (Using "
                                 + abstractOpenIdConfig.getIdentityProviderType().toString().toLowerCase() + ")");
        } else if (NullSafe.isBlankString(configurationEndpoint)) {
            resultBuilder
                    .unhealthy()
                    .withMessage(LogUtil.message("Property {} is false, but {} is unset. " +
                                                 "You must provide the configuration endpoint for the external IDP.",
                            abstractOpenIdConfig.getFullPathStr(AbstractOpenIdConfig.PROP_NAME_IDP_TYPE),
                            abstractOpenIdConfig.getFullPathStr(AbstractOpenIdConfig.PROP_NAME_CONFIGURATION_ENDPOINT)
                    ));
        } else {
            // Hit the config endpoint to check the IDP is accessible.
            // Even if we already have the config from it, if we can't see the IDP we have problems.
            resultBuilder.withDetail("url", configurationEndpoint);
            try {
                fetchOpenIdConfigurationResponse(configurationEndpoint, abstractOpenIdConfig);
                resultBuilder.healthy();
            } catch (final Exception e) {
                resultBuilder.unhealthy(e)
                        .withMessage("Error fetching Open ID Connect configuration from " +
                                     configurationEndpoint + ". Is the identity provider down?");
            }
        }
        return resultBuilder.build();
    }

    private boolean isFetchRequired(final String configurationEndpoint) {
        // Debatable if we need to re-fetch in case any of the config has changed.
        return openIdConfigurationResp == null
               || !Objects.equals(lastConfigurationEndpoint, configurationEndpoint);
    }

    private OpenIdConfigurationResponse updateOpenIdConfigurationResponse(
            final AbstractOpenIdConfig abstractOpenIdConfig) {
        final String configurationEndpoint = abstractOpenIdConfig.getOpenIdConfigurationEndpoint();

        LOGGER.debug("About to get lock to update open id configuration");
        synchronized (this) {
            LOGGER.debug("Got lock");
            // Re-test under lock
            if (isFetchRequired(configurationEndpoint)) {
                try {
                    final OpenIdConfigurationResponse response = fetchOpenIdConfigurationResponse(
                            configurationEndpoint, abstractOpenIdConfig);
                    openIdConfigurationResp = mergeResponse(response, abstractOpenIdConfig);
                } catch (final RuntimeException | IOException e) {
                    LOGGER.error(e.getMessage(), e);
                }

                if (openIdConfigurationResp == null) {
                    openIdConfigurationResp = OpenIdConfigurationResponse.builder()
                            .issuer(abstractOpenIdConfig.getIssuer())
                            .authorizationEndpoint(abstractOpenIdConfig.getAuthEndpoint())
                            .tokenEndpoint(abstractOpenIdConfig.getTokenEndpoint())
                            .jwksUri(abstractOpenIdConfig.getJwksUri())
                            .logoutEndpoint(abstractOpenIdConfig.getLogoutEndpoint())
                            .build();
                }
                lastConfigurationEndpoint = configurationEndpoint;
            }
            return openIdConfigurationResp;
        }
    }

    private OpenIdConfigurationResponse fetchOpenIdConfigurationResponse(
            final String configurationEndpoint,
            final AbstractOpenIdConfig abstractOpenIdConfig) throws IOException {

        Objects.requireNonNull(configurationEndpoint,
                "Property "
                + abstractOpenIdConfig.getFullPathStr(AbstractOpenIdConfig.PROP_NAME_CONFIGURATION_ENDPOINT)
                + " has not been set");
        LOGGER.info("Fetching open id configuration from: " + configurationEndpoint);

//        final HttpGet httpGet = new HttpGet(configurationEndpoint);
        long sleepMs = 500;
        Throwable lastThrowable = null;

        while (true) {
            try {
                final WebTarget webTarget = jerseyClientFactory.getNamedClient(JerseyClientName.OPEN_ID)
                        .target(configurationEndpoint);

                try (final Response response = webTarget.request(MediaType.APPLICATION_JSON).get()) {
                    if (response.getStatus() == HttpServletResponse.SC_OK) {
                        // According to the spec, the response may contain unexpected props so as we don't easily
                        // have access to configure the client to relax jackson, do it manually.
                        // https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderConfigurationResponse
                        final String responseJson = response.readEntity(String.class);
                        LOGGER.debug("configurationEndpoint: '{}'\n{}",
                                configurationEndpoint,
                                responseJson);
                        final OpenIdConfigurationResponse openIdConfigurationResponse = parseConfigurationResponse(
                                configurationEndpoint,
                                responseJson);

                        LOGGER.info("Successfully fetched open id configuration from: " + configurationEndpoint);
                        return openIdConfigurationResponse;
                    } else {
                        throw new AuthenticationException("Received status " + response.getStatus() +
                                                          " from " + configurationEndpoint);
                    }
                }
            } catch (final AuthenticationException e) {
                // This is not a connection issue so just bubble it up and likely crash the app
                // if this is happening as part of the guice injector init.
                throw e;
            } catch (final Exception e) {
                // The app is pretty dead without a connection to the IDP so keep retrying
                LOGGER.warn(LogUtil.message(
                        "Unable to establish connection to the identity provider at {} to fetch Open ID " +
                        "configuration. Will try again in {}. Enable debug to see stack trace. Error: {}",
                        configurationEndpoint, Duration.ofMillis(sleepMs), e.getMessage()));

                if (LOGGER.isDebugEnabled()) {
                    if (lastThrowable == null || !e.getMessage().equals(lastThrowable.getMessage())) {
                        // Only log the stack when it changes, else it fills up the log pretty quickly
                        LOGGER.debug("Unable to establish connection to IDP at {} to fetch Open ID configuration.",
                                configurationEndpoint, e);
                    }
                    lastThrowable = e;
                }
                try {
                    Thread.sleep(sleepMs);
                } catch (final InterruptedException ie) {
                    // Nothing to do here as the
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(LogUtil.message("Thread interrupted waiting to connect to {}",
                            configurationEndpoint), e);
                }

                // Gradually increase the sleep time up to a maximum
                sleepMs = (long) (sleepMs * 1.3);
                if (sleepMs >= MAX_SLEEP_TIME_MS) {
                    sleepMs = MAX_SLEEP_TIME_MS;
                }
            }
        }
    }

    private OpenIdConfigurationResponse parseConfigurationResponse(final String configurationEndpoint,
                                                                   final String msg) {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        final OpenIdConfigurationResponse openIdConfigurationResponse;
        try {
            openIdConfigurationResponse = mapper.readValue(
                    msg,
                    OpenIdConfigurationResponse.class);
        } catch (final JsonProcessingException e) {
            throw new AuthenticationException(LogUtil.message("Unable to parse open ID configuration " +
                                                              "from {}. {}", configurationEndpoint, e.getMessage()), e);
        }
        final AbstractOpenIdConfig localConfig = localOpenIdConfigProvider.get();
        final String responseIssuer = openIdConfigurationResponse.getIssuer();
        final Set<String> validIssuers = getValidIssuers(localConfig);
        if (!validIssuers.isEmpty()) {
            // Allow local config to define a set of issuers that we expect to see
            if (!validIssuers.contains(responseIssuer)) {
                throw new AuthenticationException(LogUtil.message(
                        "Issuer '{}' obtained from configuration endpoint {} " +
                        "does not match those in the 'issuer' or validIssuers' properties.",
                        openIdConfigurationResponse.getIssuer(), configurationEndpoint));
            }
        } else if (!configurationEndpoint.startsWith(responseIssuer)) {
            // Spec says issuer in config response must match the config endpoint base URL
            // https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderConfigurationRequest
            // Some IDPs don't seem to follow the spec.
            throw new AuthenticationException(LogUtil.message("Issuer '{}' obtained from configuration endpoint {} " +
                                                              "does not share the same base URI. If this is valid " +
                                                              "consider adding the non-standard " +
                                                              "issuer to the 'validIssuers' property.",
                    openIdConfigurationResponse.getIssuer(), configurationEndpoint));
        }
        return openIdConfigurationResponse;
    }

    private Set<String> getValidIssuers(final AbstractOpenIdConfig openIdConfig) {
        final Set<String> validIssuers = new HashSet<>();
        if (!NullSafe.isBlankString(openIdConfig.getIssuer())) {
            validIssuers.add(openIdConfig.getIssuer());
        }
        validIssuers.addAll(NullSafe.set(openIdConfig.getValidIssuers()));
        LOGGER.debug(() -> LogUtil.message("Valid issuers:\n{}", String.join("\n", validIssuers)));
        return validIssuers;
    }

    private static OpenIdConfigurationResponse mergeResponse(
            final OpenIdConfigurationResponse response,
            final OpenIdConfiguration openIdConfiguration) {

        // TODO: 30/11/2022 Debatable if we need to allow these overrides,
        //  ought to just rely on the response from the idp
        // Overwrite configuration with any values we might have manually configured.
        final Builder builder = response.copy();

        final BiConsumer<Consumer<String>, Supplier<String>> mergeFunc = (builderSetter, configGetter) -> {
            final String val = configGetter.get();
            if (!NullSafe.isBlankString(val)) {
                builderSetter.accept(val);
            }
        };

        mergeFunc.accept(builder::issuer, openIdConfiguration::getIssuer);
        mergeFunc.accept(builder::authorizationEndpoint, openIdConfiguration::getAuthEndpoint);
        mergeFunc.accept(builder::tokenEndpoint, openIdConfiguration::getTokenEndpoint);
        mergeFunc.accept(builder::jwksUri, openIdConfiguration::getJwksUri);
        mergeFunc.accept(builder::logoutEndpoint, openIdConfiguration::getLogoutEndpoint);

        return builder.build();
    }

    @Override
    public IdpType getIdentityProviderType() {
        return localOpenIdConfigProvider.get().getIdentityProviderType();
    }

    @Override
    public String getOpenIdConfigurationEndpoint() {
        return localOpenIdConfigProvider.get().getOpenIdConfigurationEndpoint();
    }

    @Override
    public String getClientId() {
        return localOpenIdConfigProvider.get().getClientId();
    }

    @Override
    public String getClientSecret() {
        return localOpenIdConfigProvider.get().getClientSecret();
    }

    @Override
    public boolean isFormTokenRequest() {
        return localOpenIdConfigProvider.get().isFormTokenRequest();
    }

    @Override
    public List<String> getRequestScopes() {
        return localOpenIdConfigProvider.get().getRequestScopes();
    }

    @Override
    public List<String> getClientCredentialsScopes() {
        return localOpenIdConfigProvider.get().getClientCredentialsScopes();
    }

    @Override
    public Set<String> getAllowedAudiences() {
        return localOpenIdConfigProvider.get().getAllowedAudiences();
    }

    @Override
    public boolean isAudienceClaimRequired() {
        return localOpenIdConfigProvider.get().isAudienceClaimRequired();
    }

    @Override
    public Set<String> getValidIssuers() {
        return localOpenIdConfigProvider.get().getValidIssuers();
    }

    @Override
    public String getUniqueIdentityClaim() {
        return localOpenIdConfigProvider.get().getUniqueIdentityClaim();
    }

    @Override
    public String getUserDisplayNameClaim() {
        return localOpenIdConfigProvider.get().getUserDisplayNameClaim();
    }

    @Override
    public String getFullNameClaimTemplate() {
        return localOpenIdConfigProvider.get().getFullNameClaimTemplate();
    }

    @Override
    public String getLogoutRedirectParamName() {
        return localOpenIdConfigProvider.get().getLogoutRedirectParamName();
    }

    @Override
    public Set<String> getExpectedSignerPrefixes() {
        return localOpenIdConfigProvider.get().getExpectedSignerPrefixes();
    }

    @Override
    public String getPublicKeyUriPattern() {
        return localOpenIdConfigProvider.get().getPublicKeyUriPattern();
    }
}
