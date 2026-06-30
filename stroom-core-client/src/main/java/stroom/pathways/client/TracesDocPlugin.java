/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.pathways.client;

import stroom.core.client.ContentManager;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.document.client.DocumentPlugin;
import stroom.document.client.DocumentPluginEventManager;
import stroom.entity.client.presenter.DocPresenter;
import stroom.pathways.client.presenter.TracesPresenter;
import stroom.pathways.shared.TracesDoc;
import stroom.pathways.shared.TracesDocResource;
import stroom.planb.shared.SharedFileStoreSettings;
import stroom.planb.shared.TraceSettings;
import stroom.security.client.api.ClientSecurityContext;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;
import javax.inject.Singleton;

@Singleton
public class TracesDocPlugin extends DocumentPlugin<TracesDoc> {

    private static final TracesDocResource TRACES_DOC_RESOURCE = GWT.create(TracesDocResource.class);

    private final Provider<TracesPresenter> editorProvider;
    private final RestFactory restFactory;

    @Inject
    public TracesDocPlugin(
            final EventBus eventBus,
            final Provider<TracesPresenter> editorProvider,
            final RestFactory restFactory,
            final ContentManager contentManager,
            final DocumentPluginEventManager entityPluginEventManager,
            final ClientSecurityContext securityContext) {
        super(eventBus, contentManager, entityPluginEventManager, securityContext);
        this.editorProvider = editorProvider;
        this.restFactory = restFactory;
    }

    @Override
    protected DocPresenter<?, ?> createEditor() {
        return editorProvider.get();
    }

    @Override
    public void load(final DocRef docRef,
                     final Consumer<TracesDoc> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(TRACES_DOC_RESOURCE)
                .method(res -> res.fetch(docRef.getUuid()))
                .onSuccess(resultConsumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    @Override
    public void save(final DocRef docRef,
                     final TracesDoc document,
                     final Consumer<TracesDoc> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(TRACES_DOC_RESOURCE)
                .method(res -> res.update(document.getUuid(), document))
                .onSuccess(resultConsumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    @Override
    public String getType() {
        return TracesDoc.TYPE;
    }

    @Override
    protected DocRef getDocRef(final TracesDoc document) {
        return DocRefUtil.create(document);
    }

    /**
     * Block the save and display a warning if the Enable Shared File Store checkbox
     * is checked but no path was entered.
     *
     * <p>After {@link DocPresenter#write}, an enabled-but-pathless shared file store
     * is represented as {@code ShardingSettings{shardCount>0, sharedPath=blank}}.
     */
    @Override
    protected String getPreSaveError(final TracesDoc doc) {
        if (doc.getSettings() instanceof final TraceSettings settings) {
            final SharedFileStoreSettings sfs = settings.getSharedFileStore();
            if (sfs != null
                    && sfs.getShardCount() > 0
                    && (sfs.getSharedPath() == null || sfs.getSharedPath().isBlank())) {
                return "A path must be provided when the shared file store is enabled.";
            }
        }
        return null;
    }
}
