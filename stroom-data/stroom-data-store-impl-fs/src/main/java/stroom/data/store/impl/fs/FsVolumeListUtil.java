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

package stroom.data.store.impl.fs;

import stroom.data.store.impl.fs.shared.FsVolume;

import java.util.ArrayList;
import java.util.List;

public class FsVolumeListUtil {

    public static List<FsVolume> removeFullVolumes(final List<FsVolume> list) {
        final List<FsVolume> newList = new ArrayList<>(list.size());
        for (final FsVolume volume : list) {
            if (!volume.getCapacityInfo().isFull()) {
                newList.add(volume);
            }
        }
        return newList;
    }

//    public static List<FileVolume> removeMatchingRack(final List<FileVolume> list, final Rack rack) {
//        final List<FileVolume> newList = new ArrayList<>(list.size());
//        for (final FileVolume volume : list) {
//            if (!volume.getNode().getRack().equals(rack)) {
//                newList.add(volume);
//            }
//        }
//        return newList;
//    }

    public static List<FsVolume> removeVolumesWithoutValidState(final List<FsVolume> list) {
        final List<FsVolume> newList = new ArrayList<>(list.size());
        for (final FsVolume volume : list) {
            if (volume.getVolumeState() != null && volume.getVolumeState().getBytesUsed() != null
                    && volume.getVolumeState().getBytesFree() != null
                    && volume.getVolumeState().getBytesTotal() != null) {
                newList.add(volume);
            }
        }
        return newList;
    }
}
