package stroom.data.store.impl.fs;

import stroom.data.store.impl.fs.db.jooq.tables.records.FsVolumeStateRecord;
import stroom.data.store.impl.fs.shared.FsVolumeState;
import stroom.db.util.GenericDao;
import stroom.util.shared.HasIntCrud;

import javax.inject.Inject;
import java.util.Optional;

import static stroom.data.store.impl.fs.db.jooq.tables.FsVolumeState.FS_VOLUME_STATE;

public class FsVolumeStateDao implements HasIntCrud<FsVolumeState> {
    private GenericDao<FsVolumeStateRecord, FsVolumeState, Integer> dao;

    @Inject
    FsVolumeStateDao(final ConnectionProvider connectionProvider) {
        dao = new GenericDao<>(FS_VOLUME_STATE, FS_VOLUME_STATE.ID, FsVolumeState.class, connectionProvider);
    }

    @Override
    public FsVolumeState create(final FsVolumeState volumeState) {
        return dao.create(volumeState);
    }

    @Override
    public FsVolumeState update(final FsVolumeState job) {
        return dao.update(job);
    }

    @Override
    public boolean delete(int id) {
        return dao.delete(id);
    }

    @Override
    public Optional<FsVolumeState> fetch(int id) {
        return dao.fetch(id);
    }
}
