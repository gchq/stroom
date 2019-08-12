package stroom.index.impl.service;

import stroom.index.impl.IndexVolumeDao;
import stroom.index.impl.IndexVolumeGroupDao;
import stroom.index.impl.IndexVolumeGroupService;
import stroom.index.shared.IndexVolumeGroup;
import stroom.security.api.Security;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.util.AuditUtil;
import stroom.util.NextNameGenerator;

import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

public class IndexVolumeGroupServiceImpl implements IndexVolumeGroupService {
    private final IndexVolumeGroupDao indexVolumeGroupDao;
    private IndexVolumeDao indexVolumeDao;
    private final Security security;
    private final SecurityContext securityContext;

    @Inject
    public IndexVolumeGroupServiceImpl(final IndexVolumeGroupDao indexVolumeGroupDao,
                                       final IndexVolumeDao indexVolumeDao,
                                       final Security security,
                                       final SecurityContext securityContext) {
        this.indexVolumeGroupDao = indexVolumeGroupDao;
        this.indexVolumeDao = indexVolumeDao;
        this.security = security;
        this.securityContext = securityContext;
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
    public IndexVolumeGroup create(String name) {
        final IndexVolumeGroup indexVolumeGroup = new IndexVolumeGroup();
        indexVolumeGroup.setName(name);
        AuditUtil.stamp(securityContext.getUserId(), indexVolumeGroup);
        return security.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION,
                () -> indexVolumeGroupDao.getOrCreate(indexVolumeGroup));
    }

    @Override
    public IndexVolumeGroup create() {
        final IndexVolumeGroup indexVolumeGroup = new IndexVolumeGroup();
        var newName = NextNameGenerator.getNextName(indexVolumeGroupDao.getNames(), "New group");
        indexVolumeGroup.setName(newName);
        AuditUtil.stamp(securityContext.getUserId(), indexVolumeGroup);
        return security.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION,
                () -> indexVolumeGroupDao.getOrCreate(indexVolumeGroup));
    }

    @Override
    public IndexVolumeGroup update(IndexVolumeGroup indexVolumeGroup) {
        AuditUtil.stamp(securityContext.getUserId(), indexVolumeGroup);
        return security.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION,
                () -> indexVolumeGroupDao.update(indexVolumeGroup));
    }

    @Override
    public IndexVolumeGroup get(final int id) {
        return security.secureResult(() -> indexVolumeGroupDao.get(id));
    }

    @Override
    public void delete(final int id) {
        security.secure(PermissionNames.MANAGE_VOLUMES_PERMISSION,
                () -> {
                    //TODO Transaction?
                    var indexVolumesInGroup = indexVolumeDao.getAll().stream().filter( indexVolume -> indexVolume.getIndexVolumeGroupId().equals(id)).collect(Collectors.toList());
                    indexVolumesInGroup.forEach(indexVolume -> indexVolumeDao.delete(indexVolume.getId()));
                    indexVolumeGroupDao.delete(id);
                });
    }


    static String getNextNameForNewGroup(List<String> names){
        return NextNameGenerator.getNextName(names, "New group");
    }
}
