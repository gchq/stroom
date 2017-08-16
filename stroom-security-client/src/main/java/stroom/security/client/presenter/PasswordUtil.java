/*
 * Copyright 2016 Crown Copyright
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

package stroom.security.client.presenter;

/**
 * <p>
 * Utility of password checking.
 * </p>
 */
public final class PasswordUtil {
    private static final int MIN_PASSWORD_LENGTH = 8;

    private PasswordUtil() {
        // NA Utility
    }

    /**
     * @param password Check a password.
     * @return OK or not
     */
    public static boolean isOkPassword(final String password) {
        if (password == null) {
            return false;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return false;
        }
        boolean foundNonLetter = false;
        boolean foundLowerLetter = false;
        boolean foundUpperLetter = false;

        for (int i = 0; i < password.length(); i++) {
            final char c = password.charAt(i);
            if (Character.isLetter(c)) {
                if (Character.isLowerCase(c)) {
                    foundLowerLetter = true;
                } else {
                    foundUpperLetter = true;
                }
            } else {
                foundNonLetter = true;
            }
        }

        return foundNonLetter && foundLowerLetter && foundUpperLetter;
    }
}
