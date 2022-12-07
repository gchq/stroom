package stroom.security.common.impl;

import stroom.security.api.exception.AuthenticationException;
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.openid.api.TokenRequest;
import stroom.security.openid.api.TokenRequest.Builder;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicNameValuePair;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

final class OpenIdPostBuilder {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(OpenIdPostBuilder.class);

    private final String endpointUri;
    private final OpenIdConfiguration openIdConfiguration;
    private final ObjectMapper objectMapper;

    private String clientId = null;
    private String clientSecret = null;
    private String code = null;
    private String grantType = null;
    private String redirectUri = null;
    private String refreshToken = null;
    private List<String> scopes = new ArrayList<>();

    OpenIdPostBuilder(final String endpointUri,
                      final OpenIdConfiguration openIdConfiguration,
                      final ObjectMapper objectMapper) {
        this.endpointUri = endpointUri;
        this.openIdConfiguration = openIdConfiguration;
        this.objectMapper = objectMapper;
    }

    public OpenIdPostBuilder withClientId(final String clientId) {
        this.clientId = clientId;
        return this;
    }

    public OpenIdPostBuilder withClientSecret(final String clientSecret) {
        this.clientSecret = clientSecret;
        return this;
    }

    public OpenIdPostBuilder withCode(final String code) {
        this.code = code;
        return this;
    }

    public OpenIdPostBuilder withGrantType(final String grantType) {
        this.grantType = grantType;
        return this;
    }

    public OpenIdPostBuilder withRedirectUri(final String redirectUri) {
        this.redirectUri = redirectUri;
        return this;
    }

    public OpenIdPostBuilder withRefreshToken(final String refreshToken) {
        this.refreshToken = refreshToken;
        return this;
    }

    public OpenIdPostBuilder withScopes(final List<String> scopes) {
        this.scopes = scopes;
        return this;
    }

    public OpenIdPostBuilder addScope(final String scope) {
        if (scopes == null) {
            scopes = new ArrayList<>();
        }
        this.scopes.add(scope);
        return this;
    }

    private void addBasicAuth(final HttpPost httpPost) {
        // Some OIDC providers expect authentication using a basic auth header
        // others expect the client(Id|Secret) to be in the form params and some cope
        // with both. Therefore, put them in both places to cover all bases.
        if (!NullSafe.isBlankString(clientId) && NullSafe.isBlankString(clientSecret)) {
            String authorization = openIdConfiguration.getClientId()
                    + ":"
                    + openIdConfiguration.getClientSecret();
            authorization = Base64.getEncoder().encodeToString(authorization.getBytes(StandardCharsets.UTF_8));
            authorization = "Basic " + authorization;
            httpPost.setHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
    }

    private void setFormParams(final HttpPost httpPost,
                               final List<NameValuePair> nvps) {
        try {
            httpPost.setHeader(HttpHeaders.ACCEPT, "*/*");
            httpPost.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED);
            httpPost.setEntity(new UrlEncodedFormEntity(nvps));
        } catch (final UnsupportedEncodingException e) {
            throw new AuthenticationException(e.getMessage(), e);
        }
    }

    private void buildFormPost(final HttpPost httpPost) {
        final List<NameValuePair> pairs = new ArrayList<>();

        final BiConsumer<String, String> addPair = (name, val) -> {
            if (!NullSafe.isBlankString(val)) {
                pairs.add(new BasicNameValuePair(name, val));
            }
        };

        final String scopesStr = String.join(" ", scopes);
        addPair.accept(OpenId.CLIENT_ID, clientId);
        addPair.accept(OpenId.CLIENT_SECRET, clientSecret);
        addPair.accept(OpenId.CODE, code);
        addPair.accept(OpenId.GRANT_TYPE, grantType);
        addPair.accept(OpenId.REDIRECT_URI, redirectUri);
        addPair.accept(OpenId.REFRESH_TOKEN, refreshToken);
        addPair.accept(OpenId.SCOPE, scopesStr);

        LOGGER.debug("Form name/value pairs: {}", pairs);

        setFormParams(httpPost, pairs);
    }

    private void buildJsonPost(final HttpPost httpPost) {
        try {
            final Builder builder = TokenRequest.builder();
            final BiConsumer<Consumer<String>, String> addValue = (func, val) -> {
                if (!NullSafe.isBlankString(val)) {
                    func.accept(val);
                }
            };
            final String scopesStr = String.join(" ", scopes);
            addValue.accept(builder::clientId, clientId);
            addValue.accept(builder::clientSecret, clientId);
            addValue.accept(builder::code, clientId);
            addValue.accept(builder::grantType, clientId);
            addValue.accept(builder::redirectUri, clientId);
            addValue.accept(builder::refreshToken, refreshToken);
            addValue.accept(builder::scope, scopesStr);

            final TokenRequest tokenRequest = builder.build();
            final String json = objectMapper.writeValueAsString(tokenRequest);

            LOGGER.debug("json: {}", json);

            httpPost.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
        } catch (final JsonProcessingException e) {
            throw new AuthenticationException(e.getMessage(), e);
        }
    }

    public HttpPost build() {
        final HttpPost httpPost = new HttpPost(endpointUri);
        if (openIdConfiguration.isFormTokenRequest()) {
            buildFormPost(httpPost);
        } else {
            buildJsonPost(httpPost);
        }
        addBasicAuth(httpPost);
        return httpPost;
    }
}
