package stroom.cache.impl;


import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cache.shared.CacheInfo;
import stroom.cache.shared.CacheInfoResponse;
import stroom.cache.shared.CacheResource;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.task.api.TaskContext;
import stroom.test.common.util.test.AbstractResourceTest;
import stroom.util.shared.ResourcePaths;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static org.mockito.Mockito.when;

@Deprecated // Only here as an example of how to use AbstractResourceTest, superseded by TestCacheResourceImpl
class TestCacheResourceImplOld extends AbstractResourceTest<CacheResource> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestCacheResourceImplOld.class);

    @Mock
    NodeService nodeService;

    @Mock
    NodeInfo nodeInfo;

    @Mock
    CacheManagerService cacheManagerService;

    /**
     * Create a {@link TaskContext} that wraps the runnable/supplier with no
     * extra functionality
     */
    static TaskContext createTaskContext() {

        final TaskContext taskContext = Mockito.mock(TaskContext.class);

        // Set up TaskContext to just return the passed runnable/supplier
        when(taskContext.subTask(Mockito.any(Runnable.class)))
            .thenAnswer(i -> i.getArgument(0));
        when(taskContext.subTask(Mockito.any(Supplier.class)))
            .thenAnswer(i -> i.getArgument(0));

        return taskContext;
    }

    @Override
    public CacheResource getRestResource() {
        return new CacheResourceImpl(
            nodeService,
            nodeInfo,
            webTargetFactory(),
            cacheManagerService,
            TestCacheResourceImplOld::createTaskContext);
    }

    @Override
    public String getResourceBasePath() {
        return CacheResource.BASE_PATH;
    }

    @Test
    void list() {
        final String subPath = "";

        final List<String> expectedResponse = List.of("cache1", "cache2");

        when(cacheManagerService.getCacheNames())
            .thenReturn(expectedResponse);

        doGetTest(subPath, List.class, expectedResponse);
    }

    @Test
    void info_sameNode() {
        final String subPath = ResourcePaths.buildPath(CacheResource.INFO);

        List<CacheInfo> cacheInfoList = List.of(
            new CacheInfo("cache1", Collections.emptyMap(), "node1"));

        final CacheInfoResponse expectedResponse = new CacheInfoResponse(cacheInfoList);

        when(nodeInfo.getThisNodeName())
            .thenReturn("node1");

        when(cacheManagerService.find(Mockito.any())).thenReturn(cacheInfoList);

        doGetTest(
            subPath,
            CacheInfoResponse.class,
            expectedResponse,
            webTarget -> webTarget.queryParam("cacheName", "cache1"),
            webTarget -> webTarget.queryParam("nodeName", "node1"));

    }

}