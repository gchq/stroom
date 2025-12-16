package stroom.data.grid.client;

import stroom.ai.shared.GeneralTableContext;
import stroom.data.client.event.AskStroomAiEvent;
import stroom.svg.shared.SvgImage;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.IconParentMenuItem;
import stroom.widget.menu.client.presenter.Item;

import com.google.gwt.dom.client.TableCellElement;
import com.google.gwt.dom.client.TableRowElement;
import com.google.gwt.dom.client.TableSectionElement;
import com.google.gwt.event.shared.HasHandlers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class MyDataGridAiSupport<T> {

    private final HasHandlers globalEventBus;
    private final MyDataGrid<T> dataGrid;
    private final String chatMemoryId = UUID.randomUUID().toString();

    public MyDataGridAiSupport(final HasHandlers globalEventBus,
                               final MyDataGrid<T> dataGrid) {
        this.globalEventBus = globalEventBus;
        this.dataGrid = dataGrid;
    }

    Item createContextMenu(final int row,
                           final int col) {
        final List<Item> menuItems = new ArrayList<>();
        if (row >= 0 && col >= 0) {
            menuItems.add(new IconMenuItem.Builder()
                    .icon(SvgImage.AI)
                    .text("Cell")
                    .command(() -> aiCell(row, col))
                    .build());

            menuItems.add(new IconMenuItem.Builder()
                    .icon(SvgImage.AI)
                    .text("Row")
                    .command(() -> aiRow(row))
                    .build());

            menuItems.add(new IconMenuItem.Builder()
                    .icon(SvgImage.AI)
                    .text("Selected Rows")
                    .command(this::aiSelectedRows)
                    .build());

            menuItems.add(new IconMenuItem.Builder()
                    .icon(SvgImage.AI)
                    .text("Column")
                    .command(() -> aiColumn(col))
                    .build());

            menuItems.add(new IconMenuItem.Builder()
                    .icon(SvgImage.AI)
                    .text("Column For Selected Rows")
                    .command(() -> aiColumnForSelectedRows(col))
                    .build());
        }

        menuItems.add(new IconMenuItem.Builder()
                .icon(SvgImage.AI)
                .text("Table")
                .command(this::aiTable)
                .build());

        return new IconParentMenuItem.Builder()
                .icon(SvgImage.AI)
                .text("Ask Stroom AI About")
                .children(menuItems)
                .build();
    }

    private void aiCell(final int row, final int col) {
        AskStroomAiEvent.fire(globalEventBus,
                new GeneralTableContext(chatMemoryId, getHeader(col),
                        Collections.singletonList(Collections.singletonList(dataGrid.getCellText(row, col)))));
    }

    private void aiRow(final int row) {
        AskStroomAiEvent.fire(globalEventBus,
                new GeneralTableContext(chatMemoryId, getHeaders(),
                        Collections.singletonList(getRow(row))));
    }

    private void aiSelectedRows() {
        final List<List<String>> rows = new ArrayList<>();
        for (int row = 0; row < dataGrid.getVisibleItemCount(); row++) {
            final T item = dataGrid.getVisibleItem(row);
            if (item != null) {
                if (dataGrid.getSelectionModel().isSelected(item)) {
                    rows.add(getRow(row));
                }
            }
        }

        AskStroomAiEvent.fire(globalEventBus,
                new GeneralTableContext(chatMemoryId, getHeaders(),
                        rows));
    }

    private void aiColumn(final int col) {
        final List<List<String>> rows = new ArrayList<>();
        for (int row = 0; row < dataGrid.getVisibleItemCount(); row++) {
            rows.add(Collections.singletonList(dataGrid.getCellText(row, col)));
        }

        AskStroomAiEvent.fire(globalEventBus,
                new GeneralTableContext(chatMemoryId, getHeader(col),
                        rows));
    }

    private void aiColumnForSelectedRows(final int col) {
        final List<List<String>> rows = new ArrayList<>();
        for (int row = 0; row < dataGrid.getVisibleItemCount(); row++) {
            final T item = dataGrid.getVisibleItem(row);
            if (item != null) {
                if (dataGrid.getSelectionModel().isSelected(item)) {
                    rows.add(Collections.singletonList(dataGrid.getCellText(row, col)));
                }
            }
        }

        AskStroomAiEvent.fire(globalEventBus,
                new GeneralTableContext(chatMemoryId, getHeader(col),
                        rows));
    }

    private void aiTable() {
        final List<List<String>> rows = new ArrayList<>();
        for (int row = 0; row < dataGrid.getVisibleItemCount(); row++) {
            rows.add(getRow(row));
        }

        AskStroomAiEvent.fire(globalEventBus,
                new GeneralTableContext(chatMemoryId, getHeaders(),
                        rows));
    }

    private List<String> getRow(final int row) {
        final List<String> cells = new ArrayList<>();
        final int columnOffset = dataGrid.getColumnOffset();
        for (int col = columnOffset; col < dataGrid.getColumnCount(); col++) {
            cells.add(dataGrid.getCellText(row, col));
        }
        return cells;
    }

    private List<String> getHeaders() {
        final List<String> headers = new ArrayList<>();
        final int columnOffset = dataGrid.getColumnOffset();
        final TableSectionElement head = dataGrid.getTableHeadElement();
        if (head != null && head.getRows().getLength() > 0) {
            final TableRowElement headerRow = head.getRows().getItem(0);
            for (int col = columnOffset; col < dataGrid.getColumnCount(); col++) {
                final TableCellElement th = headerRow.getCells().getItem(col);
                headers.add(th.getInnerText());
            }
        }
        return headers;
    }

    private List<String> getHeader(final int col) {
        final TableSectionElement head = dataGrid.getTableHeadElement();
        if (head != null && head.getRows().getLength() > 0) {
            final TableRowElement headerRow = head.getRows().getItem(0);
            final TableCellElement th = headerRow.getCells().getItem(col);
            return Collections.singletonList(th.getInnerText());
        }
        return Collections.singletonList("");
    }
}
