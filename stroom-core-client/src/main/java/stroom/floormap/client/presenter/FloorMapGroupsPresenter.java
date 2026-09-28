/*
 * Copyright 2016-2026 Crown Copyright
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

import stroom.cell.info.client.SvgCell;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.grid.client.MyDataGrid;
import stroom.floormap.client.FloorMapCellHtml;
import stroom.floormap.client.FloorMapSwatchHtml;
import stroom.floormap.client.model.FloorMapEntityList.EntityEntry;
import stroom.floormap.client.overlay.FloorMapAreaCellText;
import stroom.floormap.client.overlay.FloorMapGroupSnapshot;
import stroom.floormap.client.presenter.FloorMapGroupsPresenter.FloorMapGroupsView;
import stroom.floormap.shared.FloorMapGroup;
import stroom.floormap.shared.TypeStyle;
import stroom.floormap.shared.TypeStyle.Shape;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.ButtonView;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.TextColumn;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.ListDataProvider;
import com.google.gwt.view.client.SingleSelectionModel;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Presenter for the <strong>Groups</strong> panel — the third tab of the Map
 * tab's right-hand dock, listing the document's {@link FloorMapGroup}s.
 *
 * <p>A group is a named collection of map entities the user assembles by hand
 * ("Maintenance", "Security"). Membership is generic: any id from the map's one id
 * namespace, so people from the events stream, static object facts and even areas
 * can sit in the same group.</p>
 *
 * <h3>Tracking a group is not tracking an entity</h3>
 * <p>This panel <strong>never moves the camera</strong>. Selecting a group selects
 * it for editing; switching its eye on rings its members on the canvas in the
 * group's colour. It does not call {@code setTrackedObjectId}, so an entity the
 * Tracking panel is following keeps being followed while groups are used. That
 * separation is the whole point of the feature and should not be "improved" into a
 * camera jump.</p>
 *
 * <h3>Highlight is transient and starts off</h3>
 * <p>Which groups are highlighted is view state — never persisted, exactly like
 * layer visibility — and <em>every</em> group starts hidden, including one just
 * created. A consequence worth knowing: creating a group and adding members
 * produces no canvas change at all, so the confirmation that membership landed is
 * the Members / Positioned columns updating.</p>
 *
 * <p>"Starts hidden" is a property of a newly created presenter, not something
 * re-imposed on every document read — see {@link #setGroups(List)} for why that
 * distinction matters when the user presses save.</p>
 *
 * <h3>Positioned means "has a position at this instant"</h3>
 * <p>The Positioned column counts members with a position <em>right now</em>. A
 * member whose events have gone quiet has no position and is not counted, so the
 * number can fall without anyone moving. It is not a head-count of who is on
 * site — see {@link FloorMapGroupSnapshot}.</p>
 */
public class FloorMapGroupsPresenter extends MyPresenterWidget<FloorMapGroupsView> {

    /** Column text for a group with nothing to show. */
    private static final String NONE = "—";

    /** Size of the colour swatch on each row, in pixels. */
    private static final int SWATCH_SIZE_PX = 12;

    private final Provider<FloorMapGroupEditPresenter> groupEditPresenterProvider;

    private final MyDataGrid<FloorMapGroup> dataGrid;
    private final ListDataProvider<FloorMapGroup> dataProvider = new ListDataProvider<>();
    private final SingleSelectionModel<FloorMapGroup> selectionModel = new SingleSelectionModel<>();

    private final ButtonView newButton;
    private final ButtonView editButton;
    private final ButtonView deleteButton;

    /** The document's groups as last read or edited, in display order. */
    private List<FloorMapGroup> groups = new ArrayList<>();

    /**
     * Ids of the groups currently highlighted on the canvas. Transient view state:
     * starts empty on every document read and is never written to the document.
     * Keyed on group <em>id</em> so a rename cannot drop a highlight.
     */
    private final Set<String> shownGroupIds = new LinkedHashSet<>();

    /** The live per-group counts, refreshed on every facts/events query refresh. */
    private FloorMapGroupSnapshot snapshot = FloorMapGroupSnapshot.EMPTY;

    /** The roster the member picker offers, pushed by the parent presenter. */
    private List<EntityEntry> roster = Collections.emptyList();

    /** Resolves any entity id — an area, a person, an object — to its display name. */
    private Function<String, String> nameResolver;

    /** Notified with the new group list whenever the user edits one. */
    private Consumer<List<FloorMapGroup>> groupsEditHandler;

    /** Run whenever the set of highlighted groups changes. */
    private Runnable highlightChangeHandler;

    /** Source of generated group ids. */
    private final Random random = new Random();

    @Inject
    public FloorMapGroupsPresenter(final EventBus eventBus,
                                   final FloorMapGroupsView view,
                                   final Provider<FloorMapGroupEditPresenter> groupEditPresenterProvider) {
        super(eventBus, view);
        this.groupEditPresenterProvider = groupEditPresenterProvider;

        dataGrid = new MyDataGrid<>(this);
        dataGrid.setSelectionModel(selectionModel);
        view.setGridView(dataGrid);
        initGridColumns();
        dataProvider.addDataDisplay(dataGrid);

        final ButtonPanel buttonPanel = new ButtonPanel();
        newButton = buttonPanel.addButton(SvgPresets.ADD);
        newButton.setTitle("New Group");
        editButton = buttonPanel.addButton(SvgPresets.EDIT);
        editButton.setTitle("Edit Group (name, colour and members)");
        editButton.setEnabled(false);
        deleteButton = buttonPanel.addButton(SvgPresets.DELETE);
        deleteButton.setTitle("Delete Group");
        deleteButton.setEnabled(false);
        view.setToolbar(buttonPanel);
    }

    @Override
    protected void onBind() {
        super.onBind();

        //noinspection unused e
        registerHandler(selectionModel.addSelectionChangeHandler(e -> {
            final boolean hasSelection = selectionModel.getSelectedObject() != null;
            editButton.setEnabled(hasSelection);
            deleteButton.setEnabled(hasSelection);
        }));

        //noinspection unused e
        registerHandler(newButton.addClickHandler(e -> onNewGroup()));
        //noinspection unused e
        registerHandler(editButton.addClickHandler(e -> onEditGroup()));
        //noinspection unused e
        registerHandler(deleteButton.addClickHandler(e -> onDeleteGroup()));
    }

    private void initGridColumns() {
        // Highlight toggle. A button cell, so clicking the eye flips the group's
        // highlight without also having to select the row.
        final Column<FloorMapGroup, Preset> highlightColumn =
                new Column<>(new SvgCell()) {
                    @Override
                    public Preset getValue(final FloorMapGroup group) {
                        return highlightPreset(isShown(group));
                    }
                };
        //noinspection unused index, val
        highlightColumn.setFieldUpdater((index, group, val) -> toggleHighlight(group));
        dataGrid.addColumn(highlightColumn, "", ColumnSizeConstants.ICON_COL);

        // Name, led by a swatch in the group's highlight colour.
        final Column<FloorMapGroup, SafeHtml> nameColumn =
                new Column<>(new SafeHtmlCell()) {
                    @Override
                    public SafeHtml getValue(final FloorMapGroup group) {
                        return nameCell(group);
                    }
                };
        dataGrid.addResizableColumn(nameColumn, "Name", 140);

        // Total membership, however much of it is on the map right now.
        final Column<FloorMapGroup, String> membersColumn = new TextColumn<>() {
            @Override
            public String getValue(final FloorMapGroup group) {
                return String.valueOf(group.countMembers());
            }
        };
        dataGrid.addColumn(membersColumn, "Members", ColumnSizeConstants.SMALL_COL);

        // Members with a position at this instant.
        final Column<FloorMapGroup, SafeHtml> positionedColumn =
                new Column<>(new SafeHtmlCell()) {
                    @Override
                    public SafeHtml getValue(final FloorMapGroup group) {
                        return positionedCell(group);
                    }
                };
        dataGrid.addColumn(positionedColumn, "Positioned", ColumnSizeConstants.SMALL_COL);

        // Which areas those members are in. Resizable because area names are
        // user-chosen and can be long.
        final Column<FloorMapGroup, SafeHtml> areasColumn =
                new Column<>(new SafeHtmlCell()) {
                    @Override
                    public SafeHtml getValue(final FloorMapGroup group) {
                        return areasCell(group);
                    }
                };
        dataGrid.addResizableColumn(areasColumn, "Areas", 200);
    }

    /** The eye icon for a row, worded for what a click will do next. */
    private static Preset highlightPreset(final boolean shown) {
        return new Preset(
                shown
                        ? SvgImage.EYE
                        : SvgImage.EYE_OFF,
                shown
                        ? "Highlighted on the map — click to stop highlighting"
                        : "Not highlighted — click to highlight this group's members",
                true);
    }

    /**
     * The Name cell: a swatch in the group's colour, its name, and a tooltip
     * naming every member so the membership is readable without opening the
     * dialog.
     */
    private SafeHtml nameCell(final FloorMapGroup group) {
        final SafeHtml swatch = FloorMapSwatchHtml.swatch(
                new TypeStyle(null, Shape.CIRCLE, group.findColourOrDefault()), SWATCH_SIZE_PX);
        return FloorMapCellHtml.cellWithSwatch(swatch, group.getName(), membersTooltip(group));
    }

    /**
     * Every member named, one per line — never a "+N" summary, which would hide
     * exactly the names the user is looking for.
     */
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
                // Distinguishes "not on the map right now" from "not a member",
                // which the bare count cannot.
                tooltip.append(" (no position at this time)");
            }
        }
        return tooltip.toString();
    }

    /**
     * The Positioned cell — how many members are on the map at this instant, out
     * of how many there are.
     */
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

    /**
     * The Areas cell — every area the group's positioned members are standing in,
     * with how many are in each, most-populated first.
     */
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

    /**
     * The display name for any entity id, falling back to the raw id when the
     * parent supplied no resolver or does not know it.
     */
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

    // ------------------------------------------------------------------------
    // Editing
    // ------------------------------------------------------------------------

    private void onNewGroup() {
        editNewGroup(FloorMapGroup.create(groups, random));
    }

    /**
     * Opens the edit dialog on a not-yet-added group, adding and selecting it only
     * if the user confirms — so cancelling leaves no empty group behind.
     */
    private void editNewGroup(final FloorMapGroup created) {
        groupEditPresenterProvider.get().show(created, roster, nameResolver, true, saved -> {
            applyEdit(FloorMapGroup.replace(groups, saved));
            selectionModel.setSelected(saved, true);
        });
    }

    private void onEditGroup() {
        final FloorMapGroup selected = selectionModel.getSelectedObject();
        if (selected == null) {
            return;
        }
        groupEditPresenterProvider.get().show(selected, roster, nameResolver, false, saved ->
                applyEdit(FloorMapGroup.replace(groups, saved)));
    }

    private void onDeleteGroup() {
        final FloorMapGroup selected = selectionModel.getSelectedObject();
        if (selected == null) {
            return;
        }
        // Highlight state is keyed on id, so drop it with the group or a later
        // group reusing the id would inherit it.
        shownGroupIds.remove(selected.getId());
        selectionModel.clear();
        applyEdit(FloorMapGroup.without(groups, selected.getId()));
        notifyHighlightChanged();
    }

    /**
     * Adds an entity to a group, from elsewhere in the UI (the Tracking panel's
     * "Add to Group" action). A no-op when the entity is already a member.
     *
     * @param groupId  the target group's id
     * @param entityId the entity to add
     */
    public void addMember(final String groupId, final String entityId) {
        final FloorMapGroup group = FloorMapGroup.find(groups, groupId);
        if (group == null || entityId == null || group.contains(entityId)) {
            return;
        }
        applyEdit(FloorMapGroup.replace(groups, group.withMember(entityId)));
    }

    /**
     * Creates a group containing just the given entity — the Tracking panel's
     * "New group with this entity" path — and opens the edit dialog on it.
     *
     * <p>The dialog is opened rather than the group being created silently: the
     * caller is on a different dock tab, and with highlight off by default a silent
     * create would produce no visible feedback whatsoever. It also gives the group
     * a name at the point the user cares about it.</p>
     *
     * @param entityId the first member; may be {@code null} for an empty group
     */
    public void createGroupWith(final String entityId) {
        editNewGroup(FloorMapGroup.create(groups, random).withMember(entityId));
    }

    /** The groups as currently edited, for callers offering an "add to…" menu. */
    public List<FloorMapGroup> getGroups() {
        return Collections.unmodifiableList(groups);
    }

    /**
     * Applies an edited group list: refreshes the grid and tells the host to stage
     * it against the document (which lights the save button).
     */
    private void applyEdit(final List<FloorMapGroup> edited) {
        groups = new ArrayList<>(edited);
        // Highlight state can only name groups that still exist.
        shownGroupIds.retainAll(groupIds());
        refreshGrid();
        if (groupsEditHandler != null) {
            groupsEditHandler.accept(new ArrayList<>(groups));
        }
        // A membership change alters what is highlighted, if the group is shown.
        notifyHighlightChanged();
    }

    private Set<String> groupIds() {
        final Set<String> ids = new LinkedHashSet<>();
        for (final FloorMapGroup group : groups) {
            ids.add(group.getId());
        }
        return ids;
    }

    private boolean isShown(final FloorMapGroup group) {
        return shownGroupIds.contains(group.getId());
    }

    private void toggleHighlight(final FloorMapGroup group) {
        if (!shownGroupIds.remove(group.getId())) {
            shownGroupIds.add(group.getId());
        }
        dataGrid.redraw();
        notifyHighlightChanged();
    }

    private void notifyHighlightChanged() {
        if (highlightChangeHandler != null) {
            highlightChangeHandler.run();
        }
    }

    // ------------------------------------------------------------------------
    // Inputs from the host presenter
    // ------------------------------------------------------------------------

    /**
     * Sets the groups read from the document.
     *
     * <p><strong>Deliberately preserves the highlight state and the selection.</strong>
     * Saving a document re-reads every visited tab, so clearing here would silently
     * switch off the user's highlights every time they pressed save. Highlight
     * starts off because the field starts empty on a freshly created presenter —
     * one per open document — not because this method resets it. Ids naming groups
     * that no longer exist are dropped.</p>
     *
     * <p>The snapshot is left alone for the same reason: the queries that follow a
     * read will refresh it, and zeroing it first would flash "0 of N" across every
     * row on every save.</p>
     *
     * @param groups the document's groups; {@code null} treated as empty
     */
    public void setGroups(final List<FloorMapGroup> groups) {
        this.groups = groups != null
                ? new ArrayList<>(groups)
                : new ArrayList<>();
        shownGroupIds.retainAll(groupIds());
        refreshGrid();
        notifyHighlightChanged();
    }

    /**
     * Updates the live per-group counts.
     *
     * <p>Called on every facts/events query refresh (~300ms during playback), so
     * the grid is only redrawn when the counts actually changed — otherwise
     * playback would re-render it continuously.</p>
     *
     * @param snapshot the new snapshot; may be {@code null}
     */
    public void setSnapshot(final FloorMapGroupSnapshot snapshot) {
        final FloorMapGroupSnapshot next = snapshot != null
                ? snapshot
                : FloorMapGroupSnapshot.EMPTY;
        final boolean changed = !Objects.equals(this.snapshot, next);
        this.snapshot = next;
        if (changed) {
            dataGrid.redraw();
        }
    }

    /**
     * Sets the entity roster the member picker offers, and the resolver used to
     * name members and areas in the grid.
     *
     * @param roster       every entity seen on the map; {@code null} treated as empty
     * @param nameResolver resolves an entity id to its display name; may be {@code null}
     */
    public void setRoster(final List<EntityEntry> roster,
                          final Function<String, String> nameResolver) {
        this.roster = roster != null
                ? roster
                : Collections.emptyList();
        this.nameResolver = nameResolver;
        // Names shown in the grid come from the resolver, so a roster refresh can
        // change them (an area learning its label, for instance).
        dataGrid.redraw();
    }

    /**
     * @param groupsEditHandler receives the new group list whenever the user
     *                          edits groups, so the host can stage it for save
     */
    public void setGroupsEditHandler(final Consumer<List<FloorMapGroup>> groupsEditHandler) {
        this.groupsEditHandler = groupsEditHandler;
    }

    /**
     * @param highlightChangeHandler run whenever the highlighted set changes, so
     *                               the host can push a new overlay to the canvas
     */
    public void setHighlightChangeHandler(final Runnable highlightChangeHandler) {
        this.highlightChangeHandler = highlightChangeHandler;
    }

    /**
     * The ids of the groups currently highlighted — read by the host when building
     * the canvas overlay.
     */
    public Set<String> getShownGroupIds() {
        return Collections.unmodifiableSet(shownGroupIds);
    }

    /** Re-pushes the group list into the grid, preserving the selected group. */
    private void refreshGrid() {
        final FloorMapGroup selected = selectionModel.getSelectedObject();
        final List<FloorMapGroup> visible = new ArrayList<>(groups);
        dataProvider.setList(visible);
        dataGrid.setRowData(0, visible);

        // Selection is by value, and a group's value changes when it is edited, so
        // re-select by id rather than relying on the old instance still matching.
        if (selected != null) {
            final FloorMapGroup current = FloorMapGroup.find(visible, selected.getId());
            if (current != null) {
                selectionModel.setSelected(current, true);
            } else {
                selectionModel.clear();
            }
        }
    }

    /**
     * View contract for the Groups panel: a toolbar strip above a data grid —
     * the same shape as the Tracking panel.
     */
    public interface FloorMapGroupsView extends View {

        /**
         * Sets the data-grid widget into the main content area of the panel.
         *
         * @param gridWidget the data grid widget; must not be {@code null}
         */
        void setGridView(Widget gridWidget);

        /**
         * Sets the toolbar widget into the area above the grid.
         *
         * @param toolbarWidget the toolbar widget; must not be {@code null}
         */
        void setToolbar(Widget toolbarWidget);
    }
}
