package stroom.security.common.impl;

import stroom.security.api.exception.AuthenticationException;
import stroom.security.openid.api.IdpType;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletResponse;

/**
 * Combines the values from {@link OpenIdConfig} with values obtained from the external
 * Open ID Connect IDP server.
 */
@Singleton
public class ExternalIdpConfigurationProvider
        implements IdpConfigurationProvider, HasHealthCheck {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ExternalIdpConfigurationProvider.class);
    private static final long MAX_SLEEP_TIME_MS = 30_000;

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

        if (!IdpType.EXTERNAL_IDP.equals(openIdConfig.getIdentityProviderType())) {
            resultBuilder
                    .healthy()
                    .withMessage("Not using external IDP (Using "
                            + openIdConfig.getIdentityProviderType().toString().toLowerCase() + ")");
        } else if (NullSafe.isBlankString(configurationEndpoint)) {
            resultBuilder
                    .unhealthy()
                    .withMessage(LogUtil.message("Property {} is false, but {} is unset. " +
                                    "You must provide the configuration endpoint for the external IDP.",
                            openIdConfig.getFullPathStr(OpenIdConfig.PROP_NAME_IDP_TYPE),
                            openIdConfig.getFullPathStr(OpenIdConfig.PROP_NAME_CONFIGURATION_ENDPOINT)
                    ));
        } else {
            // Hit the config endpoint to check the IDP is accessible.
            // Even if we already have the config from it, if we can't see the IDP we have problems.
            try {
                OpenIdConfigurationResponse response = fetchOpenIdConfigurationResponse(
                        configurationEndpoint, openIdConfig);
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
        // Debatable if we need to re-fetch in case any of the config has changed.
        return openIdConfigurationResp == null
                || !Objects.equals(lastConfigurationEndpoint, configurationEndpoint);
    }

    private OpenIdConfigurationResponse updateOpenIdConfigurationResponse(
            final OpenIdConfig openIdConfig) {
        final String configurationEndpoint = openIdConfig.getOpenIdConfigurationEndpoint();

        LOGGER.debug("About to get lock to update open id configuration");
        synchronized (this) {
            LOGGER.debug("Got lock");
            // Re-test under lock
            if (isFetchRequired(configurationEndpoint)) {
                try {
                    final OpenIdConfigurationResponse response = fetchOpenIdConfigurationResponse(
                            configurationEndpoint, openIdConfig);
                    openIdConfigurationResp = mergeResponse(response, openIdConfig);
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
    }

    private OpenIdConfigurationResponse fetchOpenIdConfigurationResponse(
            final String configurationEndpoint,
            final OpenIdConfig openIdConfig) throws IOException {

        Objects.requireNonNull(configurationEndpoint,
                "Property "
                        + openIdConfig.getFullPathStr(OpenIdConfig.PROP_NAME_CONFIGURATION_ENDPOINT)
                        + " has not been set");
        LOGGER.info("Fetching open id configuration from: " + configurationEndpoint);
        try (final CloseableHttpClient httpClient = httpClientProvider.get()) {
            final HttpGet httpGet = new HttpGet(configurationEndpoint);
            long sleepMs = 500;
            Throwable lastThrowable = null;

            while (true) {
                try (final CloseableHttpResponse response = httpClient.execute(httpGet)) {
                    if (HttpServletResponse.SC_OK == response.getStatusLine().getStatusCode()) {
                        final HttpEntity entity = response.getEntity();
                        String msg;
                        try (final InputStream is = entity.getContent()) {
                            msg = StreamUtil.streamToString(is);
                        }

                        final OpenIdConfigurationResponse openIdConfigurationResponse = parseConfigurationResponse(
                                configurationEndpoint,
                                msg);
                        LOGGER.info("Successfully fetched open id configuration from: " + configurationEndpoint);
                        return openIdConfigurationResponse;
                    } else {
                        throw new AuthenticationException("Received status " + response.getStatusLine() +
                                " from " + configurationEndpoint);
                    }
                } catch (AuthenticationException e) {
                    // This is not a connection issue so just bubble it up and likely crash the app
                    // if this is happening as part of the guice injector init.
                    throw e;
                } catch (Exception e) {
                    // The app is pretty dead without a connection to the IDP so keep retrying
                    LOGGER.warn(LogUtil.message(
                            "Unable to establish connection to {} to fetch Open ID configuration. " +
                                    "Will try again in {}. Enable debug to see stack trace. Error: {}",
                            configurationEndpoint, Duration.ofMillis(sleepMs), e.getMessage()));

                    if (LOGGER.isDebugEnabled()) {
                        if (lastThrowable == null || !e.getMessage().equals(lastThrowable.getMessage())) {
                            // Only log the stack when it changes, else it fills up the log pretty quickly
                            LOGGER.debug("Unable to establish connection to {} to fetch Open ID configuration.",
                                    configurationEndpoint, e);
                        }
                        lastThrowable = e;
                    }
                    try {
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException ie) {
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
        } catch (JsonProcessingException e) {
            throw new AuthenticationException(LogUtil.message("Unable to parse open ID configuration " +
                    "from {}. {}", configurationEndpoint, e.getMessage()), e);
        }
        // Spec says issue must match the config endpoint base
        // https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderConfigurationRequest
        if (!configurationEndpoint.startsWith(openIdConfigurationResponse.getIssuer())) {
            throw new AuthenticationException(LogUtil.message("Invalid issuer '{}' for endpoint {}",
                    openIdConfigurationResponse.getIssuer(), configurationEndpoint));
        }
        return openIdConfigurationResponse;
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
        return openIdConfigProvider.get().getIdentityProviderType();
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
