package stroom.security.impl;

import stroom.security.api.SecurityContext;
import stroom.security.mock.MockSecurityContext;
import stroom.security.shared.FindUserNameCriteria;
import stroom.security.shared.UserNameProvider;
import stroom.security.user.api.UserNameService;
import stroom.util.shared.ResultPage;
import stroom.util.shared.SimpleUserName;
import stroom.util.shared.UserName;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestUserNameServiceImpl {

    @Test
    void find() {
        final SecurityContext securityContext = new MockSecurityContext();

        final UserName userName1a = new SimpleUserName("100", "jbloggs1", "Joe Bloggs");
        final UserName userName1b = new SimpleUserName("200", "jsmith1", "John Smith");

        final UserName userName2a = new SimpleUserName("100", "jbloggs2", "Joe Bloggs");
        final UserName userName2c = new SimpleUserName("300", "jdoe2", "John Doe");

        final Set<UserNameProvider> userNameProviders = Set.of(
                new MyUserNameProvider(1, List.of(
                        userName1a, userName1b)),
                new MyUserNameProvider(2, List.of(
                        userName2a, userName2c
                )));

        final UserNameService userNameService = new UserNameServiceImpl(
                userNameProviders,
                securityContext);

        final ResultPage<UserName> userNameResultPage = userNameService.find(null);
        final List<UserName> userNames = userNameResultPage.getValues();

        assertThat(userNames)
                .hasSize(3)
                .containsExactlyInAnyOrder(userName1a, userName1b, userName2c);

        // Make sure we only get one joe bloggs and it is the one from the higher priority provider
        final List<UserName> joeBloggsUsers = userNames.stream()
                .filter(userName -> userName.getSubjectId().equals("100"))
                .toList();
        assertThat(joeBloggsUsers)
                .hasSize(1);
        assertThat(joeBloggsUsers.get(0))
                .isSameAs(userName1a);
    }


    // --------------------------------------------------------------------------------


    private static class MyUserNameProvider implements UserNameProvider {

        private final int priority;
        private final List<UserName> userNames;

        private MyUserNameProvider(final int priority, final List<UserName> userNames) {
            this.priority = priority;
            this.userNames = userNames;
        }

        @Override
        public int getPriority() {
            return priority;
        }

        @Override
        public ResultPage<UserName> findUserNames(final FindUserNameCriteria criteria) {
            return ResultPage.createUnboundedList(userNames);
        }

        @Override
        public Optional<UserName> getBySubjectId(final String subjectId) {
            return Optional.empty();
        }

        @Override
        public Optional<UserName> getByDisplayName(final String displayName) {
            return Optional.empty();
        }

        @Override
        public Optional<UserName> getByUuid(final String userUuid) {
            return Optional.empty();
        }
    }
}
