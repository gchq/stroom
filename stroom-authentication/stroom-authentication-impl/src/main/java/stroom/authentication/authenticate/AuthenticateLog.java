package stroom.authentication.authenticate;

public interface AuthenticateLog {
    void login(LoginResult loginResult, Throwable ex);

    void logout(Throwable ex);

    void resetEmail(String emailAddress, Throwable ex);

    void changePassword(Throwable ex);
}
