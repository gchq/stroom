package stroom.analytics.client.presenter;

import stroom.analytics.client.presenter.StreamingProcessingPresenter.StreamingProcessingView;
import stroom.analytics.shared.AnalyticProcessResource;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.StreamingAnalyticProcessConfig;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.explorer.client.presenter.EntityDropDownPresenter;
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

import java.util.Objects;

public class StreamingProcessingPresenter
        extends MyPresenterWidget<StreamingProcessingView>
        implements HasDirtyHandlers {

    private static final AnalyticProcessResource ANALYTIC_PROCESS_RESOURCE = GWT.create(AnalyticProcessResource.class);

    private final EntityDropDownPresenter errorFeedPresenter;
    private final ProcessorPresenter processorPresenter;
    private final RestFactory restFactory;
    private DocRef currentErrorFeed;
    private boolean isErrorFeedInitialised = false;

    @Inject
    public StreamingProcessingPresenter(final EventBus eventBus,
                                        final StreamingProcessingView view,
                                        final EntityDropDownPresenter errorFeedPresenter,
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
        registerHandler(errorFeedPresenter.addDataSelectionHandler(e -> {
            final DocRef selectedEntityReference = errorFeedPresenter.getSelectedEntityReference();
            // Don't want to fire dirty event when the entity is first set
            if (isErrorFeedInitialised) {
                if (!Objects.equals(selectedEntityReference, currentErrorFeed)) {
                    currentErrorFeed = selectedEntityReference;
                    onDirty();
                }
            } else {
                isErrorFeedInitialised = true;
            }
        }));
    }

    public void read(final StreamingAnalyticProcessConfig streamingAnalyticProcessConfig) {
        this.currentErrorFeed = streamingAnalyticProcessConfig.getErrorFeed();
        errorFeedPresenter.setSelectedEntityReference(currentErrorFeed);
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

    public StreamingAnalyticProcessConfig write() {
        return StreamingAnalyticProcessConfig
                .builder()
                .build();
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
