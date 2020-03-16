package stroom.cache.impl;


import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cache.shared.CacheInfo;
import stroom.cache.shared.CacheInfoResponse;
import stroom.cache.shared.CacheResource;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.task.api.TaskContext;
import stroom.test.common.util.test.AbstractMultiNodeResourceTest;
import stroom.util.shared.ResourcePaths;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
class TestCacheResourceImpl extends AbstractMultiNodeResourceTest<CacheResource> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestCacheResourceImpl.class);

    @Mock
    CacheManagerService cacheManagerService;

    Map<String, CacheManagerService> cacheManagerServiceMocks = new HashMap<>();

    static TaskContext getTaskContext() {

        final TaskContext taskContext = Mockito.mock(TaskContext.class);

        // Set up TaskContext to just return the passed runnable/supplier
        when(taskContext.subTask(Mockito.any(Runnable.class)))
            .thenAnswer(i -> i.getArgument(0));
        when(taskContext.subTask(Mockito.any(Supplier.class)))
            .thenAnswer(i -> i.getArgument(0));

        return taskContext;
    }

    @Override
    public String getResourceBasePath() {
        return CacheResource.BASE_PATH;
    }

    @Override
    public CacheResource getRestResource(final TestNode node,
                                         final List<TestNode> allNodes,
                                         final Map<String, String> baseEndPointUrls) {

        // Set up the NodeService mock
        final NodeService nodeService = Mockito.mock(NodeService.class,
            NodeService.class.getName() + "_" + node.getNodeName());

        when(nodeService.isEnabled(Mockito.anyString()))
            .thenAnswer(invocation ->
                allNodes.stream()
                    .filter(testNode -> testNode.getNodeName().equals(invocation.getArgument(0)))
                    .anyMatch(TestNode::isEnabled));

        when(nodeService.getBaseEndpointUrl(Mockito.anyString()))
            .thenAnswer(invocation -> baseEndPointUrls.get((String) invocation.getArgument(0)));

        // Set up the NodeInfo mock

        final NodeInfo nodeInfo = Mockito.mock(NodeInfo.class,
            NodeInfo.class.getName() + "_" + node.getNodeName());

        when(nodeInfo.getThisNodeName())
            .thenReturn(node.getNodeName());

        // Set up the CacheManagerService mock

        final CacheManagerService cacheManagerService = Mockito.mock(CacheManagerService.class,
            CacheManagerService.class.getName() + "_" + node.getNodeName());

        cacheManagerServiceMocks.put(node.getNodeName(), cacheManagerService);

        when(cacheManagerService.getCacheNames())
            .thenReturn(List.of("cache1", "cache2"));

        when(cacheManagerService.find(Mockito.any(FindCacheInfoCriteria.class)))
            .thenAnswer(invocation -> {
                FindCacheInfoCriteria criteria = (invocation.getArgument(0));
                if (criteria.getName().isConstrained()) {
                    return List.of(new CacheInfo(criteria.getName().getString(), Collections.emptyMap(), node.getNodeName()));
                } else {
                    return List.of(
                        new CacheInfo("cache1", Collections.emptyMap(), node.getNodeName()),
                        new CacheInfo("cache2", Collections.emptyMap(), node.getNodeName()));
                }
            });

        // Now create the service

        return new CacheResourceImpl(
            nodeService,
            nodeInfo,
            webTargetFactory(),
            cacheManagerService,
            TestCacheResourceImpl::getTaskContext);
    }

    @Test
    void list() {
        final String subPath = "";

        final List<String> expectedResponse = List.of("cache1", "cache2");

        initNodes();

        when(cacheManagerServiceMocks.get("node1").getCacheNames())
            .thenReturn(expectedResponse);

        doGetTest(
            subPath,
            List.class,
            expectedResponse);
    }

    @Test
    void info_sameNode() {
        final String subPath = ResourcePaths.buildPath(CacheResource.INFO);

        List<CacheInfo> cacheInfoList = List.of(
            new CacheInfo("cache1", Collections.emptyMap(), "node1"));

        final CacheInfoResponse expectedResponse = new CacheInfoResponse(cacheInfoList);

        initNodes();

        when(cacheManagerService.find(Mockito.any()))
            .thenReturn(cacheInfoList);

        doGetTest(
            subPath,
            CacheInfoResponse.class,
            expectedResponse,
            webTarget -> webTarget.queryParam("cacheName", "cache1"),
            webTarget -> webTarget.queryParam("nodeName", "node1"));
    }

    @Test
    void info_otherNode() {
        final String subPath = ResourcePaths.buildPath(CacheResource.INFO);

        List<CacheInfo> cacheInfoList = List.of(
            new CacheInfo("cache1", Collections.emptyMap(), "node2"));

        final CacheInfoResponse expectedResponse = new CacheInfoResponse(cacheInfoList);

        initNodes();

        when(cacheManagerService.find(Mockito.any()))
            .thenReturn(cacheInfoList);

        doGetTest(
            subPath,
            CacheInfoResponse.class,
            expectedResponse,
            webTarget -> webTarget.queryParam("cacheName", "cache1"),
            webTarget -> webTarget.queryParam("nodeName", "node2"));
    }

    @Test
    void clear_sameNode() {
        final String subPath = "";

        final Long expectedResponse = 1L;

        initNodes();

        when(cacheManagerServiceMocks.get("node1").clear(Mockito.any(FindCacheInfoCriteria.class)))
            .thenReturn(1L);

        doDeleteTest(
            subPath,
            Long.class,
            expectedResponse,
            webTarget -> webTarget.queryParam("cacheName", "cache1"),
            webTarget -> webTarget.queryParam("nodeName", "node1"));

        verify(cacheManagerServiceMocks.get("node1"))
            .clear(Mockito.any());
        verify(cacheManagerServiceMocks.get("node2"), Mockito.never())
            .clear(Mockito.any());
        verify(cacheManagerServiceMocks.get("node3"), Mockito.never())
            .clear(Mockito.any());
    }

    @Test
    void clear_otherNode() {
        final String subPath = "";

        final Long expectedResponse = 1L;

        initNodes();

        when(cacheManagerServiceMocks.get("node2").clear(Mockito.any(FindCacheInfoCriteria.class)))
            .thenReturn(1L);

        doDeleteTest(
            subPath,
            Long.class,
            expectedResponse,
            webTarget -> webTarget.queryParam("cacheName", "cache1"),
            webTarget -> webTarget.queryParam("nodeName", "node2"));

        verify(cacheManagerServiceMocks.get("node1"), Mockito.never())
            .clear(Mockito.any());
        verify(cacheManagerServiceMocks.get("node2"))
            .clear(Mockito.any());
        verify(cacheManagerServiceMocks.get("node3"), Mockito.never())
            .clear(Mockito.any());
    }

}