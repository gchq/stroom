package stroom.cache.impl;


import stroom.cache.shared.CacheInfo;
import stroom.cache.shared.CacheInfoResponse;
import stroom.cache.shared.CacheResource;
import stroom.node.mock.MockNodeService;
import stroom.test.common.util.test.AbstractResourceTest;
import stroom.util.shared.ResourcePaths;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.when;

class TestCacheResourceImpl extends AbstractResourceTest<CacheResource> {

    @Mock
    private CacheManagerService cacheManagerService;
    private MockNodeService mockNodeService = new MockNodeService();

    @Override
    public String getResourceBasePath() {
        return CacheResource.BASE_PATH;
    }

    @Override
    public CacheResource getRestResource() {
        return new CacheResourceImpl(
                () -> mockNodeService,
                () -> cacheManagerService,
                null);
    }

    @Test
    void list() {
        final String subPath = "";

        final List<String> expectedResponse = List.of("cache1", "cache2");

        when(cacheManagerService.getCacheNames())
                .thenReturn(expectedResponse);

        doGetTest(
                subPath,
                List.class,
                expectedResponse);
    }

    @Test
    void info() {
        final String subPath = ResourcePaths.buildPath(
                CacheResource.INFO,
                "cache1",
                "node1");

        List<CacheInfo> cacheInfoList = List.of(
                new CacheInfo("cache1", Collections.emptyMap(), "node1"));

        final CacheInfoResponse expectedResponse = new CacheInfoResponse(cacheInfoList);

        when(cacheManagerService.find(Mockito.any()))
                .thenReturn(cacheInfoList);

        doGetTest(
                subPath,
                CacheInfoResponse.class,
                expectedResponse);

        Assertions.assertThat(mockNodeService.getLastUrl())
                .isEqualTo(ResourcePaths.buildAuthenticatedApiPath(CacheResource.BASE_PATH, subPath));
    }

    @Test
    void clear() {
        final String subPath = ResourcePaths.buildPath(
                "cache1",
                "node1");

        final Long expectedResponse = 1L;

        when(cacheManagerService.clear(Mockito.any(FindCacheInfoCriteria.class)))
                .thenReturn(1L);

        doDeleteTest(
                subPath,
                Long.class,
                expectedResponse);

        Assertions.assertThat(mockNodeService.getLastUrl())
                .isEqualTo(ResourcePaths.buildAuthenticatedApiPath(CacheResource.BASE_PATH, subPath));
    }
}
