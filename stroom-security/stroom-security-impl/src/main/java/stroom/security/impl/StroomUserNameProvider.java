package stroom.security.impl;

import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.FindUserNameCriteria;
import stroom.security.shared.User;
import stroom.security.shared.UserNameProvider;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserName;

import java.util.List;
import java.util.Optional;
import jakarta.inject.Inject;

class StroomUserNameProvider implements UserNameProvider {

    private final UserCache userCache;
    private final UserService userService;

    @Inject
    StroomUserNameProvider(final UserCache userCache,
                           final UserService userService) {
        this.userCache = userCache;
        this.userService = userService;
    }

    @Override
    public int getPriority() {
        // Stroom users trump account users.
        return 1;
    }

    @Override
    public ResultPage<UserName> findUserNames(final FindUserNameCriteria criteria) {
        final FindUserCriteria findUserCriteria = new FindUserCriteria(
                criteria.getPageRequest(),
                criteria.getSortList(),
                criteria.getQuickFilterInput(),
                false,
                null);
        final List<UserName> userNames = userService.find(findUserCriteria)
                .stream()
                .map(usr -> (UserName) usr)
                .toList();

        return new ResultPage<>(userNames);
    }

    @Override
    public ResultPage<UserName> findAssociates(final FindUserNameCriteria criteria) {
        // Delegate perm checks to userService
        final List<UserName> associates = userService.getAssociates(criteria.getQuickFilterInput());
        return new ResultPage<>(associates);
    }

    @Override
    public Optional<UserName> getBySubjectId(final String subjectId) {
        return userCache.get(subjectId)
                .map(User::asUserName);
    }

    @Override
    public Optional<UserName> getByDisplayName(final String displayName) {
        return userCache.getByDisplayName(displayName)
                .map(User::asUserName);
    }

    @Override
    public Optional<UserName> getByUuid(final String userUuid) {
        return userCache.getByUuid(userUuid)
                .map(User::asUserName);
    }
}
