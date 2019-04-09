package stroom.index.impl.service;

import stroom.index.impl.IndexVolumeGroupDao;
import stroom.index.impl.IndexVolumeGroupService;
import stroom.index.shared.IndexVolumeGroup;
import stroom.security.api.Security;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.util.AuditUtil;

import javax.inject.Inject;
import java.util.List;

public class IndexVolumeGroupServiceImpl implements IndexVolumeGroupService {
    private final IndexVolumeGroupDao indexVolumeGroupDao;
    private final Security security;
    private final SecurityContext securityContext;

    @Inject
    public IndexVolumeGroupServiceImpl(final IndexVolumeGroupDao indexVolumeGroupDao,
                                       final Security security,
                                       final SecurityContext securityContext) {
        this.indexVolumeGroupDao = indexVolumeGroupDao;
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
    public IndexVolumeGroup create(final String name) {
        final IndexVolumeGroup indexVolumeGroup = new IndexVolumeGroup();
        AuditUtil.stamp(securityContext.getUserId(), indexVolumeGroup);
        return security.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION,
                () -> indexVolumeGroupDao.create(indexVolumeGroup));
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
