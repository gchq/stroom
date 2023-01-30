package stroom.query.common.v2;

import stroom.node.api.NodeInfo;
import stroom.query.api.v2.ResultStoreSettingsFactory;
import stroom.security.api.SecurityContext;

import java.util.List;
import java.util.Objects;
import javax.inject.Inject;

public final class ResultStoreFactory {

    private final SerialisersFactory serialisersFactory;
    private final SizesProvider sizesProvider;
    private final SecurityContext securityContext;
    private final NodeInfo nodeInfo;
    private final ResultStoreSettingsFactory resultStoreSettingsFactory;

    @Inject
    ResultStoreFactory(final SerialisersFactory serialisersFactory,
                       final SizesProvider sizesProvider,
                       final SecurityContext securityContext,
                       final NodeInfo nodeInfo,
                       final ResultStoreSettingsFactory resultStoreSettingsFactory) {
        this.serialisersFactory = serialisersFactory;
        this.sizesProvider = sizesProvider;
        this.securityContext = securityContext;
        this.nodeInfo = nodeInfo;
        this.resultStoreSettingsFactory = resultStoreSettingsFactory;
    }

    /**
     * @param store The underlying store to use for creating the search responses.
     */
    public ResultStore create(final List<String> highlights,
                              final Coprocessors coprocessors) {
        final String userId = securityContext.getUserId();
        Objects.requireNonNull(userId, "No user is logged in");

        return new ResultStore(
                serialisersFactory,
                sizesProvider,
                userId,
                highlights,
                coprocessors,
                nodeInfo.getThisNodeName(),
                resultStoreSettingsFactory.get());
    }
}
