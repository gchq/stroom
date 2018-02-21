/*
 * Copyright 2016 Crown Copyright
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

package stroom.volume;

import stroom.node.shared.Volume;
import stroom.node.shared.VolumeState;

import java.util.List;

public class MostFreeVolumeSelector implements VolumeSelector {
    public static final String NAME = "MostFree";

    private final RoundRobinVolumeSelector roundRobinVolumeSelector = new RoundRobinVolumeSelector();

    @Override
    public Volume select(final List<Volume> list) {
        final List<Volume> filtered = VolumeListUtil.removeVolumesWithoutValidState(list);
        if (filtered.size() == 0) {
            return roundRobinVolumeSelector.select(list);
        }
        if (filtered.size() == 1) {
            return filtered.get(0);
        }

        double largestFree = 0;
        Volume selected = null;
        for (final Volume volume : filtered) {
            final VolumeState volumeState = volume.getVolumeState();
            final double free = volumeState.getBytesFree();
            if (free > largestFree) {
                largestFree = free;
                selected = volume;
            }
        }

        return selected;
    }

    @Override
    public String getName() {
        return NAME;
    }
}
