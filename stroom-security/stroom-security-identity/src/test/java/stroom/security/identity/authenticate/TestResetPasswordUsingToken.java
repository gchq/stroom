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
import stroom.security.api.UserSessionEvictor;
import stroom.security.identity.account.AccountDao;
import stroom.security.identity.account.AccountService;
import stroom.security.identity.account.ResetToken;
import stroom.security.identity.config.IdentityConfig;
import stroom.security.identity.config.PasswordPolicyConfig;
import stroom.security.identity.shared.Account;
import stroom.security.identity.shared.ChangePasswordResponse;
import stroom.security.identity.shared.ResetPasswordRequest;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.task.api.ExecutorProvider;

import jakarta.inject.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestResetPasswordUsingToken {

    private static final String USER_ID = "jbloggs";
    // A real opaque reset link and the hash stored against the account.
    private static final PasswordResetLink.Issued ISSUED = PasswordResetLink.issue(USER_ID);
    private static final String TOKEN = ISSUED.token();
    private static final String HASH = ISSUED.tokenHash();
    private static final String NEW_PASSWORD = "correct-horse-battery-staple";

    @Mock
    private AccountDao accountDao;
    @Mock
    private AccountService accountService;
    @Mock
    private ExecutorProvider executorProvider;
    @Mock
    private UserSessionEvictor userSessionEvictor;

    @Mock
    private Provider<StroomEventLoggingService> stroomEventLoggingService;
    @Mock
    private StroomEventLoggingService eventLoggingService;
    @Mock
    private OpenIdConfiguration openIdConfiguration;

    @BeforeEach
    void setUp() {
        lenient().when(executorProvider.get(any())).thenReturn(Runnable::run);
        lenient().when(stroomEventLoggingService.get()).thenReturn(eventLoggingService);
        lenient().when(openIdConfiguration.getIdentityProviderType()).thenReturn(IdpType.INTERNAL_IDP);
        // A valid link: the stored hash matches, expiring an hour from now (within the window).
        lenient().when(accountDao.getPasswordResetToken(USER_ID))
                .thenReturn(Optional.of(new ResetToken(HASH, System.currentTimeMillis() + 3_600_000L)));
        lenient().when(accountDao.unlockAndSetPassword(eq(USER_ID), anyString(), eq(HASH)))
                .thenReturn(true);
        // By default the new password is not the one already on the account.
        lenient().when(accountDao.validateCredentials(eq(USER_ID), anyString()))
                .thenReturn(new CredentialValidationResult(false, false, false, false, false, false));
    }

    @Test
    void unlockedAccountCanResetItsPassword() {
        givenAccount(account(false, false, true));

        final ChangePasswordResponse response = resetPassword(config(false));

        assertThat(response.isChangeSucceeded()).isTrue();
        verify(accountDao).unlockAndSetPassword(USER_ID, NEW_PASSWORD, HASH);
    }

    @Test
    void successfulResetSignsOutEveryExistingSession() {
        // A reset is account recovery: the person who requested it may not control the sessions the
        // old password left open, so every session (with no exception) must be ended.
        givenAccount(account(false, false, true));

        final ChangePasswordResponse response = resetPassword(config(false));

        assertThat(response.isChangeSucceeded()).isTrue();
        verify(userSessionEvictor).evictUserSessions(USER_ID, null);
    }

    @Test
    void failedResetLeavesSessionsAlone() {
        // If the password was not actually changed, existing sessions must not be disturbed.
        givenAccount(account(false, false, false));

        final ChangePasswordResponse response = resetPassword(config(true));

        assertThat(response.isChangeSucceeded()).isFalse();
        verify(userSessionEvictor, never()).evictUserSessions(anyString(), any());
    }

    @Test
    void resettingAPasswordDoesNotReactivateAnInactiveAccount() {
        // The whole point of unlockAndSetPassword rather than resetPassword. Only a successful
        // authentication may make an inactive account active again.
        givenAccount(account(false, true, true));

        final ChangePasswordResponse response = resetPassword(config(false));

        assertThat(response.isChangeSucceeded()).isTrue();
        verify(accountDao).unlockAndSetPassword(USER_ID, NEW_PASSWORD, HASH);
        verify(accountDao, never()).reactivateAccount(anyString());
        verify(accountDao, never()).resetPassword(anyString(), anyString());
    }

    @Test
    void lockedAccountCannotResetItsPasswordUnlessConfigured() {
        givenAccount(account(true, false, true));

        final ChangePasswordResponse response = resetPassword(config(false));

        assertThat(response.isChangeSucceeded()).isFalse();
        // Must not say why. The caller does not necessarily own this account.
        assertThat(response.getMessage()).doesNotContain("locked");
        assertThat(response.getMessage()).contains("contact your administrator");
        verify(accountDao, never()).unlockAndSetPassword(anyString(), anyString(), anyString());
    }

    @Test
    void lockedAccountCanResetItsPasswordWhenConfigured() {
        givenAccount(account(true, false, true));

        final ChangePasswordResponse response = resetPassword(config(true));

        assertThat(response.isChangeSucceeded()).isTrue();
        verify(accountDao).unlockAndSetPassword(USER_ID, NEW_PASSWORD, HASH);
    }

    @Test
    void disabledAccountCanNeverResetItsPassword() {
        givenAccount(account(false, false, false));

        final ChangePasswordResponse response = resetPassword(config(true));

        assertThat(response.isChangeSucceeded()).isFalse();
        verify(accountDao, never()).unlockAndSetPassword(anyString(), anyString(), anyString());
    }

    @Test
    void currentPasswordCannotBeReused() {
        // Otherwise a user locked out by mistyping could clear the lock without changing anything.
        givenAccount(account(true, false, true));
        when(accountDao.validateCredentials(USER_ID, NEW_PASSWORD))
                .thenReturn(new CredentialValidationResult(true, false, true, false, false, false));

        final ChangePasswordResponse response = resetPassword(config(true));

        assertThat(response.isChangeSucceeded()).isFalse();
        assertThat(response.getMessage()).contains("reuse");
        verify(accountDao, never()).unlockAndSetPassword(anyString(), anyString(), anyString());
    }

    @Test
    void linkConsumedByAConcurrentRequestIsRefused() {
        // Two requests can both get past the hash read, so the conditional update is what actually
        // decides. The loser must be told the link is spent rather than reporting success.
        givenAccount(account(false, false, true));
        when(accountDao.unlockAndSetPassword(eq(USER_ID), anyString(), eq(HASH))).thenReturn(false);

        final ChangePasswordResponse response = resetPassword(config(false));

        assertThat(response.isChangeSucceeded()).isFalse();
        assertThat(response.getMessage()).contains("invalid or has expired");
    }

    @Test
    void onlyTheMostRecentlyIssuedTokenWorks() {
        // Requesting another reset email records a new token, so the older link must stop working.
        givenAccount(account(false, false, true));
        when(accountDao.getPasswordResetToken(USER_ID))
                .thenReturn(Optional.of(new ResetToken("a-newer-hash", System.currentTimeMillis() + 3_600_000L)));

        final ChangePasswordResponse response = resetPassword(config(false));

        assertThat(response.isChangeSucceeded()).isFalse();
        verify(accountDao, never()).unlockAndSetPassword(anyString(), anyString(), anyString());
    }

    @Test
    void usedTokenIsRefusedBecauseTheHashIsCleared() {
        // Setting a password by any route clears the hash, which is what makes a link single use and
        // also invalidates it if the password is changed some other way.
        givenAccount(account(false, false, true));
        when(accountDao.getPasswordResetToken(USER_ID)).thenReturn(Optional.empty());

        final ChangePasswordResponse response = resetPassword(config(false));

        assertThat(response.isChangeSucceeded()).isFalse();
        verify(accountDao, never()).unlockAndSetPassword(anyString(), anyString(), anyString());
    }

    @Test
    void expiredLinkIsRefused() {
        // Once past its stored expiry the link no longer works.
        givenAccount(account(false, false, true));
        when(accountDao.getPasswordResetToken(USER_ID)).thenReturn(Optional.of(new ResetToken(HASH, 0L)));

        final ChangePasswordResponse response = resetPassword(config(false));

        assertThat(response.isChangeSucceeded()).isFalse();
        verify(accountDao, never()).unlockAndSetPassword(anyString(), anyString(), anyString());
    }

    @Test
    void processingAccountCanNeverResetItsPassword() {
        final Account account = account(false, false, true);
        account.setProcessingAccount(true);
        givenAccount(account);

        final ChangePasswordResponse response = resetPassword(config(true));

        assertThat(response.isChangeSucceeded()).isFalse();
        verify(accountDao, never()).unlockAndSetPassword(anyString(), anyString(), anyString());
    }

    @Test
    void mismatchedConfirmationIsReportedRatherThanThrown() {
        // PasswordValidator signals policy violations by throwing. On this unauthenticated endpoint that
        // would surface as a 500 rather than telling the user what they got wrong.
        givenAccount(account(false, false, true));

        final ChangePasswordResponse response = resetPassword(config(false),
                new ResetPasswordRequest(TOKEN, NEW_PASSWORD, "something-else"));

        assertThat(response.isChangeSucceeded()).isFalse();
        assertThat(response.getMessage()).contains("confirmation");
        verify(accountDao, never()).unlockAndSetPassword(anyString(), anyString(), anyString());
    }

    @Test
    void passwordThatIsTooShortIsReportedRatherThanThrown() {
        givenAccount(account(false, false, true));

        final ChangePasswordResponse response = resetPassword(config(false),
                new ResetPasswordRequest(TOKEN, "x", "x"));

        assertThat(response.isChangeSucceeded()).isFalse();
        assertThat(response.getMessage()).contains("length");
        verify(accountDao, never()).unlockAndSetPassword(anyString(), anyString(), anyString());
    }

    @Test
    void validTokenForAnAccountThatNoLongerExistsIsRefused() {
        when(accountDao.get(USER_ID)).thenReturn(Optional.empty());

        final ChangePasswordResponse response = resetPassword(config(false));

        assertThat(response.isChangeSucceeded()).isFalse();
        verify(accountDao, never()).unlockAndSetPassword(anyString(), anyString(), anyString());
    }

    @Test
    void malformedTokenIsRefused() {
        final ChangePasswordResponse response = resetPassword(config(false),
                new ResetPasswordRequest("not-a-valid-token", NEW_PASSWORD, NEW_PASSWORD));

        assertThat(response.isChangeSucceeded()).isFalse();
        verify(accountDao, never()).unlockAndSetPassword(anyString(), anyString(), anyString());
    }

    @Test
    void resetsAreRefusedWhenPasswordResetsAreDisallowed() {
        final ChangePasswordResponse response = resetPassword(config(true, false));

        assertThat(response.isChangeSucceeded()).isFalse();
        verify(accountDao, never()).unlockAndSetPassword(anyString(), anyString(), anyString());
        // The guard is placed before any lookup deliberately, so a disabled feature does no work.
        verify(accountDao, never()).get(anyString());
    }

    @Test
    void resetsAreRefusedWithAnExternalIdp() {
        // With an external IDP there are no local passwords to reset.
        when(openIdConfiguration.getIdentityProviderType()).thenReturn(IdpType.EXTERNAL_IDP);

        final ChangePasswordResponse response = resetPassword(config(false));

        assertThat(response.isChangeSucceeded()).isFalse();
        verify(accountDao, never()).unlockAndSetPassword(anyString(), anyString(), anyString());
        verify(accountDao, never()).get(anyString());
    }

    private void givenAccount(final Account account) {
        when(accountDao.get(USER_ID)).thenReturn(Optional.of(account));
    }

    private Account account(final boolean locked, final boolean inactive, final boolean enabled) {
        final Account account = new Account();
        account.setUserId(USER_ID);
        account.setLocked(locked);
        account.setInactive(inactive);
        account.setEnabled(enabled);
        return account;
    }

    private ChangePasswordResponse resetPassword(final IdentityConfig config) {
        return resetPassword(config, new ResetPasswordRequest(TOKEN, NEW_PASSWORD, NEW_PASSWORD));
    }

    private ChangePasswordResponse resetPassword(final IdentityConfig config,
                                                 final ResetPasswordRequest request) {
        final AuthenticationServiceImpl service = new AuthenticationServiceImpl(
                null,
                config,
                null,
                accountDao,
                accountService,
                () -> openIdConfiguration,
                new IdentityConfig().getTokenConfig(),
                null,
                stroomEventLoggingService,
                executorProvider,
                userSessionEvictor);
        return service.resetPasswordUsingToken(request);
    }

    private IdentityConfig config(final boolean allowLockedAccountPasswordReset) {
        return config(allowLockedAccountPasswordReset, true);
    }

    private IdentityConfig config(final boolean allowLockedAccountPasswordReset,
                                  final boolean allowPasswordResets) {
        return new IdentityConfig(
                null,
                null,
                ".*\\((.*)\\)",
                null,
                3,
                null,
                false,
                allowLockedAccountPasswordReset,
                null,
                null,
                null,
                null,
                passwordPolicy(allowPasswordResets),
                null);
    }

    private PasswordPolicyConfig passwordPolicy(final boolean allowPasswordResets) {
        return new PasswordPolicyConfig(
                allowPasswordResets,
                null,
                null,
                null,
                null,
                0,
                8,
                null);
    }
}
