/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.security.impl;

import stroom.node.api.FindNodeCriteria;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.security.mock.MockSecurityContext;
import stroom.security.shared.SessionListResponse;
import stroom.security.shared.SessionResource;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.task.shared.ThreadPool;
import stroom.test.common.TestUtil;
import stroom.test.common.util.test.AbstractMultiNodeResourceTest;

import jakarta.servlet.http.HttpServletRequest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
class TestSessionListListener extends AbstractMultiNodeResourceTest<SessionResource> {

    private static ExecutorService executorService;
    private static ExecutorProvider executorProvider;

    private final Map<String, SessionListService> sessionListServiceMap = new HashMap<>();

    private static final int BASE_PORT = 7030;


    public TestSessionListListener() {
        super(createNodeList(BASE_PORT));
    }

    @BeforeAll
    static void beforeAll() {
        executorService = Executors.newCachedThreadPool();
        executorProvider = new ExecutorProvider() {

            @Override
            public Executor get() {
                return executorService;
            }

            @Override
            public Executor get(final ThreadPool threadPool) {
                return executorService;
            }
        };
    }

    @AfterAll
    static void afterAll() {
        executorService.shutdown();
    }

    @BeforeEach
    void beforeEach() {
        sessionListServiceMap.clear();
    }

    @Test
    void listSessions() throws InterruptedException {

        initNodes();

        final SessionListService sessionListService1 = sessionListServiceMap.get("node1");

        final SessionListResponse sessionListResponse = sessionListService1.listSessions();

        Thread.sleep(50);

        // We call method on node1, so no requests
        Assertions.assertThat(getRequestEvents("node1"))
                .hasSize(0);
        // Node is remote so one call
        Assertions.assertThat(getRequestEvents("node2"))
                .hasSize(1);
        // Node is disabled, so no requests
        Assertions.assertThat(getRequestEvents("node3"))
                .hasSize(0);
    }

    @Test
    void testListSessions_oneNode() throws InterruptedException {
        initNodes();

        final SessionListService sessionListService1 = sessionListServiceMap.get("node1");

        final SessionListResponse sessionListResponse = sessionListService1.listSessions("node2");

        Thread.sleep(50);

        // We call method on node1, so no requests
        Assertions.assertThat(getRequestEvents("node1"))
                .hasSize(0);
        // Node is remote so one call
        Assertions.assertThat(getRequestEvents("node2"))
                .hasSize(1);
        // Node is disabled, so no requests
        Assertions.assertThat(getRequestEvents("node3"))
                .hasSize(0);
    }

    @Override
    public String getResourceBasePath() {
        return SessionResource.BASE_PATH;
    }

    @Override
    public SessionResource getRestResource(final TestNode node,
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

        when(nodeService.findNodeNames(Mockito.any(FindNodeCriteria.class)))
                .thenReturn(List.of("node1", "node2"));

        // Set up the NodeInfo mock

        final NodeInfo nodeInfo = Mockito.mock(NodeInfo.class,
                NodeInfo.class.getName() + "_" + node.getNodeName());

        when(nodeInfo.getThisNodeName())
                .thenReturn(node.getNodeName());

        final SessionListService sessionListService = new SessionListListener(
                nodeInfo,
                nodeService,
                new SimpleTaskContextFactory(),
                webTargetFactory(),
                Mockito.mock(StroomUserIdentityFactory.class),
                new MockSecurityContext(),
                executorProvider);

        sessionListServiceMap.put(node.getNodeName(), sessionListService);

        return new SessionResourceImpl(
                TestUtil.mockProvider(OpenIdManager.class),
                TestUtil.mockProvider(HttpServletRequest.class),
                TestUtil.mockProvider(AuthenticationEventLog.class),
                () -> sessionListService,
                TestUtil.mockProvider(StroomUserIdentityFactory.class),
                MockSecurityContext::new);
    }
}
