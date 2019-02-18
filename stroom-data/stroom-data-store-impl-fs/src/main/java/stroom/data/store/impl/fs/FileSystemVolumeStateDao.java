package stroom.data.store.impl.fs;

import stroom.data.store.impl.fs.db.jooq.tables.records.FileVolumeStateRecord;
import stroom.data.store.impl.fs.shared.FSVolumeState;
import stroom.db.util.GenericDao;
import stroom.util.entity.BasicCrudDao;

import javax.inject.Inject;
import java.util.Optional;

import static stroom.data.store.impl.fs.db.jooq.tables.FileVolumeState.FILE_VOLUME_STATE;

public class FileSystemVolumeStateDao implements BasicCrudDao<FSVolumeState> {
//    private final ConnectionProvider connectionProvider;
    private GenericDao<FileVolumeStateRecord, FSVolumeState> dao;

    @Inject
    FileSystemVolumeStateDao(final ConnectionProvider connectionProvider) {
//        this.connectionProvider = connectionProvider;
        dao = new GenericDao(FILE_VOLUME_STATE, FILE_VOLUME_STATE.ID, FSVolumeState.class, connectionProvider);
    }

//    @Override
//    public FileSystemVolumeState create(final FileSystemVolumeState job) {
//        return dao.create(job);
//    }

    @Override
    public FSVolumeState create() {
//        throw new RuntimeException("Not implemented yet -- interface inappropriate for use with GenericDao");
        return dao.create(new FSVolumeState());
    }

    @Override
    public FSVolumeState update(final FSVolumeState job) {
        return dao.update(job);
    }

    @Override
    public int delete(int id) {
        return dao.delete(id);
    }

    @Override
    public Optional<FSVolumeState> fetch(int id) {
        return dao.fetch(id);
    }
}
