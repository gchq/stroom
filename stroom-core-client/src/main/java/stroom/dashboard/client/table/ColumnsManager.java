/*
 * Copyright 2017 Crown Copyright
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

package stroom.dashboard.client.table;

import stroom.alert.client.event.AlertEvent;
import stroom.dashboard.client.main.UniqueUtil;
import stroom.data.grid.client.Heading;
import stroom.data.grid.client.HeadingListener;
import stroom.query.api.Column;
import stroom.query.api.ColumnFilter;
import stroom.query.api.ColumnValueSelection;
import stroom.query.api.Sort;
import stroom.query.api.Sort.SortDirection;
import stroom.query.client.presenter.ColumnHeaderHtmlUtil;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.menu.client.presenter.HideMenuEvent;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.IconParentMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.PopupLocation;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.Rect;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.user.cellview.client.SortIcon;
import com.google.gwt.user.client.Timer;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ColumnsManager implements HeadingListener, FilterCellManager, HasHandlers {

    private final EventBus eventBus;
    private final TablePresenter tablePresenter;
    private final Provider<RenameColumnPresenter> renameColumnPresenterProvider;
    private final Provider<ColumnFunctionEditorPresenter> expressionPresenterProvider;
    private final FormatPresenter formatPresenter;
    private final TableFilterPresenter tableFilterPresenter;
    private final ColumnValuesFilterPresenter columnValuesFilterPresenter;
    private int columnsStartIndex;
    private int currentMenuColIndex = -1;
    private int currentFilterColIndex = -1;
    private boolean moving;
    private final Map<String, String> currentQuickFilters = new HashMap<>();


    public ColumnsManager(final EventBus eventBus,
                          final TablePresenter tablePresenter,
                          final Provider<RenameColumnPresenter> renameColumnPresenterProvider,
                          final Provider<ColumnFunctionEditorPresenter> expressionPresenterProvider,
                          final FormatPresenter formatPresenter,
                          final TableFilterPresenter tableFilterPresenter,
                          final ColumnValuesFilterPresenter columnValuesFilterPresenter) {
        this.eventBus = eventBus;
        this.tablePresenter = tablePresenter;
        this.renameColumnPresenterProvider = renameColumnPresenterProvider;
        this.expressionPresenterProvider = expressionPresenterProvider;
        this.formatPresenter = formatPresenter;
        this.tableFilterPresenter = tableFilterPresenter;
        this.columnValuesFilterPresenter = columnValuesFilterPresenter;
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
                        columnValuesFilterPresenter.hide();
                    }
                }
            }
        }
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
                final HasHandlers columnsManager = this;
                final Column column = getColumn(colIndex);
                if (column != null) {
                    new Timer() {
                        @Override
                        public void run() {
                            final Element th = heading.getElement();
                            final Element button = ElementUtil.findChild(th, "column-valueFilterIcon");
                            final Element target = event.getEventTarget().cast();
                            final boolean isFilterButton = button.isOrHasChild(target);

                            if (currentFilterColIndex == colIndex) {
                                HidePopupRequestEvent.builder(columnValuesFilterPresenter).fire();

                            } else if (isFilterButton) {
                                currentFilterColIndex = colIndex;
                                columnValuesFilterPresenter.setNameFilter(currentQuickFilters.get(column.getId()));
                                final ColumnValuesDataSupplier dataSupplier = tablePresenter
                                        .getDataSupplier(column, null);
                                columnValuesFilterPresenter.show(
                                        () -> button,
                                        th,
                                        column,
                                        () -> dataSupplier,
                                        hideEvent -> {
                                            currentQuickFilters.put(
                                                    column.getId(),
                                                    columnValuesFilterPresenter.getNameFilter());
                                            resetFilterColIndex();
                                        },
                                        column.getColumnValueSelection(),
                                        ColumnsManager.this);
                            }

                            if (currentMenuColIndex == colIndex) {
                                HideMenuEvent.builder().fire(columnsManager);

                            } else if (!isFilterButton) {
                                currentMenuColIndex = colIndex;
                                final List<Item> menuItems = getMenuItems(column);

                                Rect relativeRect = new Rect(th);
                                relativeRect = relativeRect.grow(3);
                                final PopupPosition popupPosition = new PopupPosition(relativeRect,
                                        PopupLocation.BELOW);

                                ShowMenuEvent
                                        .builder()
                                        .items(menuItems)
                                        .popupPosition(popupPosition)
                                        .addAutoHidePartner(th)
                                        .onHide(e2 -> resetMenuColIndex())
                                        .fire(columnsManager);
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
            final List<Column> columns = tablePresenter.getTableComponentSettings().getColumns();
            columns.remove(column);

            final int destIndex = toIndex - columnsStartIndex;
            if (columns.size() <= destIndex) {
                columns.add(column);
            } else if (destIndex > fromIndex) {
                columns.add(destIndex - 1, column);
            } else {
                columns.add(destIndex, column);
            }

            tablePresenter.setDirty(true);
        }
    }

    @Override
    public void resizeColumn(final int colIndex, final int size) {
        final Column column = getColumn(colIndex);
        if (column != null) {
            replaceColumn(column, column.copy().width(size).build());
            tablePresenter.setDirty(true);
        }
    }

    private void changeSort(final Column column, final SortDirection direction) {
        final List<Column> columns = tablePresenter.getTableComponentSettings().getColumns();
        boolean change = false;

        if (direction == null) {
            if (column.getSort() != null) {
                final int order = column.getSort().getOrder();
                replaceColumn(column, column.copy().sort(null).build());
                increaseSortOrder(columns, order);
                change = true;
            }
        } else {
            final Sort sort = column.getSort();
            if (sort == null) {
                final int lowestSortOrder = getLowestSortOrder(columns);
                final Sort newSort = new Sort(lowestSortOrder + 1, direction);
                replaceColumn(column, column.copy().sort(newSort).build());
                change = true;
            } else {
                final int lowestSortOrder = getLowestSortOrder(columns);
                if (sort.getDirection() != direction || sort.getOrder() != lowestSortOrder) {
                    // Increase sort order on all columns where the sort order is
                    // lower than this columns sort order as this column will now
                    // have the lowest order.
                    increaseSortOrder(columns, column.getSort().getOrder());

                    final Sort newSort = new Sort(lowestSortOrder, direction);
                    replaceColumn(column, column.copy().sort(newSort).build());

                    change = true;
                }
            }
        }

        if (change) {
            tablePresenter.setDirty(true);
            tablePresenter.updateColumns();
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

    private void increaseSortOrder(final List<Column> columns, final int order) {
        for (final Column column : columns) {
            final Sort sort = column.getSort();
            if (sort != null && sort.getOrder() > order) {
                final Sort newSort = new Sort(sort.getOrder() - 1, sort.getDirection());
                replaceColumn(column, column.copy().sort(newSort).build());
            }
        }
    }

    public void showRename(final Column column) {
        renameColumnPresenterProvider.get().show(tablePresenter, column, (oldField, newField) -> {
            replaceColumn(oldField, newField);
            tablePresenter.setDirty(true);
            tablePresenter.updateColumns();
        });
    }

    public void showExpression(final Column column) {
        expressionPresenterProvider.get().show(tablePresenter, column, (oldField, newField) -> {
            replaceColumn(oldField, newField);
            tablePresenter.setDirty(true);
            tablePresenter.refresh();
        });
    }

    public void showFormat(final Column column) {
        formatPresenter.show(column, (oldField, newField) -> {
            replaceColumn(oldField, newField);
            tablePresenter.setDirty(true);
            tablePresenter.refresh();
        });
    }

    private void filterColumn(final Column column) {
        tableFilterPresenter.show(column, (oldField, newField) -> {
            replaceColumn(oldField, newField);

            if (newField.getColumnFilter() != null &&
                NullSafe.isNonBlankString(newField.getColumnFilter().getFilter())) {
                if (!tablePresenter.getTableComponentSettings().applyValueFilters()) {
                    tablePresenter.toggleApplyValueFilters();
                }
            }

            tablePresenter.setDirty(true);
            tablePresenter.updateColumns();
            tablePresenter.onColumnFilterChange();
        });
    }

    public void addColumn(final Column templateColumn) {
        addColumn(getColumns().size(), templateColumn);
    }

    public void addColumn(final int index, final Column templateColumn) {
        final String columnName = makeUniqueColumnName(templateColumn.getName());
        final Column newColumn = templateColumn.copy()
                .id(createRandomColumnId() + NullSafe.string(templateColumn.getId()))
                .name(columnName)
                .build();

        final List<Column> columns = getColumns();
        columns.add(index, newColumn);
        updateColumns(columns);

        tablePresenter.setDirty(true);
        tablePresenter.updateColumns();
        tablePresenter.refresh();
    }

    private void duplicateColumn(final Column column) {
        final List<Column> columns = getColumns();
        final int index = columns.indexOf(column);
        addColumn(index + 1, column);
    }

    private void moveFirst(final Column column) {
        final List<Column> columns = getColumns();
        columns.remove(column);
        columns.add(0, column);
        updateColumns(columns);
        tablePresenter.setDirty(true);
        tablePresenter.updateColumns();
    }

    private void moveLast(final Column column) {
        final List<Column> columns = getColumns();
        columns.remove(column);
        columns.add(column);
        updateColumns(columns);
        tablePresenter.setDirty(true);
        tablePresenter.updateColumns();
    }

    private String makeUniqueColumnName(final String columnName) {
        final Set<String> currentColumns = getColumns().stream().map(Column::getName).collect(
                Collectors.toSet());
        return UniqueUtil.makeUniqueName(columnName, currentColumns);
    }

    private String createRandomColumnId() {
        final Set<String> usedColumnIds = getColumns().stream().map(Column::getId).collect(Collectors.toSet());
        return createRandomColumnId(usedColumnIds);
    }

    public String createRandomColumnId(final Set<String> usedColumnIds) {
        final String componentId = tablePresenter.getComponentConfig().getId();
        return UniqueUtil.createUniqueColumnId(componentId, usedColumnIds);
    }

    private void deleteColumn(final Column column) {
        if (getVisibleColumnCount() <= 1) {
            AlertEvent.fireError(tablePresenter, "You cannot remove or hide all columns", null);
        } else {
            replaceColumn(column, null);

            tablePresenter.setDirty(true);
            tablePresenter.updateColumns();
            tablePresenter.refresh();
        }
    }

    private void replaceColumn(final Column oldColumn, final Column newColumn) {
        final List<Column> columns = new ArrayList<>();
        for (final Column column : getColumns()) {
            if (column.getId().equals(oldColumn.getId())) {
                if (newColumn != null) {
                    columns.add(newColumn);
                }
            } else {
                columns.add(column);
            }
        }
        updateColumns(columns);
    }

    @Override
    public void setValueFilter(final Column column,
                               final String valueFilter) {
        ColumnFilter columnFilter = null;
        if (NullSafe.isNonBlankString(valueFilter)) {
            // TODO : Add case sensitive option.
            columnFilter = new ColumnFilter(valueFilter);
        }

        if (!Objects.equals(column.getColumnFilter(), columnFilter)) {
            // Required to replace column filter in place so we don't need to re-render the table which would lose
            // focus from column filter textbox.
            column.setColumnFilter(columnFilter);

            replaceColumn(column, column.copy().columnFilter(columnFilter).build());
            tablePresenter.setFocused(false);
            tablePresenter.setDirty(true);
            tablePresenter.onColumnFilterChange();
        }
    }

    @Override
    public void setValueSelection(final Column column, final ColumnValueSelection columnValueSelection) {
        if (!Objects.equals(column.getColumnValueSelection(), columnValueSelection)) {
            // Required to replace column filter in place so we don't need to re-render the table which would lose
            // focus from column filter textbox.
            column.setColumnValueSelection(columnValueSelection);

            replaceColumn(column, column.copy().columnValueSelection(columnValueSelection).build());
            tablePresenter.setFocused(false);
            tablePresenter.setDirty(true);
            tablePresenter.updateColumns();
            tablePresenter.onColumnFilterChange();
        }
    }

    public List<Column> getColumns() {
        if (tablePresenter.getSettings() != null && tablePresenter.getTableComponentSettings().getColumns() != null) {
            return new ArrayList<>(tablePresenter.getTableComponentSettings().getColumns());
        }
        return new ArrayList<>();
    }

    private void updateColumns(final List<Column> columns) {
        tablePresenter.setSettings(
                tablePresenter.getTableComponentSettings()
                        .copy()
                        .columns(columns)
                        .build());
    }

    private void showColumn(final Column column) {
        replaceColumn(column, column.copy().visible(true).build());
        tablePresenter.setDirty(true);
        tablePresenter.updateColumns();
    }

    private void hideColumn(final Column column) {
        if (getVisibleColumnCount() <= 1) {
            AlertEvent.fireError(tablePresenter, "You cannot remove or hide all columns", null);
        } else {
            replaceColumn(column, column.copy().visible(false).build());
            tablePresenter.setDirty(true);
            tablePresenter.updateColumns();
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

    public void setColumnsStartIndex(final int columnsStartIndex) {
        this.columnsStartIndex = columnsStartIndex;
    }

    private List<Item> getMenuItems(final Column column) {
        final List<Item> menuItems = new ArrayList<>();

        // Create rename menu.
        menuItems.add(createRenameMenu(column));
        // Create expression menu.
        menuItems.add(createExpressionMenu(column));
        // Create sort menu.
        menuItems.add(createSortMenu(column));
        // Create group by menu.
        menuItems.add(createGroupByMenu(column));
        // Create format menu.
        menuItems.add(createFormatMenu(column));
        // Add column filter menu item.
        menuItems.add(createColumnFilterMenu(column));

        // Create move menu.
        menuItems.add(createMoveFirstMenu(column));
        menuItems.add(createMoveLastMenu(column));

        // Create duplicate menu.
        menuItems.add(createDuplicateMenu(column));

        // Create hide menu.
        menuItems.add(createHideMenu(column));

        // Create show menu.
        final Item showMenu = createShowMenu();
        if (showMenu != null) {
            menuItems.add(showMenu);
        }

        // Create remove menu.
        menuItems.add(createRemoveMenu(column));

        return menuItems;
    }

    private Item createRenameMenu(final Column column) {
        return new IconMenuItem.Builder()
                .priority(0)
                .icon(SvgImage.EDIT)
                .text("Rename")
                .command(() -> showRename(column))
                .build();
    }

    private Item createExpressionMenu(final Column column) {
        boolean highlight = false;
        if (column.getExpression() != null) {
            String expression = column.getExpression();
            expression = expression.replaceAll("\\$\\{[^\\{\\}]*\\}", "");
            expression = expression.trim();
            if (!expression.isEmpty()) {
                highlight = true;
            }
        }

        return new IconMenuItem.Builder()
                .priority(1)
                .icon(SvgImage.FIELDS_EXPRESSION)
                .text("Expression")
                .command(() -> showExpression(column))
                .highlight(highlight)
                .build();
    }

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
        menuItems.add(createSortOption(column, 2, SortIcon.SORT_NONE, SortIcon.UNSORTED, null));
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

    private Item createGroupByMenu(final Column column) {
        final List<Item> menuItems = new ArrayList<>();
        final int maxGroup = fixGroups(getColumns());
        for (int i = 0; i < maxGroup; i++) {
            final int group = i;
            final Item item = new IconMenuItem.Builder()
                    .priority(i)
                    .icon(SvgImage.FIELDS_GROUP)
                    .text("Level " + (i + 1))
                    .command(() -> setGroup(column, group))
                    .highlight(column.getGroup() != null && column.getGroup() == i)
                    .build();
            menuItems.add(item);
        }

        // Add the next possible group if the column isn't the only column in the
        // next group.
        if (addNextGroup(maxGroup, column)) {
            final Item item = new IconMenuItem.Builder()
                    .priority(maxGroup)
                    .icon(SvgImage.FIELDS_GROUP)
                    .text("Level " + (maxGroup + 1))
                    .command(() -> setGroup(column, maxGroup))
                    .build();
            menuItems.add(item);
        }

        final Item item = new IconMenuItem.Builder()
                .priority(maxGroup + 1)
                .text("Not grouped")
                .command(() -> setGroup(column, null))
                .build();
        menuItems.add(item);

        return new IconParentMenuItem.Builder()
                .priority(3)
                .icon(SvgImage.FIELDS_GROUP)
                .text("Group")
                .children(menuItems)
                .highlight(column.getGroup() != null)
                .build();
    }

    private void setGroup(final Column column, final Integer group) {
        if (!Objects.equals(column.getGroup(), group)) {
            replaceColumn(column, column.copy().group(group).build());
            fixGroups(getColumns());
            tablePresenter.setDirty(true);
            tablePresenter.updateColumns();
            tablePresenter.refresh();
        }
    }

    private boolean addNextGroup(final int maxGroup, final Column column) {
        // If the column we are dragging has no group then add the next possible
        // group.
        if (column.getGroup() == null) {
            return true;
        }

        // If the group the dragging column belongs to is not the current max
        // group then add the next possible group.
        if (column.getGroup() < maxGroup - 1) {
            return true;
        }

        // If there is another column with the same group level as the dragging
        // group then add the next possible group.
        for (final Column col : getColumns()) {
            if (col != column && col.getGroup() != null && col.getGroup() >= column.getGroup()) {
                return true;
            }
        }

        return false;
    }

    private int fixGroups(final List<Column> columns) {
        // Make a map that groups columns by group depth.
        final Map<Integer, List<Column>> map = new HashMap<>();
        for (final Column column : columns) {
            final Integer group = column.getGroup();
            if (group != null) {
                map.computeIfAbsent(group, k -> new ArrayList<>()).add(column);
            }
        }

        // Fix group depths and add columns to grouped columns list.
        final List<Integer> depths = new ArrayList<>(map.keySet());
        Collections.sort(depths);

        for (int i = 0; i < depths.size(); i++) {
            final Integer depth = depths.get(i);
            final List<Column> groupedColumns = map.get(depth);
            for (final Column column : groupedColumns) {
                replaceColumn(column, column.copy().group(i).build());
            }
        }

        return depths.size();
    }

    private Item createColumnFilterMenu(final Column column) {
        return new IconMenuItem.Builder()
                .priority(4)
                .icon(SvgImage.FILTER)
                .disabledIcon(SvgImage.FILTER)
                .text("Filter")
                .command(() -> filterColumn(column))
                .highlight((column.getFilter() != null
                            && ((column.getFilter().getIncludes() != null
                                 && !column.getFilter().getIncludes().trim().isEmpty())
                                || (column.getFilter().getExcludes() != null
                                    && !column.getFilter().getExcludes().trim().isEmpty())
                                || !column.getFilter().getIncludeDictionaries().isEmpty()
                                || !column.getFilter().getExcludeDictionaries().isEmpty())
                           ) ||
                           (column.getColumnFilter() != null
                            && ((column.getColumnFilter().getFilter() != null
                                 && !column.getColumnFilter().getFilter().trim().isEmpty()))))
                .build();
    }

    private Item createFormatMenu(final Column column) {
        return new IconMenuItem.Builder()
                .priority(5)
                .icon(SvgImage.FIELDS_FORMAT)
                .text("Format")
                .command(() -> showFormat(column))
                .highlight(column.getFormat() != null && column.getFormat().getSettings() != null
                           && !column.getFormat().getSettings().isDefault())
                .build();
    }

    private Item createMoveFirstMenu(final Column column) {
        return new IconMenuItem.Builder()
                .priority(6)
                .icon(SvgImage.STEP_BACKWARD)
                .text("Move First")
                .command(() -> moveFirst(column))
                .build();
    }

    private Item createMoveLastMenu(final Column column) {
        return new IconMenuItem.Builder()
                .priority(7)
                .icon(SvgImage.STEP_FORWARD)
                .text("Move Last")
                .command(() -> moveLast(column))
                .build();
    }

    private Item createDuplicateMenu(final Column column) {
        return new IconMenuItem.Builder()
                .priority(8)
                .icon(SvgImage.COPY)
                .text("Duplicate")
                .command(() -> duplicateColumn(column))
                .build();
    }

    private Item createHideMenu(final Column column) {
        return new IconMenuItem.Builder()
                .priority(9)
                .icon(SvgImage.HIDE)
                .text("Hide")
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
                .priority(10)
                .icon(SvgImage.SHOW)
                .text("Show")
                .children(menuItems)
                .build();
    }

    private Item createRemoveMenu(final Column column) {
        return new IconMenuItem.Builder()
                .priority(11)
                .icon(SvgImage.DELETE)
                .text("Remove")
                .command(() -> deleteColumn(column))
                .build();
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }
}
