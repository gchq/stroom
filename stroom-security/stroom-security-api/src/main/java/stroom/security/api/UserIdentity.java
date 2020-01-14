package stroom.security.api;

import java.io.Serializable;

public interface UserIdentity extends Serializable {
    String getId();

    String getJws();

    String getSessionId();
}
