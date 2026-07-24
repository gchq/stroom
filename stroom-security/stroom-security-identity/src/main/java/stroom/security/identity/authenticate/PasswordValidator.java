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

import com.nulabinc.zxcvbn.Zxcvbn;

import java.util.Objects;

public class PasswordValidator {

    // Zxcvbn loads its dictionaries once and measure() is stateless, so a single shared instance is safe
    // and avoids reloading the dictionaries on every password check.
    private static final Zxcvbn ZXCVBN = new Zxcvbn();

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

    /**
     * Enforce password strength on the server using zxcvbn (the same estimator the UI uses), so the
     * advertised strength policy is actually applied and cannot be bypassed by calling the API directly.
     * The score is 0 (weakest) to 4 (strongest); the password is rejected if it scores below the configured
     * minimum. This replaces the old character-class complexity regex, which OWASP discourages in favour of
     * an entropy-based estimate.
     */
    public static void validateStrength(final String newPassword,
                                        final int minimumStrength) {
        if (newPassword == null) {
            throw new RuntimeException("Password is null");
        }
        if (ZXCVBN.measure(newPassword).getScore() < minimumStrength) {
            throw new RuntimeException("Password does not meet the minimum strength requirement");
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
