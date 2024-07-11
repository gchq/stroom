package stroom.security.identity.authenticate.api;

import stroom.security.identity.exceptions.BadRequestException;
import stroom.util.shared.ResourcePaths;

import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;
import java.util.Optional;

public interface AuthenticationService {
    String SIGN_IN_URL_PATH = ResourcePaths.SIGN_IN_PATH;

    AuthStatus currentAuthState(HttpServletRequest request);

    URI createSignInUri(String redirectUri);

    interface AuthStatus {
        Optional<AuthState> getAuthState();

        Optional<BadRequestException> getError();

        boolean isNew();
    }

    interface AuthState {

        String getSubject();

        boolean isRequirePasswordChange();

        long getLastCredentialCheckMs();
    }
}
