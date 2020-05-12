package stroom.config.app;

import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.inject.Singleton;

@Singleton
@JsonPropertyOrder(alphabetic = true)
public class SessionCookieConfig extends AbstractConfig {

    @JsonProperty
    @JsonPropertyDescription("Marks the session cookies with the secure flag, indicating they " +
            "should only be transmitted over a secure connection.")
    private boolean secure = true;

    @JsonProperty
    @JsonPropertyDescription("Marks the session cookies as 'HttpOnly' so that we are inaccessible " +
            "to client-side javascript code.")
    private boolean httpOnly = true;

    public boolean isSecure() {
        return secure;
    }

    @SuppressWarnings("unused")
    public void setSecure(final boolean secure) {
        this.secure = secure;
    }

    public boolean isHttpOnly() {
        return httpOnly;
    }

    @SuppressWarnings("unused")
    public void setHttpOnly(final boolean httpOnly) {
        this.httpOnly = httpOnly;
    }
}
