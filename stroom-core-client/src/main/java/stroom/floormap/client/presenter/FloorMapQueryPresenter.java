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

import stroom.docref.DocRef;
import stroom.document.client.event.ChangeEvent;
import stroom.document.client.event.HasChangeHandlers;
import stroom.entity.client.presenter.HasClose;
import stroom.entity.client.presenter.HasToolbar;
import stroom.floormap.client.event.FloorMapDataEvent;
import stroom.floormap.client.model.FloorMapLocationResolver;
import stroom.floormap.client.model.FloorMapObject;
import stroom.floormap.client.presenter.FloorMapQueryPresenter.FloorMapQueryView;
import stroom.floormap.shared.FloorMapDoc;
import stroom.floormap.shared.FloorMapEventColumns;
import stroom.floormap.shared.FloorMapEventRole;
import stroom.floormap.shared.FloorMapJsonKeys;
import stroom.query.api.Column;
import stroom.query.api.Row;
import stroom.query.api.TableResult;
import stroom.query.api.TimeRange;
import stroom.query.client.presenter.QueryEditPresenter;
import stroom.query.client.presenter.QueryResultTablePresenter;
import stroom.query.shared.QueryTablePreferences;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.Console;

import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.inject.Inject;

/// Presenter for the Floor Map query tab.
///
/// Embeds a [QueryEditPresenter] for authoring and executing StroomQL
/// queries, parses the resulting [TableResult] rows into
/// [FloorMapObject] lists, and fires [FloorMapDataEvent] so the
/// canvas can display the matched entities.  Also provides column-mapping
/// dropdowns that let the user choose which result columns contain the entity
/// ID, both location forms and the entity type — one dropdown per [FloorMapEventRole],
/// driven by the mapping on the document rather than by settings and a hardcoded column name. The
/// type dropdown replaced an auto-detect on a column literally named "type"; the
/// `@`-heuristic person fallback survives for data that carries no type at all.
public class FloorMapQueryPresenter
        extends MyPresenterWidget<FloorMapQueryView>
        implements HasToolbar, HasClose, HasChangeHandlers {

    private final QueryEditPresenter queryEditPresenter;
    /// Which result column carries each event role. Never `null` once `read` has run.
    private FloorMapEventColumns currentEventColumns = FloorMapEventColumns.defaults();
    /// UUID of the document being queried, stamped onto [FloorMapDataEvent].
    private String docUuid;
    /// `true` while this tab's query is running — see [#onBind()].
    private boolean searching;

    @Inject
    public FloorMapQueryPresenter(final EventBus eventBus,
                                  final FloorMapQueryView view,
                                  final QueryEditPresenter queryEditPresenter) {
        super(eventBus, view);
        this.queryEditPresenter = queryEditPresenter;
        view.setQueryEditView(queryEditPresenter.getView());
    }

    @Override
    protected void onBind() {
        super.onBind();

        // A dropdown change has to mark the document dirty, and nothing did it: the tab wires dirty
        // to addChangeHandler, which tracks only the query editor. So the mapping was unsaveable on
        // its own - the save icon stayed disabled and the edit was lost on the next tab switch,
        // persisting only if an unrelated query-text edit happened to enable saving. Firing
        // ChangeEvent from here is what the tab is already listening for.
        getView().setColumnChangeHandler(() -> {
            currentEventColumns = getView().getEventColumns();
            ChangeEvent.fire(this);
        });

        // Listen to column updates inside the table so we can update the dropdown lists dynamically.
        registerHandler(queryEditPresenter.addChangeHandler(this::updateColumnSelections));

        // Listen to table data updates and fire FloorMapDataEvent.
        //
        // TableUpdateEvent is fired on the shared event bus by every query result
        // table in the application, so the source guard is what keeps an unrelated
        // table's update (a Dashboard, another Query document) from republishing
        // this tab's last result set as the map overlay.
        final QueryResultTablePresenter tablePresenter =
                queryEditPresenter.getQueryResultPresenter().getTablePresenter();
        registerHandler(tablePresenter.addUpdateHandler(e -> {
            if (e.getSource() != tablePresenter) {
                return;
            }
            // The dropdowns track whatever columns the table currently has, so
            // they follow every update, partial result set or not.
            updateColumnSelections();

            // The overlay does not — see the search-state listener below.
            if (!searching) {
                publishMapObjects();
            }
        }));

        // Queries here run incrementally: the result table is updated on every
        // poll of a still-filling store, and at high row counts those partial
        // result sets differ from one poll to the next. Publishing each of them
        // walked the map's entities across the floor for as long as the search
        // ran — visibly so with the timeline paused, which places entities
        // instantly. The searching-to-idle transition is the only point at which
        // the rows are the answer to the query rather than a snapshot of
        // progress, so that is when the overlay is published.
        registerHandler(queryEditPresenter.addSearchStateListener(searching -> {
            // Only the running-to-idle transition, so that the reset at the start
            // of the next run — which also reports "not searching" — does not
            // republish the result set the previous run left behind.
            final boolean finished = this.searching && !searching;
            this.searching = searching;
            if (finished) {
                updateColumnSelections();
                publishMapObjects();
            }
        }));

        // This tab does NOT follow the timeline. The playback events query is
        // owned by FloorMapMapPresenter, because this presenter is created
        // lazily (only when its tab is first opened) and the Map tab's animated
        // entity overlay must not depend on that. Running it here as well would
        // fire a second, identical query per playback tick.
    }

    /// Stops this tab's query when the document is closed.
    ///
    /// Without this the search outlives the document: the result store is left
    /// on the server, the client keeps polling it, and each response still fires a
    /// [FloorMapDataEvent] stamped with this document's UUID — which a
    /// *reopened* copy of the same document accepts as live entity data.
    /// Reachable only because this presenter declares [HasClose];
    /// `AbstractTabProvider` forwards the close hook to nothing else.
    @Override
    public void onClose() {
        queryEditPresenter.onClose();
    }

    /// Parses the current result table into map objects and publishes them as the
    /// canvas entity overlay.
    ///
    /// Only ever called for a finished result set (see [#onBind()]).
    private void publishMapObjects() {
        final TableResult tableResult = queryEditPresenter.getQueryResultPresenter()
                .getTablePresenter()
                .getCurrentTableResult();

        if (tableResult != null) {
            final List<FloorMapObject> objects = parseRows(
                    tableResult, currentEventColumns, Console::error);
            FloorMapDataEvent.fire(FloorMapQueryPresenter.this, docUuid, objects);
        }
    }

    /// Refreshes the per-role column dropdowns from the latest table columns, preserving the user's
    /// current selections where possible.
    private void updateColumnSelections() {
        final List<Column> columns = queryEditPresenter.getQueryResultPresenter()
                .getTablePresenter()
                .getCurrentColumns();

        if (columns != null && !columns.isEmpty()) {
            final List<String> colNames = columns
                    .stream()
                    .map(Column::getName)
                    .toList();

            // Save what is selected now, before repopulating drops anything the new result does
            // not offer.
            final FloorMapEventColumns onScreen = getView().getEventColumns();
            getView().setAvailableColumns(colNames);

            FloorMapEventColumns next = currentEventColumns;
            for (final FloorMapEventRole role : FloorMapEventRole.values()) {
                final String selected = onScreen.getColumn(role);
                final String stored = currentEventColumns.getColumn(role);
                // Prefer what is on screen, fall back to what is stored, and leave the role
                // unmapped rather than silently pointing it at a column that no longer exists.
                if (selected != null && colNames.contains(selected)) {
                    next = next.with(role, selected);
                } else if (stored != null && colNames.contains(stored)) {
                    next = next.with(role, stored);
                } else {
                    next = next.with(role, null);
                }
            }
            currentEventColumns = next;
            getView().setEventColumns(currentEventColumns);
        }
    }

    /// Reduces a time window to the most recent row per entity.
    ///
    /// The Map tab queries a trailing window rather than an instant, because an instant is
    /// unsatisfiable against a store that applies the time range literally — see
    /// `FloorMapMapPresenter.runQueryAtSelectedTime`. A window can return several events for
    /// one entity, and the canvas wants exactly one position each, so the extras are dropped here.
    /// A store that already deduplicates server-side returns one row per key anyway, and this then
    /// costs a pass over the rows and changes nothing.
    ///
    /// **How "most recent" is decided, and where that is imperfect.** The time
    /// column arrives already rendered as text, formatted to the viewing user's date-time
    /// preference, so there is no timestamp to compare — only its presentation. Two forms are
    /// handled properly: epoch milliseconds, compared numerically, and the ISO-8601 form Stroom
    /// emits when no pattern preference is set, which sorts correctly as text. A user pattern that
    /// is *not* lexicographically ordered — `dd/MM/yyyy` being the obvious one — makes
    /// the text comparison pick the wrong row of the window. The error is bounded by the window
    /// (an entity can appear at a position up to that stale, not at a wrong one) and is strictly
    /// better than the zero rows this replaced, but the real fix is for the query to carry a raw
    /// numeric time alongside the formatted one.
    ///
    /// With no usable time column the last row for each entity wins, which is at least
    /// deterministic for a given result.
    ///
    /// @param columns      the result columns; may be `null`
    /// @param rows         the rows to reduce; may be `null`
    /// @param entityColumn the column naming the entity; `null` leaves rows untouched
    /// @param timeColumn   the column holding the effective time; absent or unmatched falls back to
    ///         last-row-wins
    /// @return one row per entity, in first-appearance order; never `null`
    static List<Row> latestPerEntity(final List<Column> columns,
                                     final List<Row> rows,
                                     final String entityColumn,
                                     final String timeColumn) {
        if (rows == null || columns == null || entityColumn == null) {
            return rows == null ? new ArrayList<>() : new ArrayList<>(rows);
        }

        int entityColIndex = -1;
        int timeColIndex = -1;
        for (int i = 0; i < columns.size(); i++) {
            final String name = columns.get(i).getName();
            if (entityColumn.equals(name)) {
                entityColIndex = i;
            } else if (timeColumn != null && timeColumn.equals(name)) {
                timeColIndex = i;
            }
        }
        if (entityColIndex == -1) {
            // Nothing to group by; parseRows will reject these rows anyway.
            return new ArrayList<>(rows);
        }

        // LinkedHashMap so the surviving rows keep the order the entities first appeared, which
        // keeps the canvas's draw order stable between frames rather than reshuffling per poll.
        final Map<String, Row> latest = new LinkedHashMap<>();
        for (final Row row : rows) {
            final String entityId = valueAt(row, entityColIndex);
            if (entityId == null) {
                continue;
            }
            final Row incumbent = latest.get(entityId);
            if (incumbent == null || isAfter(row, incumbent, timeColIndex)) {
                latest.put(entityId, row);
            }
        }
        return new ArrayList<>(latest.values());
    }

    /// Whether `candidate` is the later of the two rows at `timeColIndex`.
    ///
    /// With no time column every row is treated as later than the one before it, which makes
    /// the last row for an entity win.
    private static boolean isAfter(final Row candidate, final Row incumbent, final int timeColIndex) {
        if (timeColIndex == -1) {
            return true;
        }
        final String candidateTime = valueAt(candidate, timeColIndex);
        final String incumbentTime = valueAt(incumbent, timeColIndex);
        if (candidateTime == null) {
            return false;
        }
        if (incumbentTime == null) {
            return true;
        }
        final Long candidateMs = asEpochMs(candidateTime);
        final Long incumbentMs = asEpochMs(incumbentTime);
        if (candidateMs != null && incumbentMs != null) {
            return candidateMs >= incumbentMs;
        }
        return candidateTime.compareTo(incumbentTime) >= 0;
    }

    /// The value at `index`, or `null` if the row is short or holds nothing there.
    private static String valueAt(final Row row, final int index) {
        final List<String> values = row == null ? null : row.getValues();
        return values == null || values.size() <= index ? null : values.get(index);
    }

    /// Parses epoch milliseconds, or `null` when the text is not a bare number.
    private static Long asEpochMs(final String value) {
        try {
            return Long.valueOf(value.trim());
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    /// Parses all rows of the supplied [TableResult] into
    /// [FloorMapObject] instances using the given entity and location
    /// column mappings.
    ///
    /// Static and package-visible because two callers run the same events
    /// query: this editor tab, and [FloorMapMapPresenter], which owns the
    /// timeline-driven playback query feeding the animated entity overlay.
    ///
    /// An entity is positioned outright when its [FloorMapEventRole#LOCATION] column holds
    /// coordinates; one carrying a [FloorMapEventRole#LOCATION_REF] instead gets a
    /// [FloorMapObject#getLocationRef()] and must be run through
    /// [FloorMapLocationResolver#resolve] against the current facts before it is drawn.
    ///
    /// @param tableResult  the query result to parse
    /// @param eventColumns which result column carries each role
    /// @param warnings     receives a message for contradictory or malformed location data, at most
    ///         one per result; may be `null`
    /// @return a list of map objects; never `null`
    static List<FloorMapObject> parseRows(final TableResult tableResult,
                                          final FloorMapEventColumns eventColumns,
                                          final Consumer<String> warnings) {
        if (tableResult == null) {
            return new ArrayList<>();
        }
        return parseRows(tableResult.getColumns(), tableResult.getRows(), eventColumns, warnings);
    }

    /// As [#parseRows(TableResult, FloorMapEventColumns, Consumer)], over a caller-supplied
    /// row list.
    ///
    /// Split out so a caller can filter the rows first — [#latestPerEntity] reduces a
    /// time window to one row per entity — without rebuilding a [TableResult] whose
    /// `totalResults` would then disagree with its contents.
    ///
    /// @param columns      the result columns; may be `null`
    /// @param rows         the rows to parse; may be `null`
    /// @param eventColumns which result column carries each role
    /// @param warnings     receives a message for contradictory or malformed location data, at most
    ///         one per result; may be `null`
    /// @return a list of map objects; never `null`
    static List<FloorMapObject> parseRows(final List<Column> columns,
                                          final List<Row> rows,
                                          final FloorMapEventColumns eventColumns,
                                          final Consumer<String> warnings) {
        final List<FloorMapObject> list = new ArrayList<>();

        if (rows == null || columns == null || eventColumns == null) {
            return list;
        }

        final int entityIdx = columnIndex(columns, eventColumns.getColumn(FloorMapEventRole.ENTITY_ID));
        final int locationIdx = columnIndex(columns, eventColumns.getColumn(FloorMapEventRole.LOCATION));
        final int refIdx = columnIndex(columns, eventColumns.getColumn(FloorMapEventRole.LOCATION_REF));
        final int typeIdx = columnIndex(columns, eventColumns.getColumn(FloorMapEventRole.TYPE));

        // No identity, nothing to draw. Either location role on its own is enough - a store whose
        // events only ever carry fact keys has no Location column at all, and one whose events are
        // all pre-resolved has no Location Ref.
        if (entityIdx == -1 || (locationIdx == -1 && refIdx == -1)) {
            return list;
        }

        boolean bothWarned = false;
        for (final Row row : rows) {
            final List<String> values = row.getValues();
            if (values == null || values.size() <= entityIdx) {
                continue;
            }
            final String entityId = values.get(entityIdx);
            if (entityId == null) {
                continue;
            }

            String type = "object";
            if (typeIdx != -1 && values.size() > typeIdx && values.get(typeIdx) != null) {
                type = values.get(typeIdx);
            } else if (entityId.contains("@")) {
                // Kept as a last resort: data carrying no type at all is a real case, and dropping
                // this would change behaviour for anyone relying on it.
                type = FloorMapJsonKeys.PERSON;
            }

            final String locationStr = cell(values, locationIdx);
            final String refStr = FloorMapLocationResolver.parseReference(cell(values, refIdx));

            // Location wins, because it needs no lookup. Both set is contradictory data rather than
            // a preference, so it is warned about once per result - warning per row would be three
            // messages a second during playback.
            if (locationStr != null && refStr != null && !bothWarned && warnings != null) {
                bothWarned = true;
                warnings.accept("Floor map events: '" + entityId + "' carries both a "
                                + FloorMapEventRole.LOCATION.getDisplayName() + " and a "
                                + FloorMapEventRole.LOCATION_REF.getDisplayName()
                                + ". Only one can be true, and the coordinates are being used"
                                + " because they need no lookup. Fix the data, or unmap one of the"
                                + " two roles on the Events Query tab.");
            }

            final double[] coords = FloorMapLocationResolver.parseCoordinates(locationStr);
            if (coords != null) {
                list.add(new FloorMapObject(entityId, type, coords[0], coords[1]));
            } else if (refStr != null) {
                final FloorMapObject object = new FloorMapObject(entityId, type, 0, 0);
                object.setLocationRef(refStr);
                list.add(object);
            } else if (locationStr != null && warnings != null && !bothWarned) {
                // A Location that is not two numbers is malformed, full stop. Under the old
                // single-column scheme it silently became a reference to a fact named after a
                // coordinate string, and the map reported a missing desk.
                bothWarned = true;
                warnings.accept("Floor map events: '" + entityId + "' has "
                                + FloorMapEventRole.LOCATION.getDisplayName() + " '" + locationStr
                                + "', which is not two comma-separated numbers. Coordinates are"
                                + "'x, y'; a fact key belongs in "
                                + FloorMapEventRole.LOCATION_REF.getDisplayName() + ".");
            }
        }

        return list;
    }

    /// The value at `index`, or `null` when absent, blank, or the column is unmapped.
    private static String cell(final List<String> values, final int index) {
        if (index < 0 || index >= values.size()) {
            return null;
        }
        final String value = values.get(index);
        return value == null || value.trim().isEmpty() ? null : value;
    }

    /// Exact match, as the mapping promises: an alias that matches nothing is a fault, not a hint.
    private static int columnIndex(final List<Column> columns, final String name) {
        if (name == null) {
            return -1;
        }
        for (int i = 0; i < columns.size(); i++) {
            final Column column = columns.get(i);
            if (column != null && name.equals(column.getName())) {
                return i;
            }
        }
        return -1;
    }

    /// Convenience overload that reads all query state from a [FloorMapDoc].
    ///
    /// @param doc the floor map document to read from
    public void read(final FloorMapDoc doc) {
        read(doc.asDocRef(), doc.getEventsQuery(), doc.getEventsQueryTimeRange(),
                doc.getEventsQueryTablePreferences(),
                doc.getEventColumns(), true,
                buildQueryVariables(doc));
    }

    /// Populates the query editor and column-mapping dropdowns from the supplied
    /// parameters.
    ///
    /// @param docRef               the document reference for the query context
    /// @param query                the StroomQL query text
    /// @param timeRange            the time range filter; may be `null`
    /// @param queryTablePreferences table column preferences; may be `null`
    /// @param eventColumns         which result column carries each event role
    /// @param showColumnMappings   `true` to show the column-mapping dropdowns
    public void read(final DocRef docRef,
                     final String query,
                     final TimeRange timeRange,
                     final QueryTablePreferences queryTablePreferences,
                     final FloorMapEventColumns eventColumns,
                     final boolean showColumnMappings,
                     final Map<String, String> queryVariables) {
        this.docUuid = docRef != null ? docRef.getUuid() : null;
        this.currentEventColumns = eventColumns == null
                ? FloorMapEventColumns.defaults()
                : eventColumns;

        getView().setEventColumns(currentEventColumns);
        getView().setColumnMappingsVisible(showColumnMappings);

        // Populate the inner query editor.
        queryEditPresenter.setQueryVariables(queryVariables);
        queryEditPresenter.setQuery(docRef, query, false);
        queryEditPresenter.setTimeRange(timeRange);
        queryEditPresenter.read(queryTablePreferences);

        updateColumnSelections();
    }

    /// Writes the current query editor state and column selections back into a
    /// copy of the supplied document.
    ///
    /// @param doc the document to update
    /// @return a new document copy with the query state applied
    public FloorMapDoc write(final FloorMapDoc doc) {
        this.currentEventColumns = getView().getEventColumns();

        return doc.copy()
                .eventColumns(currentEventColumns)
                .eventsQuery(queryEditPresenter.getQuery())
                .eventsQueryTimeRange(queryEditPresenter.getTimeRange())
                .eventsQueryTablePreferences(queryEditPresenter.write())
                .build();
    }

    /// Registers a handler for changes the user makes on *this tab* — the query text, and the
    /// per-role column dropdowns.
    ///
    /// Two sources, deliberately. The query editor's own `addChangeHandler` uses
    /// `addHandlerToSource`, so it fires only for this editor rather than for every
    /// `ChangeEvent` on the shared bus — which is why the tab uses it for dirty tracking. But
    /// it knows nothing about the dropdowns, so registering only the delegate left the column
    /// mapping **unsaveable on its own**: the save icon stayed disabled and the edit was lost on
    /// the next tab switch, persisting only when an unrelated query-text edit happened to enable
    /// saving.
    ///
    /// @param handler notified on either kind of change
    /// @return a registration that removes both
    public HandlerRegistration addChangeHandler(final ChangeEvent.ChangeHandler handler) {
        // Both sources: the embedded query editor, and this presenter's own ChangeEvent for a
        // dropdown change. Registering only the delegate is what left the mapping unsaveable.
        final HandlerRegistration query = queryEditPresenter.addChangeHandler(handler);
        final HandlerRegistration columns = addHandlerToSource(ChangeEvent.getType(), handler);
        return () -> {
            query.removeHandler();
            columns.removeHandler();
        };
    }

    public String getQuery() {
        return queryEditPresenter.getQuery();
    }

    public TimeRange getQueryTimeRange() {
        return queryEditPresenter.getTimeRange();
    }

    public QueryTablePreferences getQueryTablePreferences() {
        return queryEditPresenter.write();
    }

    public FloorMapEventColumns getEventColumns() {
        return getView().getEventColumns();
    }

    public void setTaskMonitorFactory(final TaskMonitorFactory taskMonitorFactory) {
        queryEditPresenter.setTaskMonitorFactory(taskMonitorFactory);
    }

    @Override
    public List<Widget> getToolbars() {
        return queryEditPresenter.getToolbars();
    }

    /// Builds the query parameter map from a [FloorMapDoc]'s store references.
    /// Parameters `FactStore` and `EventStore` are mapped to the
    /// store names so that `param('FactStore')` and `param('EventStore')`
    /// references in queries resolve correctly.
    ///
    /// @param doc the floor map document; never null
    /// @return a parameter map, possibly empty but never null
    public static Map<String, String> buildQueryVariables(final FloorMapDoc doc) {
        final Map<String, String> vars = new HashMap<>();
        if (doc.getFactsStoreRef() != null && doc.getFactsStoreRef().getName() != null) {
            vars.put("FactStore", doc.getFactsStoreRef().getName());
        }
        if (doc.getEventsStoreRef() != null && doc.getEventsStoreRef().getName() != null) {
            vars.put("EventStore", doc.getEventsStoreRef().getName());
        }
        return vars;
    }

    public interface FloorMapQueryView extends View {
        void setQueryEditView(View view);

        void setAvailableColumns(List<String> columnNames);

        /// Applies a mapping to the per-role dropdowns; an unmapped role selects nothing.
        void setEventColumns(FloorMapEventColumns eventColumns);

        /// Reads the per-role dropdowns back; a dropdown with nothing selected is unmapped.
        FloorMapEventColumns getEventColumns();

        /// Registers a handler notified whenever the user changes any role's dropdown.
        void setColumnChangeHandler(Runnable handler);

        void setColumnMappingsVisible(boolean visible);
    }
}
