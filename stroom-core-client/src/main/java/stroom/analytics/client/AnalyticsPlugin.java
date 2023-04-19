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
public class AnalyticsPlugin extends DocumentPlugin<AnalyticRuleDoc> {

    private static final AnalyticRuleResource ALERT_RULE_RESOURCE = GWT.create(AnalyticRuleResource.class);

    private final Provider<AnalyticRulePresenter> editorProvider;
    private final RestFactory restFactory;

    @Inject
    public AnalyticsPlugin(final EventBus eventBus,
                           final Provider<AnalyticRulePresenter> editorProvider,
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
                     final Consumer<AnalyticRuleDoc> resultConsumer,
                     final Consumer<Throwable> errorConsumer) {
        final Rest<AnalyticRuleDoc> rest = restFactory.create();
        rest
                .onSuccess(resultConsumer)
                .onFailure(errorConsumer)
                .call(ALERT_RULE_RESOURCE)
                .fetch(docRef.getUuid());
    }

    @Override
    public void save(final DocRef docRef,
                     final AnalyticRuleDoc document,
                     final Consumer<AnalyticRuleDoc> resultConsumer,
                     final Consumer<Throwable> errorConsumer) {
        final Rest<AnalyticRuleDoc> rest = restFactory.create();
        rest
                .onSuccess(resultConsumer)
                .onFailure(errorConsumer)
                .call(ALERT_RULE_RESOURCE)
                .update(document.getUuid(), document);
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
