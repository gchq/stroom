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

package stroom.ai.impl.tools.sample;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Records of an aggregated feed whose source type is held inside a string field (seen in external testing,
/// 2026-11-17): a wrapper's `event` holding escaped JSON with `logger_name` in some records, key=value pairs with
/// `logger_name=` in others, XML naming its logger in an attribute in others, and plain text in the rest.
final class HeldFixtures {

    private HeldFixtures() {
        // Static only.
    }

    /// @param n How many records.
    /// @return The records, parsed as the survey parses JSON lines (flattened), a sixth each of: JSON from
    /// `com.x.Auth` (two kinds) twice, JSON from `com.x.Db`, key=value from `com.x.Db` or `com.x.Mail`, XML from
    /// `com.x.Xml` (two kinds), and plain text.
    static List<Map<String, Object>> wrapped(final int n) {
        final List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final Map<String, Object> record = new LinkedHashMap<>();
            record.put("system", "Teams");
            record.put("host", "h" + i % 3);
            record.put("event", held(i));
            out.add(record);
        }
        return out;
    }

    /// @param i A record's number.
    /// @return What its `event` holds.
    static String held(final int i) {
        final boolean even = i / 6 % 2 == 0;
        return switch (i % 6) {
            case 0, 1 -> even
                    ? "{\"logger_name\": \"com.x.Auth\", \"action\": \"login\", \"user\": \"u" + i
                      + "\", \"client\": \"10.0.0." + i % 9 + "\"}"
                    : "{\"logger_name\": \"com.x.Auth\", \"action\": \"logout\", \"user\": \"u" + i
                      + "\", \"session\": \"s" + i + "\"}";
            case 2 -> even
                    ? "{\"logger_name\": \"com.x.Db\", \"query\": \"select " + i + "\", \"rows\": " + i + "}"
                    : "{\"logger_name\": \"com.x.Db\", \"query\": \"update " + i + "\", \"table\": \"t" + i % 4
                      + "\"}";
            case 3 -> even
                    ? "logger_name=com.x.Db query=\"select " + i + "\" rows=" + i
                    : "logger_name=com.x.Mail from=a" + i + "@x.org to=b@x.org size=" + i;
            case 4 -> even
                    ? "<Log logger=\"com.x.Xml\"><Op>read</Op><Path>/data/" + i + "</Path></Log>"
                    : "<Log logger=\"com.x.Xml\"><Op>write</Op><Size>" + i + "</Size></Log>";
            default -> "User u" + i + " logged in from the portal after " + i + " tries";
        };
    }
}
