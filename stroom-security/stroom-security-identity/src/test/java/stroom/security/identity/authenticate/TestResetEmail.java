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
import stroom.security.api.UserSessionEvictor;
import stroom.security.identity.account.AccountDao;
import stroom.security.identity.account.AccountService;
import stroom.security.identity.account.ResetToken;
import stroom.security.identity.config.IdentityConfig;
import stroom.security.identity.shared.Account;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.task.api.ExecutorProvider;

import event.logging.EventAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestResetEmail {

    private static final String EMAIL = "jbloggs@example.com";
    private static final String USER_ID = "jbloggs";

    @Mock
    private AccountDao accountDao;
    @Mock
    private AccountService accountService;
    @Mock
    private EmailSender emailSender;
    @Mock
    private ExecutorProvider executorProvider;
    @Mock
    private UserSessionEvictor userSessionEvictor;
    @Mock
    private OpenIdConfiguration openIdConfiguration;
    @Mock
    private StroomEventLoggingService stroomEventLoggingService;

    @BeforeEach
    void setUp() {
        // Run the emailing straight away rather than on another thread, so the test can assert on it.
        lenient().when(executorProvider.get(any())).thenReturn(Runnable::run);
        lenient().when(openIdConfiguration.getIdentityProviderType()).thenReturn(IdpType.INTERNAL_IDP);
    }

    @Test
    void unknownEmailAddressStillReportsSuccessAndSendsNothing() {
        // The endpoint is unauthenticated, so reporting anything other than success would let anyone work
        // out which email addresses have accounts.
        when(accountDao.getByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThat(service().resetEmail(EMAIL)).isTrue();

        verify(emailSender, never()).send(anyString(), any(), any(), anyString());
        // Nothing beyond the lookup happens, so a caller cannot tell a known address from an unknown one
        // by how long we take to answer.
        verify(accountDao, never()).tryRecordResetEmailRequest(anyString(), anyLong(), anyLong());
    }

    @Test
    void withAnExternalIdpNothingIsSentAndNoAccountIsEvenLookedUp() {
        // Local password resets do not exist with an external IDP, so the request is a no-op.
        when(openIdConfiguration.getIdentityProviderType()).thenReturn(IdpType.EXTERNAL_IDP);

        assertThat(service().resetEmail(EMAIL)).isTrue();

        verify(accountDao, never()).getByEmail(anyString());
        verify(emailSender, never()).send(anyString(), any(), any(), anyString());
    }

    @Test
    void requestTooSoonAfterTheLastOneSendsNothing() {
        when(accountDao.getByEmail(EMAIL)).thenReturn(Optional.of(account()));
        when(accountDao.tryRecordResetEmailRequest(eq(USER_ID), anyLong(), anyLong())).thenReturn(false);

        assertThat(service().resetEmail(EMAIL)).isTrue();

        verify(emailSender, never()).send(anyString(), any(), any(), anyString());
        // A throttled request must not burn the outstanding link either.
        verify(accountDao, never()).setPasswordResetToken(anyString(), any());
    }

    @Test
    void theEmailedTokenIsAnOpaqueLinkWhoseHashIsStored() {
        when(accountDao.getByEmail(EMAIL)).thenReturn(Optional.of(account()));
        when(accountDao.tryRecordResetEmailRequest(eq(USER_ID), anyLong(), anyLong())).thenReturn(true);

        assertThat(service().resetEmail(EMAIL)).isTrue();

        // The mail goes to the address held against the account, not to whatever the caller typed.
        final ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(eq(EMAIL), any(), any(), tokenCaptor.capture());
        final String token = tokenCaptor.getValue();

        // The emailed token is opaque, not a JWT (which would have two dots), so it can never be presented
        // as a bearer credential.
        assertThat(token.chars().filter(c -> c == '.').count()).isEqualTo(1);
        final PasswordResetLink.Parsed parsed = PasswordResetLink.parse(token).orElseThrow();
        assertThat(parsed.userId()).isEqualTo(USER_ID);

        // Only the hash of the secret is stored against the account, and it must match the emailed token
        // or the link would never verify. The stored value is not the token itself.
        final ArgumentCaptor<ResetToken> resetTokenCaptor = ArgumentCaptor.forClass(ResetToken.class);
        verify(accountDao).setPasswordResetToken(eq(USER_ID), resetTokenCaptor.capture());
        assertThat(parsed.tokenHash()).isEqualTo(resetTokenCaptor.getValue().hash());
        assertThat(resetTokenCaptor.getValue().hash()).isNotEqualTo(token);

        // The account is found by email address, not by treating the address as a user id, which is what
        // used to happen and meant 'forgot password' only worked when the two were the same string.
        verify(accountDao).getByEmail(EMAIL);
        verify(accountService, never()).read(anyString());
    }

    @Test
    void disabledAccountGetsTheCourtesyEmailNotALink() {
        // Previously a disabled account was sent a real link and only refused after typing a new password
        // twice. No link may be issued that cannot be used; the owner is told to see an administrator.
        final Account account = account();
        account.setEnabled(false);
        givenResettableRequestFor(account);

        assertThat(service().resetEmail(EMAIL)).isTrue();

        verify(emailSender).sendCannotResetEmail(eq(EMAIL), any(), any());
        verify(emailSender, never()).send(anyString(), any(), any(), anyString());
        verify(accountDao, never()).setPasswordResetToken(anyString(), any());
    }

    @Test
    void inactiveAccountWhoseSignInWouldBeRefusedGetsTheCourtesyEmail() {
        // Without auto reactivation, a completed reset still ends at "account deactivated" on sign in. The
        // refusal is moved to the first step, where the email routes them to the person who can fix it.
        final Account account = account();
        account.setEnabled(true);
        account.setInactive(true);
        givenResettableRequestFor(account);

        assertThat(service(config(false, null)).resetEmail(EMAIL)).isTrue();

        verify(emailSender).sendCannotResetEmail(eq(EMAIL), any(), any());
        verify(emailSender, never()).send(anyString(), any(), any(), anyString());
    }

    @Test
    void inactiveAccountThatWouldReactivateOnSignInStillGetsARealLink() {
        // With auto reactivation the journey completes: reset, sign in, reactivate. Gating it here would
        // break the good configuration.
        final Account account = account();
        account.setEnabled(true);
        account.setInactive(true);
        givenResettableRequestFor(account);

        assertThat(service(config(true, null)).resetEmail(EMAIL)).isTrue();

        verify(emailSender).send(eq(EMAIL), any(), any(), anyString());
        verify(emailSender, never()).sendCannotResetEmail(anyString(), any(), any());
    }

    @Test
    void lockedAccountWithoutSelfServiceGetsTheCourtesyEmail() {
        final Account account = account();
        account.setEnabled(true);
        account.setFailureLockedMs(System.currentTimeMillis());
        givenResettableRequestFor(account);

        assertThat(service(config(null, false)).resetEmail(EMAIL)).isTrue();

        verify(emailSender).sendCannotResetEmail(eq(EMAIL), any(), any());
        verify(emailSender, never()).send(anyString(), any(), any(), anyString());
    }

    @Test
    void lockedAccountWithSelfServiceGetsARealLink() {
        // This IS the self service unlock: completing the reset clears the lock.
        final Account account = account();
        account.setEnabled(true);
        account.setFailureLockedMs(System.currentTimeMillis());
        givenResettableRequestFor(account);

        assertThat(service(config(null, true)).resetEmail(EMAIL)).isTrue();

        verify(emailSender).send(eq(EMAIL), any(), any(), anyString());
        verify(emailSender, never()).sendCannotResetEmail(anyString(), any(), any());
    }

    @Test
    void theCourtesyEmailSitsBehindTheSameCooldownAsTheLink() {
        // Otherwise reset requests become a way to bomb the mailbox of exactly the accounts that cannot
        // act on them.
        final Account account = account();
        account.setEnabled(false);
        when(accountDao.getByEmail(EMAIL)).thenReturn(Optional.of(account));
        when(accountDao.tryRecordResetEmailRequest(eq(USER_ID), anyLong(), anyLong())).thenReturn(false);

        assertThat(service().resetEmail(EMAIL)).isTrue();

        verify(emailSender, never()).sendCannotResetEmail(anyString(), any(), any());
    }

    @Test
    void theRefusalIsAuditedWithItsExactReason() {
        // The requester learns nothing and the email says only "currently cannot"; the audit trail is
        // where the true state belongs.
        final Account account = account();
        account.setEnabled(false);
        givenResettableRequestFor(account);

        service().resetEmail(EMAIL);

        verify(stroomEventLoggingService).log(
                eq("AuthenticationServiceImpl.resetEmailRefused"),
                contains("disabled"),
                any(EventAction.class));
    }

    private void givenResettableRequestFor(final Account account) {
        when(accountDao.getByEmail(EMAIL)).thenReturn(Optional.of(account));
        when(accountDao.tryRecordResetEmailRequest(eq(USER_ID), anyLong(), anyLong())).thenReturn(true);
    }

    private AuthenticationServiceImpl service() {
        return service(new IdentityConfig());
    }

    private AuthenticationServiceImpl service(final IdentityConfig config) {
        return new AuthenticationServiceImpl(
                null,
                config,
                emailSender,
                accountDao,
                accountService,
                () -> openIdConfiguration,
                new IdentityConfig().getTokenConfig(),
                null,
                () -> stroomEventLoggingService,
                executorProvider,
                userSessionEvictor);
    }

    /**
     * As deserialising a YAML block naming only these properties would build it.
     */
    private static IdentityConfig config(final Boolean reactivateInactiveAccountsOnLogin,
                                         final Boolean allowLockedAccountPasswordReset) {
        return new IdentityConfig(
                null, null, null, null, null, null,
                reactivateInactiveAccountsOnLogin,
                allowLockedAccountPasswordReset,
                null, null, null, null, null, null);
    }

    private Account account() {
        final Account account = new Account();
        account.setUserId(USER_ID);
        account.setEmail(EMAIL);
        // The request-time gate reads account state, so the harness account must be a healthy one.
        account.setEnabled(true);
        return account;
    }
}
