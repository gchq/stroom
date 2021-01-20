package stroom.security.identity.openid;

import stroom.config.common.UriFactory;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.identity.config.TokenConfig;
import stroom.security.identity.token.JwkCache;
import stroom.security.identity.token.JwkEventLog;
import stroom.security.openid.api.OpenIdConfigurationResponse;
import stroom.security.openid.api.TokenRequest;
import stroom.security.openid.api.TokenResponse;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import event.logging.OtherObject;
import event.logging.ViewEventAction;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.PublicJsonWebKey;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.RedirectionException;
import javax.ws.rs.core.Response.Status;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// TODO : @66 Add audit logging
class OpenIdResourceImpl implements OpenIdResource {
    private final OpenIdService service;
    private final JwkCache jwkCache;
    private final JwkEventLog jwkEventLog;
    private final UriFactory uriFactory;
    private final TokenConfig tokenConfig;
    private final StroomEventLoggingService stroomEventLoggingService;

    @Inject
    OpenIdResourceImpl(final OpenIdService service,
                       final JwkCache jwkCache,
                       final JwkEventLog jwkEventLog,
                       final UriFactory uriFactory,
                       final TokenConfig tokenConfig,
                       final StroomEventLoggingService stroomEventLoggingService) {
        this.service = service;
        this.jwkCache = jwkCache;
        this.jwkEventLog = jwkEventLog;
        this.uriFactory = uriFactory;
        this.tokenConfig = tokenConfig;
        this.stroomEventLoggingService = stroomEventLoggingService;
    }

    @Override
    public void auth(final HttpServletRequest request,
                     final String scope,
                     final String responseType,
                     final String clientId,
                     final String redirectUri,
                     @Nullable final String nonce,
                     @Nullable final String state,
                     @Nullable final String prompt) {
        final URI result = service.auth(
                request,
                scope,
                responseType,
                clientId,
                redirectUri,
                nonce,
                state,
                prompt);
        throw new RedirectionException(Status.SEE_OTHER, result);
    }

    @Override
    public TokenResponse token(final TokenRequest tokenRequest) {
        return service.token(tokenRequest);
    }

    @Override
    public Map<String, List<Map<String, Object>>> certs(final HttpServletRequest httpServletRequest) {

        return stroomEventLoggingService.loggedResult(
                "getCerts",
                "Read a token by the token ID.",
                ViewEventAction.builder()
                        .addObject(OtherObject.builder()
                                .withType("PublicKey")
                                .withName("Public Key")
                                .build())
                        .build(),
                () -> {
                    // Do the work
                    final List<PublicJsonWebKey> list = jwkCache.get();
                    final List<Map<String, Object>> maps = list.stream()
                            .map(jwk ->
                                    jwk.toParams(JsonWebKey.OutputControlLevel.PUBLIC_ONLY))
                            .collect(Collectors.toList());

                    Map<String, List<Map<String, Object>>> keys = new HashMap<>();
                    keys.put("keys", maps);

                    return keys;
                });
    }

    @Override
    public String openIdConfiguration() {
        try {
            final OpenIdConfigurationResponse response = OpenIdConfigurationResponse.builder()
                    .authorizationEndpoint(uriFactory.publicUri("/oauth2/v1/noauth/auth").toString())
                    .idTokenSigningSlgValuesSupported(new String[]{"RS256"})
                    .issuer(tokenConfig.getJwsIssuer())
                    .jwksUri(uriFactory.publicUri("/oauth2/v1/noauth/certs").toString())
                    .responseTypesSupported(new String[]{"code",
                            "token",
                            "id_token",
                            "code token",
                            "code id_token",
                            "token id_token",
                            "code token id_token",
                            "none"})
                    .scopesSupported(new String[]{"openid",
                            "email"})
                    .subjectTypesSupported(new String[]{"public"})
                    .tokenEndpoint(uriFactory.publicUri("/oauth2/v1/noauth/token").toString())
                    .build();
            final ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
            mapper.setSerializationInclusion(Include.NON_NULL);
            return mapper.writeValueAsString(response);
        } catch (final JsonProcessingException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
