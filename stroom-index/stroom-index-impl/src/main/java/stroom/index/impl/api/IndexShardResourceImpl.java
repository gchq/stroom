package stroom.index.impl.api;

import stroom.index.impl.IndexShardService;

import javax.inject.Inject;

public class IndexShardResourceImpl implements IndexShardResource {

    private final IndexShardService indexShardService;

    @Inject
    public IndexShardResourceImpl(final IndexShardService indexShardService) {
        this.indexShardService = indexShardService;
    }
}
