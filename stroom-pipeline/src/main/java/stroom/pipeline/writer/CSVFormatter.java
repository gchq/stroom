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

package stroom.pipeline.writer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class CSVFormatter {

    private static final String COMMA = ",";
    private static final String QUOTE = "\"";
    private static final String DOUBLE_QUOTE = "\"\"";
    private static final Pattern QUOTE_PATTERN = Pattern.compile(QUOTE);
    private static final String EQUALS = "=";
    private static final String ESCAPED_EQUALS = "\\=";
    private static final Pattern EQUALS_PATTERN = Pattern.compile(EQUALS);

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

    public static String escape(final String value) {
        if (value == null) {
            return "";
        }

        String escaped = value;
        escaped = QUOTE_PATTERN.matcher(escaped).replaceAll(DOUBLE_QUOTE);
        escaped = EQUALS_PATTERN.matcher(escaped).replaceAll(ESCAPED_EQUALS);

        return escaped;
    }
}
