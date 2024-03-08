package stroom.security.shared;

import stroom.util.shared.ResultPage;
import stroom.util.shared.UserName;

import java.util.Optional;

public interface UserNameProvider {

    /**
     * @return True if this provider is enabled for providing names. If false the other
     * methods should not be called.
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * @return Non-zero positive value to indicate priority of the provider. Lower number means
     * higher priority.
     */
    int getPriority();

    ResultPage<UserName> findUserNames(final FindUserNameCriteria criteria);

    /**
     * Finds all users belonging to at least one of the current user's groups.
     * If the current user is an admin or has {@link PermissionNames#MANAGE_USERS_PERMISSION}
     * then they will see all users.
     */
    ResultPage<UserName> findAssociates(final FindUserNameCriteria criteria);

    Optional<UserName> getBySubjectId(final String subjectId);

    Optional<UserName> getByDisplayName(final String displayName);

    Optional<UserName> getByUuid(final String userUuid);
}
