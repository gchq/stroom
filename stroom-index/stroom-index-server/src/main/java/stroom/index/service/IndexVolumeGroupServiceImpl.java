package stroom.index.service;

import stroom.index.dao.IndexVolumeGroupDao;
import stroom.index.shared.IndexVolumeGroup;

import javax.inject.Inject;
import java.util.List;

public class IndexVolumeGroupServiceImpl implements IndexVolumeGroupService {

    private final IndexVolumeGroupDao indexVolumeGroupDao;

    @Inject
    public IndexVolumeGroupServiceImpl(final IndexVolumeGroupDao indexVolumeGroupDao) {
        this.indexVolumeGroupDao = indexVolumeGroupDao;
    }

    @Override
    public List<String> getNames() {
        return indexVolumeGroupDao.getNames();
    }

    @Override
    public List<IndexVolumeGroup> getAll() {
        return indexVolumeGroupDao.getAll();
    }

    @Override
    public IndexVolumeGroup create(final String name) {
        return indexVolumeGroupDao.create(name);
    }

    @Override
    public IndexVolumeGroup get(final String name) {
        return indexVolumeGroupDao.get(name);
    }

    @Override
    public void delete(final String name) {
        indexVolumeGroupDao.delete(name);
    }
}
