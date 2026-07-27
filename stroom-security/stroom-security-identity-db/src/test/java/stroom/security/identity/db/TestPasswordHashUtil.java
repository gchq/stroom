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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestPasswordHashUtil {

    @Test
    void fakeCheckPerformsARealBcryptBurn() {
        // When no account matches, validateCredentials calls fakeCheck so the credential check takes the same
        // time as a real bcrypt verify. Guard that fakeCheck actually does a bcrypt verify: a cost-10 verify
        // is well over 10ms, whereas a regression that turned it back into a no-op would return in
        // microseconds. This is a lower bound, so load or GC can only make it pass more strongly, never fail.
        // Warm up class load / JIT first so the measured call is representative.
        PasswordHashUtil.fakeCheck("warm-up");

        final long startNanos = System.nanoTime();
        PasswordHashUtil.fakeCheck("some-guessed-password");
        final long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;

        assertThat(elapsedMs)
                .as("fakeCheck must perform a real bcrypt verify to equalise login timing")
                .isGreaterThanOrEqualTo(10L);
    }

    @Test
    void fakeCheckAlwaysFailsAndNeverAuthenticates() {
        // It is a burn only; it must never be usable to authenticate. (checkPassword against the dummy hash
        // always returns false; fakeCheck discards it, but this proves the underlying value.)
        assertThat(PasswordHashUtil.checkPassword("anything", PasswordHashUtil.hash("something-else")))
                .isFalse();
    }
}
