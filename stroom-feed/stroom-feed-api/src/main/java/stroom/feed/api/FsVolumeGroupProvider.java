package stroom.feed.api;

import stroom.docref.DocRef;

public interface FsVolumeGroupProvider {

    DocRef getVolumeGroupName(String feedName,
                              String streamType,
                              DocRef overrideVolumeGroup);
}
