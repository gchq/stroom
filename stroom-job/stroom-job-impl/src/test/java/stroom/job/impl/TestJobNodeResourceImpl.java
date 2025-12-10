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
import stroom.job.shared.JobNode;
import stroom.job.shared.JobNodeInfo;
import stroom.job.shared.JobNodeListResponse;
import stroom.job.shared.JobNodeResource;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.test.common.util.test.AbstractMultiNodeResourceTest;
import stroom.util.jersey.UriBuilderUtil;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.scheduler.Schedule;
import stroom.util.shared.scheduler.ScheduleType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
class TestJobNodeResourceImpl extends AbstractMultiNodeResourceTest<JobNodeResource> {

    private static final JobNode JOB_NODE_1 = buildJobNode(1, 1, "node1");
    private static final JobNode JOB_NODE_2 = buildJobNode(2, 1, "node2");

    private static final JobNodeListResponse JOB_NODES = JobNodeListResponse.createUnboundedJobNodeResponse(List.of(
            JOB_NODE_1,
            JOB_NODE_2));

    private final Map<String, JobNodeService> jobNodeServiceMap = new HashMap<>();
    private final Map<String, DocumentEventLog> documentEventLogMap = new HashMap<>();

    private static JobNode buildJobNode(final int id, final int version, final String node) {
        final JobNode jobNode = new JobNode();
        jobNode.setId(id);
        jobNode.setVersion(version);
        jobNode.setNodeName(node);
        return jobNode;
    }

    private static final int BASE_PORT = 7020;

    public TestJobNodeResourceImpl() {
        super(createNodeList(BASE_PORT));
    }

    @BeforeEach
    void beforeEach() {
        jobNodeServiceMap.clear();
    }

    @Test
    void info_sameNode() {
        initNodes();

        final String subPath = JobNodeResource.INFO_PATH_PART;

        final JobNodeInfo expectedResponse = new JobNodeInfo(
                BASE_PORT, 2L, 3L, 4L);

        final JobNodeInfo response = doGetTest(
                subPath,
                JobNodeInfo.class,
                expectedResponse,
                webTarget -> UriBuilderUtil.addParam(webTarget, "jobName", "myJob"),
                webTarget -> UriBuilderUtil.addParam(webTarget, "nodeName", "node1")
        );

        assertThat(getRequestEvents("node1"))
                .hasSize(1);
        assertThat(getRequestEvents("node2"))
                .hasSize(0);
        assertThat(getRequestEvents("node3"))
                .hasSize(0);
    }

    @Test
    void info_otherNode() {
        initNodes();

        final String subPath = JobNodeResource.INFO_PATH_PART;

        final JobNodeInfo expectedResponse = new JobNodeInfo(
                BASE_PORT + 1, 2L, 3L, 4L);

        final JobNodeInfo response = doGetTest(
                subPath,
                JobNodeInfo.class,
                expectedResponse,
                webTarget -> UriBuilderUtil.addParam(webTarget, "jobName", "myJob"),
                webTarget -> UriBuilderUtil.addParam(webTarget, "nodeName", "node2")
        );

        assertThat(getRequestEvents("node1"))
                .hasSize(1);
        assertThat(getRequestEvents("node2"))
                .hasSize(1);
        assertThat(getRequestEvents("node3"))
                .hasSize(0);
    }

    @Test
    void setTaskLimit() {

        initSingleNode();

        final String subPath = ResourcePaths.buildPath("1", JobNodeResource.TASK_LIMIT_PATH_PART);

        final Integer newTaskLimit = 500;

        doPutTest(
                subPath,
                newTaskLimit);

        final ArgumentCaptor<JobNode> jobNodeCaptor = ArgumentCaptor.forClass(JobNode.class);

        verify(jobNodeServiceMap.get("node1"), times(1))
                .update(jobNodeCaptor.capture());

        assertThat(jobNodeCaptor.getValue().getTaskLimit())
                .isEqualTo(newTaskLimit);

        final ArgumentCaptor<JobNode> beforeCaptor = ArgumentCaptor.forClass(JobNode.class);
        final ArgumentCaptor<JobNode> afterCaptor = ArgumentCaptor.forClass(JobNode.class);

        verify(documentEventLogMap.get("node1"), times(1))
                .update(beforeCaptor.capture(), afterCaptor.capture(), any());

        // equal apart from version
        assertThat(beforeCaptor.getValue())
                .isEqualTo(afterCaptor.getValue());

        assertThat(beforeCaptor.getValue().getVersion())
                .isNotEqualTo(afterCaptor.getValue().getVersion());

        assertThat(afterCaptor.getValue().getTaskLimit())
                .isEqualTo(newTaskLimit.intValue());

        assertThat(afterCaptor.getValue().getTaskLimit())
                .isNotEqualTo(JOB_NODE_1.getTaskLimit());
    }

    @Test
    void setSchedule() {

        initSingleNode();

        final String subPath = ResourcePaths.buildPath("1", JobNodeResource.SCHEDULE_PATH_PART);

        final Schedule newSchedule = new Schedule(ScheduleType.CRON, "0 1 1 1 * ?");

        doPutTest(subPath, newSchedule);

        final ArgumentCaptor<JobNode> jobNodeCaptor = ArgumentCaptor.forClass(JobNode.class);

        verify(jobNodeServiceMap.get("node1"), times(1))
                .update(jobNodeCaptor.capture());

        assertThat(jobNodeCaptor.getValue().getSchedule()).isEqualTo(newSchedule.getExpression());

        final ArgumentCaptor<JobNode> beforeCaptor = ArgumentCaptor.forClass(JobNode.class);
        final ArgumentCaptor<JobNode> afterCaptor = ArgumentCaptor.forClass(JobNode.class);

        verify(documentEventLogMap.get("node1"), times(1))
                .update(beforeCaptor.capture(), afterCaptor.capture(), any());

        // equal apart from version
        assertThat(beforeCaptor.getValue())
                .isEqualTo(afterCaptor.getValue());

        assertThat(beforeCaptor.getValue().getVersion())
                .isNotEqualTo(afterCaptor.getValue().getVersion());

        assertThat(afterCaptor.getValue().getSchedule()).isEqualTo(newSchedule.getExpression());

        assertThat(afterCaptor.getValue().getSchedule())
                .isNotEqualTo(JOB_NODE_1.getSchedule());
    }

    @Test
    void setEnabled() {
        initSingleNode();

        final String subPath = ResourcePaths.buildPath("1", JobNodeResource.ENABLED_PATH_PART);

        final boolean newIsEnabled = true;

        doPutTest(
                subPath,
                newIsEnabled);

        final ArgumentCaptor<JobNode> jobNodeCaptor = ArgumentCaptor.forClass(JobNode.class);

        verify(jobNodeServiceMap.get("node1"), times(1))
                .update(jobNodeCaptor.capture());

        assertThat(jobNodeCaptor.getValue().isEnabled())
                .isEqualTo(newIsEnabled);

        final ArgumentCaptor<JobNode> beforeCaptor = ArgumentCaptor.forClass(JobNode.class);
        final ArgumentCaptor<JobNode> afterCaptor = ArgumentCaptor.forClass(JobNode.class);

        verify(documentEventLogMap.get("node1"), times(1))
                .update(beforeCaptor.capture(), afterCaptor.capture(), any());

        // equal apart from version
        assertThat(beforeCaptor.getValue())
                .isEqualTo(afterCaptor.getValue());

        assertThat(beforeCaptor.getValue().getVersion())
                .isNotEqualTo(afterCaptor.getValue().getVersion());

        assertThat(afterCaptor.getValue().isEnabled())
                .isEqualTo(newIsEnabled);

        assertThat(afterCaptor.getValue().isEnabled())
                .isNotEqualTo(JOB_NODE_1.isEnabled());
    }

    @Override
    public String getResourceBasePath() {
        return JobNodeResource.BASE_PATH;
    }

    @Override
    public JobNodeResource getRestResource(final TestNode node,
                                           final List<TestNode> allNodes,
                                           final Map<String, String> baseEndPointUrls) {
        // Set up the JobNodeService mock
        final JobNodeService jobNodeService = createNamedMock(JobNodeService.class, node);

        // Use the port as a unique task count
        when(jobNodeService.getInfo(any()))
                .thenReturn(new JobNodeInfo(
                        node.getPort(), 2L, 3L, 4L));

        when(jobNodeService.find(any()))
                .thenReturn(JOB_NODES);

        when(jobNodeService.fetch(anyInt()))
                .thenReturn(Optional.of(buildJobNode(1, 1, "node1")));

        when(jobNodeService.update(any(JobNode.class)))
                .then(invocation -> {
                    final JobNode input = invocation.getArgument(0);

                    final JobNode output = buildJobNode(
                            input.getId(), input.getVersion() + 1, input.getNodeName());
                    output.setTaskLimit(input.getTaskLimit());
                    output.setSchedule(input.getSchedule());
                    output.setEnabled(input.isEnabled());

                    return output;
                });

        jobNodeServiceMap.put(node.getNodeName(), jobNodeService);

        // Set up the NodeService mock
        final NodeService nodeService = createNamedMock(NodeService.class, node);

        when(nodeService.isEnabled(Mockito.anyString()))
                .then(invocation ->
                        allNodes.stream()
                                .filter(testNode -> testNode.getNodeName().equals(invocation.getArgument(0)))
                                .anyMatch(TestNode::isEnabled));

        when(nodeService.getBaseEndpointUrl(Mockito.anyString()))
                .then(invocation -> baseEndPointUrls.get(invocation.getArgument(0)));

        // Set up the NodeInfo mock

        final NodeInfo nodeInfo = createNamedMock(NodeInfo.class, node);

        when(nodeInfo.getThisNodeName())
                .thenReturn(node.getNodeName());

        final DocumentEventLog documentEventLog = createNamedMock(DocumentEventLog.class, node);
        final StroomEventLoggingService stroomEventLoggingService = createNamedMock(
                StroomEventLoggingService.class, node);

        documentEventLogMap.put(node.getNodeName(), documentEventLog);

        return new JobNodeResourceImpl(
                () -> jobNodeService,
                () -> nodeService,
                () -> nodeInfo,
                () -> webTargetFactory(),
                () -> documentEventLog,
                () -> stroomEventLoggingService);
    }
}
