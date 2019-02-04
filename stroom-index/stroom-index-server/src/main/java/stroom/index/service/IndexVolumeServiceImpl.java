package stroom.index.service;

import stroom.index.dao.IndexVolumeDao;
import stroom.index.service.IndexVolumeService;
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
    public void addVolumeToGroup(final Long volumeId,
                                 final String name) {
        indexVolumeDao.addVolumeToGroup(volumeId, name);
    }

    @Override
    public void removeVolumeFromGroup(final Long volumeId,
                                      final String name) {
        indexVolumeDao.removeVolumeFromGroup(volumeId, name);
    }

    @Override
    public List<IndexVolume> getVolumesInGroup(final String groupName) {
        return indexVolumeDao.getVolumesInGroup(groupName);
    }
}
