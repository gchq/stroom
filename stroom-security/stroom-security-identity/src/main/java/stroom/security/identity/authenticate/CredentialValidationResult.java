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

public class CredentialValidationResult {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid credentials";
    private static final String ACCOUNT_LOCKED_MESSAGE = "This account is locked. Please contact your administrator";
    private static final String ACCOUNT_DISABLED_MESSAGE =
            "This account is disabled. Please contact your administrator";
    private static final String ACCOUNT_INACTIVE_MESSAGE =
            "This account is marked as inactive. Please contact your administrator";
    private static final String ACCOUNT_PROCESSING_MESSAGE = "This is a processing account";

    private final boolean validCredentials;
    private final boolean accountDoesNotExist;
    private final boolean locked;
    private final boolean disabled;
    private final boolean inactive;
    private final boolean processingAccount;

    public CredentialValidationResult(final boolean validCredentials,
                                      final boolean accountDoesNotExist,
                                      final boolean locked,
                                      final boolean disabled,
                                      final boolean inactive,
                                      final boolean processingAccount) {
        this.validCredentials = validCredentials;
        this.accountDoesNotExist = accountDoesNotExist;
        this.locked = locked;
        this.disabled = disabled;
        this.inactive = inactive;
        this.processingAccount = processingAccount;
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

    public boolean isProcessingAccount() {
        return processingAccount;
    }

    @Override
    public String toString() {
        if (!validCredentials || accountDoesNotExist) {
            return INVALID_CREDENTIALS_MESSAGE;
        }
        if (locked) {
            return ACCOUNT_LOCKED_MESSAGE;
        }
        if (disabled) {
            return ACCOUNT_DISABLED_MESSAGE;
        }
        if (inactive) {
            return ACCOUNT_INACTIVE_MESSAGE;
        }
        if (processingAccount) {
            return ACCOUNT_PROCESSING_MESSAGE;
        }
        return "";
    }

    public boolean isAllOk() {
        return validCredentials && !accountDoesNotExist && !locked && !disabled && !inactive && !processingAccount;
    }
}
