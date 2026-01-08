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
import stroom.data.client.presenter.EditExpressionPresenter;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.meta.shared.MetaFields;
import stroom.processor.client.presenter.ProcessorEditPresenter.ProcessorEditView;
import stroom.processor.shared.CreateProcessFilterRequest;
import stroom.processor.shared.FeedDependencies;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterResource;
import stroom.processor.shared.ProcessorType;
import stroom.processor.shared.QueryData;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.datasource.QueryField;
import stroom.query.client.presenter.DateTimeSettingsFactory;
import stroom.query.client.presenter.SimpleFieldSelectionListModel;
import stroom.query.shared.ExpressionResource;
import stroom.query.shared.ValidateExpressionRequest;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.UserRefSelectionBoxPresenter;
import stroom.security.shared.FindUserContext;
import stroom.util.shared.NullSafe;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.function.Consumer;

public class ProcessorEditPresenter
        extends MyPresenterWidget<ProcessorEditView>
        implements ProcessorEditUiHandlers {

    private static final ProcessorFilterResource PROCESSOR_FILTER_RESOURCE = GWT.create(ProcessorFilterResource.class);
    private static final ExpressionResource EXPRESSION_RESOURCE = GWT.create(ExpressionResource.class);

    private final EditExpressionPresenter editExpressionPresenter;
    private final RestFactory restFactory;
    private final DateTimeSettingsFactory dateTimeSettingsFactory;
    private final UserRefSelectionBoxPresenter userRefSelectionBoxPresenter;
    private final ClientSecurityContext clientSecurityContext;
    private final Provider<FeedDependencyPresenter> feedDependencyPresenterProvider;

    private ProcessorType processorType;
    private DocRef pipelineRef;
    private FeedDependencies feedDependencies;
    private Consumer<ProcessorFilter> consumer;

    @Inject
    public ProcessorEditPresenter(final EventBus eventBus,
                                  final ProcessorEditView view,
                                  final EditExpressionPresenter editExpressionPresenter,
                                  final RestFactory restFactory,
                                  final DateTimeSettingsFactory dateTimeSettingsFactory,
                                  final UserRefSelectionBoxPresenter userRefSelectionBoxPresenter,
                                  final ClientSecurityContext clientSecurityContext,
                                  final Provider<FeedDependencyPresenter> feedDependencyPresenterProvider) {
        super(eventBus, view);
        view.setUiHandlers(this);

        this.editExpressionPresenter = editExpressionPresenter;
        this.restFactory = restFactory;
        this.dateTimeSettingsFactory = dateTimeSettingsFactory;
        this.userRefSelectionBoxPresenter = userRefSelectionBoxPresenter;
        this.clientSecurityContext = clientSecurityContext;
        this.feedDependencyPresenterProvider = feedDependencyPresenterProvider;
        view.setExpressionView(editExpressionPresenter.getView());
        view.setRunAsUserView(userRefSelectionBoxPresenter.getView());
        userRefSelectionBoxPresenter.setContext(FindUserContext.RUN_AS);
    }

    private void read(final ExpressionOperator expression,
                      final DocRef dataSource,
                      final List<QueryField> fields,
                      final Long minMetaCreateTimeMs,
                      final Long maxMetaCreateTimeMs,
                      final boolean export) {

        final SimpleFieldSelectionListModel selectionBoxModel = new SimpleFieldSelectionListModel();
        selectionBoxModel.addItems(fields);
        editExpressionPresenter.init(restFactory, dataSource, selectionBoxModel);
        editExpressionPresenter.read(NullSafe.requireNonNullElse(expression, ExpressionOperator.builder().build()));

        getView().setMinMetaCreateTimeMs(minMetaCreateTimeMs);
        getView().setMaxMetaCreateTimeMs(maxMetaCreateTimeMs);
        getView().setExport(export);
    }

    public void show(final ProcessorType processorType,
                     final DocRef pipelineRef,
                     final ProcessorFilter filter,
                     final ExpressionOperator defaultExpression,
                     final Consumer<ProcessorFilter> consumer) {
        final Long minMetaCreateTimeMs;
        final Long maxMetaCreateTimeMs;
        if (filter == null) {
            minMetaCreateTimeMs = null;
            maxMetaCreateTimeMs = null;
            userRefSelectionBoxPresenter.setSelected(clientSecurityContext.getUserRef());
        } else {
            minMetaCreateTimeMs = filter.getMinMetaCreateTimeMs();
            maxMetaCreateTimeMs = filter.getMaxMetaCreateTimeMs();
            if (filter.getRunAsUser() == null) {
                userRefSelectionBoxPresenter.setSelected(clientSecurityContext.getUserRef());
            } else {
                userRefSelectionBoxPresenter.setSelected(filter.getRunAsUser());
            }
        }
        show(processorType, pipelineRef, filter, defaultExpression, minMetaCreateTimeMs, maxMetaCreateTimeMs, consumer);
    }

    public void show(final ProcessorType processorType,
                     final DocRef pipelineRef,
                     final ProcessorFilter filter,
                     final ExpressionOperator defaultExpression,
                     final Long minMetaCreateTimeMs,
                     final Long maxMetaCreateTimeMs,
                     final Consumer<ProcessorFilter> consumer) {
        this.processorType = processorType;
        this.pipelineRef = pipelineRef;
        this.consumer = consumer;

        final boolean existingFilter = filter != null && filter.getId() != null;
        final QueryData queryData = getOrCreateQueryData(filter, defaultExpression);
        final List<QueryField> fields = MetaFields.getProcessorFilterFields();
        final boolean export = NullSafe.getOrElse(filter, ProcessorFilter::isExport, false);
        feedDependencies = NullSafe.get(queryData, QueryData::getFeedDependencies);
        read(
                queryData.getExpression(),
                MetaFields.STREAM_STORE_DOC_REF,
                fields,
                minMetaCreateTimeMs,
                maxMetaCreateTimeMs,
                export);

        // Show the processor creation dialog.
        final PopupSize popupSize = PopupSize.resizable(800, 700);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption(existingFilter
                        ? "Edit Filter"
                        : "Add Filter")
                .modal(true)
                .onShow(e -> editExpressionPresenter.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final ExpressionOperator expression = editExpressionPresenter.write();
                        final Long minMetaCreateTime = getView().getMinMetaCreateTimeMs();
                        final Long maxMetaCreateTime = getView().getMaxMetaCreateTimeMs();
                        final boolean exportRead = getView().isExport();

                        validateExpression(fields, expression, () -> {
                            try {
                                final QueryData qd = queryData
                                        .copy()
                                        .dataSource(MetaFields.STREAM_STORE_DOC_REF)
                                        .expression(expression)
                                        .build();

                                if (existingFilter) {
                                    ConfirmEvent.fire(
                                            ProcessorEditPresenter.this,
                                            "You are about to update an existing filter. Any streams that " +
                                            "might now be included by this filter but are older than the " +
                                            "current tracker position will not be processed. " +
                                            "Are you sure you wish to do this?",
                                            result -> {
                                                if (result) {
                                                    validateFeed(
                                                            filter,
                                                            qd,
                                                            minMetaCreateTime,
                                                            maxMetaCreateTime,
                                                            exportRead,
                                                            e);
                                                } else {
                                                    e.reset();
                                                }
                                            });
                                } else {
                                    validateFeed(null,
                                            qd,
                                            minMetaCreateTime,
                                            maxMetaCreateTime,
                                            exportRead,
                                            e);
                                }
                            } catch (final RuntimeException ex) {
                                AlertEvent.fireError(ProcessorEditPresenter.this, ex.getMessage(), e::reset);
                            }
                        });
                    } else {
                        consumer.accept(null);
                        e.hide();
                    }
                })
                .fire();
    }

    @Override
    public void onEditFeedDependencies() {
        final FeedDependencyPresenter feedDependencyPresenter = feedDependencyPresenterProvider.get();
        feedDependencyPresenter.show(feedDependencies, updated -> this.feedDependencies = updated);
    }

    private void validateExpression(final List<QueryField> fields,
                                    final ExpressionOperator expression,
                                    final Runnable onSuccess) {

        restFactory
                .create(EXPRESSION_RESOURCE)
                .method(res -> res.validate(new ValidateExpressionRequest(
                        expression,
                        fields,
                        dateTimeSettingsFactory.getDateTimeSettings())))
                .onSuccess(result -> {
                    if (result.isOk()) {
                        onSuccess.run();
                    } else {
                        AlertEvent.fireError(ProcessorEditPresenter.this, result.getString(), null);
                    }
                })
                .onFailure(throwable ->
                        AlertEvent.fireError(ProcessorEditPresenter.this, throwable.getMessage(), null))
                .taskMonitorFactory(this)
                .exec();
    }

    private void hide(final ProcessorFilter result, final HidePopupRequestEvent event) {
        consumer.accept(result);
        event.hide();
    }

    private QueryData getOrCreateQueryData(final ProcessorFilter filter,
                                           final ExpressionOperator defaultExpression) {
        if (filter != null && filter.getQueryData() != null) {
            return filter.getQueryData();
        }
        return QueryData.builder().expression(defaultExpression).build();
    }

    private void validateFeed(final ProcessorFilter filter,
                              final QueryData queryData,
                              final Long minMetaCreateTimeMs,
                              final Long maxMetaCreateTimeMs,
                              final boolean export,
                              final HidePopupRequestEvent event) {

        final int feedCount = termCount(queryData, MetaFields.FEED);
        final int streamIdCount = termCount(queryData, MetaFields.ID);
        final int parentStreamIdCount = termCount(queryData, MetaFields.PARENT_ID);

        if (streamIdCount == 0
            && parentStreamIdCount == 0
            && feedCount == 0) {
            ConfirmEvent.fire(this,
                    "You are about to process all feeds. Are you sure you wish to do this?", result -> {
                        if (result) {
                            validateStreamType(
                                    filter,
                                    queryData,
                                    minMetaCreateTimeMs,
                                    maxMetaCreateTimeMs,
                                    export,
                                    event);
                        } else {
                            event.reset();
                        }
                    });
        } else {
            createOrUpdateProcessor(filter, queryData, minMetaCreateTimeMs, maxMetaCreateTimeMs, export, event);
        }
    }

    private void validateStreamType(final ProcessorFilter filter,
                                    final QueryData queryData,
                                    final Long minMetaCreateTimeMs,
                                    final Long maxMetaCreateTimeMs,
                                    final boolean export,
                                    final HidePopupRequestEvent event) {
        final int streamTypeCount = termCount(queryData, MetaFields.TYPE);
        final int streamIdCount = termCount(queryData, MetaFields.ID);
        final int parentStreamIdCount = termCount(queryData, MetaFields.PARENT_ID);

        if (streamIdCount == 0
            && parentStreamIdCount == 0
            && streamTypeCount == 0) {
            ConfirmEvent.fire(this,
                    "You are about to process all stream types. Are you sure you wish to do this?",
                    result -> {
                        if (result) {
                            createOrUpdateProcessor(
                                    filter,
                                    queryData,
                                    minMetaCreateTimeMs,
                                    maxMetaCreateTimeMs,
                                    export,
                                    event);
                        } else {
                            event.reset();
                        }
                    });
        } else {
            createOrUpdateProcessor(filter, queryData, minMetaCreateTimeMs, maxMetaCreateTimeMs, export, event);
        }
    }

    private int termCount(final QueryData queryData, final QueryField field) {
        if (queryData == null || queryData.getExpression() == null) {
            return 0;
        }
        return ExpressionUtil.termCount(queryData.getExpression(), field.getFldName());
    }

    private void createOrUpdateProcessor(final ProcessorFilter filter,
                                         final QueryData queryData,
                                         final Long minMetaCreateTimeMs,
                                         final Long maxMetaCreateTimeMs,
                                         final boolean export,
                                         final HidePopupRequestEvent event) {
        if (filter != null) {
            // Now update the processor filter using the find stream criteria.
            filter.setQueryData(queryData.copy().feedDependencies(feedDependencies).build());
            filter.setMinMetaCreateTimeMs(minMetaCreateTimeMs);
            filter.setMaxMetaCreateTimeMs(maxMetaCreateTimeMs);
            filter.setExport(export);
            filter.setRunAsUser(userRefSelectionBoxPresenter.getSelected());

            restFactory
                    .create(PROCESSOR_FILTER_RESOURCE)
                    .method(res -> res.update(filter.getId(), filter))
                    .onSuccess(r -> hide(r, event))
                    .onFailure(RestErrorHandler.forPopup(this, event))
                    .taskMonitorFactory(this)
                    .exec();

        } else {
            // Now create the processor filter using the find stream criteria.
            final CreateProcessFilterRequest request = CreateProcessFilterRequest
                    .builder()
                    .processorType(processorType)
                    .pipeline(pipelineRef)
                    .queryData(queryData)
                    .autoPriority(true)
                    .enabled(false)
                    .export(export)
                    .minMetaCreateTimeMs(minMetaCreateTimeMs)
                    .maxMetaCreateTimeMs(maxMetaCreateTimeMs)
                    .runAsUser(userRefSelectionBoxPresenter.getSelected())
                    .build();
            restFactory
                    .create(PROCESSOR_FILTER_RESOURCE)
                    .method(res -> res.create(request))
                    .onSuccess(r -> hide(r, event))
                    .taskMonitorFactory(this)
                    .exec();
        }
    }


    // --------------------------------------------------------------------------------


    public interface ProcessorEditView extends View, HasUiHandlers<ProcessorEditUiHandlers> {

        void setExpressionView(View view);

        Long getMinMetaCreateTimeMs();

        void setMinMetaCreateTimeMs(Long minMetaCreateTimeMs);

        Long getMaxMetaCreateTimeMs();

        void setMaxMetaCreateTimeMs(Long maxMetaCreateTimeMs);

        boolean isExport();

        void setExport(boolean export);

        void setRunAsUserView(View view);
    }
}
