/*
 * Copyright 2026 Crown Copyright
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

package stroom.widget.datagrid.client;

import stroom.ai.shared.GeneralTableContext;
import stroom.data.client.event.AskStroomAiTableRequest;
import stroom.hyperlink.client.LinkValue;
import stroom.svg.shared.SvgImage;
import stroom.widget.contextmenu.client.ContextMenuManager;
import stroom.widget.contextmenu.client.TextEditTarget;
import stroom.widget.datagrid.client.model.TableText;
import stroom.widget.menu.client.IconMenuItem;
import stroom.widget.menu.client.IconParentMenuItem;
import stroom.widget.menu.client.Item;
import stroom.widget.menu.client.MenuRequest;
import stroom.widget.menu.client.Separator;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.util.client.Clipboard;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.cell.client.Cell.Context;
import com.google.gwt.dom.client.Element;
import com.google.gwt.http.client.URL;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.SelectionModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/// The menu [DataGrid] shows on a right-click (design R-M1 to R-M6), replicating
/// `MyDataGrid`'s: the cell's own items, link items, Copy, Export Table and Ask Stroom AI About.
///
/// Cell text comes from rendering the row data, not from the DOM, so a virtual grid copies and
/// exports every row on the page, not just the rows in view (R-M4).
///
/// @param <R> The row type.
final class DataGridContextMenu<R> {

    private static final String CSV_FILE_NAME = "table.csv";

    private final DataGrid<R> grid;

    /// @param grid The grid the menu is for.
    DataGridContextMenu(final DataGrid<R> grid) {
        this.grid = grid;
    }

    /// Show the menu.
    ///
    /// @param position  Where to show it.
    /// @param rowIndex  The page-relative index of the row clicked, or `-1`.
    /// @param colIndex  The index of the column clicked, or `-1`.
    /// @param target    The element clicked.
    /// @param textField The text field clicked (a cell's, or the header's filter), whose edit items
    ///                  start the menu, or null.
    void show(final PopupPosition position,
              final int rowIndex,
              final int colIndex,
              final Element target,
              final TextEditTarget textField) {
        final boolean onCell = rowIndex >= 0 && colIndex >= 0;
        final List<Item> menuItems = new ArrayList<>();
        if (textField != null) {
            menuItems.addAll(ContextMenuManager.createEditItems(textField));
            addSeparator(menuItems);
        }

        if (onCell) {
            final List<Item> cellItems = getCellMenuItems(rowIndex, colIndex);
            menuItems.addAll(cellItems);

            final LinkValue hyperlink = findHyperlink(target);
            if (hyperlink != null) {
                addSeparator(menuItems);
                addLinkItems(menuItems, hyperlink);
            }
            addSeparator(menuItems);
        }

        menuItems.add(new IconParentMenuItem.Builder()
                .icon(SvgImage.COPY)
                .text("Copy")
                .children(getCopyItems(rowIndex, colIndex))
                .build());
        menuItems.add(new IconMenuItem.Builder()
                .icon(SvgImage.DOWNLOAD)
                .text("Export Table")
                .command(() -> DataGridDom.download(CSV_FILE_NAME, "text/csv", getTableCsv()))
                .build());
        menuItems.add(new IconParentMenuItem.Builder()
                .icon(SvgImage.AI)
                .text("Ask Stroom AI About")
                .children(getAiItems(rowIndex, colIndex))
                .build());

        // The request goes up the page to its menu relay (see MenuRequest#relay)
        MenuRequest.fire(grid.getElement(),
                null,
                menuItems,
                position,
                textField != null
                        ? e -> textField.restore()
                        : null);
    }

    // A separator between groups of items: none at the top, and never two together
    private static void addSeparator(final List<Item> menuItems) {
        if (!menuItems.isEmpty() && !(menuItems.get(menuItems.size() - 1) instanceof Separator)) {
            menuItems.add(new Separator(1));
        }
    }

    // --------------------------------------------------------------------------------
    // Cell and link items
    // --------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<Item> getCellMenuItems(final int rowIndex, final int colIndex) {
        final Column<R, ?> column = grid.getColumn(colIndex);
        final Cell<?> cell = column.getCell();
        if (!(cell instanceof HasContextMenus)) {
            return Collections.emptyList();
        }
        final R row = grid.getVisibleItem(rowIndex);
        final Object key = grid.getKeyProvider() != null
                ? grid.getKeyProvider().getKey(row)
                : null;
        final Context context = new Context(rowIndex, colIndex, key);
        final List<Item> items = ((HasContextMenus<Object>) cell).getContextMenuItems(context,
                column.getValue(row));
        return items != null
                ? items
                : Collections.emptyList();
    }

    private static LinkValue findHyperlink(final Element target) {
        Element element = target;
        while (element != null && !"a".equalsIgnoreCase(element.getTagName())) {
            element = element.getParentElement();
        }
        if (element != null) {
            final String href = element.getAttribute("href");
            if (href != null && !href.isEmpty() && !href.startsWith("javascript")) {
                return new LinkValue(TableText.normaliseWhitespace(element.getInnerText()),
                        URL.decodeQueryString(href),
                        null,
                        null,
                        null);
            }
        }

        element = target;
        while (element != null && !element.hasAttribute("link")) {
            element = element.getParentElement();
        }
        if (element != null) {
            final String link = element.getAttribute("link");
            if (link != null) {
                return LinkValue.create(link);
            }
        }
        return null;
    }

    private void addLinkItems(final List<Item> menuItems, final LinkValue hyperlink) {
        menuItems.add(new IconMenuItem.Builder()
                .icon(SvgImage.COPY)
                .text("Copy Link URL")
                .command(() -> Clipboard.writeText(hyperlink.getHref()))
                .build());
        menuItems.add(new IconMenuItem.Builder()
                .icon(SvgImage.OPEN)
                .text("Follow URL")
                .command(() -> fireHyperlink(hyperlink))
                .build());
        if ("dashboard".equals(hyperlink.getType())) {
            if ("self".equals(hyperlink.getTarget())) {
                menuItems.add(new IconMenuItem.Builder()
                        .icon(SvgImage.OPEN)
                        .text("Open In New Tab")
                        .command(() -> fireHyperlink(hyperlink.copy().target("tab").build()))
                        .build());
            } else {
                menuItems.add(new IconMenuItem.Builder()
                        .icon(SvgImage.OPEN)
                        .text("Open In This Tab")
                        .command(() -> fireHyperlink(hyperlink.copy().target("self").build()))
                        .build());
            }
        }
    }

    private void fireHyperlink(final LinkValue hyperlink) {
        grid.followHyperlink(hyperlink);
    }

    // --------------------------------------------------------------------------------
    // Copy and export
    // --------------------------------------------------------------------------------

    private List<Item> getCopyItems(final int rowIndex, final int colIndex) {
        final List<Item> items = new ArrayList<>();
        if (rowIndex >= 0 && colIndex >= 0) {
            items.add(copyItem("Cell", () -> grid.getCellText(rowIndex, colIndex)));
            items.add(copyItem("Row", () -> TableText.toCsvLine(getRow(rowIndex))));
            items.add(copyItem("Selected Rows", () -> {
                final List<String> lines = new ArrayList<>();
                for (final int row : getSelectedRows()) {
                    lines.add(TableText.toCsvLine(getRow(row)));
                }
                return TableText.joinLines(lines);
            }));
            items.add(copyItem("Column", () -> TableText.joinLines(getColumn(colIndex, getAllRows()))));
            items.add(copyItem("Column For Selected Rows",
                    () -> TableText.joinLines(getColumn(colIndex, getSelectedRows()))));
        }
        items.add(copyItem("Table", this::getTableCsv));
        return items;
    }

    private static Item copyItem(final String text, final Supplier<String> textSupplier) {
        return new IconMenuItem.Builder()
                .icon(SvgImage.COPY)
                .text(text)
                .command(() -> Clipboard.writeText(textSupplier.get()))
                .build();
    }

    private String getTableCsv() {
        final List<String> lines = new ArrayList<>();
        lines.add(TableText.toCsvLine(getHeaders()));
        for (final int row : getAllRows()) {
            lines.add(TableText.toCsvLine(getRow(row)));
        }
        return TableText.joinLines(lines);
    }

    // --------------------------------------------------------------------------------
    // Ask Stroom AI
    // --------------------------------------------------------------------------------

    private List<Item> getAiItems(final int rowIndex, final int colIndex) {
        final List<Item> items = new ArrayList<>();
        if (rowIndex >= 0 && colIndex >= 0) {
            items.add(aiItem("Cell", () -> {
                final String name = grid.getHeaderText(colIndex);
                askAi(prefix() + "Cell [" + name + "]",
                        Collections.singletonList(name),
                        Collections.singletonList(Collections.singletonList(grid.getCellText(rowIndex, colIndex))));
            }));
            items.add(aiItem("Row", () -> {
                final List<String> headers = getHeaders();
                askAi(prefix() + "Row (" + headers.size() + " cols)",
                        headers,
                        Collections.singletonList(getRow(rowIndex)));
            }));
            items.add(aiItem("Selected Rows", () -> {
                final List<String> headers = getHeaders();
                final List<List<String>> rows = getRows(getSelectedRows());
                askAi(prefix() + "Selected rows (" + rows.size() + " rows, " + headers.size() + " cols)",
                        headers,
                        rows);
            }));
            items.add(aiItem("Column", () -> {
                final String name = grid.getHeaderText(colIndex);
                final List<List<String>> rows = asRows(getColumn(colIndex, getAllRows()));
                askAi(prefix() + "Column [" + name + "] (" + rows.size() + " rows)",
                        Collections.singletonList(name),
                        rows);
            }));
            items.add(aiItem("Column For Selected Rows", () -> {
                final String name = grid.getHeaderText(colIndex);
                final List<List<String>> rows = asRows(getColumn(colIndex, getSelectedRows()));
                askAi(prefix() + "Column [" + name + "] (" + rows.size() + " selected rows)",
                        Collections.singletonList(name),
                        rows);
            }));
        }
        items.add(aiItem("Current Page", () -> {
            final List<String> headers = getHeaders();
            final List<List<String>> rows = getRows(getAllRows());
            final String name = grid.getTableName() != null
                    ? grid.getTableName()
                    : "Table";
            askAi(name + " (" + rows.size() + " rows, " + headers.size() + " cols)", headers, rows);
        }));
        return items;
    }

    private static Item aiItem(final String text, final Runnable command) {
        return new IconMenuItem.Builder()
                .icon(SvgImage.AI)
                .text(text)
                .command(command::run)
                .build();
    }

    private void askAi(final String description, final List<String> headers, final List<List<String>> rows) {
        AskStroomAiTableRequest.fire(grid.getElement(), new GeneralTableContext(description, headers, rows));
    }

    // The table name prefix for descriptions, e.g. "Annotations ", or "" if there is no name.
    private String prefix() {
        return grid.getTableName() != null
                ? grid.getTableName() + " "
                : "";
    }

    private static List<List<String>> asRows(final List<String> column) {
        final List<List<String>> rows = new ArrayList<>(column.size());
        for (final String cell : column) {
            rows.add(Collections.singletonList(cell));
        }
        return rows;
    }

    // --------------------------------------------------------------------------------
    // Table text
    // --------------------------------------------------------------------------------

    private int getColumnOffset() {
        final List<String> headers = new ArrayList<>();
        for (int col = 0; col < grid.getColumnCount(); col++) {
            headers.add(grid.getHeaderText(col));
        }
        return TableText.columnOffset(headers);
    }

    private List<String> getHeaders() {
        final List<String> headers = new ArrayList<>();
        for (int col = getColumnOffset(); col < grid.getColumnCount(); col++) {
            headers.add(grid.getHeaderText(col));
        }
        return headers;
    }

    private List<String> getRow(final int rowIndex) {
        final List<String> cells = new ArrayList<>();
        for (int col = getColumnOffset(); col < grid.getColumnCount(); col++) {
            cells.add(grid.getCellText(rowIndex, col));
        }
        return cells;
    }

    private List<List<String>> getRows(final List<Integer> rowIndices) {
        final List<List<String>> rows = new ArrayList<>(rowIndices.size());
        for (final int row : rowIndices) {
            rows.add(getRow(row));
        }
        return rows;
    }

    private List<String> getColumn(final int colIndex, final List<Integer> rowIndices) {
        final List<String> cells = new ArrayList<>(rowIndices.size());
        for (final int row : rowIndices) {
            cells.add(grid.getCellText(row, colIndex));
        }
        return cells;
    }

    private List<Integer> getAllRows() {
        final List<Integer> rows = new ArrayList<>();
        for (int row = 0; row < grid.getVisibleItemCount(); row++) {
            rows.add(row);
        }
        return rows;
    }

    private List<Integer> getSelectedRows() {
        final List<Integer> rows = new ArrayList<>();
        final SelectionModel<? super R> selectionModel = grid.getSelectionModel();
        if (selectionModel == null) {
            return rows;
        }
        for (int row = 0; row < grid.getVisibleItemCount(); row++) {
            final R item = grid.getVisibleItem(row);
            if (item != null && selectionModel.isSelected(item)) {
                rows.add(row);
            }
        }
        return rows;
    }

}
