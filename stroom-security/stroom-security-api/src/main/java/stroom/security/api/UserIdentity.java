package stroom.security.api;

import java.util.Comparator;

public interface UserIdentity {

    Comparator<UserIdentity> IDENTITY_COMPARATOR = Comparator.comparing(UserIdentity::getId);

    String getId();

}
