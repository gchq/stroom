/*
 * Copyright 2023 Crown Copyright
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
import stroom.entity.client.presenter.DocPresenter;
import stroom.processor.client.component.ProcessorsController;
import stroom.widget.component.client.Contexts;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public class StreamingProcessingPresenter
        extends MyPresenterWidget<StreamingProcessingView> {

    private static final AnalyticProcessResource ANALYTIC_PROCESS_RESOURCE = GWT.create(AnalyticProcessResource.class);

    private final ProcessorsController processors;
    private final RestFactory restFactory;
    private DocPresenter<?, ?> documentEditPresenter;

    @Inject
    public StreamingProcessingPresenter(final EventBus eventBus,
                                        final StreamingProcessingView view,
                                        final ProcessorsController processors,
                                        final RestFactory restFactory) {
        super(eventBus, view);
        own(processors);
        this.processors = processors;
        this.restFactory = restFactory;
        getView().setProcessors(processors);

        processors.setEditInterceptor(() -> {
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
                       final String query) {
        // Read only as its document is
        final boolean readOnly = context(Contexts.READ_ONLY);
        restFactory
                .create(ANALYTIC_PROCESS_RESOURCE)
                .method(res -> res.getDefaultProcessingFilterExpression(query))
                .onSuccess(expressionOperator -> {
                    processors.setDefaultExpression(expressionOperator);
                    processors.read(analyticRuleDoc.asDocRef(), analyticRuleDoc);
                    processors.setAllowUpdate(!readOnly);
                })
                .taskMonitorFactory(this)
                .exec();
    }

    public void setDocumentEditPresenter(final DocPresenter<?, ?> documentEditPresenter) {
        this.documentEditPresenter = documentEditPresenter;
    }

    public interface StreamingProcessingView extends View {

        void setProcessors(IsWidget processors);
    }
}
