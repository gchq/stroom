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

import stroom.data.grid.client.MyDataGrid;
import stroom.floormap.client.FloorMapAria;
import stroom.floormap.client.FloorMapCellHtml;
import stroom.floormap.client.model.FloorMapEntityList.EntityEntry;
import stroom.floormap.client.overlay.FloorMapAreaCellText;
import stroom.floormap.client.overlay.FloorMapAreaMembership;
import stroom.floormap.client.presenter.FloorMapTrackingPresenter.FloorMapTrackingView;
import stroom.floormap.shared.FloorMapGroup;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.InlineSvgToggleButton;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.Separator;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.PopupLocation;
import stroom.widget.util.client.Rect;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.TextColumn;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.CellPreviewEvent;
import com.google.gwt.view.client.ListDataProvider;
import com.google.gwt.view.client.SingleSelectionModel;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/// Presenter for the Tracking panel on the Map tab — a grid listing every
/// entity seen on the floor map: moving entities from the events stream
/// (people, assets, vehicles, or any other typed event object) plus the
/// static facts from the facts query (objects, backgrounds and areas).
///
/// Tracking is about things that move, so the grid lists **event
/// entities only** by default; the toolbar's **Show Facts**
/// toggle folds the fact-only rows in. The toggle is view state — it is not
/// persisted with the document, and the roster itself always holds everything so
/// flipping it back costs no query.
///
/// The **Area** column answers exactly one question: *which
/// area is this object or user inside?* It names every containing area,
/// innermost (most specific) first, or `—` when the row is inside none.
/// **Area rows always show `—`** — area-inside-area is
/// deliberately not computed. Occupancy (how many entities are in an area) lives on
/// the canvas badge instead, so this column never means two different things.
///
/// Detail that will not fit on the single-line cell — the containing areas
/// one-per-line, or where an entity was last seen — goes in the cell's
/// tooltip.
///
/// Selecting a row tracks that entity: the parent presenter highlights it on
/// the canvas, centres the camera on it, and follows it as it moves. Because a
/// deliberate manual pan pauses following, clicking the
/// *already-selected* row re-invokes the selection consumer (a plain
/// `SelectionChangeEvent` would not fire) so the user can resume
/// following without deselecting first. The toolbar's single
/// **Stop Tracking** button clears the selection.
public class FloorMapTrackingPresenter extends MyPresenterWidget<FloorMapTrackingView> {

    /// Column text for a row that is not inside any area.
    private static final String NO_AREA = "—";

    /// This grid's element id, so the canvas can name it as the map's text alternative
    /// via `aria-describedby`. Minted per instance, not a constant.
    ///
    /// A previous version of this comment argued for a fixed id: the reference
    /// crosses presenters, so threading a handle through "purely to learn a string"
    /// looked like coupling for nothing, and the Map tab has only one Tracking panel so the id
    /// "cannot collide with itself". The second half is true and beside the point —
    /// Stroom opens documents in tabs, so the collision is with *another
    /// document's* grid, not with this one's. Two floor maps open at once gave two
    /// elements the same id, and each canvas's `aria-describedby` then resolved
    /// to whichever came first in the DOM. Nothing looks wrong to a sighted user; the
    /// screen reader simply describes the wrong map.
    ///
    /// The coupling objection also does not hold: the consumer already holds this
    /// presenter (FloorMapMapPresenter.floorMapTrackingPresenter), so it reads the id from the instance.
    private final String gridElementId = FloorMapAria.uniqueId("floormap-tracking-grid");

    private final MyDataGrid<EntityEntry> dataGrid;
    private final ListDataProvider<EntityEntry> dataProvider = new ListDataProvider<>();
    private final SingleSelectionModel<EntityEntry> selectionModel = new SingleSelectionModel<>();
    private Consumer<EntityEntry> selectionConsumer;

    private final ButtonView stopTrackingButton;
    private final ButtonView addToGroupButton;
    private final InlineSvgToggleButton showFactsButton;

    /// Supplies the current groups for the "Add to Group" menu; may be `null`.
    private Supplier<List<FloorMapGroup>> groupsSupplier;

    /// Adds an entity (second argument) to a group (first argument).
    private BiConsumer<String, String> addToGroup;

    /// Creates a new group containing the given entity.
    private Consumer<String> createGroupWith;

    /// The whole roster as last pushed by the parent, filtered down to the
    /// visible rows by [#applyFilter()]. Held so the Show Facts toggle can
    /// re-filter without waiting for the next query refresh.
    private final List<EntityEntry> allEntities = new ArrayList<>();

    /// Whether fact-only rows are currently listed alongside event entities.
    private boolean showFacts;

    /// The current area-containment snapshot backing the Area column. Empty until
    /// the parent pushes one, so the column reads as "no area" rather than
    /// blowing up on a map with no areas.
    private FloorMapAreaMembership areaMembership = FloorMapAreaMembership.EMPTY;

    /// Resolves any entity id to the name shown to the user. Supplied by the
    /// parent, which owns the roster; falls back to the raw id.
    private Function<String, String> nameResolver;

    /// Last area an entity was seen in, by entity id. The roster is accumulating
    /// — an entity with no events near the current instant keeps its row but has
    /// no position — so the column shows "no area" while the tooltip can still
    /// say where it was last seen.
    private final Map<String, String> lastKnownAreaKey = new HashMap<>();

    @Inject
    public FloorMapTrackingPresenter(final EventBus eventBus,
                                     final FloorMapTrackingView view) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>(this);
        dataGrid.setSelectionModel(selectionModel);
        // The grid is the map's text equivalent — a navigable row per entity with
        // its type and containing area — so it needs a stable id for the canvas to
        // point aria-describedby at. See getGridElementId().
        dataGrid.getElement().setId(gridElementId);
        view.setGridView(dataGrid);
        initGridColumns();
        dataProvider.addDataDisplay(dataGrid);

        // Toolbar
        final ButtonPanel buttonPanel = new ButtonPanel();
        stopTrackingButton = buttonPanel.addButton(SvgPresets.CLEAR);
        stopTrackingButton.setTitle("Stop Tracking");

        // Puts the selected row into a group without leaving this panel. Group
        // membership is otherwise edited from the Groups tab.
        addToGroupButton = buttonPanel.addButton(SvgPresets.ADD);
        addToGroupButton.setTitle("Add to Group");
        addToGroupButton.setEnabled(false);

        showFactsButton = new InlineSvgToggleButton();
        showFactsButton.setSvg(SvgImage.LOCATE);
        showFactsButton.setState(showFacts);
        showFactsButton.setTitle(showFactsTitle());
        buttonPanel.addButton(showFactsButton);

        view.setToolbar(buttonPanel);
    }

    @Override
    protected void onBind() {
        super.onBind();
        //noinspection unused e
        registerHandler(selectionModel.addSelectionChangeHandler(e -> {
            final EntityEntry selected = selectionModel.getSelectedObject();
            stopTrackingButton.setEnabled(selected != null);
            addToGroupButton.setEnabled(selected != null && groupsSupplier != null);
            if (selectionConsumer != null) {
                selectionConsumer.accept(selected);
            }
        }));

        // Clicking the row that is already selected does not fire a
        // SelectionChangeEvent, but it must still re-invoke the consumer —
        // that is the "re-centre / resume following after a manual pan" gesture.
        registerHandler(dataGrid.addCellPreviewHandler((final CellPreviewEvent<EntityEntry> e) -> {
            if ("click".equals(e.getNativeEvent().getType())) {
                final EntityEntry clicked = e.getValue();
                if (clicked != null
                        && clicked.equals(selectionModel.getSelectedObject())
                        && selectionConsumer != null) {
                    selectionConsumer.accept(clicked);
                }
            }
        }));

        //noinspection unused e
        registerHandler(stopTrackingButton.addClickHandler(e ->
                selectionModel.clear()));

        //noinspection unused e
        registerHandler(showFactsButton.addClickHandler(e ->
                setShowFacts(showFactsButton.getState())));

        //noinspection unused e
        registerHandler(addToGroupButton.addClickHandler(e -> showAddToGroupMenu()));
    }

    /// Wires up the "Add to Group" action. Without this the button stays disabled,
    /// so a host that has no Groups panel (the Editor tab) simply does not offer it.
    ///
    /// @param groupsSupplier   supplies the current groups, in display order
    /// @param addToGroup       adds an entity (second argument) to a group by id
    ///         (first argument)
    /// @param createGroupWith  creates a new group containing the given entity
    public void setAddToGroupSupport(final Supplier<List<FloorMapGroup>> groupsSupplier,
                                     final BiConsumer<String, String> addToGroup,
                                     final Consumer<String> createGroupWith) {
        this.groupsSupplier = groupsSupplier;
        this.addToGroup = addToGroup;
        this.createGroupWith = createGroupWith;
        addToGroupButton.setEnabled(
                selectionModel.getSelectedObject() != null && groupsSupplier != null);
    }

    /// Shows the group menu for the selected row: every existing group, then "New
    /// group with this entity".
    ///
    /// A group the entity is *already* in is listed with a tick and
    /// disabled, rather than hidden — hiding it would leave the user wondering
    /// whether the group still exists.
    private void showAddToGroupMenu() {
        final EntityEntry selected = selectionModel.getSelectedObject();
        if (selected == null || groupsSupplier == null) {
            return;
        }
        final String entityId = selected.getId();
        final List<FloorMapGroup> groups = groupsSupplier.get();

        final List<Item> items = new ArrayList<>();
        int priority = 0;
        if (groups != null) {
            for (final FloorMapGroup group : groups) {
                final boolean alreadyIn = group.contains(entityId);
                final IconMenuItem.Builder builder = new IconMenuItem.Builder()
                        .priority(priority++)
                        // The member count is spelled out so the user can tell a
                        // populated group from an empty one before adding to it.
                        .text(group.getName() + " (" + group.countMembers()
                              + (group.countMembers() == 1 ? " member)" : " members)"))
                        .enabled(!alreadyIn);
                if (alreadyIn) {
                    builder.icon(SvgImage.TICK)
                            .tooltip(selected.getDisplayName() + " is already in this group");
                } else {
                    builder.command(() -> {
                        if (addToGroup != null) {
                            addToGroup.accept(group.getId(), entityId);
                        }
                    });
                }
                items.add(builder.build());
            }
            if (!groups.isEmpty()) {
                items.add(new Separator(priority++));
            }
        }

        items.add(new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.ADD)
                .text("New group with " + selected.getDisplayName())
                .command(() -> {
                    if (createGroupWith != null) {
                        createGroupWith.accept(entityId);
                    }
                })
                .build());

        final Rect relativeRect = new Rect(
                addToGroupButton.asWidget().getElement()).grow(3);
        ShowMenuEvent.builder()
                .items(items)
                .popupPosition(new PopupPosition(relativeRect, PopupLocation.BELOW))
                .fire(this);
    }

    /// The entity grid's element id, for the canvas to point
    /// `aria-describedby` at.
    ///
    /// @return the grid's element id
    public String getGridElementId() {
        return gridElementId;
    }

    /// The Show Facts button's tooltip, worded for what a click will do next
    /// rather than for the state it is in.
    private String showFactsTitle() {
        return showFacts
                ? "Hide Facts (objects, backgrounds and areas)"
                : "Show Facts (objects, backgrounds and areas)";
    }

    private void initGridColumns() {
        // Name Column
        final Column<EntityEntry, String> nameColumn = new TextColumn<>() {
            @Override
            public String getValue(final EntityEntry entry) {
                return entry.getDisplayName();
            }
        };
        dataGrid.addColumn(nameColumn, "Name");

        // Type Column (e.g. person, vehicle, object)
        final Column<EntityEntry, String> typeColumn = new TextColumn<>() {
            @Override
            public String getValue(final EntityEntry entry) {
                return entry.getType();
            }
        };
        dataGrid.addColumn(typeColumn, "Type");

        // Area Column — which area is this object/user inside? Area rows always
        // show a dash. Wide enough for a couple of names before the cell
        // ellipsises; resizable because area names are user-chosen and can be
        // long.
        final Column<EntityEntry, SafeHtml> areaColumn =
                new Column<>(new SafeHtmlCell()) {
                    @Override
                    public SafeHtml getValue(final EntityEntry entry) {
                        return areaCell(entry);
                    }
                };
        dataGrid.addResizableColumn(areaColumn, "Area", 200);

        // Id Column (the full entity id, e.g. an email address)
        final Column<EntityEntry, String> idColumn = new TextColumn<>() {
            @Override
            public String getValue(final EntityEntry entry) {
                return entry.getId();
            }
        };
        dataGrid.addColumn(idColumn, "Id");
    }

    /// Renders one Area cell, with a `title` tooltip carrying the detail
    /// that will not fit on the cell's single line: the containing areas
    /// one-per-line for a nested entity, a fixed note for an area (areas are not located inside
    /// other areas - occupancy lives on the canvas badge, not here), or where an entity without a
    /// current position was last seen.
    private SafeHtml areaCell(final EntityEntry entry) {
        final String id = entry.getId();

        // An area is never located inside anything — only objects and users are.
        // Stated explicitly rather than falling through to the empty-list branch
        // below, so the column's behaviour for areas does not depend on how
        // membership happens to be computed.
        if (areaMembership.isArea(id)) {
            return cell(NO_AREA, "Areas are not located inside other areas");
        }

        final List<String> containingKeys = areaMembership.getAreaKeys(id);
        if (containingKeys.isEmpty()) {
            // Either genuinely outside every area, or with no position at this
            // instant — worth distinguishing in the tooltip rather than implying
            // "outside".
            final String lastKnown = nameFor(lastKnownAreaKey.get(id));
            return cell(NO_AREA, lastKnown != null
                    ? "Not in a known area at this time (last seen in " + lastKnown + ")"
                    : "Not in a known area at this time");
        }

        // Every containing area is named, innermost (most specific) first. The
        // cell is a single nowrap line that ellipsises when the column is too
        // narrow, so the tooltip repeats the list in full, one per line.
        final String joined = FloorMapAreaCellText.joinNames(namesFor(containingKeys));
        if (containingKeys.size() == 1) {
            return cell(joined, "Inside " + joined);
        }
        final StringBuilder tooltip = new StringBuilder("Inside ")
                .append(containingKeys.size())
                .append(" areas (innermost first):");
        for (final String key : containingKeys) {
            tooltip.append("\n• ").append(nameFor(key));
        }
        return cell(joined, tooltip.toString());
    }

    /// Resolves each id to its display name, preserving order.
    private List<String> namesFor(final List<String> ids) {
        final List<String> names = new ArrayList<>(ids.size());
        for (final String id : ids) {
            names.add(nameFor(id));
        }
        return names;
    }

    /// Wraps cell text in a span carrying a `title` tooltip, via the shared
    /// helper the Groups panel's cells also use.
    private static SafeHtml cell(final String text, final String tooltip) {
        return FloorMapCellHtml.cell(text, tooltip);
    }

    /// The display name for any entity id — an area, a person, an object — or
    /// `null` when there is no id. Falls back to the raw id if the parent
    /// supplied no resolver or does not know the id.
    private String nameFor(final String id) {
        if (id == null) {
            return null;
        }
        if (nameResolver != null) {
            final String name = nameResolver.apply(id);
            if (name != null && !name.isEmpty()) {
                return name;
            }
        }
        return id;
    }

    /// Updates the Area column's backing snapshot.
    ///
    /// Called on every facts/events query refresh (~300ms during playback), so
    /// the grid is only redrawn when the containment actually changed — otherwise
    /// playback would re-render the grid continuously.
    ///
    /// @param areaMembership the new containment snapshot; may be `null`
    /// @param nameResolver   resolves any entity id (area, person, object) to its
    ///         display name; may be `null` to show raw ids
    public void setAreaMembership(final FloorMapAreaMembership areaMembership,
                                  final Function<String, String> nameResolver) {
        final FloorMapAreaMembership next = areaMembership != null
                ? areaMembership
                : FloorMapAreaMembership.EMPTY;
        this.nameResolver = nameResolver;

        // Remember where each entity was last seen before overwriting, so a row
        // that loses its position can still say where it was.
        for (final String entityId : next.getEntityIds()) {
            lastKnownAreaKey.put(entityId, next.getInnermostAreaKey(entityId));
        }

        final boolean changed = !Objects.equals(this.areaMembership, next);
        this.areaMembership = next;
        if (changed) {
            dataGrid.redraw();
        }
    }

    /// Replaces the entire grid data with the supplied list.
    ///
    /// Only the rows passing the current Show Facts filter reach the grid; the
    /// full list is kept so the toggle can re-filter without a query refresh.
    ///
    /// The current selection is *not* automatically adjusted —
    /// callers should follow up with [#setSelected(String)]. Because
    /// [EntityEntry] equality is id-based, restoring the same id does not
    /// re-fire the selection consumer.
    ///
    /// @param data the entities to display; must not be `null`
    public void setData(final List<EntityEntry> data) {
        allEntities.clear();
        allEntities.addAll(data);
        applyFilter();
    }

    /// Pushes the rows passing the current Show Facts filter into the grid.
    ///
    /// Hiding the facts while a fact row is being tracked would leave the
    /// canvas following something the panel no longer lists, so that selection is
    /// cleared — which stops tracking through the usual selection handler.
    private void applyFilter() {
        final List<EntityEntry> visible;
        if (showFacts) {
            visible = new ArrayList<>(allEntities);
        } else {
            visible = new ArrayList<>(allEntities.size());
            for (final EntityEntry entry : allEntities) {
                if (entry.isFromEvents()) {
                    visible.add(entry);
                }
            }
        }

        dataProvider.setList(visible);
        dataGrid.setRowData(0, visible);

        final EntityEntry selected = selectionModel.getSelectedObject();
        if (selected != null && !visible.contains(selected)) {
            selectionModel.clear();
        }
    }

    /// Discards the containment snapshot and the last-seen-in history — called
    /// when the roster itself is reset (a (re-)opened document), so a new map
    /// cannot inherit the previous one's areas.
    public void clearAreaState() {
        areaMembership = FloorMapAreaMembership.EMPTY;
        lastKnownAreaKey.clear();
    }

    /// Selects the grid row whose entity id matches the given value.
    ///
    /// A fact hidden by the Show Facts toggle is *revealed* rather than
    /// skipped: the caller is usually the canvas, where clicking a static object
    /// starts tracking it, and a tracked entity the panel refuses to list would
    /// leave the two views disagreeing. The toggle stays on afterwards.
    ///
    /// If `id` is `null` or matches nothing in the roster, the
    /// current selection is cleared.
    ///
    /// @param id the entity id to look for; may be `null`
    public void setSelected(final String id) {
        if (id != null) {
            for (final EntityEntry entry : allEntities) {
                if (id.equals(entry.getId())) {
                    if (!showFacts && !entry.isFromEvents()) {
                        setShowFacts(true);
                    }
                    selectionModel.setSelected(entry, true);
                    return;
                }
            }
        }
        selectionModel.clear();
    }

    /// Flips the Show Facts state, its button and the visible rows together.
    private void setShowFacts(final boolean showFacts) {
        this.showFacts = showFacts;
        showFactsButton.setState(showFacts);
        showFactsButton.setTitle(showFactsTitle());
        applyFilter();
    }

    /// Returns the id of the currently selected entity, or `null` if
    /// nothing is selected.
    public String getSelectedId() {
        final EntityEntry selected = selectionModel.getSelectedObject();
        return selected != null
                ? selected.getId()
                : null;
    }

    /// Registers a callback invoked whenever the tracked entity changes.
    ///
    /// The consumer receives the newly selected [EntityEntry], or
    /// `null` when tracking stops. It is also re-invoked with the current
    /// entry when the already-selected row is clicked again (re-centre /
    /// resume-follow gesture).
    ///
    /// @param selectionConsumer called on every selection change or re-click
    public void setSelectionConsumer(final Consumer<EntityEntry> selectionConsumer) {
        this.selectionConsumer = selectionConsumer;
    }

    /// View contract for the Tracking panel.
    ///
    /// Implementations provide the layout that hosts the data grid and the
    /// toolbar strip above it.
    public interface FloorMapTrackingView extends View {

        /// Sets the data-grid widget into the main content area of the panel.
        ///
        /// @param gridWidget the data grid widget; must not be `null`
        void setGridView(Widget gridWidget);

        /// Sets the toolbar widget (containing the Stop Tracking button) into
        /// the toolbar area above the grid.
        ///
        /// @param toolbarWidget the toolbar widget; must not be `null`
        void setToolbar(Widget toolbarWidget);
    }
}
