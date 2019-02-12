/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.data.store.impl.fs;

import stroom.data.store.impl.fs.shared.FileSystemVolume;
import stroom.data.store.impl.fs.shared.FindFileSystemVolumeCriteria;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.Clearable;
import stroom.entity.shared.Flushable;

/**
 * API for handling volumes.
 */
public interface FileSystemVolumeService extends Flushable, Clearable {
    FileSystemVolume load(FileSystemVolume entity);


    /**
     * Save the entity.
     *
     * @return The persisted entity.
     * @throws RuntimeException If a DB error occurred during deletion such as an optimistic
     *                          lock exception or entity constraint.
     */
    FileSystemVolume save(FileSystemVolume entity);

    /**
     * Delete an entity.
     *
     * @return True if the entity was deleted successfully.
     * @throws RuntimeException If a DB error occurred during deletion such as an optimistic
     *                          lock exception or entity constraint.
     */
    Boolean delete(FileSystemVolume entity);

    BaseResultList<FileSystemVolume> find(FindFileSystemVolumeCriteria criteria);

    /**
     * Given a node return back where we need to write to.
     *
     * @param node The local node required if we prefer to use local volumes.
     * @return set of volumes to write to
     */
    FileSystemVolume getVolume();
}
