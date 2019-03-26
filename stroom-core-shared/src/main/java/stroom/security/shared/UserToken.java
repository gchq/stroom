package stroom.security.shared;

import java.io.Serializable;
import java.util.Objects;

public class UserToken implements Serializable {
    private static final String DELIMITER = "|";

    private String type;
    private String userId;
    private String sessionId;

    public UserToken() {
        // Default constructor necessary for GWT serialisation.
    }

    public UserToken(final String type, final String userId, final String sessionId) {
        this.type = type;
        this.userId = userId;
        this.sessionId = sessionId;
    }

    public String getType() {
        return type;
    }

    public String getUserId() {
        return userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final UserToken userToken = (UserToken) o;
        return Objects.equals(type, userToken.type) &&
                Objects.equals(userId, userToken.userId) &&
                Objects.equals(sessionId, userToken.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, userId, sessionId);
    }

    @Override
    public String toString() {
        return type + DELIMITER + userId + DELIMITER + sessionId;
    }
}