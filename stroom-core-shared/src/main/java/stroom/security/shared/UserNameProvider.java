package stroom.security.shared;

import stroom.util.shared.ResultPage;
import stroom.util.shared.UserName;

public interface UserNameProvider {

    ResultPage<UserName> findUserNames(final FindUserNameCriteria criteria);
}
