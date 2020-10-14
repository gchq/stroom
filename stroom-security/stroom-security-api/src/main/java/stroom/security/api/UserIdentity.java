package stroom.security.api;

import java.io.Serializable;
import java.util.Comparator;

public interface UserIdentity extends Serializable {

    static final Comparator<UserIdentity> IDENTITY_COMPARATOR = Comparator.comparing(UserIdentity::getId);

    String getId();

    String getJws();

    String getSessionId();
}
