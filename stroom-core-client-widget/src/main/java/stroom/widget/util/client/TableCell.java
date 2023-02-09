package stroom.widget.util.client;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

public class TableCell {

    private final SafeHtml value;
    private final boolean header;
    private final int colspan;

    private TableCell(final SafeHtml value,
                      final boolean header,
                      final int colspan) {
        this.value = value;
        this.header = header;
        this.colspan = colspan;
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

    public static class Builder {

        private SafeHtml value;
        private boolean header;
        private int colspan = 1;


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

        public TableCell build() {
            if (value == null) {
                return new TableCell(SafeHtmlUtils.EMPTY_SAFE_HTML, header, colspan);
            }
            return new TableCell(value, header, colspan);
        }
    }
}