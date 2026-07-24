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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestPasswordValidator {

    @Test
    void weakPasswordsAreRejectedServerSide() {
        // The whole point of the fix: strength is enforced server-side, not just in the browser.
        // 'password' and 'letmein' both score 0 with zxcvbn, so a minimum of 3 must reject them.
        assertThatThrownBy(() -> PasswordValidator.validateStrength("password", 3))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("strength");
        assertThatThrownBy(() -> PasswordValidator.validateStrength("letmein", 3))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void strongPasswordIsAccepted() {
        // A high-entropy value scores 4; it must pass a minimum of 3 without throwing.
        PasswordValidator.validateStrength("9xK$mZ2vqL8pN4wR3", 3);
    }

    @Test
    void minimumOfZeroAcceptsAnything() {
        // Strength 0 is the 'off' setting — any non-null password passes.
        PasswordValidator.validateStrength("password", 0);
    }

    @Test
    void nullPasswordIsRejected() {
        assertThatThrownBy(() -> PasswordValidator.validateStrength(null, 3))
                .isInstanceOf(RuntimeException.class);
    }
}
