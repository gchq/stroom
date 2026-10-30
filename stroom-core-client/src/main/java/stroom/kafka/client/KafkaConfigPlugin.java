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

package stroom.kafka.client;

import stroom.core.client.ContentManager;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.document.client.DocumentPlugin;
import stroom.document.client.DocumentPluginEventManager;
import stroom.document.client.editor.DocumentEditorHost;
import stroom.kafka.client.component.KafkaConfigEditor;
import stroom.kafka.shared.KafkaConfigDoc;
import stroom.kafka.shared.KafkaConfigResource;
import stroom.security.client.api.ClientSecurityContext;
import stroom.widget.task.client.TaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;
import javax.inject.Singleton;

@Singleton
public class KafkaConfigPlugin extends DocumentPlugin<KafkaConfigDoc> {

    private static final KafkaConfigResource KAFKA_CONFIG_RESOURCE = GWT.create(KafkaConfigResource.class);

    private final EventBus eventBus;
    private final Provider<KafkaConfigEditor> editorProvider;

    private final RestFactory restFactory;

    @Inject
    public KafkaConfigPlugin(final EventBus eventBus,
                             final Provider<KafkaConfigEditor> editorProvider,
                             final RestFactory restFactory,
                             final ContentManager contentManager,
                             final DocumentPluginEventManager entityPluginEventManager,
                             final ClientSecurityContext securityContext) {
        super(eventBus, contentManager, entityPluginEventManager, securityContext);
        this.eventBus = eventBus;
        this.editorProvider = editorProvider;
        this.restFactory = restFactory;
    }

    @Override
    protected DocumentEditorHost<?> createEditor() {
        return new DocumentEditorHost<>(eventBus, editorProvider.get(), KafkaConfigDoc.TYPE);
    }

    @Override
    public String getType() {
        return KafkaConfigDoc.TYPE;
    }

    @Override
    protected DocRef getDocRef(final KafkaConfigDoc document) {
        return DocRefUtil.create(document);
    }

    @Override
    public void load(final DocRef docRef,
                     final Consumer<KafkaConfigDoc> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(KAFKA_CONFIG_RESOURCE)
                .method(res -> res.fetch(docRef.getUuid()))
                .onSuccess(resultConsumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    @Override
    public void save(final DocRef docRef,
                     final KafkaConfigDoc document,
                     final Consumer<KafkaConfigDoc> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(KAFKA_CONFIG_RESOURCE)
                .method(res -> res.update(document.getUuid(), document))
                .onSuccess(resultConsumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }
}
