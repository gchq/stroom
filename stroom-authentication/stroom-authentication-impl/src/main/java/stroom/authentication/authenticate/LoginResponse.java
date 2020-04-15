package stroom.authentication.authenticate;

public class LoginResponse {
    private boolean loginSuccessful;
    private String redirectUri;
    private String message;
    private int responseCode;

    public LoginResponse() {

    }

    public LoginResponse(final boolean loginSuccessful, String message, String redirectUri) {
        this.loginSuccessful = loginSuccessful;
        this.redirectUri = redirectUri;
        this.message = message;
    }

    public LoginResponse(final boolean loginSuccessful, String message, String redirectUri, int responseCode) {
        this.loginSuccessful = loginSuccessful;
        this.redirectUri = redirectUri;
        this.message = message;
        this.responseCode = responseCode;
    }

    public boolean isLoginSuccessful() {
        return loginSuccessful;
    }

    public void setLoginSuccessful(boolean loginSuccessful) {
        this.loginSuccessful = loginSuccessful;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(int responseCode) {
        this.responseCode = responseCode;
    }
}
