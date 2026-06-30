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

package stroom.pathways.impl;

import stroom.docref.DocRef;
import stroom.node.api.NodeInfo;
import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.TracesDoc;
import stroom.planb.impl.data.ShardMergeEventData;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Singleton
@EntityEventHandler(
        type = TracesDoc.TYPE,
        action = {EntityAction.UPDATE}
)
public class PathwaysEntityEventHandler implements EntityEvent.Handler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PathwaysEntityEventHandler.class);

    private final Provider<PathwaysStore> pathwaysStoreProvider;
    private final Provider<PathwaysProcessor> pathwaysProcessorProvider;
    private final TaskContextFactory taskContextFactory;
    private final SecurityContext securityContext;
    private final Executor executor;

    @Inject
    public PathwaysEntityEventHandler(final Provider<PathwaysStore> pathwaysStoreProvider,
                                      final Provider<PathwaysProcessor> pathwaysProcessorProvider,
                                      final TaskContextFactory taskContextFactory,
                                      final SecurityContext securityContext,
                                      final ExecutorProvider executorProvider) {
        this.pathwaysStoreProvider = pathwaysStoreProvider;
        this.pathwaysProcessorProvider = pathwaysProcessorProvider;
        this.taskContextFactory = taskContextFactory;
        this.securityContext = securityContext;
        this.executor = executorProvider.get();
    }

    @Override
    public void onChange(final EntityEvent event) {
        LOGGER.debug("onChange - event: {}", event);

        // Only react if this UPDATE carries ShardMergeEventData payload
        if (event == null || !event.hasDataClass(ShardMergeEventData.class)) {
            return;
        }

        final DocRef mergedTracesDocRef = event.getDocRef();
        final ShardMergeEventData eventData = event.getDataObject(ShardMergeEventData.class);
        if (eventData == null) {
            return;
        }

        final int shardIndex = eventData.getShardIndex();
        final String batchDirName = eventData.getBatchDirName();

        LOGGER.info("Received shard merge event for Traces: {}, Shard: {}, Batch: {}",
                mergedTracesDocRef.getName(), shardIndex, batchDirName);

        final PathwaysStore pathwaysStore = pathwaysStoreProvider.get();
        final PathwaysProcessor pathwaysProcessor = pathwaysProcessorProvider.get();

        // Find matching pathways docs that reference the merged traces doc
        for (final DocRef pathwaysDocRef : NullSafe.list(pathwaysStore.list())) {
            final PathwaysDoc pathwaysDoc = pathwaysStore.readDocument(pathwaysDocRef);
            if (pathwaysDoc != null && Objects.equals(pathwaysDoc.getTracesDocRef(), mergedTracesDocRef)) {
                LOGGER.info("Triggering pathways processing for doc {}", pathwaysDoc.getName());

                // Run pathways processing asynchronously to avoid blocking the event bus thread pool
                final Runnable runnable = taskContextFactory.context(
                        "Process Pathways for " + pathwaysDoc.getName(),
                        taskContext -> securityContext.asProcessingUser(() ->
                                pathwaysProcessor.exec(pathwaysDoc)
                        )
                );
                CompletableFuture.runAsync(runnable, executor);
            }
        }
    }
}
