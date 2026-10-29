/*
 * Copyright 2016 Crown Copyright
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

package stroom.feed.client;

import stroom.core.client.ContentManager;
import stroom.core.client.UrlParameters;
import stroom.dispatch.client.RestErrorHandler;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.document.client.DocumentPlugin;
import stroom.document.client.DocumentPluginEventManager;
import stroom.entity.client.component.DocumentEditorHost;
import stroom.explorer.client.event.ExplorerTaskMonitorFactory;
import stroom.feed.client.component.FeedEditor;
import stroom.feed.shared.FeedDoc;
import stroom.security.client.api.ClientSecurityContext;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.util.client.Clipboard;

import com.google.gwt.user.client.Window;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;
import javax.inject.Singleton;

@Singleton
public class FeedPlugin extends DocumentPlugin<FeedDoc> {

    private final EventBus eventBus;
    private final Provider<FeedEditor> editorProvider;
    private final FeedClient feedClient;

    @Inject
    public FeedPlugin(final EventBus eventBus,
                      final Provider<FeedEditor> editorProvider,
                      final FeedClient feedClient,
                      final ContentManager contentManager,
                      final DocumentPluginEventManager entityPluginEventManager,
                      final ClientSecurityContext securityContext) {
        super(eventBus, contentManager, entityPluginEventManager, securityContext);
        this.eventBus = eventBus;
        this.editorProvider = editorProvider;
        this.feedClient = feedClient;

        // Handle feed open events.
        getEventBus().addHandler(OpenFeedEvent.getType(), event ->
                feedClient.getDocRefForName(event.getName(), docRef -> {
                    if (docRef != null) {
                        open(docRef, event.isForceOpen(), false, new DefaultTaskMonitorFactory(this));
                    }
                }, new ExplorerTaskMonitorFactory(this)));

        getEventBus().addHandler(CopyFeedUrlEvent.getType(), event ->
                feedClient.getDocRefForName(event.getName(), docRef -> {
                    if (docRef != null) {
                        // Generate a URL that can be used to open a new Stroom window with the target document
                        // loaded
                        final String docUrl = Window.Location.createUrlBuilder()
                                .setPath("/")
                                .setParameter(UrlParameters.ACTION, UrlParameters.OPEN_DOC_ACTION)
                                .setParameter(UrlParameters.DOC_TYPE_QUERY_PARAM, docRef.getType())
                                .setParameter(UrlParameters.DOC_UUID_QUERY_PARAM, docRef.getUuid())
                                .buildString();
                        Clipboard.writeText(docUrl);
                    }
                }, new ExplorerTaskMonitorFactory(this)));
    }

    @Override
    protected DocumentEditorHost<?> createEditor() {
        return new DocumentEditorHost<>(eventBus, editorProvider.get(), FeedDoc.TYPE);
    }

    @Override
    public void load(final DocRef docRef,
                     final Consumer<FeedDoc> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        feedClient.load(docRef, resultConsumer, errorHandler, taskMonitorFactory);
    }

    @Override
    public void save(final DocRef docRef,
                     final FeedDoc document,
                     final Consumer<FeedDoc> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        feedClient.save(document, resultConsumer, errorHandler, taskMonitorFactory);
    }

    @Override
    public String getType() {
        return FeedDoc.TYPE;
    }

    @Override
    protected DocRef getDocRef(final FeedDoc document) {
        return DocRefUtil.create(document);
    }
}
