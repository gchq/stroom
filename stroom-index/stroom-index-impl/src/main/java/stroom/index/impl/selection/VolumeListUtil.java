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

package stroom.index.impl.selection;

import stroom.index.shared.IndexVolume;

import java.util.ArrayList;
import java.util.List;

public class VolumeListUtil {
    public static List<IndexVolume> removeFullVolumes(final List<IndexVolume> list) {
        final List<IndexVolume> newList = new ArrayList<>(list.size());
        for (final IndexVolume volume : list) {
            if (!volume.isFull()) {
                newList.add(volume);
            }
        }
        return newList;
    }

    public static List<IndexVolume> removeVolumesWithoutValidState(final List<IndexVolume> list) {
        final List<IndexVolume> newList = new ArrayList<>(list.size());
        for (final IndexVolume volume : list) {
            if (volume.getBytesUsed() != null
                    && volume.getBytesFree() != null
                    && volume.getBytesTotal() != null) {
                newList.add(volume);
            }
        }
        return newList;
    }
}
