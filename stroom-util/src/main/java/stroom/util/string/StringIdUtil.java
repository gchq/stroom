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

package stroom.util.string;

import stroom.util.logging.LogUtil;

public class StringIdUtil {

    /**
     * Convert id into a zero padded string that has a length that is divisible by three.
     * The length will be the shortest possible to fit the id value.
     * e.g.
     * <ul>
     *    <li>{@code 1 => 001}</li>
     *    <li>{@code 12 => 012}</li>
     *    <li>{@code 123 => 123}</li>
     *    <li>{@code 1234 => 001234}</li>
     *    <li>{@code 12345678 => 012345678}</li>
     * </ul>
     */
    public static String idToString(final long id) {
        if (id < 0) {
            throw new IllegalArgumentException("Negative IDs not supported");
        }
        final String idStr = String.valueOf(id);
        final int remainder = idStr.length() % 3;
        return switch (remainder) {
            case 0 -> idStr; // Length is OK as is
            case 1 -> "00" + idStr;
            case 2 -> "0" + idStr;
            default -> {
                // Should never happen
                throw new IllegalStateException(
                        LogUtil.message("Unexpected remainder {}, id {}, idStr {}, len {}",
                                remainder, id, idStr, idStr.length()));
            }
        };
    }

    public static boolean isValidIdString(final String idString) {
        if (idString == null) {
            return false;
        } else {
            final int len = idString.length();
            if (len >= 3 && len % 3 == 0) {
                for (final char chr : idString.toCharArray()) {
                    if (!Character.isDigit(chr)) {
                        return false;
                    }
                }
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * @return The number of digits when the ID is represented as a string, i.e.
     * when the string has a length that is a multiple of 3.
     * e.g. Returns 3 for ID 1, 6 for ID 1234.
     */
    public static int getDigitCountAsId(final long id) {
        final int baseDigitCount = StringUtil.getDigitCount(id);
        final int mod = baseDigitCount % 3;
        return switch (mod) {
            case 0 -> baseDigitCount;
            case 1 -> baseDigitCount + 2;
            case 2 -> baseDigitCount + 1;
            default -> throw new IllegalStateException("Unexpected value: " + mod);
        };
    }

}
