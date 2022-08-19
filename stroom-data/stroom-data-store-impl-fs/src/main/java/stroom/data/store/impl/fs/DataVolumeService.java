package stroom.data.store.impl.fs;

import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.security.api.SecurityContext;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;

public class DataVolumeService {

    private final DataVolumeDao dataVolumeDao;
    private final SecurityContext securityContext;
    private final FsOrphanedMetaDao fsOrphanedMetaDao;

    @Inject
    DataVolumeService(final DataVolumeDao dataVolumeDao,
                      final SecurityContext securityContext,
                      final FsOrphanedMetaDao fsOrphanedMetaDao) {
        this.dataVolumeDao = dataVolumeDao;
        this.securityContext = securityContext;
        this.fsOrphanedMetaDao = fsOrphanedMetaDao;
    }

    public ResultPage<DataVolume> find(final FindDataVolumeCriteria criteria) {
        if (!criteria.isValidCriteria()) {
            throw new IllegalArgumentException("Not enough criteria to run");
        }

        return securityContext.secureResult(() ->
                dataVolumeDao.find(criteria));
    }

    /**
     * Return the meta data volumes for a stream id.
     */
    public DataVolume findDataVolume(final long metaId) {
        return securityContext.secureResult(() ->
                dataVolumeDao.findDataVolume(metaId));
    }

    public DataVolume createDataVolume(final long metaId, final FsVolume volume) {
        return securityContext.secureResult(() ->
                dataVolumeDao.createDataVolume(metaId, volume));
    }

    public long getLastMinMetaId() {
        return securityContext.secureResult(fsOrphanedMetaDao::getLastMinMetaId);
    }

    void updateLastMinMetaId(final long lastMinMetaId) {
        securityContext.secure(() ->
                fsOrphanedMetaDao.updateLastMinMetaId(lastMinMetaId));
    }
}
