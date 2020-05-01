package stroom.core.welcome;

import io.swagger.annotations.Api;
import stroom.config.global.shared.SessionInfoResource;
import stroom.node.api.NodeInfo;
import stroom.security.api.SecurityContext;
import stroom.util.shared.BuildInfo;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.SessionInfo;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Api(value = "sessionInfo - /v1")
@Path("/sessionInfo" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SessionInfoResourceImpl implements SessionInfoResource {
    private final NodeInfo nodeInfo;
    private final SecurityContext securityContext;
    private final Provider<BuildInfo> buildInfoProvider;

    @Inject
    SessionInfoResourceImpl(final NodeInfo nodeInfo,
                            final SecurityContext securityContext,
                            final Provider<BuildInfo> buildInfoProvider) {
        this.nodeInfo = nodeInfo;
        this.securityContext = securityContext;
        this.buildInfoProvider = buildInfoProvider;
    }

    @GET
    public SessionInfo get() {
        return new SessionInfo(securityContext.getUserId(), nodeInfo.getThisNodeName(), buildInfoProvider.get());
    }
}
