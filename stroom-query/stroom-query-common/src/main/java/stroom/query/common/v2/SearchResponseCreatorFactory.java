package stroom.query.common.v2;

import javax.inject.Inject;

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
}
