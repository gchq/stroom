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
 */

package stroom.search.impl;

import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.query.api.v2.QueryKey;
import stroom.query.common.v2.NodeResult;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskManager;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResourcePaths;

import io.swagger.annotations.Api;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.Consumes;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Api(value = "remoteSearch - /v1")
@Path("/remoteSearch" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RemoteSearchResourceImpl implements RemoteSearchResource {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RemoteSearchManager.class);

    private final NodeService nodeService;
    private final NodeInfo nodeInfo;
    private final WebTargetFactory webTargetFactory;
    private final RemoteSearchResults remoteSearchResults;
    private final TaskManager taskManager;
    private final ExecutorProvider executorProvider;
    private final TaskContextFactory taskContextFactory;
    private final Provider<ClusterSearchTaskHandler> clusterSearchTaskHandlerProvider;

    @Inject
    RemoteSearchResourceImpl(final NodeService nodeService,
                             final NodeInfo nodeInfo,
                             final WebTargetFactory webTargetFactory,
                             final RemoteSearchResults remoteSearchResults,
                             final TaskManager taskManager,
                             final ExecutorProvider executorProvider,
                             final TaskContextFactory taskContextFactory,
                             final Provider<ClusterSearchTaskHandler> clusterSearchTaskHandlerProvider) {
        this.nodeService = nodeService;
        this.nodeInfo = nodeInfo;
        this.webTargetFactory = webTargetFactory;
        this.remoteSearchResults = remoteSearchResults;
        this.taskManager = taskManager;
        this.executorProvider = executorProvider;
        this.taskContextFactory = taskContextFactory;
        this.clusterSearchTaskHandlerProvider = clusterSearchTaskHandlerProvider;
    }

    @Override
    public Boolean start(final String nodeName,
                         final ClusterSearchTask clusterSearchTask) {
        if (NodeCallUtil.shouldExecuteLocally(nodeInfo, nodeName)) {
            return start(clusterSearchTask);

        } else {
            return (Boolean) call(nodeName, RemoteSearchResource.START_PATH_PART, clusterSearchTask);
        }
    }

    private Boolean start(final ClusterSearchTask clusterSearchTask) {
        LOGGER.debug(() -> "startSearch " + clusterSearchTask);
        final Runnable runnable = taskContextFactory.context(clusterSearchTask.getTaskName(), taskContext -> {
            final RemoteSearchResultFactory remoteSearchResultFactory = new RemoteSearchResultFactory(taskManager);
            taskContext.getTaskId().setParentId(clusterSearchTask.getSourceTaskId());
            remoteSearchResults.put(clusterSearchTask.getKey(), remoteSearchResultFactory);
            final ClusterSearchTaskHandler clusterSearchTaskHandler = clusterSearchTaskHandlerProvider.get();
            clusterSearchTaskHandler.exec(taskContext, clusterSearchTask, remoteSearchResultFactory);
        });
        final Executor executor = executorProvider.get();
        CompletableFuture.runAsync(runnable, executor);
        return true;
    }

    @Override
    public NodeResult poll(final String nodeName, final QueryKey key) {
        if (NodeCallUtil.shouldExecuteLocally(nodeInfo, nodeName)) {
            return poll(key);

        } else {
            return (NodeResult) call(nodeName, RemoteSearchResource.POLL_PATH_PART, key);
        }
    }

    private NodeResult poll(final QueryKey key) {
        LOGGER.debug(() -> "poll " + key);
        final Optional<RemoteSearchResultFactory> optional = remoteSearchResults.get(key);
        return optional.map(RemoteSearchResultFactory::create).orElse(null);
    }

    @Override
    public Boolean destroy(final String nodeName, final QueryKey key) {
        if (NodeCallUtil.shouldExecuteLocally(nodeInfo, nodeName)) {
            return destroy(key);

        } else {
            return (Boolean) call(nodeName, RemoteSearchResource.DESTROY_PATH_PART, key);
        }
    }

    private Boolean destroy(final QueryKey key) {
        LOGGER.debug(() -> "destroy " + key);
        remoteSearchResults.invalidate(key);
        return true;
    }

    private Object call(final String nodeName, final String path, final Object entity) {
        final String url = NodeCallUtil.getBaseEndpointUrl(nodeInfo, nodeService, nodeName)
                + ResourcePaths.buildAuthenticatedApiPath(
                RemoteSearchResource.BASE_PATH,
                path,
                nodeName);

        try {
            final Response response = webTargetFactory
                    .create(url)
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.json(entity));
            if (response.getStatus() != 200) {
                throw new WebApplicationException(response);
            }
            return response.getEntity();
        } catch (Throwable e) {
            throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
        }
    }
}