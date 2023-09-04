/*
 * Copyright 2016 Crown Copyright
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

import stroom.pipeline.shared.SourceLocation;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.Result;
import stroom.query.client.presenter.QueryResultPresenter.QueryResultView;

import com.google.gwt.user.client.ui.ThinSplitLayoutPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.Set;

public class QueryResultPresenter
        extends MyPresenterWidget<QueryResultView>
        implements ResultConsumer {

    private final QueryResultTablePresenter tablePresenter;
    private final TextPresenter textPresenter;
    private final ThinSplitLayoutPanel splitLayoutPanel;

    private QueryModel queryModel;


    @Inject
    public QueryResultPresenter(final EventBus eventBus,
                                final QueryResultView view,
                                final QueryResultTablePresenter tablePresenter,
                                final TextPresenter textPresenter) {
        super(eventBus, view);
        this.tablePresenter = tablePresenter;
        this.textPresenter = textPresenter;

        splitLayoutPanel = new ThinSplitLayoutPanel();
        splitLayoutPanel.addStyleName("max");

        view.setWidget(tablePresenter.getWidget());
    }

    public void setQueryModel(final QueryModel queryModel) {
        this.queryModel = queryModel;
    }

    @Override
    protected void onBind() {
        registerHandler(tablePresenter.addExpanderHandler(event ->
                queryModel.refresh(QueryModel.TABLE_COMPONENT_ID)));
        registerHandler(tablePresenter.addRangeChangeHandler(event ->
                queryModel.refresh(QueryModel.TABLE_COMPONENT_ID)));
        registerHandler(tablePresenter.getSelectionModel().addSelectionHandler(event ->
                onSelection(tablePresenter.getSelectionModel().getSelected())));
    }

    private void onSelection(final TableRow tableRow) {
        if (tableRow == null) {
            getView().setWidget(tablePresenter.getWidget());
        } else {
            final String streamId = tableRow.getText("StreamId");
            final String eventId = tableRow.getText("EventId");
            if (streamId != null && eventId != null && streamId.length() > 0 && eventId.length() > 0) {
                try {
                    final long strmId = Long.parseLong(streamId);
                    final long evtId = Long.parseLong(eventId);
                    final SourceLocation sourceLocation = SourceLocation
                            .builder(strmId)
                            .withPartIndex(0L)
                            .withRecordIndex(evtId - 1)
                            .build();
                    textPresenter.show(sourceLocation);
                    final double size = Math.max(100, getWidget().getElement().getOffsetWidth() / 2D);
                    splitLayoutPanel.addEast(textPresenter.getWidget(), size);
                    splitLayoutPanel.add(tablePresenter.getWidget());
                    getView().setWidget(splitLayoutPanel);

                } catch (final RuntimeException e) {
                    getView().setWidget(tablePresenter.getWidget());
                }
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
    public Set<String> getOpenGroups() {
        return tablePresenter.getOpenGroups();
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

    public interface QueryResultView extends View {

        void setWidget(Widget widget);
    }
}
