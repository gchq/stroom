package stroom.index;

import stroom.index.dao.IndexVolumeDao;
import stroom.index.shared.IndexVolume;

import javax.inject.Inject;
import java.util.List;

public class IndexVolumeServiceImpl implements IndexVolumeService {
    private final IndexVolumeDao indexVolumeDao;

    @Inject
    IndexVolumeServiceImpl(final IndexVolumeDao indexVolumeDao) {
        this.indexVolumeDao = indexVolumeDao;
    }

    @Override
    public List<IndexVolume> getVolumesInGroup(final String groupName) {
        return indexVolumeDao.getVolumesInGroup(groupName);
    }
}
