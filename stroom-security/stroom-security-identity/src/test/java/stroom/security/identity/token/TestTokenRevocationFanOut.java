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

package stroom.security.identity.token;

import stroom.node.api.FindNodeCriteria;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.TokenRevocationResource;
import stroom.test.common.util.test.AbstractMultiNodeResourceTest;
import stroom.util.shared.UserRef;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Proves the revocation fan-out really crosses the wire, using the same multi-node harness as the session
 * fan-out. The unit-level behaviour (write-first, error isolation) is in {@code TestTokenRevocationService};
 * what this adds is that a peer node is actually called, over the real REST contract, with a body the endpoint
 * accepts.
 * <p>
 * That last part is the reason this test exists rather than being folded into the unit test: the inter-node POST
 * sends an empty JSON entity, and a {@code text/plain} body would be rejected 415 by the remote node. A
 * single-node deployment never exercises the hop at all, so only a test like this can catch it.
 * </p>
 */
@MockitoSettings(strictness = Strictness.LENIENT)
class TestTokenRevocationFanOut extends AbstractMultiNodeResourceTest<TokenRevocationResource> {

    private static final int BASE_PORT = 7040;
    private static final String SUBJECT = "jbloggs";

    private final Map<String, TokenRevocationService> serviceMap = new HashMap<>();
    private final Map<String, RevokedJtiCache> cacheMap = new HashMap<>();

    public TestTokenRevocationFanOut() {
        super(createNodeList(BASE_PORT));
    }

    @BeforeEach
    void beforeEach() {
        serviceMap.clear();
        cacheMap.clear();
    }

    @Test
    void revokingOnOneNodeTellsThePeersToDropTheirDenylists() throws InterruptedException {
        initNodes();

        serviceMap.get("node1").revokeForSubject(SUBJECT);

        Thread.sleep(50);

        // node1 is local, so it invalidates directly rather than calling itself.
        Assertions.assertThat(getRequestEvents("node1")).isEmpty();
        // node2 is a remote, enabled peer, so it is told over REST.
        Assertions.assertThat(getRequestEvents("node2")).hasSize(1);
        // node3 is disabled, so it is not in the node list at all.
        Assertions.assertThat(getRequestEvents("node3")).isEmpty();

        // The local cache is dropped without a round trip...
        Mockito.verify(cacheMap.get("node1")).invalidate();
        // ...and the peer's cache is dropped as a result of the call it received.
        Mockito.verify(cacheMap.get("node2")).invalidate();
    }

    @Override
    public String getResourceBasePath() {
        return TokenRevocationResource.BASE_PATH;
    }

    @Override
    public TokenRevocationResource getRestResource(final TestNode node,
                                                   final List<TestNode> allNodes,
                                                   final Map<String, String> baseEndPointUrls) {
        final NodeService nodeService = Mockito.mock(NodeService.class,
                NodeService.class.getName() + "_" + node.getNodeName());
        when(nodeService.isEnabled(anyString()))
                .thenAnswer(invocation -> allNodes.stream()
                        .filter(testNode -> testNode.getNodeName().equals(invocation.getArgument(0)))
                        .anyMatch(TestNode::isEnabled));
        when(nodeService.getBaseEndpointUrl(anyString()))
                .thenAnswer(invocation -> baseEndPointUrls.get((String) invocation.getArgument(0)));
        when(nodeService.findNodeNames(any(FindNodeCriteria.class)))
                .thenReturn(List.of("node1", "node2"));

        final NodeInfo nodeInfo = Mockito.mock(NodeInfo.class,
                NodeInfo.class.getName() + "_" + node.getNodeName());
        when(nodeInfo.getThisNodeName()).thenReturn(node.getNodeName());

        final OAuthTokenDao oAuthTokenDao = Mockito.mock(OAuthTokenDao.class,
                OAuthTokenDao.class.getName() + "_" + node.getNodeName());
        when(oAuthTokenDao.revokeBySubjectId(anyString(), anyString(), anyLong())).thenReturn(1);

        final RevokedJtiCache revokedJtiCache = Mockito.mock(RevokedJtiCache.class,
                RevokedJtiCache.class.getName() + "_" + node.getNodeName());
        cacheMap.put(node.getNodeName(), revokedJtiCache);

        final SecurityContext securityContext = Mockito.mock(SecurityContext.class,
                SecurityContext.class.getName() + "_" + node.getNodeName());
        when(securityContext.getUserRef())
                .thenReturn(UserRef.builder().uuid("admin").subjectId("admin").build());
        when(securityContext.secureResult(any(AppPermission.class), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        Mockito.doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(securityContext).asProcessingUser(any(Runnable.class));

        final TokenRevocationService service = new TokenRevocationService(
                oAuthTokenDao,
                revokedJtiCache,
                securityContext,
                nodeInfo,
                nodeService,
                webTargetFactory());
        serviceMap.put(node.getNodeName(), service);

        return new TokenRevocationResourceImpl(() -> service);
    }
}
