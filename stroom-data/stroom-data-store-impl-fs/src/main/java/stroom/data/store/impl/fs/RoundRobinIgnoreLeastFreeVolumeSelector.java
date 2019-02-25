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

package stroom.data.store.impl.fs;

import stroom.data.store.impl.fs.shared.FSVolume;
import stroom.data.store.impl.fs.shared.FSVolumeState;

import java.util.List;

public class RoundRobinIgnoreLeastFreeVolumeSelector implements FileVolumeSelector {
    public static final String NAME = "RoundRobinIgnoreLeastFree";

    private final RoundRobinVolumeSelector roundRobinVolumeSelector = new RoundRobinVolumeSelector();

    @Override
    public FSVolume select(final List<FSVolume> list) {
        final List<FSVolume> filtered = FileVolumeListUtil.removeVolumesWithoutValidState(list);
        if (filtered.size() == 0) {
            return roundRobinVolumeSelector.select(list);
        }
        if (filtered.size() == 1) {
            return filtered.get(0);
        }

        // Remove the volume with the least amount of free space.
        double smalledFree = Double.MAX_VALUE;
        FSVolume smallest = null;
        for (final FSVolume volume : filtered) {
            final FSVolumeState volumeState = volume.getVolumeState();
            final double free = volumeState.getBytesFree();
            if (free < smalledFree) {
                smalledFree = free;
                smallest = volume;
            }
        }
        filtered.remove(smallest);

        return roundRobinVolumeSelector.select(filtered);
    }

    @Override
    public String getName() {
        return NAME;
    }
}
