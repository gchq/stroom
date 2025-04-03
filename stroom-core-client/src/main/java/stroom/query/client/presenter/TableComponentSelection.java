package stroom.query.client.presenter;

import stroom.dashboard.client.table.ComponentSelection;
import stroom.query.api.v2.ColumnRef;
import stroom.util.shared.NullSafe;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TableComponentSelection implements ComponentSelection {

    private final List<ColumnRef> columns;
    private final TableRow tableRow;
    private final Map<String, String> values = new HashMap<>();

    private TableComponentSelection(final List<ColumnRef> columns,
                                    final TableRow tableRow) {
        this.columns = columns;
        this.tableRow = tableRow;

        for (final ColumnRef column : columns) {
            if (column.getId() != null) {
                final String value = tableRow.getText(column.getId());
                if (value != null) {
                    values.computeIfAbsent(column.getId(), k -> value);
                }
            }
        }
        for (final ColumnRef column : columns) {
            if (column.getName() != null) {
                final String value = tableRow.getText(column.getName());
                if (value != null) {
                    values.computeIfAbsent(column.getName(), k -> value);
                }
            }
        }
    }

    public static List<ComponentSelection> create(final List<ColumnRef> columns,
                                                  final List<TableRow> tableRows) {
        return NullSafe.list(tableRows)
                .stream()
                .map(tableRow -> new TableComponentSelection(columns, tableRow))
                .collect(Collectors.toList());
    }

    @Override
    public SafeHtml asSafeHtml() {
        boolean firstParam = true;
        final SafeHtmlBuilder sb = new SafeHtmlBuilder();
        for (final ColumnRef column : columns) {
            if (column.getId() != null) {
                final String value = tableRow.getText(column.getId());
                if (value != null) {
                    if (!firstParam) {
                        sb.appendHtmlConstant(", ");
                    }
                    sb.appendHtmlConstant("<b>");
                    sb.appendEscaped(column.getName());
                    sb.appendHtmlConstant("</b>");
                    sb.appendEscaped("=");
                    sb.appendEscaped(value);
                    firstParam = false;
                }
            }
        }
        return sb.toSafeHtml();
    }

    @Override
    public String get(final String key) {
        return values.get(key);
    }
}
