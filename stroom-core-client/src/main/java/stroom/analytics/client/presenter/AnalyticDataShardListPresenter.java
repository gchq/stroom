/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.analytics.client.presenter;

import stroom.analytics.shared.AnalyticDataShard;
import stroom.analytics.shared.AnalyticDataShardResource;
import stroom.analytics.shared.FindAnalyticDataShardCriteria;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.node.client.NodeManager;
import stroom.preferences.client.DateTimeFormatter;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.DelayedUpdate;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.ResultPage;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class AnalyticDataShardListPresenter
        extends MyPresenterWidget<PagerView> {

    private static final AnalyticDataShardResource ANALYTIC_DATA_SHARD_RESOURCE =
            GWT.create(AnalyticDataShardResource.class);

    private final MyDataGrid<AnalyticDataShard> dataGrid;
    private final MultiSelectionModelImpl<AnalyticDataShard> selectionModel;
    private final RestFactory restFactory;
    private final NodeManager nodeManager;
    private final DateTimeFormatter dateTimeFormatter;
    //    private final ButtonView newButton;
//    private final ButtonView openButton;
//    private final ButtonView deleteButton;
    private final FindAnalyticDataShardCriteria criteria;
    private final RestDataProvider<AnalyticDataShard, ResultPage<AnalyticDataShard>> dataProvider;
    private boolean initialised;

    private final Map<String, List<AnalyticDataShard>> responseMap = new HashMap<>();
    private final Map<String, List<String>> errorMap = new HashMap<>();
    private final DelayedUpdate delayedUpdate;
    private Range range;
    private Consumer<ResultPage<AnalyticDataShard>> dataConsumer;

    @Inject
    public AnalyticDataShardListPresenter(final EventBus eventBus,
                                          final PagerView view,
                                          final RestFactory restFactory,
                                          final NodeManager nodeManager,
                                          final DateTimeFormatter dateTimeFormatter) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.nodeManager = nodeManager;
        this.dateTimeFormatter = dateTimeFormatter;

        dataGrid = new MyDataGrid<>(this);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);

//        newButton = view.addButton(SvgPresets.NEW_ITEM);
//        openButton = view.addButton(SvgPresets.EDIT);
//        deleteButton = view.addButton(SvgPresets.DELETE);

        initTableColumns();

        criteria = new FindAnalyticDataShardCriteria();
//        dataProvider = new RestDataProvider<AnalyticDataShard, ResultPage<AnalyticDataShard>>(eventBus) {
//            @Override
//            protected void exec(final Range range,
//                                final Consumer<ResultPage<AnalyticDataShard>> dataConsumer,
//                                final Consumer<Throwable> throwableConsumer) {
//                if (criteria.getAnalyticDocUuid() != null) {
//                    CriteriaUtil.setRange(criteria, range);
//                    final Rest<ResultPage<AnalyticDataShard>> rest = restFactory.create();
//                    rest
//                            .onSuccess(dataConsumer)
//                            .onFailure(throwableConsumer)
//                            .call(ANALYTIC_DATA_SHARD_RESOURCE)
//                            .find(criteria);
//                }
//            }
//        };


        delayedUpdate = new DelayedUpdate(this::update);
        dataProvider = new RestDataProvider<AnalyticDataShard, ResultPage<AnalyticDataShard>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<AnalyticDataShard>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                AnalyticDataShardListPresenter.this.range = range;
                AnalyticDataShardListPresenter.this.dataConsumer = dataConsumer;
                delayedUpdate.reset();
                fetchNodes(range, dataConsumer, errorHandler, view);
            }
        };
    }

    private void update() {
        // Combine data from all nodes.
        final List<AnalyticDataShard> list = responseMap
                .values()
                .stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
        final ResultPage<AnalyticDataShard> resultPage = ResultPage.createCriterialBasedList(list, criteria);

//        final HashSet<TaskProgress> currentTaskSet = new HashSet<TaskProgress>(resultPage.getValues());
//        selectedTaskProgress.retainAll(currentTaskSet);

//        final String allErrors = errorMap.entrySet()
//                .stream()
//                .flatMap(r -> r.getValue().stream().map(message -> r.getKey() + ": " + message))
//                .collect(Collectors.joining("\n"));
//        setErrors(allErrors);
//
//        final ResultPage<AnalyticDataShard> response = new TaskProgressResponse(
//                resultPage.getValues(),
//                null,
//                resultPage.getPageResponse());

        dataConsumer.accept(resultPage);
//        updateButtonStates();
    }


    public void fetchNodes(final Range range,
                           final Consumer<ResultPage<AnalyticDataShard>> dataConsumer,
                           final RestErrorHandler errorHandler,
                           final TaskMonitorFactory taskMonitorFactory) {
        nodeManager.listAllNodes(
                nodeNames -> fetchTasksForNodes(range, dataConsumer, nodeNames, taskMonitorFactory),
                errorHandler, taskMonitorFactory);
    }

    private void fetchTasksForNodes(final Range range,
                                    final Consumer<ResultPage<AnalyticDataShard>> dataConsumer,
                                    final List<String> nodeNames,
                                    final TaskMonitorFactory taskMonitorFactory) {
        responseMap.clear();
        for (final String nodeName : nodeNames) {
            if (criteria.getAnalyticDocUuid() != null) {
                CriteriaUtil.setRange(criteria, range);
                restFactory
                        .create(ANALYTIC_DATA_SHARD_RESOURCE)
                        .method(res -> res.find(nodeName, criteria))
                        .onSuccess(response -> {
                            responseMap.put(nodeName, response.getValues());
//                            errorMap.put(nodeName, response.getErrors());
                            delayedUpdate.update();
                        })
                        .onFailure(throwable -> {
                            responseMap.remove(nodeName);
                            errorMap.put(nodeName, Collections.singletonList(throwable.getMessage()));
                            delayedUpdate.update();
                        })
                        .taskMonitorFactory(taskMonitorFactory)
                        .exec();
            }


//            final Rest<TaskProgressResponse> rest = restFactory.create();
//            rest
//                    .onSuccess(response -> {
//                        responseMap.put(nodeName, response.getValues());
//                        errorMap.put(nodeName, response.getErrors());
//                        delayedUpdate.update();
//                    })
//                    .onFailure(throwable -> {
//                        responseMap.remove(nodeName);
//                        errorMap.put(nodeName, Collections.singletonList(throwable.getMessage()));
//                        delayedUpdate.update();
//                    })
//                    .call(TASK_RESOURCE)
//                    .find(nodeName, request);
        }
    }


    private void enableButtons() {
//        final AnalyticDataShard selected = selectionModel.getSelected();
//        final boolean enabled = !isReadOnly() && selected != null;
//        newButton.setEnabled(!isReadOnly());
//        openButton.setEnabled(enabled);
//        deleteButton.setEnabled(enabled);
    }

    @Override
    protected void onBind() {
//        registerHandler(selectionModel.addSelectionHandler(event -> {
//            enableButtons();
//            if (event.getSelectionType().isDoubleSelect()) {
//                onEdit(selectionModel.getSelected());
//            }
//        }));
//        if (newButton != null) {
//            registerHandler(newButton.addClickHandler(event -> {
//                if (MouseUtil.isPrimary(event)) {
//                    onNew();
//                }
//            }));
//        }
//        if (openButton != null) {
//            registerHandler(openButton.addClickHandler(event -> {
//                if (MouseUtil.isPrimary(event)) {
//                    onEdit(selectionModel.getSelected());
//                }
//            }));
//        }
//        if (deleteButton != null) {
//            registerHandler(deleteButton.addClickHandler(event -> {
//                if (MouseUtil.isPrimary(event)) {
//                    onDelete();
//                }
//            }));
//        }

        super.onBind();
    }

    private void initTableColumns() {
        // Node.
        dataGrid.addResizableColumn(new Column<AnalyticDataShard, String>(new TextCell()) {
            @Override
            public String getValue(final AnalyticDataShard row) {
                return Optional.ofNullable(row)
                        .map(AnalyticDataShard::getNode)
                        .orElse(null);
            }
        }, "Node", ColumnSizeConstants.MEDIUM_COL);

        // Create Time.
        dataGrid.addResizableColumn(new Column<AnalyticDataShard, String>(new TextCell()) {
            @Override
            public String getValue(final AnalyticDataShard row) {
                return dateTimeFormatter.formatWithDuration(row.getCreateTimeMs());
            }
        }, "Create Time", ColumnSizeConstants.DATE_AND_DURATION_COL);

        // Shard size
        dataGrid.addResizableColumn(new Column<AnalyticDataShard, String>(new TextCell()) {
            @Override
            public String getValue(final AnalyticDataShard row) {
                return ModelStringUtil.formatIECByteSizeString(row.getSize());
            }
        }, "Size", ColumnSizeConstants.SMALL_COL);

        // Path.
        dataGrid.addResizableColumn(new Column<AnalyticDataShard, String>(new TextCell()) {
            @Override
            public String getValue(final AnalyticDataShard row) {
                return row.getPath();
            }
        }, "Path", 600);


        dataGrid.addEndColumn(new EndColumn<>());
    }

    protected void read(final DocRef docRef) {
        criteria.setAnalyticDocUuid(docRef.getUuid());
        refresh();
    }

    public void refresh() {
        if (!initialised) {
            initialised = true;
            dataProvider.addDataDisplay(dataGrid);
        } else {
            dataProvider.refresh();
        }
    }
//
//    @Override
//    protected AnalyticRuleDoc onWrite(final AnalyticRuleDoc alertRule) {
//        return alertRule;
//    }
//
//    private void onNew() {
//        final AnalyticNotificationEditPresenter editor = editProvider.get();
//        final AnalyticNotificationStreamConfig config = AnalyticNotificationStreamConfig
//                .builder()
//                .timeToWaitForData(SimpleDuration.builder().time(1).timeUnit(TimeUnit.HOURS).build())
//                .useSourceFeedIfPossible(true)
//                .build();
//        final AnalyticNotification newNotification = AnalyticNotification
//                .builder()
//                .analyticUuid(criteria.getAnalyticDocUuid())
//                .config(config)
//                .build();
//        editor.show(newNotification, notification -> {
//            refresh();
////            selectionModel.setSelected(notification);
////            setDirty(true);
//        }, true);
//    }
//
//    private void onEdit(final AnalyticDataShard row) {
//        if (row != null) {
//            final AnalyticNotificationEditPresenter editor = editProvider.get();
//            editor.show(row.getAnalyticNotification(), notification -> {
//                refresh();
////                selectionModel.setSelected(row);
////                setDirty(true);
//            }, false);
//        }
//    }
//
//    private void onDelete() {
//        final AnalyticDataShard selected = selectionModel.getSelected();
//        if (selected != null) {
//            ConfirmEvent.fire(this, "Are you sure you want to delete the selected notification?",
//                    result -> {
//                        if (result) {
//                            final Rest<AnalyticNotification> rest = restFactory.create();
//                            rest
//                                    .onSuccess(r -> {
//                                        refresh();
////                                        setDirty(true);
//                                    })
//                                    .call(ANALYTIC_DATA_SHARD_RESOURCE)
//                                    .delete(selected.getAnalyticNotification().getUuid(),
//                                            selected.getAnalyticNotification());
//                        }
//                    });
//        }
//    }
//
//    @Override
//    public void onDirty() {
////        setDirty(true);
//    }
//
//    @Override
//    public String getType() {
//        return AnalyticRuleDoc.DOCUMENT_TYPE;
//    }


    public MultiSelectionModelImpl<AnalyticDataShard> getSelectionModel() {
        return selectionModel;
    }
}
