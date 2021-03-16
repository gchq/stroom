package stroom.node.impl;

import stroom.config.common.UriFactory;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.node.impl.TestNodeServiceImpl.NoddyRestResource;
import stroom.node.shared.Node;
import stroom.security.mock.MockSecurityContext;
import stroom.test.common.util.test.AbstractMultiNodeResourceTest;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import org.assertj.core.api.Assertions;
import org.glassfish.jersey.server.ContainerRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.client.SyncInvoker;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

class TestNodeServiceImpl extends AbstractMultiNodeResourceTest<NoddyRestResource> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestNodeServiceImpl.class);
    private static final int BASE_PORT = 7040;

    private final Map<String, NoddyRestResource> resourceMap = new HashMap<>();
    private final Map<String, NodeService> nodeServiceMap = new HashMap<>();

    protected TestNodeServiceImpl() {
        super(createNodeList(BASE_PORT));
    }

    @Test
    void remoteRestResult_thisNode() {
        initNodes();

        final String name = "bill";
        final String targetNode = AbstractMultiNodeResourceTest.NODE1;

        final WebTarget webTarget = getFirstNodeWebTarget(ResourcePaths.buildPath(targetNode, name));

        LOGGER.info("webTarget uri: {}", webTarget.getUri());

        final Response response = webTarget
                .request(MediaType.APPLICATION_JSON_TYPE)
                .get();

        final String responseStr = response.readEntity(String.class);
        LOGGER.info("responseStr {}", responseStr);

        Assertions.assertThat(responseStr)
                .isEqualTo(targetNode + " says " + name);

        // Only rest calls to node 1 as it is the target so can run locally
        Assertions.assertThat(getRequestEvents(AbstractMultiNodeResourceTest.NODE1))
                .hasSize(1);
        Assertions.assertThat(getRequestEvents(AbstractMultiNodeResourceTest.NODE2))
                .hasSize(0);
        Assertions.assertThat(getRequestEvents(AbstractMultiNodeResourceTest.NODE3))
                .hasSize(0);
    }

    @Test
    void remoteRestResult_otherNode() {
        initNodes();

        final String name = "bill";
        final String targetNode = AbstractMultiNodeResourceTest.NODE2;

        final WebTarget webTarget = getFirstNodeWebTarget(ResourcePaths.buildPath(targetNode, name));

        LOGGER.info("webTarget uri: {}", webTarget.getUri());

        final Response response = webTarget
                .request(MediaType.APPLICATION_JSON_TYPE)
                .get();

        final String responseStr = response.readEntity(String.class);
        LOGGER.info("responseStr {}", responseStr);

        Assertions.assertThat(responseStr)
                .isEqualTo(targetNode + " says " + name);

        // Rest call to node1 which passes it on to node2 to answer
        Assertions.assertThat(getRequestEvents(AbstractMultiNodeResourceTest.NODE1))
                .hasSize(1);
        Assertions.assertThat(getRequestEvents(AbstractMultiNodeResourceTest.NODE2))
                .hasSize(1);
        Assertions.assertThat(getRequestEvents(AbstractMultiNodeResourceTest.NODE3))
                .hasSize(0);

        ContainerRequest containerRequest = getRequestEvents(AbstractMultiNodeResourceTest.NODE2)
                .get(0)
                .getContainerRequest();

        Assertions.assertThat(containerRequest.getMethod())
                .isEqualTo("GET");

        Assertions.assertThat(containerRequest.getRequestUri().toString())
                .contains(AbstractMultiNodeResourceTest.NODE2);
    }

    @Test
    void remoteRestCall_thisNode() {

        initNodes();

        final String targetNode = AbstractMultiNodeResourceTest.NODE1;

        final WebTarget webTarget = getFirstNodeWebTarget(ResourcePaths.buildPath(targetNode));

        LOGGER.info("webTarget uri: {}", webTarget.getUri());

        final Response response = webTarget
                .request(MediaType.APPLICATION_JSON_TYPE)
                .delete();

        final String responseStr = response.readEntity(String.class);
        LOGGER.info("responseStr {}", responseStr);

        // Only rest calls to node 1 as it is the target so can run locally
        Assertions.assertThat(getRequestEvents(AbstractMultiNodeResourceTest.NODE1))
                .hasSize(1);
        Assertions.assertThat(getRequestEvents(AbstractMultiNodeResourceTest.NODE2))
                .hasSize(0);
        Assertions.assertThat(getRequestEvents(AbstractMultiNodeResourceTest.NODE3))
                .hasSize(0);
    }

    @Test
    void remoteRestCall_otherNode() {

        initNodes();

        final String targetNode = AbstractMultiNodeResourceTest.NODE2;

        final WebTarget webTarget = getFirstNodeWebTarget(ResourcePaths.buildPath(targetNode));

        LOGGER.info("webTarget uri: {}", webTarget.getUri());

        final Response response = webTarget
                .request(MediaType.APPLICATION_JSON_TYPE)
                .delete();

        final String responseStr = response.readEntity(String.class);
        LOGGER.info("responseStr {}", responseStr);

        // Only rest calls to node 1 as it is the target so can run locally
        Assertions.assertThat(getRequestEvents(AbstractMultiNodeResourceTest.NODE1))
                .hasSize(1);
        Assertions.assertThat(getRequestEvents(AbstractMultiNodeResourceTest.NODE2))
                .hasSize(1);
        Assertions.assertThat(getRequestEvents(AbstractMultiNodeResourceTest.NODE3))
                .hasSize(0);

        ContainerRequest containerRequest = getRequestEvents(AbstractMultiNodeResourceTest.NODE2)
                .get(0)
                .getContainerRequest();

        Assertions.assertThat(containerRequest.getMethod())
                .isEqualTo("DELETE");

        Assertions.assertThat(containerRequest.getRequestUri().toString())
                .contains(AbstractMultiNodeResourceTest.NODE2);
    }

    @Override
    public String getResourceBasePath() {
        return NoddyRestResource.BASE_PATH;
    }

    @Override
    public NoddyRestResource getRestResource(final TestNode node,
                                             final List<TestNode> allNodes,
                                             final Map<String, String> baseEndPointUrls) {
        LOGGER.info("Setting up node {}", node.getNodeName());
        final NodeInfo nodeInfo = createNamedMock(NodeInfo.class, node);

        Mockito.when(nodeInfo.getThisNodeName())
                .thenReturn(node.getNodeName());

        final NodeDao nodeDao = createNamedMock(NodeDao.class, node);

        final String baseEndpointUrl = getBaseEndPointUrl(node);
        LOGGER.info("baseEndpointUrl {}", baseEndpointUrl);

        // We don't need the node that comes back

        Mockito.when(nodeDao.getNode(Mockito.anyString()))
                .thenAnswer(invocation -> {
                    final String nodeName = invocation.getArgument(0);

                    return new Node(
                            1,
                            1,
                            0L,
                            "admin",
                            0L,
                            "admin",
                            nodeName,
                            baseEndPointUrls.get(nodeName),
                            10,
                            true);
                });

        final MockSecurityContext mockSecurityContext = new MockSecurityContext();

        UriFactory uriFactory = createNamedMock(UriFactory.class, node);
        Mockito.when(uriFactory.nodeUri(Mockito.anyString()))
                .thenAnswer(invocation ->
                        URI.create(baseEndpointUrl + invocation.getArgument(0)));

        final NodeService nodeService = new NodeServiceImpl(
                mockSecurityContext,
                nodeDao,
                nodeInfo,
                uriFactory,
                null,
                AbstractMultiNodeResourceTest.webTargetFactory(),
                this::getHttpServletRequest);

        final NoddyRestResource noddyRestResource = new NoddyRestResource(nodeService, nodeInfo);

        nodeServiceMap.put(node.getNodeName(), nodeService);
        resourceMap.put(node.getNodeName(), noddyRestResource);

        return noddyRestResource;
    }

    @Path(NoddyRestResource.BASE_PATH)
    public static class NoddyRestResource implements RestResource {

        private final NodeService nodeService;
        private final NodeInfo nodeInfo;

        public static final String BASE_PATH = "/nodes";

        public NoddyRestResource(final NodeService nodeService,
                                 final NodeInfo nodeInfo) {
            this.nodeService = nodeService;
            this.nodeInfo = nodeInfo;
        }

        @GET
        @Path("/{nodeName}/{name}")
        public String hello(@PathParam("nodeName") final String nodeName,
                            @PathParam("name") final String name) {
            LOGGER.info("hello called on node {}", nodeName);

            return nodeService.remoteRestResult(
                    nodeName,
                    String.class,
                    () -> "full path not used",
                    () -> nodeInfo.getThisNodeName() + " says " + name,
                    SyncInvoker::get);
        }

        @DELETE
        @Path("/{nodeName}")
        public void doStuff(@PathParam("nodeName") final String nodeName) {
            LOGGER.info("doStuff called on node {}", nodeName);

            nodeService.remoteRestCall(
                    nodeName,
                    () -> "full path not used",
                    () -> {
                        LOGGER.info("Doing stuff locally on node {}", nodeInfo.getThisNodeName());
                    },
                    SyncInvoker::delete);
        }
    }
}
