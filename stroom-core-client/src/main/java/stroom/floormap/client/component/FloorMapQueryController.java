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

import stroom.docref.DocRef;
import stroom.entity.client.presenter.HasToolbar;
import stroom.floormap.client.model.FloorMapObject;
import stroom.floormap.client.presenter.FloorMapEventQuery;
import stroom.floormap.shared.FloorMapDoc;
import stroom.floormap.shared.FloorMapEventColumns;
import stroom.floormap.shared.FloorMapEventRole;
import stroom.query.api.TableResult;
import stroom.query.api.TimeRange;
import stroom.query.client.component.QueryEditController;
import stroom.query.client.component.QueryResultTableController;
import stroom.query.shared.QueryTablePreferences;
import stroom.util.client.Console;
import stroom.widget.component.client.Controller;

import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.inject.Inject;

/// Presenter for the Floor Map query tab.
///
/// Embeds a [QueryEditController] for authoring and executing StroomQL
/// queries, parses the resulting [TableResult] rows into
/// [FloorMapObject] lists, and gives them to its owner ([#setOnEventData]) so the
/// canvas can display the matched entities.  Also provides column-mapping
/// dropdowns that let the user choose which result columns contain the entity
/// ID, both location forms and the entity type — one dropdown per [FloorMapEventRole],
/// driven by the mapping on the document rather than by settings and a hardcoded column name. The
/// type dropdown replaced an auto-detect on a column literally named "type"; the
/// `@`-heuristic person fallback survives for data that carries no type at all.
public class FloorMapQueryController
        extends Controller
        implements HasToolbar {

    private final FloorMapQuery view;
    private final QueryEditController queryEditPresenter;
    /// Which result column carries each event role. Never `null` once `read` has run.
    private FloorMapEventColumns currentEventColumns = FloorMapEventColumns.defaults();
    /// UUID of the document being queried.
    private String docUuid;
    /// `true` while this tab's query is running — see [#onBind()].
    private boolean searching;

    private Runnable onChange = () -> {
    };
    private Consumer<List<FloorMapObject>> onEventData = objects -> {
    };

    @Inject
    public FloorMapQueryController(final EventBus eventBus,
                                   final FloorMapQuery view,
                                   final QueryEditController queryEditPresenter) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        own(queryEditPresenter);
        this.queryEditPresenter = queryEditPresenter;
        view.setQueryEdit(queryEditPresenter);

        // A dropdown change has to mark the document dirty, as a query edit does (the mapping was
        // once unsaveable on its own, as only the query editor's changes were told)
        view.setOnColumnChange(() -> {
            currentEventColumns = view.getEventColumns();
            onChange.run();
        });

        // A query edit updates the dropdown lists (the table's columns may have changed), and is a
        // change to the document
        queryEditPresenter.setOnChange(() -> {
            updateColumnSelections();
            onChange.run();
        });

        // Listen to this tab's table's data updates and publish the events (it once heard every
        // query result table's in the application, and needed a source guard)
        final QueryResultTableController tablePresenter =
                queryEditPresenter.getQueryResultPresenter().getTablePresenter();
        own(tablePresenter.addUpdateListener(() -> {
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
        own(queryEditPresenter.addSearchStateListener(searching -> {
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

    /// Parses the current result table into map objects and publishes them as the
    /// canvas entity overlay.
    ///
    /// Only ever called for a finished result set (see [#onBind()]).
    private void publishMapObjects() {
        final TableResult tableResult = queryEditPresenter.getQueryResultPresenter()
                .getTablePresenter()
                .getCurrentTableResult();

        if (tableResult != null) {
            final List<FloorMapObject> objects = FloorMapEventQuery.parseRows(
                    tableResult, currentEventColumns, Console::error);
            onEventData.accept(objects);
        }
    }

    /// Refreshes the per-role column dropdowns from the latest table columns, preserving the user's
    /// current selections where possible.
    ///
    /// With no result yet — the tab opened without the query being run — the lists are seeded from
    /// the mapping's own column names instead, and the view is told to say why the list is short.
    /// Without this the lists were empty and no role could be changed until the query was run.
    /// A result holding only special columns counts as no result — see
    /// [FloorMapEventQuery#selectableColumnNames].
    private void updateColumnSelections() {
        final List<String> colNames = FloorMapEventQuery.selectableColumnNames(
                queryEditPresenter.getQueryResultPresenter()
                        .getTablePresenter()
                        .getCurrentColumns());

        if (!colNames.isEmpty()) {
            // Save what is selected now, before repopulating drops anything the new result does
            // not offer.
            final FloorMapEventColumns onScreen = view.getEventColumns();
            view.setAvailableColumns(colNames);

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
            view.setEventColumns(currentEventColumns);
            view.setColumnsHintVisible(false);
        } else {
            // No result to check the mapping against, so offer what it already names and leave it
            // alone. Unmapping roles whose column is "missing" here would wipe every role, because
            // with no result every column is missing.
            view.setAvailableColumns(FloorMapEventQuery.mappedColumnNames(currentEventColumns));
            view.setEventColumns(currentEventColumns);
            view.setColumnsHintVisible(true);
        }
    }

    /// Convenience overload that reads all query state from a [FloorMapDoc].
    ///
    /// @param doc the floor map document to read from
    public void read(final FloorMapDoc doc) {
        read(doc.asDocRef(), doc.getEventsQuery(), doc.getEventsQueryTimeRange(),
                doc.getEventsQueryTablePreferences(),
                doc.getEventColumns(), true,
                FloorMapEventQuery.buildQueryVariables(doc));
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

        view.setEventColumns(currentEventColumns);
        view.setColumnMappingsVisible(showColumnMappings);

        // Populate the inner query editor.
        queryEditPresenter.setQueryVariables(queryVariables);
        queryEditPresenter.setQuery(docRef, query);
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
        this.currentEventColumns = view.getEventColumns();

        return doc.copy()
                .eventColumns(currentEventColumns)
                .eventsQuery(queryEditPresenter.getQuery())
                .eventsQueryTimeRange(queryEditPresenter.getTimeRange())
                .eventsQueryTablePreferences(queryEditPresenter.write())
                .build();
    }

    /// @param onChange What to run when the user changes something on this tab: the query, or
    ///                 one of the per-role column dropdowns.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    /// @param onEventData What to run with the events a finished query returns, as map objects.
    public void setOnEventData(final Consumer<List<FloorMapObject>> onEventData) {
        this.onEventData = onEventData;
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
        return view.getEventColumns();
    }


    @Override
    public List<Widget> getToolbars() {
        return queryEditPresenter.getToolbars();
    }

}
