package stroom.core.welcome;

import stroom.config.global.shared.SessionInfoResource;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.node.api.NodeInfo;
import stroom.security.api.SecurityContext;
import stroom.util.shared.BuildInfo;
import stroom.util.shared.SessionInfo;
import stroom.util.shared.UserName;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

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
        // TODO: 26/07/2023 Would be nice to include the groups the user is a member of
        final UserName userName = securityContextProvider.get().getUserName();
        return new SessionInfo(
                userName,
                nodeInfoProvider.get().getThisNodeName(),
                buildInfoProvider.get());
    }
}
