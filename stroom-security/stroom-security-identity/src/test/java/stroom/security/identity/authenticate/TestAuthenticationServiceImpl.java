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

package stroom.security.identity.authenticate;

import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.identity.account.AccountDao;
import stroom.security.identity.account.AccountService;
import stroom.security.identity.authenticate.api.AuthenticationService;
import stroom.security.identity.config.IdentityConfig;
import stroom.security.identity.config.PasswordPolicyConfig;
import stroom.security.identity.shared.Account;
import stroom.security.identity.shared.ChangePasswordRequest;
import stroom.security.identity.shared.ChangePasswordResponse;
import stroom.security.identity.shared.ConfirmPasswordRequest;
import stroom.security.identity.shared.ConfirmPasswordResponse;
import stroom.security.identity.shared.LoginRequest;
import stroom.security.identity.shared.LoginResponse;
import stroom.task.api.ExecutorProvider;
import stroom.util.cert.CertificateExtractor;

import event.logging.AuthenticateOutcomeReason;
import event.logging.MultiObject;
import event.logging.UpdateEventAction;
import jakarta.inject.Provider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
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
    @Mock
    private CertificateExtractor certificateExtractor;

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
                true, false, false, false, false));

        login(configWithReactivation(true));

        verify(eventLoggingService, never()).log(anyString(), anyString(), any());
    }

    @Test
    void nonExistentAccountIsNotReactivated() {
        // validateCredentials never reports a non existent account as inactive, but the guard must
        // not depend on that.
        givenCredentialsResult(new CredentialValidationResult(
                false, true, false, false, true));

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
        assertThat(response.getMessage()).contains("deactivated due to inactivity");
    }

    @Test
    void inactiveAccountIsNotReactivatedWithWrongPassword() {
        // An incorrect password must never reactivate an account, even though it is inactive.
        givenCredentialsResult(new CredentialValidationResult(
                false, false, false, false, true));
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
                true, false, true, false, true));

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
                false, false, true, false, false));

        final LoginResponse response = login(configWithReactivation(true));

        verify(accountDao, never()).incrementLoginFailures(anyString());
        assertThat(response.isLoginSuccessful()).isFalse();
    }

    @Test
    void wrongPasswordOnAnUnlockedAccountCountsAsAFailure() {
        givenCredentialsResult(new CredentialValidationResult(
                false, false, false, false, false));
        when(accountDao.incrementLoginFailures(USER_ID)).thenReturn(false);

        final LoginResponse response = login(configWithReactivation(true));

        verify(accountDao).incrementLoginFailures(USER_ID);
        assertThat(response.isLoginSuccessful()).isFalse();
    }

    @Test
    void disabledAndInactiveAccountIsNotReactivated() {
        givenCredentialsResult(new CredentialValidationResult(
                true, false, false, true, true));

        final LoginResponse response = login(configWithReactivation(true));

        verify(accountDao, never()).reactivateAccount(anyString());
        assertThat(response.isLoginSuccessful()).isFalse();
    }

    @Test
    void activeAccountIsNotReactivated() {
        givenCredentialsResult(new CredentialValidationResult(
                true, false, false, false, false));

        final LoginResponse response = login(configWithReactivation(true));

        verify(accountDao, never()).reactivateAccount(anyString());
        assertThat(response.isLoginSuccessful()).isTrue();
    }

    private String userState(final MultiObject multiObject) {
        return ((event.logging.User) multiObject.getObjects().get(0)).getState();
    }

    private CredentialValidationResult validCredentialsButInactive() {
        return new CredentialValidationResult(true, false, false, false, true);
    }

    private void givenStepUpResult(final String password, final CredentialValidationResult result) {
        when(accountDao.validateCredentials(USER_ID, password)).thenReturn(result);
    }

    @Test
    void lockedMessageSaysHowLongRatherThanSendingEveryoneToAnAdministrator() {
        // The previous constant said "contact your administrator" for every lockout, in a default
        // configuration where the only thing an administrator could have said was "wait half an hour".
        givenCredentialsResult(lockedResult());
        givenLockEndsInMinutes(30);

        final LoginResponse response = login(configWithReactivation(false));

        assertThat(response.getMessage())
                .contains("temporarily locked")
                .contains("about 30 minutes")
                .doesNotContain("administrator");
    }

    @Test
    void lockedMessageNeverClaimsLessTimeThanRemains() {
        // Rounded up: forty seconds is "about 1 minute", not a promise that is already broken.
        givenCredentialsResult(lockedResult());
        when(accountDao.get(USER_ID)).thenReturn(Optional.of(
                accountLockedUntil(System.currentTimeMillis() + 40_000)));

        final LoginResponse response = login(configWithReactivation(false));

        assertThat(response.getMessage()).contains("about 1 minute");
        assertThat(response.getMessage()).doesNotContain("minutes");
    }

    @Test
    void lockedMessageSendsToTheAdministratorOnlyWhenTheLockNeverLapses() {
        // A lock duration of zero never lapses, so waiting is not a remedy and must not be suggested.
        givenCredentialsResult(lockedResult());
        when(accountDao.get(USER_ID)).thenReturn(Optional.of(accountLockedUntil(null)));

        final LoginResponse response = login(configWithReactivation(false));

        assertThat(response.getMessage()).contains("contact your administrator");
        assertThat(response.getMessage()).doesNotContain("Try again");
    }

    @Test
    void lockedMessageOffersTheResetWhereSelfServiceUnlockIsOn() {
        givenCredentialsResult(lockedResult());
        givenLockEndsInMinutes(30);

        final LoginResponse response = login(configWithSelfServiceUnlock());

        assertThat(response.getMessage()).contains("Forgot password?");
    }

    @Test
    void disabledOutranksLockedInTheMessageToo() {
        // Both flags can be true at once - a disabled account can still carry an unexpired failure lock -
        // and telling that account to "try again in about 30 minutes" promises that waiting will help
        // when it will not. The administrator is the only door for a disabled account.
        givenCredentialsResult(new CredentialValidationResult(false, false, true, true, false));

        final LoginResponse response = login(configWithReactivation(false));

        assertThat(response.getMessage())
                .contains("disabled")
                .doesNotContain("Try again");
    }

    private static CredentialValidationResult lockedResult() {
        return new CredentialValidationResult(false, false, true, false, false);
    }

    private void givenLockEndsInMinutes(final int minutes) {
        when(accountDao.get(USER_ID)).thenReturn(Optional.of(
                accountLockedUntil(System.currentTimeMillis() + minutes * 60_000L)));
    }

    private static Account accountLockedUntil(final Long untilMs) {
        final Account account = new Account();
        account.setUserId(USER_ID);
        account.setEnabled(true);
        account.setFailureLockedMs(System.currentTimeMillis());
        account.setFailureLockedUntilMs(untilMs);
        return account;
    }

    private IdentityConfig configWithSelfServiceUnlock() {
        return new IdentityConfig(
                null,
                true,
                ".*\\((.*)\\)",
                null,
                3,
                null,
                false,
                true,
                null,
                null,
                null,
                null,
                new PasswordPolicyConfig(),
                null);
    }

    private void givenCredentialsResult(final CredentialValidationResult result) {
        when(accountDao.validateCredentials(USER_ID, PASSWORD)).thenReturn(result);
    }

    private IdentityConfig configWithReactivation(final boolean reactivate) {
        return new IdentityConfig(
                null,
                // Certificate authentication on, so the certificate sign in tests reach the path at all.
                true,
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

    @Test
    void inactiveAccountIsReactivatedByACertificateSignIn() {
        // The option is not password-only. A valid certificate is the same proof of identity, so a
        // certificate user must be able to recover from dormancy the same way - otherwise a certificate
        // deployment gets nothing from turning the option on.
        givenCertificateFor(accountThatIs(true, false, true));

        certificateSignIn(configWithReactivation(true));

        verify(accountDao).reactivateAccount(USER_ID);
    }

    @Test
    void certificateSignInDoesNotReactivateWhenTheOptionIsOff() {
        givenCertificateFor(accountThatIs(true, false, true));

        certificateSignIn(configWithReactivation(false));

        verify(accountDao, never()).reactivateAccount(anyString());
    }

    @Test
    void certificateSignInDoesNotReactivateALockedAccount() {
        // Being inactive has to be the only thing in the way. Reactivating an account that still cannot
        // sign in changes its state for nothing, and would make the two paths disagree about when
        // reactivation happens.
        givenCertificateFor(accountThatIs(true, true, true));

        certificateSignIn(configWithReactivation(true));

        verify(accountDao, never()).reactivateAccount(anyString());
    }

    @Test
    void certificateSignInDoesNotReactivateADisabledAccount() {
        givenCertificateFor(accountThatIs(false, false, true));

        certificateSignIn(configWithReactivation(true));

        verify(accountDao, never()).reactivateAccount(anyString());
    }

    @Test
    void certificateRefusalForADisabledAndLockedAccountSaysDisabled() {
        // Same precedence as the password path: disabled outranks locked, because a lock lapses and a
        // barring does not. Reporting ACCOUNT_LOCKED for a barred account would name the wrong control.
        givenCertificateFor(accountThatIs(false, true, false));

        final AuthenticationService.AuthStatus status = serviceWith(configWithReactivation(false))
                .currentAuthState(request);

        assertThat(status.getError()).isPresent();
        assertThat(status.getError().orElseThrow().getMessage())
                .contains("disabled")
                .doesNotContain("locked");
    }

    @Test
    void certificateReactivationIsEventLoggedSeparatelyFromTheSignIn() {
        // The state change has to appear in the audit in its own right. The sign in event does not record
        // that the account was altered, so without this the reactivation would leave no trace.
        givenCertificateFor(accountThatIs(true, false, true));

        certificateSignIn(configWithReactivation(true));

        final ArgumentCaptor<UpdateEventAction> captor = ArgumentCaptor.forClass(UpdateEventAction.class);
        verify(eventLoggingService).log(anyString(), anyString(), captor.capture());

        final UpdateEventAction action = captor.getValue();
        assertThat(userState(action.getBefore())).isEqualTo("Enabled/Inactive/Unlocked");
        assertThat(userState(action.getAfter())).isEqualTo("Enabled/Active/Unlocked");
    }

    @Test
    void wrongPasswordAtStepUpIsCountedAgainstTheLockout() {
        // Re-checking the password of someone already signed in is asking whether the person is who the
        // session says. A check that does not count is a way to guess a password at leisure: the counter
        // never moves, the account never locks, and the sign in path that does count is never reached.
        givenSignedIn();
        givenStepUpResult("the-wrong-password", new CredentialValidationResult(false, false, false, false, false));

        final ConfirmPasswordResponse response = serviceWith(configWithReactivation(false))
                .confirmPassword(request, new ConfirmPasswordRequest("the-wrong-password"));

        assertThat(response.isValid()).isFalse();
        verify(accountDao).incrementLoginFailures(USER_ID);
    }

    @Test
    void correctPasswordAtStepUpIsNotCountedAgainstTheLockout() {
        givenSignedIn();
        givenStepUpResult("the-right-password", new CredentialValidationResult(true, false, false, false, false));

        final ConfirmPasswordResponse response = serviceWith(configWithReactivation(false))
                .confirmPassword(request, new ConfirmPasswordRequest("the-right-password"));

        assertThat(response.isValid()).isTrue();
        verify(accountDao, never()).incrementLoginFailures(anyString());
    }

    @Test
    void alreadyLockedAccountIsNotCountedAgainAtStepUp() {
        // Nothing to gain from the write, and continued guessing must not extend a lock that would
        // otherwise clear itself.
        givenSignedIn();
        givenStepUpResult("the-wrong-password", new CredentialValidationResult(false, false, true, false, false));

        serviceWith(configWithReactivation(false))
                .confirmPassword(request, new ConfirmPasswordRequest("the-wrong-password"));

        verify(accountDao, never()).incrementLoginFailures(anyString());
    }

    /**
     * Put an auth state in the session the way a successful sign in would, so the step-up paths are
     * reachable. The state object is private to the service, so it has to be made by signing in.
     */
    private void givenSignedIn() {
        final Map<String, Object> attributes = new HashMap<>();
        final HttpSession session = mock(HttpSession.class);
        lenient().when(request.getSession(anyBoolean())).thenReturn(session);
        lenient().doAnswer(invocation -> attributes.put(invocation.getArgument(0), invocation.getArgument(1)))
                .when(session).setAttribute(anyString(), any());
        lenient().when(session.getAttribute(anyString()))
                .thenAnswer(invocation -> attributes.get(invocation.getArgument(0)));

        givenCredentialsResult(new CredentialValidationResult(true, false, false, false, false));
        assertThat(login(configWithReactivation(false)).isLoginSuccessful()).isTrue();
    }

    @Test
    void wrongCurrentPasswordIsRefusedRatherThanThrown() {
        // Getting your own current password wrong is an ordinary thing to do. Throwing made it a 500 with
        // the raw message in the body, where the reset flow had always returned a refusal.
        givenSignedIn();
        givenStepUpResult("the-wrong-password", new CredentialValidationResult(false, false, false, false, false));

        final ChangePasswordResponse response = serviceWith(configWithReactivation(false))
                .changePassword(request, new ChangePasswordRequest(
                        USER_ID, "the-wrong-password", "a-new-password", "a-new-password"));

        assertThat(response.isChangeSucceeded()).isFalse();
        assertThat(response.getMessage()).isNotBlank();
        assertThat(response.isForceSignIn())
                .as("the session is fine, only the password was wrong")
                .isFalse();
    }

    @Test
    void newPasswordThatBreaksThePolicyIsRefusedRatherThanThrown() {
        givenSignedIn();
        givenStepUpResult("the-right-password", new CredentialValidationResult(true, false, false, false, false));

        final ChangePasswordResponse response = serviceWith(configWithReactivation(false))
                .changePassword(request, new ChangePasswordRequest(
                        USER_ID, "the-right-password", "short", "does-not-match"));

        assertThat(response.isChangeSucceeded()).isFalse();
        assertThat(response.getMessage()).isNotBlank();
        assertThat(response.isForceSignIn()).isFalse();
    }

    @Test
    void refusalSaysWhichControlFiredRatherThanAlwaysBlamingThePassword() {
        // The audit is read by people entitled to know which control refused a sign in. Recording a lockout
        // as a wrong password loses exactly the fact the event exists to keep.
        givenCredentialsResult(new CredentialValidationResult(false, false, true, false, false));

        final AuthenticationServiceImpl.LoginOutcome outcome = loginOutcome(configWithReactivation(false));

        assertThat(outcome.response().isLoginSuccessful()).isFalse();
        assertThat(outcome.reason()).isEqualTo(AuthenticateOutcomeReason.ACCOUNT_LOCKED);
    }

    @Test
    void wrongPasswordIsStillReportedAsAWrongPassword() {
        givenCredentialsResult(new CredentialValidationResult(false, false, false, false, false));
        when(accountDao.incrementLoginFailures(USER_ID)).thenReturn(false);

        final AuthenticationServiceImpl.LoginOutcome outcome = loginOutcome(configWithReactivation(false));

        assertThat(outcome.reason()).isEqualTo(AuthenticateOutcomeReason.INCORRECT_PASSWORD);
    }

    @Test
    void anUnknownAccountIsReportedAsAnUnknownUsername() {
        // Precise in the audit, where it is safe, while the caller still gets one generic message.
        givenCredentialsResult(new CredentialValidationResult(false, true, false, false, false));

        final AuthenticationServiceImpl.LoginOutcome outcome = loginOutcome(configWithReactivation(false));

        assertThat(outcome.reason()).isEqualTo(AuthenticateOutcomeReason.INCORRECT_USERNAME);
    }

    @Test
    void disabledAccountDoesNotAccrueFailuresForPasswordsNobodyChecked() {
        // A disabled account never has its password checked, so there is no failure to count. Counting one
        // anyway would let it lock as well, and re-enabling it would then leave the user still shut out.
        givenCredentialsResult(new CredentialValidationResult(false, false, false, true, false));

        login(configWithReactivation(false));

        verify(accountDao, never()).incrementLoginFailures(anyString());
    }

    @Test
    void inactiveAccountStillCountsAWrongPassword() {
        // Unlike a disabled one, an inactive account does have its password checked - that is what earns it
        // reactivation - so a wrong password there is a real failure and must be counted.
        givenCredentialsResult(new CredentialValidationResult(false, false, false, false, true));
        when(accountDao.incrementLoginFailures(USER_ID)).thenReturn(false);

        login(configWithReactivation(false));

        verify(accountDao).incrementLoginFailures(USER_ID);
    }

    private Account accountThatIs(final boolean enabled, final boolean locked, final boolean inactive) {
        final Account account = new Account();
        account.setUserId(USER_ID);
        account.setEnabled(enabled);
        account.setFailureLockedMs(locked
                ? System.currentTimeMillis()
                : null);
        account.setInactive(inactive);
        return account;
    }

    private void givenCertificateFor(final Account account) {
        // The CN pattern in the test config extracts whatever is in brackets.
        when(certificateExtractor.getCN(request)).thenReturn(Optional.of("CN=Joe Bloggs (" + USER_ID + ")"));
        when(accountDao.get(USER_ID)).thenReturn(Optional.of(account));
    }

    private void certificateSignIn(final IdentityConfig config) {
        serviceWith(config).currentAuthState(request);
    }

    private LoginResponse login(final IdentityConfig config) {
        return loginOutcome(config).response();
    }

    private AuthenticationServiceImpl.LoginOutcome loginOutcome(final IdentityConfig config) {
        return serviceWith(config).handleLogin(new LoginRequest(USER_ID, PASSWORD), request);
    }

    private AuthenticationServiceImpl serviceWith(final IdentityConfig config) {
        return new AuthenticationServiceImpl(
                null,
                config,
                null,
                accountDao,
                accountService,
                null,
                null,
                certificateExtractor,
                stroomEventLoggingService,
                executorProvider,
                null);
    }
}
