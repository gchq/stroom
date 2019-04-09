package stroom.data.store.impl.fs;

import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.security.api.Security;
import stroom.security.shared.PermissionNames;
import stroom.util.shared.BaseResultList;

import javax.inject.Inject;

public class DataVolumeService {
    private final DataVolumeDao dataVolumeDao;
    private final Security security;

    @Inject
    DataVolumeService(final DataVolumeDao dataVolumeDao,
                      final Security security) {
        this.dataVolumeDao = dataVolumeDao;
        this.security = security;
    }

    public BaseResultList<DataVolume> find(final FindDataVolumeCriteria criteria) {
        if (!criteria.isValidCriteria()) {
            throw new IllegalArgumentException("Not enough criteria to run");
        }

        return security.secureResult(PermissionNames.DELETE_DATA_PERMISSION, () -> dataVolumeDao.find(criteria));
    }

    /**
     * Return the meta data volumes for a stream id.
     */
    public DataVolume findDataVolume(final long metaId) {
        return security.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION, () -> dataVolumeDao.findDataVolume(metaId));
    }

    public DataVolume createDataVolume(final long dataId, final FsVolume volume) {
        return security.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION, () -> dataVolumeDao.createDataVolume(dataId, volume));
    }
}
