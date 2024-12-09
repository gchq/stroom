package stroom.query.client.presenter;

import stroom.hyperlink.client.Hyperlink;
import stroom.util.shared.Expander;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class TableRow {

    private final Expander expander;
    private final String groupKey;
    private final Map<String, Cell> cells;
    private final String matchingRule;

    public TableRow(final Expander expander,
                    final String groupKey,
                    final Map<String, Cell> cells,
                    final String matchingRule) {
        this.expander = expander;
        this.groupKey = groupKey;
        this.cells = cells;
        this.matchingRule = matchingRule;
    }

    public Expander getExpander() {
        return expander;
    }

    public String getGroupKey() {
        return groupKey;
    }

    public SafeHtml getValue(final String fieldId) {
        // Turn the raw value into html on demand
        final Cell cell = cells.get(fieldId);
        if (cell != null) {
            return decorateValue(cell);
        } else {
            return SafeHtmlUtil.NBSP;
        }
    }

    public String getMatchingRule() {
        return matchingRule;
    }

    private SafeHtml decorateValue(final Cell cell) {
        final SafeHtmlBuilder safeHtmlBuilder = new SafeHtmlBuilder();

        final String styleAttrStr = (cell.getStyles() != null && !cell.getStyles().isEmpty())
                ? (" style=" + cell.getStyles())
                : "";

        final String titleAttrStr = cell.getRawValue() != null
                ?
                " title=\"" + SafeHtmlUtils.htmlEscape(cell.getRawValue()) + "\""
                : "";

        safeHtmlBuilder.appendHtmlConstant("<div class=\"cell\"" + styleAttrStr + titleAttrStr + ">");

        appendValue(cell.getRawValue(), safeHtmlBuilder);

        safeHtmlBuilder.appendHtmlConstant("</div>");
        return safeHtmlBuilder.toSafeHtml();
    }

    public String getText(final String fieldId) {
        final Cell cell = cells.get(fieldId);
        if (cell != null) {
            final String rawValue = cell.getRawValue();
            if (rawValue != null) {
                try {
                    if (rawValue.startsWith("[")) {
                        final Hyperlink hyperlink = Hyperlink.create(rawValue);
                        return hyperlink.getText();
                    }
                    return rawValue;
                } catch (final NumberFormatException e) {
                    // Ignore.
                    return null;
                }
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private void appendValue(final String value, final SafeHtmlBuilder sb) {
        final List<Object> parts = getParts(value);
        if (parts.size() == 0) {
            appendText(value, sb);

        } else {
            parts.forEach(p -> {
                if (p instanceof Hyperlink) {
                    final Hyperlink hyperlink = (Hyperlink) p;
                    if (!hyperlink.getText().trim().isEmpty()) {
                        sb.appendHtmlConstant("<u link=\"" + hyperlink + "\">");
                        appendText(hyperlink.getText(), sb);
                        sb.appendHtmlConstant("</u>");
                    }
                } else {
                    appendText(p.toString(), sb);
                }
            });
        }
    }

    private void appendText(final String text, final SafeHtmlBuilder sb) {
        if (text == null || text.trim().length() == 0) {
            sb.append(SafeHtmlUtil.NBSP);
        } else {
            sb.appendEscaped(text);
        }
    }

    private List<Object> getParts(final String value) {
        final List<Object> parts = new ArrayList<>();

        final StringBuilder sb = new StringBuilder();

        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);

            if (c == '[') {
                final Hyperlink hyperlink = Hyperlink.create(value, i);
                if (hyperlink != null) {
                    if (sb.length() > 0) {
                        parts.add(sb.toString());
                        sb.setLength(0);
                    }
                    parts.add(hyperlink);
                    i += hyperlink.toString().length() - 1;
                } else {
                    sb.append(c);
                }

            } else {
                sb.append(c);
            }
        }

        if (sb.length() > 0) {
            parts.add(sb.toString());
        }

        return parts;
    }

    @Override
    public String toString() {
        return "TableRow{" +
               "expander=" + expander +
               ", groupKey='" + groupKey + '\'' +
               ", cells=" + cells +
               '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TableRow tableRow = (TableRow) o;
        return Objects.equals(groupKey, tableRow.groupKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupKey);
    }

    public static class Cell {

        private final String rawValue;
        private final String styles;

        public Cell(final String rawValue,
                    final String styles) {
            this.rawValue = rawValue;
            this.styles = styles;
        }

        private String getRawValue() {
            return rawValue;
        }

        private String getStyles() {
            return styles;
        }

        @Override
        public String toString() {
            return rawValue;
        }
    }
}
