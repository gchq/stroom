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

import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolumeState;

import java.util.List;

public class MostFreePercentVolumeSelector implements FsVolumeSelector {
    public static final String NAME = "MostFreePercent";

    private final RoundRobinVolumeSelector roundRobinVolumeSelector = new RoundRobinVolumeSelector();

    @Override
    public FsVolume select(final List<FsVolume> list) {
        final List<FsVolume> filtered = FsVolumeListUtil.removeVolumesWithoutValidState(list);
        if (filtered.size() == 0) {
            return roundRobinVolumeSelector.select(list);
        }
        if (filtered.size() == 1) {
            return filtered.get(0);
        }

        double largestFractionFree = 0;
        FsVolume selected = null;
        for (final FsVolume volume : filtered) {
            final FsVolumeState volumeState = volume.getVolumeState();
            final double total = volumeState.getBytesTotal();
            final double free = volumeState.getBytesFree();
            final double fractionFree = free / total;
            if (fractionFree > largestFractionFree) {
                largestFractionFree = fractionFree;
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
