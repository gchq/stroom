package stroom.data.store.impl.fs;

import stroom.docref.SharedObject;
import stroom.entity.FindService;
import stroom.entity.SupportsCriteriaLogging;
import stroom.node.shared.VolumeEntity;
import stroom.node.shared.VolumeEntity.VolumeType;
import stroom.data.store.FindStreamVolumeCriteria;
import stroom.data.store.impl.fs.StreamVolumeService.StreamVolume;

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

    class StreamVolumeImpl implements StreamVolume {
        private final long streamId;
        private final String volumePath;
        private final VolumeType volumeType;
        private final int nodeId;
        private final int rackId;

        StreamVolumeImpl(final long streamId,
                         final String volumePath,
                         final VolumeType volumeType,
                         final int nodeId,
                         final int rackId) {
            this.streamId = streamId;
            this.volumePath = volumePath;
            this.volumeType = volumeType;
            this.nodeId = nodeId;
            this.rackId = rackId;
        }

        @Override
        public long getStreamId() {
            return streamId;
        }

        @Override
        public String getVolumePath() {
            return volumePath;
        }

        @Override
        public VolumeType getVolumeType() {
            return volumeType;
        }

        @Override
        public int getNodeId() {
            return nodeId;
        }

        @Override
        public int getRackId() {
            return rackId;
        }
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
