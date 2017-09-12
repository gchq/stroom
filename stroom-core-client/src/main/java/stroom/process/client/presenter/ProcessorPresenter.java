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
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.DocRefs;
import stroom.entity.shared.DocumentServiceDeleteAction;
import stroom.entity.shared.DocumentServiceReadAction;
import stroom.entity.shared.DocumentServiceWriteAction;
import stroom.entity.shared.EntityIdSet;
import stroom.entity.shared.EntityReferenceComparator;
import stroom.entity.shared.NamedEntity;
import stroom.entity.shared.Period;
import stroom.feed.shared.Feed;
import stroom.pipeline.shared.PipelineEntity;
import stroom.process.shared.CreateProcessorAction;
import stroom.process.shared.LoadEntityIdSetAction;
import stroom.process.shared.SetId;
import stroom.process.shared.StreamProcessorFilterRow;
import stroom.process.shared.StreamProcessorRow;
import stroom.query.api.v1.DocRef;
import stroom.query.api.v1.ExpressionBuilder;
import stroom.query.api.v1.ExpressionOperator;
import stroom.query.api.v1.ExpressionOperator.Op;
import stroom.query.api.v1.ExpressionTerm.Condition;
import stroom.query.client.ExpressionTreePresenter;
import stroom.streamstore.client.presenter.StreamFilterPresenter;
import stroom.streamstore.shared.FindStreamAttributeMapCriteria;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.QueryData;
import stroom.streamstore.shared.StreamType;
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
    private final ExpressionTreePresenter expressionPresenter;
    private final StreamFilterPresenter streamFilterPresenter;
    private final ClientDispatchAsync dispatcher;

    private PipelineEntity pipelineEntity;
    private SharedObject selectedProcessor;
    private ButtonView addButton;
    private ButtonView editButton;
    private ButtonView removeButton;

    private boolean allowUpdate;

    @Inject
    public ProcessorPresenter(final EventBus eventBus, final ProcessorView view,
                              final ProcessorListPresenter processorListPresenter, final ExpressionTreePresenter expressionPresenter,
                              final StreamFilterPresenter streamFilterPresenter, final ClientDispatchAsync dispatcher) {
        super(eventBus, view);
        this.processorListPresenter = processorListPresenter;
        this.expressionPresenter = expressionPresenter;
        this.streamFilterPresenter = streamFilterPresenter;
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
            final FindStreamCriteria findStreamCriteria = streamProcessorFilter.getFindStreamCriteria();
            setData(findStreamCriteria);
        }
    }

    private void setData(final FindStreamCriteria findStreamCriteria) {
        expressionPresenter.read(null);
        // getView().setDetails("");

        if (findStreamCriteria != null) {
            if (findStreamCriteria.getQueryData() != null) {
                final QueryData queryData = findStreamCriteria.getQueryData();
                final ExpressionOperator expression = queryData.getExpression();
                final DocRef dataSourceRef = queryData.getDataSource();

                if (expression != null && dataSourceRef != null) {
                    dispatcher.exec(new DocumentServiceReadAction<NamedEntity>(dataSourceRef)).onSuccess(result -> {
                        final ExpressionBuilder builder = new ExpressionBuilder(Op.AND);
                        builder.addTerm("Data Source", Condition.EQUALS, result.getName());

                        builder.addOperator(expression);

                        expressionPresenter.read(builder.build());
                    });
                } else {
                    expressionPresenter.read(expression);
                }
            } else {
                final SetId feedIncludeSetId = new SetId("Feed Include", Feed.ENTITY_TYPE);
                final SetId feedExcludeSetId = new SetId("Feed Exclude", Feed.ENTITY_TYPE);
                final SetId streamTypeSetId = new SetId("Stream Type", StreamType.ENTITY_TYPE);

                final SharedMap<SetId, EntityIdSet<?>> entitySetMap = new SharedMap<>();
                if (findStreamCriteria.getFeeds() != null) {
                    if (findStreamCriteria.getFeeds().getInclude() != null) {
                        entitySetMap.put(feedIncludeSetId, findStreamCriteria.getFeeds().getInclude());
                    }
                    if (findStreamCriteria.getFeeds().getExclude() != null) {
                        entitySetMap.put(feedExcludeSetId, findStreamCriteria.getFeeds().getExclude());
                    }
                }
                entitySetMap.put(streamTypeSetId, findStreamCriteria.getStreamTypeIdSet());

                // Load entities.
                dispatcher.exec(new LoadEntityIdSetAction(entitySetMap)).onSuccess(result -> {
                    final DocRefs feedsInclude = result.get(feedIncludeSetId);
                    final DocRefs feedsExclude = result.get(feedExcludeSetId);
                    final DocRefs streamTypes = result.get(streamTypeSetId);

                    final ExpressionBuilder operator = new ExpressionBuilder(Op.AND);
                    addEntityListTerm(operator, feedsInclude, "Feed");

                    if (feedsExclude != null && feedsExclude.iterator().hasNext()) {
                        final ExpressionBuilder not = operator.addOperator(Op.NOT);
                        addEntityListTerm(not, feedsExclude, "Feed");
                    }

                    addEntityListTerm(operator, streamTypes, "Stream Type");
                    addIdTerm(operator, findStreamCriteria.getStreamIdSet(), "Stream Id");
                    addIdTerm(operator, findStreamCriteria.getParentStreamIdSet(), "Parent Stream Id");
                    addPeriodTerm(operator, findStreamCriteria.getCreatePeriod(), "Creation time");
                    addPeriodTerm(operator, findStreamCriteria.getEffectivePeriod(), "Effective time");

                    expressionPresenter.read(operator.build());
                });
            }
        }
    }

    private void addEntityListTerm(final ExpressionBuilder operator, final DocRefs entities,
                                   final String label) {
        if (entities != null) {
            final List<DocRef> list = new ArrayList<>(entities.getDoc());
            if (list.size() > 0) {
                if (list.size() > 1) {
                    final ExpressionBuilder or = operator.addOperator(Op.OR);

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

    private void addEntity(final ExpressionBuilder operator, final DocRef entity, final String label) {
        if (entity != null) {
            operator.addTerm(label, Condition.EQUALS, entity.getName());
        }
    }

    private void addIdTerm(final ExpressionBuilder operator, final EntityIdSet<?> entities, final String label) {
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

    private void addPeriodTerm(final ExpressionBuilder operator, final Period period, final String label) {
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

    private void addOrEditProcessor(final StreamProcessorFilter filter) {
        final FindStreamAttributeMapCriteria criteria = new FindStreamAttributeMapCriteria();

        if (filter != null && filter.getFindStreamCriteria() != null) {
            criteria.obtainFindStreamCriteria().copyFrom(filter.getFindStreamCriteria());
        }

        streamFilterPresenter.setCriteria(criteria, true, true, false);

        final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                if (ok) {
                    streamFilterPresenter.write();
                    final FindStreamAttributeMapCriteria criteria = streamFilterPresenter.getCriteria();
                    final FindStreamCriteria findStreamCriteria = criteria.obtainFindStreamCriteria();

                    if (filter != null) {
                        ConfirmEvent.fire(ProcessorPresenter.this,
                                "You are about to update an existing filter. Any streams that might now be included by this filter but are older than the current tracker position will not be processed. Are you sure you wish to do this?",
                                result -> {
                                    if (result) {
                                        validateFeed(filter, findStreamCriteria);
                                    }
                                });
                    } else {
                        validateFeed(null, findStreamCriteria);
                    }

                } else {
                    HidePopupEvent.fire(ProcessorPresenter.this, streamFilterPresenter);
                }
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
                // Do nothing.
            }
        };

        // Show the processor creation dialog.
        final PopupSize popupSize = new PopupSize(412, 600, 412, 600, true);
        if (filter != null) {
            ShowPopupEvent.fire(this, streamFilterPresenter, PopupType.OK_CANCEL_DIALOG, popupSize, "Edit Filter",
                    popupUiHandlers);
        } else {
            ShowPopupEvent.fire(this, streamFilterPresenter, PopupType.OK_CANCEL_DIALOG, popupSize, "Add Filter",
                    popupUiHandlers);
        }
    }

    private void validateFeed(final StreamProcessorFilter filter, final FindStreamCriteria findStreamCriteria) {
        if (findStreamCriteria.obtainStreamIdSet().size() == 0
                && findStreamCriteria.obtainParentStreamIdSet().size() == 0
                && findStreamCriteria.obtainFeeds().obtainInclude().getSet().size() == 0
                && findStreamCriteria.obtainFeeds().obtainExclude().getSet().size() == 0) {
            ConfirmEvent.fire(ProcessorPresenter.this,
                    "You are about to process all feeds. Are you sure you wish to do this?", result -> {
                        if (result) {
                            validateStreamType(filter, findStreamCriteria);
                        }
                    });
        } else {
            validateStreamType(filter, findStreamCriteria);
        }
    }

    private void validateStreamType(final StreamProcessorFilter filter, final FindStreamCriteria findStreamCriteria) {
        if (findStreamCriteria.obtainStreamIdSet().size() == 0
                && findStreamCriteria.obtainParentStreamIdSet().size() == 0
                && findStreamCriteria.obtainStreamTypeIdSet().size() == 0) {
            ConfirmEvent.fire(ProcessorPresenter.this,
                    "You are about to process all stream types. Are you sure you wish to do this?",
                    result -> {
                        if (result) {
                            createOrUpdateProcessor(filter, findStreamCriteria);
                        }
                    });
        } else {
            createOrUpdateProcessor(filter, findStreamCriteria);
        }
    }

    private void createOrUpdateProcessor(final StreamProcessorFilter filter,
                                         final FindStreamCriteria findStreamCriteria) {
        if (filter != null) {
            // Now update the processor filter using the find stream criteria.
            filter.setFindStreamCriteria(findStreamCriteria);
            dispatcher.exec(new DocumentServiceWriteAction<StreamProcessorFilter>(filter)).onSuccess(result -> {
                refresh(result);
                HidePopupEvent.fire(ProcessorPresenter.this, streamFilterPresenter);
            });

        } else {
            // Now create the processor filter using the find stream criteria.
            dispatcher.exec(new CreateProcessorAction(DocRefUtil.create(pipelineEntity), findStreamCriteria, false, 10)).onSuccess(result -> {
                refresh(result);
                HidePopupEvent.fire(ProcessorPresenter.this, streamFilterPresenter);
            });
        }
    }

    private void removeProcessor() {
        if (selectedProcessor != null) {
            if (selectedProcessor instanceof StreamProcessorRow) {
                final StreamProcessorRow streamProcessorRow = (StreamProcessorRow) selectedProcessor;
                ConfirmEvent.fire(this, "Are you sure you want to delete this processor?", result -> {
                    if (result) {
                        dispatcher.exec(new DocumentServiceDeleteAction(streamProcessorRow.getEntity())).onSuccess(res -> processorListPresenter.refresh());
                    }
                });
            } else if (selectedProcessor instanceof StreamProcessorFilterRow) {
                final StreamProcessorFilterRow streamProcessorFilterRow = (StreamProcessorFilterRow) selectedProcessor;
                ConfirmEvent.fire(this, "Are you sure you want to delete this filter?", result -> {
                    if (result) {
                        dispatcher.exec(new DocumentServiceDeleteAction(streamProcessorFilterRow.getEntity())).onSuccess(res -> processorListPresenter.refresh());
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
