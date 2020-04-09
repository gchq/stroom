package stroom.authentication.oauth2;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.RedirectionException;
import javax.ws.rs.core.Response.Status;
import java.net.URI;

// TODO : @66 Add audit logging
class OAuth2ResourceImpl implements OAuth2Resource {
    private final OAuth2Service service;

    @Inject
    OAuth2ResourceImpl(final OAuth2Service service) {
        this.service = service;
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
    public TokenResponse token(final HttpServletRequest request, final TokenRequest tokenRequest) {
        return service.token(request, tokenRequest);
    }
}
