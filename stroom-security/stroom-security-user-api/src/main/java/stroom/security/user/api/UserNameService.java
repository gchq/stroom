package stroom.security.user.api;

import stroom.security.shared.FindUserNameCriteria;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserName;

import java.util.Optional;

public interface UserNameService {

    ResultPage<UserName> find(FindUserNameCriteria criteria);

    /**
     * <p>This will look up a user using their unique identifier.</p>
     */
    Optional<UserName> getBySubjectId(final String subjectId);

    /**
     * <p>This will look up a user using the display name as seen in the UI. If a user cannot be found
     * it will look up the user using their unique identifier.</p>
     */
    Optional<UserName> getByDisplayName(final String displayName);

    /**
     * Look up a user by their Stroom user uuid.
     */
    Optional<UserName> getByUuid(final String userUuid);
}
