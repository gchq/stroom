package stroom.security.shared;

import stroom.util.shared.ResultPage;

public interface UserNameProvider {

    ResultPage<String> findUserNames(FindUserNameCriteria criteria);
}
