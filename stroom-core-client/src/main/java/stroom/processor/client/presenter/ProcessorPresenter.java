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

package stroom.processor.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.event.ShowPermissionsDialogEvent;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.explorer.shared.ExplorerNode;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterResource;
import stroom.processor.shared.ProcessorFilterRow;
import stroom.processor.shared.ProcessorListRow;
import stroom.processor.shared.ProcessorType;
import stroom.processor.shared.QueryData;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.client.ExpressionTreePresenter;
import stroom.security.shared.DocPermissionResource;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.Objects;
import java.util.function.Supplier;

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

    private ProcessorType processorType;
    private DocRef docRef;
    private ProcessorListRow selectedProcessor;
    private ButtonView addButton;
    private ButtonView editButton;
    private ButtonView duplicateButton;
    private ButtonView removeButton;
    private ButtonView permissionsButton;

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
                              final ProcessorInfoBuilder processorInfoBuilder) {
        super(eventBus, view);
        this.processorListPresenter = processorListPresenter;
        this.processorEditPresenterProvider = processorEditPresenterProvider;
        this.expressionPresenter = expressionPresenter;
        this.restFactory = restFactory;
        this.processorInfoBuilder = processorInfoBuilder;

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

            if (isAdmin) {
                permissionsButton = processorListPresenter.getView().addButton(SvgPresets.LOCKED_AMBER);
                permissionsButton.setTitle("Permissions");
                registerHandler(permissionsButton.addClickHandler(event -> {
                    if (allowUpdate) {
                        setPermissions();
                    }
                }));
            }

            enableButtons(false);
        }
    }

    private void enableButtons(final boolean enabled) {
        if (addButton != null) {
            addButton.setEnabled(allowUpdate);
        }
        if (editButton != null) {
            if (allowUpdate) {
                editButton.setEnabled(enabled);
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
        if (permissionsButton != null) {
            if (allowUpdate) {
                permissionsButton.setEnabled(enabled);
            } else {
                permissionsButton.setEnabled(false);
            }
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
        selectedProcessor = processorListPresenter.getSelectionModel().getSelected();
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
        expressionPresenter.read(expression);
    }

    public MultiSelectionModel<ProcessorListRow> getSelectionModel() {
        return processorListPresenter.getSelectionModel();
    }

    private void addProcessor() {
        if (allowCreate) {
            edit(null, defaultExpression, null);
        }
    }

    /**
     * Make a copy of the currently selected processor
     */
    private void duplicateProcessor() {
        if (allowCreate) {
            // Now create the processor filter using the find stream criteria.
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
            edit(copy, null, null);
        }
    }

    private void editProcessor() {
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
                                edit(loadedFilter, null, processorFilterRow.getOwnerDisplayName());
                            }
                        })
                        .taskMonitorFactory(this)
                        .exec();
            }
        }
    }

    private void edit(final ProcessorFilter filter,
                      final ExpressionOperator defaultExpression,
                      final String ownerDisplayName) {
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
                                        // The owner can't be changed in the editor
                                        refresh(result, ownerDisplayName);
                                    }
                                });
            } else {
                processorEditPresenterProvider.get()
                        .show(processorType, docRef, filter, null, result -> {
                            if (result != null) {
                                // The owner can't be changed in the editor
                                refresh(result, ownerDisplayName);
                            }
                        });
            }
        }
    }

    private void removeProcessor() {
        if (selectedProcessor instanceof ProcessorFilterRow) {
            final ProcessorFilterRow processorFilterRow = (ProcessorFilterRow) selectedProcessor;
            ConfirmEvent.fire(this, "Are you sure you want to delete this filter?", result -> {
                if (result) {
                    restFactory
                            .create(PROCESSOR_FILTER_RESOURCE)
                            .method(res -> res.delete(processorFilterRow.getProcessorFilter().getId()))
                            .onSuccess(res -> processorListPresenter.refresh())
                            .taskMonitorFactory(this)
                            .exec();
                }
            });
        }
    }

    private void setPermissions() {
        if (selectedProcessor instanceof ProcessorFilterRow) {
            final ProcessorFilterRow processorFilterRow = (ProcessorFilterRow) selectedProcessor;
            final DocRef processorFilterRef = new DocRef(ProcessorFilter.ENTITY_TYPE,
                    processorFilterRow.getProcessorFilter().getUuid(),
                    null);
            final ExplorerNode explorerNode = ExplorerNode.builder()
                    .docRef(processorFilterRef)
                    .build();
            ShowPermissionsDialogEvent.fire(this, explorerNode);
        }
    }

    public void refresh(final ProcessorFilter processorFilter) {
        Objects.requireNonNull(processorFilter);
        restFactory
                .create(DOC_PERMISSION_RESOURCE)
                .method(res -> res.getDocumentOwners(processorFilter.getUuid()))
                .onSuccess(owners -> {
                    String ownerDisplayName;
                    if (owners == null || owners.size() == 0) {
                        ownerDisplayName = "Error: No owner";
                    } else if (owners.size() > 1) {
                        ownerDisplayName = "Error: Multiple owners";
                    } else {
                        ownerDisplayName = owners.get(0).getDisplayName();
                    }
                    refresh(processorFilter, ownerDisplayName);
                })
                .taskMonitorFactory(this)
                .exec();
    }

    public void refresh(final ProcessorFilter processorFilter, final String ownerDisplayName) {

        final ProcessorListRow processorListRow = new ProcessorFilterRow(processorFilter, ownerDisplayName);
        processorListPresenter.setNextSelection(processorListRow);
        processorListPresenter.refresh();

        processorListPresenter.getSelectionModel().clear();
        processorListPresenter.getSelectionModel().setSelected(processorListRow, true);
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
