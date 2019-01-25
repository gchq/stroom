package stroom.index;

import stroom.docref.DocRef;
import stroom.index.dao.IndexVolumeDao;
import stroom.index.shared.IndexVolume;

import javax.inject.Inject;
import java.util.Set;

public class IndexVolumeServiceImpl implements IndexVolumeService {
    private final IndexVolumeDao indexVolumeDao;

    @Inject
    IndexVolumeServiceImpl(final IndexVolumeDao indexVolumeDao) {
        this.indexVolumeDao = indexVolumeDao;
    }

    @SuppressWarnings("unchecked")
    public Set<IndexVolume> getVolumesForIndex(final DocRef indexRef) {
        return indexVolumeDao.getVolumesForIndex(indexRef.getUuid());
    }
}
