package stroom.core.welcome;

import stroom.cluster.api.NodeInfo;
import stroom.config.global.shared.SessionInfoResource;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.security.api.SecurityContext;
import stroom.util.shared.BuildInfo;
import stroom.util.shared.SessionInfo;

import javax.inject.Inject;
import javax.inject.Provider;

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

    @Override
    public SessionInfo get() {
        return new SessionInfo(
                securityContextProvider.get().getUserId(),
                nodeInfoProvider.get().getThisNodeName(),
                buildInfoProvider.get());
    }
}
