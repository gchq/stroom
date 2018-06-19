package stroom.data.volume.api;

import stroom.docref.SharedObject;
import stroom.entity.FindService;
import stroom.entity.SupportsCriteriaLogging;
import stroom.node.shared.VolumeEntity;
import stroom.node.shared.VolumeEntity.VolumeType;
import stroom.data.store.FindStreamVolumeCriteria;
import stroom.data.volume.api.StreamVolumeService.StreamVolume;

import java.util.Set;

public interface StreamVolumeService extends FindService<StreamVolume, FindStreamVolumeCriteria>, SupportsCriteriaLogging<FindStreamVolumeCriteria> {
    Set<StreamVolume> findStreamVolume(long streamId);

    Set<StreamVolume> createStreamVolumes(long streamId, Set<VolumeEntity> volumes);

    StreamVolume pickBestVolume(Set<StreamVolume> mdVolumeSet, long nodeId, long rackId);

    interface StreamVolume extends SharedObject {
        long getStreamId();

        String getVolumePath();

        VolumeType getVolumeType();

        int getNodeId();

        int getRackId();
    }



//    interface StreamAndVolumes extends SharedObject {
//        Stream getStream();
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
//        public Stream getStream() {
//            return stream;
//        }
//
//        public Set<Volume> getVolumes() {
//            return volumes;
//        }
//    }
}
