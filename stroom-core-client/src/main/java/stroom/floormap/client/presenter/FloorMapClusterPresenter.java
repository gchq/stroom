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

package stroom.floormap.client.presenter;

import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.floormap.client.FloorMapCellHtml;
import stroom.floormap.client.cluster.FloorMapCluster;
import stroom.floormap.client.cluster.FloorMapClusterFilter;
import stroom.floormap.client.cluster.FloorMapClusterMember;
import stroom.floormap.client.overlay.FloorMapAreaCellText;
import stroom.floormap.client.overlay.FloorMapAreaMembership;
import stroom.floormap.client.presenter.FloorMapClusterPresenter.FloorMapClusterView;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.TextColumn;
import com.google.gwt.view.client.CellPreviewEvent;
import com.google.gwt.view.client.ListDataProvider;
import com.google.gwt.view.client.SingleSelectionModel;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/// Lists the members of one cluster, so entities merged into a summary glyph stay
/// reachable.
///
/// This is the part that actually answers the original problem. Ten users at one
/// desk were not merely invisible, they were *unreachable*: nothing on the
/// canvas could be clicked to get at the nine underneath. A count on the glyph says
/// how many there are; the hover tooltip names them; this dialog is where one can be
/// picked out and followed.
///
/// Choosing a row tracks that entity — the same path the Tracking panel uses — and
/// closes the dialog, because the thing the user then wants to look at is the map.
///
/// ## Finding one member
///
/// A crowded map is exactly where clusters get big, and a flat list of several
/// hundred is no more use than the crowd it replaced. A search box and — where the
/// members actually differ — Area and Group dropdowns narrow the list, and rows are
/// ordered by name, since the cluster's own order is an artefact of how the
/// clustering lattice was walked. There is deliberately **no type
/// filter**: clustering runs per type, so every member here shares one. The
/// wording and the matching rules live in the shared, unit-tested
/// [FloorMapClusterFilter].
///
/// The **Area** column has the single meaning it has everywhere else:
/// *which area is this member inside?* It carries no "last seen in" fallback,
/// unlike the Tracking panel's version — this dialog describes one drawn frame and
/// holds no history. Every member of a cluster has a position by construction, so
/// that fallback would have nothing to say.
public class FloorMapClusterPresenter
        extends MyPresenterWidget<FloorMapClusterView>
        implements FloorMapClusterUiHandlers {

    /// Column text for a member that is not inside any area.
    private static final String NO_AREA = "—";

    /// Column text for a member that belongs to no group.
    private static final String NO_GROUP = "—";

    /// Room for a dozen or so rows before the grid pages, over the map. Wider than
    /// the columns strictly need so the search box and both dropdowns sit on one
    /// line rather than wrapping.
    private static final PopupSize POPUP_SIZE = PopupSize.resizable(700, 500);

    private final MyDataGrid<FloorMapClusterMember> dataGrid;
    private final ListDataProvider<FloorMapClusterMember> dataProvider = new ListDataProvider<>();
    private final SingleSelectionModel<FloorMapClusterMember> selectionModel =
            new SingleSelectionModel<>();
    private final ButtonView trackButton;

    /// Every member of the cluster being shown, name-sorted — the list the search
    /// and dropdowns filter. Held because filtering must always run against the
    /// whole cluster: narrowing an already-narrowed list would make the controls
    /// one-way, and backspacing in the search box would never bring rows back.
    private final List<FloorMapClusterMember> allMembers = new ArrayList<>();

    /// Called with a member id when the user picks one; set per [#show].
    private Consumer<String> onTrack;

    @Inject
    public FloorMapClusterPresenter(final EventBus eventBus,
                                    final FloorMapClusterView view,
                                    final PagerView pagerView) {
        super(eventBus, view);
        view.setDataView(pagerView);
        view.setUiHandlers(this);

        dataGrid = new MyDataGrid<>(this);
        dataGrid.setSelectionModel(selectionModel);
        // Says which of the two empty states this is: a filter that matched
        // nothing looks exactly like a broken dialog otherwise.
        dataGrid.setEmptyText("No members match the search and filters");
        pagerView.setDataWidget(dataGrid);
        dataProvider.addDataDisplay(dataGrid);

        trackButton = pagerView.addButton(
                SvgPresets.enabled(SvgImage.LOCATE, "Track this entity"));
        trackButton.setEnabled(false);

        initGridColumns();
    }

    @Override
    protected void onBind() {
        super.onBind();

        //noinspection unused e
        registerHandler(selectionModel.addSelectionChangeHandler(e ->
                trackButton.setEnabled(selectionModel.getSelectedObject() != null)));

        registerHandler(trackButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                trackSelected();
            }
        }));

        // Double-click is the shortcut for the button, so a member can be
        // followed without a second aim at the toolbar.
        registerHandler(dataGrid.addCellPreviewHandler(
                (final CellPreviewEvent<FloorMapClusterMember> e) -> {
                    if ("dblclick".equals(e.getNativeEvent().getType())) {
                        selectionModel.setSelected(e.getValue(), true);
                        trackSelected();
                    }
                }));
    }

    /// Shows the member list for a cluster.
    ///
    /// @param cluster      the clicked cluster
    /// @param nameResolver resolves a member id to its display name, so a name here
    ///         matches the name in every grid; may be `null`
    /// @param membership   the area-containment snapshot backing the Area column;
    ///         may be `null`
    /// @param typeOf       resolves a member id to its entity type; may be
    ///         `null`, in which case the cluster's own type is
    ///         used for every row (they are all of one type anyway)
    /// @param groupNamesOf resolves a member id to the names of the groups it
    ///         belongs to; may be `null` on a host with no Groups
    ///         panel, which leaves the Group column empty and the Group
    ///         filter unoffered
    /// @param onTrack      called with the chosen member's id
    public void show(final FloorMapCluster cluster,
                     final Function<String, String> nameResolver,
                     final FloorMapAreaMembership membership,
                     final Function<String, String> typeOf,
                     final Function<String, List<String>> groupNamesOf,
                     final Consumer<String> onTrack) {
        this.onTrack = onTrack;

        final FloorMapAreaMembership areas = membership != null
                ? membership
                : FloorMapAreaMembership.EMPTY;

        final List<FloorMapClusterMember> members = new ArrayList<>(cluster.size());
        for (final String memberId : cluster.getMemberIds()) {
            final String name = nameResolver != null
                    ? nameResolver.apply(memberId)
                    : null;
            final String type = typeOf != null
                    ? typeOf.apply(memberId)
                    : null;
            final List<String> groups = groupNamesOf != null
                    ? groupNamesOf.apply(memberId)
                    : null;
            members.add(new FloorMapClusterMember(
                    memberId,
                    name != null && !name.isEmpty()
                            ? name
                            : memberId,
                    type != null && !type.isEmpty()
                            ? type
                            : cluster.getType(),
                    areaNamesFor(memberId, areas, nameResolver),
                    groups));
        }

        allMembers.clear();
        allMembers.addAll(FloorMapClusterFilter.sortedByName(members));

        // The controls describe THIS cluster, so they are rebuilt on every
        // showing — and reset first, or a filter left over from the last cluster
        // would silently hide most of this one.
        getView().clearFilters();
        getView().setAreaFilterOptions(FloorMapClusterFilter.areaOptions(allMembers));
        getView().setGroupFilterOptions(FloorMapClusterFilter.groupOptions(allMembers));
        applyFilter();

        selectionModel.clear();
        trackButton.setEnabled(false);

        //noinspection unused e
        ShowPopupEvent.builder(this)
                .popupType(PopupType.CLOSE_DIALOG)
                .popupSize(POPUP_SIZE)
                // Says what the dialog is a list OF, not just a count.
                .caption(cluster.getLabel() + " in this cluster")
                // Typing is the most likely next action in a dialog opened to
                // find someone, so the search box takes the caret.
                .onShow(e -> getView().focusSearch())
                .fire();
    }

    /// {@inheritDoc}
    @Override
    public void onFilterChange() {
        applyFilter();
    }

    /// Re-runs the search and the dropdowns over the whole cluster and shows what
    /// survives.
    ///
    /// A selected row that the filter has just hidden is deselected: leaving it
    /// selected would leave the Track button live for a member no longer on
    /// screen, which is one click away from following someone the user cannot
    /// see.
    private void applyFilter() {
        final List<FloorMapClusterMember> visible = FloorMapClusterFilter.filter(
                allMembers,
                getView().getSearchText(),
                getView().getAreaFilter(),
                getView().getGroupFilter());
        dataProvider.setList(visible);

        final FloorMapClusterMember selected = selectionModel.getSelectedObject();
        if (selected != null && !visible.contains(selected)) {
            selectionModel.clear();
        }
    }

    /// Tracks the selected member and closes, since the map is what to look at next.
    private void trackSelected() {
        final FloorMapClusterMember selected = selectionModel.getSelectedObject();
        if (selected != null && onTrack != null) {
            onTrack.accept(selected.getId());
            HidePopupRequestEvent.builder(this).fire();
        }
    }

    /// The names of every area containing a member, innermost (most specific)
    /// first — the same order and the same joining the Tracking panel's Area column
    /// uses, so the two never read differently for one entity.
    private static List<String> areaNamesFor(final String memberId,
                                             final FloorMapAreaMembership membership,
                                             final Function<String, String> nameResolver) {
        final List<String> keys = membership.getAreaKeys(memberId);
        final List<String> names = new ArrayList<>(keys.size());
        for (final String key : keys) {
            final String name = nameResolver != null
                    ? nameResolver.apply(key)
                    : null;
            names.add(name != null && !name.isEmpty()
                    ? name
                    : key);
        }
        return names;
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
        final Column<FloorMapClusterMember, SafeHtml> areaColumn =
                new Column<>(new SafeHtmlCell()) {
                    @Override
                    public SafeHtml getValue(final FloorMapClusterMember member) {
                        return areaCell(member);
                    }
                };
        dataGrid.addResizableColumn(areaColumn, "Area", 200);

        // Shown even on a map with no groups, exactly as the Area column is shown
        // on a map with no areas. A column that came and went would shift the
        // ones beside it between one cluster and the next.
        final Column<FloorMapClusterMember, SafeHtml> groupColumn =
                new Column<>(new SafeHtmlCell()) {
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

    /// Renders one Area cell. The cell is a single `nowrap` line that
    /// ellipsises, so where a member is in several areas the tooltip repeats the
    /// list in full, one per line.
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

    /// Renders one Group cell: every group the member belongs to, with the full
    /// list repeated in the tooltip for when the column is too narrow to show it.
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


    /// The dialog's chrome: a search box, up to two dropdown filters, and the grid
    /// beneath them.
    public interface FloorMapClusterView extends View, HasUiHandlers<FloorMapClusterUiHandlers> {

        /// Sets the widget shown below the filter bar — the pager-wrapped member
        /// grid.
        ///
        /// @param view the data view
        void setDataView(View view);

        /// Populates the Area dropdown, or hides it.
        ///
        /// @param options the options from
        ///         [FloorMapClusterFilter#areaOptions], whose first
        ///         entry is the "any" option. An **empty** list
        ///         hides the control: a dropdown whose every option selects
        ///         the same rows is furniture
        void setAreaFilterOptions(List<String> options);

        /// Populates the Group dropdown, or hides it.
        ///
        /// @param options the options from
        ///         [FloorMapClusterFilter#groupOptions]; empty hides
        ///         the control
        void setGroupFilterOptions(List<String> options);

        /// @return the current search text; never `null`
        String getSearchText();

        /// @return the selected Area option, or `null` when not offered
        String getAreaFilter();

        /// @return the selected Group option, or `null` when not offered
        String getGroupFilter();

        /// Empties the search box and returns both dropdowns to their "any" option
        /// **without** notifying the handlers — the caller is
        /// mid-rebuild and applies the filter itself once the new options are in
        /// place.
        void clearFilters();

        /// Puts the caret in the search box.
        void focusSearch();
    }
}
