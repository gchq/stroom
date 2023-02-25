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

package stroom.alert.rule.client;

import stroom.alert.rule.client.presenter.AlertRulePresenter;
import stroom.alert.rule.shared.AlertRuleDoc;
import stroom.alert.rule.shared.AlertRuleResource;
import stroom.core.client.ContentManager;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.document.client.DocumentPlugin;
import stroom.document.client.DocumentPluginEventManager;
import stroom.entity.client.presenter.DocumentEditPresenter;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;
import javax.inject.Singleton;

@Singleton
public class AlertRulePlugin extends DocumentPlugin<AlertRuleDoc> {

    private static final AlertRuleResource ALERT_RULE_RESOURCE = GWT.create(AlertRuleResource.class);

    private final Provider<AlertRulePresenter> editorProvider;
    private final RestFactory restFactory;

    @Inject
    public AlertRulePlugin(final EventBus eventBus,
                      final Provider<AlertRulePresenter> editorProvider,
                      final RestFactory restFactory,
                      final ContentManager contentManager,
                      final DocumentPluginEventManager entityPluginEventManager) {
        super(eventBus, contentManager, entityPluginEventManager);
        this.editorProvider = editorProvider;
        this.restFactory = restFactory;
    }

    @Override
    protected DocumentEditPresenter<?, ?> createEditor() {
        return editorProvider.get();
    }

    @Override
    public void load(final DocRef docRef,
                     final Consumer<AlertRuleDoc> resultConsumer,
                     final Consumer<Throwable> errorConsumer) {
        final Rest<AlertRuleDoc> rest = restFactory.create();
        rest
                .onSuccess(resultConsumer)
                .onFailure(errorConsumer)
                .call(ALERT_RULE_RESOURCE)
                .fetch(docRef.getUuid());
    }

    @Override
    public void save(final DocRef docRef,
                     final AlertRuleDoc document,
                     final Consumer<AlertRuleDoc> resultConsumer,
                     final Consumer<Throwable> errorConsumer) {
        final Rest<AlertRuleDoc> rest = restFactory.create();
        rest
                .onSuccess(resultConsumer)
                .onFailure(errorConsumer)
                .call(ALERT_RULE_RESOURCE)
                .update(document.getUuid(), document);
    }

    @Override
    public String getType() {
        return AlertRuleDoc.DOCUMENT_TYPE;
    }

    @Override
    protected DocRef getDocRef(final AlertRuleDoc document) {
        return DocRefUtil.create(document);
    }
}
