package stroom.index.impl.service;

import stroom.index.impl.IndexVolumeDao;
import stroom.index.impl.IndexVolumeService;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolumeGroup;
import stroom.security.api.Security;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.util.AuditUtil;

import javax.inject.Inject;
import java.util.List;

public class IndexVolumeServiceImpl implements IndexVolumeService {
    private final IndexVolumeDao indexVolumeDao;
    private final Security security;
    private final SecurityContext securityContext;

    @Inject
    IndexVolumeServiceImpl(final IndexVolumeDao indexVolumeDao,
                           final Security security,
                           final SecurityContext securityContext) {
        this.indexVolumeDao = indexVolumeDao;
        this.security = security;
        this.securityContext = securityContext;
    }

    @Override
    public IndexVolume create(final String nodeName,
                              final String path) {
        final IndexVolume indexVolume = new IndexVolume();
        AuditUtil.stamp(securityContext.getUserId(), indexVolume);
        indexVolume.setNodeName(nodeName);
        indexVolume.setPath(path);

        return security.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION,
                () -> indexVolumeDao.create(indexVolume));
    }

    @Override
    public IndexVolume getById(final int id) {
        return security.secureResult(() -> indexVolumeDao.fetch(id).orElse(null));
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
    public List<IndexVolumeGroup> getGroupsForVolume(final int id) {
        return security.secureResult(() -> indexVolumeDao.getGroupsForVolume(id));
    }

    @Override
    public void addVolumeToGroup(final int volumeId,
                                 final String name) {
        security.secure(PermissionNames.MANAGE_VOLUMES_PERMISSION,
                () -> indexVolumeDao.addVolumeToGroup(volumeId, name));
    }

    @Override
    public void removeVolumeFromGroup(final int volumeId,
                                      final String name) {
        security.secure(PermissionNames.MANAGE_VOLUMES_PERMISSION,
                () -> indexVolumeDao.removeVolumeFromGroup(volumeId, name));
    }

    @Override
    public void delete(final int id) {
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
