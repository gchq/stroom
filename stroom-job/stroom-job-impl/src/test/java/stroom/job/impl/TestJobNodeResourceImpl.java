package stroom.job.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import stroom.event.logging.api.DocumentEventLog;
import stroom.job.shared.JobNode;
import stroom.job.shared.JobNodeInfo;
import stroom.job.shared.JobNodeListResponse;
import stroom.job.shared.JobNodeResource;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.test.common.util.test.AbstractMultiNodeResourceTest;
import stroom.util.shared.ResourcePaths;

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

    private static final JobNodeListResponse JOB_NODES = JobNodeListResponse.createUnboundedJobeNodeResponse(List.of(
        JOB_NODE_1,
        JOB_NODE_2));

    private final Map<String, JobNodeService> jobNodeServiceMap = new HashMap<>();
    private final Map<String, DocumentEventLog> documentEventLogMap = new HashMap<>();

    private static JobNode buildJobNode(final int id, final int version, final String node) {
        JobNode jobNode = new JobNode();
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
    void list1() {

        initSingleNode();

        final String subPath = "";

        ArgumentCaptor<FindJobNodeCriteria> criteriaCaptor = ArgumentCaptor.forClass(FindJobNodeCriteria.class);

        final JobNodeListResponse response = doGetTest(
            subPath,
            JobNodeListResponse.class,
            JOB_NODES,
            webTarget -> webTarget.queryParam("jobName", "myJob"),
            webTarget -> webTarget.queryParam("nodeName", "node1")
        );

        verify(jobNodeServiceMap.get("node1"), Mockito.only())
            .find(criteriaCaptor.capture());

        assertThat(criteriaCaptor.getValue().getJobName().getString())
            .isEqualTo("myJob");
        assertThat(criteriaCaptor.getValue().getNodeName().getString())
            .isEqualTo("node1");

    }

    @Test
    void list2() {

        initSingleNode();

        final String subPath = "";

        ArgumentCaptor<FindJobNodeCriteria> criteriaCaptor = ArgumentCaptor.forClass(FindJobNodeCriteria.class);

        final JobNodeListResponse response = doGetTest(
            subPath,
            JobNodeListResponse.class,
            JOB_NODES);

        verify(jobNodeServiceMap.get("node1"), Mockito.only())
            .find(criteriaCaptor.capture());

        assertThat(criteriaCaptor.getValue().getJobName().isConstrained())
            .isFalse();
        assertThat(criteriaCaptor.getValue().getNodeName().isConstrained())
            .isFalse();
    }

    @Test
    void list3() {

        initSingleNode();

        final String subPath = "";

        ArgumentCaptor<FindJobNodeCriteria> criteriaCaptor = ArgumentCaptor.forClass(FindJobNodeCriteria.class);

        final JobNodeListResponse response = doGetTest(
            subPath,
            JobNodeListResponse.class,
            JOB_NODES,
            webTarget -> webTarget.queryParam("nodeName", "node1")
        );

        verify(jobNodeServiceMap.get("node1"), Mockito.only())
            .find(criteriaCaptor.capture());

        assertThat(criteriaCaptor.getValue().getJobName().isConstrained())
            .isFalse();
        assertThat(criteriaCaptor.getValue().getNodeName().getString())
            .isEqualTo("node1");
    }

    @Test
    void info_sameNode() {
        initNodes();

        final String subPath = JobNodeResource.INFO_PATH_PART;

        final JobNodeInfo expectedResponse = new JobNodeInfo(BASE_PORT, 2L, 3L);

        final JobNodeInfo response = doGetTest(
            subPath,
            JobNodeInfo.class,
            expectedResponse,
            webTarget -> webTarget.queryParam("jobName", "myJob"),
            webTarget -> webTarget.queryParam("nodeName", "node1")
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

        final JobNodeInfo expectedResponse = new JobNodeInfo(BASE_PORT + 1, 2L, 3L);

        final JobNodeInfo response = doGetTest(
            subPath,
            JobNodeInfo.class,
            expectedResponse,
            webTarget -> webTarget.queryParam("jobName", "myJob"),
            webTarget -> webTarget.queryParam("nodeName", "node2")
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

        final String newSchedule = "1 1 1";

        doPutTest(
            subPath,
            newSchedule);

        final ArgumentCaptor<JobNode> jobNodeCaptor = ArgumentCaptor.forClass(JobNode.class);

        verify(jobNodeServiceMap.get("node1"), times(1))
            .update(jobNodeCaptor.capture());

        assertThat(jobNodeCaptor.getValue().getSchedule())
            .isEqualTo(newSchedule);

        final ArgumentCaptor<JobNode> beforeCaptor = ArgumentCaptor.forClass(JobNode.class);
        final ArgumentCaptor<JobNode> afterCaptor = ArgumentCaptor.forClass(JobNode.class);

        verify(documentEventLogMap.get("node1"), times(1))
            .update(beforeCaptor.capture(), afterCaptor.capture(), any());

        // equal apart from version
        assertThat(beforeCaptor.getValue())
            .isEqualTo(afterCaptor.getValue());

        assertThat(beforeCaptor.getValue().getVersion())
            .isNotEqualTo(afterCaptor.getValue().getVersion());

        assertThat(afterCaptor.getValue().getSchedule())
            .isEqualTo(newSchedule);

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
            .thenReturn(new JobNodeInfo(node.getPort(), 2L, 3L));

        when(jobNodeService.find(any()))
            .thenReturn(JOB_NODES);

        when(jobNodeService.fetch(anyInt()))
            .thenReturn(Optional.of(buildJobNode(1, 1, "node1")));

        when(jobNodeService.update(any()))
            .then(invocation -> {
                final JobNode input = invocation.getArgument(0);

                final JobNode output = buildJobNode(input.getId(), input.getVersion() + 1, input.getNodeName());
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
            .then(invocation -> baseEndPointUrls.get((String) invocation.getArgument(0)));

        // Set up the NodeInfo mock

        final NodeInfo nodeInfo = createNamedMock(NodeInfo.class, node);

        when(nodeInfo.getThisNodeName())
            .thenReturn(node.getNodeName());

        final DocumentEventLog documentEventLog = createNamedMock(DocumentEventLog.class, node);

        documentEventLogMap.put(node.getNodeName(), documentEventLog);

        return new JobNodeResourceImpl(
            jobNodeService,
            nodeService,
            nodeInfo,
            webTargetFactory(),
            documentEventLog);
    }
}