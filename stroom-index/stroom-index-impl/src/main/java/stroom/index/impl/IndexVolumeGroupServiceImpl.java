package stroom.index.impl;

import stroom.index.shared.IndexVolumeGroup;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.util.AuditUtil;
import stroom.util.NextNameGenerator;

import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

public class IndexVolumeGroupServiceImpl implements IndexVolumeGroupService {
    private final IndexVolumeGroupDao indexVolumeGroupDao;
    private final IndexVolumeDao indexVolumeDao;
    private final SecurityContext securityContext;

    @Inject
    public IndexVolumeGroupServiceImpl(final IndexVolumeGroupDao indexVolumeGroupDao,
                                       final IndexVolumeDao indexVolumeDao,
                                       final SecurityContext securityContext) {
        this.indexVolumeGroupDao = indexVolumeGroupDao;
        this.indexVolumeDao = indexVolumeDao;
        this.securityContext = securityContext;
    }

    @Override
    public List<String> getNames() {
        return securityContext.secureResult(indexVolumeGroupDao::getNames);
    }

    @Override
    public List<IndexVolumeGroup> getAll() {
        return securityContext.secureResult(indexVolumeGroupDao::getAll);
    }

    @Override
    public IndexVolumeGroup getOrCreate(final String name) {
        final IndexVolumeGroup indexVolumeGroup = new IndexVolumeGroup();
        indexVolumeGroup.setName(name);
        AuditUtil.stamp(securityContext.getUserId(), indexVolumeGroup);
        return securityContext.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION,
                () -> indexVolumeGroupDao.getOrCreate(indexVolumeGroup));
    }

    @Override
    public IndexVolumeGroup create() {
        final IndexVolumeGroup indexVolumeGroup = new IndexVolumeGroup();
        var newName = NextNameGenerator.getNextName(indexVolumeGroupDao.getNames(), "New group");
        indexVolumeGroup.setName(newName);
        AuditUtil.stamp(securityContext.getUserId(), indexVolumeGroup);
        return securityContext.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION,
                () -> indexVolumeGroupDao.getOrCreate(indexVolumeGroup));
    }

    @Override
    public IndexVolumeGroup update(final IndexVolumeGroup indexVolumeGroup) {
        AuditUtil.stamp(securityContext.getUserId(), indexVolumeGroup);
        return securityContext.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION,
                () -> indexVolumeGroupDao.update(indexVolumeGroup));
    }

    @Override
    public IndexVolumeGroup get(final String name) {
        return securityContext.secureResult(() -> indexVolumeGroupDao.get(name));
    }

    @Override
    public IndexVolumeGroup get(final int id) {
        return securityContext.secureResult(() -> indexVolumeGroupDao.get(id));
    }

    @Override
    public void delete(int id) {
        securityContext.secure(PermissionNames.MANAGE_VOLUMES_PERMISSION,
                () -> {
                    //TODO Transaction?
                    var indexVolumesInGroup = indexVolumeDao.getAll().stream()
                            .filter(indexVolume ->
                                    indexVolume.getIndexVolumeGroupId().equals(id))
                            .collect(Collectors.toList());
                    indexVolumesInGroup.forEach(indexVolume -> indexVolumeDao.delete(indexVolume.getId()));
                    indexVolumeGroupDao.delete(id);
                });
    }
}
