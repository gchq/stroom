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

    ResultPage<UserName> findUserNames(final FindUserNameCriteria criteria);

    Optional<UserName> getByUserId(final String userId);

    Optional<UserName> getByDisplayName(final String displayName);
}
