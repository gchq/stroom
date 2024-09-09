package stroom.processor.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.data.client.presenter.EditExpressionPresenter;
import stroom.datasource.api.v2.QueryField;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.meta.shared.MetaFields;
import stroom.processor.client.presenter.ProcessorEditPresenter.ProcessorEditView;
import stroom.processor.shared.CreateProcessFilterRequest;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterResource;
import stroom.processor.shared.ProcessorType;
import stroom.processor.shared.QueryData;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.client.presenter.DateTimeSettingsFactory;
import stroom.query.client.presenter.SimpleFieldSelectionListModel;
import stroom.query.shared.ExpressionResource;
import stroom.query.shared.ValidateExpressionRequest;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.function.Consumer;

public class ProcessorEditPresenter
        extends MyPresenterWidget<ProcessorEditView> {

    private static final ProcessorFilterResource PROCESSOR_FILTER_RESOURCE = GWT.create(ProcessorFilterResource.class);
    private static final ExpressionResource EXPRESSION_RESOURCE = GWT.create(ExpressionResource.class);

    private final EditExpressionPresenter editExpressionPresenter;
    private final RestFactory restFactory;
    private final DateTimeSettingsFactory dateTimeSettingsFactory;

    private ProcessorType processorType;
    private DocRef pipelineRef;
    private Consumer<ProcessorFilter> consumer;

    @Inject
    public ProcessorEditPresenter(final EventBus eventBus,
                                  final ProcessorEditView view,
                                  final EditExpressionPresenter editExpressionPresenter,
                                  final RestFactory restFactory,
                                  final DateTimeSettingsFactory dateTimeSettingsFactory) {
        super(eventBus, view);
        this.editExpressionPresenter = editExpressionPresenter;
        this.restFactory = restFactory;
        this.dateTimeSettingsFactory = dateTimeSettingsFactory;
        view.setExpressionView(editExpressionPresenter.getView());
    }

    public void read(final ExpressionOperator expression,
                     final DocRef dataSource,
                     final List<QueryField> fields,
                     final Long minMetaCreateTimeMs,
                     final Long maxMetaCreateTimeMs) {
        final SimpleFieldSelectionListModel selectionBoxModel = new SimpleFieldSelectionListModel();
        selectionBoxModel.addItems(fields);
        editExpressionPresenter.init(restFactory, dataSource, selectionBoxModel);

        if (expression != null) {
            editExpressionPresenter.read(expression);
        } else {
            editExpressionPresenter.read(ExpressionOperator.builder().build());
        }

        getView().setMinMetaCreateTimeMs(minMetaCreateTimeMs);
        getView().setMaxMetaCreateTimeMs(maxMetaCreateTimeMs);
    }

    public ExpressionOperator write() {
        return editExpressionPresenter.write();
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
        } else {
            minMetaCreateTimeMs = filter.getMinMetaCreateTimeMs();
            maxMetaCreateTimeMs = filter.getMaxMetaCreateTimeMs();
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
        final List<QueryField> fields = MetaFields.getAllFields();
        read(
                queryData.getExpression(),
                MetaFields.STREAM_STORE_DOC_REF,
                fields,
                minMetaCreateTimeMs,
                maxMetaCreateTimeMs);

        // Show the processor creation dialog.
        final PopupSize popupSize = PopupSize.resizable(800, 600);
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
                        final ExpressionOperator expression = write();
                        final Long minMetaCreateTime = getView().getMinMetaCreateTimeMs();
                        final Long maxMetaCreateTime = getView().getMaxMetaCreateTimeMs();

                        validateExpression(fields, expression, () -> {
                            try {
                                queryData.setDataSource(MetaFields.STREAM_STORE_DOC_REF);
                                queryData.setExpression(expression);

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
                                                            filter, queryData, minMetaCreateTime, maxMetaCreateTime, e);
                                                } else {
                                                    e.reset();
                                                }
                                            });
                                } else {
                                    validateFeed(null, queryData, minMetaCreateTime, maxMetaCreateTime, e);
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
                .onFailure(throwable -> {
                    AlertEvent.fireError(ProcessorEditPresenter.this, throwable.getMessage(), null);
                })
                .taskHandlerFactory(this)
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
                            validateStreamType(filter, queryData, minMetaCreateTimeMs, maxMetaCreateTimeMs, event);
                        } else {
                            event.reset();
                        }
                    });
        } else {
            createOrUpdateProcessor(filter, queryData, minMetaCreateTimeMs, maxMetaCreateTimeMs, event);
        }
    }

    private void validateStreamType(final ProcessorFilter filter,
                                    final QueryData queryData,
                                    final Long minMetaCreateTimeMs,
                                    final Long maxMetaCreateTimeMs,
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
                            createOrUpdateProcessor(filter, queryData, minMetaCreateTimeMs, maxMetaCreateTimeMs, event);
                        } else {
                            event.reset();
                        }
                    });
        } else {
            createOrUpdateProcessor(filter, queryData, minMetaCreateTimeMs, maxMetaCreateTimeMs, event);
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
                                         final HidePopupRequestEvent event) {
        if (filter != null) {
            // Now update the processor filter using the find stream criteria.
            filter.setQueryData(queryData);
            filter.setMinMetaCreateTimeMs(minMetaCreateTimeMs);
            filter.setMaxMetaCreateTimeMs(maxMetaCreateTimeMs);

            restFactory
                    .create(PROCESSOR_FILTER_RESOURCE)
                    .method(res -> res.update(filter.getId(), filter))
                    .onSuccess(r -> hide(r, event))
                    .onFailure(RestErrorHandler.forPopup(this, event))
                    .taskHandlerFactory(this)
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
                    .minMetaCreateTimeMs(minMetaCreateTimeMs)
                    .maxMetaCreateTimeMs(maxMetaCreateTimeMs)
                    .build();
            restFactory
                    .create(PROCESSOR_FILTER_RESOURCE)
                    .method(res -> res.create(request))
                    .onSuccess(r -> hide(r, event))
                    .taskHandlerFactory(this)
                    .exec();
        }
    }

    public interface ProcessorEditView extends View {

        void setExpressionView(View view);

        Long getMinMetaCreateTimeMs();

        void setMinMetaCreateTimeMs(Long minMetaCreateTimeMs);

        Long getMaxMetaCreateTimeMs();

        void setMaxMetaCreateTimeMs(Long maxMetaCreateTimeMs);
    }
}
