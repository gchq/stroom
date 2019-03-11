package stroom.process.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.ConfirmEvent;
import stroom.datasource.api.v2.DataSourceField;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.docref.DocRef;
import stroom.meta.shared.MetaFieldNames;
import stroom.process.client.presenter.ProcessorEditPresenter.ProcessorEditView;
import stroom.processor.shared.CreateProcessorFilterAction;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.QueryData;
import stroom.processor.shared.UpdateProcessorFilterAction;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.receive.rules.client.presenter.EditExpressionPresenter;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.List;
import java.util.function.Consumer;

public class ProcessorEditPresenter extends MyPresenterWidget<ProcessorEditView> {
    private final EditExpressionPresenter editExpressionPresenter;
    private final ClientDispatchAsync dispatcher;

    private DocRef pipelineRef;
    private Consumer<ProcessorFilter> consumer;

    @Inject
    public ProcessorEditPresenter(final EventBus eventBus,
                                  final ProcessorEditView view,
                                  final EditExpressionPresenter editExpressionPresenter,
                                  final ClientDispatchAsync dispatcher) {
        super(eventBus, view);
        this.editExpressionPresenter = editExpressionPresenter;
        this.dispatcher = dispatcher;
        view.setExpressionView(editExpressionPresenter.getView());
    }

    public void read(final ExpressionOperator expression, final DocRef dataSource, final List<DataSourceField> fields) {
        editExpressionPresenter.init(dispatcher, dataSource, fields);

        if (expression != null) {
            editExpressionPresenter.read(expression);
        } else {
            editExpressionPresenter.read(new ExpressionOperator.Builder(Op.AND).build());
        }
    }

    public ExpressionOperator write() {
        return editExpressionPresenter.write();
    }

    public void show(final DocRef pipelineRef, final ProcessorFilter filter, final Consumer<ProcessorFilter> consumer) {
        this.pipelineRef = pipelineRef;
        this.consumer = consumer;

        final QueryData queryData = getOrCreateQueryData(filter);
        read(queryData.getExpression(), MetaFieldNames.STREAM_STORE_DOC_REF, MetaFieldNames.getFields());

        final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                if (ok) {
                    final ExpressionOperator expression = write();
                    queryData.setDataSource(MetaFieldNames.STREAM_STORE_DOC_REF);
                    queryData.setExpression(expression);

                    if (filter != null) {
                        ConfirmEvent.fire(ProcessorEditPresenter.this,
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
                    hide(null);
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
            ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, popupSize, "Edit Filter",
                    popupUiHandlers);
        } else {
            ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, popupSize, "Add Filter",
                    popupUiHandlers);
        }
    }

    private void hide(final ProcessorFilter result) {
        consumer.accept(result);
        HidePopupEvent.fire(ProcessorEditPresenter.this, ProcessorEditPresenter.this, false, result != null);
    }

    private QueryData getOrCreateQueryData(final ProcessorFilter filter) {
        if (filter != null && filter.getQueryData() != null) {
            return filter.getQueryData();
        }
        return new QueryData();
    }

    private void validateFeed(final ProcessorFilter filter, final QueryData queryData) {
        final int feedCount = termCount(queryData, MetaFieldNames.FEED_NAME);
        final int streamIdCount = termCount(queryData, MetaFieldNames.ID);
        final int parentStreamIdCount = termCount(queryData, MetaFieldNames.PARENT_ID);

        if (streamIdCount == 0
                && parentStreamIdCount == 0
                && feedCount == 0) {
            ConfirmEvent.fire(this,
                    "You are about to process all feeds. Are you sure you wish to do this?", result -> {
                        if (result) {
                            validateStreamType(filter, queryData);
                        }
                    });
        } else {
            createOrUpdateProcessor(filter, queryData);
        }
    }

    private void validateStreamType(final ProcessorFilter filter, final QueryData queryData) {
        final int streamTypeCount = termCount(queryData, MetaFieldNames.TYPE_NAME);
        final int streamIdCount = termCount(queryData, MetaFieldNames.ID);
        final int parentStreamIdCount = termCount(queryData, MetaFieldNames.PARENT_ID);

        if (streamIdCount == 0
                && parentStreamIdCount == 0
                && streamTypeCount == 0) {
            ConfirmEvent.fire(this,
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

    private void createOrUpdateProcessor(final ProcessorFilter filter,
                                         final QueryData queryData) {
        if (filter != null) {
            // Now update the processor filter using the find stream criteria.
            filter.setQueryData(queryData);
            dispatcher.exec(new UpdateProcessorFilterAction(filter)).onSuccess(this::hide);

        } else {
            // Now create the processor filter using the find stream criteria.
            dispatcher.exec(new CreateProcessorFilterAction(pipelineRef, queryData, false, 10)).onSuccess(this::hide);
        }
    }

    public interface ProcessorEditView extends View {
        void setExpressionView(View view);
    }


//    private void addOrEditProcessor(final ProcessorFilter filter) {
//        final QueryData queryData = getOrCreateQueryData(filter);
//        filterPresenter.read(queryData.getExpression(), MetaDataSource.STREAM_STORE_DOC_REF, MetaDataSource.getFields());
//
//        final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
//            @Override
//            public void onHideRequest(final boolean autoClose, final boolean ok) {
//                if (ok) {
//                    final ExpressionOperator expression = filterPresenter.write();
//                    queryData.setDataSource(MetaDataSource.STREAM_STORE_DOC_REF);
//                    queryData.setExpression(expression);
//
//                    if (filter != null) {
//                        ConfirmEvent.fire(ProcessorPresenter.this,
//                                "You are about to update an existing filter. Any streams that might now be included by this filter but are older than the current tracker position will not be processed. Are you sure you wish to do this?",
//                                result -> {
//                                    if (result) {
//                                        validateFeed(filter, queryData);
//                                    }
//                                });
//                    } else {
//                        validateFeed(null, queryData);
//                    }
//
//                } else {
//                    HidePopupEvent.fire(ProcessorPresenter.this, filterPresenter);
//                }
//            }
//
//            @Override
//            public void onHide(final boolean autoClose, final boolean ok) {
//                // Do nothing.
//            }
//        };
//
//        // Show the processor creation dialog.
//        final PopupSize popupSize = new PopupSize(800, 600, 400, 400, true);
//        if (filter != null) {
//            ShowPopupEvent.fire(this, filterPresenter, PopupType.OK_CANCEL_DIALOG, popupSize, "Edit Filter",
//                    popupUiHandlers);
//        } else {
//            ShowPopupEvent.fire(this, filterPresenter, PopupType.OK_CANCEL_DIALOG, popupSize, "Add Filter",
//                    popupUiHandlers);
//        }
//    }
//
//    private QueryData getOrCreateQueryData(final ProcessorFilter filter) {
//        if (filter != null && filter.getQueryData() != null) {
//            return filter.getQueryData();
//        }
//        return new QueryData();
//    }
//
//    private void validateFeed(final ProcessorFilter filter, final QueryData queryData) {
//        final int feedCount = termCount(queryData, MetaDataSource.FEED_NAME);
//        final int streamIdCount = termCount(queryData, MetaDataSource.ID);
//        final int parentStreamIdCount = termCount(queryData, MetaDataSource.PARENT_ID);
//
//        if (streamIdCount == 0
//                && parentStreamIdCount == 0
//                && feedCount == 0) {
//            ConfirmEvent.fire(ProcessorPresenter.this,
//                    "You are about to process all feeds. Are you sure you wish to do this?", result -> {
//                        if (result) {
//                            validateStreamType(filter, queryData);
//                        }
//                    });
//        } else {
//            createOrUpdateProcessor(filter, queryData);
//        }
//    }
//
//    private void validateStreamType(final ProcessorFilter filter, final QueryData queryData) {
//        final int streamTypeCount = termCount(queryData, MetaDataSource.TYPE_NAME);
//        final int streamIdCount = termCount(queryData, MetaDataSource.ID);
//        final int parentStreamIdCount = termCount(queryData, MetaDataSource.PARENT_ID);
//
//        if (streamIdCount == 0
//                && parentStreamIdCount == 0
//                && streamTypeCount == 0) {
//            ConfirmEvent.fire(ProcessorPresenter.this,
//                    "You are about to process all stream types. Are you sure you wish to do this?",
//                    result -> {
//                        if (result) {
//                            createOrUpdateProcessor(filter, queryData);
//                        }
//                    });
//        } else {
//            createOrUpdateProcessor(filter, queryData);
//        }
//    }
//
//    private int termCount(final QueryData queryData, final String field) {
//        if (queryData == null || queryData.getExpression() == null) {
//            return 0;
//        }
//        return termCount(queryData.getExpression(), field);
//    }
//
//    private int termCount(final ExpressionOperator expressionOperator, final String field) {
//        int count = 0;
//        if (expressionOperator.getEnabled()) {
//            for (final ExpressionItem item : expressionOperator.getChildren()) {
//                if (item.getEnabled()) {
//                    if (item instanceof ExpressionTerm) {
//                        if (field.equals(((ExpressionTerm) item).getField())) {
//                            count++;
//                        }
//                    } else if (item instanceof ExpressionOperator) {
//                        count += termCount((ExpressionOperator) item, field);
//                    }
//                }
//            }
//        }
//        return count;
//    }
//
//    private void createOrUpdateProcessor(final ProcessorFilter filter,
//                                         final QueryData queryData) {
//        if (filter != null) {
//            // Now update the processor filter using the find stream criteria.
//            filter.setQueryData(queryData);
//            dispatcher.exec(new EntityServiceSaveAction<>(filter)).onSuccess(result -> {
//                refresh(result);
//                HidePopupEvent.fire(ProcessorPresenter.this, filterPresenter);
//            });
//
//        } else {
//            // Now create the processor filter using the find stream criteria.
//            dispatcher.exec(new CreateProcessorAction(docRef, queryData, false, 10)).onSuccess(result -> {
//                refresh(result);
//                HidePopupEvent.fire(ProcessorPresenter.this, filterPresenter);
//            });
//        }
//    }
}
