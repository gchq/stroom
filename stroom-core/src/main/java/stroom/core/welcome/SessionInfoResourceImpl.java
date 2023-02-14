package stroom.core.welcome;

import stroom.config.global.shared.SessionInfoResource;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.node.api.NodeInfo;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.util.shared.BuildInfo;
import stroom.util.shared.SessionInfo;
import stroom.util.shared.UserName;

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
        final UserIdentity userIdentity = securityContextProvider.get().getUserIdentity();
        final UserName userName = new UserName(
                userIdentity.getId(),
                userIdentity.getPreferredUsername(),
                userIdentity.getFullName().orElse(null));
        return new SessionInfo(
                userName,
                nodeInfoProvider.get().getThisNodeName(),
                buildInfoProvider.get());
    }
}
