package stroom.query.common.v2;

import stroom.node.api.NodeInfo;
import stroom.query.api.v2.SearchRequestSource;
import stroom.security.api.SecurityContext;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;

import java.util.Objects;

public final class ResultStoreFactory {

    private final SizesProvider sizesProvider;
    private final SecurityContext securityContext;
    private final NodeInfo nodeInfo;
    private final ResultStoreSettingsFactory resultStoreSettingsFactory;
    private final MapDataStoreFactory mapDataStoreFactory;

    @Inject
    ResultStoreFactory(final SizesProvider sizesProvider,
                       final SecurityContext securityContext,
                       final NodeInfo nodeInfo,
                       final ResultStoreSettingsFactory resultStoreSettingsFactory,
                       final MapDataStoreFactory mapDataStoreFactory) {
        this.sizesProvider = sizesProvider;
        this.securityContext = securityContext;
        this.nodeInfo = nodeInfo;
        this.resultStoreSettingsFactory = resultStoreSettingsFactory;
        this.mapDataStoreFactory = mapDataStoreFactory;
    }

    public ResultStore create(final SearchRequestSource searchRequestSource,
                              final CoprocessorsImpl coprocessors) {
        final UserRef userRef = securityContext.getUserRef();
        Objects.requireNonNull(userRef, "No user is logged in");

        return new ResultStore(
                searchRequestSource,
                sizesProvider,
                userRef,
                coprocessors,
                nodeInfo.getThisNodeName(),
                resultStoreSettingsFactory.get(),
                mapDataStoreFactory);
    }
}
