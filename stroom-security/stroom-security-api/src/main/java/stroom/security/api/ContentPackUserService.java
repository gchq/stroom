package stroom.security.api;

import stroom.util.shared.UserRef;

@Deprecated // We want to switch content pack import to be a UI directed thing which would allow the ownership of
// imported things to be better controlled.
public interface ContentPackUserService {
    /**
     * Gets the {@link UserIdentity} of a user or group identified by the subjectId.
     *
     * @throws stroom.security.api.exception.AuthenticationException if the user/group can't be found
     */
    UserRef getUserRef(String subjectId, boolean isGroup);
}
