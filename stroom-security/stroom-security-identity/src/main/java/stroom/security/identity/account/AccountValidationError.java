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

import jakarta.validation.constraints.NotNull;

public enum AccountValidationError {
    NO_USER("Please supply a user with an email address and a password. "),
    NO_NAME("User's name cannot be empty. "),
    NO_PASSWORD("User's password cannot be empty. "),
    MISSING_ID("Please supply an ID for the user. "),
    USER_ALREADY_EXISTS("A user with this name already exists. Please try another name. ");

    @NotNull
    private final String message;

    AccountValidationError(@NotNull final String message) {
        this.message = message;
    }

    @NotNull
    public final String getMessage() {
        return this.message;
    }
}
