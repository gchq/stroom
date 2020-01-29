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

import com.codahale.metrics.health.HealthCheck.Result;
import event.logging.BaseAdvancedQueryOperator.And;
import event.logging.Query;
import event.logging.Query.Advanced;
import stroom.event.logging.api.DocumentEventLog;
import stroom.job.shared.JobNode;
import stroom.job.shared.JobNodeInfo;
import stroom.job.shared.JobNodeResource;
import stroom.job.shared.ListJobNodeResponse;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.util.HasHealthCheck;
import stroom.util.guice.ResourcePaths;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.RestResource;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.function.Consumer;

class JobNodeResourceImpl implements JobNodeResource, RestResource, HasHealthCheck {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(JobNodeResourceImpl.class);

    private final JobNodeService jobNodeService;
    private final NodeService nodeService;
    private final NodeInfo nodeInfo;
    private final WebTargetFactory webTargetFactory;
    private final DocumentEventLog documentEventLog;

    @Inject
    JobNodeResourceImpl(final JobNodeService jobNodeService,
                                final NodeService nodeService,
                                final NodeInfo nodeInfo,
                                final WebTargetFactory webTargetFactory,
                                final DocumentEventLog documentEventLog) {
        this.jobNodeService = jobNodeService;
        this.nodeService = nodeService;
        this.nodeInfo = nodeInfo;
        this.webTargetFactory = webTargetFactory;
        this.documentEventLog = documentEventLog;
    }

    @Override
    public ListJobNodeResponse list(final String jobName, final String nodeName) {
        ListJobNodeResponse response = null;

        final Query query = new Query();
        final Advanced advanced = new Advanced();
        query.setAdvanced(advanced);
        final And and = new And();
        advanced.getAdvancedQueryItems().add(and);

        try {
            final FindJobNodeCriteria findJobNodeCriteria = new FindJobNodeCriteria();
            if (jobName != null && !jobName.isEmpty()) {
                findJobNodeCriteria.getJobName().setString(jobName);
            }
            if (nodeName != null && !nodeName.isEmpty()) {
                findJobNodeCriteria.getNodeName().setString(nodeName);
            }

            final BaseResultList<JobNode> results = jobNodeService.find(findJobNodeCriteria);
            response = results.toResultPage(new ListJobNodeResponse());
            documentEventLog.search("ListJobNodes", query, JobNode.class.getSimpleName(), results.getPageResponse(), null);
        } catch (final RuntimeException e) {
            documentEventLog.search("ListJobNodes", query, JobNode.class.getSimpleName(), null, e);
        }
        return response;
    }

    @Override
    public JobNodeInfo info(final String jobName, final String nodeName) {
        JobNodeInfo jobNodeInfo = null;
        try {
            // If this is the node that was contacted then just return our local info.
            if (nodeInfo.getThisNodeName().equals(nodeName)) {
                jobNodeInfo = jobNodeService.getInfo(jobName);

            } else {
                String url = NodeCallUtil.getUrl(nodeService, nodeName);
                url += ResourcePaths.API_PATH + "/jobNode/info";
                final Response response = webTargetFactory
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
            }

        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
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
        JobNode jobNode = null;
        JobNode before = null;
        JobNode after = null;

        try {
            // Get the before version.
            before = jobNodeService.fetch(id).orElse(null);
            jobNode = jobNodeService.fetch(id).orElse(null);
            if (jobNode == null) {
                throw new RuntimeException("Unknown job node: " + id);
            }
            mutation.accept(jobNode);
            after = jobNodeService.update(jobNode);

            documentEventLog.update(before, after, null);

        } catch (final RuntimeException e) {
            documentEventLog.update(before, after, e);
            throw e;
        }
    }

    @Override
    public Result getHealth() {
        return Result.healthy();
    }
}