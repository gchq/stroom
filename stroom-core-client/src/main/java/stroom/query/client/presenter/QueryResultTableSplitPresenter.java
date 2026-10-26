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

package stroom.query.client.presenter;

import stroom.index.shared.IndexConstants;
import stroom.pipeline.shared.SourceLocation;
import stroom.query.api.GroupSelection;
import stroom.query.api.OffsetRange;
import stroom.query.api.Result;
import stroom.query.api.SpecialColumns;
import stroom.query.client.component.QueryTextPaneController;
import stroom.query.client.presenter.QueryResultTableSplitPresenter.QueryResultTableSplitView;
import stroom.query.shared.QueryTablePreferences;
import stroom.widget.layout.client.SplitPane;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;

import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class QueryResultTableSplitPresenter
        extends MyPresenterWidget<QueryResultTableSplitView>
        implements ResultComponent {

    private final QueryResultTablePresenter tablePresenter;
    private final QueryTextPaneController textPane;
    private SimplePanel tableContainer;
    private SplitPane splitLayoutPanel;
    private boolean showingSplit = true;


    @Inject
    public QueryResultTableSplitPresenter(final EventBus eventBus,
                                          final QueryResultTableSplitView view,
                                          final QueryResultTablePresenter tablePresenter,
                                          final QueryTextPaneController textPane) {
        super(eventBus, view);
        own(tablePresenter);
        own(textPane);
        this.tablePresenter = tablePresenter;
        this.textPane = textPane;

        view.setWidget(tablePresenter.getWidget());
        showSplit(false);

        own(tablePresenter.getSelectionModel().addSelectionHandler(event ->
                onSelection(tablePresenter.getSelectionModel().getSelected())));
    }

    @Override
    public void setQueryModel(final QueryModel queryModel) {
        tablePresenter.setQueryModel(queryModel);
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
        if (show != showingSplit) {
            showingSplit = show;
            if (show) {
                if (splitLayoutPanel == null) {
                    tableContainer = new SimplePanel();
                    tableContainer.setStyleName("max");

                    // The table beside the source of the selected row, half each, the source no
                    // narrower than 100px
                    splitLayoutPanel = new SplitPane();
                    splitLayoutPanel.setThin(true);
                    splitLayoutPanel.setSizes("*, 50%");
                    splitLayoutPanel.setMinSizes("0px, 100px");
                    splitLayoutPanel.setNames("Table, Source");
                    splitLayoutPanel.addStyleName("max");
                    splitLayoutPanel.add(tableContainer);
                    splitLayoutPanel.add(textPane);
                }

                tableContainer.setWidget(tablePresenter.getWidget());
                getView().setWidget(splitLayoutPanel);
            } else {
                getView().setWidget(tablePresenter.getWidget());
            }
        }
    }

    public void clear() {
        tablePresenter.getSelectionModel().clear();
    }

    public QueryResultTablePresenter getTablePresenter() {
        return tablePresenter;
    }

    @Override
    public OffsetRange getRequestedRange() {
        return tablePresenter.getRequestedRange();
    }

    @Override
    public GroupSelection getGroupSelection() {
        return tablePresenter.getGroupSelection();
    }

    @Override
    public void reset() {
        tablePresenter.reset();
    }

    @Override
    public void startSearch() {
        tablePresenter.startSearch();
    }

    @Override
    public void endSearch() {
        tablePresenter.endSearch();
    }

    @Override
    public void setData(final Result componentResult) {
        tablePresenter.setData(componentResult);
    }

    public void setQueryTablePreferencesSupplier(final Supplier<QueryTablePreferences> queryTablePreferencesSupplier) {
        tablePresenter.setQueryTablePreferencesSupplier(queryTablePreferencesSupplier);
    }

    public void setQueryTablePreferencesConsumer(final Consumer<QueryTablePreferences> queryTablePreferencesConsumer) {
        tablePresenter.setQueryTablePreferencesConsumer(queryTablePreferencesConsumer);
    }

    public void updateQueryTablePreferences() {
        tablePresenter.updateQueryTablePreferences();
    }

    /// @param onChange What to run when the user changes the table's settings.
    public void setOnChange(final Runnable onChange) {
        tablePresenter.setOnChange(onChange);
    }

    public void setQueryResultVisPresenter(final QueryResultVisPresenter queryResultVisPresenter) {
        tablePresenter.setQueryResultVisPresenter(queryResultVisPresenter);
    }

    public void setQuery(final String query) {
        tablePresenter.setQuery(query);
    }

    public void onContentTabVisible(final boolean visible) {
        tablePresenter.onContentTabVisible(visible);
    }

    public interface QueryResultTableSplitView extends View {

        void setWidget(Widget widget);
    }
}
