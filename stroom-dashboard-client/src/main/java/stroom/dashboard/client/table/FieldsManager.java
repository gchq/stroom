/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.dashboard.client.table;

import com.google.gwt.core.shared.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.user.client.Timer;
import stroom.dashboard.shared.Field;
import stroom.dashboard.shared.Sort;
import stroom.dashboard.shared.Sort.SortDirection;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.data.grid.client.DataGridViewImpl.Heading;
import stroom.data.grid.client.DataGridViewImpl.HeadingListener;
import stroom.widget.button.client.GlyphIcons;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuListPresenter;
import stroom.widget.menu.client.presenter.SimpleParentMenuItem;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.VerticalLocation;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.tab.client.presenter.ImageIcon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FieldsManager implements HeadingListener {
    private static Resources resources;
    private final TablePresenter tablePresenter;
    private final ExpressionPresenter expressionPresenter;
    private final FormatPresenter formatPresenter;
    private final FilterPresenter filterPresenter;
    private final MenuListPresenter menuListPresenter;
    private int fieldsStartIndex;
    private boolean busy;
    private TableComponentSettings tableSettings;
    public FieldsManager(final TablePresenter tablePresenter, final MenuListPresenter menuListPresenter,
                         final ExpressionPresenter expressionPresenter, final FormatPresenter formatPresenter,
                         final FilterPresenter filterPresenter) {
        this.tablePresenter = tablePresenter;
        this.menuListPresenter = menuListPresenter;
        this.expressionPresenter = expressionPresenter;
        this.formatPresenter = formatPresenter;
        this.filterPresenter = filterPresenter;

        if (resources == null) {
            resources = GWT.create(Resources.class);
            resources.style().ensureInjected();
        }
    }

    public Resources getResources() {
        return resources;
    }

    @Override
    public void onContextMenu(final NativeEvent event, final Heading heading) {
        if (heading != null && heading.getColIndex() >= fieldsStartIndex) {
            final Field field = getField(heading.getColIndex());
            if (field != null) {
                busy = true;
                new Timer() {
                    @Override
                    public void run() {
                        final Element target = heading.getElement();
                        final PopupPosition popupPosition = new PopupPosition(target.getAbsoluteLeft(),
                                target.getAbsoluteRight(), target.getAbsoluteTop(), target.getAbsoluteBottom(), null,
                                VerticalLocation.BELOW);
                        final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
                            @Override
                            public void onHideRequest(final boolean autoClose, final boolean ok) {
                                HidePopupEvent.fire(tablePresenter, menuListPresenter);
                            }

                            @Override
                            public void onHide(final boolean autoClose, final boolean ok) {
                                busy = false;
                            }
                        };

                        updateMenuItems(field);
                        ShowPopupEvent.fire(tablePresenter, menuListPresenter, PopupType.POPUP, popupPosition,
                                popupUiHandlers);
                    }
                }.schedule(0);
            }
        }
    }

    @Override
    public boolean isBusy() {
        return busy;
    }

    public void setBusy(final boolean busy) {
        this.busy = busy;
    }

    public void setTableSettings(final TableComponentSettings tableSettings) {
        this.tableSettings = tableSettings;
    }

    @Override
    public void moveColumn(final int fromIndex, final int toIndex) {
        final Field field = getField(fromIndex);
        if (field != null) {
            final List<Field> fields = tableSettings.getFields();
            fields.remove(field);

            final int destIndex = toIndex - fieldsStartIndex;
            if (fields.size() <= destIndex) {
                fields.add(field);
            } else if (destIndex > fromIndex) {
                fields.add(destIndex - 1, field);
            } else {
                fields.add(destIndex, field);
            }

            tablePresenter.setDirty(true);
        }
    }

    @Override
    public void resizeColumn(final int colIndex, final int size) {
        final Field field = getField(colIndex);
        if (field != null) {
            field.setWidth(size);

            tablePresenter.setDirty(true);
        }
    }

    private void changeSort(final Field field, final SortDirection direction) {
        final List<Field> fields = tableSettings.getFields();
        boolean change = false;

        if (direction == null) {
            if (field.getSort() != null) {
                final int order = field.getSort().getOrder();
                field.setSort(null);
                increaseSortOrder(fields, order);
                change = true;
            }
        } else {
            if (field.getSort() == null) {
                final int lowestSortOrder = getLowestSortOrder(fields);
                field.setSort(new Sort(lowestSortOrder + 1, direction));
                change = true;
            } else {
                final int lowestSortOrder = getLowestSortOrder(fields);

                if (field.getSort().getDirection() != direction || field.getSort().getOrder() != lowestSortOrder) {
                    field.getSort().setDirection(direction);

                    // Increase sort order on all fields where the sort order is
                    // lower than this fields sort order as this field will now
                    // have the lowest order.
                    increaseSortOrder(fields, field.getSort().getOrder());

                    field.getSort().setOrder(lowestSortOrder);

                    change = true;
                }
            }
        }

        if (change) {
            tablePresenter.setDirty(true);
            tablePresenter.reset();
            tablePresenter.clearAndRefresh();
        }
    }

    private int getLowestSortOrder(final List<Field> fields) {
        int lowestOrder = -1;
        for (final Field field : fields) {
            if (field.getSort() != null) {
                if (lowestOrder < field.getSort().getOrder()) {
                    lowestOrder = field.getSort().getOrder();
                }
            }
        }

        return lowestOrder;
    }

    private void increaseSortOrder(final List<Field> fields, final int order) {
        for (final Field field : fields) {
            final Sort sort = field.getSort();
            if (sort != null && sort.getOrder() > order) {
                sort.setOrder(sort.getOrder() - 1);
            }
        }
    }

    public void showExpression(final Field field) {
        expressionPresenter.show(tablePresenter, field);
    }

    public void showFormat(final Field field) {
        formatPresenter.show(tablePresenter, field);
    }

    private void filterField(final Field field) {
        filterPresenter.show(tablePresenter, field);
    }

    public void addField(final Field field) {
        tableSettings.addField(field);
        tablePresenter.setDirty(true);
        tablePresenter.updateColumns();
        tablePresenter.clearAndRefresh();
    }

    private void deleteField(final Field field) {
        tableSettings.removeField(field);
        tablePresenter.setDirty(true);
        tablePresenter.updateColumns();
        tablePresenter.clearAndRefresh();
    }

    private Field getField(final int colIndex) {
        final List<Field> fields = tableSettings.getFields();
        final int index = colIndex - fieldsStartIndex;
        if (index >= 0 && index < fields.size()) {
            return fields.get(index);
        }

        return null;
    }

    public void setFieldsStartIndex(final int fieldsStartIndex) {
        this.fieldsStartIndex = fieldsStartIndex;
    }

    private void updateMenuItems(final Field field) {
        final List<Item> menuItems = new ArrayList<Item>();
        final Set<Item> highlights = new HashSet<>();

        // Create expression menu.
        menuItems.add(createExpressionMenu(field, highlights));
        // Create sort menu.
        menuItems.add(createSortMenu(field, highlights));
        // Create group by menu.
        menuItems.add(createGroupByMenu(field, highlights));
        // Create format menu.
        menuItems.add(createFormatMenu(field, highlights));
        // Add filter menu item.
        menuItems.add(createFilterMenu(field, highlights));

        // Create remove menu.
        menuItems.add(createRemoveMenu(field, highlights));

        menuListPresenter.setHighlightItems(highlights);
        menuListPresenter.setData(menuItems);
    }

    private Item createExpressionMenu(final Field field, final Set<Item> highlights) {
        final Item item = new IconMenuItem(0, ImageIcon.create(resources.expression()), null, "Expression", null, true, () -> showExpression(field));
        if (field.getExpression() != null) {
            String expression = field.getExpression();
            expression = expression.replaceAll("\\$\\{[^\\{\\}]*\\}", "");
            expression = expression.trim();
            if (expression.length() > 0) {
                highlights.add(item);
            }
        }
        return item;
    }

    private Item createSortMenu(final Field field, final Set<Item> highlights) {
        final List<Item> menuItems = new ArrayList<Item>();
        menuItems.add(
                createSortOption(field, highlights, 0, resources.sortaz(), "Sort A to Z", SortDirection.ASCENDING));
        menuItems.add(
                createSortOption(field, highlights, 1, resources.sortza(), "Sort Z to A", SortDirection.DESCENDING));
        menuItems.add(createSortOption(field, highlights, 2, null, "Unsorted", null));
        final Item item = new SimpleParentMenuItem(1, ImageIcon.create(resources.sortaz()), null, "Sort", null, true, menuItems);
        if (field.getSort() != null) {
            highlights.add(item);
        }
        return item;
    }

    private Item createSortOption(final Field field, final Set<Item> highlights, final int pos,
                                  final ImageResource icon, final String text, final SortDirection sortDirection) {
        final Item item = new IconMenuItem(pos, ImageIcon.create(icon), null, text, null, true, () -> changeSort(field, sortDirection));
        if (field.getSort() != null && field.getSort().getDirection() == sortDirection) {
            highlights.add(item);
        }
        return item;
    }

    private Item createGroupByMenu(final Field field, final Set<Item> highlights) {
        final List<Item> menuItems = new ArrayList<Item>();
        final int maxGroup = fixGroups(tableSettings.getFields());
        for (int i = 0; i < maxGroup; i++) {
            final int group = i;
            final Item item = new IconMenuItem(i, ImageIcon.create(resources.group()), null, "Level " + (i + 1), null, true,
                    () -> setGroup(field, group));
            menuItems.add(item);

            if (field.getGroup() != null && field.getGroup() == i) {
                highlights.add(item);
            }
        }

        // Add the next possible group if the field isn't the only field in the
        // next group.
        if (addNextGroup(maxGroup, field)) {
            final Item item = new IconMenuItem(maxGroup, ImageIcon.create(resources.group()), null, "Level " + (maxGroup + 1), null,
                    true, () -> setGroup(field, maxGroup));
            menuItems.add(item);
        }

        final Item item = new IconMenuItem(maxGroup + 1, "Not grouped", null, true, () -> setGroup(field, null));
        menuItems.add(item);

        final Item parentItem = new SimpleParentMenuItem(2, ImageIcon.create(resources.group()), null, "Group", null, true, menuItems);

        if (field.getGroup() != null) {
            highlights.add(parentItem);
        }

        return parentItem;
    }

    private void setGroup(final Field field, final Integer group) {
        if (field.getGroup() != group) {
            field.setGroup(group);
            fixGroups(tableSettings.getFields());
            tablePresenter.setDirty(true);
            tablePresenter.updateColumns();
            tablePresenter.clearAndRefresh();
        }
    }

    private boolean addNextGroup(final int maxGroup, final Field field) {
        // If the field we are dragging has no group then add the next possible
        // group.
        if (field.getGroup() == null) {
            return true;
        }

        // If the group the dragging field belongs to is not the current max
        // group then add the next possible group.
        if (field.getGroup() < maxGroup - 1) {
            return true;
        }

        // If there is another field with the same group level as the dragging
        // group then add the next possible group.
        for (final Field fld : tableSettings.getFields()) {
            if (fld != field && fld.getGroup() != null && fld.getGroup() >= field.getGroup()) {
                return true;
            }
        }

        return false;
    }

    private int fixGroups(final List<Field> fields) {
        // Make a map that groups fields by group depth.
        final Map<Integer, List<Field>> map = new HashMap<Integer, List<Field>>();
        for (final Field field : fields) {
            final Integer group = field.getGroup();
            if (group != null) {
                List<Field> groupedFields = map.get(group);
                if (groupedFields == null) {
                    groupedFields = new ArrayList<Field>();
                    map.put(group, groupedFields);
                }

                groupedFields.add(field);
            }
        }

        // Fix group depths and add fields to grouped fields list.
        final List<Integer> depths = new ArrayList<Integer>(map.keySet());
        Collections.sort(depths);

        for (int i = 0; i < depths.size(); i++) {
            final Integer depth = depths.get(i);
            final List<Field> groupedFields = map.get(depth);
            for (final Field field : groupedFields) {
                field.setGroup(i);
            }
        }

        return depths.size();
    }

    private Item createFilterMenu(final Field field, final Set<Item> highlights) {
        final Item item = new IconMenuItem(3, GlyphIcons.FILTER, GlyphIcons.FILTER, "Filter", null, true, () -> filterField(field));
        if (field.getFilter() != null && ((field.getFilter().getIncludes() != null
                && field.getFilter().getIncludes().trim().length() > 0)
                || (field.getFilter().getExcludes() != null && field.getFilter().getExcludes().trim().length() > 0))) {
            highlights.add(item);
        }
        return item;
    }

    private Item createFormatMenu(final Field field, final Set<Item> highlights) {
        final Item item = new IconMenuItem(4, ImageIcon.create(resources.format()), null, "Format", null, true, () -> showFormat(field));
        if (field.getFormat() != null && field.getFormat().getSettings() != null
                && !field.getFormat().getSettings().isDefault()) {
            highlights.add(item);
        }
        return item;
    }

    private Item createRemoveMenu(final Field field, final Set<Item> highlights) {
        final Item item = new IconMenuItem(5, GlyphIcons.REMOVE, GlyphIcons.REMOVE, "Remove", null, true, () -> deleteField(field));
        return item;
    }

    public interface Style extends CssResource {
        String labels();

        String label();

        String row();

        String fieldLabel();

        String fieldText();

        String sortOrder();

        String buttons();
    }

    public interface Resources extends ClientBundle {
        ImageResource expression();

        ImageResource sortaz();

        ImageResource sortza();

        ImageResource group();

        ImageResource format();

        ImageResource filter();

        @Source("fields.css")
        Style style();
    }
}
