package stroom.analytics.client.presenter;

import stroom.analytics.client.presenter.StreamingProcessingPresenter.StreamingProcessingView;
import stroom.analytics.shared.AnalyticProcessResource;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.StreamingAnalyticProcessConfig;
import stroom.dispatch.client.RestFactory;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.feed.shared.FeedDoc;
import stroom.processor.client.presenter.ProcessorPresenter;
import stroom.query.api.v2.ExpressionOperator;
import stroom.security.shared.DocumentPermissionNames;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class StreamingProcessingPresenter
        extends MyPresenterWidget<StreamingProcessingView>
        implements HasDirtyHandlers {

    private static final AnalyticProcessResource ANALYTIC_PROCESS_RESOURCE = GWT.create(AnalyticProcessResource.class);

    private final DocSelectionBoxPresenter errorFeedPresenter;
    private final ProcessorPresenter processorPresenter;
    private final RestFactory restFactory;

    @Inject
    public StreamingProcessingPresenter(final EventBus eventBus,
                                        final StreamingProcessingView view,
                                        final DocSelectionBoxPresenter errorFeedPresenter,
                                        final ProcessorPresenter processorPresenter,
                                        final RestFactory restFactory) {
        super(eventBus, view);
        this.errorFeedPresenter = errorFeedPresenter;
        this.processorPresenter = processorPresenter;
        this.restFactory = restFactory;

        errorFeedPresenter.setIncludedTypes(FeedDoc.DOCUMENT_TYPE);
        errorFeedPresenter.setRequiredPermissions(DocumentPermissionNames.READ);

        getView().setErrorFeedView(errorFeedPresenter.getView());
        getView().setProcessorsView(processorPresenter.getView());
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(errorFeedPresenter.addDataSelectionHandler(e -> onDirty()));
    }

    public void read(final StreamingAnalyticProcessConfig streamingAnalyticProcessConfig) {
        errorFeedPresenter.setSelectedEntityReference(streamingAnalyticProcessConfig.getErrorFeed());
    }

    public StreamingAnalyticProcessConfig write() {
        return StreamingAnalyticProcessConfig
                .builder()
                .errorFeed(errorFeedPresenter.getSelectedEntityReference())
                .build();
    }

    public void update(final AnalyticRuleDoc analyticRuleDoc,
                       final boolean readOnly,
                       final String query) {
        restFactory
                .builder()
                .forType(ExpressionOperator.class)
                .onSuccess(expressionOperator -> {
                    processorPresenter.read(analyticRuleDoc.asDocRef(), analyticRuleDoc, readOnly);
                    processorPresenter.setAllowUpdate(true);
                })
                .call(ANALYTIC_PROCESS_RESOURCE)
                .getDefaultProcessingFilterExpression(query);
    }

    public void onDirty() {
        DirtyEvent.fire(this, true);
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    public interface StreamingProcessingView extends View {

        void setErrorFeedView(View view);

        void setProcessorsView(View view);
    }
}
