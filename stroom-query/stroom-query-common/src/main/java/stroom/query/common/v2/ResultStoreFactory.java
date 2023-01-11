package stroom.query.common.v2;

import stroom.security.api.SecurityContext;

import java.util.List;
import java.util.Objects;
import javax.inject.Inject;

public final class ResultStoreFactory {

    private final SerialisersFactory serialisersFactory;
    private final SizesProvider sizesProvider;
    private final SecurityContext securityContext;

    @Inject
    ResultStoreFactory(final SerialisersFactory serialisersFactory,
                       final SizesProvider sizesProvider,
                       final SecurityContext securityContext) {
        this.serialisersFactory = serialisersFactory;
        this.sizesProvider = sizesProvider;
        this.securityContext = securityContext;
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
                coprocessors);
    }
}
