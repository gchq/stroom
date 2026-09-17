/*
 * Copyright 2020 Crown Copyright
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

import event.logging.AuthenticateOutcomeReason;

public class CredentialValidationResult {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid credentials";
    private static final String ACCOUNT_LOCKED_MESSAGE = "This account is locked. Please contact your administrator";
    private static final String ACCOUNT_DISABLED_MESSAGE =
            "This account is disabled. Please contact your administrator";
    // Only ever shown after a correct password, since credentials are answered first for this state, so
    // saying why the account was deactivated discloses nothing to anyone but its owner.
    private static final String ACCOUNT_INACTIVE_MESSAGE =
            "This account has been deactivated due to inactivity. Please contact your administrator";

    private final boolean validCredentials;
    private final boolean accountDoesNotExist;
    private final boolean locked;
    private final boolean disabled;
    private final boolean inactive;

    public CredentialValidationResult(final boolean validCredentials,
                                      final boolean accountDoesNotExist,
                                      final boolean locked,
                                      final boolean disabled,
                                      final boolean inactive) {
        this.validCredentials = validCredentials;
        this.accountDoesNotExist = accountDoesNotExist;
        this.locked = locked;
        this.disabled = disabled;
        this.inactive = inactive;
    }

    public boolean isValidCredentials() {
        return validCredentials;
    }

    public boolean isAccountDoesNotExist() {
        return accountDoesNotExist;
    }

    public boolean isLocked() {
        return locked;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public boolean isInactive() {
        return inactive;
    }

    @Override
    public String toString() {
        // State first, credentials second. The other way round, the message answered whether the password
        // was right for an account that was going to be refused either way - and since a locked account
        // does not count failures, that made locking one the way to guess at it for free.
        //
        // Disabled outranks locked, which outranks inactive: an administrator barring the account is a
        // stronger statement than either of the states the system applies on its own.
        if (disabled) {
            return ACCOUNT_DISABLED_MESSAGE;
        }
        if (locked) {
            return ACCOUNT_LOCKED_MESSAGE;
        }
        if (!validCredentials || accountDoesNotExist) {
            return INVALID_CREDENTIALS_MESSAGE;
        }
        if (inactive) {
            return ACCOUNT_INACTIVE_MESSAGE;
        }
        return "";
    }

    /**
     * Why a sign in was refused, for the audit trail.
     * <p>
     * Deliberately more specific than {@link #toString()}, which is what the caller is told. The caller gets
     * one generic answer so that the refusal cannot be used to work out which accounts exist or what state
     * they are in; the audit is read by people entitled to know, and is no use if it records every refusal
     * as a wrong password when a lockout or a disabled account is what actually fired.
     * </p>
     * <p>
     * The order matches {@link #toString()}, so the reason and the message always describe the same thing.
     * </p>
     *
     * @return the reason, or null where nothing was refused.
     */
    public AuthenticateOutcomeReason getOutcomeReason() {
        if (disabled) {
            // No outcome reason describes a disabled account, so the description carries it instead.
            return AuthenticateOutcomeReason.OTHER;
        }
        if (locked) {
            return AuthenticateOutcomeReason.ACCOUNT_LOCKED;
        }
        if (accountDoesNotExist) {
            return AuthenticateOutcomeReason.INCORRECT_USERNAME;
        }
        if (!validCredentials) {
            return AuthenticateOutcomeReason.INCORRECT_PASSWORD;
        }
        if (inactive) {
            return AuthenticateOutcomeReason.OTHER;
        }
        return null;
    }

    public boolean isAllOk() {
        return validCredentials && !accountDoesNotExist && !locked && !disabled && !inactive;
    }
}
