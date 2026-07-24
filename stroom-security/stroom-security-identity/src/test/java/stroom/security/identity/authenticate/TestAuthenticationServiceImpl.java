/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.security.identity.authenticate;

import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.identity.account.AccountDao;
import stroom.security.identity.account.AccountService;
import stroom.security.identity.config.IdentityConfig;
import stroom.security.identity.config.PasswordPolicyConfig;
import stroom.security.identity.shared.Account;
import stroom.security.identity.shared.LoginRequest;
import stroom.security.identity.shared.LoginResponse;
import stroom.task.api.ExecutorProvider;

import event.logging.MultiObject;
import event.logging.UpdateEventAction;
import jakarta.inject.Provider;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestAuthenticationServiceImpl {

    private static final String USER_ID = "jbloggs";
    private static final String PASSWORD = "letmein";

    @Mock
    private AccountDao accountDao;
    @Mock
    private AccountService accountService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private Provider<StroomEventLoggingService> stroomEventLoggingService;
    @Mock
    private StroomEventLoggingService eventLoggingService;
    @Mock
    private ExecutorProvider executorProvider;

    @BeforeEach
    void setUp() {
        // Only the tests that get as far as a successful sign in use these.
        final Account account = new Account();
        account.setUserId(USER_ID);
        lenient().when(accountService.read(USER_ID)).thenReturn(Optional.of(account));
        lenient().when(accountDao.needsPasswordChange(anyString(), any(), anyBoolean()))
                .thenReturn(false);
        lenient().when(stroomEventLoggingService.get()).thenReturn(eventLoggingService);
        // The service resolves its email executor when constructed.
        lenient().when(executorProvider.get(any())).thenReturn(Runnable::run);
    }

    @Test
    void inactiveAccountIsReactivatedOnSuccessfulLoginWhenEnabled() {
        givenCredentialsResult(validCredentialsButInactive());

        final LoginResponse response = login(configWithReactivation(true));

        verify(accountDao).reactivateAccount(USER_ID);
        assertThat(response.isLoginSuccessful()).isTrue();
    }

    @Test
    void reactivationHappensBeforeTheSuccessfulLoginIsRecorded() {
        // recordSuccessfulLogin sets LAST_LOGIN_MS, which is what stops the Account Maintenance job
        // deactivating the account again, so it has to follow the reactivation.
        givenCredentialsResult(validCredentialsButInactive());

        login(configWithReactivation(true));

        final InOrder inOrder = inOrder(accountDao);
        inOrder.verify(accountDao).reactivateAccount(USER_ID);
        inOrder.verify(accountDao).recordSuccessfulLogin(USER_ID);
    }

    @Test
    void reactivationIsEventLogged() {
        givenCredentialsResult(validCredentialsButInactive());

        login(configWithReactivation(true));

        final ArgumentCaptor<UpdateEventAction> captor = ArgumentCaptor.forClass(UpdateEventAction.class);
        verify(eventLoggingService).log(anyString(), anyString(), captor.capture());

        final UpdateEventAction action = captor.getValue();
        assertThat(userState(action.getBefore())).isEqualTo("Enabled/Inactive/Unlocked");
        assertThat(userState(action.getAfter())).isEqualTo("Enabled/Active/Unlocked");
    }

    @Test
    void nothingIsEventLoggedWhenNoReactivationHappens() {
        givenCredentialsResult(new CredentialValidationResult(
                true, false, false, false, false, false));

        login(configWithReactivation(true));

        verify(eventLoggingService, never()).log(anyString(), anyString(), any());
    }

    @Test
    void processingAccountIsNotReactivated() {
        givenCredentialsResult(new CredentialValidationResult(
                true, false, false, false, true, true));

        final LoginResponse response = login(configWithReactivation(true));

        verify(accountDao, never()).reactivateAccount(anyString());
        assertThat(response.isLoginSuccessful()).isFalse();
    }

    @Test
    void nonExistentAccountIsNotReactivated() {
        // validateCredentials never reports a non existent account as inactive, but the guard must
        // not depend on that.
        givenCredentialsResult(new CredentialValidationResult(
                false, true, false, false, true, false));

        final LoginResponse response = login(configWithReactivation(true));

        verify(accountDao, never()).reactivateAccount(anyString());
        verify(accountDao, never()).incrementLoginFailures(anyString());
        assertThat(response.isLoginSuccessful()).isFalse();
    }

    @Test
    void inactiveAccountIsNotReactivatedWhenDisabledByConfig() {
        givenCredentialsResult(validCredentialsButInactive());

        final LoginResponse response = login(configWithReactivation(false));

        verify(accountDao, never()).reactivateAccount(anyString());
        assertThat(response.isLoginSuccessful()).isFalse();
        assertThat(response.getMessage()).contains("inactive");
    }

    @Test
    void inactiveAccountIsNotReactivatedWithWrongPassword() {
        // An incorrect password must never reactivate an account, even though it is inactive.
        givenCredentialsResult(new CredentialValidationResult(
                false, false, false, false, true, false));
        when(accountDao.incrementLoginFailures(USER_ID)).thenReturn(false);

        final LoginResponse response = login(configWithReactivation(true));

        verify(accountDao, never()).reactivateAccount(anyString());
        assertThat(response.isLoginSuccessful()).isFalse();
    }

    @Test
    void lockedAndInactiveAccountIsNotReactivated() {
        // Being inactive must be the ONLY thing blocking sign in, otherwise reactivating would
        // silently work around the lock.
        givenCredentialsResult(new CredentialValidationResult(
                true, false, true, false, true, false));

        final LoginResponse response = login(configWithReactivation(true));

        verify(accountDao, never()).reactivateAccount(anyString());
        assertThat(response.isLoginSuccessful()).isFalse();
        assertThat(response.getMessage()).contains("locked");
    }

    @Test
    void lockedAccountDoesNotCountAsAFurtherFailure() {
        // A wrong password against an already locked account must not bump the failure count, so continued
        // guessing cannot extend a temporary lock (and it saves a pointless write). The lock is left to
        // expire on its own.
        givenCredentialsResult(new CredentialValidationResult(
                false, false, true, false, false, false));

        final LoginResponse response = login(configWithReactivation(true));

        verify(accountDao, never()).incrementLoginFailures(anyString());
        assertThat(response.isLoginSuccessful()).isFalse();
    }

    @Test
    void wrongPasswordOnAnUnlockedAccountCountsAsAFailure() {
        givenCredentialsResult(new CredentialValidationResult(
                false, false, false, false, false, false));
        when(accountDao.incrementLoginFailures(USER_ID)).thenReturn(false);

        final LoginResponse response = login(configWithReactivation(true));

        verify(accountDao).incrementLoginFailures(USER_ID);
        assertThat(response.isLoginSuccessful()).isFalse();
    }

    @Test
    void disabledAndInactiveAccountIsNotReactivated() {
        givenCredentialsResult(new CredentialValidationResult(
                true, false, false, true, true, false));

        final LoginResponse response = login(configWithReactivation(true));

        verify(accountDao, never()).reactivateAccount(anyString());
        assertThat(response.isLoginSuccessful()).isFalse();
    }

    @Test
    void activeAccountIsNotReactivated() {
        givenCredentialsResult(new CredentialValidationResult(
                true, false, false, false, false, false));

        final LoginResponse response = login(configWithReactivation(true));

        verify(accountDao, never()).reactivateAccount(anyString());
        assertThat(response.isLoginSuccessful()).isTrue();
    }

    private String userState(final MultiObject multiObject) {
        return ((event.logging.User) multiObject.getObjects().get(0)).getState();
    }

    private CredentialValidationResult validCredentialsButInactive() {
        return new CredentialValidationResult(true, false, false, false, true, false);
    }

    private void givenCredentialsResult(final CredentialValidationResult result) {
        when(accountDao.validateCredentials(USER_ID, PASSWORD)).thenReturn(result);
    }

    private IdentityConfig configWithReactivation(final boolean reactivate) {
        return new IdentityConfig(
                null,
                null,
                ".*\\((.*)\\)",
                null,
                3,
                null,
                reactivate,
                false,
                null,
                null,
                null,
                null,
                new PasswordPolicyConfig(),
                null);
    }

    private LoginResponse login(final IdentityConfig config) {
        final AuthenticationServiceImpl service = new AuthenticationServiceImpl(
                null,
                config,
                null,
                accountDao,
                accountService,
                null,
                null,
                null,
                stroomEventLoggingService,
                executorProvider,
                null);
        return service.handleLogin(new LoginRequest(USER_ID, PASSWORD), request);
    }
}
