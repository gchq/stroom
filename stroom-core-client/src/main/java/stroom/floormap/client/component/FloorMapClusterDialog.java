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

import stroom.floormap.client.FloorMapCellHtml;
import stroom.floormap.client.cluster.FloorMapClusterFilter;
import stroom.floormap.client.cluster.FloorMapClusterMember;
import stroom.floormap.client.overlay.FloorMapAreaCellText;
import stroom.item.client.SelectionBox;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.SingleSelectionEventManager;
import stroom.widget.form.client.FormGroup;
import stroom.widget.quickfilter.client.QuickFilter;
import stroom.widget.util.client.HtmlBuilder;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.TextColumn;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.CellPreviewEvent;
import com.google.gwt.view.client.ListDataProvider;
import com.google.gwt.view.client.SingleSelectionModel;

import java.util.List;

/// The content of the cluster member dialog: a filter bar over the member grid.
///
/// The three controls are equals — the search box and both dropdowns narrow the same list — so
/// they sit on one line rather than the search box being promoted above the filters.
///
/// A dropdown with nothing to choose between is *hidden* rather than disabled (see
/// [#setAreaFilterOptions]): a cluster standing wholly inside one room has nothing to say about
/// areas, and a greyed control would only invite the user to work out why.
public class FloorMapClusterDialog extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    /// Column text for a member that is not inside any area.
    private static final String NO_AREA = "—";

    /// Column text for a member that belongs to no group.
    private static final String NO_GROUP = "—";

    @UiField
    QuickFilter quickFilter;
    @UiField
    FormGroup areaFilterGroup;
    @UiField
    SelectionBox<String> areaFilter;
    @UiField
    FormGroup groupFilterGroup;
    @UiField
    SelectionBox<String> groupFilter;
    @UiField
    ToolbarButton trackButton;
    @UiField
    DataGrid<FloorMapClusterMember> dataGrid;

    private final ListDataProvider<FloorMapClusterMember> dataProvider = new ListDataProvider<>();
    private final SingleSelectionModel<FloorMapClusterMember> selectionModel = new SingleSelectionModel<>();
    private Runnable onFilterChange = () -> {
    };
    private Runnable onTrack = () -> {
    };

    /// True while the controls are being repopulated for a new cluster, so the resulting value
    /// changes are not reported as the user filtering. Without it every showing would run the
    /// filter several times over, and — worse — would do so between clearing the options and
    /// setting the new ones, when the dropdowns momentarily hold values belonging to the previous
    /// cluster.
    private boolean populating;

    /// Makes an empty dialog content, with no members.
    public FloorMapClusterDialog() {
        initWidget(BINDER.createAndBindUi(this));
        trackButton.setOnClick(this::onTrackButton);

        // The widget's default help text describes the platform's field-qualified quick filter,
        // which this box is not — it says what this one actually does, including the one rule a
        // user could not guess (several words all have to match).
        quickFilter.registerPopupTextProvider(() -> new HtmlBuilder()
                .bold("Search")
                .br()
                .append("Matches anywhere in a member's name, id, type, area or group.")
                .br()
                .append("Every word has to match something, so \"ali bay\" finds Alice "
                        + "in the Loading Bay.")
                .br()
                .toSafeHtml());

        quickFilter.addValueChangeHandler(e -> fireFilterChange());
        areaFilter.addValueChangeHandler(e -> fireFilterChange());
        groupFilter.addValueChangeHandler(e -> fireFilterChange());

        // Rows keyed by id, so a row whose values change is still the same row
        dataGrid.setKeyProvider(FloorMapClusterMember::getId);
        dataGrid.setSelectionModel(selectionModel, new SingleSelectionEventManager<>(dataGrid, selectionModel));
        dataProvider.addDataDisplay(dataGrid);
        initGridColumns();

        selectionModel.addSelectionChangeHandler(e ->
                trackButton.setEnabled(selectionModel.getSelectedObject() != null));

        // Double-click is the shortcut for the button, so a member can be followed without a
        // second aim at the toolbar.
        dataGrid.addCellPreviewHandler((final CellPreviewEvent<FloorMapClusterMember> e) -> {
            if ("dblclick".equals(e.getNativeEvent().getType())) {
                selectionModel.setSelected(e.getValue(), true);
                onTrack.run();
            }
        });
    }

    /// Populates the Area dropdown, or hides it.
    ///
    /// @param options The options from [FloorMapClusterFilter#areaOptions], whose first entry is
    ///                the "any" option. An **empty** list hides the control: a dropdown whose every
    ///                option selects the same rows is furniture.
    public void setAreaFilterOptions(final List<String> options) {
        populate(areaFilterGroup, areaFilter, options);
    }

    /// Populates the Group dropdown, or hides it.
    ///
    /// @param options The options from [FloorMapClusterFilter#groupOptions]; empty hides the
    ///                control.
    public void setGroupFilterOptions(final List<String> options) {
        populate(groupFilterGroup, groupFilter, options);
    }

    /// @return The current search text; never `null`.
    public String getSearchText() {
        return quickFilter.getText();
    }

    /// @return The selected Area option, or `null` when not offered.
    public String getAreaFilter() {
        // A hidden control constrains nothing; its stale value must not survive into a cluster
        // whose members it was never built from.
        return areaFilterGroup.isVisible()
                ? areaFilter.getValue()
                : null;
    }

    /// @return The selected Group option, or `null` when not offered.
    public String getGroupFilter() {
        return groupFilterGroup.isVisible()
                ? groupFilter.getValue()
                : null;
    }

    /// Empties the search box and returns both dropdowns to their "any" option **without**
    /// calling the filter callback — the caller is mid-rebuild and applies the filter itself once
    /// the new options are in place.
    public void clearFilters() {
        populating = true;
        try {
            // setText DOES fire a value change, so the guard is load-bearing here: without it,
            // emptying the box would filter the outgoing cluster's members a moment before the new
            // ones arrive.
            quickFilter.setText("");
            areaFilter.clear();
            groupFilter.clear();
        } finally {
            populating = false;
        }
    }

    /// Puts the caret in the search box.
    public void focusSearch() {
        quickFilter.forceFocus();
    }

    /// Shows the members that pass the filters. A selected member no longer shown is deselected:
    /// leaving it selected would leave the Track button live for a member no longer on screen,
    /// which is one click away from following someone the user cannot see.
    ///
    /// @param members The members to show.
    public void setMembers(final List<FloorMapClusterMember> members) {
        dataProvider.setList(members);
        final FloorMapClusterMember selected = selectionModel.getSelectedObject();
        if (selected != null && !members.contains(selected)) {
            selectionModel.clear();
        }
    }

    /// @return The member chosen, or `null` if none is.
    public FloorMapClusterMember getSelected() {
        return selectionModel.getSelectedObject();
    }

    /// Chooses no member, which disables the Track button.
    public void clearSelection() {
        selectionModel.clear();
        trackButton.setEnabled(false);
    }

    /// @param onFilterChange Called when the user changes the search or either dropdown.
    public void setOnFilterChange(final Runnable onFilterChange) {
        this.onFilterChange = onFilterChange;
    }

    /// @param onTrack Called when the user asks to track the chosen member, with the Track button
    ///                or a double click on its row.
    public void setOnTrack(final Runnable onTrack) {
        this.onTrack = onTrack;
    }

    private void onTrackButton() {
        onTrack.run();
    }

    private void fireFilterChange() {
        if (!populating) {
            onFilterChange.run();
        }
    }

    /// Fills one dropdown and shows it, or hides it when there is nothing to choose between. The
    /// first option is the "any" one, and is selected — a freshly shown dialog filters nothing.
    private void populate(final FormGroup group,
                          final SelectionBox<String> box,
                          final List<String> options) {
        populating = true;
        try {
            box.clear();
            final boolean offered = options != null && !options.isEmpty();
            if (offered) {
                box.addItems(options);
                box.setValue(options.get(0));
            }
            group.setVisible(offered);
        } finally {
            populating = false;
        }
    }

    private void initGridColumns() {
        final Column<FloorMapClusterMember, String> nameColumn = new TextColumn<>() {
            @Override
            public String getValue(final FloorMapClusterMember member) {
                return member.getName();
            }
        };
        dataGrid.addResizableColumn(nameColumn, "Name", 200);

        final Column<FloorMapClusterMember, String> typeColumn = new TextColumn<>() {
            @Override
            public String getValue(final FloorMapClusterMember member) {
                return member.getType();
            }
        };
        dataGrid.addColumn(typeColumn, "Type");

        // Which area is this member inside? Named innermost first, or a dash.
        // Resizable because area names are user-chosen and can be long.
        final Column<FloorMapClusterMember, SafeHtml> areaColumn = new Column<>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final FloorMapClusterMember member) {
                return areaCell(member);
            }
        };
        dataGrid.addResizableColumn(areaColumn, "Area", 200);

        // Shown even on a map with no groups, exactly as the Area column is shown on a map with no
        // areas. A column that came and went would shift the ones beside it between one cluster
        // and the next.
        final Column<FloorMapClusterMember, SafeHtml> groupColumn = new Column<>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final FloorMapClusterMember member) {
                return groupCell(member);
            }
        };
        dataGrid.addResizableColumn(groupColumn, "Group", 150);

        final Column<FloorMapClusterMember, String> idColumn = new TextColumn<>() {
            @Override
            public String getValue(final FloorMapClusterMember member) {
                return member.getId();
            }
        };
        dataGrid.addColumn(idColumn, "Id");
    }

    /// Renders one Area cell. The cell is a single `nowrap` line that ellipsises, so where a
    /// member is in several areas the tooltip repeats the list in full, one per line.
    private static SafeHtml areaCell(final FloorMapClusterMember member) {
        final List<String> areaNames = member.getAreaNames();
        if (areaNames.isEmpty()) {
            return FloorMapCellHtml.cell(NO_AREA, "Not in a known area at this time");
        }
        final String joined = FloorMapAreaCellText.joinNames(areaNames);
        if (areaNames.size() == 1) {
            return FloorMapCellHtml.cell(joined, "Inside " + joined);
        }
        final StringBuilder tooltip = new StringBuilder("Inside ")
                .append(areaNames.size())
                .append(" areas (innermost first):");
        for (final String name : areaNames) {
            tooltip.append("\n• ").append(name);
        }
        return FloorMapCellHtml.cell(joined, tooltip.toString());
    }

    /// Renders one Group cell: every group the member belongs to, with the full list repeated in
    /// the tooltip for when the column is too narrow to show it.
    private static SafeHtml groupCell(final FloorMapClusterMember member) {
        final List<String> groupNames = member.getGroupNames();
        if (groupNames.isEmpty()) {
            return FloorMapCellHtml.cell(NO_GROUP, "Not in a group");
        }
        final String joined = FloorMapAreaCellText.joinNames(groupNames);
        if (groupNames.size() == 1) {
            return FloorMapCellHtml.cell(joined, "In " + joined);
        }
        final StringBuilder tooltip = new StringBuilder("In ")
                .append(groupNames.size())
                .append(" groups:");
        for (final String name : groupNames) {
            tooltip.append("\n• ").append(name);
        }
        return FloorMapCellHtml.cell(joined, tooltip.toString());
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, FloorMapClusterDialog> {

    }
}
