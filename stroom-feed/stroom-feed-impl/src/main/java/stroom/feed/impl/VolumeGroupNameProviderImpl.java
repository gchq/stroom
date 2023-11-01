package stroom.feed.impl;

import stroom.feed.api.VolumeGroupNameProvider;
import stroom.feed.shared.FeedDoc;

import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class VolumeGroupNameProviderImpl implements VolumeGroupNameProvider {

    private final FeedDocCache feedDocCache;

    @Inject
    public VolumeGroupNameProviderImpl(final FeedDocCache feedDocCache) {
        this.feedDocCache = feedDocCache;
    }

    @Override
    public String getVolumeGroupName(final String feedName,
                                     final String streamType,
                                     final String overrideVolumeGroup) {
        if (overrideVolumeGroup != null && !overrideVolumeGroup.isBlank()) {
            return overrideVolumeGroup;
        }

        final Optional<FeedDoc> optional = feedDocCache.get(feedName);
        if (optional.isPresent()) {
            final String volumeGroup = optional.get().getVolumeGroup();
            if (volumeGroup != null && !volumeGroup.isBlank()) {
                return volumeGroup;
            }
        }

        return null;
    }
}
