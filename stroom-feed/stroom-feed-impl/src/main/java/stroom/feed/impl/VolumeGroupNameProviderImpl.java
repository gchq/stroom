/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.feed.impl;

import stroom.feed.api.VolumeGroupNameProvider;
import stroom.feed.shared.FeedDoc;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Optional;

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
