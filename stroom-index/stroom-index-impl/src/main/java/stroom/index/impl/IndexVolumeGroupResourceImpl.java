package stroom.index.impl;

import stroom.entity.shared.ExpressionCriteria;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.index.shared.IndexVolumeGroup;
import stroom.index.shared.IndexVolumeGroupResource;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;
import javax.inject.Provider;

@AutoLogged
class IndexVolumeGroupResourceImpl implements IndexVolumeGroupResource {

    private final Provider<IndexVolumeGroupService> indexVolumeGroupServiceProvider;

    @Inject
    IndexVolumeGroupResourceImpl(final Provider<IndexVolumeGroupService> indexVolumeGroupServiceProvider) {
        this.indexVolumeGroupServiceProvider = indexVolumeGroupServiceProvider;
    }

    @Override
    public ResultPage<IndexVolumeGroup> find(final ExpressionCriteria request) {
        return ResultPage.createUnboundedList(indexVolumeGroupServiceProvider.get().getAll());
    }

    @Override
    public IndexVolumeGroup create(final String name) {
        return indexVolumeGroupServiceProvider.get().getOrCreate(name);
    }

    @Override
    public IndexVolumeGroup fetch(final Integer id) {
        return indexVolumeGroupServiceProvider.get().get(id);
    }

    @Override
    public IndexVolumeGroup fetchByName(final String name) {
        return indexVolumeGroupServiceProvider.get().get(name);
    }

    @Override
    public IndexVolumeGroup update(final Integer id, final IndexVolumeGroup indexVolumeGroup) {
        return indexVolumeGroupServiceProvider.get().update(indexVolumeGroup);
    }

    @Override
    public Boolean delete(final Integer id) {
        indexVolumeGroupServiceProvider.get().delete(id);
        return true;
    }
}
