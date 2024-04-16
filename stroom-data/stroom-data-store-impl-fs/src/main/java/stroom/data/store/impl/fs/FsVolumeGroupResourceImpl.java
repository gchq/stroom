package stroom.data.store.impl.fs;

import stroom.data.store.api.FsVolumeGroupService;
import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.data.store.impl.fs.shared.FsVolumeGroupResource;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

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
    public FsVolumeGroup create(final FsVolumeGroup volumeGroup) {
        return volumeGroupServiceProvider.get().create(volumeGroup);
    }

    @Override
    public FsVolumeGroup fetch(final Integer id) {
        return volumeGroupServiceProvider.get().get(id);
    }

    public FsVolumeGroup fetchByDocRef(final DocRef docRef) {
        return volumeGroupServiceProvider.get().get(docRef);
    }

    @Override
    public FsVolumeGroup fetchByName(final String name) {
        return volumeGroupServiceProvider.get().get(name);
    }

    @Override
    public FsVolumeGroup update(final Integer id, final FsVolumeGroup volumeGroup) {
        return volumeGroupServiceProvider.get().update(volumeGroup);
    }

    @Override
    public Boolean delete(final Integer id) {
        volumeGroupServiceProvider.get().delete(id);
        return true;
    }
}
