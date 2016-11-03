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

package stroom.volume.server;

import java.util.List;

import stroom.node.shared.Volume;
import stroom.node.shared.VolumeState;

public class WeightedFreePercentRandomVolumeSelector implements VolumeSelector {
    public static final String NAME = "WeightedFreePercentRandom";

    private final RandomVolumeSelector randomVolumeSelector = new RandomVolumeSelector();

    @Override
    public Volume select(final List<Volume> list) {
        final List<Volume> filtered = VolumeListUtil.removeVolumesWithoutValidState(list);
        if (filtered.size() == 0) {
            return randomVolumeSelector.select(list);
        }
        if (filtered.size() == 1) {
            return filtered.get(0);
        }

        final double[] thresholds = getWeightingThresholds(filtered);
        final double random = Math.random();

        int index = thresholds.length - 1;
        for (int i = 0; i < thresholds.length; i++) {
            if (thresholds[i] >= random) {
                index = i;
                break;
            }
        }

        return filtered.get(index);
    }

    private double[] getWeightingThresholds(final List<Volume> list) {
        double totalFractionFree = 0;
        for (final Volume volume : list) {
            final VolumeState volumeState = volume.getVolumeState();
            final double total = volumeState.getBytesTotal();
            final double free = volumeState.getBytesFree();
            final double fractionFree = free / total;

            totalFractionFree += fractionFree;
        }

        final double increment = 1D / totalFractionFree;
        final double[] thresholds = new double[list.size()];
        int i = 0;
        for (final Volume volume : list) {
            final VolumeState volumeState = volume.getVolumeState();
            final double total = volumeState.getBytesTotal();
            final double free = volumeState.getBytesFree();
            final double fractionFree = free / total;

            thresholds[i] = increment * fractionFree;
            if (i > 0) {
                thresholds[i] += thresholds[i - 1];
            }

            i++;
        }

        return thresholds;
    }

    @Override
    public String getName() {
        return NAME;
    }
}
