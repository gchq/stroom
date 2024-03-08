package stroom.feed.api;

public interface VolumeGroupNameProvider {

    String getVolumeGroupName(String feedName, String streamType, String overrideVolumeGroup);
}
