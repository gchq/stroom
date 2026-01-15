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

package stroom.job.impl;

import stroom.event.logging.api.DocumentEventLog;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.job.shared.BatchScheduleRequest;
import stroom.job.shared.FindJobNodeCriteria;
import stroom.job.shared.JobNode;
import stroom.job.shared.JobNodeAndInfo;
import stroom.job.shared.JobNodeAndInfoListResponse;
import stroom.job.shared.JobNodeInfo;
import stroom.job.shared.JobNodeListResponse;
import stroom.job.shared.JobNodeResource;
import stroom.job.shared.JobNodeUtil;
import stroom.node.api.NodeCallException;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.util.jersey.UriBuilderUtil;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.scheduler.Schedule;

import event.logging.AdvancedQuery;
import event.logging.And;
import event.logging.ComplexLoggedOutcome;
import event.logging.Data;
import event.logging.MultiObject;
import event.logging.OtherObject;
import event.logging.Query;
import event.logging.Term;
import event.logging.TermCondition;
import event.logging.UpdateEventAction;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

@AutoLogged(OperationType.MANUALLY_LOGGED)
class JobNodeResourceImpl implements JobNodeResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(JobNodeResourceImpl.class);

    private final Provider<JobNodeService> jobNodeServiceProvider;
    private final Provider<NodeService> nodeServiceProvider;
    private final Provider<NodeInfo> nodeInfoProvider;
    private final Provider<WebTargetFactory> webTargetFactoryProvider;
    private final Provider<DocumentEventLog> documentEventLogProvider;
    private final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider;

    @Inject
    JobNodeResourceImpl(final Provider<JobNodeService> jobNodeServiceProvider,
                        final Provider<NodeService> nodeServiceProvider,
                        final Provider<NodeInfo> nodeInfoProvider,
                        final Provider<WebTargetFactory> webTargetFactoryProvider,
                        final Provider<DocumentEventLog> documentEventLogProvider,
                        final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider) {
        this.jobNodeServiceProvider = jobNodeServiceProvider;
        this.nodeServiceProvider = nodeServiceProvider;
        this.nodeInfoProvider = nodeInfoProvider;
        this.webTargetFactoryProvider = webTargetFactoryProvider;
        this.documentEventLogProvider = documentEventLogProvider;
        this.stroomEventLoggingServiceProvider = stroomEventLoggingServiceProvider;
    }

//    @Override
//    public JobNodeListResponse list(final String jobName, final String nodeName) {
//        final FindJobNodeCriteria findJobNodeCriteria = new FindJobNodeCriteria();
//        if (jobName != null) {
//            findJobNodeCriteria.getJobName().setString(jobName);
//        }
//        if (nodeName != null) {
//            findJobNodeCriteria.getNodeName().setString(nodeName);
//        }
//        return find(findJobNodeCriteria);
//    }

    @Override
    public JobNodeAndInfoListResponse find(final FindJobNodeCriteria findJobNodeCriteria) {
        JobNodeAndInfoListResponse response = null;

        final And.Builder<Void> andBuilder = And.builder();

        if (findJobNodeCriteria.getJobName().isConstrained()) {
            andBuilder.addTerm(Term.builder()
                    .withName("Job")
                    .withCondition(TermCondition.EQUALS)
                    .withValue(findJobNodeCriteria.getJobName().getString())
                    .build());
        }
        if (findJobNodeCriteria.getNodeName().isConstrained()) {
            andBuilder.addTerm(Term.builder()
                    .withName("NodeName")
                    .withCondition(TermCondition.EQUALS)
                    .withValue(findJobNodeCriteria.getNodeName().getString())
                    .build());
        }

        final Query query = Query.builder()
                .withAdvanced(AdvancedQuery.builder()
                        .addAnd(andBuilder.build())
                        .build())
                .build();
        try {

            final String nodeNameCriteria = findJobNodeCriteria.getNodeName().getString();
            // No point trying to hit a disabled node for info as it is likely down
            if (nodeNameCriteria != null) {
                response = doFindByNode(findJobNodeCriteria);
            } else {
                response = doFind(findJobNodeCriteria);
            }

            documentEventLogProvider.get().search(
                    "ListJobNodes",
                    query,
                    JobNodeAndInfo.class.getSimpleName(),
                    response.getPageResponse(),
                    null);
        } catch (final RuntimeException e) {
            documentEventLogProvider.get().search(
                    "ListJobNodes",
                    query,
                    JobNodeAndInfo.class.getSimpleName(),
                    null,
                    e);
            throw e;
        }
        return response;
    }

    private JobNodeAndInfoListResponse doFind(final FindJobNodeCriteria criteria) {
        final JobNodeService jobNodeService = jobNodeServiceProvider.get();
        final JobNodeListResponse jobNodeListResponse = jobNodeService.find(criteria);
        final String thisNodeName = nodeInfoProvider.get().getThisNodeName();

        final List<JobNodeAndInfo> list = jobNodeListResponse.getValues()
                .stream()
                .map(jobNode -> {
                    // We can add in the jobNodeInfo if the jobNode is for this node as the info
                    // is obtained from the node's in memory state.
                    if (Objects.equals(jobNode.getNodeName(), thisNodeName)) {
                        final JobNodeInfo jobNodeInfo = jobNodeService.getInfo(jobNode.getJobName());
                        return new JobNodeAndInfo(jobNode, jobNodeInfo);
                    } else {
                        return JobNodeAndInfo.withoutInfo(jobNode);
                    }
                })
                .toList();

        return JobNodeAndInfoListResponse.createUnboundedResponse(list);
    }

    private JobNodeAndInfoListResponse doFindByNode(final FindJobNodeCriteria criteria) {
        final String nodeName = criteria.getNodeName().getString();

        // Criteria are constrained to a single node, so hit that node, so we can get its
        // node info
        try {
            return nodeServiceProvider.get()
                    .remoteRestResult(
                            nodeName,
                            JobNodeAndInfoListResponse.class,
                            () ->
                                    ResourcePaths.buildAuthenticatedApiPath(
                                            JobNodeResource.BASE_PATH,
                                            JobNodeResource.FIND_PATH_PART),
                            () ->
                                    doFind(criteria),
                            builder -> builder.post(Entity.json(criteria)));
        } catch (final NodeCallException e) {
            LOGGER.debug(() -> LogUtil.message("Error calling node {}: {}", nodeName, e.getMessage(), e));
            // Node likely down so just return the jobNode from the DB without the node's in-mem state
            return doFind(criteria);
        }
    }

    @Override
    public JobNodeInfo info(final String jobName, final String nodeName) {
        final JobNodeInfo jobNodeInfo;
        // If this is the node that was contacted then just return our local info.
        if (NodeCallUtil.shouldExecuteLocally(nodeInfoProvider.get(), nodeName)) {
            jobNodeInfo = jobNodeServiceProvider.get().getInfo(jobName);

        } else {
            final String url = NodeCallUtil.getBaseEndpointUrl(nodeInfoProvider.get(),
                    nodeServiceProvider.get(), nodeName) +
                               ResourcePaths.buildAuthenticatedApiPath(JobNodeResource.INFO_PATH);
            try {
                WebTarget webTarget = webTargetFactoryProvider.get().create(url);
                webTarget = UriBuilderUtil.addParam(webTarget, "nodeName", nodeName);
                webTarget = UriBuilderUtil.addParam(webTarget, "jobName", jobName);
                final Response response = webTarget
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

//    @Override
//    public JobNodeAndInfoListResponse findNodeJobs(final FindJobNodeCriteria criteria) {
//        if (!criteria.getNodeName().isConstrained()) {
//            throw new IllegalArgumentException("This method must be constrained to a single node.");
//        }
//        final String nodeName = criteria.getNodeName().getString();
//
//        return nodeServiceProvider.get()
//                .remoteRestResult(
//                        nodeName,
//                        JobNodeAndInfoListResponse.class,
//                        () -> ResourcePaths.buildAuthenticatedApiPath(JobNodeResource.FIND_NODE_JOBS_PATH_PART),
//                        () -> {
//                            final JobNodeService jobNodeService = jobNodeServiceProvider.get();
//                            final JobNodeListResponse jobNodeListResponse = jobNodeService.find(criteria);
//                            final List<JobNodeAndInfo> infoList = jobNodeListResponse.getValues().stream()
//                                    .map(jobNode -> {
//                                        final JobNodeInfo info = jobNodeService.getInfo(jobNode.getJobName());
//                                        return new JobNodeAndInfo(jobNode, info);
//                                    })
//                                    .toList();
//                            return new JobNodeAndInfoListResponse(infoList);
//                        },
//                        builder -> builder.post(Entity.json(criteria)));
//    }

    @Override
    public void setTaskLimit(final Integer id, final Integer taskLimit) {
        modifyJobNode(id, jobNode -> jobNode.setTaskLimit(taskLimit));
    }

    @Override
    public void setSchedule(final Integer id, final Schedule schedule) {
        if (schedule != null) {
            modifyJobNode(id, jobNode -> JobNodeUtil.setSchedule(jobNode, schedule));
        }
    }

    @Override
    public void setScheduleBatch(final BatchScheduleRequest batchScheduleRequest) {

        List<OtherObject> beforeList = null;
        if (NullSafe.nonNull(batchScheduleRequest, BatchScheduleRequest::getSchedule)) {
            final JobNodeService jobNodeService = jobNodeServiceProvider.get();
            final Set<Integer> jobNodeIds = batchScheduleRequest.getJobNodeIds();
            beforeList = jobNodeService.find(new FindJobNodeCriteria())
                    .getValues()
                    .stream()
                    .filter(jobNode -> jobNodeIds.contains(jobNode.getId()))
                    .map(this::mapJobNodeToConfiguration)
                    .toList();

            stroomEventLoggingServiceProvider.get()
                    .loggedWorkBuilder()
                    .withTypeId(StroomEventLoggingUtil.buildTypeId(
                            this, "setScheduleBatch"))
                    .withDescription("Updating schedule for " + jobNodeIds.size() + " nodes")
                    .withDefaultEventAction(UpdateEventAction.builder()
                            .withBefore(MultiObject.builder()
                                    .addObjects(beforeList)
                                    .build())
                            .build())
                    .withComplexLoggedAction(updateEventAction -> {
                        try {
                            jobNodeService.update(batchScheduleRequest);

                            final List<OtherObject> afterList = jobNodeService.find(new FindJobNodeCriteria())
                                    .getValues()
                                    .stream()
                                    .filter(jobNode -> jobNodeIds.contains(jobNode.getId()))
                                    .map(this::mapJobNodeToConfiguration)
                                    .toList();

                            final UpdateEventAction eventActionCopy = updateEventAction.newCopyBuilder()
                                    .withAfter(MultiObject.builder()
                                            .addObjects(afterList)
                                            .build())
                                    .build();
                            return ComplexLoggedOutcome.success(eventActionCopy);
                        } catch (final Exception e) {
                            LOGGER.debug("Error setting schedule for IDs {}, schedule: {}",
                                    batchScheduleRequest.getJobNodeIds(), batchScheduleRequest.getSchedule(), e);
                            throw new RuntimeException(
                                    LogUtil.message("Error setting schedule for IDs {}, schedule: {}",
                                            batchScheduleRequest.getJobNodeIds(), batchScheduleRequest.getSchedule()),
                                    e);
                        }
                    })
                    .runActionAndLog();
        }
    }

    private OtherObject mapJobNodeToConfiguration(final JobNode jobNode) {
        if (jobNode != null) {
            return OtherObject.builder()
                    .withId(String.valueOf(jobNode.getId()))
                    .withName(jobNode.getJobName())
                    .withType("JobNode")
                    .addData(Data.builder()
                            .withName("Schedule")
                            .withValue(jobNode.getSchedule())
                            .build())
                    .addData(Data.builder()
                            .withName("Enabled")
                            .withValue(String.valueOf(jobNode.isEnabled()))
                            .build())
                    .addData(Data.builder()
                            .withName("JobType")
                            .withValue(jobNode.getJobType().getDisplayValue())
                            .build())
                    .addData(Data.builder()
                            .withName("Node")
                            .withValue(jobNode.getNodeName())
                            .build())
                    .build();
        } else {
            return null;
        }
    }

    @Override
    public void setEnabled(final Integer id, final Boolean enabled) {
        modifyJobNode(id, jobNode -> jobNode.setEnabled(enabled));
    }

    @AutoLogged(value = OperationType.PROCESS, verb = "Executing job on node")
    @Override
    public boolean execute(final Integer id) {
        final JobNodeService jobNodeService = jobNodeServiceProvider.get();
        final JobNode jobNode = jobNodeService.fetch(id)
                .orElseThrow(NotFoundException::new);

        try {
            return nodeServiceProvider.get().remoteRestResult(
                    jobNode.getNodeName(),
                    Boolean.class,
                    () -> ResourcePaths.buildAuthenticatedApiPath(
                            JobNodeResource.BASE_PATH,
                            String.valueOf(jobNode.getId()),
                            JobNodeResource.EXECUTE_PATH_PART),
                    () -> {
                        jobNodeService.executeJob(jobNode);
                        return true;
                    },
                    builder -> builder.post(null));
        } catch (final Exception e) {
            LOGGER.error("Error executing job {} on node {}: {}",
                    jobNode.getJobName(), jobNode.getNodeName(), LogUtil.exceptionMessage(e), e);
            throw new RuntimeException(e);
        }
    }

    private void modifyJobNode(final int id, final Consumer<JobNode> mutation) {
        final JobNode jobNode;
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
