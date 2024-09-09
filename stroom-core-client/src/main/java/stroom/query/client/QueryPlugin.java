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

package stroom.query.client;

import stroom.core.client.ContentManager;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.document.client.DocumentPlugin;
import stroom.document.client.DocumentPluginEventManager;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.query.client.presenter.QueryDocPresenter;
import stroom.query.shared.QueryDoc;
import stroom.query.shared.QueryResource;
import stroom.security.client.api.ClientSecurityContext;
import stroom.task.client.TaskHandlerFactory;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;
import javax.inject.Singleton;

@Singleton
public class QueryPlugin extends DocumentPlugin<QueryDoc> {

    private static final QueryResource QUERY_RESOURCE = GWT.create(QueryResource.class);

    private final Provider<QueryDocPresenter> editorProvider;
    private final RestFactory restFactory;

    @Inject
    public QueryPlugin(final EventBus eventBus,
                       final Provider<QueryDocPresenter> editorProvider,
                       final RestFactory restFactory,
                       final ContentManager contentManager,
                       final DocumentPluginEventManager entityPluginEventManager,
                       final ClientSecurityContext securityContext) {
        super(eventBus, contentManager, entityPluginEventManager, securityContext);
        this.editorProvider = editorProvider;
        this.restFactory = restFactory;
    }

    @Override
    protected DocumentEditPresenter<?, ?> createEditor() {
        return editorProvider.get();
    }

    @Override
    public void load(final DocRef docRef,
                     final Consumer<QueryDoc> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskHandlerFactory taskHandlerFactory) {
        restFactory
                .create(QUERY_RESOURCE)
                .method(res -> res.fetch(docRef.getUuid()))
                .onSuccess(resultConsumer)
                .onFailure(errorHandler)
                .taskHandlerFactory(taskHandlerFactory)
                .exec();
    }

    @Override
    public void save(final DocRef docRef,
                     final QueryDoc document,
                     final Consumer<QueryDoc> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskHandlerFactory taskHandlerFactory) {
        restFactory
                .create(QUERY_RESOURCE)
                .method(res -> res.update(document.getUuid(), document))
                .onSuccess(resultConsumer)
                .onFailure(errorHandler)
                .taskHandlerFactory(taskHandlerFactory)
                .exec();
    }

    @Override
    public String getType() {
        return QueryDoc.DOCUMENT_TYPE;
    }

    @Override
    protected DocRef getDocRef(final QueryDoc document) {
        return DocRefUtil.create(document);
    }
}
