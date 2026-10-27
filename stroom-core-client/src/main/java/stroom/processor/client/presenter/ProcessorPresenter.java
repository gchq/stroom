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

package stroom.processor.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.data.client.component.ExpressionDialogController;
import stroom.data.client.presenter.OpenLinkUtil;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.client.component.BatchProcessorFilterEditController;
import stroom.processor.client.component.ProcessorAction;
import stroom.processor.client.component.ProcessorEditController;
import stroom.processor.client.component.ProcessorInfoBuilder;
import stroom.processor.client.component.ProcessorListController;
import stroom.processor.shared.ProcessorFields;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterFields;
import stroom.processor.shared.ProcessorFilterResource;
import stroom.processor.shared.ProcessorFilterRow;
import stroom.processor.shared.ProcessorListRow;
import stroom.processor.shared.ProcessorType;
import stroom.processor.shared.QueryData;
import stroom.processor.task.client.event.OpenProcessorTaskEvent;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.datasource.QueryField;
import stroom.query.client.component.ExpressionTreeController;
import stroom.security.shared.DocPermissionResource;
import stroom.util.client.CountDownAndRun;
import stroom.util.shared.NullSafe;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.Selection;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ProcessorPresenter
        extends MyPresenterWidget<ProcessorPresenter.ProcessorView>
        implements HasDocumentRead<Object> {

    private static final ProcessorFilterResource PROCESSOR_FILTER_RESOURCE = GWT.create(ProcessorFilterResource.class);
    private static final DocPermissionResource DOC_PERMISSION_RESOURCE = GWT.create(DocPermissionResource.class);

    private final ProcessorListController processorList;
    private final Provider<ProcessorEditController> processorEditProvider;
    private final ExpressionTreeController expressionTree;
    private final RestFactory restFactory;
    private final ProcessorInfoBuilder processorInfoBuilder;
    private final Provider<ExpressionDialogController> filterDialogProvider;
    private final Provider<BatchProcessorFilterEditController> batchProcessorFilterEditProvider;

    private ProcessorType processorType;
    private DocRef docRef;
    //    private ProcessorListRow selectedProcessor;
    private boolean buttonsCreated;

    private boolean allowCreate;
    private boolean allowUpdate;
    private boolean isAdmin;

    private ExpressionOperator defaultExpression;
    private Supplier<Boolean> editInterceptor = () -> true;

    @Inject
    public ProcessorPresenter(final EventBus eventBus,
                              final ProcessorView view,
                              final ProcessorListController processorList,
                              final Provider<ProcessorEditController> processorEditProvider,
                              final ExpressionTreeController expressionTree,
                              final RestFactory restFactory,
                              final ProcessorInfoBuilder processorInfoBuilder,
                              final Provider<ExpressionDialogController> filterDialogProvider,
                              final Provider<BatchProcessorFilterEditController>
                                      batchProcessorFilterEditProvider) {
        super(eventBus, view);
        own(processorList);
        own(expressionTree);
        this.processorList = processorList;
        this.processorEditProvider = processorEditProvider;
        this.expressionTree = expressionTree;
        this.restFactory = restFactory;
        this.processorInfoBuilder = processorInfoBuilder;
        this.filterDialogProvider = filterDialogProvider;
        this.batchProcessorFilterEditProvider = batchProcessorFilterEditProvider;

        // Stop users from selecting expression items.
        expressionTree.setSelectionModel(null);

        view.setProcessorList(processorList);
        view.setDetailsView(expressionTree);

        own(processorList.getSelectionModel().addSelectionHandler(event -> {
            updateData();
            if (event.getSelectionType().isDoubleSelect()) {
                if (allowUpdate) {
                    editProcessor();
                }
            }
        }));
        own(OpenLinkUtil.addClickHandler(this, getWidget()));
    }

    @Override
    public void read(final DocRef docRef, final Object document) {
        processorType = ProcessorType.PIPELINE;
        this.docRef = docRef;
        processorList.read(docRef, document);
        if (document instanceof PipelineDoc) {
            allowCreate = true;
        } else if (document instanceof AnalyticRuleDoc) {
            processorType = ProcessorType.STREAMING_ANALYTIC;
            allowCreate = true;
        }
    }

    public void setIsAdmin(final boolean isAdmin) {
        this.isAdmin = isAdmin;
    }

    public void setAllowUpdate(final boolean allowUpdate) {
        this.allowUpdate = allowUpdate;

        if (allowUpdate) {
            createButtons();
        }

        processorList.setAllowUpdate(allowUpdate);
    }

    private void createButtons() {
        if (!buttonsCreated) {
            buttonsCreated = true;
            final Set<ProcessorAction> actions = EnumSet.of(
                    ProcessorAction.EDIT,
                    ProcessorAction.DELETE,
                    ProcessorAction.FILTER,
                    ProcessorAction.SHOW_TASKS);
            if (allowCreate) {
                actions.add(ProcessorAction.NEW);
                actions.add(ProcessorAction.DUPLICATE);
            }
            if (allowUpdate) {
                actions.add(ProcessorAction.BATCH_EDIT);
            }
            processorList.setAvailableActions(actions);
            processorList.setOnAction(action -> {
                switch (action) {
                    case NEW -> onAdd();
                    case EDIT -> onEdit();
                    case DUPLICATE -> onDuplicate();
                    case DELETE -> onRemove();
                    case FILTER -> onFilter();
                    case BATCH_EDIT -> onBatchEdit();
                    case SHOW_TASKS -> showTasksTab();
                }
            });

            enableButtons(false);
        }
    }

    private void onBatchEdit() {
        batchProcessorFilterEditProvider.get().show(this,
                processorList.getExpression(),
                processorList::refresh);
    }

    private void showTasksTab() {
        final ProcessorListRow selectedProcessor = processorList.getSelectionModel().getSelected();
        if (selectedProcessor instanceof final ProcessorFilterRow processorFilterRow) {
            OpenProcessorTaskEvent.fire(this, processorFilterRow.getProcessorFilter());
        }
    }

    private void onFilter() {
        final List<QueryField> fields = new ArrayList<>();
        fields.addAll(ProcessorFields.getFields());
        fields.addAll(ProcessorFilterFields.getFields());
        filterDialogProvider.get().show(this,
                "Filter Processors",
                PopupSize.resizable(800, 600),
                processorList.getExpression(),
                ProcessorFilterFields.PROCESSOR_FILTERS_DOC_REF,
                fields,
                (expression, e) -> {
                    processorList.setExpression(expression);
                    refresh();
                    e.hide();
                });
    }

    private void enableButtons(final boolean enabled) {
        final boolean onlyOneRowSelected = processorList.getSelectionModel().getSelectedItems().size() == 1;

        processorList.setActionEnabled(ProcessorAction.NEW, allowUpdate);
        processorList.setActionEnabled(ProcessorAction.EDIT, allowUpdate && enabled && onlyOneRowSelected);
        processorList.setActionEnabled(ProcessorAction.DUPLICATE, allowUpdate && enabled);
        processorList.setActionEnabled(ProcessorAction.DELETE, allowUpdate && enabled);
        processorList.setActionEnabled(ProcessorAction.SHOW_TASKS, enabled && onlyOneRowSelected);
    }

    private void onAdd() {
        if (allowUpdate) {
            addProcessor();
        }
    }

    private void onEdit() {
        if (allowUpdate) {
            editProcessor();
        }
    }

    private void onDuplicate() {
        if (allowUpdate) {
            duplicateProcessor();
        }
    }

    private void onRemove() {
        if (allowUpdate) {
            removeProcessor();
        }
    }

    private void updateData() {
        final ProcessorListRow selectedProcessor = processorList.getSelectionModel().getSelected();
        setData(selectedProcessor);
        enableButtons(selectedProcessor instanceof ProcessorFilterRow);
    }

    private void setData(final ProcessorListRow row) {
        final SafeHtml safeHtml = processorInfoBuilder.get(row);
        getView().setInfo(safeHtml);

        ExpressionOperator expression = null;
        if (row instanceof ProcessorFilterRow) {
            final ProcessorFilter processorFilter = ((ProcessorFilterRow) row).getProcessorFilter();
            final QueryData queryData = processorFilter.getQueryData();
            if (queryData != null && queryData.getExpression() != null) {
                expression = queryData.getExpression();
            }
        }
        expressionTree.read(expression);
    }

    public MultiSelectionModel<ProcessorListRow> getSelectionModel() {
        return processorList.getSelectionModel();
    }

    private void addProcessor() {
        if (allowCreate) {
            edit(null, defaultExpression);
        }
    }

    /**
     * Make a copy of the currently selected processor
     */
    private void duplicateProcessor() {
        if (allowCreate) {
            // Now create the processor filter using the find stream criteria.
            final ProcessorListRow selectedProcessor = processorList.getSelectionModel().getSelected();
            final ProcessorFilterRow row = (ProcessorFilterRow) selectedProcessor;
            final ProcessorFilter processorFilter = row.getProcessorFilter();
            final ProcessorFilter copy = processorFilter
                    .copy()
                    .id(null)
                    .version(null)
                    .processorFilterTracker(null)
                    .createUser(null)
                    .createTimeMs(null)
                    .updateUser(null)
                    .createTimeMs(null)
                    .build();
            edit(copy, null);
        }
    }

    private void editProcessor() {
        final ProcessorListRow selectedProcessor = processorList.getSelectionModel().getSelected();
        if (selectedProcessor != null) {
            if (selectedProcessor instanceof ProcessorFilterRow) {
                final ProcessorFilterRow processorFilterRow = (ProcessorFilterRow) selectedProcessor;
                final ProcessorFilter filter = processorFilterRow.getProcessorFilter();

                restFactory
                        .create(PROCESSOR_FILTER_RESOURCE)
                        .method(res -> res.fetch(filter.getId()))
                        .onSuccess(loadedFilter -> {
                            if (loadedFilter == null) {
                                AlertEvent.fireError(
                                        ProcessorPresenter.this,
                                        "Unable to load filter",
                                        null);
                            } else {
                                edit(loadedFilter, null);
                            }
                        })
                        .taskMonitorFactory(this)
                        .exec();
            }
        }
    }

    private void edit(final ProcessorFilter filter,
                      final ExpressionOperator defaultExpression) {
        if (editInterceptor.get()) {
            if (filter == null && ProcessorType.STREAMING_ANALYTIC.equals(processorType)) {
                processorEditProvider.get()
                        .show(this,
                                processorType,
                                docRef,
                                null,
                                defaultExpression,
                                System.currentTimeMillis(),
                                null,
                                null,
                                null,
                                result -> {
                                    if (result != null) {
                                        // The runAsUser can't be changed in the editor
                                        refresh(result);
                                    }
                                });
            } else {
                processorEditProvider.get()
                        .show(this, processorType, docRef, filter, null, result -> {
                            if (result != null) {
                                // The runAsUser can't be changed in the editor
                                refresh(result);
                            }
                        });
            }
        }
    }

    private void removeProcessor() {
        final Selection<ProcessorListRow> selection = processorList.getSelectionModel().getSelection();
        if (selection != null && selection.getSelectedItems() != null) {
            final List<ProcessorFilterRow> rows = selection
                    .getSelectedItems()
                    .stream()
                    .filter(s -> s instanceof ProcessorFilterRow)
                    .map(s -> (ProcessorFilterRow) s)
                    .collect(Collectors.toList());
            if (!rows.isEmpty()) {
                String message = "Are you sure you want to delete the selected filter?";
                if (rows.size() > 1) {
                    message = "Are you sure you want to delete the selected filters?";
                }
                ConfirmEvent.fire(this, message, result -> {
                    if (result) {
                        final CountDownAndRun countDownAndRun = new CountDownAndRun(
                                rows.size(), processorList::refresh);
                        for (final ProcessorFilterRow row : rows) {
                            restFactory
                                    .create(PROCESSOR_FILTER_RESOURCE)
                                    .method(res -> res.delete(row.getProcessorFilter().getId()))
                                    .onSuccess(res ->
                                            countDownAndRun.countdown())
                                    .onFailure(new DefaultErrorHandler(this, countDownAndRun::countdown))
                                    .taskMonitorFactory(this)
                                    .exec();
                        }
                    }
                });
            }
        }
    }

//    private void setPermissions() {
//        if (selectedProcessor instanceof ProcessorFilterRow) {
//            final ProcessorFilterRow processorFilterRow = (ProcessorFilterRow) selectedProcessor;
//            final DocRef processorFilterRef = new DocRef(ProcessorFilter.ENTITY_TYPE,
//                    processorFilterRow.getProcessorFilter().getUuid(),
//                    null);
//            ShowPermissionsDialogEvent.fire(this, processorFilterRef);
//        }
//    }

    public void refresh() {
        refresh(processorList.getSelectionModel().getSelected());
    }

    public void refresh(final ProcessorFilter processorFilter) {
        refresh(NullSafe.get(processorFilter, ProcessorFilterRow::new));
    }

    public void refresh(final ProcessorListRow row) {
        processorList.setNextSelection(row);
        processorList.refresh();

        if (row != null) {
            processorList.getSelectionModel().clear();
            processorList.getSelectionModel().setSelected(row, true);
        }

        updateData();
    }

    public void setDefaultExpression(final ExpressionOperator defaultExpression) {
        this.defaultExpression = defaultExpression;
    }

    public void setEditInterceptor(final Supplier<Boolean> editInterceptor) {
        this.editInterceptor = editInterceptor;
    }

    // --------------------------------------------------------------------------------


    public interface ProcessorView extends View {

        void setProcessorList(IsWidget view);

        void setInfo(SafeHtml info);

        void setDetailsView(IsWidget view);
    }
}
