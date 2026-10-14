/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.impl.workspace;

import java.util.regex.Pattern;

/// Masks the parts of a value that make similar values look different (UUIDs, IP addresses, dates and times, long
/// hex strings and numbers), so that free text such as log messages reduces to a few hundred patterns that can be
/// counted. The patterns used here are fixed and linear, so they are safe to run on any input.
public final class ValueMasker {

    private static final Pattern UUID = Pattern.compile(
            "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");
    private static final Pattern IPV4 = Pattern.compile("\\b\\d{1,3}(\\.\\d{1,3}){3}\\b");
    private static final Pattern DATE_TIME = Pattern.compile(
            "\\b\\d{4}-\\d{2}-\\d{2}([T ]\\d{2}:\\d{2}(:\\d{2}(\\.\\d{1,9})?)?(Z|[+-]\\d{2}:?\\d{2})?)?\\b"
            + "|\\b\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?\\b");
    // At least one letter, so long numbers are numbers.
    private static final Pattern HEX = Pattern.compile("\\b(0x)?(?=[0-9]*[a-fA-F])[0-9a-fA-F]{8,}\\b");
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    private ValueMasker() {
        // Static only.
    }

    /// @param value A value.
    /// @return The value with variable parts masked, e.g. "Login failed for 10.0.0.1 after 3 tries" becomes
    /// "Login failed for <ip> after <n> tries".
    public static String mask(final String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String masked = UUID.matcher(value).replaceAll("<uuid>");
        masked = IPV4.matcher(masked).replaceAll("<ip>");
        masked = DATE_TIME.matcher(masked).replaceAll("<time>");
        masked = HEX.matcher(masked).replaceAll("<hex>");
        return NUMBER.matcher(masked).replaceAll("<n>");
    }
}
