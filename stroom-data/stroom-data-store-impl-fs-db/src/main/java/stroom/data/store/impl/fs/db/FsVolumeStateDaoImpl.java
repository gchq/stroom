package stroom.data.store.impl.fs.db;

import stroom.data.store.impl.fs.FsVolumeStateDao;
import stroom.data.store.impl.fs.db.jooq.tables.records.FsVolumeStateRecord;
import stroom.data.store.impl.fs.shared.FsVolumeState;
import stroom.db.util.GenericDao;

import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.data.store.impl.fs.db.jooq.tables.FsVolumeState.FS_VOLUME_STATE;

@Singleton
public class FsVolumeStateDaoImpl implements FsVolumeStateDao {

    private final GenericDao<FsVolumeStateRecord, FsVolumeState, Integer> genericDao;

    @Inject
    FsVolumeStateDaoImpl(final FsDataStoreDbConnProvider fsDataStoreDbConnProvider) {
        genericDao = new GenericDao<>(
                fsDataStoreDbConnProvider,
                FS_VOLUME_STATE,
                FS_VOLUME_STATE.ID,
                FsVolumeState.class);
    }

    @Override
    public FsVolumeState create(final FsVolumeState volumeState) {
        return genericDao.create(volumeState);
    }

    @Override
    public FsVolumeState update(final FsVolumeState volumeState) {
        return genericDao.update(volumeState);
    }

    @Override
    public FsVolumeState updateWithoutOptimisticLocking(final FsVolumeState volumeState) {
        return genericDao.updateWithoutOptimisticLocking(volumeState);
    }

    @Override
    public boolean delete(int id) {
        return genericDao.delete(id);
    }

    @Override
    public Optional<FsVolumeState> fetch(int id) {
        return genericDao.fetch(id);
    }
}
