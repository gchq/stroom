package stroom.widget.tooltip.client.presenter;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import java.util.ArrayList;
import java.util.List;

public class TableBuilder {

    private final List<Row> rows;

    public TableBuilder() {
        rows = new ArrayList<>();
    }

    public Row row() {
        final Row row = new Row();
        rows.add(row);
        return row;
    }

    @Override
    public String toString() {
        final SafeHtmlBuilder sb = new SafeHtmlBuilder();
        write(sb);
        return sb.toSafeHtml().asString();
    }

    public void write(final SafeHtmlBuilder sb) {
        sb.appendHtmlConstant("<table>");

        for (final Row row : rows) {
            sb.appendHtmlConstant("<tr>");
            for (final Cell cell : row.cells) {
                String colspan = "";
                if (cell.colspan > 1) {
                    colspan = " colspan=\"" + cell.colspan + "\"";
                }

                String elem = "td";
                if (cell.header) {
                    elem = "th";
                }

                sb.appendHtmlConstant("<" + elem + colspan + ">");
                sb.append(cell.value);
                sb.appendHtmlConstant("</" + elem + ">");
            }
            sb.appendHtmlConstant("</tr>");
        }

        sb.appendHtmlConstant("</table>");
    }

    public static class Row {

        private final List<Cell> cells = new ArrayList<>();

        public Row data(final String value) {
            return data(value, 1);
        }

        public Row data(final SafeHtml value) {
            return data(value, 1);
        }

        public Row data(final String value, final int colspan) {
            return data(SafeHtmlUtils.fromString(value), colspan);
        }

        public Row data(final SafeHtml value, final int colspan) {
            cells.add(new Cell(value, false, colspan));
            return this;
        }

        public Row header(final String value) {
            return header(value, 1);
        }

        public Row header(final SafeHtml value) {
            return header(value, 1);
        }

        public Row header(final String value, final int colspan) {
            return header(SafeHtmlUtils.fromString(value), colspan);
        }

        public Row header(final SafeHtml value, final int colspan) {
            cells.add(new Cell(value, true, colspan));
            return this;
        }
    }

    private static class Cell {

        private final SafeHtml value;
        private final boolean header;
        private final int colspan;

        public Cell(final SafeHtml value,
                    final boolean header,
                    final int colspan) {
            this.value = value;
            this.header = header;
            this.colspan = colspan;
        }
    }
}
