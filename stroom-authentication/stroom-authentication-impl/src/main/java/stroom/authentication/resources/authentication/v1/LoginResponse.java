package stroom.authentication.resources.authentication.v1;

public class LoginResponse {
    private boolean loginSuccessful;
    private String redirectUrl;
    private String message;

    public LoginResponse() {

    }

    public LoginResponse(final boolean loginSuccessful, String message, String redirectUrl) {
        this.loginSuccessful = loginSuccessful;
        this.redirectUrl = redirectUrl;
        this.message = message;
    }

    public boolean isLoginSuccessful() {
        return loginSuccessful;
    }

    public void setLoginSuccessful(boolean loginSuccessful) {
        this.loginSuccessful = loginSuccessful;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public void setRedirectUrl(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
