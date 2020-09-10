package stroom.dashboard.client.table;

import com.google.gwt.safehtml.shared.SafeHtml;
import stroom.hyperlink.client.Hyperlink;

import java.util.Map;

public class TableRow {
    private String groupKey;
    private Integer depth;
    private Map<String, SafeHtml> values;

    public TableRow(final String groupKey,
                    final Integer depth,
                    final Map<String, SafeHtml> values) {
        this.groupKey = groupKey;
        this.depth = depth;
        this.values = values;
    }

    public String getGroupKey() {
        return groupKey;
    }

    public Integer getDepth() {
        return depth;
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
