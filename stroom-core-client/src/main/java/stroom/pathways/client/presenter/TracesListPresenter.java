/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.pathways.client.presenter;

import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.pathways.shared.FindTraceCriteria;
import stroom.pathways.shared.TracesResource;
import stroom.pathways.shared.otel.trace.NanoDuration;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.pathways.shared.pathway.Pathway;
import stroom.preferences.client.DateTimeFormatter;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.time.SimpleDuration;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.function.Consumer;
import java.util.function.Function;

public class TracesListPresenter
        extends MyPresenterWidget<PagerView> {

    private static final TracesResource TRACES_RESOURCE = GWT.create(TracesResource.class);

    private final DateTimeFormatter dateTimeFormatter;
    private final RestFactory restFactory;
    private final MyDataGrid<TraceRoot> dataGrid;
    private final MultiSelectionModelImpl<TraceRoot> selectionModel;
    private RestDataProvider<TraceRoot, ResultPage<TraceRoot>> dataProvider;

    private DocRef dataSourceRef;
    private String filter;
    private Pathway pathway;

    @Inject
    public TracesListPresenter(final EventBus eventBus,
                               final PagerView view,
                               final RestFactory restFactory,
                               final DateTimeFormatter dateTimeFormatter) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.dateTimeFormatter = dateTimeFormatter;

        dataGrid = new MyDataGrid<>(this);
        view.setDataWidget(dataGrid);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        addColumns();
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(selectionModel.addSelectionHandler(event -> {

        }));
        registerHandler(dataGrid.addColumnSortHandler(event -> refresh()));
    }

    public MultiSelectionModelImpl<TraceRoot> getSelectionModel() {
        return selectionModel;
    }

    private void addColumns() {
        addNameColumn();
        addIdColumn();
        addTraceStartColumn();
        addDurationColumn();
        addServicesColumn();
        addDepthColumn();
        addTotalSpansColumn();
        dataGrid.addEndColumn(new EndColumn<>());
    }

    private void addNameColumn() {
        final Function<TraceRoot, String> valueExtractor = trace -> NullSafe.get(trace, TraceRoot::getName);
        addTextColumn("Operation", 300, valueExtractor);
    }

    private void addIdColumn() {
        final Function<TraceRoot, String> valueExtractor = trace -> NullSafe.get(trace, TraceRoot::getTraceId);
        addTextColumn("Trace Id", 300, valueExtractor);
    }


    private void addTraceStartColumn() {
        final Function<TraceRoot, NanoTime> valueExtractor = TraceRoot::getStartTime;
        addTimeColumn("Trace Start", 200, valueExtractor);
    }

    private void addDurationColumn() {
        final Function<TraceRoot, String> valueExtractor = trace -> {
            final NanoTime start = trace.getStartTime();
            final NanoTime end = trace.getEndTime();
            final NanoDuration duration = end.diff(start);
            return duration.toString();
        };
        final Column<TraceRoot, String> column = DataGridUtil
                .textColumnBuilder(valueExtractor)
                .withSorting("Duration")
                .build();
        dataGrid.addResizableColumn(column,
                "Duration",
                100);
    }

    private void addServicesColumn() {
        final Function<TraceRoot, String> valueExtractor = trace ->
                Integer.toString(NullSafe.get(trace, TraceRoot::getServices));
        addTextColumn("Services", 100, valueExtractor);
    }

    private void addDepthColumn() {
        final Function<TraceRoot, String> valueExtractor = trace ->
                Integer.toString(NullSafe.get(trace, TraceRoot::getDepth));
        addTextColumn("Depth", 100, valueExtractor);
    }

    private void addTotalSpansColumn() {
        final Function<TraceRoot, String> valueExtractor = trace -> Integer.toString(NullSafe.get(trace,
                TraceRoot::getTotalSpans));
        addTextColumn("Total Spans", 100, valueExtractor);
    }

    private void addTextColumn(final String name,
                               final int width,
                               final Function<TraceRoot, String> function) {
        final Column<TraceRoot, String> column = DataGridUtil
                .textColumnBuilder(function)
                .withSorting(name)
                .build();
        dataGrid.addResizableColumn(column,
                name,
                width);
//        dataGrid.sort(column);
    }

    private void addTimeColumn(final String name,
                               final int width,
                               final Function<TraceRoot, NanoTime> function) {
        final Function<TraceRoot, String> valueExtractor = pathway -> {
            final NanoTime nanoTime = function.apply(pathway);
            return nanoTime == null
                    ? ""
                    : dateTimeFormatter.format(nanoTime.toEpochMillis());
        };
        final Column<TraceRoot, String> column = DataGridUtil
                .textColumnBuilder(valueExtractor)
                .withSorting(name)
                .build();
        dataGrid.addResizableColumn(column,
                name,
                width);
//        dataGrid.sort(column);
    }

    public void refresh() {
        if (dataProvider == null) {
            dataProvider = new RestDataProvider<TraceRoot, ResultPage<TraceRoot>>(getEventBus()) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<ResultPage<TraceRoot>> dataConsumer,
                                    final RestErrorHandler errorHandler) {
                    if (dataSourceRef == null) {
                        dataConsumer.accept(ResultPage.empty());
                    } else {
                        final FindTraceCriteria criteria = new FindTraceCriteria(
                                CriteriaUtil.createPageRequest(range),
                                CriteriaUtil.createSortList(dataGrid.getColumnSortList()),
                                dataSourceRef,
                                filter,
                                pathway,
                                SimpleDuration.ZERO);

                        restFactory
                                .create(TRACES_RESOURCE)
                                .method(res -> res.findTraces(criteria))
                                .onSuccess(dataConsumer)
                                .onFailure(errorHandler)
                                .taskMonitorFactory(getView())
                                .exec();
                    }
                }
            };
            dataProvider.addDataDisplay(dataGrid);

        } else {
            dataProvider.refresh();
        }
    }

    public void setDataSourceRef(final DocRef dataSourceRef) {
        this.dataSourceRef = dataSourceRef;
    }

    public void setFilter(final String filter) {
        this.filter = filter;
    }

    public void setPathway(final Pathway pathway) {
        this.pathway = pathway;
    }
}
