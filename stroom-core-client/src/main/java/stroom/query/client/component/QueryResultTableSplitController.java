/*
 * Copyright 2023 Crown Copyright
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

package stroom.query.client.component;

import stroom.index.shared.IndexConstants;
import stroom.pipeline.shared.SourceLocation;
import stroom.query.api.GroupSelection;
import stroom.query.api.OffsetRange;
import stroom.query.api.Result;
import stroom.query.api.SpecialColumns;
import stroom.query.client.QueryModel;
import stroom.query.client.ResultComponent;
import stroom.query.client.TableRow;
import stroom.query.shared.QueryTablePreferences;
import stroom.widget.component.client.LayerController;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;
import java.util.function.Supplier;

/// A query's results table ([QueryResultTableSplit]), beside the source of the row selected when the
/// row has a stream and an event id.
public class QueryResultTableSplitController
        extends LayerController
        implements ResultComponent {

    private final QueryResultTableSplit view;
    private final QueryResultTableController tableController;
    private final QueryTextPaneController textPane;

    /// Made by GIN, with its content and its parts.
    ///
    /// @param eventBus       The application's event bus.
    /// @param view           The content.
    /// @param tablePresenter The results table.
    /// @param textPane       The source of the row selected.
    @Inject
    public QueryResultTableSplitController(final EventBus eventBus,
                                           final QueryResultTableSplit view,
                                           final QueryResultTableController tableController,
                                           final QueryTextPaneController textPane) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        own(tableController);
        own(textPane);
        this.tableController = tableController;
        this.textPane = textPane;

        view.setTable(tableController);
        view.setSource(textPane);

        own(tableController.getSelectionModel().addSelectionHandler(event ->
                onSelection(tableController.getSelectionModel().getSelected())));
    }

    @Override
    public void setQueryModel(final QueryModel queryModel) {
        tableController.setQueryModel(queryModel);
    }

    private void onSelection(final TableRow tableRow) {
        if (tableRow == null) {
            showSplit(false);
        } else {
            String streamId = tableRow.getText(IndexConstants.STREAM_ID);
            if (streamId == null) {
                streamId = tableRow.getText(SpecialColumns.RESERVED_STREAM_ID);
            }

            String eventId = tableRow.getText(IndexConstants.EVENT_ID);
            if (eventId == null) {
                eventId = tableRow.getText(SpecialColumns.RESERVED_EVENT_ID);
            }

            if (streamId != null && eventId != null && streamId.length() > 0 && eventId.length() > 0) {
                try {
                    final long strmId = Long.parseLong(streamId);
                    final long evtId = Long.parseLong(eventId);
                    final SourceLocation sourceLocation = SourceLocation
                            .builder(strmId)
                            .withPartIndex(0L)
                            .withRecordIndex(evtId - 1)
                            .build();
                    textPane.show(
                            sourceLocation,
                            () -> showSplit(true),
                            () -> showSplit(false));

                } catch (final RuntimeException e) {
                    showSplit(false);
                }
            } else {
                showSplit(false);
            }
        }
    }

    private void showSplit(final boolean show) {
        view.setSourceShown(show);
    }

    /// Clears the table's selection, which hides the source.
    public void clear() {
        tableController.getSelectionModel().clear();
    }

    /// @return The results table.
    public QueryResultTableController getTable() {
        return tableController;
    }

    @Override
    public OffsetRange getRequestedRange() {
        return tableController.getRequestedRange();
    }

    @Override
    public GroupSelection getGroupSelection() {
        return tableController.getGroupSelection();
    }

    @Override
    public void reset() {
        tableController.reset();
    }

    @Override
    public void startSearch() {
        tableController.startSearch();
    }

    @Override
    public void endSearch() {
        tableController.endSearch();
    }

    @Override
    public void setData(final Result componentResult) {
        tableController.setData(componentResult);
    }

    /// @param queryTablePreferencesSupplier Gives the table's preferences, e.g. its columns.
    public void setQueryTablePreferencesSupplier(final Supplier<QueryTablePreferences> queryTablePreferencesSupplier) {
        tableController.setQueryTablePreferencesSupplier(queryTablePreferencesSupplier);
    }

    /// @param queryTablePreferencesConsumer Given the table's preferences when the user changes them.
    public void setQueryTablePreferencesConsumer(final Consumer<QueryTablePreferences> queryTablePreferencesConsumer) {
        tableController.setQueryTablePreferencesConsumer(queryTablePreferencesConsumer);
    }

    /// Shows the table's preferences again, e.g. after they were read.
    public void updateQueryTablePreferences() {
        tableController.updateQueryTablePreferences();
    }

    /// @param onChange What to run when the user changes the table's settings.
    public void setOnChange(final Runnable onChange) {
        tableController.setOnChange(onChange);
    }

    /// @param queryResultVis The results' visualisation, refreshed with the table.
    public void setQueryResultVis(final QueryResultVisController queryResultVis) {
        tableController.setQueryResultVis(queryResultVis);
    }

    /// @param query The query, whose data source the table's column menus use.
    public void setQuery(final String query) {
        tableController.setQuery(query);
    }

    /// @param visible Whether the query's tab is showing.
    public void onContentTabVisible(final boolean visible) {
        tableController.onContentTabVisible(visible);
    }
}
