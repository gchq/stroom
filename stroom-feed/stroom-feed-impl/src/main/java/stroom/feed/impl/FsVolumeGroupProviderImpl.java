package stroom.feed.impl;

import stroom.data.store.api.FsVolumeGroupService;
import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.docref.DocRef;
import stroom.feed.api.FsVolumeGroupProvider;
import stroom.feed.shared.FeedDoc;
import stroom.util.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class FsVolumeGroupProviderImpl implements FsVolumeGroupProvider {

    private final FeedDocCache feedDocCache;
    private final FsVolumeGroupService fsVolumeGroupService;

    @Inject
    public FsVolumeGroupProviderImpl(final FeedDocCache feedDocCache,
                                     final FsVolumeGroupService fsVolumeGroupService) {
        this.feedDocCache = feedDocCache;
        this.fsVolumeGroupService = fsVolumeGroupService;
    }

    @Override
    public DocRef getVolumeGroupName(final String feedName,
                                     final String streamType,
                                     final DocRef overrideVolumeGroup) {
        if (overrideVolumeGroup != null) {
            return overrideVolumeGroup;
        } else {
            // Get the vol grp off the feed or fall back to the default vol grp
            return feedDocCache.get(feedName)
                    .map(FeedDoc::getVolumeGroupDocRef)
                    .orElseGet(() ->
                            NullSafe.get(
                                    fsVolumeGroupService.getDefaultVolumeGroup(),
                                    FsVolumeGroup::asDocRef));
        }
    }
}
