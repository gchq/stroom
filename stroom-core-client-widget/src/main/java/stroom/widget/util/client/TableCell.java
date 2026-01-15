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

package stroom.widget.util.client;

import stroom.util.shared.NullSafe;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TableCell {

    public static final String WRAP_CLASS = "wrap";

    private final SafeHtml value;
    private final boolean header;
    private final int colspan;
    private final String classValue;

    private TableCell(final SafeHtml value,
                      final boolean header,
                      final int colspan,
                      final String classValue) {
        this.value = value;
        this.header = header;
        this.colspan = colspan;
        this.classValue = classValue;
    }

    public static TableCell header(final SafeHtml value) {
        return TableCell.builder().value(value).header(true).build();
    }

    public static TableCell header(final SafeHtml value,
                                   final int colspan) {
        return TableCell.builder().value(value).header(true).colspan(colspan).build();
    }

    public static TableCell header(final String value) {
        return TableCell.builder().value(value).header(true).build();
    }

    public static TableCell header(final String value,
                                   final int colspan) {
        return TableCell.builder().value(value).header(true).colspan(colspan).build();
    }

    public static TableCell data(final SafeHtml value) {
        return TableCell.builder().value(value).build();
    }

    public static TableCell data(final SafeHtml value,
                                 final int colspan) {
        return TableCell.builder().value(value).colspan(colspan).build();
    }

    public static TableCell data(final String value) {
        return TableCell.builder().value(value).build();
    }

    public static TableCell data(final String value,
                                 final int colspan) {
        return TableCell.builder().value(value).colspan(colspan).build();
    }

    public void write(final HtmlBuilder htmlBuilder) {
        htmlBuilder.appendTrustedString("<");
        if (header) {
            htmlBuilder.appendTrustedString("th");
        } else {
            htmlBuilder.appendTrustedString("td");
        }
        if (!NullSafe.isBlankString(classValue)) {
            htmlBuilder.appendTrustedString(" class=\"");
            htmlBuilder.append(classValue);
            htmlBuilder.appendTrustedString("\"");
        }
        if (colspan > 1) {
            htmlBuilder.appendTrustedString(" colspan=\"");
            htmlBuilder.append(colspan);
            htmlBuilder.appendTrustedString("\"");
        }
        htmlBuilder.appendTrustedString(">");
        htmlBuilder.append(value);
        htmlBuilder.appendTrustedString("</");
        if (header) {
            htmlBuilder.appendTrustedString("th");
        } else {
            htmlBuilder.appendTrustedString("td");
        }
        htmlBuilder.appendTrustedString(">");
    }

    public static TableCell.Builder builder() {
        return new TableCell.Builder();
    }


    // --------------------------------------------------------------------------------


    public static class Builder {

        private SafeHtml value;
        private boolean header;
        private int colspan = 1;
        private List<String> cssClasses = null;


        public TableCell.Builder value(final String value) {
            if (value != null) {
                this.value = SafeHtmlUtils.fromString(value);
            }
            return this;
        }

        public TableCell.Builder value(final SafeHtml value) {
            this.value = value;
            return this;
        }

        public TableCell.Builder header(final boolean header) {
            this.header = header;
            return this;
        }

        public TableCell.Builder colspan(final int colspan) {
            this.colspan = colspan;
            return this;
        }

        /**
         * Add single class or space separated classes to the cell
         */
        public TableCell.Builder addClass(final String cssClass) {
            if (!NullSafe.isBlankString(cssClass)) {
                if (cssClasses == null) {
                    cssClasses = new ArrayList<>();
                }
                cssClasses.add(cssClass);
            }
            return this;
        }

        public TableCell build() {
            final String cssClass = NullSafe.stream(cssClasses)
                    .collect(Collectors.joining(" "));
            if (value == null) {
                return new TableCell(SafeHtmlUtils.EMPTY_SAFE_HTML, header, colspan, cssClass);
            }
            return new TableCell(value, header, colspan, cssClass);
        }
    }
}
