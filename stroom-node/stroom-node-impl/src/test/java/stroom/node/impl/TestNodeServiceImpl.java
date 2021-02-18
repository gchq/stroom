package stroom.node.impl;

import stroom.config.common.UriFactory;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.node.impl.TestNodeServiceImpl.NoddyRestResource;
import stroom.node.shared.Node;
import stroom.security.mock.MockSecurityContext;
import stroom.test.common.util.test.AbstractResourceTest;
import stroom.util.shared.RestResource;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.client.SyncInvoker;

class TestNodeServiceImpl extends AbstractResourceTest<NoddyRestResource> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestNodeServiceImpl.class);

    @Mock
    NodeInfo nodeInfo;
    @Mock
    UriFactory uriFactory;
    @Mock
    NodeDao nodeDao;

    @Test
    void remoteRestResult_thisNode() {

        final String thisNodeName = "node1";

        Mockito.when(nodeInfo.getThisNodeName())
                .thenReturn(thisNodeName);

        Mockito.when(uriFactory.nodeUri(Mockito.anyString()))
                .thenReturn(URI.create(""));

        final Node node = new Node();
        node.setId(1);
        node.setUrl("");
        node.setName(thisNodeName);

        Mockito.when(nodeDao.getNode(Mockito.eq(thisNodeName)))
                .thenReturn(new Node());

        Mockito.when(nodeDao.update(Mockito.any()))
                .thenReturn(node);

        final NodeService nodeService = new NodeServiceImpl(
                new MockSecurityContext(),
                nodeDao,
                nodeInfo,
                uriFactory,
                null,
                webTargetFactory());

        final String name = "jimbob";
        final String response = nodeService.remoteRestResult(
                thisNodeName,
                String.class,
                "/" + name,
                () -> "Hello " + name + " this is local calling",
                SyncInvoker::get);

        Assertions.assertThat(response)
                .contains("local");
    }

    @Test
    void remoteRestResult_otherNode() {

        final String thisNodeName = "node1";
        final String otherNodeName = "node2";
        final String thisUrl = "http://localhost:1";
        final String otherUrl = getServerBaseUri().toString();

        System.out.println("otherUrl: " + otherUrl);

        Mockito.when(nodeInfo.getThisNodeName())
                .thenReturn(thisNodeName);

        final Node thisNode = new Node();
        thisNode.setId(1);
        thisNode.setUrl(thisUrl);
        thisNode.setName(thisNodeName);

        final Node otherNode = new Node();
        otherNode.setId(2);
        otherNode.setUrl(otherUrl);
        otherNode.setName(otherNodeName);

        // This is called to get the uri of this node
        Mockito
                .doAnswer(invocation ->
                        new URI(thisNode.getUrl()))
                .when(uriFactory)
                .nodeUri(Mockito.any());

        // Return the appropriate node obj depending on name
        Mockito
                .doAnswer(invocation -> {
                    final String nodeName = invocation.getArgument(0);
                    if (thisNodeName.equals(nodeName)) {
                        return thisNode;
                    } else if (otherNodeName.equals(nodeName)) {
                        return otherNode;
                    } else {
                        throw new RuntimeException("Should not get here");
                    }
                })
                .when(nodeDao)
                .getNode(Mockito.any());

        final NodeService nodeService = new NodeServiceImpl(
                new MockSecurityContext(),
                nodeDao,
                nodeInfo,
                uriFactory,
                null,
                webTargetFactory());

        final String name = "jimbob";
        final String response = nodeService.remoteRestResult(
                otherNodeName,
                String.class,
                "/" + name,
                () -> "Hello " + name + " this is local calling",
                SyncInvoker::get);

        // Passed in otherNode's name so it should call out to otherNode via rest.
        Assertions.assertThat(response)
                .contains("remote");
    }

    @Test
    void remoteRestCall_thisNode() {

        final String thisNodeName = "node1";

        Mockito.when(nodeInfo.getThisNodeName())
                .thenReturn(thisNodeName);

        Mockito.when(uriFactory.nodeUri(Mockito.anyString()))
                .thenReturn(URI.create(""));

        final Node node = new Node();
        node.setId(1);
        node.setUrl("");
        node.setName(thisNodeName);

        Mockito.when(nodeDao.getNode(Mockito.eq(thisNodeName)))
                .thenReturn(new Node());

        Mockito.when(nodeDao.update(Mockito.any()))
                .thenReturn(node);

        final NodeService nodeService = new NodeServiceImpl(
                new MockSecurityContext(),
                nodeDao,
                nodeInfo,
                uriFactory,
                null,
                webTargetFactory());

        final AtomicBoolean wasLocalRunnableCalled = new AtomicBoolean(false);
        nodeService.remoteRestCall(
                thisNodeName,
                "/",
                () -> {
                    wasLocalRunnableCalled.set(true);
                    System.out.println("Doing local stuff");
                },
                SyncInvoker::delete);

        Assertions.assertThat(wasLocalRunnableCalled)
                .isTrue();
    }

    @Test
    void remoteRestCall_otherNode() {

        final String thisNodeName = "node1";
        final String otherNodeName = "node2";
        final String thisUrl = "http://localhost:1";
        final String otherUrl = getServerBaseUri().toString();

        System.out.println("otherUrl: " + otherUrl);

        Mockito.when(nodeInfo.getThisNodeName())
                .thenReturn(thisNodeName);

        final Node thisNode = new Node();
        thisNode.setId(1);
        thisNode.setUrl(thisUrl);
        thisNode.setName(thisNodeName);

        final Node otherNode = new Node();
        otherNode.setId(2);
        otherNode.setUrl(otherUrl);
        otherNode.setName(otherNodeName);

        // This is called to get the uri of this node
        Mockito
                .doAnswer(invocation ->
                        new URI(thisNode.getUrl()))
                .when(uriFactory)
                .nodeUri(Mockito.any());

        // Return the appropriate node obj depending on name
        Mockito
                .doAnswer(invocation -> {
                    final String nodeName = invocation.getArgument(0);
                    if (thisNodeName.equals(nodeName)) {
                        return thisNode;
                    } else if (otherNodeName.equals(nodeName)) {
                        return otherNode;
                    } else {
                        throw new RuntimeException("Should not get here");
                    }
                })
                .when(nodeDao)
                .getNode(Mockito.any());

        final NodeService nodeService = new NodeServiceImpl(
                new MockSecurityContext(),
                nodeDao,
                nodeInfo,
                uriFactory,
                null,
                webTargetFactory());

        final AtomicBoolean wasLocalRunnableCalled = new AtomicBoolean(false);
        nodeService.remoteRestCall(
                otherNodeName,
                "/",
                () -> {
                    wasLocalRunnableCalled.set(true);
                    System.out.println("Doing local stuff");
                },
                SyncInvoker::delete);

        // Passed in otherNode's name so it should call out to otherNode via rest.
        Assertions.assertThat(wasLocalRunnableCalled)
                .isFalse();
    }

    @Override
    public NoddyRestResource getRestResource() {
        return new NoddyRestResource();
    }

    @Override
    public String getResourceBasePath() {
        return NoddyRestResource.BASE_PATH;
    }

    @Path(NoddyRestResource.BASE_PATH)
    public static class NoddyRestResource implements RestResource {

        public static final String BASE_PATH = "";

        @GET
        @Path("/{name}")
        public String hello(@PathParam("name") final String name) {
            return "hello " + name + " this is remote calling.";
        }

        @DELETE
        @Path("/")
        public void doStuff() {
            System.out.println("Doing remote stuff");
        }
    }
}
