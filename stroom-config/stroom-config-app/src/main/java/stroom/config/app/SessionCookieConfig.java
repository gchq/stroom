package stroom.config.app;

import stroom.util.shared.IsConfig;

import javax.inject.Singleton;

@Singleton
public class SessionCookieConfig extends IsConfig {
    private boolean secure = true;
    private boolean httpOnly = true;

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(final boolean secure) {
        this.secure = secure;
    }

    public boolean isHttpOnly() {
        return httpOnly;
    }

    public void setHttpOnly(final boolean httpOnly) {
        this.httpOnly = httpOnly;
    }
}
