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

import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FindFsVolumeCriteria;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.Clearable;
import stroom.util.shared.Flushable;

/**
 * API for handling volumes.
 */
public interface FsVolumeService extends Flushable, Clearable {
    FsVolume create(final FsVolume fileVolume);

    FsVolume update(FsVolume fileVolume);

    int delete(int id);

    FsVolume fetch(int id);

    BaseResultList<FsVolume> find(FindFsVolumeCriteria criteria);

    /**
     * Given a node return back where we need to write to.
     *
     * @param node The local node required if we prefer to use local volumes.
     * @return set of volumes to write to
     */
    FsVolume getVolume();
}
