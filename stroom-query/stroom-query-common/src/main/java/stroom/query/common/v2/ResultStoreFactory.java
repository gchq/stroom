package stroom.query.common.v2;

import stroom.node.api.NodeInfo;
import stroom.query.api.v2.SearchRequestSource;
import stroom.security.api.SecurityContext;

import java.util.Objects;
import jakarta.inject.Inject;

public final class ResultStoreFactory {

    private final SizesProvider sizesProvider;
    private final SecurityContext securityContext;
    private final NodeInfo nodeInfo;
    private final ResultStoreSettingsFactory resultStoreSettingsFactory;

    @Inject
    ResultStoreFactory(final SizesProvider sizesProvider,
                       final SecurityContext securityContext,
                       final NodeInfo nodeInfo,
                       final ResultStoreSettingsFactory resultStoreSettingsFactory) {
        this.sizesProvider = sizesProvider;
        this.securityContext = securityContext;
        this.nodeInfo = nodeInfo;
        this.resultStoreSettingsFactory = resultStoreSettingsFactory;
    }

    public ResultStore create(final SearchRequestSource searchRequestSource,
                              final CoprocessorsImpl coprocessors) {
        final String userUuid = securityContext.getUserUuid();
        final String createUser = securityContext.getUserIdentityForAudit();
        Objects.requireNonNull(userUuid, "No user is logged in");

        return new ResultStore(
                searchRequestSource,
                sizesProvider,
                userUuid,
                createUser,
                coprocessors,
                nodeInfo.getThisNodeName(),
                resultStoreSettingsFactory.get());
    }
}
