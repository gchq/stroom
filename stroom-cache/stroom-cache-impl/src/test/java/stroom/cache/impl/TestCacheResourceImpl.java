package stroom.cache.impl;


import org.glassfish.jersey.logging.LoggingFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.jupiter.api.BeforeEach;
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
import stroom.util.shared.RestResource;

import javax.ws.rs.core.Application;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;

import static org.mockito.Mockito.when;

class TestCacheResourceImpl extends AbstractResourceTest<CacheResource> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestCacheResourceImpl.class);

    @Mock
    NodeService nodeService;

    @Mock
    NodeInfo nodeInfo;

    @Mock
    CacheManagerService cacheManagerService;

    @Mock
    TaskContext taskContext;

    @BeforeEach
    private void setup() {


    }

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
    public CacheResource getRestResource() {
        return new CacheResourceImpl(
            nodeService,
            nodeInfo,
            webTargetFactory(),
            cacheManagerService,
            TestCacheResourceImpl::getTaskContext);
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

    @Test
    void info_otherNode() {
        final String subPath = ResourcePaths.buildPath(CacheResource.INFO);

        List<CacheInfo> cacheInfoList = List.of(
            new CacheInfo("cache1", Collections.emptyMap(), "node2"));

        final CacheInfoResponse expectedResponse = new CacheInfoResponse(cacheInfoList);

        when(nodeInfo.getThisNodeName())
            .thenReturn("node1");

        when(cacheManagerService.find(Mockito.any())).thenReturn(cacheInfoList);

        when(nodeService.getBaseEndpointUrl("node2")).thenReturn("");


        doGetTest(
            subPath,
            CacheInfoResponse.class,
            expectedResponse,
            webTarget -> webTarget.queryParam("cacheName", "cache1"),
            webTarget -> webTarget.queryParam("nodeName", "node2"));

    }

    @Test
    void clear() {

    }

    @Test
    void getHealth() throws Exception {
        
        LOGGER.info("-------------------");

        final List<String> expectedResponse = List.of("cache1", "cache2");

        when(cacheManagerService.getCacheNames())
            .thenReturn(expectedResponse);


        JerseyTest jerseyTest = new JerseyTestBuilder<>(
            () -> new CacheResourceImpl(
                nodeService,
                nodeInfo,
                webTargetFactory(),
                cacheManagerService,
                TestCacheResourceImpl::getTaskContext), 9090)
            .build();

        jerseyTest.setUp();

        CacheManagerService cacheManagerService2 = Mockito.mock(CacheManagerService.class);

        when(cacheManagerService2.getCacheNames())
            .thenReturn(List.of("cache1", "cache2", "cache3"));

        JerseyTest jerseyTest2 = new JerseyTestBuilder<>(
            () -> new CacheResourceImpl(
                nodeService,
                nodeInfo,
                webTargetFactory(),
                cacheManagerService2,
                TestCacheResourceImpl::getTaskContext), 9090)
            .build();

        jerseyTest2.setUp();

        List<String> response = jerseyTest.target(ResourcePaths.buildPath(
            CacheResource.BASE_PATH))
            .request()
            .get(List.class);

        System.out.println(response);

        List<String> response2 = jerseyTest2.target(ResourcePaths.buildPath(
            CacheResource.BASE_PATH))
            .request()
            .get(List.class);

        System.out.println(response2);
    }

    public static class JerseyTestBuilder<R extends RestResource> {

        private final Supplier<R> resourceSupplier;
        private final int port;

        public JerseyTestBuilder(final Supplier<R> resourceSupplier,
                                 final int port) {
            this.resourceSupplier = resourceSupplier;
            this.port = port;
        }

        public JerseyTest build() {
            return new JerseyTest() {

                @Override
                protected Application configure() {
                    return new ResourceConfig()
                        .register(resourceSupplier.get())
                        .register(
                            new LoggingFeature(
                                java.util.logging.Logger.getLogger(LoggingFeature.DEFAULT_LOGGER_NAME),
                                Level.INFO,
                                LoggingFeature.Verbosity.PAYLOAD_ANY,
                                LoggingFeature.DEFAULT_MAX_ENTITY_SIZE));
                }

                @Override
                protected URI getBaseUri() {
                    return UriBuilder
                        .fromUri("http://localhost/")
                        .port(port)
                        .build();
                }
            };


        }
    }

}