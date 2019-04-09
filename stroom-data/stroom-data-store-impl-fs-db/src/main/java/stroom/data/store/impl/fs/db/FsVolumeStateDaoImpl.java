package stroom.data.store.impl.fs.db;

import stroom.data.store.impl.fs.FsVolumeStateDao;
import stroom.data.store.impl.fs.db.jooq.tables.records.FsVolumeStateRecord;
import stroom.data.store.impl.fs.shared.FsVolumeState;
import stroom.db.util.GenericDao;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

import static stroom.data.store.impl.fs.db.jooq.tables.FsVolumeState.FS_VOLUME_STATE;

@Singleton
public class FsVolumeStateDaoImpl implements FsVolumeStateDao {
    private GenericDao<FsVolumeStateRecord, FsVolumeState, Integer> genericDao;

    @Inject
    FsVolumeStateDaoImpl(final ConnectionProvider connectionProvider) {
        genericDao = new GenericDao<>(FS_VOLUME_STATE, FS_VOLUME_STATE.ID, FsVolumeState.class, connectionProvider);
    }

    @Override
    public FsVolumeState create(final FsVolumeState volumeState) {
        return genericDao.create(volumeState);
    }

    @Override
    public FsVolumeState update(final FsVolumeState job) {
        return genericDao.update(job);
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
