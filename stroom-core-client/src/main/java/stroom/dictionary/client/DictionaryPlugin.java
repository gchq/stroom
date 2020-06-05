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

package stroom.dictionary.client;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.core.client.ContentManager;
import stroom.dictionary.client.presenter.DictionaryPresenter;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.dictionary.shared.DictionaryResource;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.document.client.DocumentPlugin;
import stroom.document.client.DocumentPluginEventManager;
import stroom.entity.client.presenter.DocumentEditPresenter;

import java.util.function.Consumer;

public class DictionaryPlugin extends DocumentPlugin<DictionaryDoc> {
    private static final DictionaryResource DICTIONARY_RESOURCE = GWT.create(DictionaryResource.class);

    private final Provider<DictionaryPresenter> editorProvider;
    private final RestFactory restFactory;

    @Inject
    public DictionaryPlugin(final EventBus eventBus,
                            final Provider<DictionaryPresenter> editorProvider,
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
    public void load(final DocRef docRef, final Consumer<DictionaryDoc> resultConsumer, final Consumer<Throwable> errorConsumer) {
        final Rest<DictionaryDoc> rest = restFactory.create();
        rest
                .onSuccess(resultConsumer)
                .onFailure(errorConsumer)
                .call(DICTIONARY_RESOURCE)
                .read(docRef);
    }

    @Override
    public void save(final DocRef docRef, final DictionaryDoc document, final Consumer<DictionaryDoc> resultConsumer, final Consumer<Throwable> errorConsumer) {
        final Rest<DictionaryDoc> rest = restFactory.create();
        rest
                .onSuccess(resultConsumer)
                .onFailure(errorConsumer)
                .call(DICTIONARY_RESOURCE)
                .update(document);
    }

    @Override
    public String getType() {
        return DictionaryDoc.DOCUMENT_TYPE;
    }

    @Override
    protected DocRef getDocRef(final DictionaryDoc document) {
        return DocRefUtil.create(document);
    }
}
