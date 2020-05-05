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

import stroom.event.logging.api.DocumentEventLog;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.task.shared.FindTaskProgressCriteria;
import stroom.task.shared.FindTaskProgressRequest;
import stroom.task.shared.TaskProgress;
import stroom.task.shared.TaskProgressResponse;
import stroom.task.shared.TaskResource;
import stroom.task.shared.TerminateTaskProgressRequest;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.servlet.SessionIdProvider;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Sort.Direction;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

// TODO : @66 add event logging
class TaskResourceImpl implements TaskResource {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TaskResourceImpl.class);

    private final TaskManagerImpl taskManager;
    private final SessionIdProvider sessionIdProvider;
    private final NodeService nodeService;
    private final NodeInfo nodeInfo;
    private final WebTargetFactory webTargetFactory;
    private final DocumentEventLog documentEventLog;

    @Inject
    TaskResourceImpl(final TaskManagerImpl taskManager,
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
        return find(nodeName, new FindTaskProgressRequest(new FindTaskProgressCriteria()));
    }

    @Override
    public TaskProgressResponse find(final String nodeName, final FindTaskProgressRequest request) {
        TaskProgressResponse result = null;
        // If this is the node that was contacted then just return our local info.
        if (NodeCallUtil.shouldExecuteLocally(nodeInfo, nodeName)) {
            final ResultPage<TaskProgress> resultPage = taskManager.find(request.getCriteria());
            result = new TaskProgressResponse(resultPage.getValues(), resultPage.getPageResponse());

        } else {
            final String url = NodeCallUtil.getBaseEndpointUrl(nodeService, nodeName)
                    + ResourcePaths.buildAuthenticatedApiPath(
                    TaskResource.BASE_PATH,
                    TaskResource.FIND_PATH_PART,
                    nodeName);

            try {
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
            } catch (final Throwable e) {
                throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
            }
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
            throw e;
        }
        return null;
    }

    @Override
    public Boolean terminate(final String nodeName, final TerminateTaskProgressRequest request) {

        // If this is the node that was contacted then just return our local info.
        if (NodeCallUtil.shouldExecuteLocally(nodeInfo, nodeName)) {
            taskManager.terminate(request.getCriteria(), request.isKill());
        } else {
            final String url = NodeCallUtil.getBaseEndpointUrl(nodeService, nodeName)
                    + ResourcePaths.buildAuthenticatedApiPath(
                    TaskResource.BASE_PATH,
                    TaskResource.TERMINATE_PATH_PART,
                    nodeName);

            try {
                final Response response = webTargetFactory
                        .create(url)
                        .request(MediaType.APPLICATION_JSON)
                        .post(Entity.json(request));
                if (response.getStatus() != 200) {
                    throw new WebApplicationException(response);
                }
            } catch (Throwable e) {
                throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
            }
        }

        return true;
    }
}