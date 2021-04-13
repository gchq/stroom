package stroom.job.impl;

import stroom.event.logging.api.DocumentEventLog;
import stroom.job.shared.JobNode;
import stroom.job.shared.JobNodeInfo;
import stroom.job.shared.JobNodeListResponse;
import stroom.job.shared.JobNodeResource;
import stroom.node.mock.MockNodeService;
import stroom.test.common.util.test.AbstractResourceTest;
import stroom.util.shared.ResourcePaths;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class TestJobNodeResourceImpl extends AbstractResourceTest<JobNodeResource> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestJobNodeResourceImpl.class);

    private static final JobNode JOB_NODE_1 = buildJobNode(1, 1, "node1");
    private static final JobNode JOB_NODE_2 = buildJobNode(2, 1, "node2");

    private static final JobNodeListResponse JOB_NODES = JobNodeListResponse.createUnboundedJobeNodeResponse(List.of(
            JOB_NODE_1,
            JOB_NODE_2));

//    private final Map<String, JobNodeService> jobNodeServiceMap = new HashMap<>();
//    private final Map<String, DocumentEventLog> documentEventLogMap = new HashMap<>();

    private static JobNode buildJobNode(final int id, final int version, final String node) {
        JobNode jobNode = new JobNode();
        jobNode.setId(id);
        jobNode.setVersion(version);
        jobNode.setNodeName(node);
        return jobNode;
    }

    private static final int BASE_PORT = 7020;

    @Mock
    private JobNodeService jobNodeService;
    @Mock
    private DocumentEventLog documentEventLog;
    private MockNodeService mockNodeService = new MockNodeService();


//    public TestJobNodeResourceImpl() {
//        super(createNodeList(BASE_PORT));
//    }

//    @BeforeEach
//    void beforeEach() {
//        jobNodeServiceMap.clear();
//    }

    @Test
    void list1() {

        final String subPath = "";

        ArgumentCaptor<FindJobNodeCriteria> criteriaCaptor = ArgumentCaptor.forClass(FindJobNodeCriteria.class);

        Mockito.when(jobNodeService.find(any()))
                .thenReturn(JOB_NODES);

        final JobNodeListResponse response = doGetTest(
                subPath,
                JobNodeListResponse.class,
                JOB_NODES,
                webTarget -> webTarget.queryParam("jobName", "myJob"),
                webTarget -> webTarget.queryParam("nodeName", "node1")
        );

        verify(jobNodeService, Mockito.only())
                .find(criteriaCaptor.capture());

        assertThat(criteriaCaptor.getValue().getJobName().getString())
                .isEqualTo("myJob");
        assertThat(criteriaCaptor.getValue().getNodeName().getString())
                .isEqualTo("node1");

    }

    @Test
    void list2() {

        final String subPath = "";

        ArgumentCaptor<FindJobNodeCriteria> criteriaCaptor = ArgumentCaptor.forClass(FindJobNodeCriteria.class);

        Mockito.when(jobNodeService.find(any()))
                .thenReturn(JOB_NODES);

        final JobNodeListResponse response = doGetTest(
                subPath,
                JobNodeListResponse.class,
                JOB_NODES);

        verify(jobNodeService, Mockito.only())
                .find(criteriaCaptor.capture());

        assertThat(criteriaCaptor.getValue().getJobName().isConstrained())
                .isFalse();
        assertThat(criteriaCaptor.getValue().getNodeName().isConstrained())
                .isFalse();
    }

    @Test
    void list3() {

        final String subPath = "";

        ArgumentCaptor<FindJobNodeCriteria> criteriaCaptor = ArgumentCaptor.forClass(FindJobNodeCriteria.class);

        Mockito.when(jobNodeService.find(any()))
                .thenReturn(JOB_NODES);

        final JobNodeListResponse response = doGetTest(
                subPath,
                JobNodeListResponse.class,
                JOB_NODES,
                webTarget -> webTarget.queryParam("nodeName", "node1")
        );

        verify(jobNodeService, Mockito.only())
                .find(criteriaCaptor.capture());

        assertThat(criteriaCaptor.getValue().getJobName().isConstrained())
                .isFalse();
        assertThat(criteriaCaptor.getValue().getNodeName().getString())
                .isEqualTo("node1");
    }

    @Test
    void info() {

        final String subPath = ResourcePaths.buildPath(
                JobNodeResource.INFO_PATH_PART,
                "myJob",
                "node1");

        final JobNodeInfo expectedResponse = new JobNodeInfo(
                BASE_PORT,
                2L,
                3L);

        Mockito.when(jobNodeService.getInfo(Mockito.anyString()))
                .thenReturn(expectedResponse);

        final JobNodeInfo response = doGetTest(
                subPath,
                JobNodeInfo.class,
                expectedResponse);

        Assertions.assertThat(response)
                .isEqualTo(expectedResponse);

        LOGGER.info("lastUrl {}", mockNodeService.getLastUrl());

        Assertions.assertThat(mockNodeService.getLastUrl())
                .isEqualTo(ResourcePaths.buildAuthenticatedApiPath(subPath));
    }

    @Test
    void setTaskLimit() {

//        initSingleNode();

        final String subPath = ResourcePaths.buildPath("1", JobNodeResource.TASK_LIMIT_PATH_PART);

        final Integer newTaskLimit = 500;

        initJobNodeServiceMock();

        doPutTest(
                subPath,
                newTaskLimit);

        final ArgumentCaptor<JobNode> jobNodeCaptor = ArgumentCaptor.forClass(JobNode.class);

        verify(jobNodeService, times(1))
                .update(jobNodeCaptor.capture());

        assertThat(jobNodeCaptor.getValue().getTaskLimit())
                .isEqualTo(newTaskLimit);

        final ArgumentCaptor<JobNode> beforeCaptor = ArgumentCaptor.forClass(JobNode.class);
        final ArgumentCaptor<JobNode> afterCaptor = ArgumentCaptor.forClass(JobNode.class);

        verify(documentEventLog, times(1))
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

    private void initJobNodeServiceMock() {

        Mockito.when(jobNodeService.fetch(anyInt()))
                .thenReturn(Optional.of(buildJobNode(1, 1, "node1")));

        Mockito.when(jobNodeService.update(any()))
                .then(invocation -> {
                    final JobNode input = invocation.getArgument(0);

                    final JobNode output = buildJobNode(
                            input.getId(),
                            input.getVersion() + 1,
                            input.getNodeName());
                    output.setTaskLimit(input.getTaskLimit());
                    output.setSchedule(input.getSchedule());
                    output.setEnabled(input.isEnabled());

                    return output;
                });
    }

    @Test
    void setSchedule() {

        initJobNodeServiceMock();

        final String subPath = ResourcePaths.buildPath("1", JobNodeResource.SCHEDULE_PATH_PART);

        final String newSchedule = "1 1 1";

        doPutTest(
                subPath,
                newSchedule);

        final ArgumentCaptor<JobNode> jobNodeCaptor = ArgumentCaptor.forClass(JobNode.class);

        verify(jobNodeService, times(1))
                .update(jobNodeCaptor.capture());

        assertThat(jobNodeCaptor.getValue().getSchedule())
                .isEqualTo(newSchedule);

        final ArgumentCaptor<JobNode> beforeCaptor = ArgumentCaptor.forClass(JobNode.class);
        final ArgumentCaptor<JobNode> afterCaptor = ArgumentCaptor.forClass(JobNode.class);

        verify(documentEventLog, times(1))
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
        initJobNodeServiceMock();

        final String subPath = ResourcePaths.buildPath("1", JobNodeResource.ENABLED_PATH_PART);

        final boolean newIsEnabled = true;

        doPutTest(
                subPath,
                newIsEnabled);

        final ArgumentCaptor<JobNode> jobNodeCaptor = ArgumentCaptor.forClass(JobNode.class);

        verify(jobNodeService, times(1))
                .update(jobNodeCaptor.capture());

        assertThat(jobNodeCaptor.getValue().isEnabled())
                .isEqualTo(newIsEnabled);

        final ArgumentCaptor<JobNode> beforeCaptor = ArgumentCaptor.forClass(JobNode.class);
        final ArgumentCaptor<JobNode> afterCaptor = ArgumentCaptor.forClass(JobNode.class);

        verify(documentEventLog, times(1))
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
    public JobNodeResource getRestResource() {
        return new JobNodeResourceImpl(
                () -> jobNodeService,
                () -> mockNodeService,
                () -> documentEventLog);
    }
}
