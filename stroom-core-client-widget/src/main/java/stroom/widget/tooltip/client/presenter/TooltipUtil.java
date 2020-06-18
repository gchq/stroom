/*
 * Copyright 2016 Crown Copyright
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

package stroom.widget.tooltip.client.presenter;


import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import java.util.function.Function;

public final class TooltipUtil {
    private TooltipUtil() {
        // Utility class.
    }

//    public static void addHeading(final SafeHtmlBuilder buffer,
//                                  final String heading) {
//        buffer.appendHtmlConstant("<b>");
//        buffer.appendEscaped(heading);
//        buffer.appendHtmlConstant("</b><br/>");
//    }
//
//    public static void addRowData(final SafeHtmlBuilder buffer,
//                                  final String heading,
//                                  final Object value) {
//        addRowData(buffer, heading, value, false);
//    }
//
//    public static void addRowData(final SafeHtmlBuilder buffer,
//                                  final String heading,
//                                  final Object value,
//                                  final boolean showBlank) {
//        if (value != null) {
//            final String s = String.valueOf(value);
//            if (s.length() > 0 || showBlank) {
//                buffer.appendEscaped(heading);
//                buffer.appendEscaped(" : ");
//                buffer.appendEscaped(s);
//                buffer.appendHtmlConstant("<br/>");
//            }
//        } else {
//            if (showBlank) {
//                buffer.appendEscaped(heading);
//                buffer.appendEscaped(": ");
//                buffer.appendHtmlConstant("<br/>");
//            }
//        }
//    }
//
//    public static void addRowData(final SafeHtmlBuilder buffer, final String value) {
//        if (value != null && !value.isEmpty()) {
//            buffer.appendEscaped(value);
//            buffer.appendHtmlConstant("<br/>");
//        }
//    }
//
//    public static void addBreak(final SafeHtmlBuilder buffer) {
//        buffer.appendHtmlConstant("<br/>");
//    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final SafeHtmlBuilder buffer;

        private Builder() {
            buffer = new SafeHtmlBuilder();
        }

        public Builder addHeading(final String heading) {
            buffer.appendHtmlConstant("<b>");
            buffer.appendEscaped(heading);
            buffer.appendHtmlConstant("</b><br/>");
            return this;
        }

        public Builder addRowData(final String heading, final Object value) {
            addRowData(heading, value, false);
            return this;
        }

        public Builder addRowData(final String heading,
                                  final Object value,
                                  final boolean showBlank) {
            if (value != null) {
                final String s = String.valueOf(value);
                if (s.length() > 0 || showBlank) {
                    buffer.appendEscaped(heading);
                    buffer.appendEscaped(" : ");
                    buffer.appendEscaped(s);
                    buffer.appendHtmlConstant("<br/>");
                }
            } else {
                if (showBlank) {
                    buffer.appendEscaped(heading);
                    buffer.appendEscaped(": ");
                    buffer.appendHtmlConstant("<br/>");
                }
            }
            return this;
        }

        public Builder addRowData(final String value) {
            if (value != null && !value.isEmpty()) {
                buffer.appendEscaped(value);
                buffer.appendHtmlConstant("<br/>");
            }
            return this;
        }

        public Builder addBreak() {
            buffer.appendHtmlConstant("<br/>");
            return this;
        }

        public Builder appendWithoutBreak(final String value) {
            if (value != null && !value.isEmpty()) {
                buffer.appendEscaped(value);
            }
            return this;
        }

        public Builder appendLinkWithoutBreak(final String url, final String title) {
            String escapedUrl = SafeHtmlUtils.htmlEscape(url);
            buffer.append(SafeHtmlUtils.fromTrustedString(
                    "<a href=\"" +
                            escapedUrl +
                            "\" target=\"_blank\">"));
            if (title != null && !title.isEmpty()) {
                buffer.appendEscaped(title);
            }
            buffer.appendHtmlConstant("</a>");
            return this;
        }
        public Builder addTable(Function<TableBuilder, SafeHtml> tableBuilderFunc) {
            TableBuilder tableBuilder = new TableBuilder();
            buffer.append(tableBuilderFunc.apply(tableBuilder));
            return this;
        }

        public Builder addTable() {
            buffer.append(SafeHtmlUtils.fromTrustedString("<table>\n" +
                    "  <tr>\n" +
                    "    <th>Firstname</th>\n" +
                    "    <th>Lastname</th>\n" +
                    "  </tr>\n" +
                    "  <tr>\n" +
                    "    <td>Jill</td>\n" +
                    "    <td>Smith</td>\n" +
                    "  </tr>\n" +
                    "  <tr>\n" +
                    "    <td>Eve</td>\n" +
                    "    <td>Jackson</td>\n" +
                    "  </tr>\n" +
                    "</table>"));
            return this;
        }

        public String build() {
            return buffer.toSafeHtml().asString();
        }
    }

    public static class TableBuilder {
        private final SafeHtmlBuilder buffer;

        public TableBuilder() {
            buffer = new SafeHtmlBuilder()
                    .appendHtmlConstant("<Table>");
        }

        public TableBuilder addHeaderRow(final String col1, final String col2) {
            buffer
                    .appendHtmlConstant("<tr><th>")
                    .appendEscaped(col1)
                    .appendHtmlConstant("</th><th>")
                    .appendEscaped(col2)
                    .appendHtmlConstant("</th></tr>");
            return this;
        }

        public TableBuilder addRow(final String col1, final String col2) {
            buffer
                    .appendHtmlConstant("<tr><td>")
                    .appendEscaped(col1)
                    .appendHtmlConstant("</td><td>")
                    .appendEscaped(col2)
                    .appendHtmlConstant("</td></tr>");
            return this;
        }

        public SafeHtml build() {
            return buffer.appendHtmlConstant("<Table>")
                    .toSafeHtml();
        }
    }
}
