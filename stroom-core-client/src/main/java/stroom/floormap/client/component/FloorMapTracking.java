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
import stroom.floormap.client.model.FloorMapEntityList.EntityEntry;
import stroom.floormap.client.overlay.FloorMapAreaCellText;
import stroom.floormap.client.overlay.FloorMapAreaMembership;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.button.client.ToolbarToggleButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.SingleSelectionEventManager;
import stroom.widget.util.client.FloorMapAria;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.TextColumn;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.CellPreviewEvent;
import com.google.gwt.view.client.ListDataProvider;
import com.google.gwt.view.client.SingleSelectionModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/// The floor map's Tracking panel: a row per entity (its name, type, the areas it is in and its id),
/// one of which can be selected to track it on the map, and the actions on it. The entities seen
/// in events are listed; Show Facts lists the facts (objects, backgrounds and areas) too. The grid
/// is the map canvas's text alternative, so it has an id of its own ([#getGridElementId()]).
public class FloorMapTracking extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    // Column text for a row not inside any area
    private static final String NO_AREA = "—";

    @UiField
    ToolbarButton stopTrackingButton;
    @UiField
    ToolbarButton addToGroupButton;
    @UiField
    ToolbarToggleButton showFactsButton;
    @UiField
    DataGrid<EntityEntry> dataGrid;

    private final ListDataProvider<EntityEntry> dataProvider = new ListDataProvider<>();
    private final SingleSelectionModel<EntityEntry> selectionModel = new SingleSelectionModel<>();
    // Minted for each panel: two floor maps open at once mustn't share an id, or each canvas's
    // aria-describedby would name whichever came first in the page
    private final String gridElementId = FloorMapAria.uniqueId("floormap-tracking-grid");
    // Every entity as last given, filtered to the rows shown by applyFilter(), so Show Facts can
    // filter again at once
    private final List<EntityEntry> allEntities = new ArrayList<>();
    private boolean showFacts;
    private boolean addToGroupAvailable;
    // Which areas each entity is in now; empty until given, so the column reads "no area"
    private FloorMapAreaMembership areaMembership = FloorMapAreaMembership.EMPTY;
    private Function<String, String> nameResolver;
    // The area each entity was last seen in: an entity with no events near now keeps its row but
    // has no position, so the column shows "no area" and the tooltip where it was last seen
    private final Map<String, String> lastKnownAreaKey = new HashMap<>();

    private Consumer<EntityEntry> onSelectionChange = entry -> {
    };
    private Consumer<EntityEntry> onAddToGroup = entry -> {
    };

    /// Makes an empty panel.
    public FloorMapTracking() {
        initWidget(BINDER.createAndBindUi(this));
        dataGrid.setSelectionModel(selectionModel, new SingleSelectionEventManager<>(dataGrid, selectionModel));
        dataGrid.getElement().setId(gridElementId);
        addColumns();
        dataProvider.addDataDisplay(dataGrid);
        stopTrackingButton.setOnClick(selectionModel::clear);
        addToGroupButton.setOnClick(() -> {
            final EntityEntry selected = selectionModel.getSelectedObject();
            if (selected != null && addToGroupAvailable) {
                onAddToGroup.accept(selected);
            }
        });
        showFactsButton.setOnToggle(this::setShowFacts);
        selectionModel.addSelectionChangeHandler(e -> {
            final EntityEntry selected = selectionModel.getSelectedObject();
            stopTrackingButton.setEnabled(selected != null);
            addToGroupButton.setEnabled(selected != null && addToGroupAvailable);
            onSelectionChange.accept(selected);
        });
        // Clicking the row already selected changes no selection, but is told all the same: it is
        // the gesture to centre on it again, or to follow it again after a pan
        dataGrid.addCellPreviewHandler((final CellPreviewEvent<EntityEntry> e) -> {
            if ("click".equals(e.getNativeEvent().getType())) {
                final EntityEntry clicked = e.getValue();
                if (clicked != null && clicked.equals(selectionModel.getSelectedObject())) {
                    onSelectionChange.accept(clicked);
                }
            }
        });
    }

    /// @return The grid's element id, for the canvas to point `aria-describedby` at.
    public String getGridElementId() {
        return gridElementId;
    }

    /// @return The Add to Group button, for the menu it opens to be shown below.
    public Element getAddToGroupElement() {
        return addToGroupButton.getElement();
    }

    /// @param available Whether Add to Group is offered (not where there is no Groups panel).
    public void setAddToGroupAvailable(final boolean available) {
        this.addToGroupAvailable = available;
        addToGroupButton.setEnabled(selectionModel.getSelectedObject() != null && available);
    }

    /// Shows the entities in place of those shown (only those Show Facts lets through; all are
    /// kept, to filter again). The selection isn't changed: follow with [#setSelected(String)]
    /// (an entity is its id, so selecting the same id tells no one).
    ///
    /// @param entities The entities.
    public void setEntities(final List<EntityEntry> entities) {
        allEntities.clear();
        allEntities.addAll(entities);
        applyFilter();
    }

    /// Selects the entity with this id, or none. A fact hidden by Show Facts is listed rather than
    /// skipped (the caller is usually the canvas, where clicking an object tracks it, and the two
    /// mustn't disagree), and Show Facts is left on.
    ///
    /// @param id The id, or null for none.
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

    /// @return The id of the entity selected, or null if none is.
    public String getSelectedId() {
        final EntityEntry selected = selectionModel.getSelectedObject();
        return selected != null
                ? selected.getId()
                : null;
    }

    /// Sets which areas each entity is in now: called at each query refresh (~300ms during
    /// playback), so the rows are drawn again only when that has changed.
    ///
    /// @param areaMembership Which areas each entity is in; null for none.
    /// @param nameResolver   Names an entity (an area, a person, an object) by its id; null to show
    ///                       ids.
    public void setAreaMembership(final FloorMapAreaMembership areaMembership,
                                  final Function<String, String> nameResolver) {
        final FloorMapAreaMembership next = areaMembership != null
                ? areaMembership
                : FloorMapAreaMembership.EMPTY;
        this.nameResolver = nameResolver;
        // Where each entity was last seen, so a row that loses its position can still say
        for (final String entityId : next.getEntityIds()) {
            lastKnownAreaKey.put(entityId, next.getInnermostAreaKey(entityId));
        }
        final boolean changed = !Objects.equals(this.areaMembership, next);
        this.areaMembership = next;
        if (changed) {
            dataGrid.redraw();
        }
    }

    /// Forgets which areas entities are and were in, for a document opened (again): a new map
    /// mustn't have the last one's areas.
    public void clearAreaState() {
        areaMembership = FloorMapAreaMembership.EMPTY;
        lastKnownAreaKey.clear();
    }

    /// @param onSelectionChange Given the entity selected to track, or null to stop, each time it
    ///                          changes, and again when the selected row is clicked.
    public void setOnSelectionChange(final Consumer<EntityEntry> onSelectionChange) {
        this.onSelectionChange = onSelectionChange;
    }

    /// @param onAddToGroup Given the selected entity when Add to Group is pressed.
    public void setOnAddToGroup(final Consumer<EntityEntry> onAddToGroup) {
        this.onAddToGroup = onAddToGroup;
    }

    // Shows or hides the facts: the button (named for what a press does next) and the rows
    private void setShowFacts(final boolean showFacts) {
        this.showFacts = showFacts;
        showFactsButton.setPressed(showFacts);
        showFactsButton.setTitle(showFacts
                ? "Hide Facts (objects, backgrounds and areas)"
                : "Show Facts (objects, backgrounds and areas)");
        applyFilter();
    }

    // The rows Show Facts lets through. A fact tracked when the facts are hidden would leave the
    // canvas following something not listed, so it is no longer selected (which stops tracking)
    private void applyFilter() {
        final List<EntityEntry> visible = new ArrayList<>(allEntities.size());
        for (final EntityEntry entry : allEntities) {
            if (showFacts || entry.isFromEvents()) {
                visible.add(entry);
            }
        }
        dataProvider.setList(visible);
        dataGrid.setRowData(0, visible);
        final EntityEntry selected = selectionModel.getSelectedObject();
        if (selected != null && !visible.contains(selected)) {
            selectionModel.clear();
        }
    }

    private void addColumns() {
        dataGrid.addColumn(new TextColumn<>() {
            @Override
            public String getValue(final EntityEntry entry) {
                return entry.getDisplayName();
            }
        }, "Name");
        // e.g. person, vehicle, object
        dataGrid.addColumn(new TextColumn<>() {
            @Override
            public String getValue(final EntityEntry entry) {
                return entry.getType();
            }
        }, "Type");
        // The areas it is in: an area's row shows a dash. Resizable, as area names are the
        // user's and can be long
        dataGrid.addResizableColumn(new Column<>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final EntityEntry entry) {
                return areaCell(entry);
            }
        }, "Area", 200);
        // The whole id, e.g. an email address
        dataGrid.addColumn(new TextColumn<>() {
            @Override
            public String getValue(final EntityEntry entry) {
                return entry.getId();
            }
        }, "Id");
    }

    // An Area cell, its tooltip with what won't fit: the areas one a line, a note for an area, or
    // where an entity with no position now was last seen
    private SafeHtml areaCell(final EntityEntry entry) {
        final String id = entry.getId();
        // An area is never inside anything: only objects and users are
        if (areaMembership.isArea(id)) {
            return FloorMapCellHtml.cell(NO_AREA, "Areas are not located inside other areas");
        }
        final List<String> containingKeys = areaMembership.getAreaKeys(id);
        if (containingKeys.isEmpty()) {
            // Outside every area, or with no position now: the tooltip tells which
            final String lastKnown = nameFor(lastKnownAreaKey.get(id));
            return FloorMapCellHtml.cell(NO_AREA, lastKnown != null
                    ? "Not in a known area at this time (last seen in " + lastKnown + ")"
                    : "Not in a known area at this time");
        }
        // Every area, innermost first, on one line that ellipsises: the tooltip lists them all
        final List<String> names = new ArrayList<>(containingKeys.size());
        for (final String key : containingKeys) {
            names.add(nameFor(key));
        }
        final String joined = FloorMapAreaCellText.joinNames(names);
        if (containingKeys.size() == 1) {
            return FloorMapCellHtml.cell(joined, "Inside " + joined);
        }
        final StringBuilder tooltip = new StringBuilder("Inside ")
                .append(containingKeys.size())
                .append(" areas (innermost first):");
        for (final String name : names) {
            tooltip.append("\n• ").append(name);
        }
        return FloorMapCellHtml.cell(joined, tooltip.toString());
    }

    // An entity's name, or its id when there is no resolver or it doesn't know it; null for none
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


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, FloorMapTracking> {

    }
}
