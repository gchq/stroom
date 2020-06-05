package stroom.authentication.authenticate.api;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Optional;

public interface AuthenticationService {

    String UNAUTHORISED_URL_PATH = "/s/unauthorised";
    String LOGIN_URL_PATH = "/s/login";
    String CHANGE_PASSWORD_URL_PATH = "/s/changepassword";

    Optional<AuthState> currentAuthState(HttpServletRequest request);

    URI createLoginUri(String redirectUri);

    URI createChangePasswordUri(String redirectUri);

    interface AuthState {
        String getSubject();

        boolean isRequirePasswordChange();
    }
}
