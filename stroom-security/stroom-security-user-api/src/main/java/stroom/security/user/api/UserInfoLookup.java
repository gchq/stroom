package stroom.security.user.api;

import stroom.util.shared.UserInfo;

import java.util.Optional;

public interface UserInfoLookup {

    /**
     * Look up the name info for a user that may or may not have been
     * deleted.
     * Should only be used for rendering an existing known userUuid in a
     * more human friendly way and NOT for selecting a user.
     * This user may be enabled/disabled and deleted/active.
     */
    Optional<UserInfo> getByUuid(String userUuid);
}
