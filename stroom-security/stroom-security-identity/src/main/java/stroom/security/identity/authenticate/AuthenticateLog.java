package stroom.security.identity.authenticate;

public interface AuthenticateLog {
    void login(CredentialValidationResult result, Throwable ex);

    void logout(Throwable ex);

    void resetEmail(String emailAddress, Throwable ex);

    void changePassword(Throwable ex);
}
