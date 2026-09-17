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

package stroom.security.identity.dao;

import stroom.collection.mock.MockCollectionModule;
import stroom.db.util.JooqUtil;
import stroom.dictionary.mock.MockWordListProviderModule;
import stroom.docstore.mock.MockDocFinderModule;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.api.SecurityContext;
import stroom.security.identity.account.AccountDao;
import stroom.security.identity.account.ResetToken;
import stroom.security.identity.authenticate.CredentialValidationResult;
import stroom.security.identity.config.IdentityConfig;
import stroom.security.identity.db.IdentityDbConnProvider;
import stroom.security.identity.db.IdentityDbModule;
import stroom.security.identity.exceptions.NoSuchUserException;
import stroom.security.identity.shared.Account;
import stroom.security.identity.shared.AccountAction;
import stroom.security.identity.shared.AccountChange;
import stroom.security.identity.shared.AccountFields;
import stroom.security.identity.shared.FindAccountRequest;
import stroom.security.mock.MockSecurityContext;
import stroom.test.common.util.db.DbTestModule;
import stroom.util.time.StroomDuration;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static stroom.security.identity.db.jooq.tables.Account.ACCOUNT;

/**
 * Covers the account state that the self service unlock flows depend on. These assertions are the whole
 * point of {@code reactivateAccount} and {@code unlockAndSetPassword} existing separately from
 * {@code resetPassword}, and they cannot be proved with a mocked DAO.
 */
class TestAccountDaoImpl {

    // Matches what TestModule binds, so tests can reason about when a lock lapses.
    private static final StroomDuration LOCK_DURATION = StroomDuration.ofMinutes(30);
    private static final int LOCK_THRESHOLD = 3;

    // A reset link expiry far enough in the future that these tests are never near it.
    private static final long FUTURE_EXPIRY = System.currentTimeMillis() + 3_600_000L;

    @Inject
    private AccountDao accountDao;
    @Inject
    private IdentityDbConnProvider identityDbConnProvider;

    @BeforeEach
    void setUp() {
        Guice.createInjector(new TestModule(LOCK_DURATION)).injectMembers(this);
    }

    @Test
    void reactivateAccountClearsInactiveAndNothingElse() {
        final String userId = createAccount(account -> {
            account.setInactive(true);
            account.setFailureLockedMs(System.currentTimeMillis());
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
            account.setFailureLockedMs(System.currentTimeMillis());
            account.setInactive(true);
        });
        final String passwordHashBefore = passwordHash(userId);

        accountDao.setPasswordResetToken(userId, new ResetToken("the-hash", FUTURE_EXPIRY));

        assertThat(accountDao.unlockAndSetPassword(userId, "a-brand-new-password", "the-hash")).isTrue();

        final Account account = accountDao.get(userId).orElseThrow();
        assertThat(account.isLocked()).isFalse();
        assertThat(account.getFailureCount()).isZero();
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
    void lapsedLockReportsUnlockedWithoutWaitingForASignInAttempt() {
        // The lock is released lazily, on the next sign in attempt, so the stored flag outlives the lock. An
        // account whose window has passed would be admitted immediately, and must not be reported as locked
        // in the meantime - which the accounts screen used to do.
        final String userId = createAccount(account -> {
        });
        lockThroughFailedLogins(userId);
        assertThat(accountDao.get(userId).orElseThrow().isLocked())
                .as("still within its window")
                .isTrue();

        // Move the expiry into the past, as the passage of time would. Nothing else happens: no sign in is
        // attempted, so the lazy auto-unlock has had no opportunity to run.
        expireTheLock(userId);

        final Account reread = accountDao.get(userId).orElseThrow();
        assertThat(reread.isLocked())
                .as("a lock whose window has passed is not in force")
                .isFalse();
        assertThat(reread.getFailureLockedMs())
                .as("but the lock time is untouched until something clears it")
                .isNotNull();
    }

    @Test
    void theLockedFilterAgreesWithTheDerivedState() {
        // Filtering happens in the database and the column is rendered from the mapped account, so the two
        // apply the rule in different places. If the filter used the stored flag it would return accounts
        // the screen shows as unlocked, which is the failure this is here to catch.
        final String inForce = createAccount(account -> {
        });
        lockThroughFailedLogins(inForce);
        final String lapsed = createAccount(account -> {
        });
        lockThroughFailedLogins(lapsed);
        expireTheLock(lapsed);

        final List<String> locked = searchUserIds(true);
        assertThat(locked).contains(inForce);
        assertThat(locked)
                .as("a lock whose window has passed must not be returned as locked")
                .doesNotContain(lapsed);

        final List<String> notLocked = searchUserIds(false);
        assertThat(notLocked).contains(lapsed);
        assertThat(notLocked).doesNotContain(inForce);
    }

    @Test
    void lockedAccountIsRefusedIdenticallyWhateverThePassword() {
        // The refusal must not say which password was right. Failures are deliberately not counted while an
        // account is locked, so a check that answered here would be one an attacker could run for free -
        // making locking the account the way to guess at it.
        final String userId = createAccount(account -> {
        });
        lockThroughFailedLogins(userId);

        final CredentialValidationResult withRight =
                accountDao.validateCredentials(userId, "the-original-password");
        final CredentialValidationResult withWrong =
                accountDao.validateCredentials(userId, "not-the-password");

        assertThat(withRight.isValidCredentials())
                .as("a locked account must not report that the password was right")
                .isFalse();
        assertThat(withRight.isLocked()).isTrue();
        assertThat(withRight.toString())
                .as("and the message must be the same either way, since it reaches the caller")
                .isEqualTo(withWrong.toString());
    }

    @Test
    void disabledAccountIsRefusedIdenticallyWhateverThePassword() {
        final String userId = createAccount(account -> account.setEnabled(false));

        final CredentialValidationResult withRight =
                accountDao.validateCredentials(userId, "the-original-password");
        final CredentialValidationResult withWrong =
                accountDao.validateCredentials(userId, "not-the-password");

        assertThat(withRight.isValidCredentials()).isFalse();
        assertThat(withRight.isDisabled()).isTrue();
        assertThat(withRight.toString()).isEqualTo(withWrong.toString());
    }

    @Test
    void inactiveAccountStillHasItsPasswordChecked() {
        // Unlike the other two, being inactive is talked out of with a correct password: that is what earns
        // reactivation, so the check has to happen and its answer has to be honest.
        final String userId = createAccount(account -> account.setInactive(true));

        final CredentialValidationResult result =
                accountDao.validateCredentials(userId, "the-original-password");

        assertThat(result.isValidCredentials()).isTrue();
        assertThat(result.isInactive()).isTrue();
    }

    @Test
    void validateCredentialsReleasesALockThatHasRunItsCourse() {
        // The mechanism, as opposed to how the state reads. A lockout has to release itself, or it is a way
        // to deny someone their account. Nothing else clears a lock without an administrator, so if this
        // stops working the lockout quietly becomes permanent while every derived value still looks right.
        final String userId = createAccount(account -> {
        });
        lockThroughFailedLogins(userId);
        expireTheLock(userId);

        final CredentialValidationResult result =
                accountDao.validateCredentials(userId, "the-original-password");

        assertThat(result.isLocked())
                .as("a lock that has run its course must not bar the sign in that finds it")
                .isFalse();
        assertThat(result.isValidCredentials()).isTrue();
        assertThat(accountDao.get(userId).orElseThrow().getFailureLockedMs())
                .as("and the row itself must be cleared, not merely read as unlocked")
                .isNull();
        assertThat(accountDao.get(userId).orElseThrow().getFailureCount())
                .as("with the count cleared too, or the next failure re-locks immediately")
                .isZero();
    }

    @Test
    void validateCredentialsLeavesALockAloneWhenNoLockLapses() {
        // With a zero duration nothing releases itself, so the same call must leave the lock standing.
        final AccountDao noAutoUnlock = daoWithLockDuration(StroomDuration.ZERO);
        final String userId = createAccount(account -> {
        });
        lockThroughFailedLogins(userId);
        expireTheLock(userId);

        final CredentialValidationResult result =
                noAutoUnlock.validateCredentials(userId, "the-original-password");

        assertThat(result.isLocked()).isTrue();
        assertThat(noAutoUnlock.get(userId).orElseThrow().getFailureLockedMs()).isNotNull();
    }

    @Test
    void signInAfterTheLockLapsesDoesNotLeaveTheAccountLocked() {
        // A successful sign in must leave no trace of the lock it was admitted past. Clearing only part of
        // the lock state would once have turned a lock that had already lapsed into a permanent one, which
        // a certificate sign in could reach: it is admitted after the window has passed without the lazy
        // auto-unlock having run.
        final String userId = createAccount(account -> {
        });
        lockThroughFailedLogins(userId);
        expireTheLock(userId);

        accountDao.recordSuccessfulLogin(userId);

        final Account reread = accountDao.get(userId).orElseThrow();
        assertThat(reread.isLocked())
                .as("signing in must not lock the account it just admitted")
                .isFalse();
        assertThat(reread.getFailureLockedMs())
                .as("the lock time has to go, since it is the only thing saying a lock exists")
                .isNull();
    }

    @Test
    void lockDoesNotLapseWhenTheDurationIsZero() {
        // A zero duration means a lock stays until somebody clears it. The account carries no end time,
        // which is how the shared object already says "this does not lapse", so nothing outside the DAO
        // has to know about the setting.
        final AccountDao noAutoUnlock = daoWithLockDuration(StroomDuration.ZERO);
        final String userId = createAccount(account -> {
        });
        lockThroughFailedLogins(userId);

        final Account account = noAutoUnlock.get(userId).orElseThrow();
        assertThat(account.getFailureLockedUntilMs()).isNull();
        assertThat(account.isLocked()).isTrue();
    }

    @Test
    void shorteningTheDurationReleasesALockAlreadyInForce() {
        // The point of holding when the lock was applied rather than when it was due to end: the setting
        // governs locks that already exist, not just new ones.
        final String userId = createAccount(account -> {
        });
        lockThroughFailedLogins(userId);
        assertThat(accountDao.get(userId).orElseThrow().isLocked()).isTrue();

        final AccountDao muchShorter = daoWithLockDuration(StroomDuration.ofMillis(1));

        assertThat(muchShorter.get(userId).orElseThrow().isLocked())
                .as("a lock applied under a longer duration must respect the shorter one")
                .isFalse();
    }

    @Test
    void changeCannotRevertALockoutItNeverAskedAbout() {
        // The race that made a whole-object save unsafe: an administrator opens the account editor, an
        // attacker trips the lockout, and the administrator saves an unrelated edit. A change carries only
        // what was edited, so there is no LOCKED value in it to write and the lockout cannot be undone.
        final String userId = createAccount(account -> {
        });

        // The attacker locks the account after the editor was opened.
        lockThroughFailedLogins(userId);

        applyChange(userId, AccountChange.builder().comments("an unrelated edit"));

        final Account reread = accountDao.get(userId).orElseThrow();
        assertThat(reread.isLocked())
                .as("the lockout must still stand")
                .isTrue();
        assertThat(reread.getComments())
                .as("and the edit that was actually made must still be applied")
                .isEqualTo("an unrelated edit");
    }

    @Test
    void repeatedLoginFailuresDoNotStopAnAdministratorDisablingTheAccount() {
        // Somebody guessing at an account drives writes to it as fast as they like. An administrator's change
        // must still apply, or the disable control would be denied exactly when it is wanted.
        final String userId = createAccount(account -> {
        });
        accountDao.incrementLoginFailures(userId);
        accountDao.incrementLoginFailures(userId);

        applyChange(userId, AccountChange.builder().action(AccountAction.DISABLE));

        assertThat(accountDao.get(userId).orElseThrow().isEnabled()).isFalse();
    }

    @Test
    void successfulLoginDoesNotStopAnAdministratorSaving() {
        // The same for an account that authenticates constantly, such as one used by an automated client.
        final String userId = createAccount(account -> {
        });
        accountDao.recordSuccessfulLogin(userId);

        applyChange(userId, AccountChange.builder().comments("edited while the user was logging in"));

        assertThat(accountDao.get(userId).orElseThrow().getComments())
                .isEqualTo("edited while the user was logging in");
    }

    @Test
    void changeNeverRewritesLoginTelemetry() {
        // Login telemetry is not something a change can carry at all, which is what makes it impossible for a
        // save to undo a real login. A stale LAST_LOGIN_MS is what the account maintenance job measures
        // inactivity from, so restoring an old one could deactivate a live account.
        final String userId = createAccount(account -> {
        });
        accountDao.recordSuccessfulLogin(userId);
        final Account afterLogin = accountDao.get(userId).orElseThrow();

        applyChange(userId, AccountChange.builder().comments("an unrelated edit"));

        final Account reread = accountDao.get(userId).orElseThrow();
        assertThat(reread.getLoginCount()).isEqualTo(afterLogin.getLoginCount());
        assertThat(reread.getLastLoginMs()).isEqualTo(afterLogin.getLastLoginMs());
    }

    @Test
    void unlockingClearsTheCountSoTheNextFailureDoesNotRelock() {
        // The whole point of the unlock. Left at or above the threshold, the very next wrong password
        // re-locks the account and the administrator's action achieved nothing.
        final String userId = createAccount(account -> {
        });
        lockThroughFailedLogins(userId);

        applyChange(userId, AccountChange.builder().action(AccountAction.UNLOCK));

        assertThat(accountDao.get(userId).orElseThrow().getFailureCount()).isZero();
        final boolean lockedAgain = accountDao.incrementLoginFailures(userId);
        assertThat(lockedAgain)
                .as("one wrong password after an unlock must not re-lock the account")
                .isFalse();
    }

    @Test
    void unlockingDoesNotStampTheReactivationTime() {
        // A lock says nothing about whether an account is being used, so unlocking must not extend the
        // dormancy grace period that reactivation exists to give.
        final String userId = createAccount(account -> {
        });
        lockThroughFailedLogins(userId);

        applyChange(userId, AccountChange.builder().action(AccountAction.UNLOCK));

        assertThat(accountDao.get(userId).orElseThrow().getReactivatedMs()).isNull();
    }

    @Test
    void makingAnAccountActiveStampsTheReactivationTime() {
        // Stamped so the account maintenance job does not immediately make the account inactive again in the
        // window before the next successful login.
        final String userId = createAccount(account -> account.setInactive(true));

        applyChange(userId, AccountChange.builder().action(AccountAction.REACTIVATE));

        final Account reread = accountDao.get(userId).orElseThrow();
        assertThat(reread.isInactive()).isFalse();
        assertThat(reread.getReactivatedMs()).isNotNull();
    }

    @Test
    void furtherWrongPasswordDoesNotReleaseALockAlreadyInForce() {
        // A failed sign in only ever adds a lock, never clears one, so a further attempt against an already
        // locked account cannot let the attacker back in.
        final String userId = createAccount(account -> {
        });
        lockThroughFailedLogins(userId);
        final Long expiryWhenLocked = lockedUntilMs(userId);

        accountDao.incrementLoginFailures(userId);

        assertThat(accountDao.get(userId).orElseThrow().isLocked())
                .as("a lock in force must survive further attempts")
                .isTrue();
        assertThat(lockedUntilMs(userId))
                .as("and continued guessing must not extend it")
                .isEqualTo(expiryWhenLocked);
    }

    @Test
    void anUnrelatedEditDoesNotMakeAFailureLockPermanent() {
        // A failure lock releases itself when its window passes. An administrator editing something else has
        // not asked to change that, so the expiry must survive the save - the previous whole-object update
        // cleared it on every save and silently turned a temporary lock into a permanent one.
        final String userId = createAccount(account -> {
        });
        lockThroughFailedLogins(userId);
        final Long expiryBefore = lockedUntilMs(userId);
        assertThat(expiryBefore).isNotNull();

        applyChange(userId, AccountChange.builder().comments("an unrelated edit"));

        assertThat(lockedUntilMs(userId)).isEqualTo(expiryBefore);
    }

    @Test
    void applyingAChangeToAnAccountThatIsNotThereIsRejected() {
        // The change always writes the version and update time, so an existing account always reports a
        // changed row. A count of zero therefore means the account is genuinely absent, not a no-op save.
        assertThatThrownBy(() -> accountDao.applyChange(
                -1, AccountChange.builder().comments("anything").build(), "test", System.currentTimeMillis()))
                .isInstanceOf(NoSuchUserException.class);
    }

    @Test
    void anEmptyChangeIsHarmless() {
        // Guards the count check above: a change that asks for nothing must still be recognised as having
        // found its account, rather than being reported as a missing one.
        final String userId = createAccount(account -> account.setComments("untouched"));

        applyChange(userId, AccountChange.builder());

        assertThat(accountDao.get(userId).orElseThrow().getComments()).isEqualTo("untouched");
    }

    @Test
    void changeLeavesEverythingItDoesNotMentionAlone() {
        // The property the whole design rests on, asserted directly.
        final String userId = createAccount(account -> {
            account.setFirstName("Original");
            account.setComments("original comments");
        });
        final Account before = accountDao.get(userId).orElseThrow();

        applyChange(userId, AccountChange.builder().firstName("Changed"));

        final Account reread = accountDao.get(userId).orElseThrow();
        assertThat(reread.getFirstName()).isEqualTo("Changed");
        assertThat(reread.getComments()).isEqualTo(before.getComments());
        assertThat(reread.getEmail()).isEqualTo(before.getEmail());
        assertThat(reread.isEnabled()).isEqualTo(before.isEnabled());
        assertThat(reread.isLocked()).isEqualTo(before.isLocked());
    }

    @Test
    void anAccountCreatedWithABlankEmailHasNoEmail() {
        // Not a presentational nicety: an empty string is a value under the unique index, so storing one
        // would make the next account created without an address a duplicate of this one.
        final String userId = createAccount(account -> account.setEmail(""));

        assertThat(accountDao.get(userId).orElseThrow().getEmail()).isNull();
    }

    @Test
    void anAccountCreatedWithAWhitespaceEmailHasNoEmail() {
        final String userId = createAccount(account -> account.setEmail("   "));

        assertThat(accountDao.get(userId).orElseThrow().getEmail()).isNull();
    }

    @Test
    void clearingAnEmailAddressLeavesTheAccountWithNoEmail() {
        // A change that mentions the email as blank is the administrator emptying the field, so the address
        // does have to be written - as no address, rather than as an empty string.
        final String userId = createAccount(account -> account.setEmail("someone@example.com"));

        applyChange(userId, AccountChange.builder().email(""));

        assertThat(accountDao.get(userId).orElseThrow().getEmail()).isNull();
    }


    @Test
    void changingAPasswordClearsAnOutstandingResetLink() {
        // A password change by any route must invalidate a pending reset link, which is what stops an
        // outstanding link working after the password has been changed some other way.
        final String userId = createAccount(account -> {
        });
        accountDao.setPasswordResetToken(userId, new ResetToken("the-hash", FUTURE_EXPIRY));

        accountDao.changePassword(userId, "a-brand-new-password", false);

        assertThat(accountDao.getPasswordResetToken(userId)).isEmpty();
    }

    @Test
    void changingAPasswordCanLeaveTheRequirementToChangeItStanding() {
        // The administrative case: setting someone's password and requiring them to change it at next sign in
        // is a single save. If the password change cleared the flag, the user would keep operating on the
        // password the administrator just chose - and knows - with nothing ever prompting them to replace it.
        final String userId = createAccount(account -> account.setForcePasswordChange(true));

        accountDao.changePassword(userId, "a-password-the-admin-knows", true);

        assertThat(accountDao.get(userId).orElseThrow().isForcePasswordChange())
                .as("an administrator's request to force a change must survive the password being set")
                .isTrue();
    }

    @Test
    void changingAPasswordCanSatisfyTheRequirementToChangeIt() {
        // The self-service case: the user has just changed their own password, so the requirement is met.
        final String userId = createAccount(account -> account.setForcePasswordChange(true));

        accountDao.changePassword(userId, "a-password-only-the-user-knows", false);

        assertThat(accountDao.get(userId).orElseThrow().isForcePasswordChange()).isFalse();
    }

    @Test
    void changingAPasswordAlwaysRecordsWhenItChanged() {
        // Independent of the force-change flag - this is what the password-age policy is measured from.
        final String userId = createAccount(account -> account.setForcePasswordChange(true));

        accountDao.changePassword(userId, "a-brand-new-password", true);

        assertThat(accountDao.getPasswordLastChangedMs(userId)).isPresent();
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
    void failedLoginBelowThresholdDoesNotClearAnExistingLock() {
        // A failed sign in may only ever add a lock, never clear one. Named for that property rather than
        // for how the lock is stored, having twice been renamed when the storage changed. This account is
        // already locked and its failure counter is 0.
        final String userId = createAccount(account -> account.setFailureLockedMs(System.currentTimeMillis()));

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

        final int secondAccountId = accountDao.get(secondUserId).orElseThrow().getId();

        assertThatThrownBy(() -> accountDao.applyChange(
                secondAccountId, AccountChange.builder().email(takenEmail).build(), "test", 0L))
                .hasMessageContaining(takenEmail)
                .hasMessageContaining("already used by another account");
    }

    @Test
    void twoAdministratorsEditingDifferentThingsBothTakeEffect() {
        // A change is not tested against the account's version, so concurrent edits no longer collide.
        // Editing different fields both apply, which a whole-object save could not manage.
        final String userId = createAccount(account -> {
        });

        applyChange(userId, AccountChange.builder().firstName("First"));
        applyChange(userId, AccountChange.builder().comments("a comment from someone else"));

        final Account reread = accountDao.get(userId).orElseThrow();
        assertThat(reread.getFirstName()).isEqualTo("First");
        assertThat(reread.getComments()).isEqualTo("a comment from someone else");
    }

    @Test
    void accountCanBeUpdatedKeepingItsOwnEmailAddress() {
        // The account being updated must not be treated as clashing with itself.
        final String userId = createAccount(account -> {
        });
        applyChange(userId, AccountChange.builder().firstName("Joe"));

        assertThat(accountDao.get(userId).orElseThrow().getFirstName()).isEqualTo("Joe");
    }

    @Test
    void anyNumberOfAccountsMayHaveNoEmailAddress() {
        // A unique index permits any number of nulls, which is what allows accounts with no email
        // address, e.g. the seeded admin account. Blanks reach the column as nulls, so they must not be
        // treated as accounts that share an address - by any of the routes an address can be left empty.
        createAccount(account -> account.setEmail(null));
        createAccount(account -> account.setEmail(""));
        createAccount(account -> account.setEmail("   "));
        final String cleared = createAccount(account -> account.setEmail("someone@example.com"));

        applyChange(cleared, AccountChange.builder().email(""));
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

    /**
     * Read straight from the table, because the hash is not exposed on the account. Inferring it from
     * validateCredentials does not work: that deliberately refuses to check the password of a locked or
     * disabled account, so it would report no change however the hash moved.
     */
    private String passwordHash(final String userId) {
        return JooqUtil.contextResult(identityDbConnProvider, context -> context
                .select(ACCOUNT.PASSWORD_HASH)
                .from(ACCOUNT)
                .where(ACCOUNT.USER_ID.eq(userId))
                .fetchOne(ACCOUNT.PASSWORD_HASH));
    }

    /**
     * Age an in-force lock past the configured duration, standing in for the passage of time. Written
     * straight to the table because no API moves the lock time - which is the point: nothing has run that
     * could clear the lock, so what follows tests the derived state rather than the auto-unlock.
     */
    private void expireTheLock(final String userId) {
        final long lockedLongEnoughAgo = System.currentTimeMillis() - LOCK_DURATION.toMillis() - 1_000L;
        JooqUtil.context(identityDbConnProvider, context -> context
                .update(ACCOUNT)
                .set(ACCOUNT.FAILURE_LOCKED_MS, lockedLongEnoughAgo)
                .where(ACCOUNT.USER_ID.eq(userId))
                .execute());
    }

    private List<String> searchUserIds(final boolean locked) {
        final FindAccountRequest request = new FindAccountRequest(
                null,
                null,
                ExpressionOperator.builder()
                        .addBooleanTerm(AccountFields.FIELD_LOCKED, Condition.EQUALS, locked)
                        .build());
        return accountDao.search(request)
                .getValues()
                .stream()
                .map(Account::getUserId)
                .toList();
    }

    private Long lockedUntilMs(final String userId) {
        return accountDao.get(userId).orElseThrow().getFailureLockedUntilMs();
    }

    private void applyChange(final String userId, final AccountChange.Builder builder) {
        final int accountId = accountDao.get(userId).orElseThrow().getId();
        accountDao.applyChange(accountId, builder.build(), "test", System.currentTimeMillis());
    }

    /**
     * Trip the failed-login lockout, the way an attacker would.
     */
    private void lockThroughFailedLogins(final String userId) {
        boolean locked = false;
        for (int i = 0; i < 10 && !locked; i++) {
            locked = accountDao.incrementLoginFailures(userId);
        }
        assertThat(locked).as("the account should have locked").isTrue();
    }

    private AccountDao daoWithLockDuration(final StroomDuration lockDuration) {
        return Guice.createInjector(new TestModule(lockDuration)).getInstance(AccountDao.class);
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

        private final StroomDuration lockDuration;

        private TestModule(final StroomDuration lockDuration) {
            this.lockDuration = lockDuration;
        }

        @Override
        protected void configure() {
            install(new IdentityDbModule());
            install(new DbTestModule());
            // Only the account DAO is under test, so bind it directly rather than installing
            // IdentityDaoModule, which would drag in the JWK and OpenID client DAOs too.
            install(new MockCollectionModule());
            install(new MockWordListProviderModule());
            install(new MockDocFinderModule());
            bind(SecurityContext.class).to(MockSecurityContext.class);
            bind(AccountDao.class).to(AccountDaoImpl.class);
            bind(IdentityConfig.class).toInstance(new IdentityConfig(
                    null,
                    null,
                    null,
                    null,
                    // Passed explicitly: unlike the other settings this one has no default applied in the
                    // constructor, and a null threshold disables locking altogether.
                    LOCK_THRESHOLD,
                    lockDuration,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null));
        }
    }
}
