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
        } else {
            return value.replace("\"", ESCAPED_DOUBLE_QUOTE)
                    .replace("=", ESCAPED_EQUALS);
        }
    }
}
