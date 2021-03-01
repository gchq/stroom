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

import stroom.entity.shared.ExpressionCriteria;
import stroom.event.logging.api.DocumentEventLog;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.node.api.NodeService;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.AssignTasksRequest;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskList;
import stroom.processor.shared.ProcessorTaskResource;
import stroom.processor.shared.ProcessorTaskSummary;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.ResultPage;

import event.logging.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.client.Entity;

class ProcessorTaskResourceImpl implements ProcessorTaskResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessorTaskResourceImpl.class);

    private final ProcessorTaskService processorTaskService;
    private final DocumentEventLog documentEventLog;
    private final NodeService nodeService;
    private final ProcessorTaskManager processorTaskManager;

    @Inject
    ProcessorTaskResourceImpl(final ProcessorTaskService processorTaskService,
                              final DocumentEventLog documentEventLog,
                              final NodeService nodeService,
                              final ProcessorTaskManager processorTaskManager) {
        this.processorTaskService = processorTaskService;
        this.documentEventLog = documentEventLog;
        this.nodeService = nodeService;
        this.processorTaskManager = processorTaskManager;
    }

    @Override
    public ResultPage<ProcessorTask> find(final ExpressionCriteria criteria) {
        ResultPage<ProcessorTask> result;

        final Query.Builder<Void> queryBuilder = Query.builder();
        StroomEventLoggingUtil.appendExpression(queryBuilder, criteria.getExpression());
        final Query query = queryBuilder.build();

        try {
            result = processorTaskService.find(criteria);
            documentEventLog.search(
                    criteria.getClass().getSimpleName(),
                    query,
                    ProcessorTask.class.getSimpleName(),
                    result.getPageResponse(),
                    null);
        } catch (final RuntimeException e) {
            documentEventLog.search(
                    criteria.getClass().getSimpleName(),
                    query,
                    ProcessorTask.class.getSimpleName(),
                    null,
                    e);
            throw e;
        }

        return result;
    }

    @Override
    public ResultPage<ProcessorTaskSummary> findSummary(final ExpressionCriteria criteria) {
        ResultPage<ProcessorTaskSummary> result;

        final Query.Builder<Void> queryBuilder = Query.builder();
        StroomEventLoggingUtil.appendExpression(queryBuilder, criteria.getExpression());
        final Query query = queryBuilder.build();

        try {
            result = processorTaskService.findSummary(criteria);
            documentEventLog.search(
                    criteria.getClass().getSimpleName(),
                    query,
                    ProcessorTaskSummary.class.getSimpleName(),
                    result.getPageResponse(),
                    null);
        } catch (final RuntimeException e) {
            documentEventLog.search(
                    criteria.getClass().getSimpleName(),
                    query,
                    ProcessorTaskSummary.class.getSimpleName(),
                    null,
                    e);
            throw e;
        }

        return result;
    }

    @Override
    public ProcessorTaskList assignTasks(final String nodeName, final AssignTasksRequest request) {
        LOGGER.debug("assignTasks called for nodeName: {}, {}", nodeName, request);
        final ProcessorTaskList processorTaskList = nodeService.remoteRestResult(
                nodeName,
                ProcessorTaskList.class,
                () -> ResourcePaths.buildAuthenticatedApiPath(
                        ProcessorTaskResource.BASE_PATH,
                        ProcessorTaskResource.ASSIGN_TASKS_PATH_PART,
                        nodeName),
                () ->
                        processorTaskManager.assignTasks(request.getNodeName(), request.getCount()),
                builder ->
                        builder.post(Entity.json(request)));
        return processorTaskList;
    }

    @Override
    public Boolean abandonTasks(final String nodeName, final ProcessorTaskList request) {
        LOGGER.debug("abandonTasks called for nodeName: {}, {}", nodeName, request);
        final Boolean result = nodeService.remoteRestResult(
                nodeName,
                Boolean.class,
                () -> ResourcePaths.buildAuthenticatedApiPath(
                        ProcessorTaskResource.BASE_PATH,
                        ProcessorTaskResource.ABANDON_TASKS_PATH_PART,
                        nodeName),
                () ->
                        processorTaskManager.abandonTasks(request),
                builder ->
                        builder.put(Entity.json(request)));
        return result;
    }
}
