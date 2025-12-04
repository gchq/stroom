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

package stroom.analytics.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.analytics.client.presenter.StreamingProcessingPresenter.StreamingProcessingView;
import stroom.analytics.shared.AbstractAnalyticRuleDoc;
import stroom.analytics.shared.AnalyticProcessResource;
import stroom.dispatch.client.RestFactory;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.entity.client.presenter.DocumentEditPresenter;
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
    private DocumentEditPresenter<?, ?> documentEditPresenter;

    @Inject
    public StreamingProcessingPresenter(final EventBus eventBus,
                                        final StreamingProcessingView view,
                                        final ProcessorPresenter processorPresenter,
                                        final RestFactory restFactory) {
        super(eventBus, view);
        this.processorPresenter = processorPresenter;
        this.restFactory = restFactory;
        getView().setProcessorsView(processorPresenter.getView());

        processorPresenter.setEditInterceptor(() -> {
            if (documentEditPresenter != null && documentEditPresenter.isDirty()) {
                AlertEvent.fireWarn(
                        this,
                        "Please ensure all settings are correct and save before adding executions",
                        null);
                return false;
            } else {
                return true;
            }
        });
    }

    public void update(final AbstractAnalyticRuleDoc analyticRuleDoc,
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
                .taskMonitorFactory(this)
                .exec();
    }

    public void onDirty() {
        DirtyEvent.fire(this, true);
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    public void setDocumentEditPresenter(final DocumentEditPresenter<?, ?> documentEditPresenter) {
        this.documentEditPresenter = documentEditPresenter;
    }

    public interface StreamingProcessingView extends View {

        void setProcessorsView(View view);
    }
}
