/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.planb.shared;

import java.util.Optional;

/**
 * Implemented by the settings class of a store type whose writes pass through a holding shard on the
 * way to the time buckets queries read.
 *
 * <p>Kept separate from {@link HasSharedFileStore} because the two are independent: a store type can
 * live on a shared file store and still merge straight into its buckets, in which case it has no
 * holding shard and neither of these settings applies to it.
 */
public interface HasHoldingAreaSettings {

    /**
     * The holding shard's settings — the grace an incomplete record is given for the rest of its
     * data to arrive, and how often the shard is compacted. Never null.
     */
    HoldingAreaSettings getHoldingArea();

    /**
     * The holding area settings of any settings object, empty for a store type that has no holding
     * shard.
     */
    static Optional<HoldingAreaSettings> holdingAreaSettings(final AbstractPlanBSettings settings) {
        return settings instanceof final HasHoldingAreaSettings s
                ? Optional.of(s.getHoldingArea())
                : Optional.empty();
    }
}
