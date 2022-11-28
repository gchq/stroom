package stroom.security.impl;

import stroom.config.common.UriFactory;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.api.exception.AuthenticationException;
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.OpenIdClientFactory;
import stroom.security.openid.api.OpenIdConfig;
import stroom.security.openid.api.OpenIdConfigurationResponse;
import stroom.security.openid.api.OpenIdConfigurationResponse.Builder;
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

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletResponse;

@Singleton
public class ResolvedOpenIdConfig implements OpenIdConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResolvedOpenIdConfig.class);

    public static final String INTERNAL_ISSUER = "stroom";
    // These paths must tally up with those in stroom.authentication.oauth2.OAuth2Resource
    private static final String OAUTH2_BASE_PATH = "/oauth2/v1/noauth";
    private static final String AUTHENTICATION_BASE_PATH = "/authentication/v1/noauth";
    public static final String INTERNAL_AUTH_ENDPOINT = ResourcePaths.buildAuthenticatedApiPath(
            OAUTH2_BASE_PATH, "/auth");
    public static final String INTERNAL_TOKEN_ENDPOINT = ResourcePaths.buildAuthenticatedApiPath(
            OAUTH2_BASE_PATH, "/token");
    public static final String INTERNAL_JWKS_URI = ResourcePaths.buildAuthenticatedApiPath(
            OAUTH2_BASE_PATH, "/certs");
    public static final String INTERNAL_LOGOUT_ENDPOINT = ResourcePaths.buildAuthenticatedApiPath(
            AUTHENTICATION_BASE_PATH, "/logout");
    public static final String DEFAULT_REQUEST_SCOPE = "" +
            OpenId.SCOPE__OPENID +
            " " +
            OpenId.SCOPE__EMAIL;

    private final UriFactory uriFactory;
    private final Provider<OpenIdConfig> openIdConfigProvider;
    private final OpenIdClientFactory openIdClientDetailsFactory;
    private final Provider<CloseableHttpClient> httpClientProvider;

    private volatile String lastConfigurationEndpoint;
    private volatile OpenIdConfigurationResponse openIdConfigurationResp;

    @Inject
    public ResolvedOpenIdConfig(final UriFactory uriFactory,
                                final Provider<OpenIdConfig> openIdConfigProvider,
                                final OpenIdClientFactory openIdClientDetailsFactory,
                                final Provider<CloseableHttpClient> httpClientProvider) {
        this.uriFactory = uriFactory;
        this.openIdConfigProvider = openIdConfigProvider;
        this.openIdClientDetailsFactory = openIdClientDetailsFactory;
        this.httpClientProvider = httpClientProvider;
    }

    private OpenIdConfigurationResponse getOpenIdConfigurationResp() {
        final OpenIdConfig openIdConfig = openIdConfigProvider.get();
        final String configurationEndpoint = openIdConfig.getOpenIdConfigurationEndpoint();
        if (openIdConfigurationResp == null || !Objects.equals(lastConfigurationEndpoint, configurationEndpoint)) {
            if (openIdConfig.isUseInternal()) {
                openIdConfigurationResp = OpenIdConfigurationResponse.builder()
                        .issuer(INTERNAL_ISSUER)
                        .authorizationEndpoint(uriFactory.publicUri(INTERNAL_AUTH_ENDPOINT).toString())
                        .tokenEndpoint(uriFactory.nodeUri(INTERNAL_TOKEN_ENDPOINT).toString())
                        .jwksUri(uriFactory.nodeUri(INTERNAL_JWKS_URI).toString())
                        .logoutEndpoint(uriFactory.publicUri(INTERNAL_LOGOUT_ENDPOINT).toString())
                        .build();

            } else if (configurationEndpoint != null && !configurationEndpoint.isBlank()) {
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
                            openIdConfigurationResp = mapper.readValue(msg, OpenIdConfigurationResponse.class);

                            // Overwrite configuration with any values we might have manually configured.
                            Builder builder = openIdConfigurationResp.copy();
                            if (openIdConfig.getIssuer() != null &&
                                    !openIdConfig.getIssuer().isBlank()) {
                                builder = builder.issuer(openIdConfig.getIssuer());
                            }
                            if (openIdConfig.getAuthEndpoint() != null &&
                                    !openIdConfig.getAuthEndpoint().isBlank()) {
                                builder = builder.authorizationEndpoint(openIdConfig.getAuthEndpoint());
                            }
                            if (openIdConfig.getTokenEndpoint() != null &&
                                    !openIdConfig.getTokenEndpoint().isBlank()) {
                                builder = builder.tokenEndpoint(openIdConfig.getTokenEndpoint());
                            }
                            if (openIdConfig.getJwksUri() != null &&
                                    !openIdConfig.getJwksUri().isBlank()) {
                                builder = builder.jwksUri(openIdConfig.getJwksUri());
                            }
                            if (openIdConfig.getLogoutEndpoint() != null &&
                                    !openIdConfig.getLogoutEndpoint().isBlank()) {
                                builder = builder.logoutEndpoint(openIdConfig.getLogoutEndpoint());
                            } else if (openIdConfigurationResp.getLogoutEndpoint() == null ||
                                    openIdConfigurationResp.getLogoutEndpoint().isBlank()) {
                                // If the IdP doesn't provide a logout endpoint then use the internal one to invalidate
                                // the session and redirect to perform a a new auth flow.
                                builder = builder.logoutEndpoint(
                                        uriFactory.publicUri(INTERNAL_LOGOUT_ENDPOINT).toString());
                            }
                            openIdConfigurationResp = builder.build();

                        } else {
                            throw new AuthenticationException("Received status " + response.getStatusLine() +
                                    " from " + configurationEndpoint);
                        }
                    }
                } catch (final RuntimeException | IOException e) {
                    LOGGER.error(e.getMessage(), e);
                }
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

    @Override
    public String getOpenIdConfigurationEndpoint() {
        return openIdConfigProvider.get().getOpenIdConfigurationEndpoint();
    }

    public String getIssuer() {
        return getOpenIdConfigurationResp().getIssuer();
    }

    public String getAuthEndpoint() {
        return getOpenIdConfigurationResp().getAuthorizationEndpoint();
    }

    public String getTokenEndpoint() {
        return getOpenIdConfigurationResp().getTokenEndpoint();
    }

    public String getJwksUri() {
        return getOpenIdConfigurationResp().getJwksUri();
    }

    public String getLogoutEndpoint() {
        return getOpenIdConfigurationResp().getLogoutEndpoint();
    }

    @Override
    public String getLogoutRedirectParamName() {
        return openIdConfigProvider.get().getLogoutRedirectParamName();
    }

    public String getClientId() {
        final OpenIdConfig openIdConfig = openIdConfigProvider.get();
        if (openIdConfig.isUseInternal()) {
            return openIdClientDetailsFactory.getClient().getClientId();
        }
        return openIdConfig.getClientId();
    }

    public String getClientSecret() {
        final OpenIdConfig openIdConfig = openIdConfigProvider.get();
        if (openIdConfig.isUseInternal()) {
            return openIdClientDetailsFactory.getClient().getClientSecret();
        }
        return openIdConfig.getClientSecret();
    }

    public boolean isFormTokenRequest() {
        final OpenIdConfig openIdConfig = openIdConfigProvider.get();
        if (openIdConfig.isUseInternal()) {
            return true;
        }
        return openIdConfig.isFormTokenRequest();
    }

    public String getRequestScope() {
        final OpenIdConfig openIdConfig = openIdConfigProvider.get();
        if (openIdConfig.isUseInternal() ||
                openIdConfig.getRequestScope() == null ||
                openIdConfig.getRequestScope().isBlank()) {
            return DEFAULT_REQUEST_SCOPE;
        }
        return openIdConfig.getRequestScope();
    }

    @Override
    public boolean isValidateAudience() {
        return false;
    }
}
