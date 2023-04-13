package stroom.widget.util.client;

import com.google.gwt.safehtml.shared.SafeHtml;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TableBuilder {

    private final List<TableRow> rows;

    public TableBuilder() {
        rows = new ArrayList<>();
    }

    public TableBuilder row(final String... values) {
        final List<TableCell> cells = new ArrayList<>();
        for (final String value : values) {
            cells.add(TableCell.builder().value(value).build());
        }
        final TableRow row = new TableRow(cells);
        rows.add(row);
        return this;
    }

    public TableBuilder row(final SafeHtml... values) {
        final List<TableCell> cells = new ArrayList<>();
        for (final SafeHtml value : values) {
            cells.add(TableCell.builder().value(value).build());
        }
        final TableRow row = new TableRow(cells);
        rows.add(row);
        return this;
    }

    public TableBuilder row(final TableCell... cells) {
        return row(new TableRow(Arrays.asList(cells)));
    }

    public TableBuilder row() {
        return row(new TableRow(Collections.emptyList()));
    }

    public TableBuilder row(final TableRow row) {
        rows.add(row);
        return this;
    }

//    @Override
//    public String toString() {
//        final HtmlBuilder htmlBuilder = new HtmlBuilder();
//        write(htmlBuilder);
//        return htmlBuilder.toString();
//    }
//
//    public SafeHtml toSafeHtml() {
//        final HtmlBuilder htmlBuilder = new HtmlBuilder();
//        write(htmlBuilder);
//        return htmlBuilder.toSafeHtml();
//    }

    public void write(final HtmlBuilder htmlBuilder) {
        htmlBuilder.appendTrustedString("<table>");

        for (final TableRow row : rows) {
            htmlBuilder.appendTrustedString("<tr>");
            for (final TableCell cell : row.getCells()) {
                cell.write(htmlBuilder);
            }
            htmlBuilder.appendTrustedString("</tr>");
        }

        htmlBuilder.appendTrustedString("</table>");
    }
}
