package stroom.index.rest;

import stroom.index.service.IndexShardService;

import javax.inject.Inject;

public class IndexShardResourceImpl implements IndexShardResource {

    private final IndexShardService indexShardService;

    @Inject
    public IndexShardResourceImpl(final IndexShardService indexShardService) {
        this.indexShardService = indexShardService;
    }
}
