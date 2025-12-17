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

package stroom.query.client.presenter;

import stroom.hyperlink.client.Hyperlink;
import stroom.util.shared.Expander;
import stroom.util.shared.NullSafe;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safecss.shared.SafeStyles;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public class TableRow {

    private static Template template;

    private final Expander expander;
    private final String groupKey;
    private final Long annotationId;
    private final Map<String, Cell> cells;
    private final String matchingRule;
    private final int depth;

    public TableRow(final Expander expander,
                    final String groupKey,
                    final Long annotationId,
                    final Map<String, Cell> cells,
                    final String matchingRule,
                    final int depth) {
        this.expander = expander;
        this.groupKey = groupKey;
        this.annotationId = annotationId;
        this.cells = cells;
        this.matchingRule = matchingRule;
        this.depth = depth;
    }

    public Expander getExpander() {
        return expander;
    }

    public String getGroupKey() {
        return groupKey;
    }

    public Long getAnnotationId() {
        return annotationId;
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

    public int getDepth() {
        return depth;
    }

    public String getMatchingRule() {
        return matchingRule;
    }

    private SafeHtml decorateValue(final Cell cell) {
        final String rawValue = cell.getRawValue();
        final SafeHtmlBuilder innerBuilder = new SafeHtmlBuilder();
        appendValue(rawValue, innerBuilder);
        // Title is the plain text equivalent of the inner
        final String title = convertRawCellValue(rawValue);
        return SafeHtmlUtil.getTemplate()
                .divWithClassStyleAndTitle(
                        "cell",
                        cell.getStyles(),
                        title,
                        innerBuilder.toSafeHtml());
    }

    public String getText(final String fieldId) {
        final Cell cell = cells.get(fieldId);
        return NullSafe.get(
                cell,
                Cell::getRawValue,
                this::convertRawCellValue);
    }

    /**
     * Pkg private for testing only
     */
    String convertRawCellValue(final String rawValue) {
        if (rawValue != null) {
            try {
                final StringBuilder sb = new StringBuilder();
                appendValue(rawValue, sb);
                return sb.toString();
            } catch (final NumberFormatException e) {
                // Ignore.
                return null;
            }
        } else {
            return null;
        }
    }

    private void appendValue(final String value, final StringBuilder sb) {
        appendValue(value, part -> {
            if (part instanceof final Hyperlink hyperlink) {
                if (NullSafe.isNonBlankString(hyperlink.getText())) {
                    sb.append(hyperlink.getText());
                }
            } else if (part != null) {
                // A plain string
                sb.append(part);
            }
        });
    }

    private String buildTitle(final Hyperlink hyperlink) {
        final StringBuilder sb = new StringBuilder()
                .append("Link to: '")
                .append(hyperlink.getHref())
                .append("'");
        if (NullSafe.isNonBlankString(hyperlink.getType())) {
            sb.append(" (type: ")
                    .append(hyperlink.getType())
                    .append(")");
        }
        return sb.toString();
    }

    private void appendValue(final String value, final SafeHtmlBuilder sb) {
        appendValue(value, part -> {
            if (part instanceof final Hyperlink hyperlink) {
                if (NullSafe.isNonBlankString(hyperlink.getText())) {
                    // Title for the link tells you what it is linking to
                    final String title = buildTitle(hyperlink);
                    final SafeHtml displayValueHtml = NullSafe.isNonBlankString(hyperlink.getText())
                            ? SafeHtmlUtils.fromString(hyperlink.getText())
                            : SafeHtmlUtil.NBSP;
                    if (template == null) {
                        template = GWT.create(Template.class);
                    }
                    final SafeHtml linkHtml = template.link(hyperlink.toString(), title, displayValueHtml);
                    sb.append(linkHtml);
                }
            } else {
                // A plain string
                appendText(part.toString(), sb);
            }
        });
    }

    private static void appendText(final String text, final SafeHtmlBuilder sb) {
        if (NullSafe.isBlankString(text)) {
            // Why append an NBSP if text is empty/null
            sb.append(SafeHtmlUtil.NBSP);
        } else {
            sb.appendEscaped(text);
        }
    }

    private void appendValue(final String value, final Consumer<Object> partConsumer) {
        final List<Object> parts = getParts(value);
        if (NullSafe.hasItems(parts)) {
            parts.forEach(partConsumer);
        } else {
            // No parts so just consume the whole thing
            partConsumer.accept(value);
        }
    }

    private List<Object> getParts(final String value) {
        final List<Object> parts = new ArrayList<>();
        if (value.contains("[")) {
            // Might contain a link, so we need to walk over each char to extract
            // the parts
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < value.length(); i++) {
                final char c = value.charAt(i);
                if (c == '[') {
                    // Might be the start of a hyperlink or could just be a square bracket
                    final Hyperlink hyperlink = Hyperlink.create(value, i);
                    if (hyperlink != null) {
                        //noinspection SizeReplaceableByIsEmpty // isEmpty() not in GWT yet
                        if (sb.length() > 0) {
                            // Add the plain text part before this potential hyperlink
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
            //noinspection SizeReplaceableByIsEmpty // isEmpty() not in GWT yet
            if (sb.length() > 0) {
                parts.add(sb.toString());
            }
        } else {
            // No link in sight so just return the val as one part
            parts.add(value);
        }
        return parts;
    }

    @Override
    public String toString() {
        return "TableRow{" +
               "expander=" + expander +
               ", groupKey='" + groupKey + '\'' +
               ", annotationId='" + annotationId + '\'' +
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
        return Objects.equals(groupKey, tableRow.groupKey) &&
               Objects.equals(annotationId, tableRow.annotationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupKey, annotationId);
    }


    // --------------------------------------------------------------------------------


    public static class Cell {

        private final String rawValue;
        private final SafeStyles styles;

        public Cell(final String rawValue,
                    final SafeStyles styles) {
            this.rawValue = rawValue;
            this.styles = styles;
        }

        String getRawValue() {
            return rawValue;
        }

        SafeStyles getStyles() {
            return styles;
        }

        @Override
        public String toString() {
            return rawValue;
        }
    }


    // --------------------------------------------------------------------------------


    interface Template extends SafeHtmlTemplates {

        @SafeHtmlTemplates.Template("<u link=\"{0}\" title=\"{1}\">{2}</u>")
        SafeHtml link(String hyperlink, String title, SafeHtml displayValue);
    }
}
