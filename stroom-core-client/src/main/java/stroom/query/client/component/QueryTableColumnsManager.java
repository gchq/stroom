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

package stroom.query.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.dashboard.client.component.ColumnFilterEditorController;
import stroom.dashboard.client.component.ColumnFormatController;
import stroom.dashboard.client.component.ColumnValuesFilterController;
import stroom.dashboard.client.component.RulesController;
import stroom.dashboard.client.table.ColumnValuesDataSupplier;
import stroom.dashboard.client.table.FilterCellManager;
import stroom.query.api.Column;
import stroom.query.api.ColumnFilter;
import stroom.query.api.ColumnValueSelection;
import stroom.query.api.Sort;
import stroom.query.api.Sort.SortDirection;
import stroom.query.client.presenter.ColumnHeaderHtmlUtil;
import stroom.query.shared.QueryTablePreferences;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.datagrid.client.Heading;
import stroom.widget.datagrid.client.HeadingListener;
import stroom.widget.datagrid.client.SortIcon;
import stroom.widget.menu.client.HideMenuEvent;
import stroom.widget.menu.client.IconMenuItem;
import stroom.widget.menu.client.IconParentMenuItem;
import stroom.widget.menu.client.Item;
import stroom.widget.menu.client.ShowMenuEvent;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.popup.client.PopupPosition.PopupLocation;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.Rect;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.user.client.Timer;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class QueryTableColumnsManager implements HeadingListener, FilterCellManager, HasHandlers {

    private final EventBus eventBus;
    private final QueryResultTableController tablePresenter;
    private final Provider<ColumnFormatController> columnFormatProvider;
    private final Provider<RulesController> rulesProvider;
    private final Provider<ColumnFilterEditorController> columnFilterEditorProvider;
    private final ColumnValuesFilterController columnValuesFilter;
    private int columnsStartIndex;
    private int currentMenuColIndex = -1;
    private int currentFilterColIndex = -1;
    private boolean moving;

    public QueryTableColumnsManager(final EventBus eventBus,
                                    final QueryResultTableController tablePresenter,
                                    final Provider<ColumnFormatController> columnFormatProvider,
                                    final Provider<RulesController> rulesProvider,
                                    final Provider<ColumnFilterEditorController> columnFilterEditorProvider,
                                    final ColumnValuesFilterController columnValuesFilter) {
        this.eventBus = eventBus;
        this.tablePresenter = tablePresenter;
        this.columnFormatProvider = columnFormatProvider;
        this.rulesProvider = rulesProvider;
        this.columnFilterEditorProvider = columnFilterEditorProvider;
        this.columnValuesFilter = columnValuesFilter;
    }

    @Override
    public void onMoveStart(final NativeEvent event, final Supplier<Heading> headingSupplier) {
        moving = true;

        if (currentMenuColIndex != -1 || currentFilterColIndex != -1) {
            final Heading heading = headingSupplier.get();
            if (heading != null) {
                final Element element = Element.as(event.getEventTarget());
                final Element columnTop = ElementUtil.findParent(element, "column-top", 3);
                if (columnTop != null) {
                    final int colIndex = heading.getColIndex();
                    if (currentMenuColIndex == colIndex) {
                        HideMenuEvent
                                .builder()
                                .fire(this);
                    }
                    if (currentFilterColIndex == colIndex) {
                        columnValuesFilter.hide();
                    }
                }
            }
        }
    }

    @Override
    public void onMoveEnd(final NativeEvent event, final Supplier<Heading> headingSupplier) {
        moving = false;
    }

    @Override
    public void onShowMenu(final NativeEvent event, final Supplier<Heading> headingSupplier) {
        if (!moving) {
            final Heading heading = headingSupplier.get();
            if (heading != null && heading.getColIndex() >= columnsStartIndex) {
                final int colIndex = heading.getColIndex();
                final HasHandlers queryTableColumnsManager = this;

                final Column column = getColumn(colIndex);
                if (column != null) {
                    new Timer() {
                        @Override
                        public void run() {
                            final Element th = heading.getElement();
                            final Element target = event.getEventTarget().cast();
                            final Element button = ElementUtil.findChild(th, "column-valueFilterIcon");
                            final boolean isFilterButton = button.isOrHasChild(target);
                            final Element diableFilterButton = ElementUtil.findChild(th,
                                    "dashboard-table-filter-cell-disable-button");
                            final boolean isDiableFilterButton = diableFilterButton.isOrHasChild(target);

                            if (currentFilterColIndex == colIndex) {
                                columnValuesFilter.hide();

                            } else if (isFilterButton) {
                                showValuesFilter(column, colIndex, th, button);
                            }
                            // The filter's disable button acts on its own click (FilterCell), so
                            // that Enter and Space work it too: here it only must not open the menu

                            if (currentMenuColIndex == colIndex) {
                                HideMenuEvent.builder().fire(queryTableColumnsManager);

                            } else if (!isFilterButton && !isDiableFilterButton) {
                                currentMenuColIndex = colIndex;
                                final List<Item> menuItems = getMenuItems(column, colIndex, th);

                                Rect relativeRect = new Rect(th);
                                relativeRect = relativeRect.grow(3);
                                final PopupPosition popupPosition = new PopupPosition(relativeRect,
                                        PopupLocation.BELOW);

                                ShowMenuEvent
                                        .builder()
                                        .name("Options for column '" + column.getName() + "'")
                                        .items(menuItems)
                                        .popupPosition(popupPosition)
                                        .addAutoHidePartner(th)
                                        .onHide(e2 -> resetMenuColIndex())
                                        .fire(queryTableColumnsManager);
                            }
                        }
                    }.schedule(0);
                }
            }
        }
    }

    private void resetFilterColIndex() {
        currentFilterColIndex = -1;
    }

    private void resetMenuColIndex() {
        currentMenuColIndex = -1;
    }

    @Override
    public void moveColumn(final int fromIndex, final int toIndex) {
        final Column column = getColumn(fromIndex);
        if (column != null) {
            final List<Column> columns = tablePresenter.getCurrentColumns();
            columns.remove(column);

            final int destIndex = toIndex - columnsStartIndex;
            if (columns.size() <= destIndex) {
                columns.add(column);
            } else if (destIndex > fromIndex) {
                columns.add(destIndex - 1, column);
            } else {
                columns.add(destIndex, column);
            }

            tablePresenter.setPreferredColumns(columns);
        }
    }

    @Override
    public void resizeColumn(final int colIndex, final int size) {
        final Column column = getColumn(colIndex);
        if (column != null) {
            final List<Column> newColumns = replaceColumns(getColumns(), column, column.copy().width(size).build());
            tablePresenter.setPreferredColumns(newColumns);
        }
    }

    private void changeSort(final Column column, final SortDirection direction) {
        final List<Column> columns = tablePresenter.getCurrentColumns();
        List<Column> newColumns = columns;
        boolean change = false;

        if (direction == null) {
            if (column.getSort() != null) {
                final int order = column.getSort().getOrder();
                newColumns = increaseSortOrder(columns, order);
                newColumns = replaceColumns(newColumns, column, column.copy().sort(null).build());
                change = true;
            }
        } else {
            final Sort sort = column.getSort();
            if (sort == null) {
                final int lowestSortOrder = getLowestSortOrder(columns);
                final Sort newSort = new Sort(lowestSortOrder + 1, direction);
                newColumns = replaceColumns(columns, column, column.copy().sort(newSort).build());
                change = true;
            } else {
                final int lowestSortOrder = getLowestSortOrder(columns);
                if (sort.getDirection() != direction || sort.getOrder() != lowestSortOrder) {
                    // Increase sort order on all columns where the sort order is
                    // lower than this columns sort order as this column will now
                    // have the lowest order.
                    newColumns = increaseSortOrder(columns, column.getSort().getOrder());
                    final Sort newSort = new Sort(lowestSortOrder, direction);
                    newColumns = replaceColumns(newColumns, column, column.copy().sort(newSort).build());
                    change = true;
                }
            }
        }

        if (change) {
            tablePresenter.setPreferredColumns(newColumns);
            tablePresenter.refresh();
        }
    }

    private int getLowestSortOrder(final List<Column> columns) {
        int lowestOrder = -1;
        for (final Column column : columns) {
            if (column.getSort() != null) {
                if (lowestOrder < column.getSort().getOrder()) {
                    lowestOrder = column.getSort().getOrder();
                }
            }
        }

        return lowestOrder;
    }

    private List<Column> increaseSortOrder(final List<Column> columns, final int order) {
        List<Column> newColumns = columns;
        for (final Column column : columns) {
            final Sort sort = column.getSort();
            if (sort != null && sort.getOrder() > order) {
                final Sort newSort = new Sort(sort.getOrder() - 1, sort.getDirection());
                newColumns = replaceColumns(newColumns, column, column.copy().sort(newSort).build());
            }
        }
        return newColumns;
    }

    public void showFormat(final Column column) {
        columnFormatProvider.get().show(tablePresenter, column, (oldColumn, newColumn) -> {
            final List<Column> newColumns = replaceColumns(getColumns(), oldColumn, newColumn);
            tablePresenter.setPreferredColumns(newColumns);
            tablePresenter.refresh();
        });
    }

    private void filterColumn(final Column column) {
        // Made for this use, and owned by the table while it is shown
        columnFilterEditorProvider.get().edit(tablePresenter, column, (oldColumn, newColumn) -> {
            // The dialog isn't modal, so the column may have been changed or removed while it was
            // open: the filter goes on the column as it is now
            final Column current = findColumn(getColumns(), oldColumn.getId());
            if (current == null) {
                fireColumnRemoved(oldColumn);
                return;
            }
            final List<Column> newColumns = replaceColumns(getColumns(), current, current.copy()
                    .columnFilter(newColumn.getColumnFilter())
                    .build());
            tablePresenter.setPreferredColumns(newColumns);
            tablePresenter.onColumnFilterChange();
        });
    }

    private void moveFirst(final Column column) {
        final List<Column> columns = getColumns();
        columns.remove(column);
        columns.add(0, column);
        tablePresenter.setPreferredColumns(columns);
    }

    private void moveLast(final Column column) {
        final List<Column> columns = getColumns();
        columns.remove(column);
        columns.add(column);
        tablePresenter.setPreferredColumns(columns);
    }

    // The column with the id, or null if there is none
    private static Column findColumn(final List<Column> columns, final String id) {
        return columns.stream()
                .filter(column -> column.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    private void fireColumnRemoved(final Column column) {
        AlertEvent.fireWarn(this, "The column '" + column.getName() + "' was removed while its filter "
                                  + "was being edited, so the filter can't be kept.", null);
    }

    private List<Column> replaceColumns(final List<Column> sourceColumns,
                                        final Column oldColumn,
                                        final Column newColumn) {
        final List<Column> columns = new ArrayList<>();
        for (final Column column : sourceColumns) {
            if (column.getId().equals(oldColumn.getId())) {
                if (newColumn != null) {
                    columns.add(newColumn);
                }
            } else {
                columns.add(column);
            }
        }
        return columns;
    }

    @Override
    public void setColumnFilter(final Column column, final ColumnFilter columnFilter) {
        if (!Objects.equals(column.getColumnFilter(), columnFilter)) {
            if (columnFilter != null &&
                NullSafe.isNonBlankString(columnFilter.getFilter())) {
                if (tablePresenter.getQueryTablePreferences() != null &&
                    !tablePresenter.getQueryTablePreferences().showValueFilters()) {
                    tablePresenter.toggleShowValueFilters();
                }
            }

            // Required to replace column filter in place so we don't need to re-render the table which would lose
            // focus from column filter textbox.
            column.setColumnFilter(columnFilter);

            final List<Column> newColumns =
                    replaceColumns(getColumns(), column, column.copy().columnFilter(columnFilter).build());
            tablePresenter.setFocused(false);
            tablePresenter.setPreferredColumns(newColumns);
            tablePresenter.onColumnFilterChange();
        }
    }

    @Override
    public void setValueSelection(final Column column, final ColumnValueSelection columnValueSelection) {
        if (!Objects.equals(column.getColumnValueSelection(), columnValueSelection)) {
            // Required to replace column filter in place, so we don't need to re-render the table which would lose
            // focus from column filter textbox.
            column.setColumnValueSelection(columnValueSelection);

            final List<Column> newColumns = replaceColumns(getColumns(), column,
                    column.copy().columnValueSelection(columnValueSelection).build());
            tablePresenter.setPreferredColumns(newColumns);
            tablePresenter.setFocused(false);
            tablePresenter.onColumnFilterChange();
        }
    }

    private List<Column> getColumns() {
        return tablePresenter.getCurrentColumns();
//
//        if (tablePresenter.getSettings() != null && tablePresenter.getTableComponentSettings().getColumns() != null) {
//            return new ArrayList<>(tablePresenter.getTableComponentSettings().getColumns());
//        }
//        return new ArrayList<>();
    }

//    private void updateColumns(final List<Column> columns) {
//        tablePresenter.setPreferredColumns(columns);
//    }

    private void showColumn(final Column column) {
        final List<Column> newColumns = replaceColumns(getColumns(), column, column.copy().visible(true).build());
        tablePresenter.setPreferredColumns(newColumns);
    }

    private void hideColumn(final Column column) {
        if (getVisibleColumnCount() <= 1) {
            AlertEvent.fireError(tablePresenter, "You cannot remove or hide all columns", null);
        } else {
            final List<Column> newColumns = replaceColumns(getColumns(), column, column.copy().visible(false).build());
            tablePresenter.setPreferredColumns(newColumns);
        }
    }

    private long getVisibleColumnCount() {
        final List<Column> columns = getColumns();
        return columns.stream()
                .filter(Column::isVisible)
                .count();
    }

    private Column getColumn(final int colIndex) {
        final List<Column> columns = getColumns();
        int index = columnsStartIndex;
        for (final Column column : columns) {
            if (column.isVisible()) {
                if (index == colIndex) {
                    return column;
                }
                index++;
            }
        }
        return null;
    }

    public int getColumnIndex(final Column column) {
        final List<Column> columns = getColumns();
        int index = columnsStartIndex;
        for (final Column col : columns) {
            if (col.isVisible()) {
                if (col.getId().equals(column.getId())) {
                    return index;
                }
                index++;
            }
        }
        return -1;
    }

    public void setColumnsStartIndex(final int columnsStartIndex) {
        this.columnsStartIndex = columnsStartIndex;
    }

    // Shows the column's values filter below its header's filter icon (the icon or the column
    // menu's Filter Values item)
    private void showValuesFilter(final Column column,
                                  final int colIndex,
                                  final Element th,
                                  final Element button) {
        currentFilterColIndex = colIndex;
        final ColumnValuesDataSupplier dataSupplier = tablePresenter.getDataSupplier(column, null);
        columnValuesFilter.show(
                () -> button,
                th,
                column,
                () -> dataSupplier,
                hideEvent -> resetFilterColIndex(),
                column.getColumnValueSelection(),
                this);
    }

    private List<Item> getMenuItems(final Column column, final int colIndex, final Element th) {
        final List<Item> menuItems = new ArrayList<>();

//        // Create rename menu.
//        menuItems.add(createRenameMenu(column));
//        // Create expression menu.
//        menuItems.add(createExpressionMenu(column));
        // Create sort menu.
        menuItems.add(createSortMenu(column));
//        // Create group by menu.
//        menuItems.add(createGroupByMenu(column));
        // Create format menu.
        menuItems.add(createFormatMenu(column));
//        // Add filter menu item.
//        menuItems.add(createFilterMenu(column));
        // Add column filter menu item.
        menuItems.add(createColumnFilterMenu(column));
        // The values filter, as the header's filter icon opens it, for the keyboard
        menuItems.add(createValuesFilterMenu(column, colIndex, th));

        // Create move menu.
        menuItems.add(createMoveFirstMenu(column));
        menuItems.add(createMoveLastMenu(column));

//        // Create duplicate menu.
//        menuItems.add(createDuplicateMenu(column));

        // Create hide menu.
        menuItems.add(createHideMenu(column));

        // Create show menu.
        final Item showMenu = createShowMenu();
        if (showMenu != null) {
            menuItems.add(showMenu);
        }

        menuItems.add(createConditionalFormattingMenu(column));

//        // Create remove menu.
//        menuItems.add(createRemoveMenu(column));

        return menuItems;
    }

//    private Item createRenameMenu(final Column column) {
//        return new IconMenuItem.Builder()
//                .priority(0)
//                .icon(SvgImage.EDIT)
//                .text("Rename")
//                .command(() -> showRename(column))
//                .build();
//    }

//    private Item createExpressionMenu(final Column column) {
//        boolean highlight = false;
//        if (column.getExpression() != null) {
//            String expression = column.getExpression();
//            expression = expression.replaceAll("\\$\\{[^\\{\\}]*\\}", "");
//            expression = expression.trim();
//            if (expression.length() > 0) {
//                highlight = true;
//            }
//        }
//
//        return new IconMenuItem.Builder()
//                .priority(1)
//                .icon(SvgImage.FIELDS_EXPRESSION)
//                .text("Expression")
//                .command(() -> showExpression(column))
//                .highlight(highlight)
//                .build();
//    }

    private Item createSortMenu(final Column column) {
        final List<Item> menuItems = new ArrayList<>();
        menuItems.add(
                createSortOption(column,
                        0,
                        SortIcon.SORT_ASCENDING_ICON,
                        SortIcon.SORT_ASCENDING,
                        SortDirection.ASCENDING));
        menuItems.add(
                createSortOption(column,
                        1,
                        SortIcon.SORT_DESCENDING_ICON,
                        SortIcon.SORT_DESCENDING,
                        SortDirection.DESCENDING));
        // An action, so not SortIcon.UNSORTED, which names the state
        menuItems.add(createSortOption(column, 2, SortIcon.SORT_NONE, "Clear Sort", null));
        return new IconParentMenuItem.Builder()
                .priority(2)
                .icon(SortIcon.SORT_ASCENDING_ICON)
                .text(SortIcon.SORT)
                .children(menuItems)
                .highlight(column.getSort() != null)
                .build();
    }

    private Item createSortOption(final Column column,
                                  final int pos,
                                  final SvgImage icon,
                                  final String text,
                                  final SortDirection sortDirection) {
        return new IconMenuItem.Builder()
                .priority(pos)
                .icon(icon)
                .text(text)
                .command(() -> changeSort(column, sortDirection))
                .highlight(column.getSort() != null && column.getSort().getDirection() == sortDirection)
                .build();
    }

//    private Item createGroupByMenu(final Column column) {
//        final List<Item> menuItems = new ArrayList<>();
//        final int maxGroup = fixGroups(getColumns());
//        for (int i = 0; i < maxGroup; i++) {
//            final int group = i;
//            final Item item = new IconMenuItem.Builder()
//                    .priority(i)
//                    .icon(SvgImage.FIELDS_GROUP)
//                    .text("Level " + (i + 1))
//                    .command(() -> setGroup(column, group))
//                    .highlight(column.getGroup() != null && column.getGroup() == i)
//                    .build();
//            menuItems.add(item);
//        }
//
//        // Add the next possible group if the column isn't the only column in the
//        // next group.
//        if (addNextGroup(maxGroup, column)) {
//            final Item item = new IconMenuItem.Builder()
//                    .priority(maxGroup)
//                    .icon(SvgImage.FIELDS_GROUP)
//                    .text("Level " + (maxGroup + 1))
//                    .command(() -> setGroup(column, maxGroup))
//                    .build();
//            menuItems.add(item);
//        }
//
//        final Item item = new IconMenuItem.Builder()
//                .priority(maxGroup + 1)
//                .text("Not grouped")
//                .command(() -> setGroup(column, null))
//                .build();
//        menuItems.add(item);
//
//        return new IconParentMenuItem.Builder()
//                .priority(3)
//                .icon(SvgImage.FIELDS_GROUP)
//                .text("Group")
//                .children(menuItems)
//                .highlight(column.getGroup() != null)
//                .build();
//    }

//    private void setGroup(final Column column, final Integer group) {
//        if (!Objects.equals(column.getGroup(), group)) {
//            replaceColumn(column, column.copy().group(group).build());
//            fixGroups(getColumns());
//            tablePresenter.setDirty(true);
//            tablePresenter.updateColumns();
//            tablePresenter.refresh();
//        }
//    }
//
//    private boolean addNextGroup(final int maxGroup, final Column column) {
//        // If the column we are dragging has no group then add the next possible
//        // group.
//        if (column.getGroup() == null) {
//            return true;
//        }
//
//        // If the group the dragging column belongs to is not the current max
//        // group then add the next possible group.
//        if (column.getGroup() < maxGroup - 1) {
//            return true;
//        }
//
//        // If there is another column with the same group level as the dragging
//        // group then add the next possible group.
//        for (final Column col : getColumns()) {
//            if (col != column && col.getGroup() != null && col.getGroup() >= column.getGroup()) {
//                return true;
//            }
//        }
//
//        return false;
//    }
//
//    private int fixGroups(final List<Column> columns) {
//        // Make a map that groups columns by group depth.
//        final Map<Integer, List<Column>> map = new HashMap<>();
//        for (final Column column : columns) {
//            final Integer group = column.getGroup();
//            if (group != null) {
//                map.computeIfAbsent(group, k -> new ArrayList<>()).add(column);
//            }
//        }
//
//        // Fix group depths and add columns to grouped columns list.
//        final List<Integer> depths = new ArrayList<>(map.keySet());
//        Collections.sort(depths);
//
//        for (int i = 0; i < depths.size(); i++) {
//            final Integer depth = depths.get(i);
//            final List<Column> groupedColumns = map.get(depth);
//            for (final Column column : groupedColumns) {
//                replaceColumn(column, column.copy().group(i).build());
//            }
//        }
//
//        return depths.size();
//    }

    private Item createColumnFilterMenu(final Column column) {
        return new IconMenuItem.Builder()
                .priority(5)
                .icon(SvgImage.FILTER)
                .disabledIcon(SvgImage.FILTER)
                .text("Filter")
                .command(() -> filterColumn(column))
                .highlight(column.getColumnFilter() != null
                           && ((column.getColumnFilter().getFilter() != null
                                && !column.getColumnFilter().getFilter().trim().isEmpty())))
                .build();
    }

    private Item createValuesFilterMenu(final Column column, final int colIndex, final Element th) {
        return new IconMenuItem.Builder()
                .priority(5)
                .icon(SvgImage.VALUE_FILTER)
                .text("Filter Values")
                .command(() -> showValuesFilter(column, colIndex, th,
                        ElementUtil.findChild(th, "column-valueFilterIcon")))
                .highlight(column.getColumnValueSelection() != null
                           && column.getColumnValueSelection().isEnabled())
                .build();
    }

    private Item createFormatMenu(final Column column) {
        return new IconMenuItem.Builder()
                .priority(6)
                .icon(SvgImage.FIELDS_FORMAT)
                .text("Format")
                .command(() -> showFormat(column))
                .highlight(column.getFormat() != null && column.getFormat().getSettings() != null
                           && !column.getFormat().getSettings().isDefault())
                .build();
    }

    private Item createMoveFirstMenu(final Column column) {
        return new IconMenuItem.Builder()
                .priority(7)
                .icon(SvgImage.STEP_BACKWARD)
                .text("Move Column to Start")
                .command(() -> moveFirst(column))
                .build();
    }

    private Item createMoveLastMenu(final Column column) {
        return new IconMenuItem.Builder()
                .priority(8)
                .icon(SvgImage.STEP_FORWARD)
                .text("Move Column to End")
                .command(() -> moveLast(column))
                .build();
    }

//    private Item createDuplicateMenu(final Column column) {
//        return new IconMenuItem.Builder()
//                .priority(8)
//                .icon(SvgImage.COPY)
//                .text("Duplicate")
//                .command(() -> duplicateColumn(column))
//                .build();
//    }

    private Item createHideMenu(final Column column) {
        return new IconMenuItem.Builder()
                .priority(10)
                .icon(SvgImage.HIDE)
                .text("Hide Column")
                .command(() -> hideColumn(column))
                .build();
    }

    private Item createShowMenu() {
        final List<Item> menuItems = new ArrayList<>();

        int i = 0;
        for (final Column column : getColumns()) {
            if (!column.isVisible() && !column.isSpecial()) {
                final Item item2 = new IconMenuItem.Builder()
                        .priority(i++)
                        .icon(SvgImage.SHOW)
                        .text(ColumnHeaderHtmlUtil.getSafeHtml(column))
                        .command(() -> showColumn(column))
                        .build();
                menuItems.add(item2);
            }
        }

        if (menuItems.isEmpty()) {
            return null;
        }

        return new IconParentMenuItem.Builder()
                .priority(11)
                .icon(SvgImage.SHOW)
                .text("Show Hidden Column")
                .children(menuItems)
                .build();
    }

    private Item createConditionalFormattingMenu(final Column column) {
        return new IconMenuItem.Builder()
                .priority(12)
                .icon(SvgImage.FORMAT)
                .text("Edit Conditional Formatting for Table")
                .command(() -> {
                    final QueryTablePreferences queryTablePreferences = tablePresenter.getQueryTablePreferences();
                    // Made for one use, and owned by the table until it is closed
                    final RulesController rules = rulesProvider.get();
                    rules.read(queryTablePreferences);

                    final PopupSize popupSize = PopupSize.resizable(800, 650);
                    ControllerDialog.showOnce(tablePresenter, rules)
                            .popupType(PopupType.OK_CANCEL_DIALOG)
                            .popupSize(popupSize)
                            .caption("Settings")
                            .onShow(e -> rules.focus())
                            .onHideRequest(e -> {
                                if (e.isOk()) {
                                    if (rules.validate()) {
                                        // The dialog isn't modal, so the table may have changed
                                        // while it was open: the rules go on its preferences as
                                        // they are now
                                        final QueryTablePreferences current =
                                                tablePresenter.getQueryTablePreferences();
                                        final QueryTablePreferences updated = rules
                                                .write(current);
                                        if (!Objects.equals(updated, current)) {
                                            tablePresenter.setQueryTablePreferences(updated);
                                            tablePresenter.onChange();
                                            tablePresenter.refresh();
                                        }

                                        e.hide();
                                    } else {
                                        e.reset();
                                    }
                                } else {
                                    e.hide();
                                }
                            })
                            .fire();
                })
                .build();
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }
}
