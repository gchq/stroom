package stroom.dashboard.client.table;

import com.google.gwt.safehtml.shared.SafeHtml;
import stroom.hyperlink.client.Hyperlink;
import stroom.util.shared.Expander;

import java.util.Map;

public class TableRow {
    private final Expander expander;
    private final String groupKey;
    private final Map<String, SafeHtml> values;

    public TableRow(final Expander expander,
                    final String groupKey,
                    final Map<String, SafeHtml> values) {
        this.expander = expander;
        this.groupKey = groupKey;
        this.values = values;
    }

    public Expander getExpander() {
        return expander;
    }

    public String getGroupKey() {
        return groupKey;
    }

    public SafeHtml getValue(final String fieldId) {
        return values.get(fieldId);
    }

    public String getText(final String fieldId) {
        final SafeHtml safeHtml = values.get(fieldId);
        if (safeHtml != null) {
            final String value = safeHtml.asString();
            try {
                if (value.startsWith("[")) {
                    final Hyperlink hyperlink = Hyperlink.create(value);
                    return hyperlink.getText();
                }
                return value;
            } catch (final NumberFormatException e) {
                // Ignore.
            }
        }
        return null;
    }
}
