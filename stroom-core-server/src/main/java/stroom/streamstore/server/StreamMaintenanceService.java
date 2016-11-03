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

package stroom.streamstore.server;

import stroom.entity.shared.FindService;
import stroom.node.shared.Volume;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamVolume;

/**
 * Low level API to manage the stream store.
 */
public interface StreamMaintenanceService extends FindService<StreamVolume, FindStreamVolumeCriteria> {
    /**
     * Delete the files on a volume and then the StreamVolume record.
     *
     * @return count of delete
     */
    Long deleteStreamVolume(StreamVolume streamVolume);

    /**
     * Save a stream volume.
     *
     * @return updated object.
     */
    StreamVolume save(StreamVolume streamVolume);

    /**
     * Save a stream.
     *
     * @return updated object.
     */
    Stream save(Stream stream);

    /**
     * Used in tests. Find all files related to this stream.
     *
     * @param stream
     *            to search for
     * @return is of real IO files.
     */
    FileArrayList findAllStreamFile(Stream stream);

    /**
     * Scan a directory deleting old stuff and building an index of what is
     * there. Return back a list of sub dir's to nest into.
     */
    ScanVolumePathResult scanVolumePath(Volume volume, boolean doDelete, String path, long oldFileAge);

}
