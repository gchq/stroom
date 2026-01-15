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

package stroom.processor.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.data.client.presenter.ExpressionPresenter;
import stroom.data.client.presenter.OpenLinkUtil;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.pipeline.shared.PipelineDoc;
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
import stroom.query.client.ExpressionTreePresenter;
import stroom.security.shared.DocPermissionResource;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.util.client.CountDownAndRun;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.Selection;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ProcessorPresenter
        extends MyPresenterWidget<ProcessorPresenter.ProcessorView>
        implements HasDocumentRead<Object> {

    private static final ProcessorFilterResource PROCESSOR_FILTER_RESOURCE = GWT.create(ProcessorFilterResource.class);
    private static final DocPermissionResource DOC_PERMISSION_RESOURCE = GWT.create(DocPermissionResource.class);

    private final ProcessorListPresenter processorListPresenter;
    private final Provider<ProcessorEditPresenter> processorEditPresenterProvider;
    private final ExpressionTreePresenter expressionPresenter;
    private final RestFactory restFactory;
    private final ProcessorInfoBuilder processorInfoBuilder;
    private final Provider<ExpressionPresenter> filterPresenterProvider;
    private final Provider<BatchProcessorFilterEditPresenter> batchProcessorFilterEditPresenterProvider;

    private ProcessorType processorType;
    private DocRef docRef;
    //    private ProcessorListRow selectedProcessor;
    private ButtonView addButton;
    private ButtonView editButton;
    private ButtonView duplicateButton;
    private ButtonView removeButton;
    //    private ButtonView permissionsButton;
    private ButtonView filterButton;
    private ButtonView batchEditButton;
    private ButtonView showTasksButton;

    private boolean allowCreate;
    private boolean allowUpdate;
    private boolean isAdmin;

    private ExpressionOperator defaultExpression;
    private Supplier<Boolean> editInterceptor = () -> true;

    @Inject
    public ProcessorPresenter(final EventBus eventBus,
                              final ProcessorView view,
                              final ProcessorListPresenter processorListPresenter,
                              final Provider<ProcessorEditPresenter> processorEditPresenterProvider,
                              final ExpressionTreePresenter expressionPresenter,
                              final RestFactory restFactory,
                              final ProcessorInfoBuilder processorInfoBuilder,
                              final Provider<ExpressionPresenter> filterPresenterProvider,
                              final Provider<BatchProcessorFilterEditPresenter>
                                      batchProcessorFilterEditPresenterProvider) {
        super(eventBus, view);
        this.processorListPresenter = processorListPresenter;
        this.processorEditPresenterProvider = processorEditPresenterProvider;
        this.expressionPresenter = expressionPresenter;
        this.restFactory = restFactory;
        this.processorInfoBuilder = processorInfoBuilder;
        this.filterPresenterProvider = filterPresenterProvider;
        this.batchProcessorFilterEditPresenterProvider = batchProcessorFilterEditPresenterProvider;

        // Stop users from selecting expression items.
        expressionPresenter.setSelectionModel(null);

        view.setProcessorList(processorListPresenter.getView());
        view.setDetailsView(expressionPresenter.getView());
    }

    @Override
    public void read(final DocRef docRef, final Object document, final boolean readOnly) {
        processorType = ProcessorType.PIPELINE;
        this.docRef = docRef;
        processorListPresenter.read(docRef, document, readOnly);
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

        processorListPresenter.setAllowUpdate(allowUpdate);
    }

    private void createButtons() {
        if (removeButton == null) {
            if (allowCreate) {
                addButton = processorListPresenter.getView().addButton(SvgPresets.ADD);
                addButton.setTitle("Add Processor");
                registerHandler(addButton.addClickHandler(event -> {
                    if (allowUpdate) {
                        addProcessor();
                    }
                }));
            }

            editButton = processorListPresenter.getView().addButton(SvgPresets.EDIT);
            editButton.setTitle("Edit Processor");
            registerHandler(editButton.addClickHandler(event -> {
                if (allowUpdate) {
                    editProcessor();
                }
            }));

            if (allowCreate) {
                duplicateButton = processorListPresenter.getView().addButton(SvgPresets.COPY);
                duplicateButton.setTitle("Duplicate Processor");
                registerHandler(duplicateButton.addClickHandler(event -> {
                    if (allowUpdate) {
                        duplicateProcessor();
                    }
                }));
            }

            removeButton = processorListPresenter.getView().addButton(SvgPresets.DELETE);
            removeButton.setTitle("Delete Processor");
            registerHandler(removeButton.addClickHandler(event -> {
                if (allowUpdate) {
                    removeProcessor();
                }
            }));

//            if (isAdmin) {
//                permissionsButton = processorListPresenter.getView().addButton(SvgPresets.LOCKED_AMBER);
//                permissionsButton.setTitle("Permissions");
//                registerHandler(permissionsButton.addClickHandler(event -> {
//                    if (allowUpdate) {
//                        setPermissions();
//                    }
//                }));
//            }

            filterButton = processorListPresenter.getView().addButton(new Preset(
                    SvgImage.FILTER,
                    "Filter Processors",
                    true));
            registerHandler(filterButton.addClickHandler(e -> {
                if (MouseUtil.isPrimary(e)) {
                    onFilter();
                }
            }));

            if (allowUpdate) {
                batchEditButton = processorListPresenter.getView().addButton(new Preset(
                        SvgImage.GENERATE,
                        "Batch Edit Current Processors",
                        true));
                registerHandler(batchEditButton.addClickHandler(e -> {
                    if (MouseUtil.isPrimary(e)) {
                        onBatchEdit();
                    }
                }));
            }

            showTasksButton = processorListPresenter.getView().addButton(new Preset(
                    SvgImage.JOBS,
                    "Show Tasks",
                    true));
            registerHandler(showTasksButton.addClickHandler(e -> {
                if (MouseUtil.isPrimary(e)) {
                    showTasksTab();
                }
            }));

            enableButtons(false);
        }
    }

    private void onBatchEdit() {
        final BatchProcessorFilterEditPresenter presenter =
                batchProcessorFilterEditPresenterProvider.get();
        presenter.show(processorListPresenter.getExpression(),
                NullSafe.get(processorListPresenter.getCurrentResultPageResponse(), ResultPage::getPageResponse),
                processorListPresenter::refresh);
    }

    private void showTasksTab() {
        final ProcessorListRow selectedProcessor = processorListPresenter.getSelectionModel().getSelected();
        if (selectedProcessor instanceof final ProcessorFilterRow processorFilterRow) {
            OpenProcessorTaskEvent.fire(this, processorFilterRow.getProcessorFilter());
        }
    }

    private void onFilter() {
        final ExpressionPresenter presenter = filterPresenterProvider.get();
        final HidePopupRequestEvent.Handler handler = e -> {
            if (e.isOk()) {
                processorListPresenter.setExpression(presenter.write());
                refresh();
            }
            e.hide();
        };

        final List<QueryField> fields = new ArrayList<>();
        fields.addAll(ProcessorFields.getFields());
        fields.addAll(ProcessorFilterFields.getFields());
        presenter.read(processorListPresenter.getExpression(),
                ProcessorFilterFields.PROCESSOR_FILTERS_DOC_REF,
                fields);

        presenter.getWidget().getElement().addClassName("default-min-sizes");
        final PopupSize popupSize = PopupSize.resizable(800, 600);
        ShowPopupEvent.builder(presenter)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Filter Processors")
                .onShow(e -> presenter.focus())
                .onHideRequest(handler)
                .fire();
    }

    private void enableButtons(final boolean enabled) {
        final boolean onlyOneRowSelected = processorListPresenter.getSelectionModel().getSelectedItems().size() == 1;

        if (addButton != null) {
            addButton.setEnabled(allowUpdate);
        }
        if (editButton != null) {
            if (allowUpdate) {
                editButton.setEnabled(enabled && onlyOneRowSelected);
            } else {
                editButton.setEnabled(false);
            }
        }
        if (duplicateButton != null) {
            if (allowUpdate) {
                duplicateButton.setEnabled(enabled);
            } else {
                duplicateButton.setEnabled(false);
            }
        }
        if (removeButton != null) {
            if (allowUpdate) {
                removeButton.setEnabled(enabled);
            } else {
                removeButton.setEnabled(false);
            }
        }
//        if (permissionsButton != null) {
//            if (allowUpdate) {
//                permissionsButton.setEnabled(enabled);
//            } else {
//                permissionsButton.setEnabled(false);
//            }
//        }
        if (showTasksButton != null) {
            showTasksButton.setEnabled(enabled && onlyOneRowSelected);
        }
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(processorListPresenter.getSelectionModel().addSelectionHandler(event -> {
            updateData();
            if (event.getSelectionType().isDoubleSelect()) {
                if (allowUpdate) {
                    editProcessor();
                }
            }
        }));
    }

    private void updateData() {
        final ProcessorListRow selectedProcessor = processorListPresenter.getSelectionModel().getSelected();
        setData(selectedProcessor);
        enableButtons(selectedProcessor instanceof ProcessorFilterRow);
    }

    private void setData(final ProcessorListRow row) {
        final SafeHtml safeHtml = processorInfoBuilder.get(row);
        getView().setInfo(safeHtml);

        OpenLinkUtil.addClickHandler(this, getWidget());

        ExpressionOperator expression = null;
        if (row instanceof ProcessorFilterRow) {
            final ProcessorFilter processorFilter = ((ProcessorFilterRow) row).getProcessorFilter();
            final QueryData queryData = processorFilter.getQueryData();
            if (queryData != null && queryData.getExpression() != null) {
                expression = queryData.getExpression();
            }
        }
        expressionPresenter.read(expression);
    }

    public MultiSelectionModel<ProcessorListRow> getSelectionModel() {
        return processorListPresenter.getSelectionModel();
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
            final ProcessorListRow selectedProcessor = processorListPresenter.getSelectionModel().getSelected();
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
        final ProcessorListRow selectedProcessor = processorListPresenter.getSelectionModel().getSelected();
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
                processorEditPresenterProvider.get()
                        .show(processorType,
                                docRef,
                                null,
                                defaultExpression,
                                System.currentTimeMillis(),
                                null,
                                result -> {
                                    if (result != null) {
                                        // The runAsUser can't be changed in the editor
                                        refresh(result);
                                    }
                                });
            } else {
                processorEditPresenterProvider.get()
                        .show(processorType, docRef, filter, null, result -> {
                            if (result != null) {
                                // The runAsUser can't be changed in the editor
                                refresh(result);
                            }
                        });
            }
        }
    }

    private void removeProcessor() {
        final Selection<ProcessorListRow> selection = processorListPresenter.getSelectionModel().getSelection();
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
                                rows.size(), processorListPresenter::refresh);
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
        refresh(processorListPresenter.getSelectionModel().getSelected());
    }

    public void refresh(final ProcessorFilter processorFilter) {
        refresh(NullSafe.get(processorFilter, ProcessorFilterRow::new));
    }

    public void refresh(final ProcessorListRow row) {
        processorListPresenter.setNextSelection(row);
        processorListPresenter.refresh();

        if (row != null) {
            processorListPresenter.getSelectionModel().clear();
            processorListPresenter.getSelectionModel().setSelected(row, true);
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

        void setProcessorList(View view);

        void setInfo(SafeHtml info);

        void setDetailsView(View view);
    }
}
