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

package stroom.analytics.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.analytics.shared.AbstractAnalyticRuleDoc;
import stroom.analytics.shared.AnalyticProcessResource;
import stroom.dispatch.client.RestFactory;
import stroom.processor.client.component.ProcessorsController;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.BooleanSupplier;

/// A streaming analytic rule's processing: the rule's processors and their filters, a new filter
/// starting with the expression the rule's query gives.
public class StreamingProcessingController extends Controller {

    private static final AnalyticProcessResource ANALYTIC_PROCESS_RESOURCE = GWT.create(AnalyticProcessResource.class);

    private final ProcessorsController processors;
    private final RestFactory restFactory;
    private BooleanSupplier isDocumentDirty = () -> false;

    /// Made by GIN, with its content and its parts.
    ///
    /// @param eventBus    The application's event bus.
    /// @param processors  The rule's processors and their filters.
    /// @param restFactory Gets the expression a new filter starts with.
    @Inject
    public StreamingProcessingController(final EventBus eventBus,
                                         final ProcessorsController processors,
                                         final RestFactory restFactory) {
        super(eventBus);
        own(processors);
        this.processors = processors;
        this.restFactory = restFactory;
        initWidget(processors.asWidget());

        processors.setEditInterceptor(() -> {
            if (isDocumentDirty.getAsBoolean()) {
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

    /// Lists a rule's processors.
    ///
    /// @param analyticRuleDoc The rule.
    /// @param query           The rule's query, which gives the expression a new filter starts with.
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
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

    /// @param isDocumentDirty Whether the document has unsaved changes, which must be saved before a
    ///                        filter is added or edited.
    public void setIsDocumentDirty(final BooleanSupplier isDocumentDirty) {
        this.isDocumentDirty = isDocumentDirty;
    }

}
