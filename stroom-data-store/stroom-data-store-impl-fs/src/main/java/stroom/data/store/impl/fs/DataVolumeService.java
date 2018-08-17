package stroom.data.store.impl.fs;

import stroom.docref.SharedObject;
import stroom.entity.shared.BaseResultList;
import stroom.node.shared.VolumeEntity;

import java.util.Set;

public interface DataVolumeService {
    BaseResultList<DataVolume> find(FindDataVolumeCriteria criteria);

    Set<DataVolume> findStreamVolume(long dataId);

    Set<DataVolume> createStreamVolumes(long dataId, Set<VolumeEntity> volumes);

    DataVolume pickBestVolume(Set<DataVolume> mdVolumeSet, long nodeId, long rackId);

    interface DataVolume extends SharedObject {
        long getStreamId();

        String getVolumePath();

//        VolumeType getVolumeType();
//
//        int getNodeId();
//
//        int getRackId();
    }


//    interface StreamAndVolumes extends SharedObject {
//        Stream getData();
//
//        Set<Volume> getVolumes();
//    }
//
//    class StreamAndVolumesImpl implements StreamAndVolumes {
//        private final Stream stream;
//        private final Set<Volume> volumes;
//
//        StreamAndVolumesImpl(final Stream stream,
//                             final Set<Volume> volumes) {
//            this.stream = stream;
//            this.volumes = volumes;
//        }
//
//        public Stream getData() {
//            return stream;
//        }
//
//        public Set<Volume> getVolumes() {
//            return volumes;
//        }
//    }
}
