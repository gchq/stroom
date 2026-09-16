/*
 * Copyright 2017 Crown Copyright
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

package stroom.proxy.repo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CSVFormatter {

    private static final String COMMA = ",";
    private static final String QUOTE = "\"";
    private static final String ESCAPED_DOUBLE_QUOTE = "\"\"";
    private static final String EQUALS = "=";
    private static final String ESCAPED_EQUALS = "\\=";
    private static final String ESCAPED_COMMA = "\\,";

    public static String format(final Map<String, String> map, final boolean sortByKey) {
        final List<String> keys = new ArrayList<>(map.keySet());
        if (sortByKey) {
            Collections.sort(keys);
        }

        final StringBuilder sb = new StringBuilder();
        for (final String key : keys) {
            final String value = map.get(key);

            sb.append(QUOTE);
            sb.append(escape(key));
            sb.append(EQUALS);
            sb.append(escape(value));
            sb.append(QUOTE);
            sb.append(COMMA);
        }

        if (!sb.isEmpty()) {
            sb.setLength(sb.length() - 1);
        }

        return sb.toString();
    }

    /**
     * The comma escape is the one that matters. {@code LogStream} builds each
     * receive-log line with {@code String.join(",", …)} over fields escaped by this method, and those
     * top-level fields are <em>not</em> quoted - so a comma in a URL, a receipt id or an error message
     * shifted every column after it, including the attribute map at the end. An audit log that
     * silently changes shape when the data contains a comma is worse than no audit log, because it
     * still parses.
     * <p>
     * Escaped as {@code \,} to match the {@code \=} convention already in this class rather than by
     * quoting the field, which would change the shape of every line to fix the lines that are wrong.
     * Commas inside a {@code key=value} pair were already contained by the quotes {@code format} puts
     * around each pair; they are now escaped as well, so one rule holds everywhere.
     * </p>
     */
    public static String escape(final String value) {
        if (value == null) {
            return "";
        } else {
            return value.replace("\"", ESCAPED_DOUBLE_QUOTE)
                    .replace("=", ESCAPED_EQUALS)
                    .replace(",", ESCAPED_COMMA);
        }
    }
}
