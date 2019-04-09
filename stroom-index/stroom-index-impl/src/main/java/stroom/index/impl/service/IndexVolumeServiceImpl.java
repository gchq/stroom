package stroom.index.impl.service;

import stroom.index.impl.IndexVolumeDao;
import stroom.index.impl.IndexVolumeService;
import stroom.index.shared.IndexVolume;
import stroom.security.api.Security;
import stroom.security.shared.PermissionNames;

import javax.inject.Inject;
import java.util.List;

public class IndexVolumeServiceImpl implements IndexVolumeService {
    private final IndexVolumeDao indexVolumeDao;
    private final Security security;

    @Inject
    IndexVolumeServiceImpl(final IndexVolumeDao indexVolumeDao,
                           final Security security) {
        this.indexVolumeDao = indexVolumeDao;
        this.security = security;
    }

    @Override
    public IndexVolume create(final String nodeName,
                              final String path) {
        return security.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION,
                () -> indexVolumeDao.create(nodeName, path));
    }

    @Override
    public IndexVolume getById(final Long id) {
        return security.secureResult(() -> indexVolumeDao.getById(id));
    }

    @Override
    public List<IndexVolume> getAll() {
        return security.secureResult(indexVolumeDao::getAll);
    }

    @Override
    public List<IndexVolume> getVolumesInGroup(final String groupName) {
        return security.secureResult(() -> indexVolumeDao.getVolumesInGroup(groupName));
    }

    @Override
    public void addVolumeToGroup(final Long volumeId,
                                 final String name) {
        security.secure(PermissionNames.MANAGE_VOLUMES_PERMISSION,
                () -> indexVolumeDao.addVolumeToGroup(volumeId, name));
    }

    @Override
    public void removeVolumeFromGroup(final Long volumeId,
                                      final String name) {
        security.secure(PermissionNames.MANAGE_VOLUMES_PERMISSION,
                () -> indexVolumeDao.removeVolumeFromGroup(volumeId, name));
    }

    @Override
    public void delete(final Long id) {
        security.secure(PermissionNames.MANAGE_VOLUMES_PERMISSION,
                () -> indexVolumeDao.delete(id));
    }

    @Override
    public void clear() {

    }

    @Override
    public void flush() {

    }
}
