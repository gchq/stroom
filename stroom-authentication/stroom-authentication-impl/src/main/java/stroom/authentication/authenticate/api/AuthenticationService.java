package stroom.authentication.authenticate.api;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Optional;

public interface AuthenticationService {
    Optional<AuthState> currentAuthState(HttpServletRequest request);

    URI createLoginUri(String redirectUri);

    URI createChangePasswordUri(String redirectUri);

    interface AuthState {
        String getSubject();

        boolean isRequirePasswordChange();
    }
}
