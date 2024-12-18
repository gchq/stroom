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

package stroom.query.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.dashboard.client.table.ColumnFilterPresenter;
import stroom.dashboard.client.table.FormatPresenter;
import stroom.dashboard.client.table.HasValueFilter;
import stroom.dashboard.client.table.cf.RulesPresenter;
import stroom.data.grid.client.Heading;
import stroom.data.grid.client.HeadingListener;
import stroom.query.api.v2.Column;
import stroom.query.api.v2.ColumnFilter;
import stroom.query.api.v2.Sort;
import stroom.query.api.v2.Sort.SortDirection;
import stroom.query.shared.QueryTablePreferences;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.GwtNullSafe;
import stroom.widget.menu.client.presenter.HideMenuEvent;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.IconParentMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.PopupLocation;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.Rect;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.client.Timer;
import com.google.inject.Provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class QueryTableColumnsManager implements HeadingListener, HasValueFilter {

    private final QueryResultTablePresenter tablePresenter;
    private final FormatPresenter formatPresenter;
    private final Provider<RulesPresenter> rulesPresenterProvider;
    private final ColumnFilterPresenter columnFilterPresenter;
    private int columnsStartIndex;
    private int currentColIndex = -1;
    private boolean ignoreNext;

    public QueryTableColumnsManager(final QueryResultTablePresenter tablePresenter,
                                    final FormatPresenter formatPresenter,
                                    final Provider<RulesPresenter> rulesPresenterProvider,
                                    final ColumnFilterPresenter columnFilterPresenter) {
        this.tablePresenter = tablePresenter;
        this.formatPresenter = formatPresenter;
        this.rulesPresenterProvider = rulesPresenterProvider;
        this.columnFilterPresenter = columnFilterPresenter;
    }

    @Override
    public void onMouseDown(NativeEvent event, Heading heading) {
        int colIndex = -1;
        if (heading != null) {
            final Element element = Element.as(event.getEventTarget());
            final Element columnTop = ElementUtil.findParent(element, "column-top", 3);
            if (columnTop != null) {
                colIndex = heading.getColIndex();
            }
        }

        ignoreNext = currentColIndex == colIndex;
        HideMenuEvent
                .builder()
                .fire(tablePresenter);
    }

    @Override
    public void onMouseUp(final NativeEvent event, final Heading heading) {
        if (heading != null && heading.getColIndex() >= columnsStartIndex) {
            final int colIndex = heading.getColIndex();

            final Column column = getColumn(colIndex);
            if (column != null && !ignoreNext) {
                new Timer() {
                    @Override
                    public void run() {
                        if (currentColIndex == colIndex) {
                            HideMenuEvent
                                    .builder()
                                    .fire(tablePresenter);

                        } else {
                            currentColIndex = colIndex;
                            final List<Item> menuItems = getMenuItems(column);

                            Element element = event.getEventTarget().cast();
                            while (!element.getTagName().equalsIgnoreCase("th")) {
                                element = element.getParentElement();
                            }

                            Rect relativeRect = new Rect(element);
                            relativeRect = relativeRect.grow(3);
                            final PopupPosition popupPosition = new PopupPosition(relativeRect, PopupLocation.BELOW);

                            ShowMenuEvent
                                    .builder()
                                    .items(menuItems)
                                    .popupPosition(popupPosition)
                                    .addAutoHidePartner(element)
                                    .onHide(e2 -> currentColIndex = -1)
                                    .fire(tablePresenter);
                        }
                    }
                }.schedule(0);
            }
        }

        ignoreNext = false;
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
//            tablePresenter.setDirty(true);
        }
    }

    @Override
    public void resizeColumn(final int colIndex, final int size) {
        final Column column = getColumn(colIndex);
        if (column != null) {
            final List<Column> newColumns = replaceColumn(column, column.copy().width(size).build());
            tablePresenter.updateColumns(newColumns);
            tablePresenter.setDirty(true);
        }
    }

    private void changeSort(final Column column, final SortDirection direction) {
        final List<Column> columns = tablePresenter.getCurrentColumns();
        boolean change = false;

        if (direction == null) {
            if (column.getSort() != null) {
                final int order = column.getSort().getOrder();
                final List<Column> newColumns = replaceColumn(column, column.copy().sort(null).build());
                tablePresenter.updateColumns(newColumns);
                increaseSortOrder(columns, order);
                change = true;
            }
        } else {
            final Sort sort = column.getSort();
            if (sort == null) {
                final int lowestSortOrder = getLowestSortOrder(columns);
                final Sort newSort = new Sort(lowestSortOrder + 1, direction);
                final List<Column> newColumns = replaceColumn(column, column.copy().sort(newSort).build());
                tablePresenter.updateColumns(newColumns);
                change = true;
            } else {
                final int lowestSortOrder = getLowestSortOrder(columns);
                if (sort.getDirection() != direction || sort.getOrder() != lowestSortOrder) {
                    // Increase sort order on all columns where the sort order is
                    // lower than this columns sort order as this column will now
                    // have the lowest order.
                    increaseSortOrder(columns, column.getSort().getOrder());

                    final Sort newSort = new Sort(lowestSortOrder, direction);
                    final List<Column> newColumns = replaceColumn(column, column.copy().sort(newSort).build());
                    tablePresenter.updateColumns(newColumns);

                    change = true;
                }
            }
        }

        if (change) {
            tablePresenter.setDirty(true);
//            tablePresenter.updateColumns();
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
                final List<Column> newColumns = replaceColumn(column, column.copy().sort(newSort).build());
                tablePresenter.updateColumns(newColumns);
            }
        }
    }

    public void showFormat(final Column column) {
        formatPresenter.show(column, (oldColumn, newColumn) -> {
            final List<Column> newColumns = replaceColumn(oldColumn, newColumn);
            tablePresenter.updateColumns(newColumns);
            tablePresenter.setDirty(true);
            tablePresenter.refresh();
        });
    }

    private void filterColumn(final Column column) {
        columnFilterPresenter.setColumnFilter(column.getColumnFilter());
        columnFilterPresenter.show(column, (oldColumn, newColumn) -> {
            final List<Column> newColumns = replaceColumn(oldColumn, newColumn);
            tablePresenter.updateColumns(newColumns);
            tablePresenter.setDirty(true);
//            tablePresenter.updateColumns();
            tablePresenter.onColumnFilterChange();
        });
    }

    private void moveFirst(final Column column) {
        final List<Column> columns = getColumns();
        columns.remove(column);
        columns.add(0, column);
        tablePresenter.updateColumns(columns);
        tablePresenter.setPreferredColumns(columns);
//        tablePresenter.setDirty(true);
//        tablePresenter.updateColumns(columns);
    }

    private void moveLast(final Column column) {
        final List<Column> columns = getColumns();
        columns.remove(column);
        columns.add(column);
        tablePresenter.updateColumns(columns);
        tablePresenter.setPreferredColumns(columns);
//        tablePresenter.setDirty(true);
//        tablePresenter.updateColumns(columns);
    }

    private List<Column> replaceColumn(final Column oldColumn, final Column newColumn) {
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
        tablePresenter.setPreferredColumns(columns);
        return columns;
    }

    @Override
    public void setValueFilter(final Column column,
                               final String valueFilter) {
        ColumnFilter columnFilter = null;
        if (GwtNullSafe.isNonBlankString(valueFilter)) {
            // TODO : Add case sensitive option.
            columnFilter = new ColumnFilter(valueFilter);
        }

        if (!Objects.equals(column.getColumnFilter(), columnFilter)) {
            // Required to replace column filter in place so we don't need to re-render the table which would lose
            // focus from column filter textbox.
            column.setColumnFilter(columnFilter);

            if (columnFilter != null &&
                GwtNullSafe.isNonBlankString(columnFilter.getFilter())) {
                if (tablePresenter.getQueryTablePreferences() != null &&
                    !tablePresenter.getQueryTablePreferences().applyValueFilters()) {
                    tablePresenter.toggleApplyValueFilters();
                }
            }

            replaceColumn(column, column.copy().columnFilter(columnFilter).build());
//        tablePresenter.setDirty(true);
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
        final List<Column> newColumns = replaceColumn(column, column.copy().visible(true).build());
        tablePresenter.updateColumns(newColumns);
//        tablePresenter.setDirty(true);
//        tablePresenter.updateColumns(columns);
    }

    private void hideColumn(final Column column) {
        if (getVisibleColumnCount() <= 1) {
            AlertEvent.fireError(tablePresenter, "You cannot remove or hide all columns", null);
        } else {
            final List<Column> newColumns = replaceColumn(column, column.copy().visible(false).build());
            tablePresenter.updateColumns(newColumns);
//            tablePresenter.setDirty(true);
//            tablePresenter.updateColumns();
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
        for (Column column : columns) {
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

        // Create move menu.
        menuItems.add(createMoveFirstMenu(column));
        menuItems.add(createMoveLastMenu(column));

//        // Create duplicate menu.
//        menuItems.add(createDuplicateMenu(column));

        // Create hide menu.
        menuItems.add(createHideMenu(column));

        // Create show menu.
        Item showMenu = createShowMenu();
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
                        SvgImage.FIELDS_SORTAZ,
                        "Sort A to Z",
                        SortDirection.ASCENDING));
        menuItems.add(
                createSortOption(column,
                        1,
                        SvgImage.FIELDS_SORTZA,
                        "Sort Z to A",
                        SortDirection.DESCENDING));
        menuItems.add(createSortOption(column, 2, null, "Unsorted", null));
        return new IconParentMenuItem.Builder()
                .priority(2)
                .icon(SvgImage.FIELDS_SORTAZ)
                .text("Sort")
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
                                && column.getColumnFilter().getFilter().trim().length() > 0)))
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
                .text("Move First")
                .command(() -> moveFirst(column))
                .build();
    }

    private Item createMoveLastMenu(final Column column) {
        return new IconMenuItem.Builder()
                .priority(8)
                .icon(SvgImage.STEP_FORWARD)
                .text("Move Last")
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
                        .text(column.getName())
                        .command(() -> showColumn(column))
                        .build();
                menuItems.add(item2);
            }
        }

        if (menuItems.size() == 0) {
            return null;
        }

        return new IconParentMenuItem.Builder()
                .priority(11)
                .icon(SvgImage.SHOW)
                .text("Show")
                .children(menuItems)
                .build();
    }

    private Item createConditionalFormattingMenu(final Column column) {
        return new IconMenuItem.Builder()
                .priority(12)
                .icon(SvgImage.FORMAT)
                .text("Conditional Formatting")
                .command(() -> {
                    final QueryTablePreferences queryTablePreferences = tablePresenter.getQueryTablePreferences();
                    final RulesPresenter rulesPresenter = rulesPresenterProvider.get();
                    rulesPresenter.read(queryTablePreferences);

                    final PopupSize popupSize = PopupSize.resizable(800, 650);
                    ShowPopupEvent.builder(rulesPresenter)
                            .popupType(PopupType.OK_CANCEL_DIALOG)
                            .popupSize(popupSize)
                            .modal(true)
                            .caption("Settings")
                            .onShow(e -> rulesPresenter.focus())
                            .onHideRequest(e -> {
                                if (e.isOk()) {
                                    if (rulesPresenter.validate()) {
                                        final QueryTablePreferences updated = rulesPresenter
                                                .write(queryTablePreferences);
                                        if (!Objects.equals(updated, queryTablePreferences)) {
                                            tablePresenter.setQueryTablePreferences(updated);
                                            tablePresenter.setDirty(true);
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
}