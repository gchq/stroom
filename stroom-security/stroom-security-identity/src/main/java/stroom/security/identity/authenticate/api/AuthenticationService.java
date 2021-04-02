package stroom.security.identity.authenticate.api;

import stroom.security.identity.exceptions.BadRequestException;

import java.net.URI;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;

public interface AuthenticationService {
    String SIGN_IN_URL_PATH = "/s/signIn";
    String CONFIRM_PASSWORD_URL_PATH = "/s/confirmpassword";
    String CHANGE_PASSWORD_URL_PATH = "/s/changepassword";
    String API_KEYS_URL_PATH = "/s/apiKeys";
    String USERS_URL_PATH = "/s/users";

    AuthStatus currentAuthState(HttpServletRequest request);

    URI createSignInUri(String redirectUri);

    URI createConfirmPasswordUri(String redirectUri);

    URI createChangePasswordUri(String redirectUri);

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
