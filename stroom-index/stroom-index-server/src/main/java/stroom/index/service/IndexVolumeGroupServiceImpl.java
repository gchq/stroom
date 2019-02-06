package stroom.index.service;

import stroom.index.dao.IndexVolumeGroupDao;
import stroom.index.shared.IndexVolumeGroup;
import stroom.security.Security;
import stroom.security.shared.PermissionNames;

import javax.inject.Inject;
import java.util.List;

public class IndexVolumeGroupServiceImpl implements IndexVolumeGroupService {

    private final IndexVolumeGroupDao indexVolumeGroupDao;
    private final Security security;

    @Inject
    public IndexVolumeGroupServiceImpl(final IndexVolumeGroupDao indexVolumeGroupDao,
                                       final Security security) {
        this.indexVolumeGroupDao = indexVolumeGroupDao;
        this.security = security;
    }

    @Override
    public List<String> getNames() {
        return security.secureResult(indexVolumeGroupDao::getNames);
    }

    @Override
    public List<IndexVolumeGroup> getAll() {
        return security.secureResult(indexVolumeGroupDao::getAll);
    }

    @Override
    public IndexVolumeGroup create(final String name) {
        return security.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION,
                () -> indexVolumeGroupDao.create(name));
    }

    @Override
    public IndexVolumeGroup get(final String name) {
        return security.secureResult(() -> indexVolumeGroupDao.get(name));
    }

    @Override
    public void delete(final String name) {
        security.secure(PermissionNames.MANAGE_VOLUMES_PERMISSION,
                () -> indexVolumeGroupDao.delete(name));
    }
}
