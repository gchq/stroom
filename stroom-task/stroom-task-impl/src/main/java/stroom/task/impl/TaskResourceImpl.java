/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.event.logging.api.EventActionDecorator;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.node.api.NodeService;
import stroom.task.shared.FindTaskProgressCriteria;
import stroom.task.shared.FindTaskProgressRequest;
import stroom.task.shared.TaskProgress;
import stroom.task.shared.TaskProgressResponse;
import stroom.task.shared.TaskResource;
import stroom.task.shared.TerminateTaskProgressRequest;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.servlet.SessionIdProvider;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.ResultPage;

import event.logging.ProcessAction;
import event.logging.ProcessEventAction;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.client.Entity;

import java.util.Collections;
import java.util.List;

@AutoLogged
class TaskResourceImpl implements TaskResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TaskResourceImpl.class);

    private final Provider<TaskManagerImpl> taskManagerProvider;
    private final Provider<SessionIdProvider> sessionIdProvider;
    private final Provider<NodeService> nodeServiceProvider;

    @Inject
    TaskResourceImpl(final Provider<TaskManagerImpl> taskManagerProvider,
                     final Provider<SessionIdProvider> sessionIdProvider,
                     final Provider<NodeService> nodeServiceProvider) {
        this.taskManagerProvider = taskManagerProvider;
        this.sessionIdProvider = sessionIdProvider;
        this.nodeServiceProvider = nodeServiceProvider;
    }

    @Override
    public TaskProgressResponse list(final String nodeName) {
        return find(nodeName, new FindTaskProgressRequest(new FindTaskProgressCriteria()));
    }

    @AutoLogged(OperationType.UNLOGGED) // Called for each node so too noisy to log and of limited benefit
    @Override
    public TaskProgressResponse find(final String nodeName, final FindTaskProgressRequest request) {
        try {
            return nodeServiceProvider.get()
                    .remoteRestResult(
                            nodeName,
                            TaskProgressResponse.class,
                            () -> ResourcePaths.buildAuthenticatedApiPath(
                                    TaskResource.BASE_PATH,
                                    TaskResource.FIND_PATH_PART,
                                    nodeName),
                            () -> {
                                final ResultPage<TaskProgress> resultPage = taskManagerProvider.get()
                                        .find(request.getCriteria());
                                return new TaskProgressResponse(
                                        resultPage.getValues(),
                                        Collections.emptyList(),
                                        resultPage.getPageResponse());
                            },
                            builder ->
                                    builder.post(Entity.json(request)));
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            return new TaskProgressResponse(Collections.emptyList(), List.of(e.getMessage()), null);
        }
    }

    @AutoLogged(OperationType.UNLOGGED) // Called for each node so too noisy to log and of limited benefit
    @Override
    public TaskProgressResponse userTasks(final String nodeName) {
        try {
            final String sessionId = sessionIdProvider.get().get();
            if (sessionId != null) {
                final FindTaskProgressCriteria criteria = new FindTaskProgressCriteria();
                criteria.setSort(FindTaskProgressCriteria.FIELD_AGE, true, false);
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
    @AutoLogged(value = OperationType.PROCESS, verb = "Terminating",
            decorator = TerminateDecorator.class)
    public Boolean terminate(final String nodeName, final TerminateTaskProgressRequest request) {
        nodeServiceProvider.get()
                .remoteRestCall(
                        nodeName,
                        () -> ResourcePaths.buildAuthenticatedApiPath(
                                TaskResource.BASE_PATH,
                                TaskResource.TERMINATE_PATH_PART,
                                nodeName),
                        () ->
                                taskManagerProvider.get().terminate(
                                        request.getCriteria()),
                        builder ->
                                builder.post(Entity.json(request)));

        return true;
    }


    // --------------------------------------------------------------------------------


    static class TerminateDecorator implements EventActionDecorator<ProcessEventAction> {

        @Override
        public ProcessEventAction decorate(final ProcessEventAction eventAction) {
            return eventAction.newCopyBuilder()
                    .withAction(ProcessAction.TERMINATE)
                    .build();
        }
    }
}
