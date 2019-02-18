package stroom.data.store.impl.fs;

import stroom.data.store.impl.fs.db.jooq.tables.records.FileVolumeStateRecord;
import stroom.data.store.impl.fs.shared.FSVolumeState;
import stroom.db.util.GenericDao;
import stroom.entity.shared.HasIntCrud;

import javax.inject.Inject;
import java.util.Optional;

import static stroom.data.store.impl.fs.db.jooq.tables.FileVolumeState.FILE_VOLUME_STATE;

public class FileSystemVolumeStateDao implements HasIntCrud<FSVolumeState> {
    private GenericDao<FileVolumeStateRecord, FSVolumeState, Integer> dao;

    @Inject
    FileSystemVolumeStateDao(final ConnectionProvider connectionProvider) {
        dao = new GenericDao<>(FILE_VOLUME_STATE, FILE_VOLUME_STATE.ID, FSVolumeState.class, connectionProvider);
    }

    @Override
    public FSVolumeState create(final FSVolumeState volumeState) {
        return dao.create(volumeState);
    }

    @Override
    public FSVolumeState update(final FSVolumeState job) {
        return dao.update(job);
    }

    @Override
    public boolean delete(int id) {
        return dao.delete(id);
    }

    @Override
    public Optional<FSVolumeState> fetch(int id) {
        return dao.fetch(id);
    }
}
