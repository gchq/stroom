package stroom.authentication.resources.authentication.v1;

import javax.ws.rs.core.Response;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.Optional;

public interface AuthenticationService {
    Response.ResponseBuilder handleAuthenticationRequest(
            String sessionId,
            String nonce,
            String state,
            String redirectUrl,
            String clientId,
            String prompt,
            Optional<String> optionalCn);

    LoginResponse handleLogin(Credentials credentials, String sessionId) throws UnsupportedEncodingException;

    String logout(String sessionId, String redirectUrl);

    boolean resetEmail(String emailAddress);

    ChangePasswordResponse changePassword(ChangePasswordRequest changePasswordRequest);

    ChangePasswordResponse resetPassword(ResetPasswordRequest request);

    boolean needsPasswordChange(String email);

    PasswordValidationResponse isPasswordValid(PasswordValidationRequest passwordValidationRequest);

    URI postAuthenticationRedirect(String sessionId, String clientId);
}
