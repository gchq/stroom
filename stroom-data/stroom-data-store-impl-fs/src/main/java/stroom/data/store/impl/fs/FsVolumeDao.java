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

import stroom.data.store.impl.fs.shared.FindFsVolumeCriteria;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.util.shared.ResultPage;

import java.util.List;
import java.util.Set;

public interface FsVolumeDao {

    FsVolume create(FsVolume fileVolume);

    FsVolume fetch(int id);

    FsVolume update(FsVolume fileVolume);

    int delete(int id);

    ResultPage<FsVolume> find(FindFsVolumeCriteria criteria);

    Set<FsVolume> get(final String path);

    List<FsVolume> getAll();

    List<FsVolume> getVolumesInGroup(String groupName);

    List<FsVolume> getVolumesInGroup(final int groupid);
}
