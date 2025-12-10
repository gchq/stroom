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

package stroom.data.store.api;

import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.docref.DocRef;

import java.util.List;
import java.util.Optional;

public interface FsVolumeGroupService {

    String ENTITY_TYPE = "FS_VOLUME_GROUP";
    DocRef EVENT_DOCREF = new DocRef(ENTITY_TYPE, ENTITY_TYPE, ENTITY_TYPE);

    List<String> getNames();

    List<FsVolumeGroup> getAll();

    FsVolumeGroup create();

    FsVolumeGroup getOrCreate(String name);

    FsVolumeGroup update(FsVolumeGroup indexVolumeGroup);

    FsVolumeGroup get(String name);

    FsVolumeGroup get(int id);

    void delete(int id);

    void ensureDefaultVolumes();

    Optional<String> getDefaultVolumeGroup();
}
