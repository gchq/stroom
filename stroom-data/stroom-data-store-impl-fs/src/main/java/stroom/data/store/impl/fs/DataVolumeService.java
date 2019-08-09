package stroom.data.store.impl.fs;

import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.util.shared.BaseResultList;

import javax.inject.Inject;

public class DataVolumeService {
    private final DataVolumeDao dataVolumeDao;
    private final SecurityContext securityContext;

    @Inject
    DataVolumeService(final DataVolumeDao dataVolumeDao,
                      final SecurityContext securityContext) {
        this.dataVolumeDao = dataVolumeDao;
        this.securityContext = securityContext;
    }

    public BaseResultList<DataVolume> find(final FindDataVolumeCriteria criteria) {
        if (!criteria.isValidCriteria()) {
            throw new IllegalArgumentException("Not enough criteria to run");
        }

        return securityContext.secureResult(PermissionNames.DELETE_DATA_PERMISSION, () -> dataVolumeDao.find(criteria));
    }

    /**
     * Return the meta data volumes for a stream id.
     */
    public DataVolume findDataVolume(final long metaId) {
        return securityContext.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION, () -> dataVolumeDao.findDataVolume(metaId));
    }

    public DataVolume createDataVolume(final long dataId, final FsVolume volume) {
        return securityContext.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION, () -> dataVolumeDao.createDataVolume(dataId, volume));
    }
}
