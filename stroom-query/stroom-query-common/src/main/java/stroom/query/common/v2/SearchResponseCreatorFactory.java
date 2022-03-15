package stroom.query.common.v2;

import javax.inject.Inject;

public final class SearchResponseCreatorFactory {

    private final SizesProvider sizesProvider;

    @Inject
    public SearchResponseCreatorFactory(final SizesProvider sizesProvider) {
        this.sizesProvider = sizesProvider;
    }

    /**
     * @param store The underlying store to use for creating the search responses.
     */
    public SearchResponseCreator create(final String userId, final Store store) {
        return new SearchResponseCreator(userId, sizesProvider, store);
    }
}
