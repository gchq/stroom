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
import stroom.entity.client.presenter.HasRead;
import stroom.entity.shared.*;
import stroom.feed.shared.Feed;
import stroom.pipeline.shared.PipelineEntity;
import stroom.process.shared.CreateProcessorAction;
import stroom.process.shared.LoadEntityIdSetAction;
import stroom.process.shared.SetId;
import stroom.process.shared.StreamProcessorFilterRow;
import stroom.process.shared.StreamProcessorRow;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.client.ExpressionTreePresenter;
import stroom.ruleset.client.presenter.EditExpressionPresenter;
import stroom.streamstore.shared.*;
import stroom.streamtask.shared.StreamProcessorFilter;
import stroom.svg.client.SvgPresets;
import stroom.util.shared.SharedMap;
import stroom.util.shared.SharedObject;
import stroom.widget.button.client.ButtonView;
import stroom.widget.customdatebox.client.ClientDateUtil;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.util.client.MultiSelectionModel;

import java.util.ArrayList;
import java.util.List;

public class ProcessorPresenter extends MyPresenterWidget<ProcessorPresenter.ProcessorView>
        implements HasRead<BaseEntity> {
    private final ProcessorListPresenter processorListPresenter;
    private final EditExpressionPresenter editExpressionPresenter;
    private final ExpressionTreePresenter expressionPresenter;
    private final ClientDispatchAsync dispatcher;

    private PipelineEntity pipelineEntity;
    private SharedObject selectedProcessor;
    private ButtonView addButton;
    private ButtonView editButton;
    private ButtonView removeButton;

    private boolean allowUpdate;

    @Inject
    public ProcessorPresenter(final EventBus eventBus,
                              final ProcessorView view,
                              final ProcessorListPresenter processorListPresenter,
                              final EditExpressionPresenter editExpressionPresenter,
                              final ExpressionTreePresenter expressionPresenter,
                              final ClientDispatchAsync dispatcher) {
        super(eventBus, view);
        this.processorListPresenter = processorListPresenter;
        this.editExpressionPresenter = editExpressionPresenter;
        this.expressionPresenter = expressionPresenter;
        this.dispatcher = dispatcher;

        // Stop users from selecting expression items.
        expressionPresenter.setSelectionModel(null);

        view.setProcessorList(processorListPresenter.getView());
        view.setDetailsView(expressionPresenter.getView());
    }

    @Override
    public void read(final BaseEntity entity) {
        processorListPresenter.read(entity);
        if (entity instanceof PipelineEntity) {
            this.pipelineEntity = (PipelineEntity) entity;
        }
    }

    public void setAllowUpdate(final boolean allowUpdate) {
        this.allowUpdate = allowUpdate;

        if (this.pipelineEntity != null && allowUpdate) {
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
        expressionPresenter.read(null);
        // getView().setDetails("");

        final ExpressionOperator expression = queryData.getExpression();
        expressionPresenter.read(expression);
    }

    private void addEntityListTerm(final ExpressionOperator.ABuilder<?, ?> operator, final DocRefs entities,
                                   final String label) {
        if (entities != null) {
            final List<DocRef> list = new ArrayList<>(entities.getDoc());
            if (list.size() > 0) {
                if (list.size() > 1) {
                    final ExpressionOperator.OBuilder<?> or = operator.addOperator(Op.OR);

                    list.sort(new EntityReferenceComparator());
                    for (final DocRef entity : list) {
                        addEntity(or, entity, label);
                    }

                } else {
                    final DocRef entity = list.get(0);
                    addEntity(operator, entity, label);
                }
            }
        }
    }

    private void addEntity(final ExpressionOperator.ABuilder<?, ?> operator,
                           final DocRef entity,
                           final String label) {
        if (entity != null) {
            operator.addTerm(label, Condition.EQUALS, entity.getName());
        }
    }

    private void addIdTerm(final ExpressionOperator.ABuilder<?, ?> operator,
                           final EntityIdSet<?> entities,
                           final String label) {
        if (entities != null && entities.size() > 0) {
            Condition condition = Condition.EQUALS;
            if (entities.size() > 1) {
                condition = Condition.IN;
            }

            final StringBuilder sb = new StringBuilder();
            for (final Long id : entities) {
                if (id != null) {
                    sb.append(id.toString());
                    sb.append(",");
                }
            }
            if (sb.length() > 0) {
                sb.setLength(sb.length() - 1);
            }

            operator.addTerm(label, condition, sb.toString());
        }
    }

    private void addPeriodTerm(final ExpressionOperator.ABuilder<?, ?> operator,
                               final Period period,
                               final String label) {
        if (period != null && (period.getFrom() != null || period.getTo() != null)) {
            Condition condition = null;

            final StringBuilder sb = new StringBuilder();
            if (period.getFrom() != null && period.getTo() != null) {
                condition = Condition.BETWEEN;
                sb.append(ClientDateUtil.toISOString(period.getFrom()));
                sb.append(" and ");
                sb.append(ClientDateUtil.toISOString(period.getTo()));
            } else if (period.getFrom() != null) {
                condition = Condition.GREATER_THAN;
                sb.append(ClientDateUtil.toISOString(period.getFrom()));
            } else if (period.getTo() != null) {
                condition = Condition.LESS_THAN;
                sb.append(ClientDateUtil.toISOString(period.getTo()));
            }

            operator.addTerm(label, condition, sb.toString());
        }
    }

    public MultiSelectionModel<SharedObject> getSelectionModel() {
        return processorListPresenter.getSelectionModel();
    }

    private void addProcessor() {
        if (pipelineEntity != null) {
            addOrEditProcessor(null);
        }
    }

    private void editProcessor() {
        if (pipelineEntity != null && selectedProcessor != null) {
            if (selectedProcessor instanceof StreamProcessorFilterRow) {
                final StreamProcessorFilterRow streamProcessorFilterRow = (StreamProcessorFilterRow) selectedProcessor;
                final StreamProcessorFilter filter = streamProcessorFilterRow.getEntity();
                addOrEditProcessor(filter);
            }
        }
    }

    private ExpressionOperator getExpressionFromQueryData(final StreamProcessorFilter filter) {
        if (null != filter) {
            final QueryData queryData = filter.getQueryData();
            if (null != queryData) {
                return queryData.getExpression();
            }
        }

        return new ExpressionOperator.Builder(Op.AND).build();
    }

    private QueryData getQueryDataFromExpression(final ExpressionOperator expressionOperator) {
        final QueryData queryData = new QueryData();
        queryData.setExpression(expressionOperator);
        queryData.setDataSource(QueryData.STREAM_STORE_DOC_REF);
        return queryData;
    }

    private void addOrEditProcessor(final StreamProcessorFilter filter) {

        // Give it a default rule

        editExpressionPresenter.init(null, null, FindStreamDataSource.getFields());
        editExpressionPresenter.read(getExpressionFromQueryData(filter));

        final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                if (ok) {
                    final ExpressionOperator findStreamExpression = editExpressionPresenter.write();
                    final QueryData queryData = getQueryDataFromExpression(findStreamExpression);

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
                    HidePopupEvent.fire(ProcessorPresenter.this, editExpressionPresenter);
                }
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
                // Do nothing.
            }
        };

        // Show the processor creation dialog.
        final PopupSize popupSize = new PopupSize(800, 600, 412, 600, true);
        if (filter != null) {
            ShowPopupEvent.fire(this, editExpressionPresenter, PopupType.OK_CANCEL_DIALOG, popupSize, "Edit Filter",
                    popupUiHandlers);
        } else {
            ShowPopupEvent.fire(this, editExpressionPresenter, PopupType.OK_CANCEL_DIALOG, popupSize, "Add Filter",
                    popupUiHandlers);
        }
    }

    private void validateFeed(final StreamProcessorFilter filter, final QueryData queryData) {
        /*if (findStreamCriteria.obtainStreamIdSet().size() == 0
                && findStreamCriteria.obtainParentStreamIdSet().size() == 0
                && findStreamCriteria.obtainFeeds().obtainInclude().getSet().size() == 0
                && findStreamCriteria.obtainFeeds().obtainExclude().getSet().size() == 0) {
            ConfirmEvent.fire(ProcessorPresenter.this,
                    "You are about to process all feeds. Are you sure you wish to do this?", result -> {
                        if (result) {
                            validateStreamType(filter, findStreamCriteria);
                        }
                    });
        } else {*/
        // TODO I will need to trawl through the expression and check how greedy it is
        createOrUpdateProcessor(filter, queryData);
        //}
    }

    private void createOrUpdateProcessor(final StreamProcessorFilter filter,
                                         final QueryData queryData) {
        if (filter != null) {
            // Now update the processor filter using the find stream criteria.
            filter.setQueryData(queryData);
            dispatcher.exec(new EntityServiceSaveAction<>(filter)).onSuccess(result -> {
                refresh(result);
                HidePopupEvent.fire(ProcessorPresenter.this, editExpressionPresenter);
            });

        } else {
            // Now create the processor filter using the find stream criteria.
            dispatcher.exec(new CreateProcessorAction(DocRefUtil.create(pipelineEntity), queryData, false, 10)).onSuccess(result -> {
                refresh(result);
                HidePopupEvent.fire(ProcessorPresenter.this, editExpressionPresenter);
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
