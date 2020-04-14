package stroom.authentication.oauth2;

import event.logging.ObjectOutcome;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.PublicJsonWebKey;
import stroom.authentication.token.JwkCache;
import stroom.authentication.token.JwkEventLog;

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
class OAuth2ResourceImpl implements OAuth2Resource {
    private final OAuth2Service service;
    private final JwkCache jwkCache;
    private final JwkEventLog jwkEventLog;

    @Inject
    OAuth2ResourceImpl(final OAuth2Service service,
                       final JwkCache jwkCache,
                       final JwkEventLog jwkEventLog) {
        this.service = service;
        this.jwkCache = jwkCache;
        this.jwkEventLog = jwkEventLog;
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
        final URI result = service.auth(request, scope, responseType, clientId, redirectUri, nonce, state, prompt);
        throw new RedirectionException(Status.SEE_OTHER, result);
    }

    @Override
    public TokenResponse token(final TokenRequest tokenRequest) {
        return service.token(tokenRequest);
    }

    @Override
    public Map<String, List<Map<String, Object>>> certs(final HttpServletRequest httpServletRequest) {
        final List<PublicJsonWebKey> list = jwkCache.get();
        final List<Map<String, Object>> maps = list.stream()
                .map(jwk -> jwk.toParams(JsonWebKey.OutputControlLevel.PUBLIC_ONLY))
                .collect(Collectors.toList());

        Map<String, List<Map<String, Object>>> keys = new HashMap<>();
        keys.put("keys", maps);

        event.logging.Object object = new event.logging.Object();
        object.setName("PublicKey");
        ObjectOutcome objectOutcome = new ObjectOutcome();
        objectOutcome.getObjects().add(object);
//        jwkEventLog.view(
//                "getCerts",
//                httpServletRequest,
//                "anonymous",
//                objectOutcome,
//                "Read a token by the token ID.");

        return keys;
    }
}
