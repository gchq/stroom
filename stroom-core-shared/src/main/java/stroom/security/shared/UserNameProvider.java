package stroom.security.shared;

import stroom.util.shared.ResultPage;
import stroom.util.shared.UserName;

import java.util.Optional;

public interface UserNameProvider {

    ResultPage<UserName> findUserNames(final FindUserNameCriteria criteria);

    Optional<UserName> getUserName(final String userId);
}
