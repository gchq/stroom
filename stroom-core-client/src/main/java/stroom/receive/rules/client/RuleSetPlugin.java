/*
 * Copyright 2017 Crown Copyright
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

package stroom.receive.rules.client;

import stroom.core.client.ContentManager;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.ClientDocumentType;
import stroom.document.client.ClientDocumentTypeRegistry;
import stroom.document.client.DocumentPlugin;
import stroom.document.client.DocumentPluginEventManager;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.explorer.shared.DocumentTypeGroup;
import stroom.receive.rules.client.presenter.RuleSetPresenter;
import stroom.receive.rules.shared.ReceiveDataRuleSetResource;
import stroom.receive.rules.shared.ReceiveDataRules;
import stroom.security.client.api.ClientSecurityContext;
import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;
import javax.inject.Singleton;

// Not currently bound pending re-visiting receipt rules as the current implementation
// was not fully thought through
// See https://github.com/gchq/stroom/issues/1125
@Singleton
public class RuleSetPlugin extends DocumentPlugin<ReceiveDataRules> {

    private static final ReceiveDataRuleSetResource RULES_RESOURCE = GWT.create(ReceiveDataRuleSetResource.class);
    public static final ClientDocumentType DOCUMENT_TYPE = new ClientDocumentType(
            DocumentTypeGroup.CONFIGURATION,
            ReceiveDataRules.DOCUMENT_TYPE,
            "Rule Set",
            SvgImage.DOCUMENT_RECEIVE_DATA_RULE_SET);

    private final Provider<RuleSetPresenter> editorProvider;
    private final RestFactory restFactory;

    @Inject
    public RuleSetPlugin(final EventBus eventBus,
                         final Provider<RuleSetPresenter> editorProvider,
                         final RestFactory restFactory,
                         final ContentManager contentManager,
                         final DocumentPluginEventManager entityPluginEventManager,
                         final ClientSecurityContext securityContext) {
        super(eventBus, contentManager, entityPluginEventManager, securityContext);
        this.editorProvider = editorProvider;
        this.restFactory = restFactory;

        ClientDocumentTypeRegistry.put(DOCUMENT_TYPE);
    }

    @Override
    protected DocumentEditPresenter<?, ?> createEditor() {
        return editorProvider.get();
    }

    @Override
    public void load(final DocRef docRef,
                     final Consumer<ReceiveDataRules> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RULES_RESOURCE)
                .method(res -> res.fetch(docRef.getUuid()))
                .onSuccess(resultConsumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    @Override
    public void save(final DocRef docRef,
                     final ReceiveDataRules document,
                     final Consumer<ReceiveDataRules> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RULES_RESOURCE)
                .method(res -> res.update(document.getUuid(), document))
                .onSuccess(resultConsumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    @Override
    public String getType() {
        return ReceiveDataRules.DOCUMENT_TYPE;
    }

    @Override
    protected DocRef getDocRef(final ReceiveDataRules document) {
        return new DocRef(document.getType(), document.getUuid(), document.getName());
    }
}
