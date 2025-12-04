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

package stroom.cache.service.impl;


import stroom.cache.shared.CacheInfoResponse;
import stroom.cache.shared.CacheNamesResponse;
import stroom.cache.shared.CacheResource;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.test.common.util.test.AbstractMultiNodeResourceTest;
import stroom.util.jersey.UriBuilderUtil;
import stroom.util.shared.PropertyPath;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.cache.CacheIdentity;
import stroom.util.shared.cache.CacheInfo;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
class TestCacheResourceImpl extends AbstractMultiNodeResourceTest<CacheResource> {

    @Mock
    private CacheManagerService cacheManagerService;

    private final Map<String, CacheManagerService> cacheManagerServiceMocks = new HashMap<>();

    private static final int BASE_PORT = 7010;

    public TestCacheResourceImpl() {
        super(createNodeList(BASE_PORT));
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
                .thenAnswer(invocation -> baseEndPointUrls.get(invocation.getArgument(0)));

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
                    final FindCacheInfoCriteria criteria = (invocation.getArgument(0));
                    if (criteria.getName().isConstrained()) {
                        return List.of(buildCacheInfo(
                                criteria.getName().getString(),
                                node.getNodeName()));
                    } else {
                        return List.of(
                                buildCacheInfo(
                                        "cache1",
                                        node.getNodeName()),
                                buildCacheInfo(
                                        "cache2",
                                        node.getNodeName()));
                    }
                });

        // Now create the service

        return new CacheResourceImpl(
                () -> nodeService,
                () -> nodeInfo,
                AbstractMultiNodeResourceTest::webTargetFactory,
                () -> cacheManagerService,
                null);
    }

    @Test
    void list_sameNode() {
        final String subPath = ResourcePaths.buildPath(CacheResource.LIST);

        final List<CacheIdentity> caches = Stream.of(
                        "cache1",
                        "cache2")
                .map(name -> new CacheIdentity(name, PropertyPath.fromParts("root", name)))
                .collect(Collectors.toList());

        final CacheNamesResponse expectedResponse = new CacheNamesResponse(caches);

        initNodes();

        when(cacheManagerServiceMocks.get("node1").getCacheIdentities())
                .thenReturn(caches);

        doGetTest(
                subPath,
                CacheNamesResponse.class,
                expectedResponse,
                webTarget -> UriBuilderUtil.addParam(webTarget, "nodeName", "node1"));
    }

    @Test
    void list_otherNode() {
        final String subPath = ResourcePaths.buildPath(CacheResource.LIST);

        final List<CacheIdentity> caches = Stream.of(
                        "cache1",
                        "cache2")
                .map(name -> new CacheIdentity(name, PropertyPath.fromParts("root", name)))
                .collect(Collectors.toList());

        final CacheNamesResponse expectedResponse = new CacheNamesResponse(caches);

        initNodes();

        when(cacheManagerServiceMocks.get("node2").getCacheIdentities())
                .thenReturn(caches);

        doGetTest(
                subPath,
                CacheNamesResponse.class,
                expectedResponse,
                webTarget -> UriBuilderUtil.addParam(webTarget, "nodeName", "node2"));
    }

    @Test
    void info_sameNode() {
        final String subPath = ResourcePaths.buildPath(CacheResource.INFO);

        final List<CacheInfo> cacheInfoList = List.of(
                buildCacheInfo("cache1", "node1"));

        final CacheInfoResponse expectedResponse = new CacheInfoResponse(cacheInfoList);

        initNodes();

        when(cacheManagerService.find(Mockito.any()))
                .thenReturn(cacheInfoList);

        doGetTest(
                subPath,
                CacheInfoResponse.class,
                expectedResponse,
                webTarget -> UriBuilderUtil.addParam(webTarget, "cacheName", "cache1"),
                webTarget -> UriBuilderUtil.addParam(webTarget, "nodeName", "node1"));
    }

    @Test
    void info_otherNode() {
        final String subPath = ResourcePaths.buildPath(CacheResource.INFO);

        final List<CacheInfo> cacheInfoList = List.of(
                buildCacheInfo("cache1", "node2"));

        final CacheInfoResponse expectedResponse = new CacheInfoResponse(cacheInfoList);

        initNodes();

        when(cacheManagerService.find(Mockito.any()))
                .thenReturn(cacheInfoList);

        doGetTest(
                subPath,
                CacheInfoResponse.class,
                expectedResponse,
                webTarget -> UriBuilderUtil.addParam(webTarget, "cacheName", "cache1"),
                webTarget -> UriBuilderUtil.addParam(webTarget, "nodeName", "node2"));
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
                webTarget -> UriBuilderUtil.addParam(webTarget, "cacheName", "cache1"),
                webTarget -> UriBuilderUtil.addParam(webTarget, "nodeName", "node1"));

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
                webTarget -> UriBuilderUtil.addParam(webTarget, "cacheName", "cache1"),
                webTarget -> UriBuilderUtil.addParam(webTarget, "nodeName", "node2"));

        verify(cacheManagerServiceMocks.get("node1"), Mockito.never())
                .clear(Mockito.any());
        verify(cacheManagerServiceMocks.get("node2"))
                .clear(Mockito.any());
        verify(cacheManagerServiceMocks.get("node3"), Mockito.never())
                .clear(Mockito.any());
    }

    private CacheInfo buildCacheInfo(final String cacheName,
                                     final String nodeName) {
        return new CacheInfo(cacheName, PropertyPath.fromParts("test"), Collections.emptyMap(), nodeName);
    }

}
