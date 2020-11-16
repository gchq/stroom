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

package stroom.search.impl;

import stroom.cluster.task.api.ClusterTaskTerminator;
import stroom.cluster.task.api.NodeNotFoundException;
import stroom.cluster.task.api.NullClusterStateException;
import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.index.impl.IndexShardService;
import stroom.index.impl.IndexStore;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.query.api.v2.Query;
import stroom.search.impl.shard.IndexShardSearchTaskExecutor;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskManager;
import stroom.task.shared.TaskId;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

class AsyncSearchTaskHandler {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AsyncSearchTaskHandler.class);

    private final TargetNodeSetFactory targetNodeSetFactory;
    private final IndexStore indexStore;
    private final IndexShardService indexShardService;
    private final TaskManager taskManager;
    private final ClusterTaskTerminator clusterTaskTerminator;
    private final SecurityContext securityContext;
    private final ExecutorProvider executorProvider;
    private final TaskContextFactory taskContextFactory;
    private final NodeService nodeService;
    private final NodeInfo nodeInfo;
    private final WebTargetFactory webTargetFactory;
    private final RemoteSearchService remoteSearchService;

    @Inject
    AsyncSearchTaskHandler(final TargetNodeSetFactory targetNodeSetFactory,
                           final IndexStore indexStore,
                           final IndexShardService indexShardService,
                           final TaskManager taskManager,
                           final ClusterTaskTerminator clusterTaskTerminator,
                           final SecurityContext securityContext,
                           final ExecutorProvider executorProvider,
                           final TaskContextFactory taskContextFactory,
                           final NodeService nodeService,
                           final NodeInfo nodeInfo,
                           final WebTargetFactory webTargetFactory,
                           final RemoteSearchService remoteSearchService) {
        this.targetNodeSetFactory = targetNodeSetFactory;
        this.indexStore = indexStore;
        this.indexShardService = indexShardService;
        this.taskManager = taskManager;
        this.clusterTaskTerminator = clusterTaskTerminator;
        this.securityContext = securityContext;
        this.executorProvider = executorProvider;
        this.taskContextFactory = taskContextFactory;
        this.nodeService = nodeService;
        this.nodeInfo = nodeInfo;
        this.webTargetFactory = webTargetFactory;
        this.remoteSearchService = remoteSearchService;
    }

    public void exec(final TaskContext parentContext, final AsyncSearchTask task) {
        securityContext.secure(() -> securityContext.useAsRead(() -> {
            final ClusterSearchResultCollector resultCollector = task.getResultCollector();

            if (!Thread.currentThread().isInterrupted()) {
                final String sourceNode = targetNodeSetFactory.getSourceNode();
                final Map<String, List<Long>> shardMap = new HashMap<>();

                try {
                    // Get the nodes that we are going to send the search request
                    // to.
                    final Set<String> targetNodes = targetNodeSetFactory.getEnabledActiveTargetNodeSet();
                    parentContext.info(() -> task.getSearchName() + " - initialising");
                    final Query query = task.getQuery();

                    // Reload the index.
                    final IndexDoc index = indexStore.readDocument(query.getDataSource());

                    // Get an array of stored index fields that will be used for
                    // getting stored data.
                    // TODO : Specify stored fields based on the fields that all
                    // coprocessors will require. Also
                    // batch search only needs stream and event id stored fields.
                    final String[] storedFields = getStoredFields(index);

                    // Get a list of search index shards to look through.
                    final FindIndexShardCriteria findIndexShardCriteria = FindIndexShardCriteria.matchAll();
                    findIndexShardCriteria.getIndexUuidSet().add(query.getDataSource().getUuid());
                    // Only non deleted indexes.
                    findIndexShardCriteria.getIndexShardStatusSet().addAll(IndexShard.NON_DELETED_INDEX_SHARD_STATUS);
                    // Order by partition name and key.
                    findIndexShardCriteria.addSort(FindIndexShardCriteria.FIELD_PARTITION, true, false);
                    findIndexShardCriteria.addSort(FindIndexShardCriteria.FIELD_ID, true, false);
                    final ResultPage<IndexShard> indexShards = indexShardService.find(findIndexShardCriteria);

                    // Build a map of nodes that will deal with each set of shards.
                    for (final IndexShard indexShard : indexShards.getValues()) {
                        if (IndexShardStatus.CORRUPT.equals(indexShard.getStatus())) {
                            resultCollector.onFailure(indexShard.getNodeName(),
                                    new SearchException("Attempt to search an index shard marked as corrupt: id=" +
                                            indexShard.getId() +
                                            "."));
                        } else {
                            final String nodeName = indexShard.getNodeName();
                            shardMap.computeIfAbsent(nodeName, k -> new ArrayList<>()).add(indexShard.getId());
                        }
                    }

                    // Start remote cluster search execution.
                    final Executor executor = executorProvider.get(IndexShardSearchTaskExecutor.THREAD_POOL);
                    final List<CompletableFuture<Void>> futures = new ArrayList<>();
                    for (final Entry<String, List<Long>> entry : shardMap.entrySet()) {
                        final String nodeName = entry.getKey();
                        final List<Long> shards = entry.getValue();
                        if (targetNodes.contains(nodeName)) {
                            final Runnable runnable = taskContextFactory.context(parentContext, "Node search", taskContext ->
                                    searchNode(sourceNode, nodeName, shards, task, query, storedFields, taskContext));
                            final CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(runnable, executor);
                            futures.add(completableFuture);
                        } else {
                            resultCollector.onFailure(nodeName,
                                    new SearchException("Node is not enabled or active. Some search results may be missing."));
                        }
                    }

                    // Wait for all nodes to finish.
                    LOGGER.debug(() -> "Waiting for completion");
                    final CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                    all.join();
                    LOGGER.debug(() -> "Done waiting for completion");

                } catch (final RuntimeException | NodeNotFoundException | NullClusterStateException e) {
                    resultCollector.onFailure(sourceNode, e);

                } finally {
                    // Make sure we try and terminate any child tasks on worker
                    // nodes if we need to.
                    terminateTasks(task, parentContext.getTaskId());

                    // Ensure search is complete even if we had errors.
                    LOGGER.debug(() -> "Search complete");
                    resultCollector.complete();

                    // We need to wait here for the client to keep getting results if
                    // this is an interactive search.
                    parentContext.info(() -> task.getSearchName() + " - staying alive for UI requests");
                }
            }
        }));
    }

    private void searchNode(final String sourceNode,
                            final String targetNode,
                            final List<Long> shards,
                            final AsyncSearchTask task,
                            final Query query,
                            final String[] storedFields,
                            final TaskContext taskContext) {
        LOGGER.debug(() -> task.getSearchName() + " - start searching node: " + targetNode);
        taskContext.info(() -> task.getSearchName() + " - start searching node: " + targetNode);
        final String queryKey = task.getKey().getUuid();
        final ClusterSearchResultCollector resultCollector = task.getResultCollector();

        // Start remote cluster search execution.
        final ClusterSearchTask clusterSearchTask = new ClusterSearchTask(
                taskContext.getTaskId(),
                "Cluster Search",
                task.getKey(),
                query,
                shards,
                storedFields,
                task.getSettings(),
                task.getDateTimeLocale(),
                task.getNow());
        LOGGER.debug(() -> "Dispatching clusterSearchTask to node: " + targetNode);
        try {
            boolean success;
            if (NodeCallUtil.shouldExecuteLocally(nodeInfo, targetNode)) {
                success = remoteSearchService.start(clusterSearchTask);
            } else {
                success = startRemoteSearch(targetNode, clusterSearchTask);
            }
            if (!success) {
                LOGGER.debug(() -> "Failed to start remote search on node: " + targetNode);
                final SearchException searchException = new SearchException("Failed to start remote search on node: " + targetNode);
                resultCollector.onFailure(targetNode, searchException);
                throw searchException;
            }
        } catch (final Throwable e) {
            final SearchException searchException = new SearchException(e.getMessage(), e);
            resultCollector.onFailure(targetNode, searchException);
            throw searchException;
        }

        try {
            LOGGER.debug(() -> task.getSearchName() + " - searching node: " + targetNode + "...");
            taskContext.info(() -> task.getSearchName() + " - searching node: " + targetNode + "...");

            // Poll for results until completion.
            boolean complete = false;
            while (!Thread.currentThread().isInterrupted() && !complete) {
                try {
                    if (NodeCallUtil.shouldExecuteLocally(nodeInfo, targetNode)) {
                        // Just transfer the payload from the local search result store.
                        byte[] bytes;
                        try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                            remoteSearchService.poll(queryKey, outputStream);
                            bytes = outputStream.toByteArray();
                        }
                        try (final InputStream inputStream = new ByteArrayInputStream(bytes)) {
                            LOGGER.debug(() -> "Receive result for node: " + targetNode);
                            complete = resultCollector.onSuccess(targetNode, inputStream);
                        }

                    } else {
                        complete = pollRemoteSearch(targetNode, queryKey, resultCollector);
                    }
                } catch (final RuntimeException | IOException e) {
                    complete = true;
                    resultCollector.onFailure(targetNode, e);
                }
            }

        } catch (final RuntimeException e) {
            resultCollector.onFailure(sourceNode, e);

        } finally {
            LOGGER.debug(() -> task.getSearchName() + " - finished searching node: " + targetNode);
            taskContext.info(() -> task.getSearchName() + " - finished searching node: " + targetNode);

            // Destroy search results.
            try {
                if (NodeCallUtil.shouldExecuteLocally(nodeInfo, targetNode)) {
                    remoteSearchService.destroy(queryKey);

                } else {
                    final boolean success = destroyRemoteSearch(targetNode, queryKey);
                    if (!success) {
                        LOGGER.debug(() -> "Failed to destroy remote search on node: " + targetNode);
                        resultCollector.onFailure(targetNode, new SearchException("Failed to destroy remote search"));
                    }
                }
            } catch (final Throwable e) {
                resultCollector.onFailure(targetNode, new SearchException(e.getMessage(), e));
            }
        }
    }

    private void terminateTasks(final AsyncSearchTask task, final TaskId taskId) {
        // Terminate this task.
        taskManager.terminate(taskId);

        // We have to wrap the cluster termination task in another task or
        // ClusterDispatchAsyncImpl
        // will not execute it if the parent task is terminated.
        clusterTaskTerminator.terminate(task.getSearchName(), taskId, "AsyncSearchTask");
    }

    private String[] getStoredFields(final IndexDoc index) {
        return index.getFields()
                .stream()
                .filter(IndexField::isStored)
                .map(IndexField::getFieldName)
                .toArray(String[]::new);
    }

    private Boolean startRemoteSearch(final String nodeName, final ClusterSearchTask clusterSearchTask) {
        final String url = NodeCallUtil.getBaseEndpointUrl(nodeInfo, nodeService, nodeName)
                + ResourcePaths.buildAuthenticatedApiPath(
                RemoteSearchResource.BASE_PATH,
                RemoteSearchResource.START_PATH_PART);

        try {
            final Response response = webTargetFactory
                    .create(url)
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.json(clusterSearchTask));
            if (response.getStatus() == Status.NOT_FOUND.getStatusCode()) {
                throw new NotFoundException(response);
            } else if (response.getStatus() != Status.OK.getStatusCode()) {
                throw new WebApplicationException(response);
            }

            return response.readEntity(Boolean.class);
        } catch (Throwable e) {
            throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
        }
    }

    private Boolean pollRemoteSearch(final String nodeName,
                                     final String queryKey,
                                     final ClusterSearchResultCollector resultCollector) throws IOException {
        boolean complete;
        final String url = NodeCallUtil.getBaseEndpointUrl(nodeInfo, nodeService, nodeName)
                + ResourcePaths.buildAuthenticatedApiPath(
                RemoteSearchResource.BASE_PATH,
                RemoteSearchResource.POLL_PATH_PART);

        try (final InputStream inputStream = webTargetFactory
                .create(url)
                .queryParam("queryKey", queryKey)
                .request(MediaType.APPLICATION_OCTET_STREAM)
                .get(InputStream.class)) {

            LOGGER.debug(() -> "Receive result for node: " + nodeName);
            complete = resultCollector.onSuccess(nodeName, inputStream);
        }
        return complete;
    }

    private Boolean destroyRemoteSearch(final String nodeName,
                                        final String queryKey) {
        final String url = NodeCallUtil.getBaseEndpointUrl(nodeInfo, nodeService, nodeName)
                + ResourcePaths.buildAuthenticatedApiPath(
                RemoteSearchResource.BASE_PATH,
                RemoteSearchResource.DESTROY_PATH_PART);

        try {
            final Response response = webTargetFactory
                    .create(url)
                    .queryParam("queryKey", queryKey)
                    .request(MediaType.APPLICATION_JSON)
                    .get();
            if (response.getStatus() == Status.NOT_FOUND.getStatusCode()) {
                throw new NotFoundException(response);
            } else if (response.getStatus() != Status.OK.getStatusCode()) {
                throw new WebApplicationException(response);
            }

            return response.readEntity(Boolean.class);
        } catch (Throwable e) {
            throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
        }
    }
}
