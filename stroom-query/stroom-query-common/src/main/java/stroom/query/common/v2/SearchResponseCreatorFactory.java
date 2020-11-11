package stroom.query.common.v2;

import javax.inject.Inject;
import java.time.Duration;

public class SearchResponseCreatorFactory {
    private final SizesProvider sizesProvider;

    @Inject
    public SearchResponseCreatorFactory(final SizesProvider sizesProvider) {
        this.sizesProvider = sizesProvider;
    }

    /**
     * @param store The underlying store to use for creating the search responses.
     */
    public SearchResponseCreator create(final Store store) {
        return new SearchResponseCreator(sizesProvider, store);
    }

    /**
     * @param store          The underlying store to use for creating the search responses.
     * @param defaultTimeout The service's default timeout period to use for waiting for the store to complete. This
     *                       will be used when the search request hasn't specified a timeout period.
     */
    public SearchResponseCreator create(final Store store,
                                        final Duration defaultTimeout) {
        return new SearchResponseCreator(sizesProvider, store, defaultTimeout);
    }
}
