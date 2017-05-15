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

package stroom.streamstore.server.fs;

import stroom.node.shared.Node;
import stroom.node.shared.Volume.VolumeType;
import stroom.streamstore.shared.StreamVolume;
import stroom.util.concurrent.AtomicSequence;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

public final class StreamVolumeUtil {
    private StreamVolumeUtil() {
        // NA
    }

    /**
     * Create a positive sequence of numbers
     */
    private static AtomicSequence sequence = new AtomicSequence();

    /**
     * Given a set of volumes pick one that's nearest to us and readable,
     * otherwise a random one.
     */
    public static StreamVolume pickBestVolume(final Set<StreamVolume> mdVolumeSet, final Node node) {
        // Try and locate a volume on the same node that is private
        for (final StreamVolume mdVolume : mdVolumeSet) {
            if (mdVolume.getVolume().getVolumeType().equals(VolumeType.PRIVATE)
                    && mdVolume.getVolume().getNode().equals(node)) {
                return mdVolume;
            }
        }

        // Otherwise have a go on one in the same rack that is public
        for (final StreamVolume mdVolume : mdVolumeSet) {
            if (mdVolume.getVolume().getVolumeType().equals(VolumeType.PUBLIC)
                    && mdVolume.getVolume().getNode().getRack().equals(node.getRack())) {
                return mdVolume;
            }
        }

        final Set<StreamVolume> publicVolumes = new HashSet<>();
        publicVolumes.addAll(
                mdVolumeSet.stream().filter(volume -> volume.getVolume().getVolumeType().equals(VolumeType.PUBLIC))
                        .collect(Collectors.toList()));

        if (publicVolumes.size() == 0) {
            return null;
        }

        // Otherwise pick a random one
        final Iterator<StreamVolume> iter = publicVolumes.iterator();
        final int pickIndex = pickIndex(publicVolumes.size());
        for (int i = 0; i < pickIndex; i++) {
            iter.next();
        }

        final StreamVolume randomVolume = iter.next();

        return randomVolume;
    }

    /**
     * Pick a number and try and round robin on the number that is chosen.
     */
    private static int pickIndex(final int size) {
        return sequence.next(size);
    }

}
