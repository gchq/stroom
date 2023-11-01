package stroom.data.store.impl.fs;

import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.data.store.impl.fs.shared.FsVolumeGroupResource;
import stroom.entity.shared.ExpressionCriteria;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;
import javax.inject.Provider;

@AutoLogged
class FsVolumeGroupResourceImpl implements FsVolumeGroupResource {

    private final Provider<FsVolumeGroupService> volumeGroupServiceProvider;

    @Inject
    FsVolumeGroupResourceImpl(final Provider<FsVolumeGroupService> volumeGroupServiceProvider) {
        this.volumeGroupServiceProvider = volumeGroupServiceProvider;
    }

    @Override
    public ResultPage<FsVolumeGroup> find(final ExpressionCriteria request) {
        return ResultPage.createUnboundedList(volumeGroupServiceProvider.get().getAll());
    }

    @Override
    public FsVolumeGroup create(final String name) {
        return volumeGroupServiceProvider.get().getOrCreate(name);
    }

    @Override
    public FsVolumeGroup fetch(final Integer id) {
        return volumeGroupServiceProvider.get().get(id);
    }

    @Override
    public FsVolumeGroup fetchByName(final String name) {
        return volumeGroupServiceProvider.get().get(name);
    }

    @Override
    public FsVolumeGroup update(final Integer id, final FsVolumeGroup indexVolumeGroup) {
        return volumeGroupServiceProvider.get().update(indexVolumeGroup);
    }

    @Override
    public Boolean delete(final Integer id) {
        volumeGroupServiceProvider.get().delete(id);
        return true;
    }
}
