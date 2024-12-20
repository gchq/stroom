/*
 * Copyright 2022 Crown Copyright
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
 *
 */

package stroom.analytics.client;

import stroom.analytics.client.presenter.AnalyticRulePresenter;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.AnalyticRuleResource;
import stroom.core.client.ContentManager;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.document.client.ClientDocumentType;
import stroom.document.client.ClientDocumentTypeRegistry;
import stroom.document.client.DocumentPlugin;
import stroom.document.client.DocumentPluginEventManager;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.docstore.shared.DocumentTypeGroup;
import stroom.security.client.api.ClientSecurityContext;
import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;
import javax.inject.Singleton;

@Singleton
public class AnalyticsPlugin extends DocumentPlugin<AnalyticRuleDoc> {

    private static final AnalyticRuleResource ANALYTIC_RULE_RESOURCE = GWT.create(AnalyticRuleResource.class);
    public static final ClientDocumentType DOCUMENT_TYPE = new ClientDocumentType(
            DocumentTypeGroup.SEARCH,
            AnalyticRuleDoc.DOCUMENT_TYPE,
            "Analytic Rule",
            SvgImage.DOCUMENT_ANALYTIC_RULE);
    public static final ClientDocumentType ANALYTIC_DOCUMENT_TYPE = new ClientDocumentType(
            DocumentTypeGroup.SEARCH,
            "Analytics",
            "Analytics",
            SvgImage.DOCUMENT_SEARCHABLE);

    private final Provider<AnalyticRulePresenter> editorProvider;
    private final RestFactory restFactory;

    @Inject
    public AnalyticsPlugin(final EventBus eventBus,
                           final Provider<AnalyticRulePresenter> editorProvider,
                           final RestFactory restFactory,
                           final ContentManager contentManager,
                           final DocumentPluginEventManager entityPluginEventManager,
                           final ClientSecurityContext securityContext) {
        super(eventBus, contentManager, entityPluginEventManager, securityContext);
        this.editorProvider = editorProvider;
        this.restFactory = restFactory;

        ClientDocumentTypeRegistry.put(DOCUMENT_TYPE);
        ClientDocumentTypeRegistry.put(ANALYTIC_DOCUMENT_TYPE);
    }

    @Override
    protected DocumentEditPresenter<?, ?> createEditor() {
        return editorProvider.get();
    }

    @Override
    public void load(final DocRef docRef,
                     final Consumer<AnalyticRuleDoc> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANALYTIC_RULE_RESOURCE)
                .method(res -> res.fetch(docRef.getUuid()))
                .onSuccess(resultConsumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    @Override
    public void save(final DocRef docRef,
                     final AnalyticRuleDoc document,
                     final Consumer<AnalyticRuleDoc> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(ANALYTIC_RULE_RESOURCE)
                .method(res -> res.update(document.getUuid(), document))
                .onSuccess(resultConsumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    @Override
    public String getType() {
        return AnalyticRuleDoc.DOCUMENT_TYPE;
    }

    @Override
    protected DocRef getDocRef(final AnalyticRuleDoc document) {
        return DocRefUtil.create(document);
    }
}
