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

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocPresenter;
import stroom.entity.client.presenter.HasToolbar;
import stroom.entity.shared.ExpressionCriteria;
import stroom.floormap.client.FloorMapEditorHelp;
import stroom.floormap.client.editor.FloorMapDocSession;
import stroom.floormap.client.editor.FloorMapEditorModel;
import stroom.floormap.client.editor.FloorMapPendingChanges;
import stroom.floormap.client.event.FloorMapDataEvent;
import stroom.floormap.client.event.MapContextMenuEvent;
import stroom.floormap.client.event.TimeChangeEvent;
import stroom.floormap.client.model.Fact;
import stroom.floormap.client.model.FloorMapObject;
import stroom.floormap.client.presenter.FloorMapEditorPresenter.FloorMapEditorView;
import stroom.floormap.client.value.FloorMapEntryParser;
import stroom.floormap.client.value.ParsedValue;
import stroom.floormap.client.value.ValueAccessor;
import stroom.floormap.client.value.ValueAccessorFactory;
import stroom.floormap.shared.FloorMapDoc;
import stroom.floormap.shared.FloorMapFieldMapping;
import stroom.floormap.shared.FloorMapFieldMapping.Role;
import stroom.floormap.shared.FloorMapJsonKeys;
import stroom.floormap.shared.FloorMapTransformationMatrix;
import stroom.floormap.shared.TypeStyle;
import stroom.floormap.shared.ValueFormat;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.sqlstore.shared.ApplyChangesRequest;
import stroom.sqlstore.shared.ApplyChangesResult;
import stroom.sqlstore.shared.ChangeOperation;
import stroom.sqlstore.shared.FetchAtTimeRequest;
import stroom.sqlstore.shared.SqlTemporalStoreResource;
import stroom.sqlstore.shared.TemporalStoreTimeRange;
import stroom.svg.shared.SvgImage;
import stroom.util.client.Console;
import stroom.util.shared.TemporalEntry;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.InlineSvgToggleButton;
import stroom.widget.help.client.HelpButton;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.presenter.PopupPosition;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import javax.inject.Provider;



/**
 * Presenter for the FloorMap <b>Editor</b> tab.
 *
 * <p>The Editor tab provides a dedicated authoring environment for configuring a
 * {@link FloorMapDoc}. It is <em>always</em> in edit mode and exposes all
 * editing panels simultaneously.</p>
 *
 * <h3>Layout</h3>
 * <pre>
 * ┌─────────────────────────────────────────┬────────────┐
 * │            Map Canvas  (MAIN)           │    DOCK    │
 * ├─────────────────────────────────────────┴────────────┤ ◄─ draggable
 * │              Timeline control (TIMELINE)             │  fixed height
 * ├───────────────────────────┬──────────────────────────┤
 * │        Fact List          │        Time List         │
 * │       (FACT_LIST)         │       (TIME_LIST)        │
 * └───────────────────────────┴──────────────────────────┘
 * </pre>
 *
 * <p>Object properties are <em>not</em> a slot: they open as a modal dialog
 * ({@code FloorMapObjectEditPresenter}), so there is no {@code PROPERTIES}
 * region to lay out. The right-hand dock holds the Layers panel; Groups lives on the Map
 * tab's dock.</p>
 *
 * <h3>Shared selection model (single source of truth)</h3>
 * <p>The state itself lives in {@link FloorMapEditorModel}, which this presenter
 * owns and is the only thing that mutates. Child panels signal changes to this
 * presenter only; they never call each other directly. What the model holds:</p>
 *
 * <ul>
 *   <li>{@code selectedFactKey} — key of the selected fact, or {@code null}</li>
 *   <li>{@code selectedTime} — current timeline position in ms</li>
 *   <li>{@code showAllFacts} — whether "show all" mode is active</li>
 *   <li>{@code pendingChanges} — staged edits awaiting flush</li>
 * </ul>
 *
 * <h3>Staged saves</h3>
 * <p>All edits are buffered in {@link FloorMapPendingChanges}. They are flushed
 * via {@link #onSave(FloorMapDoc, Consumer)} as part of the standard
 * Stroom document save chain. On success the buffer is cleared and all panels
 * are reloaded. On failure a top-level error is shown and the staged changes are
 * <strong>kept</strong> for a retry - the panels are deliberately not reloaded, as that would
 * discard the user's in-progress edits. Replay is idempotent.</p>
 */
public class FloorMapEditorPresenter
        extends DocPresenter<FloorMapEditorView, FloorMapDoc>
        implements HasToolbar {

    /** REST endpoint */
    private static final SqlTemporalStoreResource SQL_TEMPORAL_STORE_RESOURCE =
            GWT.create(SqlTemporalStoreResource.class);

    /** One day in ms */
    private static final long ONE_DAY_MS = 24L * 60L * 60L * 1000L;

    // -----------------------------------------------------------------------
    // View slots
    // -----------------------------------------------------------------------

    /** Slot for the interactive map canvas. */
    public static final Object MAIN = new Object();

    /** Slot for the right-hand dock, which holds the Layers panel. */
    public static final Object DOCK = new Object();

    /**
     * Slot for the timeline scrubber.
     * Fixed-height — stored in {@code FloorMapEditorViewImpl.TIMELINE_HEIGHT}.
     */
    public static final Object TIMELINE = new Object();

    /** Slot for the Fact List panel (leftmost bottom column). */
    public static final Object FACT_LIST = new Object();

    /** Slot for the Time List panel (centre bottom column). */
    public static final Object TIME_LIST = new Object();

    // -----------------------------------------------------------------------
    // Child presenters
    // -----------------------------------------------------------------------

    private final RestFactory restFactory;
    private final FloorMapCanvasPresenter floorMapCanvasPresenter;
    private final FloorMapTimelinePresenter floorMapTimelinePresenter;
    private final FloorMapFactListPresenter floorMapFactListPresenter;
    private final FloorMapTimeListPresenter floorMapTimeListPresenter;
    private final FloorMapObjectEditPresenter floorMapObjectEditPresenter;

    /** The dialog that turns a measured line into the map's scale. */
    private final FloorMapSetScalePresenter floorMapSetScalePresenter;
    private final FloorMapLayersPresenter floorMapLayersPresenter;
    private final FloorMapLayerStylePresenter floorMapLayerStylePresenter;

    /**
     * Cumulative set of types observed this document session — fact types from
     * the canvas plus event types seen via {@link FloorMapDataEvent}
     * (which fires from the Map tab's events query on the shared event bus).
     * Fed to the Layers panel so unsaved types appear as provisional layers.
     */
    private final Set<String> observedTypes = new HashSet<>();

    /** The GWT-free model containing all shared state and pure logic. */
    private final FloorMapEditorModel model;

    /**
     * Toolbar toggle controlling the canvas grid overlay. Shown next to the
     * document save buttons via {@link HasToolbar} whenever the Editor tab is
     * active. On by default — the grid is the primary editing aid.
     */
    private final InlineSvgToggleButton showGridButton;

    /**
     * Contribution to the document toolbar (Save / Save As …): a single help
     * button for the map-interaction help, shown only while the Editor tab is
     * active (via {@link HasToolbar#getToolbars()}).
     */
    private final ButtonPanel helpToolbar;

    /**
     * Toolbar toggle that shows/hides the right-hand dock, which holds the Layers
     * panel. On by default, matching the view, which starts the dock visible.
     */
    private final InlineSvgToggleButton dockToggleButton;

    /**
     * The Editor's pending document-level edits — the area-support upgrade and
     * the Layers-panel type-styles list — with their read/write invariants. The
     * loaded entity is read-only, so these are staged here until save. See the
     * shared, unit-tested {@link FloorMapDocSession}.
     */
    private final FloorMapDocSession docSession = new FloorMapDocSession();

    /**
     * Notified when area support is enabled on this document, so the parent
     * {@link FloorMapPresenter} can refresh the Settings tab's grids — the
     * Settings tab writes {@code valueSchema} wholesale on save and would
     * otherwise silently revert the upgrade.
     */
    private Runnable areaSupportEnabledListener;

    /**
     * Whether the timeline range/scrubber has been initialised. Set on the first
     * {@code onRead}; a save triggers a re-read of every tab, and the timeline
     * must not re-initialise then — it would discard the user's chosen range and
     * jump the scrubber (and every panel) to the newest time. Mirrors
     * {@code FloorMapMapPresenter.timelineInitialised}.
     */
    private boolean timelineInitialised;

    /**
     * UUID of the document this Editor is showing, used to ignore
     * {@link FloorMapDataEvent}s fired by other open FloorMap documents on the
     * shared event bus (which would otherwise pollute this doc's observed types).
     */
    private String docUuid;

    // -----------------------------------------------------------------------

    @Inject
    public FloorMapEditorPresenter(final EventBus eventBus,
                                   final FloorMapEditorView view,
                                   final RestFactory restFactory,
                                   final Provider<FloorMapCanvasPresenter> canvasProvider,
                                   final Provider<FloorMapTimelinePresenter> timelineProvider,
                                   final Provider<FloorMapFactListPresenter> factListProvider,
                                   final Provider<FloorMapTimeListPresenter> timeListProvider,
                                   final Provider<FloorMapObjectEditPresenter> propertiesProvider,
                                   final Provider<FloorMapDockPresenter> dockProvider,
                                   final Provider<FloorMapLayersPresenter> layersProvider,
                                   final Provider<FloorMapLayerStylePresenter> layerStyleProvider,
                                   final Provider<FloorMapSetScalePresenter> setScaleProvider) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.model = new FloorMapEditorModel(
                new java.util.Random(), Console::warn);

        // Each child is obtained via Provider so GIN creates a fresh instance
        // for this presenter rather than sharing singletons used elsewhere.
        this.floorMapCanvasPresenter = canvasProvider.get();
        this.floorMapTimelinePresenter = timelineProvider.get();
        this.floorMapFactListPresenter = factListProvider.get();
        this.floorMapTimeListPresenter = timeListProvider.get();
        this.floorMapObjectEditPresenter = propertiesProvider.get();
        // Let the properties dialog state an image's real-world size: the canvas
        // has already measured any image it has drawn, so this needs no second
        // load.
        this.floorMapObjectEditPresenter.setAspectRatioResolver(
                floorMapCanvasPresenter::getImageAspectRatio);
        final FloorMapDockPresenter floorMapDockPresenter = dockProvider.get();
        this.floorMapSetScalePresenter = setScaleProvider.get();
        this.floorMapLayersPresenter = layersProvider.get();
        this.floorMapLayersPresenter.setEditorMode(true);
        this.floorMapLayerStylePresenter = layerStyleProvider.get();

        // Always in edit mode, with the grid overlay shown as an editing aid.
        floorMapCanvasPresenter.setEditMode(true);
        floorMapCanvasPresenter.setShowGrid(true);

        // Grid on/off toggle, surfaced next to the save buttons (HasToolbar).
        // SvgImage has no dedicated grid glyph; TABLE renders as a grid of cells.
        showGridButton = new InlineSvgToggleButton();
        showGridButton.setSvg(SvgImage.TABLE);
        showGridButton.setTitle("Toggle Grid");
        showGridButton.setState(true);

        // Show/hide the right-hand dock (the Layers panel). On by default, to
        // match FloorMapEditorViewImpl, which starts the dock visible.
        dockToggleButton = new InlineSvgToggleButton();
        dockToggleButton.setSvg(SvgImage.SHOW_MENU);
        dockToggleButton.setTitle("Toggle Controls");
        dockToggleButton.setState(true);
        // Persist a drag as a single translate of the whole selection.
        floorMapCanvasPresenter.setDragHandler(this::onFactsTransformed);
        floorMapCanvasPresenter.setGeometryHandler(this::onFactGeometryEdited);
        floorMapCanvasPresenter.setSelectionHandler(this::onCanvasSelectionChanged);
        floorMapCanvasPresenter.setAreaHandler(this::onAreaDrawn);
        floorMapCanvasPresenter.setScaleHandler(this::onScaleMeasured);

        // Contextual help. The map-interaction help sits on the document toolbar
        // (Save / Save As …) at the right-hand end, contributed via HasToolbar so
        // it appears only while the Editor tab is active — keeping it off the
        // canvas itself. The timeline gets its own help button; the Editor-only
        // Fact List and Time List panels add theirs. (The read-only Map tab
        // reuses the canvas and timeline but is not a HasToolbar for this
        // presenter, so no map-help button appears there.)
        helpToolbar = createHelpToolbar();
        floorMapTimelinePresenter.setHelpContent(FloorMapEditorHelp.timeline());

        // The Layers panel is the Editor dock's tab.
        floorMapDockPresenter.addTab("Layers", floorMapLayersPresenter);
        floorMapLayersPresenter.setChangeHandler(() -> {
            floorMapCanvasPresenter.setLayerVisibility(
                    floorMapLayersPresenter.getHiddenTypes(),
                    floorMapLayersPresenter.getDimmedTypes());
            floorMapCanvasPresenter.setLockedTypes(floorMapLayersPresenter.getLockedTypes());
        });
        // Persist reorder / appearance / discovered-type edits from the panel.
        floorMapLayersPresenter.setTypeStylesEditHandler(this::onLayerTypeStylesEdited);
        // Open the appearance dialog (shape or image, plus colour) for a layer,
        // then hand the edited style back to the panel.
        floorMapLayersPresenter.setStyleEditor(floorMapLayerStylePresenter::show);
        // Full facts-store type-discovery scan behind the panel's Discover action.
        floorMapLayersPresenter.setDiscoverHandler(this::onDiscoverTypes);

        setInSlot(MAIN, floorMapCanvasPresenter);
        setInSlot(DOCK, floorMapDockPresenter);
        setInSlot(TIMELINE, floorMapTimelinePresenter);
        setInSlot(FACT_LIST, floorMapFactListPresenter);
        setInSlot(TIME_LIST, floorMapTimeListPresenter);

        // Name the Fact List as this canvas's text alternative. The Map tab uses
        // its Tracking grid for the same purpose; on the Editor tab the Fact List
        // is the navigable row-per-object view of what the canvas is drawing.
        floorMapCanvasPresenter.setTextAlternativeId(
                floorMapFactListPresenter.getGridElementId());
        // Properties are shown as a modal dialog — no slot needed.
    }

    @Override
    protected void onBind() {
        super.onBind();

        // ---- Toolbar ---------------------------------------------------------
        //noinspection unused e
        registerHandler(showGridButton.addClickHandler(e ->
                floorMapCanvasPresenter.setShowGrid(showGridButton.getState())));
        //noinspection unused e
        registerHandler(dockToggleButton.addClickHandler(e ->
                getView().setDockVisible(dockToggleButton.getState())));

        // Event objects are produced by the events query and broadcast
        // on the shared event bus (driven by the Map tab). Listen here too so
        // their types surface as provisional layers in the Editor's Layers panel.
        registerHandler(getEventBus().addHandler(FloorMapDataEvent.getType(), event -> {
            // Ignore events from other open FloorMap documents (shared event bus).
            if (!java.util.Objects.equals(docUuid, event.getDocUuid())) {
                return;
            }
            boolean added = false;
            for (final FloorMapObject object : event.getObjects()) {
                if (object.getType() != null && observedTypes.add(object.getType())) {
                    added = true;
                }
            }
            if (added) {
                floorMapLayersPresenter.setSeenTypes(observedTypes);
            }
        }));

        // ---- Timeline events ------------------------------------------------
        registerHandler(getEventBus().addHandler(TimeChangeEvent.getType(), event -> {
            if (event.getSource() == floorMapTimelinePresenter) {
                onTimeChange(event.getTime());
            }
        }));

        // ---- Canvas context menu --------------------------------------------
        registerHandler(getEventBus().addHandler(MapContextMenuEvent.getType(), event -> {
            if (event.getSource() == floorMapCanvasPresenter) {
                onCanvasContextMenu(event);
            }
        }));

        // ---- Fact List selection + Show All toggle --------------------------
        floorMapFactListPresenter.setMultiSelectionConsumer(this::onFactListSelectionChanged);
        floorMapFactListPresenter.setShowAllConsumer(() -> onShowAllFactsToggled(true));
        floorMapFactListPresenter.setShowTimeFilteredConsumer(() -> onShowAllFactsToggled(false));
        floorMapFactListPresenter.setAddConsumer(this::onAddFactToFactList);
        floorMapFactListPresenter.setDeleteConsumer(this::onDeleteFactFromFactList);

        // ---- Time List selection / toolbar ----------------------------------
        floorMapTimeListPresenter.setSelectionConsumer(this::onTimeSelectedInTimeList);
        floorMapTimeListPresenter.setEditConsumer(this::onEditTimeInTimeList);
        floorMapTimeListPresenter.setAddConsumer(this::onAddTimeInTimeList);
        floorMapTimeListPresenter.setDeleteConsumer(this::onDeleteTimeFromTimeList);
    }

    // -----------------------------------------------------------------------
    // Toolbar contribution (HasToolbar)
    // -----------------------------------------------------------------------

    /**
     * Builds the help panel for the document toolbar: a single help button
     * describing the map interaction model, pushed to the right-hand end of the
     * toolbar.
     *
     * @return the help toolbar panel
     */
    private ButtonPanel createHelpToolbar() {
        final ButtonPanel buttonPanel = new ButtonPanel();
        // Float the panel to the right-hand end of the flex toolbar container,
        // past the save and grid buttons.
        buttonPanel.getElement().getStyle().setProperty("marginLeft", "auto");
        final HelpButton helpButton = HelpButton.create("Floor Map Editor help");
        helpButton.setHelpContentHeading("Floor Map Editor");
        helpButton.setHelpContent(FloorMapEditorHelp.canvas());
        buttonPanel.addButton(helpButton);
        return buttonPanel;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Contributes the Editor tab's toolbar buttons whenever the tab is shown:
     * the grid toggle (next to the document Save / Save As buttons) and, at the
     * right-hand end, the map help button. {@code DocTabPresenter} appends these
     * after the save buttons.</p>
     */
    @Override
    public List<Widget> getToolbars() {
        final ButtonPanel gridToolbar = new ButtonPanel();
        gridToolbar.addButton(showGridButton);
        gridToolbar.addButton(dockToggleButton);
        return Arrays.asList(gridToolbar, helpToolbar);
    }

    // -----------------------------------------------------------------------
    // DocPresenter lifecycle
    // -----------------------------------------------------------------------

    /**
     * Called by the framework when the document is opened or refreshed.
     *
     * <p>Loads the time range from the server and initialises the timeline,
     * then fetches facts at the initial time and populates all panels.</p>
     */
    @Override
    protected void onRead(final DocRef docRef, final FloorMapDoc document, final boolean readOnly) {
        this.docUuid = docRef != null ? docRef.getUuid() : null;
        // Drop any staged doc-level edits (area upgrade / Layers type-styles)
        // that this just-read document already carries (post-save re-read).
        docSession.reconcileAfterRead(document);

        // The appearance dialog picks layer graphics from this document's assets.
        floorMapLayerStylePresenter.setDocument(document);

        // Populate the Layers panel from the document's type styles. Reset the
        // observed-type accumulator for the (re-)opened document.
        observedTypes.clear();
        floorMapLayersPresenter.setSeenTypes(observedTypes);
        floorMapLayersPresenter.setLayers(typeStyles());
        floorMapCanvasPresenter.setLayerVisibility(
                floorMapLayersPresenter.getHiddenTypes(),
                floorMapLayersPresenter.getDimmedTypes());
        floorMapCanvasPresenter.setLockedTypes(floorMapLayersPresenter.getLockedTypes());

        // Measurement units as this session sees them: a Set Scale calibration
        // is staged in the doc session until save, so read it from there rather
        // than from the just-read document.
        floorMapCanvasPresenter.setMeasurementUnits(sessionEntity().getMeasurementUnits());

        final String mapName = getMapName();
        if (mapName == null) {
            floorMapFactListPresenter.setData(new ArrayList<>());
            floorMapTimeListPresenter.setData(new ArrayList<>());
            return;
        }

        // Pass mapName + doc to Properties panel so it can resolve asset paths.
        floorMapObjectEditPresenter.setMapName(mapName);
        floorMapObjectEditPresenter.setFloorMapDoc(sessionEntity());

        // Load time range → initialise slider → load canvas + Fact List.
        // Initialise the range/scrubber on the first read only; a save-triggered
        // re-read keeps the user's chosen range and current position (otherwise
        // the scrubber would jump to the newest time on every save).
        restFactory.create(SQL_TEMPORAL_STORE_RESOURCE)
                .method(res -> res.getTimeRange(mapName))
                .onSuccess(range -> {
                    if (!timelineInitialised) {
                        timelineInitialised = true;
                        initTimeline(range);
                    }
                    loadAtTime(model.getSelectedTime());
                })
                .exec();
    }

    /**
     * Called by the framework when the document is saved.
     *
     * <p>The Editor tab does not normally write state into the
     * {@link FloorMapDoc} itself (temporal store edits are flushed separately
     * via {@link #onSave}) — except for the doc-level edits staged in the session: the
     * area-support upgrade, the Layers panel's type-styles list, and a Set Scale
     * calibration, all of which are merged into the document here. The merge is re-applied to the
     * <em>incoming</em> document (rather than writing the stored lists
     * verbatim) so it stays correct regardless of the order the tabs' onWrite
     * methods run in.</p>
     */
    @Override
    protected FloorMapDoc onWrite(final FloorMapDoc document) {
        return docSession.applyToWrite(document);
    }

    /**
     * The value schema in effect for this editing session: the pending
     * area-support upgrade when one exists, otherwise the entity's persisted
     * schema.
     */
    private List<FloorMapFieldMapping> valueSchema() {
        return docSession.valueSchema(getEntity().getValueSchema());
    }

    /**
     * The type styles in effect for this editing session (see
     * {@link #valueSchema()}).
     */
    private List<TypeStyle> typeStyles() {
        return docSession.typeStyles(getEntity().getTypeStyles());
    }

    /**
     * The document as this editing session sees it: the loaded entity with any
     * pending area upgrade applied. Must be used wherever the document is
     * handed to a child presenter that resolves schema roles itself (the
     * object-edit dialog), or fill/opacity/geometry would silently resolve
     * against the pre-upgrade schema until the document is saved.
     */
    private FloorMapDoc sessionEntity() {
        return docSession.sessionEntity(getEntity());
    }

    /**
     * Sets the callback notified when area support is enabled on this document
     * (see {@link #ensureAreaSupport}).
     */
    public void setAreaSupportEnabledListener(final Runnable listener) {
        this.areaSupportEnabledListener = listener;
    }

    /**
     * Adopts an initial view {@code {scale, offsetX, offsetY}} computed by the
     * Map tab so the Editor's first frame matches and the view doesn't jump on
     * the tab switch. Only affects the one-time initial view; user pan/zoom in
     * the Editor is independent afterwards.
     *
     * @param view the view state, or {@code null} to fit locally
     */
    public void setInitialViewState(final double[] view) {
        floorMapCanvasPresenter.setInitialViewState(view);
    }

    /**
     * Handles a type-styles edit from the Layers panel (reorder / appearance /
     * discovered types): stages the new list, applies it live to the canvas and
     * object-edit dialog, and marks the document dirty.
     *
     * @param newTypeStyles the new ordered type styles
     */
    private void onLayerTypeStylesEdited(final List<TypeStyle> newTypeStyles) {
        docSession.stageTypeStyles(newTypeStyles);
        floorMapCanvasPresenter.setTypeStyles(newTypeStyles);
        floorMapObjectEditPresenter.setFloorMapDoc(sessionEntity());
        setDirty(true);
    }

    /**
     * Scans the whole facts store for every distinct type (all keys, all times),
     * unions it with the types already observed this session (fact + event), and
     * hands the result to the Layers panel to merge into the saved layers. Backs
     * the panel's Discover action.
     */
    private void onDiscoverTypes() {
        final Set<String> discovered = new HashSet<>(observedTypes);
        final DocRef store = getEntity() != null
                ? getEntity().getFactsStoreRef()
                : null;
        if (store == null || store.getName() == null || store.getName().isEmpty()) {
            // No facts store to scan — still commit anything already observed.
            floorMapLayersPresenter.mergeDiscovered(discovered);
            return;
        }
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(ExpressionTerm.builder()
                        .field("Map").condition(Condition.EQUALS).value(store.getName())
                        .build())
                .build();
        final ExpressionCriteria criteria = new ExpressionCriteria(expression);
        final List<FloorMapFieldMapping> schema = valueSchema();
        restFactory.create(SQL_TEMPORAL_STORE_RESOURCE)
                .method(res -> res.find(criteria))
                .onSuccess(result -> {
                    final List<TemporalEntry> entries = result != null
                            ? result.getValues()
                            : null;
                    final List<Fact> parsed = FloorMapEntryParser.parse(
                            entries, schema,
                            ValueAccessorFactory.forFormat(getEntity().getValueFormat()), null);
                    for (final Fact fact : parsed) {
                        if (fact.getType() != null && !fact.getType().isEmpty()) {
                            discovered.add(fact.getType());
                        }
                    }
                    floorMapLayersPresenter.mergeDiscovered(discovered);
                })
                .exec();
    }

    // -----------------------------------------------------------------------
    // Save chain hook (called by FloorMapPresenter)
    // -----------------------------------------------------------------------

    /**
     * Returns {@code true} when there are staged edits awaiting flush.
     *
     * <p>Used by {@link FloorMapPresenter#hasAssociatedDirty()} to drive the
     * dirty indicator on the document tab.</p>
     *
     * @return {@code true} when pending changes exist
     */
    public boolean hasPendingChanges() {
        return model.hasPendingChanges();
    }

    /**
     * Pauses the timeline if it is currently playing.
     *
     * <p>Called by {@link FloorMapPresenter} when the user navigates away from
     * the Editor tab, so that background queries are not issued while the tab
     * is hidden.</p>
     */
    public void pauseTimeline() {
        floorMapTimelinePresenter.pause();
    }

    /**
     * Stops the clock when the document is closed.
     *
     * <p>Closing a document tab does not unbind its presenters, so a timeline
     * left playing keeps its animation loop — and the per-tick fetches it drives
     * — running for the rest of the session.</p>
     */
    @Override
    public void onClose() {
        super.onClose();
        floorMapTimelinePresenter.pause();
    }

    /**
     * Flushes pending changes to the server as part of the Stroom save chain.
     *
     * <p>Called by {@link FloorMapPresenter} via {@code getPostSaveCallback()}
     * after the {@link FloorMapDoc} has been saved. Sends all staged operations
     * in a single {@code applyChanges} call.</p>
     *
     * <ul>
     *   <li><b>Success</b>: the operations that were sent are discarded, all
     *       panels reload from the server, and {@code callback} is invoked with
     *       the document.</li>
     *   <li><b>Failure</b> (server-side error or HTTP error): the buffer is
     *       <em>kept</em> so the user can retry, a top-level alert is shown, and
     *       panels are not reloaded over in-progress edits. The {@code callback}
     *       is <em>not</em> invoked, so the save chain stops here.</li>
     * </ul>
     *
     * @param document the saved document; passed through to the callback on
     *                 success
     * @param callback invoked with the document only when the flush succeeds
     */
    public void onSave(final FloorMapDoc document, final Consumer<FloorMapDoc> callback) {
        if (!model.hasPendingChanges()) {
            callback.accept(document);
            return;
        }

        // Snapshot how many operations this flush is sending. The UI stays live
        // during the round-trip, so anything the user stages meanwhile must not be
        // discarded on success — it was never sent.
        final int sentCount = model.getPendingChanges().getChanges().size();

        restFactory.create(SQL_TEMPORAL_STORE_RESOURCE)
                .method(res -> res.applyChanges(buildApplyChangesRequest()))
                .onSuccess(result -> {
                    if (result.isSuccess()) {
                        // Only discard the staged changes once the server has
                        // confirmed they were applied — and only the ones sent.
                        model.getPendingChanges().clearSent(sentCount);
                        // Reload all panels so they reflect the newly-persisted data
                        reloadAllPanels();
                        callback.accept(document);
                    } else {
                        onFlushError(result);
                    }
                })
                .onFailure(error -> {
                    // Keep the staged changes so a transient failure (network
                    // blip, timeout — where we cannot even know whether the
                    // server committed) does not throw away the editing session.
                    // applyChanges replays idempotently (creations/updates are
                    // upserts, deletions are idempotent), so the user can simply
                    // retry the save. Do NOT reload over their in-progress edits.
                    AlertEvent.fireError(this,
                            "Error saving floor map editor changes — your changes "
                            + "have been kept, please try saving again: "
                            + error.getMessage(), null);
                })
                .taskMonitorFactory(this)
                .exec();
    }

    /**
     * Builds an {@link ApplyChangesRequest} from the model's pending changes.
     * This method lives in the presenter (not the model) because the
     * {@code sqlstore.shared} types are not available in {@code stroom-core-shared}.
     */
    private ApplyChangesRequest buildApplyChangesRequest() {
        final List<ChangeOperation> ops = new ArrayList<>();
        for (final FloorMapPendingChanges.PendingChange change : model.getPendingChanges().getChanges()) {
            if (change instanceof FloorMapPendingChanges.Creation) {
                ops.add(ChangeOperation.upsert(
                        ((FloorMapPendingChanges.Creation) change).getEntry()));
            } else if (change instanceof FloorMapPendingChanges.Update) {
                ops.add(ChangeOperation.upsert(
                        ((FloorMapPendingChanges.Update) change).getEntry()));
            } else if (change instanceof FloorMapPendingChanges.Deletion) {
                ops.add(ChangeOperation.delete(
                        ((FloorMapPendingChanges.Deletion) change).getId()));
            }
        }
        return new ApplyChangesRequest(ops);
    }

    // -----------------------------------------------------------------------
    // Timeline
    // -----------------------------------------------------------------------

    /**
     * Initialises the timeline slider from the server-supplied time range.
     *
     * <ul>
     *   <li>If the store is empty the slider covers [now − 1 day, now + 1 day]
     *       and the initial selected time is now.</li>
     *   <li>Otherwise the slider range is [min, max] and the initial selected
     *       time is max (the most recent entry) — except when min equals max, where the range is
     *       padded to [min − 1 day, max + 1 day], since a zero-length range breaks playback.</li>
     * </ul>
     *
     * <p>A real range is also handed to
     * {@link FloorMapTimelinePresenter#setDataRange(long, long)}, which is what enables the
     * <b>Show All</b> button. That is the only thing that enables it, and on the Map tab it comes
     * from the events histogram — which this tab has none of, and needs none of: the store's own
     * min and max already are the extent Show All should fit to. Without this the button sits
     * permanently greyed, which is how it behaved until 2026-09-11.</p>
     *
     * <p>Deliberately not called from the {@code min == max} branch. {@code setDataRange} would
     * accept it, but the Show All handler guards on {@code dataRangeMin < dataRangeMax}, so arming
     * it for a single-instant store would light the button up and do nothing when pressed.</p>
     *
     * @param range the time range returned by the server; never {@code null}
     */
    private void initTimeline(final TemporalStoreTimeRange range) {
        final long now = System.currentTimeMillis();

        if (range.getMinEffectiveTimeMs() == null || range.getMaxEffectiveTimeMs() == null) {
            floorMapTimelinePresenter.setTimeRange(now - ONE_DAY_MS, now + ONE_DAY_MS);
            floorMapTimelinePresenter.setCurrentTime(now);
            model.setSelectedTime(now);
        } else {
            final long min = range.getMinEffectiveTimeMs();
            final long max = range.getMaxEffectiveTimeMs();
            if (min < max) {
                floorMapTimelinePresenter.setTimeRange(min, max);
                floorMapTimelinePresenter.setDataRange(min, max);
            } else {
                // A store holding a single effective time reports min == max, which is not
                // a usable playback range — a zero-length range leaves the step buttons and
                // the progress bar with nowhere to move, and makes playback wrap on every
                // frame. Show a window around that instant instead, matching what the
                // no-data branch above does.
                floorMapTimelinePresenter.setTimeRange(min - ONE_DAY_MS, max + ONE_DAY_MS);
            }
            floorMapTimelinePresenter.setCurrentTime(max);
            model.setSelectedTime(max);
        }
    }

    /**
     * Called when the user moves the timeline scrubber.
     *
     * <p>Updates the model's selected time and reloads the canvas and Fact List
     * at the new time. If a fact is selected, also refreshes the Time List
     * from the model's server entries for the selected fact overlaid with pending changes
     * (no extra server call).</p>
     *
     * @param timeMs the new timeline position in milliseconds
     */
    private void onTimeChange(final long timeMs) {
        model.setSelectedTime(timeMs);
        // Keep this canvas's accessible summary and live region on the same clock
        // as its timeline's labels. The Editor tab has its own canvas and timeline
        // pair, so it needs this wiring independently of the Map tab's.
        floorMapCanvasPresenter.setCurrentTimeText(
                floorMapTimelinePresenter.formatTime(timeMs));
        loadAtTime(timeMs);
        if (model.getSelectedFactKey() != null) {
            refreshTimeListAtTime(timeMs);
        }
    }

    // -----------------------------------------------------------------------
    // Data loading
    // -----------------------------------------------------------------------

    /**
     * Reloads the canvas (and Fact List) for the given timeline position.
     *
     * <p>The canvas always shows the facts <strong>active at the scrubber
     * time</strong>, so the canvas data is always the time-filtered fetch —
     * the "Show all" toggle does not change what the canvas renders. When
     * "Show all" is on, an additional fetch retrieves one row per key (each
     * key's latest shard) purely so the <strong>Fact List</strong> can list
     * every fact in the store, selectable regardless of whether it is on
     * screen at the current time.</p>
     *
     * @param timeMs the point in time to query
     */
    private void loadAtTime(final long timeMs) {
        final String mapName = getMapName();
        if (mapName == null) {
            return;
        }

        fetchAtTime(mapName, timeMs);
        if (model.isShowAllFacts()) {
            fetchAllKeysForFactList(mapName);
        }
    }

    /**
     * Fetches the most recent entry per key at or before {@code timeMs}
     * from the server, then calls {@link #onEntriesFetched} to update
     * the canvas (and, outside "Show all" mode, the Fact List).
     *
     * @param mapName the temporal store name
     * @param timeMs  the upper bound for effective_time
     */
    private void fetchAtTime(final String mapName, final long timeMs) {
        final FetchAtTimeRequest request = new FetchAtTimeRequest(mapName, timeMs);
        restFactory.create(SQL_TEMPORAL_STORE_RESOURCE)
                .method(res -> res.fetchAtTime(request))
                .onSuccess(this::onEntriesFetched)
                .exec();
    }

    /**
     * Fetches one row per key — each key's latest shard, with no time bound —
     * to populate the Fact List in "Show all" mode. Feeds the Fact List
     * <em>only</em>; the canvas is owned by the {@link #fetchAtTime} path.
     *
     * @param mapName the temporal store name
     */
    private void fetchAllKeysForFactList(final String mapName) {
        restFactory.create(SQL_TEMPORAL_STORE_RESOURCE)
                .method(res -> res.fetchAll(mapName))
                .onSuccess(this::onAllKeysFetched)
                .exec();
    }

    /**
     * Callback for {@link #fetchAtTime}. Stores the server entries, merges
     * pending changes, then refreshes the canvas. The Fact List is refreshed
     * too unless "Show all" is on — in that mode the list is owned by
     * {@link #onAllKeysFetched}, and rebuilding it here would race the two
     * responses and clobber the full key list with the time-filtered subset.
     */
    private void onEntriesFetched(final List<TemporalEntry> entries) {
        final List<TemporalEntry> merged = model.onEntriesFetched(entries);
        updateCanvas(merged);
        if (!model.isShowAllFacts()) {
            updateFactList(merged);
        }
    }

    /**
     * Callback for {@link #fetchAllKeysForFactList}: refreshes the Fact List
     * (one row per key, pending changes overlaid so unflushed creations still
     * appear). Ignored if "Show all" was toggled off while the request was in
     * flight — the time-filtered path owns the list again.
     */
    private void onAllKeysFetched(final List<TemporalEntry> entries) {
        if (!model.isShowAllFacts()) {
            return;
        }
        updateFactList(model.mergePendingChanges(entries));
    }

    /**
     * Fetches all temporal entries (every effective time) for the given key.
     * Uses {@code find(Map=name, Key=key)} with no time term.
     *
     * @param mapName the temporal store name
     * @param key     the fact key
     */
    private void fetchTimeList(final String mapName, final String key) {
        fetchTimeList(mapName, key, null);
    }

    /**
     * Fetches all shards for the key, then runs {@code after} (if non-null) once
     * the model's time list has been populated. Callers that must act on the
     * fresh time list (e.g. "Add Time Version" on a fact that isn't the current
     * selection) use the callback so they don't run against the previous
     * selection's stale shards.
     *
     * @param mapName the temporal store name
     * @param key     the fact key
     * @param after   action to run after the fetch completes, or {@code null}
     */
    private void fetchTimeList(final String mapName, final String key, final Runnable after) {
        restFactory.create(SQL_TEMPORAL_STORE_RESOURCE)
                .method(res -> res.find(mapKeyCriteria(mapName, key)))
                .onSuccess(result -> {
                    // Drop a late response for a fact that is no longer selected.
                    // Storing it would leave the model holding another fact's
                    // shards, which silently defeats the same-time overwrite
                    // guards that read the merged time list.
                    if (!Objects.equals(key, model.getSelectedFactKey())) {
                        return;
                    }
                    model.onTimeListFetched(
                            result != null ? result.getValues() : null);
                    refreshTimeListAtTime(model.getSelectedTime());
                    if (after != null) {
                        after.run();
                    }
                })
                .exec();
    }

    /** As {@link #mapKeyCriteria} but matching any of {@code keys}. */
    private ExpressionCriteria mapKeysCriteria(final String mapName, final List<String> keys) {
        final ExpressionOperator.Builder anyKey = ExpressionOperator.builder()
                .op(ExpressionOperator.Op.OR);
        for (final String key : keys) {
            anyKey.addTerm(ExpressionTerm.builder()
                    .field("Key").condition(Condition.EQUALS).value(key)
                    .build());
        }
        return new ExpressionCriteria(ExpressionOperator.builder()
                .addTerm(ExpressionTerm.builder()
                        .field("Map").condition(Condition.EQUALS).value(mapName)
                        .build())
                .addOperator(anyKey.build())
                .build());
    }

    /**
     * Criteria selecting <em>every</em> shard (all effective times) of a single
     * fact key within a map. Shared by the Time List fetch and the "delete all
     * versions" flow.
     */
    private ExpressionCriteria mapKeyCriteria(final String mapName, final String key) {
        return new ExpressionCriteria(ExpressionOperator.builder()
                .addTerm(ExpressionTerm.builder()
                        .field("Map").condition(Condition.EQUALS).value(mapName)
                        .build())
                .addTerm(ExpressionTerm.builder()
                        .field("Key").condition(Condition.EQUALS).value(key)
                        .build())
                .build());
    }

    // -----------------------------------------------------------------------
    // Canvas + Fact List rendering
    // -----------------------------------------------------------------------

    /**
     * Updates the canvas from a merged entry list, rendering the facts active
     * at the current scrubber time. Also ensures the currently selected fact
     * remains highlighted on the canvas.
     *
     * @param entries merged entries (server data + pending changes)
     */
    private void updateCanvas(final List<TemporalEntry> entries) {
        // Update canvas using shared parser (applies world-to-map transform)
        final List<Fact> facts = model.parseForCanvas(
                entries, valueSchema(),
                ValueAccessorFactory.forFormat(getEntity().getValueFormat()));
        floorMapCanvasPresenter.setTypeStyles(typeStyles());
        floorMapCanvasPresenter.setFacts(facts);
        // Accumulate fact types seen on the canvas and surface any not yet
        // configured as layers in the Layers panel (event types are added
        // separately via the FloorMapDataEvent handler).
        boolean added = false;
        for (final Fact fact : facts) {
            if (fact.getType() != null && observedTypes.add(fact.getType())) {
                added = true;
            }
        }
        if (added) {
            floorMapLayersPresenter.setSeenTypes(observedTypes);
        }
        // Restore the full (multi-)selection highlight after re-rendering, so a
        // group stays selected across canvas refreshes (e.g. after a transform).
        floorMapCanvasPresenter.setSelectedObjectIds(model.getSelectedFactKeys());
    }

    /**
     * Updates the Fact List from a merged entry list — one row per unique key.
     * The entry list may contain multiple entries for the same key (e.g. when a
     * pending creation overlaps with a server-returned entry), so it is
     * deduplicated by key to show each object exactly once.
     *
     * @param entries merged entries (server data + pending changes)
     */
    private void updateFactList(final List<TemporalEntry> entries) {
        final List<FloorMapFieldMapping> schema = valueSchema();
        final List<FloorMapFactListPresenter.FactObject> factObjects = new ArrayList<>();
        final Set<String> seenKeys = new HashSet<>();
        for (final TemporalEntry entry : entries) {
            if (seenKeys.add(entry.getKey())) {
                factObjects.add(FloorMapFactListPresenter.FactObject.fromEntry(entry, schema));
            }
        }

        floorMapFactListPresenter.setData(factObjects);

        // Restore the full (multi-)selection highlight without re-firing, so the
        // Fact List stays in sync with the canvas across data refreshes.
        floorMapFactListPresenter.setSelectedKeys(model.getSelectedFactKeys());
    }

    // -----------------------------------------------------------------------
    // Event handlers
    // -----------------------------------------------------------------------

    /**
     * Called when a canvas interaction changes the selection (click, Shift-click
     * toggle, or rubber-band marquee). The canvas already holds the highlight;
     * this syncs the model, Fact List and side panels.
     *
     * @param keys    the full selection in selection order
     * @param primary the first-selected id, or {@code null} when empty (derived
     *                from {@code keys}, so unused here)
     */
    private void onCanvasSelectionChanged(final java.util.Collection<String> keys,
                                          final String primary) {
        applySelection(keys);
    }

    /**
     * Makes {@code keys} the current selection across the model, the canvas
     * highlight and the Fact List, then reflects it into the Time List and
     * Properties panel. Loop-safe: the canvas and Fact List inbound setters do
     * not re-fire their change callbacks.
     *
     * @param keys the fact keys to select
     */
    private void applySelection(final java.util.Collection<String> keys) {
        model.setSelection(keys);
        floorMapCanvasPresenter.setSelectedObjectIds(keys);
        floorMapFactListPresenter.setSelectedKeys(keys);
        reflectSelectionSideEffects(keys);
    }

    /**
     * Updates the Time List and Properties panel for the current selection. The
     * time shard list is meaningful only for a single fact, so it is shown only
     * when exactly one fact is selected and blanked otherwise (nothing selected,
     * or a multi-selection).
     *
     * @param keys the current selection
     */
    private void reflectSelectionSideEffects(final java.util.Collection<String> keys) {
        if (keys != null && keys.size() == 1) {
            final String primary = keys.iterator().next();
            floorMapObjectEditPresenter.setMapName(getMapName());
            floorMapObjectEditPresenter.setObject(primary);
            loadTimeListForSelectedFact();
        } else {
            floorMapTimeListPresenter.setData(new ArrayList<>());
            floorMapObjectEditPresenter.setObject(null);
        }
    }

    /**
     * Persists a completed transform gesture (move/rotate/scale) as a single
     * map-space affine applied to the whole selection (see
     * {@link FloorMapCanvasPresenter.DragHandler}). Each fact's world-to-map
     * matrix is composed as {@code transform · oldMatrix}.
     */
    private void onFactsTransformed(final java.util.Collection<String> keys,
                                    final FloorMapTransformationMatrix transform) {
        if (getMapName() == null || keys == null || keys.isEmpty()) {
            return;
        }
        try {
            final int n = model.transformFacts(keys, transform,
                    valueSchema(),
                    ValueAccessorFactory.forFormat(getEntity().getValueFormat()));
            if (n > 0) {
                setDirty(true);
            }
        } catch (final Exception ex) {
            AlertEvent.fireError(this,
                    "Cannot transform selection: " + ex.getMessage(), null);
        }
        refreshCanvasOnly();
    }

    /**
     * Called when an area's geometry is edited on the canvas (a vertex moved,
     * inserted or deleted). Persists the new local-frame vertices through the
     * pending-changes pipeline and refreshes the canvas.
     *
     * @param key           the area fact's key
     * @param localVertices the new local-frame vertices ({@code >= 3})
     */
    private void onFactGeometryEdited(final String key, final double[][] localVertices) {
        if (getMapName() == null || key == null || localVertices == null) {
            return;
        }
        try {
            final boolean changed = model.updateFactGeometry(key, localVertices,
                    valueSchema(),
                    ValueAccessorFactory.forFormat(getEntity().getValueFormat()));
            if (changed) {
                setDirty(true);
            }
        } catch (final Exception ex) {
            AlertEvent.fireError(this,
                    "Cannot edit area geometry: " + ex.getMessage(), null);
        }
        refreshCanvasOnly();
    }


    /**
     * Refreshes the canvas by re-applying pending changes and reparsing,
     * without reloading the Fact List (which would clear its selection
     * and cascade into the Time List).
     */
    private void refreshCanvasOnly() {
        updateCanvas(model.buildMergedCanvasEntries());
    }

    /**
     * Called when the Fact List selection changes through user interaction
     * (click, ctrl/shift-click). Mirrors the selection onto the canvas, model
     * and side panels via {@link #applySelection}.
     *
     * @param factObjects the selected fact objects (possibly empty)
     */
    private void onFactListSelectionChanged(
            final java.util.List<FloorMapFactListPresenter.FactObject> factObjects) {
        final java.util.List<String> keys = new ArrayList<>();
        for (final FloorMapFactListPresenter.FactObject o : factObjects) {
            keys.add(o.getKey());
        }
        applySelection(keys);
    }

    /**
     * Called when a row in the Time List is selected.
     *
     * <p>Moves the timeline scrubber to the entry's effective time and reloads
     * the canvas so all panels stay in sync. {@link FloorMapTimelinePresenter#setCurrentTime}
     * only repositions the scrubber — it does <em>not</em> fire a
     * {@link stroom.floormap.client.event.TimeChangeEvent} — so there is no
     * feedback loop back into {@link #onTimeChange}.</p>
     *
     * <p>Selecting a shard first <strong>stops playback</strong> if the timeline
     * is auto-advancing: otherwise the scrubber would keep moving and immediately
     * change the active shard out from under the user's selection. The pause is a
     * no-op when the timeline is already paused.</p>
     *
     * @param entry the selected entry, or {@code null}
     */
    private void onTimeSelectedInTimeList(final TemporalEntry entry) {
        if (entry != null) {
            // Stop auto-advance before repositioning, so playback can't race the
            // selection and move the scrubber off the chosen shard.
            floorMapTimelinePresenter.pause();
            model.setSelectedTime(entry.getEffectiveTimeMs());
            floorMapTimelinePresenter.setCurrentTime(model.getSelectedTime());
            loadAtTime(model.getSelectedTime());
        }
    }

    /**
     * Called when the Time List's Edit button is clicked.
     * Opens the Properties dialog for the currently selected entry.
     *
     * @param entry the selected entry to edit
     */
    private void onEditTimeInTimeList(final TemporalEntry entry) {
        if (entry == null) {
            return;
        }
        floorMapObjectEditPresenter.showForEdit(
                "Edit Time Properties",
                entry,
                (saved, clone) -> {
                    final boolean timeChanged = !Objects.equals(
                            saved.getEffectiveTimeMs(), entry.getEffectiveTimeMs());

                    // Refuse to land on a time that already has a version. The
                    // store upserts by (key, effective-time), so both a move and
                    // a clone would overwrite it — and a move would additionally
                    // delete the original, collapsing two versions into one.
                    if (timeChanged && model.selectedFactHasEntryAtTime(saved.getEffectiveTimeMs())) {
                        AlertEvent.fireWarn(this,
                                "A time version already exists at that time for '"
                                + saved.getKey() + "'. Choose a different effective "
                                + "time, or edit the existing version instead.",
                                null);
                        // Reject, so the dialog stays open with the user's input.
                        return false;
                    }

                    // A "move" takes the version to its new time and removes the old
                    // shard; a "clone" leaves the original alongside the new one. The
                    // delete-and-upsert pairing a move needs lives in the model, so it
                    // cannot be half-applied from here.
                    if (clone || !timeChanged) {
                        model.stageUpdate(saved);
                    } else {
                        model.stageVersionMove(saved, entry.getEffectiveTimeMs());
                    }
                    setDirty(true);
                    refreshTimeListAtTime(saved.getEffectiveTimeMs());
                    refreshCanvas();
                    return true;
                });
    }

    /**
     * Called when the Time List's Add button is clicked.
     * Creates a new entry defaulted to the timeline scrubber position and cloned
     * from the shard in effect at that time (the latest shard whose effective
     * time is at or before the scrubber), staged in the pending-changes buffer.
     */
    private void onAddTimeInTimeList() {
        final String mapName = getMapName();
        if (mapName == null || model.getSelectedFactKey() == null) {
            return;
        }

        // Default the new shard to the timeline scrubber position, cloning its
        // attributes from the shard in effect at that time.
        final long newTime = model.getSelectedTime();

        // Refuse to add a version at a time that already has one: the store
        // upserts by (key, effective-time), so this would silently overwrite
        // the existing shard rather than adding a new version.
        if (model.selectedFactHasEntryAtTime(newTime)) {
            AlertEvent.fireWarn(this,
                    "A time version already exists at this time for '"
                    + model.getSelectedFactKey() + "'. Move the timeline to a "
                    + "different time to add a new version, or edit the existing one.",
                    null);
            return;
        }

        final TemporalEntry newEntry = model.buildNewEntryAtTime(mapName, newTime);

        floorMapObjectEditPresenter.show(
                "Add Time Properties",
                newEntry,
                saved -> {
                    model.stageCreation(saved);
                    setDirty(true);
                    loadAtTime(model.getSelectedTime());
                    refreshTimeListAtTime(model.getSelectedTime());
                });
    }

    /**
     * Called when the Fact List's Add button is clicked.
     *
     * <p>Delegates to {@link #onAddObjectAtPosition} using the centre of the
     * visible canvas area as the initial position, giving the same
     * properties-editor experience as the right-click "Add Object Here" action.</p>
     */
    private void onAddFactToFactList() {
        final double[] centre = floorMapCanvasPresenter.getVisibleCentreMapCoords();
        onAddObjectAtPosition(centre[0], centre[1]);
    }

    /**
     * Called when the Fact List's Delete button is clicked.
     * Confirms with the user then stages deletions for all time-entries of the selected fact.
     *
     * @param key the fact key to delete
     */
    private void onDeleteFactFromFactList(final String key) {
        if (key == null) {
            return;
        }
        final String mapName = getMapName();
        if (mapName == null) {
            return;
        }
        // Only blank the Time List / edit panel if the fact being deleted is the
        // one currently selected (the canvas context menu can delete any fact,
        // not just the selected one — deleting an unselected fact must not wipe
        // the selected fact's panels).
        final boolean wasSelected = key.equals(model.getSelectedFactKey());
        ConfirmEvent.fire(this,
                "Delete all entries for '" + key + "'? This cannot be undone.",
                ok -> {
                    if (ok) {
                        deleteAllShardsForKey(mapName, key, () -> {
                            if (wasSelected) {
                                floorMapTimeListPresenter.setData(new ArrayList<>());
                                floorMapObjectEditPresenter.loadEntry(null);
                            }
                            loadAtTime(model.getSelectedTime());
                        });
                    }
                });
    }

    /**
     * Stages deletion of every shard of every key in one request.
     *
     * <p>The criteria match the map and any of the keys, so a single fetch returns all their
     * history; the entries are then grouped by key because the model stages a key at a time. Keys
     * with no entries are still staged, so a fact with nothing stored is not silently skipped.</p>
     *
     * <p>Uses an OR of equality terms rather than a single {@code IN}: the {@code IN} handler
     * splits its value on commas and trims the parts, so any key containing a comma or leading
     * space would be silently mangled into keys that match the wrong rows - or none. This deletes
     * data, so it is not the place to assume keys are well behaved.</p>
     */
    private void deleteAllShardsForKeys(final String mapName, final List<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        restFactory.create(SQL_TEMPORAL_STORE_RESOURCE)
                .method(res -> res.find(mapKeysCriteria(mapName, keys)))
                .onSuccess(result -> {
                    final List<TemporalEntry> all = result != null
                            ? result.getValues()
                            : new ArrayList<>();
                    boolean anyStaged = false;
                    for (final String key : keys) {
                        final List<TemporalEntry> forKey = new ArrayList<>();
                        for (final TemporalEntry entry : all) {
                            if (Objects.equals(key, entry.getKey())) {
                                forKey.add(entry);
                            }
                        }
                        anyStaged |= model.stageFactDeletionForAllShards(key, forKey);
                    }
                    if (anyStaged) {
                        setDirty(true);
                    }
                    applySelection(new ArrayList<>());
                    loadAtTime(model.getSelectedTime());
                })
                .onFailure(error -> AlertEvent.fireError(this,
                        "Could not load all versions to delete the "
                        + keys.size() + " selected objects: " + error.getMessage(), null))
                .taskMonitorFactory(this)
                .exec();
    }

    /**
     * Fetches every shard of {@code key} (all effective times) and stages a
     * deletion for each, so "delete object" removes the whole history rather
     * than only the shard active at the scrubber (which would let the fact
     * reappear at other times / after reload). Runs {@code onDone} once the
     * deletions are staged.
     *
     * @param mapName the map name
     * @param key     the fact key to delete
     * @param onDone  run after deletions are staged (UI refresh)
     */
    private void deleteAllShardsForKey(final String mapName,
                                       final String key,
                                       final Runnable onDone) {
        restFactory.create(SQL_TEMPORAL_STORE_RESOURCE)
                .method(res -> res.find(mapKeyCriteria(mapName, key)))
                .onSuccess(result -> {
                    if (model.stageFactDeletionForAllShards(
                            key, result != null ? result.getValues() : null)) {
                        setDirty(true);
                    }
                    onDone.run();
                })
                .onFailure(error -> {
                    AlertEvent.fireError(this,
                            "Could not load all versions to delete '" + key + "': "
                            + error.getMessage(), null);
                    // Still run onDone so a batch delete's completion count can't
                    // stall on a single failed key.
                    onDone.run();
                })
                .taskMonitorFactory(this)
                .exec();
    }

    /**
     * Called when the Time List's Delete button is clicked on an entry.
     * Stages a deletion in the pending-changes buffer; the entry disappears
     * immediately from the Time List (optimistic hide).
     *
     * @param entry the entry to delete
     */
    private void onDeleteTimeFromTimeList(final TemporalEntry entry) {
        // Stage the deletion and compute the row to select afterwards (the item
        // above the deleted one) so the user stays in context.
        final int selectIndex = model.stageTimeEntryDeletion(entry);
        setDirty(true);

        // Rebuild the Time List optimistically from the same merged view.
        final List<TemporalEntry> merged = model.buildMergedTimeList();
        floorMapTimeListPresenter.setData(merged);
        floorMapTimeListPresenter.selectAtIndex(selectIndex);

        refreshCanvasOnly();
    }

    // -----------------------------------------------------------------------
    // Canvas context menu
    // -----------------------------------------------------------------------

    /**
     * Called when the user right-clicks on the floor map canvas.
     *
     * <p>Builds and shows a context menu whose items depend on whether the
     * click landed on an existing map object or on empty canvas space.
     * All actions use the current timeline scrubber position as the
     * effective time.</p>
     *
     * @param event the context menu event from the canvas
     */
    private void onCanvasContextMenu(final MapContextMenuEvent event) {
        showCanvasContextMenu(
                event.getObjectId(),
                event.getMapX(),
                event.getMapY(),
                event.getClientX(),
                event.getClientY(),
                event.getVertexIndex());
    }

    /**
     * Builds and displays the canvas context menu at the given screen position.
     *
     * <p>Menu structure:</p>
     * <ul>
     *   <li><b>Empty canvas:</b>
     *       <ul>
     *         <li>"Add Object Here" — creates a new object at the clicked map position</li>
     *       </ul>
     *   </li>
     *   <li><b>On an object:</b>
     *       <ul>
     *         <li>"Edit Properties" — selects the object and opens the property editor</li>
     *         <li>"Add Time Version" — creates a new effective time entry at the scrubber
     *             position, cloned from the current version</li>
     *         <li>"Duplicate Object" — clones the object with a new key, offset slightly</li>
     *         <li>"Delete Object" — confirms and stages deletion of all time entries</li>
     *       </ul>
     *   </li>
     * </ul>
     *
     * @param objectId the right-clicked object's key, or {@code null} for empty canvas
     * @param mapX     map-space X coordinate of the click
     * @param mapY     map-space Y coordinate of the click
     * @param clientX  screen X coordinate for popup positioning
     * @param clientY  screen Y coordinate for popup positioning
     */
    private void showCanvasContextMenu(final String objectId,
                                       final double mapX,
                                       final double mapY,
                                       final int clientX,
                                       final int clientY,
                                       final int vertexIndex) {
        final String mapName = getMapName();
        if (mapName == null) {
            return;
        }

        final List<Item> menuItems = new ArrayList<>();

        if (vertexIndex >= 0) {
            // ---- Right-clicked an area vertex handle ----
            menuItems.add(new IconMenuItem.Builder()
                    .priority(1)
                    .icon(SvgImage.DELETE)
                    .text("Delete Vertex")
                    .command(() -> floorMapCanvasPresenter.deleteVertex(vertexIndex))
                    .build());
        } else if (objectId == null) {
            // ---- Right-clicked on empty canvas ----
            menuItems.add(new IconMenuItem.Builder()
                    .priority(1)
                    .icon(SvgImage.ADD)
                    .text("Add Object Here")
                    .command(() -> onAddObjectAtPosition(mapX, mapY))
                    .build());
            menuItems.add(new IconMenuItem.Builder()
                    .priority(2)
                    .icon(SvgImage.PEN)
                    .text("Draw Area Here")
                    .command(() -> ensureAreaSupport(
                            floorMapCanvasPresenter::startAreaDrawing))
                    .build());
            menuItems.add(new IconMenuItem.Builder()
                    .priority(3)
                    .icon(SvgImage.DOUBLE_ARROW)
                    .text("Set Scale")
                    .command(floorMapCanvasPresenter::startScaleMeasurement)
                    .build());
        } else if (model.getSelectedFactKeys().size() > 1
                && model.getSelectedFactKeys().contains(objectId)) {
            // ---- Right-clicked within a multi-selection: group actions ----
            final List<String> keys = new ArrayList<>(model.getSelectedFactKeys());
            final int n = keys.size();
            menuItems.add(new IconMenuItem.Builder()
                    .priority(1)
                    .icon(SvgImage.COPY)
                    .text("Duplicate Selected (" + n + ")")
                    .command(() -> onDuplicateObjects(keys))
                    .build());
            menuItems.add(new IconMenuItem.Builder()
                    .priority(2)
                    .icon(SvgImage.DELETE)
                    .text("Delete Selected (" + n + ")")
                    .command(() -> onDeleteObjects(keys))
                    .build());
        } else {
            // ---- Right-clicked on a single object ----

            // Edit Properties
            menuItems.add(new IconMenuItem.Builder()
                    .priority(1)
                    .icon(SvgImage.EDIT)
                    .text("Edit Properties")
                    .command(() -> {
                        final String editMapName = getMapName();
                        if (editMapName == null) {
                            return;
                        }
                        // Select through the one selection path so *every* side
                        // effect happens — notably
                        // FloorMapObjectEditPresenter.setObject, without which the
                        // dialog writes under whatever key it was last given.
                        // This must also come before the fetch: fetchTimeList drops
                        // a response whose key is not the current selection, so
                        // selecting second would discard the very list we asked for.
                        applySelection(Collections.singletonList(objectId));

                        // Then FETCH before opening, for the same reason as "Add
                        // Time Version" below. The dialog's same-time overwrite
                        // guard reads the merged time list, and until this fetch
                        // lands that list still holds the *previously* selected
                        // fact's shards. Opening immediately let a move onto an
                        // occupied time pass the guard, which deletes the original
                        // shard and upserts over the version already at that time,
                        // collapsing two versions into one with nothing reported.
                        fetchTimeList(editMapName, objectId, () -> {
                            // Resolved inside the callback, not before it: picking
                            // the shard from the pre-fetch list could hand the
                            // dialog one belonging to the previous selection.
                            //
                            // Edit the shard the canvas is showing (active at the
                            // scrubber), not the first key match (which could be a
                            // historical shard under a pending time-version).
                            final TemporalEntry active =
                                    model.activeMergedEntryForKey(objectId);
                            if (active != null) {
                                onEditTimeInTimeList(active);
                            }
                        });
                    })
                    .build());

            // Add Time Version at scrubber position
            menuItems.add(new IconMenuItem.Builder()
                    .priority(2)
                    .icon(SvgImage.HISTORY)
                    .text("Add Time Version")
                    .command(() -> {
                        final String addMapName = getMapName();
                        if (addMapName == null) {
                            return;
                        }
                        // Select through the one selection path (see Edit
                        // Properties above), then FETCH its time list before
                        // adding — otherwise the model still holds the previous
                        // selection's shards, so the same-time overwrite guard is
                        // bypassed and the new version clones blank data.
                        applySelection(Collections.singletonList(objectId));
                        fetchTimeList(addMapName, objectId, this::onAddTimeInTimeList);
                    })
                    .build());

            // Duplicate Object
            menuItems.add(new IconMenuItem.Builder()
                    .priority(3)
                    .icon(SvgImage.COPY)
                    .text("Duplicate Object")
                    .command(() -> onDuplicateObject(objectId))
                    .build());

            // Delete Object
            menuItems.add(new IconMenuItem.Builder()
                    .priority(4)
                    .icon(SvgImage.DELETE)
                    .text("Delete Object")
                    .command(() -> onDeleteFactFromFactList(objectId))
                    .build());

            // Draw Area Here — a map is usually covered edge-to-edge by its
            // background image, so area drawing must also be reachable from a
            // right-click that lands on the background (or any object).
            menuItems.add(new IconMenuItem.Builder()
                    .priority(5)
                    .icon(SvgImage.PEN)
                    .text("Draw Area Here")
                    .command(() -> ensureAreaSupport(
                            floorMapCanvasPresenter::startAreaDrawing))
                    .build());

            // Set Scale — offered here for the same reason area drawing is: a
            // map is usually covered edge-to-edge by its background image, so an
            // action only on the empty-canvas branch is one most users can never
            // right-click their way to.
            menuItems.add(new IconMenuItem.Builder()
                    .priority(6)
                    .icon(SvgImage.DOUBLE_ARROW)
                    .text("Set Scale")
                    .command(floorMapCanvasPresenter::startScaleMeasurement)
                    .build());
        }

        final PopupPosition popupPosition = new PopupPosition(clientX, clientY);
        ShowMenuEvent
                .builder()
                .items(menuItems)
                .popupPosition(popupPosition)
                .fire(this);
    }

    /**
     * Creates a new object at the given map-space position.
     *
     * <p>Called from the canvas context menu's "Add Object Here" action.
     * Uses the current timeline scrubber position as the effective time,
     * and opens the properties editor dialog so the user can set the type,
     * name, and image before confirming.</p>
     *
     * @param mapX the X coordinate in map space
     * @param mapY the Y coordinate in map space
     */
    private void onAddObjectAtPosition(final double mapX, final double mapY) {
        final String mapName = getMapName();
        if (mapName == null) {
            return;
        }

        // Generate a unique key for the new object
        final String newKey = generateObjectKey("new");

        try {
            final ValueFormat format = getEntity().getValueFormat();
            final ValueAccessor accessor = ValueAccessorFactory.forFormat(format);
            final ParsedValue newValue = accessor.createEmpty("entry");
            accessor.setString(newValue, pathForRole(Role.TYPE), "");
            accessor.setString(newValue, pathForRole(Role.LABEL), newKey);
            accessor.setArray(newValue, pathForRole(Role.POSITION),
                    new double[]{mapX, mapY});
            final FloorMapTransformationMatrix identity = FloorMapTransformationMatrix.identity();
            accessor.setArray(newValue, pathForRole(Role.WORLD_TO_MAP),
                    new double[]{identity.getA(), identity.getB(), identity.getC(),
                            identity.getD(), identity.getE(), identity.getF()});
            final String valueStr = accessor.serialize(newValue);

            final TemporalEntry entry = new TemporalEntry(
                    mapName, newKey, model.getSelectedTime(), valueStr);

            // Open the properties editor so the user can customise before committing
            floorMapObjectEditPresenter.setMapName(mapName);
            floorMapObjectEditPresenter.setObject(newKey);
            floorMapObjectEditPresenter.setFloorMapDoc(sessionEntity());

            floorMapObjectEditPresenter.show(
                    "Add Object",
                    entry,
                    saved -> {
                        model.stageCreation(saved);
                        setDirty(true);
                        model.setSelectedFactKey(saved.getKey());

                        // Select the new object in the Fact List and canvas
                        floorMapCanvasPresenter.setSelectedObjectId(model.getSelectedFactKey());
                        floorMapFactListPresenter.setSelected(model.getSelectedFactKey());

                        // Populate the Time List optimistically from pending
                        // changes (the server doesn't know about this entry yet)
                        model.setServerEntriesForSelectedFact(new ArrayList<>());
                        refreshTimeListAtTime(model.getSelectedTime());

                        loadAtTime(model.getSelectedTime());
                    });
        } catch (final IllegalStateException ex) {
            AlertEvent.fireError(
                    this,
                    "Cannot add object: " + ex.getMessage(),
                    null);
        }
    }

    /**
     * Runs {@code onReady} once this document supports areas, upgrading the
     * document if needed.
     *
     * <p>Documents created before the area feature lack the
     * {@code GEOMETRY}/{@code FILL}/{@code OPACITY} schema mappings (and the
     * {@code "area"} type style that paints areas just above the background).
     * Rather than failing in {@link #pathForRole}, this offers to add the
     * missing defaults. The merge is role-based, so customised paths for those
     * roles are left untouched; the upgrade is staged via
     * {@link FloorMapDocSession#stageAreaUpgrade} and persisted by
     * {@link #onWrite} on the next document save.</p>
     *
     * @param onReady the action to run once area support is available
     */
    private void ensureAreaSupport(final Runnable onReady) {
        if (FloorMapDocSession.hasAreaSupport(valueSchema())
                && FloorMapDocSession.hasAreaStyle(typeStyles())) {
            onReady.run();
            return;
        }

        ConfirmEvent.fire(this,
                "This floor map is not yet configured for areas. Add the default "
                        + "Geometry, Fill and Opacity mappings to the Value Schema and "
                        + "an 'area' entry to the Type Styles? The document will be "
                        + "marked as modified.",
                ok -> {
                    if (!ok) {
                        return;
                    }
                    docSession.stageAreaUpgrade(valueSchema(),
                            getEntity().getValueFormat(), typeStyles());
                    // Apply the styles to the live canvas so the first drawn
                    // area z-orders correctly before the document is saved, and
                    // re-hand the upgraded document to the object-edit dialog
                    // so it resolves the new roles immediately.
                    floorMapCanvasPresenter.setTypeStyles(typeStyles());
                    floorMapObjectEditPresenter.setFloorMapDoc(sessionEntity());
                    setDirty(true);
                    if (areaSupportEnabledListener != null) {
                        areaSupportEnabledListener.run();
                    }
                    onReady.run();
                });
    }

    /**
     * Handles a finished Set Scale measurement: asks what the measured line
     * really spans, and calibrates the document from the answer.
     *
     * <p>The calibration is staged in the doc session rather than written
     * straight to the entity — this tab does not normally write the document at
     * all — and pushed to the canvas so the grid, scale bar and gesture readouts
     * relabel immediately. No other tab reads or writes the field, so
     * {@code copy()} carries it safely through a save from anywhere.</p>
     *
     * @param mapLength the measured length in map units
     */
    private void onScaleMeasured(final double mapLength) {
        floorMapSetScalePresenter.show(
                mapLength,
                sessionEntity().getMeasurementUnits(),
                units -> {
                    docSession.stageMeasurementUnits(units);
                    floorMapCanvasPresenter.setMeasurementUnits(units);
                    setDirty(true);
                });
    }

    /**
     * Creates a new area fact from a polygon the user has just drawn on the
     * canvas (see {@link FloorMapCanvasPresenter.AreaHandler}).
     *
     * <p>The vertices are stored in the fact's <em>local</em> frame, centred on
     * their centroid, with {@code WORLD_TO_MAP = translate(centroid)} — so the
     * existing move/scale/rotate handles pivot about the polygon's middle and
     * areas inherit duplicate/time-versioning like every other fact. The entry
     * is created at effective time {@code 0} (areas are usually timeless floor
     * features and should be visible at all past scrubber positions); a later
     * reshape at a scrubber time adds a shard as normal.</p>
     *
     * @param mapVertices the polygon vertices in map space, in click order
     */
    private void onAreaDrawn(final List<double[]> mapVertices) {
        final String mapName = getMapName();
        if (mapName == null || mapVertices == null || mapVertices.size() < 3) {
            return;
        }

        final String newKey = generateObjectKey(FloorMapJsonKeys.AREA);

        try {
            // Effective from epoch 0: the area exists across all past time.
            final TemporalEntry entry = FloorMapEditorModel.buildAreaEntry(
                    mapName, newKey, mapVertices, 0L, valueSchema(),
                    ValueAccessorFactory.forFormat(getEntity().getValueFormat()));

            // Open the properties editor so the user can name/colour the area
            // before committing.
            floorMapObjectEditPresenter.setMapName(mapName);
            floorMapObjectEditPresenter.setObject(newKey);
            floorMapObjectEditPresenter.setFloorMapDoc(sessionEntity());

            floorMapObjectEditPresenter.show(
                    "Add Area",
                    entry,
                    saved -> {
                        model.stageCreation(saved);
                        setDirty(true);
                        model.setSelectedFactKey(saved.getKey());

                        floorMapCanvasPresenter.setSelectedObjectId(model.getSelectedFactKey());
                        floorMapFactListPresenter.setSelected(model.getSelectedFactKey());

                        // Populate the Time List optimistically from pending
                        // changes (the server doesn't know about this entry yet)
                        model.setServerEntriesForSelectedFact(new ArrayList<>());
                        refreshTimeListAtTime(model.getSelectedTime());

                        loadAtTime(model.getSelectedTime());
                    });
        } catch (final RuntimeException ex) {
            AlertEvent.fireError(
                    this,
                    "Cannot add area: " + ex.getMessage(),
                    null);
        }
    }

    /**
     * Duplicates an existing object with a new key, offset from the original so
     * the copy is visible rather than sitting directly on top of it.
     *
     * <p>The new object is a clone of the original's current state at the
     * timeline scrubber position (which is also its effective time), shifted by
     * five minor grid divisions in both axes — a visually consistent nudge at
     * any zoom. The offset is applied to the placement matrix (as a drag-move
     * would be), so it moves image facts and imageless facts alike.</p>
     *
     * @param originalKey the key of the object to duplicate
     */
    private void onDuplicateObject(final String originalKey) {
        if (stageDuplicate(originalKey) != null) {
            loadAtTime(model.getSelectedTime());
        }
    }

    /**
     * Stages one duplicate without reloading, returning the new key, or {@code null} if the
     * original could not be duplicated.
     *
     * <p>Separate from {@link #onDuplicateObject} so a group duplicate can stage every copy and
     * reload once at the end. Reloading per object re-queried the facts store once per selected
     * item, so duplicating a selection of two hundred issued two hundred searches to produce one
     * visible result.</p>
     */
    private String stageDuplicate(final String originalKey) {
        final String mapName = getMapName();
        if (mapName == null) {
            return null;
        }

        // Duplicate the shard the canvas is showing (active at the scrubber),
        // not merely the first key match — which could be a historical shard
        // under a pending time-version.
        final TemporalEntry sourceEntry = model.activeMergedEntryForKey(originalKey);
        if (sourceEntry == null) {
            return null;
        }

        try {
            final String newKey = generateObjectKey(originalKey + "-copy");
            // Offset by five minor grid divisions in both axes so the duplicate
            // doesn't sit on top of the original, at a visually consistent
            // distance regardless of zoom.
            final double offset = floorMapCanvasPresenter.minorGridDivisionsToMapUnits(5.0);
            final TemporalEntry newEntry = FloorMapEditorModel.buildDuplicateEntry(
                    sourceEntry, mapName, newKey, model.getSelectedTime(),
                    offset, offset,
                    valueSchema(),
                    ValueAccessorFactory.forFormat(getEntity().getValueFormat()));

            model.stageCreation(newEntry);
            setDirty(true);
            model.setSelectedFactKey(newKey);
            return newKey;
        } catch (final Exception ex) {
            AlertEvent.fireError(
                    this,
                    "Cannot duplicate object: " + ex.getMessage(),
                    null);
            return null;
        }
    }

    /**
     * Duplicates every fact in {@code keys} (group duplicate). Each copy is
     * offset by the same grid nudge, so the group keeps its formation.
     *
     * <p>Every copy is staged first and the facts are reloaded once at the end, rather than once
     * per object. The new copies are then left selected, so the group can be dragged straight
     * away.</p>
     *
     * @param keys the fact keys to duplicate
     */
    private void onDuplicateObjects(final java.util.Collection<String> keys) {
        final List<String> newKeys = new ArrayList<>();
        for (final String key : keys) {
            final String newKey = stageDuplicate(key);
            if (newKey != null) {
                newKeys.add(newKey);
            }
        }
        if (!newKeys.isEmpty()) {
            applySelection(newKeys);
            loadAtTime(model.getSelectedTime());
        }
    }

    /**
     * Deletes every fact in {@code keys} (group delete) after a single
     * confirmation, then clears the selection and refreshes.
     *
     * @param keys the fact keys to delete
     */
    private void onDeleteObjects(final java.util.Collection<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        ConfirmEvent.fire(this,
                "Delete all entries for the " + keys.size()
                + " selected objects? This cannot be undone.",
                ok -> {
                    if (ok) {
                        final String mapName = getMapName();
                        if (mapName == null) {
                            return;
                        }
                        // One fetch covering every selected key, rather than one per key
                        // fired concurrently - deleting 200 desks used to mean 200 parallel
                        // requests.
                        deleteAllShardsForKeys(mapName, new ArrayList<>(keys));
                    }
                });
    }

    // -----------------------------------------------------------------------
    // Key generation
    // -----------------------------------------------------------------------

    /**
     * Generates a unique object key with the given prefix, guaranteed not to
     * clash with any key currently known to the editor.
     *
     * <p>The returned key has the form {@code prefix-NNNNN} where {@code NNNNN}
     * is a random integer. If the generated key already exists, a new random
     * suffix is tried until a unique key is found (up to a safety limit of
     * 1000 attempts).</p>
     *
     * <p>The prefix must not start with
     * {@link FloorMapJsonKeys#SVG_GROUP_PREFIX} as that would make the object
     * unselectable on the canvas.</p>
     *
     * @param prefix a human-readable prefix (e.g. {@code "new"}, {@code "gate-1-copy"})
     * @return a key string suitable for use as a temporal-store fact key
     */
    private String generateObjectKey(final String prefix) {
        return model.generateObjectKey(prefix);
    }

    // -----------------------------------------------------------------------
    // "Show all" toggle
    // -----------------------------------------------------------------------

    /**
     * Called when the Fact List's "Show all" button is toggled.
     *
     * @param showAll {@code true} to ignore the time filter
     */
    public void onShowAllFactsToggled(final boolean showAll) {
        model.setShowAllFacts(showAll);
        loadAtTime(model.getSelectedTime());
    }

    // -----------------------------------------------------------------------
    // Optimistic refresh helpers
    // -----------------------------------------------------------------------

    /**
     * Refreshes the Time List from the model's server entries for the selected fact,
     * overlaid with pending changes, then selects the entry active at
     * the model's selected time (i.e. the most recent entry ≤ the current
     * timeline position).
     *
     * <p>Called on timeline scrubber moves so the highlighted row tracks the
     * time position rather than always jumping to the newest entry.</p>
     *
     * @param timeMs the timeline position to select against
     */
    private void refreshTimeListAtTime(final long timeMs) {
        final List<TemporalEntry> merged = model.buildMergedTimeList();
        floorMapTimeListPresenter.setData(merged);
        floorMapTimeListPresenter.selectAtTime(timeMs);
    }

    /** Refreshes the canvas using the latest Fact List data at the current time. */
    private void refreshCanvas() {
        loadAtTime(model.getSelectedTime());
    }

    // -----------------------------------------------------------------------
    // Error handling
    // -----------------------------------------------------------------------

    /**
     * Handles a server-side flush failure.
     *
     * <p>Shows a top-level error and <strong>keeps the staged changes</strong> so the user can
     * retry. Nothing is reloaded: the server reported the changes were not applied, so reloading
     * would overwrite the user's in-progress edits with server state and lose their work.</p>
     *
     * @param result the failed {@link ApplyChangesResult}
     */
    private void onFlushError(final ApplyChangesResult result) {
        // The server reported the changes were NOT applied, so keep them staged
        // for a retry rather than clearing the user's work and reloading over
        // it (replay is idempotent — upserts + deletions).
        final String message = result.getErrorMessage() != null
                ? result.getErrorMessage()
                : "Unknown error";
        AlertEvent.fireError(this,
                "Error saving floor map editor changes — your changes have been "
                + "kept, please try saving again: " + message, null);
    }

    /**
     * Reloads all panels by re-reading from the server using the current time.
     */
    private void reloadAllPanels() {
        final String mapName = getMapName();
        if (mapName != null) {
            loadAtTime(model.getSelectedTime());
            if (model.getSelectedFactKey() != null) {
                fetchTimeList(mapName, model.getSelectedFactKey());
            }
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Loads the Time List for the currently selected fact, then selects the entry active at the
     * current timeline position — the most recent entry at or before the scrubber time, not
     * necessarily the newest — and scrolls to it.
     */
    private void loadTimeListForSelectedFact() {
        final String mapName = getMapName();
        if (mapName == null || model.getSelectedFactKey() == null) {
            floorMapTimeListPresenter.setData(new ArrayList<>());
            return;
        }
        fetchTimeList(mapName, model.getSelectedFactKey());
    }

    /**
     * Returns the temporal store map name from the document, or {@code null}
     * if the document has no store reference configured.
     *
     * @return the map name, or {@code null}
     */
    private String getMapName() {
        final FloorMapDoc doc = getEntity();
        if (doc == null
                || doc.getFactsStoreRef() == null
                || doc.getFactsStoreRef().getName() == null
                || doc.getFactsStoreRef().getName().isEmpty()) {
            return null;
        }
        return doc.getFactsStoreRef().getName();
    }

    /**
     * Returns the value path for the given {@link Role} from the session's value schema.
     *
     * <p>There is no fallback to a default schema: an unmapped role throws. The path is
     * format-neutral rather than JSON-specific - the same path drives the XML accessor when the
     * document's {@code ValueFormat} is XML.</p>
     *
     * @param role the field role to look up
     * @return the value path for the role; never {@code null}
     * @throws IllegalStateException if the schema does not contain the requested role
     */
    private String pathForRole(final Role role) {
        return FloorMapFieldMapping.requirePath(valueSchema(), role);
    }

    // -----------------------------------------------------------------------
    // View interface
    // -----------------------------------------------------------------------

    /**
     * View interface for the Editor tab.
     *
     * <p>Child content is routed through GWTP's standard
     * {@link com.gwtplatform.mvp.client.View#setInSlot} mechanism, overridden in
     * {@link stroom.floormap.client.view.FloorMapEditorViewImpl}.</p>
     */
    public interface FloorMapEditorView extends View {

        /**
         * Shows or hides the right-hand dock, preserving its dragged width.
         *
         * @param visible {@code true} to show the dock, {@code false} to hide it
         */
        void setDockVisible(boolean visible);
    }
}
