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

    public static String format(final Map<String, String> map) {
        final List<String> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);

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

        if (sb.length() > 0) {
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
