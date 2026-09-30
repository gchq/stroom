/*
 * Copyright 2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.security.identity.account;

import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentityFactory;
import stroom.security.identity.config.IdentityConfig;
import stroom.security.identity.shared.Account;
import stroom.security.shared.AppPermission;
import stroom.util.shared.UserRef;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestAccountServiceImpl {

    private static final String CALLER = "jbloggs";
    private static final String SOMEONE_ELSE = "asmith";

    @Mock
    private AccountDao accountDao;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private UserIdentityFactory userIdentityFactory;

    private AccountServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(securityContext.getUserRef()).thenReturn(UserRef.builder()
                .uuid("uuid")
                .subjectId(CALLER)
                .build());
        service = new AccountServiceImpl(
                accountDao, securityContext, new IdentityConfig(), userIdentityFactory);
    }

    @Test
    void anAccountThatIsNotYoursIsIndistinguishableFromOneThatDoesNotExist() {
        // Account ids are sequential integers and this is reachable by any signed-in user, so a refusal
        // that differs from "no such account" lets them walk the ids and learn which are live. Both cases
        // have to answer the same way.
        givenNotAnAdministrator();
        when(accountDao.get(1)).thenReturn(Optional.of(accountOf(SOMEONE_ELSE)));
        when(accountDao.get(2)).thenReturn(Optional.empty());

        final Optional<Account> somebodyElses = service.read(1);
        final Optional<Account> notThere = service.read(2);

        assertThat(somebodyElses)
                .as("an account belonging to someone else must not be distinguishable")
                .isEqualTo(notThere)
                .isEmpty();
    }

    @Test
    void yourOwnAccountIsStillReturned() {
        givenNotAnAdministrator();
        when(accountDao.get(1)).thenReturn(Optional.of(accountOf(CALLER)));

        assertThat(service.read(1)).map(Account::getUserId).contains(CALLER);
    }

    @Test
    void anAdministratorStillSeesAnyAccount() {
        when(securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)).thenReturn(true);
        when(accountDao.get(1)).thenReturn(Optional.of(accountOf(SOMEONE_ELSE)));

        assertThat(service.read(1)).map(Account::getUserId).contains(SOMEONE_ELSE);
    }

    private void givenNotAnAdministrator() {
        when(securityContext.hasAppPermission(any())).thenReturn(false);
    }

    private static Account accountOf(final String userId) {
        final Account account = new Account();
        account.setUserId(userId);
        return account;
    }
}
