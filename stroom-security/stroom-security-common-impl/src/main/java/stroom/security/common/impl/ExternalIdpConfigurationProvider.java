package stroom.security.common.impl;

import stroom.security.api.exception.AuthenticationException;
import stroom.security.openid.api.OpenIdConfig;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.openid.api.OpenIdConfigurationResponse;
import stroom.security.openid.api.OpenIdConfigurationResponse.Builder;
import stroom.util.HasHealthCheck;
import stroom.util.NullSafe;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.codahale.metrics.health.HealthCheck;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletResponse;

@Singleton
public class ExternalIdpConfigurationProvider
        implements IdpConfigurationProvider, HasHealthCheck {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ExternalIdpConfigurationProvider.class);

    private final Provider<CloseableHttpClient> httpClientProvider;
    private final Provider<OpenIdConfig> openIdConfigProvider;

    private volatile String lastConfigurationEndpoint;
    private volatile OpenIdConfigurationResponse openIdConfigurationResp;

    @Inject
    public ExternalIdpConfigurationProvider(
            final Provider<CloseableHttpClient> httpClientProvider,
            final Provider<OpenIdConfig> openIdConfigProvider) {
        this.httpClientProvider = httpClientProvider;
        this.openIdConfigProvider = openIdConfigProvider;
    }

    @Override
    public OpenIdConfigurationResponse getConfigurationResponse() {
        final OpenIdConfig openIdConfig = openIdConfigProvider.get();
        final String configurationEndpoint = openIdConfig.getOpenIdConfigurationEndpoint();

        if (isFetchRequired(configurationEndpoint)) {
            updateOpenIdConfigurationResponse(openIdConfig);
        }

        return openIdConfigurationResp;
    }

    @Override
    public HealthCheck.Result getHealth() {
        final HealthCheck.ResultBuilder resultBuilder = HealthCheck.Result.builder();
        final OpenIdConfig openIdConfig = openIdConfigProvider.get();
        final String configurationEndpoint = openIdConfig.getOpenIdConfigurationEndpoint();
        if (openIdConfig.isUseInternal()) {
            resultBuilder
                    .healthy()
                    .withMessage("Not using external IDP");
        } else if (NullSafe.isBlankString(configurationEndpoint)) {
                resultBuilder
                        .unhealthy()
                        .withMessage(LogUtil.message("Property {} is false, but {} is unset. " +
                                        "You must provide the configuration endpoint for the external IDP.",
                                openIdConfig.getFullPathStr(OpenIdConfig.PROP_NAME_USE_INTERNAL),
                                openIdConfig.getFullPathStr(OpenIdConfig.PROP_NAME_CONFIGURATION_ENDPOINT)
                        ));
        } else {
            // Hit the config endpoint to check the IDP is accessible.
            // Even if we already have the config from it, if we can't see the IDP we have problems.
            try {
                OpenIdConfigurationResponse response = fetchOpenIdConfigurationResponse(
                        configurationEndpoint);
                if (response != null) {
                    resultBuilder.healthy();
                } else {
                    resultBuilder.unhealthy()
                            .withMessage("Null response");
                }
            } catch (Exception e) {
                resultBuilder.unhealthy(e)
                        .withMessage("Error fetching Open ID Connect configuration from " +
                                configurationEndpoint + ". Is the identity provider down?");
            }
        }
        return resultBuilder.build();
    }

    private boolean isFetchRequired(final String configurationEndpoint) {
        return openIdConfigurationResp == null
                || !Objects.equals(lastConfigurationEndpoint, configurationEndpoint);
    }

    private synchronized OpenIdConfigurationResponse updateOpenIdConfigurationResponse(
            final OpenIdConfig openIdConfig) {
        final String configurationEndpoint = openIdConfig.getOpenIdConfigurationEndpoint();
        // Re-test under lock
        if (isFetchRequired(configurationEndpoint)) {
            LOGGER.info("Fetching open id configuration from: " + configurationEndpoint);
            try {
                openIdConfigurationResp = fetchOpenIdConfigurationResponse(configurationEndpoint);
            } catch (final RuntimeException | IOException e) {
                LOGGER.error(e.getMessage(), e);
            }

            if (openIdConfigurationResp == null) {
                openIdConfigurationResp = OpenIdConfigurationResponse.builder()
                        .issuer(openIdConfig.getIssuer())
                        .authorizationEndpoint(openIdConfig.getAuthEndpoint())
                        .tokenEndpoint(openIdConfig.getTokenEndpoint())
                        .jwksUri(openIdConfig.getJwksUri())
                        .logoutEndpoint(openIdConfig.getLogoutEndpoint())
                        .build();
            }
            lastConfigurationEndpoint = configurationEndpoint;
        }
        return openIdConfigurationResp;
    }

    private OpenIdConfigurationResponse fetchOpenIdConfigurationResponse(
            final String configurationEndpoint) throws IOException {
        LOGGER.info("Fetching open id configuration from: " + configurationEndpoint);
        try (final CloseableHttpClient httpClient = httpClientProvider.get()) {
            final HttpGet httpGet = new HttpGet(configurationEndpoint);
            try (final CloseableHttpResponse response = httpClient.execute(httpGet)) {
                if (HttpServletResponse.SC_OK == response.getStatusLine().getStatusCode()) {
                    final HttpEntity entity = response.getEntity();
                    String msg;
                    try (final InputStream is = entity.getContent()) {
                        msg = StreamUtil.streamToString(is);
                    }

                    final ObjectMapper mapper = new ObjectMapper();
                    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                    return mapper.readValue(msg, OpenIdConfigurationResponse.class);
                } else {
                    throw new AuthenticationException("Received status " + response.getStatusLine() +
                            " from " + configurationEndpoint);
                }
            }
        }
    }

    private static OpenIdConfigurationResponse mergeResponse(
            final OpenIdConfigurationResponse response,
            final OpenIdConfiguration openIdConfiguration) {

        // TODO: 30/11/2022 Debatable if we need to allow these overrides,
        //  ought to just rely on the response from the idp
        // Overwrite configuration with any values we might have manually configured.
        final Builder builder = response.copy();
        if (!NullSafe.isBlankString(openIdConfiguration.getIssuer())) {
            builder.issuer(openIdConfiguration.getIssuer());
        }
        if (!NullSafe.isBlankString(openIdConfiguration.getAuthEndpoint())) {
            builder.authorizationEndpoint(openIdConfiguration.getAuthEndpoint());
        }
        if (!NullSafe.isBlankString(openIdConfiguration.getTokenEndpoint())) {
            builder.tokenEndpoint(openIdConfiguration.getTokenEndpoint());
        }
        if (!NullSafe.isBlankString(openIdConfiguration.getJwksUri())) {
            builder.jwksUri(openIdConfiguration.getJwksUri());
        }
        if (!NullSafe.isBlankString(openIdConfiguration.getLogoutEndpoint())) {
            builder.logoutEndpoint(openIdConfiguration.getLogoutEndpoint());
//        } else if (NullSafe.isBlankString(response.getLogoutEndpoint())) {
//            // If the IdP doesn't provide a logout endpoint then use the internal one to invalidate
//            // the session and redirect to perform a a new auth flow.
//            builder.logoutEndpoint(
//                    uriFactory.publicUri(INTERNAL_LOGOUT_ENDPOINT).toString());
        }
        return builder.build();
    }

    @Override
    public String getOpenIdConfigurationEndpoint() {
        return openIdConfigProvider.get().getOpenIdConfigurationEndpoint();
    }

    @Override
    public String getClientId() {
        return openIdConfigProvider.get().getClientId();
    }

    @Override
    public String getClientSecret() {
        return openIdConfigProvider.get().getClientSecret();
    }

    @Override
    public boolean isFormTokenRequest() {
        return openIdConfigProvider.get().isFormTokenRequest();
    }

    @Override
    public String getRequestScope() {
        return openIdConfigProvider.get().getRequestScope();
    }

    @Override
    public boolean isValidateAudience() {
        return openIdConfigProvider.get().isValidateAudience();
    }

    @Override
    public String getLogoutRedirectParamName() {
        return openIdConfigProvider.get().getLogoutRedirectParamName();
    }
}
