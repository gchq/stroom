package stroom.node.impl;

import stroom.config.common.UriFactory;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.node.impl.TestNodeServiceImpl.NoddyRestResource;
import stroom.security.mock.MockSecurityContext;
import stroom.test.common.util.test.AbstractResourceTest;
import stroom.util.shared.RestResource;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.client.SyncInvoker;
import java.net.URI;

class TestNodeServiceImpl extends AbstractResourceTest<NoddyRestResource> {

    @Mock
    NodeInfo nodeInfo;
    @Mock
    UriFactory uriFactory;
    @Mock
    NodeDao nodeDao;

    @Disabled // TODO @AT Work in progress
    @Test
    void remoteRestResult_thisNode() {

        Mockito.when(nodeInfo.getThisNodeName())
                .thenReturn("node1");

        Mockito.when(uriFactory.nodeUri(Mockito.anyString()))
                .thenReturn(URI.create(""));

        NodeService nodeService = new NodeServiceImpl(
                new MockSecurityContext(),
                nodeDao,
                nodeInfo,
                uriFactory,
                null,
                webTargetFactory());


        final String name = "jimbob";
        final String response = nodeService.remoteRestResult("node1",
                String.class,
                "/" + name,
                () -> "Hello " + name + " this is local calling",
                SyncInvoker::get);

        Assertions.assertThat(response)
                .contains("local");
    }

    @Override
    public NoddyRestResource getRestResource() {
        return new NoddyRestResource();
    }

    @Override
    public String getResourceBasePath() {
        return NoddyRestResource.BASE_PATH;
    }

    static class NoddyRestResource implements RestResource {

        public static final String BASE_PATH = "/";

        @Path("/{name}")
        public String hello(@PathParam("name") final String name) {
            return "hello " + name + " this is remote calling.";
        }
    }
}