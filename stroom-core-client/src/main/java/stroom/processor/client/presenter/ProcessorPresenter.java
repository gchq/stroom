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
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.shared.CreateProcessFilterRequest;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterResource;
import stroom.processor.shared.ProcessorFilterRow;
import stroom.processor.shared.ProcessorListRow;
import stroom.processor.shared.QueryData;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.client.ExpressionTreePresenter;
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

public class ProcessorPresenter extends MyPresenterWidget<ProcessorPresenter.ProcessorView>
        implements HasDocumentRead<Object> {

    private static final ProcessorFilterResource PROCESSOR_FILTER_RESOURCE = GWT.create(ProcessorFilterResource.class);

    private final ProcessorListPresenter processorListPresenter;
    private final Provider<ProcessorEditPresenter> processorEditPresenterProvider;
    private final ExpressionTreePresenter expressionPresenter;
    private final RestFactory restFactory;
    private final ProcessorInfoBuilder processorInfoBuilder;

    private DocRef docRef;
    private PipelineDoc pipelineDoc;
    private ProcessorListRow selectedProcessor;
    private ButtonView addButton;
    private ButtonView editButton;
    private ButtonView cloneButton;
    private ButtonView removeButton;

    private boolean allowUpdate;

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
    public void read(final DocRef docRef, final Object entity) {
        this.docRef = docRef;
        processorListPresenter.read(docRef, entity);
        if (entity instanceof PipelineDoc) {
            this.pipelineDoc = (PipelineDoc) entity;
        }
    }

    public void setAllowUpdate(final boolean allowUpdate) {
        this.allowUpdate = allowUpdate;

        if (this.pipelineDoc != null && allowUpdate) {
            createButtons();
        }

        processorListPresenter.setAllowUpdate(allowUpdate);
    }

    private void createButtons() {
        if (addButton == null && removeButton == null) {
            addButton = processorListPresenter.getView().addButton(SvgPresets.ADD);
            addButton.setTitle("Add Processor");
            editButton = processorListPresenter.getView().addButton(SvgPresets.EDIT);
            editButton.setTitle("Edit Processor");
            cloneButton = processorListPresenter.getView().addButton(SvgPresets.COPY);
            cloneButton.setTitle("Clone Processor");
            removeButton = processorListPresenter.getView().addButton(SvgPresets.DELETE);
            removeButton.setTitle("Delete Processor");
            registerHandler(addButton.addClickHandler(event -> {
                if (allowUpdate) {
                    addProcessor();
                }
            }));
            registerHandler(editButton.addClickHandler(event -> {
                if (allowUpdate) {
                    editProcessor();
                }
            }));
            registerHandler(cloneButton.addClickHandler(event -> {
                if (allowUpdate) {
                    cloneProcessor();
                }
            }));
            registerHandler(removeButton.addClickHandler(event -> {
                if (allowUpdate) {
                    removeProcessor();
                }
            }));

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
        if (cloneButton != null) {
            if (allowUpdate) {
                cloneButton.setEnabled(enabled);
            } else {
                cloneButton.setEnabled(false);
            }
        }
        if (removeButton != null) {
            if (allowUpdate) {
                removeButton.setEnabled(enabled);
            } else {
                removeButton.setEnabled(false);
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
        if (pipelineDoc != null) {
            edit(null);
        }
    }

    /**
     * Make a copy of the currently selected processor
     */
    private void cloneProcessor() {
        if (pipelineDoc != null) {
            // Now create the processor filter using the find stream criteria.
            final ProcessorFilterRow row = (ProcessorFilterRow) selectedProcessor;
            final ProcessorFilter processorFilter = row.getProcessorFilter();
            final CreateProcessFilterRequest request = CreateProcessFilterRequest
                    .builder()
                    .pipeline(row.getProcessorFilter().getPipeline())
                    .queryData(processorFilter.getQueryData())
                    .priority(processorFilter.getPriority())
                    .autoPriority(true)
                    .enabled(false)
                    .build();
            final Rest<ProcessorFilter> rest = restFactory.create();
            rest.onSuccess(result -> processorListPresenter.refresh()).call(PROCESSOR_FILTER_RESOURCE).create(request);
        }
    }

    private void editProcessor() {
        if (pipelineDoc != null && selectedProcessor != null) {
            if (selectedProcessor instanceof ProcessorFilterRow) {
                final ProcessorFilterRow processorFilterRow = (ProcessorFilterRow) selectedProcessor;
                final ProcessorFilter filter = processorFilterRow.getProcessorFilter();

                final Rest<ProcessorFilter> rest = restFactory.create();
                rest
                        .onSuccess(loadedFilter -> {
                            if (loadedFilter == null) {
                                AlertEvent.fireError(
                                        ProcessorPresenter.this,
                                        "Unable to load filter",
                                        null);
                            } else {
                                edit(loadedFilter);
                            }
                        })
                        .call(PROCESSOR_FILTER_RESOURCE)
                        .fetch(filter.getId());
            }
        }
    }

    private void edit(final ProcessorFilter filter) {
        processorEditPresenterProvider.get().show(docRef, filter, result -> {
            if (result != null) {
                refresh(result);
            }
        });
    }

    private void removeProcessor() {
        if (selectedProcessor != null) {
//            if (selectedProcessor instanceof ProcessorRow) {
//                final ProcessorRow streamProcessorRow = (ProcessorRow) selectedProcessor;
//                ConfirmEvent.fire(this, "Are you sure you want to delete this processor?", result -> {
//                    if (result) {
//                        final Rest<Processor> rest = restFactory.create();
//                        rest
//                        .onSuccess(res ->
//                        processorListPresenter.refresh())
//                        .call(PROCESSOR_RESOURCE)
//                        .delete(streamProcessorRow.getProcessor().getId());
//                    }
//                });
//            } else
//
            if (selectedProcessor instanceof ProcessorFilterRow) {
                final ProcessorFilterRow processorFilterRow = (ProcessorFilterRow) selectedProcessor;
                ConfirmEvent.fire(this, "Are you sure you want to delete this filter?", result -> {
                    if (result) {
                        final Rest<ProcessorFilter> rest = restFactory.create();
                        rest
                                .onSuccess(res -> processorListPresenter.refresh())
                                .call(PROCESSOR_FILTER_RESOURCE)
                                .delete(processorFilterRow.getProcessorFilter().getId());
                    }
                });
            }
        }
    }

    public void refresh(final ProcessorFilter processorFilter) {
        final ProcessorListRow processorListRow = new ProcessorFilterRow(processorFilter);
        processorListPresenter.setNextSelection(processorListRow);
        processorListPresenter.refresh();

        processorListPresenter.getSelectionModel().clear();
        processorListPresenter.getSelectionModel().setSelected(processorListRow, true);
        updateData();
    }

    public interface ProcessorView extends View {

        void setProcessorList(View view);

        void setInfo(SafeHtml info);

        void setDetailsView(View view);
    }
}
