package stroom.core.welcome;

import stroom.config.global.shared.SessionInfoResource;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.node.api.NodeInfo;
import stroom.security.api.SecurityContext;
import stroom.util.shared.BuildInfo;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.SessionInfo;

import io.swagger.v3.oas.annotations.tags.Tag;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Tag(name = "Session Info")
@Path("/sessionInfo" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@AutoLogged
public class SessionInfoResourceImpl implements SessionInfoResource {

    private final Provider<NodeInfo> nodeInfoProvider;
    private final Provider<SecurityContext> securityContextProvider;
    private final Provider<BuildInfo> buildInfoProvider;

    @Inject
    SessionInfoResourceImpl(final Provider<NodeInfo> nodeInfoProvider,
                            final Provider<SecurityContext> securityContextProvider,
                            final Provider<BuildInfo> buildInfoProvider) {
        this.nodeInfoProvider = nodeInfoProvider;
        this.securityContextProvider = securityContextProvider;
        this.buildInfoProvider = buildInfoProvider;
    }

    @GET
    public SessionInfo get() {
        return new SessionInfo(
                securityContextProvider.get().getUserId(),
                nodeInfoProvider.get().getThisNodeName(),
                buildInfoProvider.get());
    }
}
