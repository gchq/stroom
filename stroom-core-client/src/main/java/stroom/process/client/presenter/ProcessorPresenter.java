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

package stroom.process.client.presenter;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.ConfirmEvent;
import stroom.alert.client.presenter.ConfirmCallback;
import stroom.dispatch.client.AsyncCallbackAdaptor;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.presenter.HasRead;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.DocRef;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.EntityIdSet;
import stroom.entity.shared.EntityReferenceComparator;
import stroom.entity.shared.EntityServiceDeleteAction;
import stroom.entity.shared.EntityServiceLoadAction;
import stroom.entity.shared.EntityServiceSaveAction;
import stroom.entity.shared.Folder;
import stroom.entity.shared.NamedEntity;
import stroom.entity.shared.Period;
import stroom.feed.shared.Feed;
import stroom.pipeline.shared.PipelineEntity;
import stroom.process.shared.CreateProcessorAction;
import stroom.process.shared.LoadEntityIdSetAction;
import stroom.process.shared.SetId;
import stroom.process.shared.StreamProcessorFilterRow;
import stroom.process.shared.StreamProcessorRow;
import stroom.query.client.ExpressionTreePresenter;
import stroom.query.shared.Condition;
import stroom.query.shared.ExpressionOperator;
import stroom.query.shared.ExpressionOperator.Op;
import stroom.query.shared.ExpressionTerm;
import stroom.query.shared.QueryData;
import stroom.security.client.ClientSecurityContext;
import stroom.streamstore.client.presenter.StreamFilterPresenter;
import stroom.streamstore.shared.FindStreamAttributeMapCriteria;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.StreamType;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamProcessorFilter;
import stroom.util.shared.SharedList;
import stroom.util.shared.SharedMap;
import stroom.util.shared.SharedObject;
import stroom.widget.button.client.GlyphButtonView;
import stroom.widget.button.client.GlyphIcons;
import stroom.widget.customdatebox.client.ClientDateUtil;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.util.client.MultiSelectionModel;

import java.util.Collections;

public class ProcessorPresenter extends MyPresenterWidget<ProcessorPresenter.ProcessorView>
        implements HasRead<BaseEntity> {
    private final ProcessorListPresenter processorListPresenter;
    private final ExpressionTreePresenter expressionPresenter;
    private final StreamFilterPresenter streamFilterPresenter;
    private final ClientDispatchAsync dispatcher;
    private final ClientSecurityContext securityContext;

    private PipelineEntity pipelineEntity;
    private SharedObject selectedProcessor;
    private GlyphButtonView addButton;
    private GlyphButtonView editButton;
    private GlyphButtonView removeButton;

    @Inject
    public ProcessorPresenter(final EventBus eventBus, final ProcessorView view,
                              final ProcessorListPresenter processorListPresenter, final ExpressionTreePresenter expressionPresenter,
                              final StreamFilterPresenter streamFilterPresenter, final ClientDispatchAsync dispatcher,
                              final ClientSecurityContext securityContext) {
        super(eventBus, view);
        this.processorListPresenter = processorListPresenter;
        this.expressionPresenter = expressionPresenter;
        this.streamFilterPresenter = streamFilterPresenter;
        this.dispatcher = dispatcher;
        this.securityContext = securityContext;

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
            createButtons();
        }
    }

    private void createButtons() {
        if (addButton == null && removeButton == null) {
            addButton = processorListPresenter.getView().addButton(GlyphIcons.ADD);
            addButton.setTitle("Add Processor");
            editButton = processorListPresenter.getView().addButton(GlyphIcons.EDIT);
            editButton.setTitle("Edit Processor");
            removeButton = processorListPresenter.getView().addButton(GlyphIcons.REMOVE);
            removeButton.setTitle("Remove Processor");
            registerHandler(addButton.addClickHandler(new ClickHandler() {
                @Override
                public void onClick(final ClickEvent event) {
                    if (allowUpdate()) {
                        addProcessor();
                    }
                }
            }));
            registerHandler(editButton.addClickHandler(new ClickHandler() {
                @Override
                public void onClick(final ClickEvent event) {
                    if (allowUpdate()) {
                        editProcessor();
                    }
                }
            }));
            registerHandler(removeButton.addClickHandler(new ClickHandler() {
                @Override
                public void onClick(final ClickEvent event) {
                    if (allowUpdate()) {
                        removeProcessor();
                    }
                }
            }));

            enableButtons(false);
        }
    }

    private boolean allowUpdate() {
        return securityContext.hasAppPermission(StreamProcessor.MANAGE_PROCESSORS_PERMISSION);
    }

    private void enableButtons(final boolean enabled) {
        if (addButton != null) {
            addButton.setEnabled(allowUpdate());
        }
        if (editButton != null) {
            if (allowUpdate()) {
                editButton.setEnabled(enabled);
            } else {
                editButton.setEnabled(false);
            }
        }
        if (removeButton != null) {
            if (allowUpdate()) {
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
                if (allowUpdate()) {
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
                    dispatcher.execute(new EntityServiceLoadAction<NamedEntity>(dataSourceRef, null), new AsyncCallbackAdaptor<NamedEntity>() {
                        @Override
                        public void onSuccess(final NamedEntity result) {
                            final ExpressionTerm ds = new ExpressionTerm();
                            ds.setField("Data Source");
                            ds.setCondition(Condition.EQUALS);
                            ds.setValue(result.getName());

                            final ExpressionOperator operator = new ExpressionOperator(Op.AND);
                            operator.addChild(ds);
                            operator.addChild(expression);

                            expressionPresenter.read(operator);
                        }
                    });
                } else {
                    expressionPresenter.read(expression);
                }
            } else {
                final SetId folderSetId = new SetId("Folder", Folder.ENTITY_TYPE);
                final SetId feedIncludeSetId = new SetId("Feed Include", Feed.ENTITY_TYPE);
                final SetId feedExcludeSetId = new SetId("Feed Exclude", Feed.ENTITY_TYPE);
                final SetId streamTypeSetId = new SetId("Stream Type", StreamType.ENTITY_TYPE);

                final SharedMap<SetId, EntityIdSet<?>> entitySetMap = new SharedMap<SetId, EntityIdSet<?>>();
                entitySetMap.put(folderSetId, findStreamCriteria.getFolderIdSet());
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
                dispatcher.execute(new LoadEntityIdSetAction(entitySetMap),
                        new AsyncCallbackAdaptor<SharedMap<SetId, SharedList<DocRef>>>() {
                            @Override
                            public void onSuccess(final SharedMap<SetId, SharedList<DocRef>> result) {
                                final SharedList<DocRef> folders = result.get(folderSetId);
                                final SharedList<DocRef> feedsInclude = result.get(feedIncludeSetId);
                                final SharedList<DocRef> feedsExclude = result.get(feedExcludeSetId);
                                final SharedList<DocRef> streamTypes = result.get(streamTypeSetId);

                                final ExpressionOperator operator = new ExpressionOperator(Op.AND);
                                addEntityListTerm(operator, folders, "Folder");
                                addEntityListTerm(operator, feedsInclude, "Feed");

                                if (feedsExclude != null && feedsExclude.size() > 0) {
                                    final ExpressionOperator not = new ExpressionOperator(Op.NOT);
                                    addEntityListTerm(not, feedsExclude, "Feed");
                                    operator.addChild(not);
                                }

                                addEntityListTerm(operator, streamTypes, "Stream Type");
                                addIdTerm(operator, findStreamCriteria.getStreamIdSet(), "Stream Id");
                                addIdTerm(operator, findStreamCriteria.getParentStreamIdSet(), "Parent Stream Id");
                                addPeriodTerm(operator, findStreamCriteria.getCreatePeriod(), "Creation time");
                                addPeriodTerm(operator, findStreamCriteria.getEffectivePeriod(), "Effective time");

                                expressionPresenter.read(operator);
                            }
                        });
            }
        }
    }

    private void addEntityListTerm(final ExpressionOperator operator, final SharedList<DocRef> entities,
                                   final String label) {
        if (entities != null && entities.size() > 0) {
            if (entities.size() > 1) {
                final ExpressionOperator or = new ExpressionOperator(Op.OR);

                Collections.sort(entities, new EntityReferenceComparator());
                for (final DocRef entity : entities) {
                    addEntity(or, entity, label);
                }

                if (or.getChildren() != null && or.getChildren().size() > 0) {
                    operator.addChild(or);
                }
            } else {
                final DocRef entity = entities.get(0);
                addEntity(operator, entity, label);
            }
        }
    }

    private void addEntity(final ExpressionOperator operator, final DocRef entity, final String label) {
        if (entity != null) {
            final ExpressionTerm term = new ExpressionTerm();
            term.setField(label);
            term.setCondition(Condition.EQUALS);
            term.setValue(entity.getName());
            operator.addChild(term);
        }
    }

    private void addIdTerm(final ExpressionOperator operator, final EntityIdSet<?> entities, final String label) {
        if (entities != null && entities.size() > 0) {
            final ExpressionTerm term = new ExpressionTerm();
            term.setField(label);
            if (entities.size() > 1) {
                term.setCondition(Condition.IN);
            } else {
                term.setCondition(Condition.EQUALS);
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
            term.setValue(sb.toString());

            operator.addChild(term);
        }
    }

    private void addPeriodTerm(final ExpressionOperator operator, final Period period, final String label) {
        if (period != null && (period.getFrom() != null || period.getTo() != null)) {
            final ExpressionTerm term = new ExpressionTerm();
            term.setField(label);

            final StringBuilder sb = new StringBuilder();
            if (period.getFrom() != null && period.getTo() != null) {
                term.setCondition(Condition.BETWEEN);
                sb.append(ClientDateUtil.createDateTimeString(period.getFrom()));
                sb.append(" and ");
                sb.append(ClientDateUtil.createDateTimeString(period.getTo()));
            } else if (period.getFrom() != null) {
                term.setCondition(Condition.GREATER_THAN);
                sb.append(ClientDateUtil.createDateTimeString(period.getFrom()));
            } else if (period.getTo() != null) {
                term.setCondition(Condition.LESS_THAN);
                sb.append(ClientDateUtil.createDateTimeString(period.getTo()));
            }

            term.setValue(sb.toString());

            operator.addChild(term);
        }
    }

    public MultiSelectionModel<SharedObject> getSelectionModel() {
        return processorListPresenter.getSelectionModel();
    }

    public void addProcessor() {
        if (pipelineEntity != null) {
            addOrEditProcessor(null);
        }
    }

    public void editProcessor() {
        if (pipelineEntity != null && selectedProcessor != null) {
            if (selectedProcessor instanceof StreamProcessorFilterRow) {
                final StreamProcessorFilterRow streamProcessorFilterRow = (StreamProcessorFilterRow) selectedProcessor;
                final StreamProcessorFilter filter = streamProcessorFilterRow.getEntity();
                addOrEditProcessor(filter);
            }
        }
    }

    public void addOrEditProcessor(final StreamProcessorFilter filter) {
        final FindStreamAttributeMapCriteria criteria = new FindStreamAttributeMapCriteria();

        if (filter != null && filter.getFindStreamCriteria() != null) {
            criteria.obtainFindStreamCriteria().copyFrom(filter.getFindStreamCriteria());
        }

        streamFilterPresenter.setCriteria(criteria, true, true, true, false, false);

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
                                new ConfirmCallback() {
                                    @Override
                                    public void onResult(final boolean result) {
                                        if (result) {
                                            validateFeed(filter, findStreamCriteria);
                                        }
                                    }
                                });
                    } else {
                        validateFeed(filter, findStreamCriteria);
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
                && findStreamCriteria.obtainFolderIdSet().size() == 0
                && findStreamCriteria.obtainFeeds().obtainInclude().getSet().size() == 0
                && findStreamCriteria.obtainFeeds().obtainExclude().getSet().size() == 0) {
            ConfirmEvent.fire(ProcessorPresenter.this,
                    "You are about to process all feeds. Are you sure you wish to do this?", new ConfirmCallback() {
                        @Override
                        public void onResult(final boolean result) {
                            if (result) {
                                validateStreamType(filter, findStreamCriteria);
                            }
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
                    new ConfirmCallback() {
                        @Override
                        public void onResult(final boolean result) {
                            if (result) {
                                createOrUpdateProcessor(filter, findStreamCriteria);
                            }
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
            dispatcher.execute(new EntityServiceSaveAction<StreamProcessorFilter>(filter),
                    new AsyncCallbackAdaptor<StreamProcessorFilter>() {
                        @Override
                        public void onSuccess(final StreamProcessorFilter result) {
                            refresh(result);
                            HidePopupEvent.fire(ProcessorPresenter.this, streamFilterPresenter);
                        }
                    });

        } else {
            // Now create the processor filter using the find stream criteria.
            dispatcher.execute(new CreateProcessorAction(DocRefUtil.create(pipelineEntity), findStreamCriteria, false, 10),
                    new AsyncCallbackAdaptor<StreamProcessorFilter>() {
                        @Override
                        public void onSuccess(final StreamProcessorFilter result) {
                            refresh(result);
                            HidePopupEvent.fire(ProcessorPresenter.this, streamFilterPresenter);
                        }
                    });
        }
    }

    public void removeProcessor() {
        if (selectedProcessor != null) {
            if (selectedProcessor instanceof StreamProcessorRow) {
                final StreamProcessorRow streamProcessorRow = (StreamProcessorRow) selectedProcessor;
                ConfirmEvent.fire(this, "Are you sure you want to delete this processor?", new ConfirmCallback() {
                    @Override
                    public void onResult(final boolean result) {
                        if (result) {
                            dispatcher.execute(
                                    new EntityServiceDeleteAction<StreamProcessor>(streamProcessorRow.getEntity()),
                                    new AsyncCallbackAdaptor<StreamProcessor>() {
                                        @Override
                                        public void onSuccess(final StreamProcessor result) {
                                            processorListPresenter.refresh();
                                        }
                                    });
                        }
                    }
                });
            } else if (selectedProcessor instanceof StreamProcessorFilterRow) {
                final StreamProcessorFilterRow streamProcessorFilterRow = (StreamProcessorFilterRow) selectedProcessor;
                ConfirmEvent.fire(this, "Are you sure you want to delete this filter?", new ConfirmCallback() {
                    @Override
                    public void onResult(final boolean result) {
                        if (result) {
                            dispatcher.execute(
                                    new EntityServiceDeleteAction<StreamProcessorFilter>(
                                            streamProcessorFilterRow.getEntity()),
                                    new AsyncCallbackAdaptor<StreamProcessorFilter>() {
                                        @Override
                                        public void onSuccess(final StreamProcessorFilter result) {
                                            processorListPresenter.refresh();
                                        }
                                    });
                        }
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
