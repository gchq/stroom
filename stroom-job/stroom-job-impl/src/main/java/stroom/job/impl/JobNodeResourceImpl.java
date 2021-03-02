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

package stroom.job.impl;

import stroom.event.logging.api.DocumentEventLog;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.job.shared.JobNode;
import stroom.job.shared.JobNodeInfo;
import stroom.job.shared.JobNodeListResponse;
import stroom.job.shared.JobNodeResource;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.shared.ResourcePaths;

import event.logging.AdvancedQuery;
import event.logging.And;
import event.logging.Query;
import event.logging.Term;
import event.logging.TermCondition;

import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@AutoLogged(OperationType.MANUALLY_LOGGED)
class JobNodeResourceImpl implements JobNodeResource {

    private final Provider<JobNodeService> jobNodeServiceProvider;
    private final Provider<NodeService> nodeServiceProvider;
    private final Provider<NodeInfo> nodeInfoProvider;
    private final Provider<WebTargetFactory> webTargetFactoryProvider;
    private final Provider<DocumentEventLog> documentEventLogProvider;

    @Inject
    JobNodeResourceImpl(final Provider<JobNodeService> jobNodeServiceProvider,
                        final Provider<NodeService> nodeServiceProvider,
                        final Provider<NodeInfo> nodeInfoProvider,
                        final Provider<WebTargetFactory> webTargetFactoryProvider,
                        final Provider<DocumentEventLog> documentEventLogProvider) {
        this.jobNodeServiceProvider = jobNodeServiceProvider;
        this.nodeServiceProvider = nodeServiceProvider;
        this.nodeInfoProvider = nodeInfoProvider;
        this.webTargetFactoryProvider = webTargetFactoryProvider;
        this.documentEventLogProvider = documentEventLogProvider;
    }

    @Override
    public JobNodeListResponse list(final String jobName, final String nodeName) {
        JobNodeListResponse response = null;

        And.Builder<Void> andBuilder = And.builder();

        final FindJobNodeCriteria findJobNodeCriteria = new FindJobNodeCriteria();
        if (jobName != null && !jobName.isEmpty()) {
            findJobNodeCriteria.getJobName().setString(jobName);
            andBuilder.addTerm(Term.builder()
                    .withName("Job")
                    .withCondition(TermCondition.EQUALS)
                    .withValue(jobName)
                    .build());
        }
        if (nodeName != null && !nodeName.isEmpty()) {
            findJobNodeCriteria.getNodeName().setString(nodeName);
            andBuilder.addTerm(Term.builder()
                    .withName("NodeName")
                    .withCondition(TermCondition.EQUALS)
                    .withValue(nodeName)
                    .build());
        }

        final Query query = Query.builder()
                .withAdvanced(AdvancedQuery.builder()
                        .addAnd(andBuilder.build())
                        .build())
                .build();
        try {

            response = jobNodeServiceProvider.get().find(findJobNodeCriteria);
            documentEventLogProvider.get().search(
                    "ListJobNodes",
                    query,
                    JobNode.class.getSimpleName(),
                    response.getPageResponse(),
                    null);
        } catch (final RuntimeException e) {
            documentEventLogProvider.get().search(
                    "ListJobNodes",
                    query,
                    JobNode.class.getSimpleName(),
                    null,
                    e);
            throw e;
        }
        return response;
    }

    @Override
    public JobNodeInfo info(final String jobName, final String nodeName) {
        JobNodeInfo jobNodeInfo;
        // If this is the node that was contacted then just return our local info.
        if (NodeCallUtil.shouldExecuteLocally(nodeInfoProvider.get(), nodeName)) {
            jobNodeInfo = jobNodeServiceProvider.get().getInfo(jobName);

        } else {
            final String url = NodeCallUtil.getBaseEndpointUrl(nodeInfoProvider.get(),
                    nodeServiceProvider.get(), nodeName) +
                    ResourcePaths.buildAuthenticatedApiPath(JobNodeResource.INFO_PATH);
            try {
                final Response response = webTargetFactoryProvider.get()
                        .create(url)
                        .queryParam("jobName", jobName)
                        .queryParam("nodeName", nodeName)
                        .request(MediaType.APPLICATION_JSON)
                        .get();
                if (response.getStatus() != 200) {
                    throw new WebApplicationException(response);
                }
                jobNodeInfo = response.readEntity(JobNodeInfo.class);
                if (jobNodeInfo == null) {
                    throw new RuntimeException("Unable to contact node \"" + nodeName + "\" at URL: " + url);
                }
            } catch (final Throwable e) {
                throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
            }
        }

        return jobNodeInfo;
    }

    @Override
    public void setTaskLimit(final Integer id, final Integer taskLimit) {
        modifyJobNode(id, jobNode -> jobNode.setTaskLimit(taskLimit));
    }

    @Override
    public void setSchedule(final Integer id, final String schedule) {
        modifyJobNode(id, jobNode -> jobNode.setSchedule(schedule));
    }

    @Override
    public void setEnabled(final Integer id, final Boolean enabled) {
        modifyJobNode(id, jobNode -> jobNode.setEnabled(enabled));
    }

    private void modifyJobNode(final int id, final Consumer<JobNode> mutation) {
        JobNode jobNode;
        JobNode before = null;
        JobNode after = null;

        try {
            final JobNodeService jobNodeService = jobNodeServiceProvider.get();
            // Get the before version.
            before = jobNodeService.fetch(id).orElse(null);
            jobNode = jobNodeService.fetch(id).orElse(null);
            if (jobNode == null) {
                throw new RuntimeException("Unknown job node: " + id);
            }
            mutation.accept(jobNode);
            after = jobNodeService.update(jobNode);

            documentEventLogProvider.get().update(before, after, null);

        } catch (final RuntimeException e) {
            documentEventLogProvider.get().update(before, after, e);
            throw e;
        }
    }
}
