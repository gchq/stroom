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

import org.mindrot.jbcrypt.BCrypt;

import java.util.Objects;

final class PasswordHashUtil {

    // A precomputed bcrypt hash, used to spend an equivalent amount of time when no account matches, so the
    // credential check takes the same time whether or not the user id exists. Generated once at class load
    // with the same cost factor as real hashes (the gensalt default), so it tracks that cost.
    private static final String DUMMY_HASH = BCrypt.hashpw("timing-equaliser", BCrypt.gensalt());

    private PasswordHashUtil() {
    }

    static String hash(final String password) {
        Objects.requireNonNull(password, "Null password");
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    static boolean checkPassword(final String password, final String passwordHash) {
        if (passwordHash == null) {
            return false;
        } else {
            return BCrypt.checkpw(password, passwordHash);
        }
    }

    /**
     * Verify the password against a fixed dummy hash and discard the result, purely to spend the same time a
     * real bcrypt verify would. Called when no account matches, so the credential check takes the same time
     * whether or not the user id exists.
     */
    static void fakeCheck(final String password) {
        checkPassword(password, DUMMY_HASH);
    }
}
