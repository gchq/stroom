package stroom.data.store.impl.fs;

import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.docref.SharedObject;
import stroom.util.shared.BaseResultList;

import java.util.List;

public interface DataVolumeDao {
    BaseResultList<DataVolume> find(FindDataVolumeCriteria criteria);

    DataVolume findDataVolume(long dataId);

    DataVolume createDataVolume(long dataId, FsVolume volume);

    int delete(List<Long> metaIdList);

    interface DataVolume extends SharedObject {
        long getStreamId();

        String getVolumePath();
    }
}
