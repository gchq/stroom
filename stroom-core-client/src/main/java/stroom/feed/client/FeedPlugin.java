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

package stroom.feed.client;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.core.client.ContentManager;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.document.client.DocumentPlugin;
import stroom.document.client.DocumentPluginEventManager;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.feed.client.presenter.FeedPresenter;
import stroom.feed.shared.FeedDoc;
import stroom.feed.shared.FeedResource;

import java.util.function.Consumer;

public class FeedPlugin extends DocumentPlugin<FeedDoc> {
    private static final FeedResource FEED_RESOURCE = GWT.create(FeedResource.class);

    private final Provider<FeedPresenter> editorProvider;
    private final RestFactory restFactory;

    @Inject
    public FeedPlugin(final EventBus eventBus,
                      final Provider<FeedPresenter> editorProvider,
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
    public void load(final DocRef docRef, final Consumer<FeedDoc> resultConsumer, final Consumer<Throwable> errorConsumer) {
        final Rest<FeedDoc> rest = restFactory.create();
        rest
                .onSuccess(resultConsumer)
                .onFailure(errorConsumer)
                .call(FEED_RESOURCE)
                .read(docRef);
    }

    @Override
    public void save(final DocRef docRef, final FeedDoc document, final Consumer<FeedDoc> resultConsumer, final Consumer<Throwable> errorConsumer) {
        final Rest<FeedDoc> rest = restFactory.create();
        rest
                .onSuccess(resultConsumer)
                .onFailure(errorConsumer)
                .call(FEED_RESOURCE)
                .update(document);
    }

    @Override
    public String getType() {
        return FeedDoc.DOCUMENT_TYPE;
    }

    @Override
    protected DocRef getDocRef(final FeedDoc document) {
        return DocRefUtil.create(document);
    }
}
