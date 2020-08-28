package stroom.security.impl;

import stroom.config.common.UriFactory;
import stroom.security.impl.exception.AuthenticationException;
import stroom.security.openid.api.OpenIdClientFactory;
import stroom.security.openid.api.OpenIdConfigurationResponse;
import stroom.util.io.StreamUtil;
import stroom.util.shared.ResourcePaths;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public class ResolvedOpenIdConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResolvedOpenIdConfig.class);

    public static final String INTERNAL_ISSUER = "stroom";
    // These paths must tally up with those in stroom.authentication.oauth2.OAuth2Resource
    private static final String OAUTH2_BASE_PATH = "/oauth2/v1/noauth";
    public static final String INTERNAL_AUTH_ENDPOINT = ResourcePaths.buildAuthenticatedApiPath(
            OAUTH2_BASE_PATH, "/auth");
    public static final String INTERNAL_TOKEN_ENDPOINT = ResourcePaths.buildAuthenticatedApiPath(
            OAUTH2_BASE_PATH, "/token");
    public static final String INTERNAL_JWKS_URI = ResourcePaths.buildAuthenticatedApiPath(
            OAUTH2_BASE_PATH, "/certs");

    private final OpenIdConfig openIdConfig;
    private final OpenIdClientFactory openIdClientDetailsFactory;

    private static String lastConfigurationEndpoint;
    private static OpenIdConfigurationResponse openIdConfiguration;

    @Inject
    public ResolvedOpenIdConfig(final UriFactory uriFactory,
                                final OpenIdConfig openIdConfig,
                                final OpenIdClientFactory openIdClientDetailsFactory,
                                final Provider<CloseableHttpClient> httpClientProvider) {
        this.openIdConfig = openIdConfig;
        this.openIdClientDetailsFactory = openIdClientDetailsFactory;

        final String configurationEndpoint = openIdConfig.getOpenIdConfigurationEndpoint();

        if (openIdConfig.isUseInternal()) {
            openIdConfiguration = new OpenIdConfigurationResponse.Builder()
                    .issuer(INTERNAL_ISSUER)
                    .authorizationEndpoint(uriFactory.publicUri(INTERNAL_AUTH_ENDPOINT).toString())
                    .tokenEndpoint(uriFactory.nodeUri(INTERNAL_TOKEN_ENDPOINT).toString())
                    .jwksUri(uriFactory.nodeUri(INTERNAL_JWKS_URI).toString())
                    .build();

        } else if (configurationEndpoint == null || configurationEndpoint.isBlank()) {
            openIdConfiguration = new OpenIdConfigurationResponse.Builder()
                    .issuer(openIdConfig.getIssuer())
                    .authorizationEndpoint(openIdConfig.getAuthEndpoint())
                    .tokenEndpoint(openIdConfig.getTokenEndpoint())
                    .jwksUri(openIdConfig.getJwksUri())
                    .build();

        } else if (!Objects.equals(lastConfigurationEndpoint, configurationEndpoint)) {
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
                        openIdConfiguration = mapper.readValue(msg, OpenIdConfigurationResponse.class);
                    } else {
                        throw new AuthenticationException("Received status " + response.getStatusLine() + " from " + configurationEndpoint);
                    }
                }
            } catch (final IOException e) {
                LOGGER.debug(e.getMessage(), e);
            } finally {
                lastConfigurationEndpoint = configurationEndpoint;
            }
        }
    }

    public String getIssuer() {
        return openIdConfiguration.getIssuer();
    }

    public String getAuthEndpoint() {
        return openIdConfiguration.getAuthorizationEndpoint();
    }

    public String getTokenEndpoint() {
        return openIdConfiguration.getTokenEndpoint();
    }

    public String getJwksUri() {
        return openIdConfiguration.getJwksUri();
    }

    public String getClientId() {
        if (openIdConfig.isUseInternal()) {
            return openIdClientDetailsFactory.getClient().getClientId();
        }
        return openIdConfig.getClientId();
    }

    public String getClientSecret() {
        if (openIdConfig.isUseInternal()) {
            return openIdClientDetailsFactory.getClient().getClientSecret();
        }
        return openIdConfig.getClientSecret();
    }
}
