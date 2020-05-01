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

package stroom.processor.impl;

import com.codahale.metrics.health.HealthCheck.Result;
import event.logging.BaseAdvancedQueryOperator.And;
import event.logging.Query;
import event.logging.Query.Advanced;
import stroom.entity.shared.ExpressionCriteria;
import stroom.event.logging.api.DocumentEventLog;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.AssignTasksRequest;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskList;
import stroom.processor.shared.ProcessorTaskResource;
import stroom.processor.shared.ProcessorTaskSummary;
import stroom.util.HasHealthCheck;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

class ProcessorTaskResourceImpl implements ProcessorTaskResource, HasHealthCheck {
    private final ProcessorTaskService processorTaskService;
    private final DocumentEventLog documentEventLog;
    private final NodeService nodeService;
    private final NodeInfo nodeInfo;
    private final WebTargetFactory webTargetFactory;
    private final ProcessorTaskManager processorTaskManager;

    @Inject
    ProcessorTaskResourceImpl(final ProcessorTaskService processorTaskService,
                              final DocumentEventLog documentEventLog,
                              final NodeService nodeService,
                              final NodeInfo nodeInfo,
                              final WebTargetFactory webTargetFactory,
                              final ProcessorTaskManager processorTaskManager) {
        this.processorTaskService = processorTaskService;
        this.documentEventLog = documentEventLog;
        this.nodeService = nodeService;
        this.nodeInfo = nodeInfo;
        this.webTargetFactory = webTargetFactory;
        this.processorTaskManager = processorTaskManager;
    }

    @Override
    public ResultPage<ProcessorTask> find(final ExpressionCriteria criteria) {
        ResultPage<ProcessorTask> result;

        final Query query = new Query();
        final Advanced advanced = new Advanced();
        query.setAdvanced(advanced);
        final And and = new And();
        advanced.getAdvancedQueryItems().add(and);

        try {
            result = processorTaskService.find(criteria);
            documentEventLog.search(criteria.getClass().getSimpleName(), query, ProcessorTask.class.getSimpleName(), result.getPageResponse(), null);
        } catch (final RuntimeException e) {
            documentEventLog.search(criteria.getClass().getSimpleName(), query, ProcessorTask.class.getSimpleName(), null, e);
            throw e;
        }

        return result;
    }

    @Override
    public ResultPage<ProcessorTaskSummary> findSummary(final ExpressionCriteria criteria) {
        ResultPage<ProcessorTaskSummary> result;

        final Query query = new Query();
        final Advanced advanced = new Advanced();
        query.setAdvanced(advanced);
        final And and = new And();
        advanced.getAdvancedQueryItems().add(and);

        try {
            result = processorTaskService.findSummary(criteria);
            documentEventLog.search(criteria.getClass().getSimpleName(), query, ProcessorTaskSummary.class.getSimpleName(), result.getPageResponse(), null);
        } catch (final RuntimeException e) {
            documentEventLog.search(criteria.getClass().getSimpleName(), query, ProcessorTaskSummary.class.getSimpleName(), null, e);
            throw e;
        }

        return result;
    }

    @Override
    public ProcessorTaskList assignTasks(final String nodeName, final AssignTasksRequest request) {
        // If this is the node that was contacted then just return the latency we have incurred within this method.
        if (NodeCallUtil.shouldExecuteLocally(nodeInfo, nodeName)) {
            return processorTaskManager.assignTasks(request.getNodeName(), request.getCount());
        } else {
            final String url = NodeCallUtil.getBaseEndpointUrl(nodeService, nodeName)
                    + ResourcePaths.buildAuthenticatedApiPath(
                    ProcessorTaskResource.BASE_PATH,
                    ProcessorTaskResource.ASSIGN_TASKS_PATH_PART,
                    nodeName);

            try {
                final Response response = webTargetFactory
                        .create(url)
                        .request(MediaType.APPLICATION_JSON)
                        .post(Entity.json(request));
                if (response.getStatus() != 200) {
                    throw new WebApplicationException(response);
                }
                return response.readEntity(ProcessorTaskList.class);
            } catch (Throwable e) {
                throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
            }
        }
    }

    @Override
    public Boolean abandonTasks(final String nodeName, final ProcessorTaskList request) {
        // If this is the node that was contacted then just return the latency we have incurred within this method.
        if (NodeCallUtil.shouldExecuteLocally(nodeInfo, nodeName)) {
            return processorTaskManager.abandonTasks(request);
        } else {
            final String url = NodeCallUtil.getBaseEndpointUrl(nodeService, nodeName)
                    + ResourcePaths.buildAuthenticatedApiPath(
                    ProcessorTaskResource.BASE_PATH,
                    ProcessorTaskResource.ABANDON_TASKS_PATH_PART,
                    nodeName);

            try {
                final Response response = webTargetFactory
                        .create(url)
                        .request(MediaType.APPLICATION_JSON)
                        .put(Entity.json(request));
                if (response.getStatus() != 200) {
                    throw new WebApplicationException(response);
                }
                return response.readEntity(Boolean.class);
            } catch (Throwable e) {
                throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
            }
        }
    }

    @Override
    public Result getHealth() {
        return Result.healthy();
    }
}