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

package stroom.security.identity.account;

import stroom.security.identity.authenticate.CredentialValidationResult;
import stroom.security.identity.exceptions.NoSuchUserException;
import stroom.security.identity.shared.Account;
import stroom.security.identity.shared.AccountResultPage;
import stroom.security.identity.shared.FindAccountRequest;
import stroom.util.shared.ResultPage;

import java.time.Duration;
import java.util.Optional;

/**
 * The user accounts of the internal identity provider. Only used when stroom is its own identity
 * provider; an external IDP holds its own users and none of this applies.
 * <p>
 * Note that an account is not a stroom user. Accounts hold the credentials used to sign in, while
 * permissions hang off the stroom user of the same subject id.
 * </p>
 */
public interface AccountDao {

    /**
     * @return Every account, ordered by user id. Processing accounts are not included.
     */
    AccountResultPage list();

    /**
     * @return The accounts matching the request's expression, sorted and paged as it asks. Processing
     * accounts are never matched.
     */
    ResultPage<Account> search(FindAccountRequest request);

    /**
     * Create an account with this password.
     *
     * @throws RuntimeException If the user id, or the email address if there is one, is already used by
     *                          another account.
     */
    Account create(Account account, String password);

    /**
     * @return The account with this user id, or empty if there is no such account.
     */
    Optional<Account> get(String userId);

    /**
     * Find the account with this email address.
     * <p>
     * Email addresses are unique among accounts that have one, but an account may have no email address
     * at all, so this never matches on a null or blank address. Returns empty rather than guessing if
     * more than one account somehow shares the address.
     * </p>
     */
    Optional<Account> getByEmail(String email);

    /**
     * @return The account with this database id, or empty if there is no such account.
     */
    Optional<Account> get(int id);

    /**
     * Update an account from the fields carried on {@link Account}.
     * <p>
     * The password and the password reset state are not part of {@link Account}, so they are untouched.
     * Use {@link #changePassword(String, String)} to set a password.
     * </p>
     */
    void update(Account account);

    /**
     * Permanently delete an account, so its credentials can no longer be used to sign in. Does nothing
     * if there is no such account.
     * <p>
     * The stroom user of the same subject id is left behind, along with its group memberships and
     * permissions, so that content the user created still resolves to a name and so that deleting an
     * account cannot quietly discard a permission set. Removing the user is a separate job.
     * </p>
     */
    void delete(int id);

    /**
     * Record that the user signed in: clears the login failure count, increments the login count and sets
     * the last login time.
     * <p>
     * Setting the last login time is also what stops the {@code Account Maintenance} job making the
     * account inactive, so a caller that has just reactivated an account must call this.
     * </p>
     */
    void recordSuccessfulLogin(String userId);

    /**
     * Clear the inactive state of an account, leaving all other account state alone.
     * Unlike {@link #resetPassword(String, String)} this must not clear the locked state,
     * enable a disabled account or change the password.
     */
    void reactivateAccount(String userId);

    /**
     * Check a password and report the state of the account it belongs to.
     * <p>
     * This only reports; it neither counts the failure nor locks the account, see
     * {@link #incrementLoginFailures(String)}. An account that does not exist is reported as invalid
     * credentials rather than as missing, so that a caller cannot tell the two apart.
     * </p>
     */
    CredentialValidationResult validateCredentials(String username, String password);

    /**
     * Count a failed sign in attempt against the account, locking it once the failures reach
     * {@code failedLoginLockThreshold}. Does nothing but count if no threshold is configured.
     *
     * @return True if the account is now locked.
     */
    boolean incrementLoginFailures(String userId);

    /**
     * Set a new password for a user who is signed in and has supplied their current one. Records when the
     * password was changed and clears any requirement to change it.
     * <p>
     * Deliberately does not change the locked, inactive or enabled state.
     * </p>
     *
     * @throws NoSuchUserException If there is no such account.
     */
    void changePassword(String userId, String newPassword);

    /**
     * Set a new password and clear everything stopping the user signing in: the locked state, the
     * inactive state, the login failure count and the disabled state.
     * <p>
     * This is the administrative reset behind the {@code reset_password} command, for getting an account
     * back irrespective of how it got stuck. It is deliberately broader than anything a user may do to
     * their own account, so it is not the one to use for self service password resets, see
     * {@link #unlockAndSetPassword(String, String, String)}.
     * </p>
     *
     * @throws NoSuchUserException If there is no such account.
     */
    void resetPassword(String userId, String newPassword);

    /**
     * Set a new password for an account and clear the locked state and login failure count, for a user
     * who has proved control of the account's email address by following a password reset link. Also
     * consumes the outstanding reset token so the link cannot be used again.
     * <p>
     * Unlike {@link #resetPassword(String, String)} this must leave the inactive state alone. Only a
     * successful authentication may clear that, see {@link #reactivateAccount(String)}.
     * </p>
     *
     * @param expectedTokenHash The reset token hash the caller believes is outstanding. The password is
     *                          only set if it is still the one held against the account, so that of two
     *                          requests using the same link only the first does anything.
     * @return True if the password was set, false if the account no longer has this token outstanding,
     * i.e. the link has already been used or a newer one has been issued.
     */
    boolean unlockAndSetPassword(String userId, String newPassword, String expectedTokenHash);

    /**
     * @return The time the account's password was last changed, falling back to the time the account was
     * created for an account whose password has never been changed. Empty if there is no such account.
     */
    Optional<Long> getPasswordLastChangedMs(String userId);

    /**
     * Record that a password reset email is being sent for this account, but only if the last one was
     * requested at or before {@code earliestPreviousRequestMs}.
     * <p>
     * This is a single conditional update rather than a read followed by a write, so of several requests
     * arriving at once only one is allowed through, on any node.
     * </p>
     *
     * @return True if a reset email may be sent, false if one was sent too recently.
     */
    boolean tryRecordResetEmailRequest(String userId, long requestTimeMs, long earliestPreviousRequestMs);

    /**
     * Record the password reset token just issued for this account (its hash and expiry), replacing any
     * previous one so that only the most recently issued link is accepted.
     */
    void setPasswordResetToken(String userId, ResetToken resetToken);

    /**
     * @return The password reset token outstanding for this account (its hash and expiry), or empty if
     * there is no such account or no reset token is outstanding for it.
     */
    Optional<ResetToken> getPasswordResetToken(String userId);

    /**
     * Whether the user must change their password before they can use stroom, because it is older than
     * {@code mandatoryPasswordChangeDuration}, because an administrator has demanded it, or because they
     * have never changed it and {@code forcePasswordChangeOnFirstLogin} is set.
     *
     * @throws NoSuchUserException If there is no such account.
     */
    boolean needsPasswordChange(String userId,
                                Duration mandatoryPasswordChangeDuration,
                                boolean forcePasswordChangeOnFirstLogin);

    /**
     * Make inactive any account that has never been used and was created longer ago than the threshold.
     * Accounts that never expire, and ones reactivated within the threshold, are left alone.
     * <p>
     * Run by the {@code Account Maintenance} job. See {@link #deactivateInactiveUsers(Duration)} for
     * accounts that have been used at least once.
     * </p>
     *
     * @return How many accounts were made inactive.
     */
    int deactivateNewInactiveUsers(Duration neverUsedAccountDeactivationThreshold);

    /**
     * Make inactive any account that has been used, but not since the threshold. Accounts that never
     * expire, and ones reactivated within the threshold, are left alone.
     * <p>
     * Run by the {@code Account Maintenance} job. See {@link #deactivateNewInactiveUsers(Duration)} for
     * accounts that have never been used.
     * </p>
     *
     * @return How many accounts were made inactive.
     */
    int deactivateInactiveUsers(Duration unusedAccountDeactivationThreshold);
}
