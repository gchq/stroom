package stroom.core.welcome;

import io.swagger.annotations.Api;
import stroom.node.api.NodeInfo;
import stroom.security.api.SecurityContext;
import stroom.ui.config.shared.UiConfig;
import stroom.util.RestResource;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Api(value = "build-info - /v1")
@Path("/build-info/v1")
@Produces(MediaType.APPLICATION_JSON)
public class BuildInfoResource implements RestResource {
    private final UiConfig uiConfig;
    private final NodeInfo nodeInfo;
    private final SecurityContext securityContext;

    @Inject
    BuildInfoResource(final UiConfig uiConfig,
                      final NodeInfo nodeInfo,
                      final SecurityContext securityContext) {
        this.uiConfig = uiConfig;
        this.nodeInfo = nodeInfo;
        this.securityContext = securityContext;
    }

    @GET
    @Path("/")
    public Response buildInfo() {
        Object response = new Object() {
            public String userName = securityContext.getUserId();
            public String buildVersion = uiConfig.getBuildInfo().getBuildVersion();
            public String buildDate = uiConfig.getBuildInfo().getUpDate();
            public String upDate = uiConfig.getBuildInfo().getUpDate();
            public String nodeName = nodeInfo.getThisNodeName();
        };
        return Response.ok(response).build();
    }
}
