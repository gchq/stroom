package stroom.security.user.api;

import stroom.security.shared.FindUserNameCriteria;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserName;

import java.util.Optional;

public interface UserNameService {

    ResultPage<UserName> find(FindUserNameCriteria criteria);

    Optional<UserName> getUserName(final String userId);
}
