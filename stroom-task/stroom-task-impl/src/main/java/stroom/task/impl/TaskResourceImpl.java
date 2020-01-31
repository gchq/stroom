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

package stroom.task.impl;

import com.codahale.metrics.health.HealthCheck.Result;
import stroom.event.logging.api.DocumentEventLog;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.task.api.TaskManager;
import stroom.task.shared.FindTaskProgressCriteria;
import stroom.task.shared.FindTaskProgressRequest;
import stroom.task.shared.TaskProgressResponse;
import stroom.task.shared.TaskResource;
import stroom.task.shared.TerminateTaskProgressRequest;
import stroom.util.HasHealthCheck;
import stroom.util.guice.ResourcePaths;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.servlet.SessionIdProvider;
import stroom.util.shared.RestResource;
import stroom.util.shared.Sort.Direction;

import javax.inject.Inject;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

// TODO : @66 add event logging
class TaskResourceImpl implements TaskResource, RestResource, HasHealthCheck {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TaskResourceImpl.class);

    private final TaskManager taskManager;
    private final SessionIdProvider sessionIdProvider;
    private final NodeService nodeService;
    private final NodeInfo nodeInfo;
    private final WebTargetFactory webTargetFactory;
    private final DocumentEventLog documentEventLog;

    @Inject
    TaskResourceImpl(final TaskManager taskManager,
                             final SessionIdProvider sessionIdProvider,
                             final NodeService nodeService,
                             final NodeInfo nodeInfo,
                             final WebTargetFactory webTargetFactory,
                             final DocumentEventLog documentEventLog) {
        this.taskManager = taskManager;
        this.sessionIdProvider = sessionIdProvider;
        this.nodeService = nodeService;
        this.nodeInfo = nodeInfo;
        this.webTargetFactory = webTargetFactory;
        this.documentEventLog = documentEventLog;
    }

    @Override
    public TaskProgressResponse list(final String nodeName) {
        return find(nodeName, new FindTaskProgressRequest());
    }

    @Override
    public TaskProgressResponse find(final String nodeName, final FindTaskProgressRequest request) {
        TaskProgressResponse result = null;
        try {
            // If this is the node that was contacted then just return our local info.
            if (nodeInfo.getThisNodeName().equals(nodeName)) {
                result = taskManager.find(request.getCriteria()).toResultPage(new TaskProgressResponse());

            } else {
                String url = NodeCallUtil.getUrl(nodeService, nodeName);
                url += ResourcePaths.API_ROOT_PATH + "/task/" + nodeName;
                final Response response = webTargetFactory
                        .create(url)
                        .request(MediaType.APPLICATION_JSON)
                        .post(Entity.json(request));
                if (response.getStatus() != 200) {
                    throw new WebApplicationException(response);
                }
                result = response.readEntity(TaskProgressResponse.class);
                if (result == null) {
                    throw new RuntimeException("Unable to contact node \"" + nodeName + "\" at URL: " + url);
                }
            }

        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw new ServerErrorException(Status.INTERNAL_SERVER_ERROR, e);
        }

        return result;
    }

    @Override
    public TaskProgressResponse userTasks(final String nodeName) {
        try {
            final String sessionId = sessionIdProvider.get();
            if (sessionId != null) {
                final FindTaskProgressCriteria criteria = new FindTaskProgressCriteria();
                criteria.setSort(FindTaskProgressCriteria.FIELD_AGE, Direction.DESCENDING, false);
                criteria.setSessionId(sessionId);
                final FindTaskProgressRequest findTaskProgressRequest = new FindTaskProgressRequest(criteria);
                return find(nodeName, findTaskProgressRequest);
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw new ServerErrorException(Status.INTERNAL_SERVER_ERROR, e);
        }
        return null;
    }

    @Override
    public Boolean terminate(final String nodeName, final TerminateTaskProgressRequest request) {
        try {
            // If this is the node that was contacted then just return our local info.
            if (nodeInfo.getThisNodeName().equals(nodeName)) {
                taskManager.terminate(request.getCriteria(), request.isKill());

            } else {
                String url = NodeCallUtil.getUrl(nodeService, nodeName);
                url += ResourcePaths.API_ROOT_PATH + "/task/" + nodeName + "/terminate";
                final Response response = webTargetFactory
                        .create(url)
                        .request(MediaType.APPLICATION_JSON)
                        .post(Entity.json(request));
                if (response.getStatus() != 200) {
                    throw new WebApplicationException(response);
                }
            }

        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw new ServerErrorException(Status.INTERNAL_SERVER_ERROR, e);
        }

        return true;
    }

    @Override
    public Result getHealth() {
        return Result.healthy();
    }
}