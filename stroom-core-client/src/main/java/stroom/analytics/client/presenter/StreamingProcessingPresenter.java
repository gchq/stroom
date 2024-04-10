package stroom.analytics.client.presenter;

import stroom.analytics.client.presenter.StreamingProcessingPresenter.StreamingProcessingView;
import stroom.analytics.shared.AnalyticProcessResource;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.dispatch.client.RestFactory;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.processor.client.presenter.ProcessorPresenter;

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

    private final ProcessorPresenter processorPresenter;
    private final RestFactory restFactory;

    @Inject
    public StreamingProcessingPresenter(final EventBus eventBus,
                                        final StreamingProcessingView view,
                                        final ProcessorPresenter processorPresenter,
                                        final RestFactory restFactory) {
        super(eventBus, view);
        this.processorPresenter = processorPresenter;
        this.restFactory = restFactory;
        getView().setProcessorsView(processorPresenter.getView());
    }

    public void update(final AnalyticRuleDoc analyticRuleDoc,
                       final boolean readOnly,
                       final String query) {
        restFactory
                .create(ANALYTIC_PROCESS_RESOURCE)
                .method(res -> res.getDefaultProcessingFilterExpression(query))
                .onSuccess(expressionOperator -> {
                    processorPresenter.setDefaultExpression(expressionOperator);
                    processorPresenter.read(analyticRuleDoc.asDocRef(), analyticRuleDoc, readOnly);
                    processorPresenter.setAllowUpdate(true);
                })
                .exec();
    }

    public void onDirty() {
        DirtyEvent.fire(this, true);
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    public interface StreamingProcessingView extends View {

        void setProcessorsView(View view);
    }
}
