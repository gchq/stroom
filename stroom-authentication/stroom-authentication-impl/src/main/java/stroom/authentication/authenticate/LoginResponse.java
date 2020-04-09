package stroom.authentication.authenticate;

public class LoginResponse {
    private boolean loginSuccessful;
    private String redirectUrl;
    private String message;
    private int responseCode;

    public LoginResponse() {

    }

    public LoginResponse(final boolean loginSuccessful, String message, String redirectUrl) {
        this.loginSuccessful = loginSuccessful;
        this.redirectUrl = redirectUrl;
        this.message = message;
    }

    public LoginResponse(final boolean loginSuccessful, String message, String redirectUrl, int responseCode) {
        this.loginSuccessful = loginSuccessful;
        this.redirectUrl = redirectUrl;
        this.message = message;
        this.responseCode = responseCode;
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

    public int getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(int responseCode) {
        this.responseCode = responseCode;
    }
}
