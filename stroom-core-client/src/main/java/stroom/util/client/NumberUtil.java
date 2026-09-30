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

package stroom.util.client;

/**
 * Utility methods for formatting numbers in a human-readable form.
 *
 * <p>All methods are pure static helpers with no GWT-specific imports, making
 * the class safe for use in both GWT client code and server-side compilation.
 */
public final class NumberUtil {

    private NumberUtil() {
    }

    /**
     * Formats an integer with thousands separators
     * (e.g. {@code 120166} → {@code "120,166"}).
     * Avoids {@code NumberFormat} to remain compatible with both GWT and
     * server-side compilation.
     *
     * @param value the integer to format
     * @return the comma-separated string representation
     */
    public static String formatInt(final int value) {
        if (value == 0) {
            return "0";
        }
        final String raw = Integer.toString(value);
        if (raw.length() <= 3) {
            return raw;
        }
        final StringBuilder sb = new StringBuilder();
        final int mod = raw.length() % 3;
        if (mod > 0) {
            sb.append(raw, 0, mod);
        }
        for (int i = mod; i < raw.length(); i += 3) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(raw, i, i + 3);
        }
        return sb.toString();
    }
}
