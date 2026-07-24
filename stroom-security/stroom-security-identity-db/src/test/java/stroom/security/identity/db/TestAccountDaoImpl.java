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

package stroom.security.identity.db;

import stroom.collection.mock.MockCollectionModule;
import stroom.dictionary.mock.MockWordListProviderModule;
import stroom.docrefinfo.mock.MockDocRefInfoModule;
import stroom.security.api.SecurityContext;
import stroom.security.identity.account.AccountDao;
import stroom.security.identity.account.ResetToken;
import stroom.security.identity.config.IdentityConfig;
import stroom.security.identity.shared.Account;
import stroom.security.mock.MockSecurityContext;
import stroom.test.common.util.db.DbTestModule;
import stroom.util.exception.DataChangedException;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the account state that the self service unlock flows depend on. These assertions are the whole
 * point of {@code reactivateAccount} and {@code unlockAndSetPassword} existing separately from
 * {@code resetPassword}, and they cannot be proved with a mocked DAO.
 */
class TestAccountDaoImpl {

    // A reset link expiry far enough in the future that these tests are never near it.
    private static final long FUTURE_EXPIRY = System.currentTimeMillis() + 3_600_000L;

    @Inject
    private AccountDao accountDao;

    @BeforeEach
    void setUp() {
        Guice.createInjector(new TestModule()).injectMembers(this);
    }

    @Test
    void reactivateAccountClearsInactiveAndNothingElse() {
        final String userId = createAccount(account -> {
            account.setInactive(true);
            account.setLocked(true);
            account.setEnabled(false);
        });
        final String passwordHashBefore = passwordHash(userId);

        accountDao.reactivateAccount(userId);

        final Account account = accountDao.get(userId).orElseThrow();
        assertThat(account.isInactive()).isFalse();
        // Reactivating must not quietly unlock or re-enable an account.
        assertThat(account.isLocked()).isTrue();
        assertThat(account.isEnabled()).isFalse();
        assertThat(passwordHash(userId)).isEqualTo(passwordHashBefore);
    }

    @Test
    void reactivateAccountRecordsWhenItHappened() {
        // REACTIVATED_MS is what stops the Account Maintenance job deactivating the account again before
        // the caller has recorded a successful login.
        final String userId = createAccount(account -> account.setInactive(true));

        accountDao.reactivateAccount(userId);

        assertThat(accountDao.get(userId).orElseThrow().getReactivatedMs()).isNotNull();
    }

    @Test
    void unlockAndSetPasswordClearsTheLockButLeavesTheAccountInactive() {
        // The requirement: resetting a password must not make an inactive account active again. Only a
        // successful authentication may do that.
        final String userId = createAccount(account -> {
            account.setLocked(true);
            account.setInactive(true);
        });
        final String passwordHashBefore = passwordHash(userId);

        accountDao.setPasswordResetToken(userId, new ResetToken("the-hash", FUTURE_EXPIRY));

        assertThat(accountDao.unlockAndSetPassword(userId, "a-brand-new-password", "the-hash")).isTrue();

        final Account account = accountDao.get(userId).orElseThrow();
        assertThat(account.isLocked()).isFalse();
        assertThat(account.getLoginFailures()).isZero();
        assertThat(account.isInactive()).isTrue();
        assertThat(passwordHash(userId)).isNotEqualTo(passwordHashBefore);
    }

    @Test
    void unlockAndSetPasswordDoesNotEnableADisabledAccount() {
        final String userId = createAccount(account -> account.setEnabled(false));
        accountDao.setPasswordResetToken(userId, new ResetToken("the-hash", FUTURE_EXPIRY));

        accountDao.unlockAndSetPassword(userId, "a-brand-new-password", "the-hash");

        assertThat(accountDao.get(userId).orElseThrow().isEnabled()).isFalse();
    }

    @Test
    void unlockAndSetPasswordMovesThePasswordChangedTimeOn() {
        // This is what makes a reset token single use, so it has to actually change.
        final String userId = createAccount(account -> {
        });
        accountDao.setPasswordResetToken(userId, new ResetToken("the-hash", FUTURE_EXPIRY));
        final Long before = accountDao.getPasswordLastChangedMs(userId).orElseThrow();

        accountDao.unlockAndSetPassword(userId, "a-brand-new-password", "the-hash");

        final Long after = accountDao.getPasswordLastChangedMs(userId).orElseThrow();
        assertThat(after).isGreaterThan(before);
    }

    @Test
    void getPasswordLastChangedMsFallsBackToTheCreateTime() {
        // A password that has never been changed has no changed time. Returning null instead would make
        // the token binding claim useless.
        final long createTimeMs = System.currentTimeMillis() - 5_000L;
        final String userId = createAccount(account -> account.setCreateTimeMs(createTimeMs));

        assertThat(accountDao.getPasswordLastChangedMs(userId)).contains(createTimeMs);
    }

    @Test
    void settingAPasswordOnlyWorksForTheOutstandingHash() {
        // This is what serialises two concurrent requests using the same link: the first clears the
        // hash, so the second matches no rows and changes nothing.
        final String userId = createAccount(account -> {
        });
        accountDao.setPasswordResetToken(userId, new ResetToken("the-hash", FUTURE_EXPIRY));

        assertThat(accountDao.unlockAndSetPassword(userId, "first-password", "the-hash")).isTrue();
        assertThat(accountDao.unlockAndSetPassword(userId, "second-password", "the-hash")).isFalse();

        // The second attempt must not have changed the password.
        assertThat(accountDao.validateCredentials(userId, "first-password").isValidCredentials()).isTrue();
        assertThat(accountDao.validateCredentials(userId, "second-password").isValidCredentials()).isFalse();
    }

    @Test
    void settingAPasswordDoesNothingForAnUnknownOrBlankHash() {
        final String userId = createAccount(account -> {
        });
        accountDao.setPasswordResetToken(userId, new ResetToken("the-hash", FUTURE_EXPIRY));

        assertThat(accountDao.unlockAndSetPassword(userId, "new-password", "a-different-hash")).isFalse();
        assertThat(accountDao.unlockAndSetPassword(userId, "new-password", null)).isFalse();
        assertThat(accountDao.unlockAndSetPassword(userId, "new-password", "")).isFalse();

        assertThat(accountDao.getPasswordResetToken(userId).map(ResetToken::hash)).contains("the-hash");
    }

    @Test
    void changingAPasswordClearsAnOutstandingResetLink() {
        // A password change by any route must invalidate a pending reset link, which is what stops an
        // outstanding link working after the password has been changed some other way.
        final String userId = createAccount(account -> {
        });
        accountDao.setPasswordResetToken(userId, new ResetToken("the-hash", FUTURE_EXPIRY));

        accountDao.changePassword(userId, "a-brand-new-password");

        assertThat(accountDao.getPasswordResetToken(userId)).isEmpty();
    }

    @Test
    void theResetTokenHashAndExpiryAreStoredAndReadBack() {
        final String userId = createAccount(account -> {
        });
        assertThat(accountDao.getPasswordResetToken(userId)).isEmpty();

        accountDao.setPasswordResetToken(userId, new ResetToken("the-hash", 1_234_567L));

        final ResetToken resetToken = accountDao.getPasswordResetToken(userId).orElseThrow();
        assertThat(resetToken.hash()).isEqualTo("the-hash");
        assertThat(resetToken.expiryMs()).isEqualTo(1_234_567L);
    }

    @Test
    void fullLengthHashIsStoredWithoutTruncation() {
        // A base64url SHA-256 hash is 43 characters; the column must hold it whole. The previous
        // varchar(36) would have truncated it, so no real reset link would ever have matched.
        final String userId = createAccount(account -> {
        });
        final String hash = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ";
        assertThat(hash).hasSize(43);

        accountDao.setPasswordResetToken(userId, new ResetToken(hash, FUTURE_EXPIRY));

        assertThat(accountDao.getPasswordResetToken(userId).map(ResetToken::hash)).contains(hash);
        assertThat(accountDao.unlockAndSetPassword(userId, "a-brand-new-password", hash)).isTrue();
    }

    @Test
    void resetEmailIsAllowedOnceThenNotUntilTheCooldownHasPassed() {
        final String userId = createAccount(account -> {
        });
        final long now = System.currentTimeMillis();
        final long oneMinuteAgo = now - 60_000L;

        // Never requested before, so allowed.
        assertThat(accountDao.tryRecordResetEmailRequest(userId, now, oneMinuteAgo)).isTrue();
        // Just requested, so refused.
        assertThat(accountDao.tryRecordResetEmailRequest(userId, now, oneMinuteAgo)).isFalse();
        // Once the last request is older than the cooldown, allowed again.
        assertThat(accountDao.tryRecordResetEmailRequest(userId, now + 120_000L, now + 60_000L)).isTrue();
    }

    @Test
    void resetEmailRequestsAreLimitedPerAccountNotGlobally() {
        final String first = createAccount(account -> {
        });
        final String second = createAccount(account -> {
        });
        final long now = System.currentTimeMillis();
        final long oneMinuteAgo = now - 60_000L;

        assertThat(accountDao.tryRecordResetEmailRequest(first, now, oneMinuteAgo)).isTrue();
        // One user asking must not stop another asking.
        assertThat(accountDao.tryRecordResetEmailRequest(second, now, oneMinuteAgo)).isTrue();
    }

    @Test
    void resetEmailIsNeverAllowedForAnUnknownAccount() {
        final long now = System.currentTimeMillis();

        assertThat(accountDao.tryRecordResetEmailRequest("no-such-user", now, now - 60_000L)).isFalse();
    }

    @Test
    void failedLoginBelowThresholdDoesNotClearAnAdministrativeLock() {
        // An administrator has locked this account; its failure counter is 0.
        final String userId = createAccount(account -> account.setLocked(true));

        // A single wrong password is below the lock threshold (3), but must not clear the lock.
        final boolean lockedAfterFailure = accountDao.incrementLoginFailures(userId);

        assertThat(lockedAfterFailure).isTrue();
        assertThat(accountDao.get(userId).orElseThrow().isLocked()).isTrue();
    }

    @Test
    void reachingTheFailureThresholdLocksTheAccount() {
        final String userId = createAccount(account -> {
        });

        // Below the threshold of 3 the account stays unlocked.
        assertThat(accountDao.incrementLoginFailures(userId)).isFalse();
        assertThat(accountDao.incrementLoginFailures(userId)).isFalse();
        assertThat(accountDao.get(userId).orElseThrow().isLocked()).isFalse();

        // The third failure reaches the threshold and locks it.
        assertThat(accountDao.incrementLoginFailures(userId)).isTrue();
        assertThat(accountDao.get(userId).orElseThrow().isLocked()).isTrue();
    }

    @Test
    void issuingAResetTokenReplacesTheOutstandingHash() {
        // Only the most recently issued reset token may work, so the hash must be overwritten.
        final String userId = createAccount(account -> {
        });

        accountDao.setPasswordResetToken(userId, new ResetToken("first", FUTURE_EXPIRY));
        assertThat(accountDao.getPasswordResetToken(userId).map(ResetToken::hash)).contains("first");

        accountDao.setPasswordResetToken(userId, new ResetToken("second", FUTURE_EXPIRY));
        assertThat(accountDao.getPasswordResetToken(userId).map(ResetToken::hash)).contains("second");
    }

    @Test
    void settingAPasswordConsumesTheOutstandingHash() {
        // Clearing the hash is what stops a reset token being used a second time.
        final String userId = createAccount(account -> {
        });
        accountDao.setPasswordResetToken(userId, new ResetToken("the-hash", FUTURE_EXPIRY));

        assertThat(accountDao.unlockAndSetPassword(userId, "a-brand-new-password", "the-hash")).isTrue();

        assertThat(accountDao.getPasswordResetToken(userId)).isEmpty();
    }

    @Test
    void getPasswordResetTokenIsEmptyWhenNoneIsOutstanding() {
        final String userId = createAccount(account -> {
        });

        assertThat(accountDao.getPasswordResetToken(userId)).isEmpty();
        assertThat(accountDao.getPasswordResetToken("no-such-user")).isEmpty();
    }

    @Test
    void accountsCanBeFoundByEmailAddress() {
        // 'Forgot password' asks the user for their email address, so it has to find them by it.
        final String userId = createAccount(account -> {
        });
        final String email = accountDao.get(userId).orElseThrow().getEmail();

        assertThat(accountDao.getByEmail(email)).isPresent();
        assertThat(accountDao.getByEmail(email).orElseThrow().getUserId()).isEqualTo(userId);
    }

    @Test
    void twoAccountsCannotShareAnEmailAddress() {
        // The unique index is what lets an email address identify one account for 'forgot password'.
        final String userId = createAccount(account -> {
        });
        final String email = accountDao.get(userId).orElseThrow().getEmail();

        // Callers check for a clash first, but two of them can pass that check at once, so the index has
        // to be able to explain itself rather than surfacing a raw 'Duplicate entry' message.
        assertThatThrownBy(() -> createAccount(account -> account.setEmail(email)))
                .hasMessageContaining(email)
                .hasMessageContaining("already used by another account");
    }

    @Test
    void twoAccountsCannotShareAUserId() {
        final String userId = createAccount(account -> {
        });

        // Only the user id clashes; the helper gives this account its own email address.
        assertThatThrownBy(() -> createAccount(account -> account.setUserId(userId)))
                .hasMessageContaining(userId)
                .hasMessageContaining("already used by another account");
    }

    @Test
    void accountCannotBeUpdatedToAnEmailAddressAnotherAccountHas() {
        final String firstUserId = createAccount(account -> {
        });
        final String takenEmail = accountDao.get(firstUserId).orElseThrow().getEmail();
        final String secondUserId = createAccount(account -> {
        });

        final Account second = accountDao.get(secondUserId).orElseThrow();
        second.setEmail(takenEmail);

        assertThatThrownBy(() -> accountDao.update(second))
                .hasMessageContaining(takenEmail)
                .hasMessageContaining("already used by another account");
    }

    @Test
    void updatingFromAStaleCopyOfAnAccountIsRejected() {
        // The version column gives optimistic locking, so an edit made against a copy of the account that
        // someone else has since changed is refused rather than silently overwriting them. Nothing else
        // covers this, so it would be easy to drop by accident.
        final String userId = createAccount(account -> {
        });
        final Account first = accountDao.get(userId).orElseThrow();
        final Account stale = accountDao.get(userId).orElseThrow();

        first.setFirstName("First");
        accountDao.update(first);

        stale.setFirstName("Second");
        assertThatThrownBy(() -> accountDao.update(stale))
                .isInstanceOf(DataChangedException.class);

        assertThat(accountDao.get(userId).orElseThrow().getFirstName()).isEqualTo("First");
    }

    @Test
    void accountCanBeUpdatedKeepingItsOwnEmailAddress() {
        // The account being updated must not be treated as clashing with itself.
        final String userId = createAccount(account -> {
        });
        final Account account = accountDao.get(userId).orElseThrow();
        account.setFirstName("Joe");

        accountDao.update(account);

        assertThat(accountDao.get(userId).orElseThrow().getFirstName()).isEqualTo("Joe");
    }

    @Test
    void anyNumberOfAccountsMayHaveNoEmailAddress() {
        // A unique index permits any number of nulls, which is what allows accounts with no email
        // address, e.g. the seeded admin account.
        createAccount(account -> account.setEmail(null));
        createAccount(account -> account.setEmail(null));
    }

    @Test
    void getByEmailIsEmptyForAnUnknownOrBlankAddress() {
        assertThat(accountDao.getByEmail("nobody@example.com")).isEmpty();
        assertThat(accountDao.getByEmail("")).isEmpty();
        assertThat(accountDao.getByEmail(null)).isEmpty();
    }

    @Test
    void accountWithNoEmailAddressCannotBeFoundByOne() {
        // Accounts may have no email address, e.g. the seeded admin account. They simply cannot be reset
        // by email. A unique index permits any number of nulls, so this must not match them.
        createAccount(account -> account.setEmail(null));

        assertThat(accountDao.getByEmail(null)).isEmpty();
        assertThat(accountDao.getByEmail("")).isEmpty();
    }

    @Test
    void getPasswordLastChangedMsIsEmptyForAnUnknownAccount() {
        assertThat(accountDao.getPasswordLastChangedMs("no-such-user")).isEmpty();
    }

    private String passwordHash(final String userId) {
        // The hash is not exposed on Account, so compare via validateCredentials instead.
        return String.valueOf(accountDao.validateCredentials(userId, "the-original-password")
                .isValidCredentials());
    }

    private String createAccount(final java.util.function.Consumer<Account> mutator) {
        final String userId = UUID.randomUUID().toString();
        final Account account = new Account();
        account.setUserId(userId);
        account.setEmail(userId + "@example.com");
        account.setCreateTimeMs(System.currentTimeMillis());
        account.setCreateUser("test");
        account.setUpdateTimeMs(System.currentTimeMillis());
        account.setUpdateUser("test");
        account.setLoginCount(0);
        account.setEnabled(true);
        mutator.accept(account);
        accountDao.create(account, "the-original-password");
        return userId;
    }


    // --------------------------------------------------------------------------------


    private static class TestModule extends AbstractModule {

        @Override
        protected void configure() {
            install(new IdentityDbModule());
            install(new DbTestModule());
            // Only the account DAO is under test, so bind it directly rather than installing
            // IdentityDaoModule, which would drag in the JWK and OpenID client DAOs too.
            install(new MockCollectionModule());
            install(new MockWordListProviderModule());
            install(new MockDocRefInfoModule());
            bind(SecurityContext.class).to(MockSecurityContext.class);
            bind(AccountDao.class).to(AccountDaoImpl.class);
            bind(IdentityConfig.class).toInstance(new IdentityConfig());
        }
    }
}
