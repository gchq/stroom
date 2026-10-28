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

package stroom.floormap.client.component;

import stroom.cell.info.client.CellIcon;
import stroom.cell.info.client.IconCell;
import stroom.data.client.ColumnSizeConstants;
import stroom.floormap.client.FloorMapCellHtml;
import stroom.floormap.client.FloorMapSwatchHtml;
import stroom.floormap.client.overlay.FloorMapAreaCellText;
import stroom.floormap.client.overlay.FloorMapGroupSnapshot;
import stroom.floormap.shared.FloorMapGroup;
import stroom.floormap.shared.TypeStyle;
import stroom.floormap.shared.TypeStyle.Shape;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.SingleSelectionEventManager;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.TextColumn;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.ListDataProvider;
import com.google.gwt.view.client.SingleSelectionModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/// The floor map's Groups panel: a row per group (whether it is highlighted on the map, its name and
/// colour, its members, how many have a position now and which areas they are in), one of which can
/// be selected, and the actions on it.
public class FloorMapGroups extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    // Column text for a group with nothing to show
    private static final String NONE = "—";
    // The colour swatch on each row, in pixels
    private static final int SWATCH_SIZE_PX = 12;

    @UiField
    ToolbarButton newButton;
    @UiField
    ToolbarButton editButton;
    @UiField
    ToolbarButton deleteButton;
    @UiField
    DataGrid<FloorMapGroup> dataGrid;

    private final ListDataProvider<FloorMapGroup> dataProvider = new ListDataProvider<>();
    private final SingleSelectionModel<FloorMapGroup> selectionModel = new SingleSelectionModel<>();

    private Set<String> shownGroupIds = Collections.emptySet();
    private FloorMapGroupSnapshot snapshot = FloorMapGroupSnapshot.EMPTY;
    private Function<String, String> nameResolver;

    private Runnable onNew = () -> {
    };
    private Consumer<FloorMapGroup> onEdit = group -> {
    };
    private Consumer<FloorMapGroup> onDelete = group -> {
    };
    private Consumer<FloorMapGroup> onToggleHighlight = group -> {
    };

    /// Makes an empty panel.
    public FloorMapGroups() {
        initWidget(BINDER.createAndBindUi(this));
        // Rows keyed by id, so a row whose values change is still the same row
        dataGrid.setKeyProvider(FloorMapGroup::getId);
        dataGrid.setSelectionModel(selectionModel, new SingleSelectionEventManager<>(dataGrid, selectionModel));
        addColumns();
        dataProvider.addDataDisplay(dataGrid);
        newButton.setOnClick(() -> onNew.run());
        editButton.setOnClick(() -> withSelected(onEdit));
        deleteButton.setOnClick(() -> withSelected(onDelete));
        selectionModel.addSelectionChangeHandler(e -> {
            final boolean hasSelection = selectionModel.getSelectedObject() != null;
            editButton.setEnabled(hasSelection);
            deleteButton.setEnabled(hasSelection);
        });
    }

    /// Shows the groups in place of those shown, keeping the selected group selected (by id, as an
    /// edited group is a new value).
    ///
    /// @param groups The groups, in order.
    public void setGroups(final List<FloorMapGroup> groups) {
        final FloorMapGroup selected = selectionModel.getSelectedObject();
        final List<FloorMapGroup> visible = new ArrayList<>(groups);
        dataProvider.setList(visible);
        dataGrid.setRowData(0, visible);
        if (selected != null) {
            final FloorMapGroup current = FloorMapGroup.find(visible, selected.getId());
            if (current != null) {
                selectionModel.setSelected(current, true);
            } else {
                selectionModel.clear();
            }
        }
    }

    /// @param group The group to select.
    public void select(final FloorMapGroup group) {
        selectionModel.setSelected(group, true);
    }

    /// Selects no group.
    public void clearSelection() {
        selectionModel.clear();
    }

    /// @param shownGroupIds The ids of the groups highlighted on the map, as the eye on each row
    ///                      shows (read as rows are drawn).
    public void setShownGroupIds(final Set<String> shownGroupIds) {
        this.shownGroupIds = shownGroupIds;
    }

    /// @param snapshot The groups' live counts: who has a position now, and in which areas.
    public void setSnapshot(final FloorMapGroupSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    /// @param nameResolver Names any entity (an area, a person, an object) by its id; null to show
    ///                     ids.
    public void setNameResolver(final Function<String, String> nameResolver) {
        this.nameResolver = nameResolver;
    }

    /// Draws the rows again, e.g. once the counts, names or highlights have changed.
    public void redraw() {
        dataGrid.redraw();
    }

    /// @param onNew Run when New Group is pressed.
    public void setOnNew(final Runnable onNew) {
        this.onNew = onNew;
    }

    /// @param onEdit Given the selected group when Edit Group is pressed.
    public void setOnEdit(final Consumer<FloorMapGroup> onEdit) {
        this.onEdit = onEdit;
    }

    /// @param onDelete Given the selected group when Delete Group is pressed.
    public void setOnDelete(final Consumer<FloorMapGroup> onDelete) {
        this.onDelete = onDelete;
    }

    /// @param onToggleHighlight Given a group whose eye is pressed, to highlight it or stop.
    public void setOnToggleHighlight(final Consumer<FloorMapGroup> onToggleHighlight) {
        this.onToggleHighlight = onToggleHighlight;
    }

    private void withSelected(final Consumer<FloorMapGroup> action) {
        final FloorMapGroup selected = selectionModel.getSelectedObject();
        if (selected != null) {
            action.accept(selected);
        }
    }

    private void addColumns() {
        // Highlight: a button cell, so clicking the eye highlights the group or stops without
        // selecting the row
        final Column<FloorMapGroup, CellIcon> highlightColumn = new Column<>(new IconCell()) {
            @Override
            public CellIcon getValue(final FloorMapGroup group) {
                return highlightIcon(shownGroupIds.contains(group.getId()));
            }
        };
        //noinspection unused index, val
        highlightColumn.setFieldUpdater((index, group, val) -> onToggleHighlight.accept(group));
        dataGrid.addColumn(highlightColumn, "", ColumnSizeConstants.ICON_COL);

        // Name, led by a swatch in the group's highlight colour
        dataGrid.addResizableColumn(new Column<>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final FloorMapGroup group) {
                return nameCell(group);
            }
        }, "Name", 140);

        // Every member, however many are on the map now
        dataGrid.addColumn(new TextColumn<>() {
            @Override
            public String getValue(final FloorMapGroup group) {
                return String.valueOf(group.countMembers());
            }
        }, "Members", ColumnSizeConstants.SMALL_COL);

        // Members with a position now
        dataGrid.addColumn(new Column<FloorMapGroup, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final FloorMapGroup group) {
                return positionedCell(group);
            }
        }, "Positioned", ColumnSizeConstants.SMALL_COL);

        // The areas they are in: resizable, as area names are the user's and can be long
        dataGrid.addResizableColumn(new Column<>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final FloorMapGroup group) {
                return areasCell(group);
            }
        }, "Areas", 200);
    }

    // The eye, worded for what a click does next
    private static CellIcon highlightIcon(final boolean shown) {
        return CellIcon.of(
                shown
                        ? SvgImage.EYE
                        : SvgImage.EYE_OFF,
                shown
                        ? "Highlighted on the map — click to stop highlighting"
                        : "Not highlighted — click to highlight this group's members",
                true);
    }

    // A swatch in the group's colour, its name, and a tooltip naming every member
    private SafeHtml nameCell(final FloorMapGroup group) {
        final SafeHtml swatch = FloorMapSwatchHtml.swatch(
                new TypeStyle(null, Shape.CIRCLE, group.findColourOrDefault()), SWATCH_SIZE_PX);
        return FloorMapCellHtml.cellWithSwatch(swatch, group.getName(), membersTooltip(group));
    }

    // Every member named, one a line: never "+N", which would hide the names looked for
    private String membersTooltip(final FloorMapGroup group) {
        if (group.countMembers() == 0) {
            return "No members yet — use Edit Group to add some";
        }
        final StringBuilder tooltip = new StringBuilder();
        tooltip.append(group.countMembers() == 1
                ? "1 member:"
                : group.countMembers() + " members:");
        for (final String memberId : group.getMemberIds()) {
            tooltip.append("\n• ").append(nameFor(memberId));
            if (!snapshot.getPositionedIds(group.getId()).contains(memberId)) {
                // Tells "not on the map now" from "not a member", which the count can't
                tooltip.append(" (no position at this time)");
            }
        }
        return tooltip.toString();
    }

    // How many members are on the map now, of how many
    private SafeHtml positionedCell(final FloorMapGroup group) {
        final int positioned = snapshot.getPositionedCount(group.getId());
        final int total = group.countMembers();
        if (total == 0) {
            return FloorMapCellHtml.cell(NONE, "No members yet");
        }
        final String text = positioned + " of " + total;
        final String tooltip = positioned == total
                ? "Every member has a position at this time"
                : positioned + " of " + total + " members have a position at this time."
                  + " A member whose events have gone quiet has no position, so this"
                  + " can fall without anyone moving.";
        return FloorMapCellHtml.cell(text, tooltip);
    }

    // Every area the positioned members are in, with how many in each, most first
    private SafeHtml areasCell(final FloorMapGroup group) {
        final Map<String, Integer> areaCounts = snapshot.getAreaCounts(group.getId());
        if (areaCounts.isEmpty()) {
            final int positioned = snapshot.getPositionedCount(group.getId());
            return FloorMapCellHtml.cell(NONE, positioned == 0
                    ? "No member has a position at this time"
                    : "No member is inside a known area at this time");
        }
        final List<String> names = new ArrayList<>(areaCounts.size());
        final List<Integer> counts = new ArrayList<>(areaCounts.size());
        for (final Map.Entry<String, Integer> entry : areaCounts.entrySet()) {
            names.add(nameFor(entry.getKey()));
            counts.add(entry.getValue());
        }
        final String joined = FloorMapAreaCellText.joinNamesWithCounts(names, counts);
        final StringBuilder tooltip = new StringBuilder(areaCounts.size() == 1
                ? "In 1 area:"
                : "In " + areaCounts.size() + " areas (most members first):");
        for (int i = 0; i < names.size(); i++) {
            tooltip.append("\n• ").append(names.get(i))
                    .append(" — ").append(counts.get(i))
                    .append(counts.get(i) == 1
                            ? " member"
                            : " members");
        }
        return FloorMapCellHtml.cell(joined, tooltip.toString());
    }

    // An entity's name, or its id when there is no resolver or it doesn't know it
    private String nameFor(final String id) {
        if (id == null) {
            return "";
        }
        if (nameResolver != null) {
            final String name = nameResolver.apply(id);
            if (name != null && !name.isEmpty()) {
                return name;
            }
        }
        return id;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, FloorMapGroups> {

    }
}
