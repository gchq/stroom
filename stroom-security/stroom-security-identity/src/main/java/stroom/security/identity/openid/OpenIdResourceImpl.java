package stroom.security.identity.openid;

import stroom.config.common.UriFactory;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.security.identity.authenticate.api.AuthenticationService.AuthStatus;
import stroom.security.identity.config.TokenConfig;
import stroom.security.identity.exceptions.BadRequestException;
import stroom.security.identity.openid.OpenIdService.AuthResult;
import stroom.security.openid.api.OpenIdConfigurationResponse;
import stroom.security.openid.api.PublicJsonWebKeyProvider;
import stroom.security.openid.api.TokenResponse;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import event.logging.AuthenticateAction;
import event.logging.AuthenticateEventAction;
import event.logging.AuthenticateLogonType;
import event.logging.AuthenticateOutcome;
import event.logging.AuthenticateOutcomeReason;
import event.logging.Data;
import event.logging.OtherObject;
import event.logging.ViewEventAction;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.PublicJsonWebKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.RedirectionException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response.Status;

@AutoLogged
class OpenIdResourceImpl implements OpenIdResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenIdResourceImpl.class);

    private final Provider<OpenIdService> openIdServiceProvider;
    private final Provider<PublicJsonWebKeyProvider> publicJsonWebKeyProviderProvider;
    private final Provider<UriFactory> uriFactoryProvider;
    private final Provider<TokenConfig> tokenConfigProvider;
    private final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider;

    @Inject
    OpenIdResourceImpl(final Provider<OpenIdService> openIdServiceProvider,
                       final Provider<PublicJsonWebKeyProvider> publicJsonWebKeyProviderProvider,
                       final Provider<UriFactory> uriFactoryProvider,
                       final Provider<TokenConfig> tokenConfigProvider,
                       final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider) {
        this.openIdServiceProvider = openIdServiceProvider;
        this.publicJsonWebKeyProviderProvider = publicJsonWebKeyProviderProvider;
        this.uriFactoryProvider = uriFactoryProvider;
        this.tokenConfigProvider = tokenConfigProvider;
        this.stroomEventLoggingServiceProvider = stroomEventLoggingServiceProvider;
    }

    @Timed
    @Override
    @AutoLogged(OperationType.MANUALLY_LOGGED)
    public void auth(final HttpServletRequest request,
                     final String scope,
                     final String responseType,
                     final String clientId,
                     final String redirectUri,
                     @Nullable final String nonce,
                     @Nullable final String state,
                     @Nullable final String prompt) {

        final AuthResult result = openIdServiceProvider.get().auth(
                request,
                scope,
                responseType,
                clientId,
                redirectUri,
                nonce,
                state,
                prompt);

        if (result.getStatus().isPresent() && result.getStatus().get().isNew()) {
            AuthStatus status = result.getStatus().get();

            final AuthenticateEventAction.Builder<Void> eventBuilder = event.logging.AuthenticateEventAction.builder()
                    .withAction(AuthenticateAction.LOGON)
                    .withLogonType(AuthenticateLogonType.INTERACTIVE);


            if (status.getError().isPresent()) {
                final AuthenticateOutcomeReason reason = status.getError().get().getReason();
                final String message = status.getError().get().getMessage();
                eventBuilder.withOutcome(AuthenticateOutcome.builder()
                        .withSuccess(false)
                        .withPermitted(false)
                        .withReason(reason)
                        .withDescription(message)
                        .withData(Data.builder()
                                .withName("Error")
                                .withValue(message)
                                .build())
                        .build());
                eventBuilder.withAuthenticationEntity(event.logging.User.builder()
                        .withId(status.getError().get().getSubject()).build());
            } else {
                eventBuilder.withAuthenticationEntity(event.logging.User.builder()
                        .withId(status.getAuthState().get().getSubject()).build());
            }

            stroomEventLoggingServiceProvider.get().log(
                    "OpenIdResourceImpl.auth",
                    "Stroom user login",
                    eventBuilder.build());
        }

        throw new RedirectionException(Status.TEMPORARY_REDIRECT, result.getUri());
    }

    @Timed
    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public TokenResponse token(final MultivaluedMap<String, String> formParams) {
        try {
            LOGGER.debug("token() " + formParams);
            return openIdServiceProvider.get().token(formParams);
        } catch (final BadRequestException e) {
            LOGGER.debug(e.getMessage(), e);

            //Normally unlogged, but always log token failures
            final AuthenticateOutcomeReason reason = e.getReason();
            final String message = e.getMessage();
            final AuthenticateEventAction.Builder<Void> eventBuilder = event.logging.AuthenticateEventAction.builder()
                    .withAction(AuthenticateAction.LOGON)
                    .withLogonType(AuthenticateLogonType.INTERACTIVE)
                    .withAuthenticationEntity(event.logging.User.builder()
                            .withId(e.getSubject()).build())
                    .withOutcome(AuthenticateOutcome.builder()
                            .withSuccess(false)
                            .withPermitted(false)
                            .withReason(reason)
                            .withDescription(message)
                            .withData(Data.builder()
                                    .withName("Error")
                                    .withValue(message)
                                    .build())
                            .build());

            stroomEventLoggingServiceProvider.get().log(
                    "OpenIdResourceImpl.token",
                    "Stroom token authentication",
                    eventBuilder.build());

            throw new WebApplicationException(e.getMessage(), e);
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            throw e;
        }
    }

    @Timed
    @Override
    @AutoLogged(OperationType.MANUALLY_LOGGED)
    public Map<String, List<Map<String, Object>>> certs(final HttpServletRequest httpServletRequest) {

        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId("getCerts")
                .withDescription("Read a token by the token ID.")
                .withDefaultEventAction(ViewEventAction.builder()
                        .addObject(OtherObject.builder()
                                .withType("PublicKey")
                                .withName("Public Key")
                                .build())
                        .build())
                .withSimpleLoggedResult(() -> {
                    // Do the work
                    final List<PublicJsonWebKey> list = publicJsonWebKeyProviderProvider.get().list();
                    final List<Map<String, Object>> maps = list.stream()
                            .map(jwk ->
                                    jwk.toParams(JsonWebKey.OutputControlLevel.PUBLIC_ONLY))
                            .collect(Collectors.toList());

                    final Map<String, List<Map<String, Object>>> keys = new HashMap<>();
                    keys.put("keys", maps);

                    return keys;
                }).getResultAndLog();
    }

    @Timed
    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public String openIdConfiguration() {
        try {
            final OpenIdConfigurationResponse response = OpenIdConfigurationResponse.builder()
                    .authorizationEndpoint(uriFactoryProvider.get().publicUri("/oauth2/v1/noauth/auth").toString())
                    .idTokenSigningSlgValuesSupported(new String[]{"RS256"})
                    .issuer(tokenConfigProvider.get().getJwsIssuer())
                    .jwksUri(uriFactoryProvider.get().publicUri("/oauth2/v1/noauth/certs").toString())
                    .responseTypesSupported(new String[]{
                            "code",
                            "token",
                            "id_token",
                            "code token",
                            "code id_token",
                            "token id_token",
                            "code token id_token",
                            "none"})
                    .scopesSupported(new String[]{
                            "openid",
                            "email"})
                    .subjectTypesSupported(new String[]{"public"})
                    .tokenEndpoint(uriFactoryProvider.get().publicUri("/oauth2/v1/noauth/token").toString())
                    .build();
            final ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
            mapper.setSerializationInclusion(Include.NON_NULL);
            return mapper.writeValueAsString(response);
        } catch (final JsonProcessingException e) {
            throw new WebApplicationException(e.getMessage(), e);
        }
    }
}
