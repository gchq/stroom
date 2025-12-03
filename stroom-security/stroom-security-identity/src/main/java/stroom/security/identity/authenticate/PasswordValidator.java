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

import java.util.Objects;

public class PasswordValidator {

    public static void validateLength(final String newPassword,
                                      final int minimumLength) {
        if (newPassword == null) {
            throw new RuntimeException("Password is null");
        }
        if (newPassword.length() < minimumLength) {
            throw new RuntimeException("Password does not meet the minimum length requirement of " +
                    minimumLength + " characters");
        }
    }

    public static void validateComplexity(final String newPassword,
                                          final String complexityRegex) {
        if (newPassword == null) {
            throw new RuntimeException("Password is null");
        }
        if (!newPassword.matches(complexityRegex)) {
            throw new RuntimeException("Password does not meet the minimum complexity requirements");
        }
    }

    public static void validateConfirmation(final String password, final String confirmationPassword) {
        if (password == null) {
            throw new RuntimeException("Password is null");
        }
        if (!Objects.equals(password, confirmationPassword)) {
            throw new RuntimeException("The confirmation password does not match");
        }
    }

    public static void validateReuse(final String oldPassword,
                                     final String newPassword) {
        if (newPassword == null) {
            throw new RuntimeException("Password is null");
        }
        if (oldPassword.equalsIgnoreCase(newPassword)) {
            throw new RuntimeException("You cannot reuse the previous password");
        }
    }

    public static void validateCredentials(final CredentialValidationResult result) {
        if (!result.isAllOk()) {
            throw new RuntimeException("Invalid credentials");
        }
    }
}
