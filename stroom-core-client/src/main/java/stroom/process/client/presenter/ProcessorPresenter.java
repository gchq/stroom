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

package stroom.process.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.entity.shared.EntityServiceDeleteAction;
import stroom.entity.shared.EntityServiceSaveAction;
import stroom.pipeline.shared.PipelineDoc;
import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.client.ExpressionTreePresenter;
import stroom.streamstore.shared.QueryData;
import stroom.streamstore.shared.StreamDataSource;
import stroom.streamtask.shared.CreateProcessorAction;
import stroom.streamtask.shared.StreamProcessorFilter;
import stroom.streamtask.shared.StreamProcessorFilterRow;
import stroom.streamtask.shared.StreamProcessorRow;
import stroom.svg.client.SvgPresets;
import stroom.docref.SharedObject;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.util.client.MultiSelectionModel;

public class ProcessorPresenter extends MyPresenterWidget<ProcessorPresenter.ProcessorView>
        implements HasDocumentRead<SharedObject> {
    private final ProcessorListPresenter processorListPresenter;
    private final ExpressionPresenter filterPresenter;
    private final ExpressionTreePresenter expressionPresenter;
    private final ClientDispatchAsync dispatcher;

    private DocRef docRef;
    private PipelineDoc pipelineDoc;
    private SharedObject selectedProcessor;
    private ButtonView addButton;
    private ButtonView editButton;
    private ButtonView removeButton;

    private boolean allowUpdate;

    @Inject
    public ProcessorPresenter(final EventBus eventBus,
                              final ProcessorView view,
                              final ProcessorListPresenter processorListPresenter,
                              final ExpressionPresenter filterPresenter,
                              final ExpressionTreePresenter expressionPresenter,
                              final ClientDispatchAsync dispatcher) {
        super(eventBus, view);
        this.processorListPresenter = processorListPresenter;
        this.filterPresenter = filterPresenter;
        this.expressionPresenter = expressionPresenter;
        this.dispatcher = dispatcher;

        // Stop users from selecting expression items.
        expressionPresenter.setSelectionModel(null);

        view.setProcessorList(processorListPresenter.getView());
        view.setDetailsView(expressionPresenter.getView());
    }

    @Override
    public void read(final DocRef docRef, final SharedObject entity) {
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
            removeButton = processorListPresenter.getView().addButton(SvgPresets.REMOVE);
            removeButton.setTitle("Remove Processor");
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

        if (selectedProcessor == null) {
            enableButtons(false);
            setData(null);

        } else if (selectedProcessor instanceof StreamProcessorRow) {
            enableButtons(true);
            setData(null);

        } else if (selectedProcessor instanceof StreamProcessorFilterRow) {
            enableButtons(true);

            final StreamProcessorFilterRow row = (StreamProcessorFilterRow) selectedProcessor;
            final StreamProcessorFilter streamProcessorFilter = row.getEntity();
            final QueryData queryData = streamProcessorFilter.getQueryData();
            setData(queryData);
        }
    }

    private void setData(final QueryData queryData) {
        ExpressionOperator expression = null;
        if (queryData != null && queryData.getExpression() != null) {
            expression = queryData.getExpression();
        }

        expressionPresenter.read(expression);
    }

    public MultiSelectionModel<SharedObject> getSelectionModel() {
        return processorListPresenter.getSelectionModel();
    }

    private void addProcessor() {
        if (pipelineDoc != null) {
            addOrEditProcessor(null);
        }
    }

    private void editProcessor() {
        if (pipelineDoc != null && selectedProcessor != null) {
            if (selectedProcessor instanceof StreamProcessorFilterRow) {
                final StreamProcessorFilterRow streamProcessorFilterRow = (StreamProcessorFilterRow) selectedProcessor;
                final StreamProcessorFilter filter = streamProcessorFilterRow.getEntity();
                addOrEditProcessor(filter);
            }
        }
    }

    private void addOrEditProcessor(final StreamProcessorFilter filter) {
        final QueryData queryData = getOrCreateQueryData(filter);
        filterPresenter.read(queryData.getExpression(), StreamDataSource.STREAM_STORE_DOC_REF, StreamDataSource.getFields());

        final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                if (ok) {
                    final ExpressionOperator expression = filterPresenter.write();
                    queryData.setDataSource(StreamDataSource.STREAM_STORE_DOC_REF);
                    queryData.setExpression(expression);

                    if (filter != null) {
                        ConfirmEvent.fire(ProcessorPresenter.this,
                                "You are about to update an existing filter. Any streams that might now be included by this filter but are older than the current tracker position will not be processed. Are you sure you wish to do this?",
                                result -> {
                                    if (result) {
                                        validateFeed(filter, queryData);
                                    }
                                });
                    } else {
                        validateFeed(null, queryData);
                    }

                } else {
                    HidePopupEvent.fire(ProcessorPresenter.this, filterPresenter);
                }
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
                // Do nothing.
            }
        };

        // Show the processor creation dialog.
        final PopupSize popupSize = new PopupSize(800, 600, 400, 400, true);
        if (filter != null) {
            ShowPopupEvent.fire(this, filterPresenter, PopupType.OK_CANCEL_DIALOG, popupSize, "Edit Filter",
                    popupUiHandlers);
        } else {
            ShowPopupEvent.fire(this, filterPresenter, PopupType.OK_CANCEL_DIALOG, popupSize, "Add Filter",
                    popupUiHandlers);
        }
    }

    private QueryData getOrCreateQueryData(final StreamProcessorFilter filter) {
        if (filter != null && filter.getQueryData() != null) {
            return filter.getQueryData();
        }
        return new QueryData();
    }

    private void validateFeed(final StreamProcessorFilter filter, final QueryData queryData) {
        final int feedCount = termCount(queryData, StreamDataSource.FEED);
        final int streamIdCount = termCount(queryData, StreamDataSource.STREAM_ID);
        final int parentStreamIdCount = termCount(queryData, StreamDataSource.PARENT_STREAM_ID);

        if (streamIdCount == 0
                && parentStreamIdCount == 0
                && feedCount == 0) {
            ConfirmEvent.fire(ProcessorPresenter.this,
                    "You are about to process all feeds. Are you sure you wish to do this?", result -> {
                        if (result) {
                            validateStreamType(filter, queryData);
                        }
                    });
        } else {
            createOrUpdateProcessor(filter, queryData);
        }
    }

    private void validateStreamType(final StreamProcessorFilter filter, final QueryData queryData) {
        final int streamTypeCount = termCount(queryData, StreamDataSource.STREAM_TYPE);
        final int streamIdCount = termCount(queryData, StreamDataSource.STREAM_ID);
        final int parentStreamIdCount = termCount(queryData, StreamDataSource.PARENT_STREAM_ID);

        if (streamIdCount == 0
                && parentStreamIdCount == 0
                && streamTypeCount == 0) {
            ConfirmEvent.fire(ProcessorPresenter.this,
                    "You are about to process all stream types. Are you sure you wish to do this?",
                    result -> {
                        if (result) {
                            createOrUpdateProcessor(filter, queryData);
                        }
                    });
        } else {
            createOrUpdateProcessor(filter, queryData);
        }
    }

    private int termCount(final QueryData queryData, final String field) {
        if (queryData == null || queryData.getExpression() == null) {
            return 0;
        }
        return termCount(queryData.getExpression(), field);
    }

    private int termCount(final ExpressionOperator expressionOperator, final String field) {
        int count = 0;
        if (expressionOperator.enabled()) {
            for (final ExpressionItem item : expressionOperator.getChildren()) {
                if (item.enabled()) {
                    if (item instanceof ExpressionTerm) {
                        if (field.equals(((ExpressionTerm) item).getField())) {
                            count++;
                        }
                    } else if (item instanceof ExpressionOperator) {
                        count += termCount((ExpressionOperator) item, field);
                    }
                }
            }
        }
        return count;
    }

    private void createOrUpdateProcessor(final StreamProcessorFilter filter,
                                         final QueryData queryData) {
        if (filter != null) {
            // Now update the processor filter using the find stream criteria.
            filter.setQueryData(queryData);
            dispatcher.exec(new EntityServiceSaveAction<>(filter)).onSuccess(result -> {
                refresh(result);
                HidePopupEvent.fire(ProcessorPresenter.this, filterPresenter);
            });

        } else {
            // Now create the processor filter using the find stream criteria.
            dispatcher.exec(new CreateProcessorAction(docRef, queryData, false, 10)).onSuccess(result -> {
                refresh(result);
                HidePopupEvent.fire(ProcessorPresenter.this, filterPresenter);
            });
        }
    }

    private void removeProcessor() {
        if (selectedProcessor != null) {
            if (selectedProcessor instanceof StreamProcessorRow) {
                final StreamProcessorRow streamProcessorRow = (StreamProcessorRow) selectedProcessor;
                ConfirmEvent.fire(this, "Are you sure you want to delete this processor?", result -> {
                    if (result) {
                        dispatcher.exec(new EntityServiceDeleteAction(streamProcessorRow.getEntity())).onSuccess(res -> processorListPresenter.refresh());
                    }
                });
            } else if (selectedProcessor instanceof StreamProcessorFilterRow) {
                final StreamProcessorFilterRow streamProcessorFilterRow = (StreamProcessorFilterRow) selectedProcessor;
                ConfirmEvent.fire(this, "Are you sure you want to delete this filter?", result -> {
                    if (result) {
                        dispatcher.exec(new EntityServiceDeleteAction(streamProcessorFilterRow.getEntity())).onSuccess(res -> processorListPresenter.refresh());
                    }
                });
            }
        }
    }

    public void refresh(final StreamProcessorFilter streamProcessorFilter) {
        processorListPresenter.setNextSelection(streamProcessorFilter);
        processorListPresenter.refresh();

        processorListPresenter.getSelectionModel().clear();
        processorListPresenter.getSelectionModel().setSelected(streamProcessorFilter, true);
        updateData();
    }

    public interface ProcessorView extends View {
        void setProcessorList(View view);

        void setDetailsView(View view);
    }
}
