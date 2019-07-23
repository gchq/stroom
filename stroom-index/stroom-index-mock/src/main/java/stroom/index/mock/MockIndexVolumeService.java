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

package stroom.index.mock;

import stroom.index.impl.CreateVolumeDTO;
import stroom.index.impl.IndexVolumeService;
import stroom.index.impl.UpdateVolumeDTO;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolumeGroup;

import java.util.Collections;
import java.util.List;

public class MockIndexVolumeService implements IndexVolumeService {

    @Override
    public IndexVolume create(CreateVolumeDTO createVolumeDTO) {
        return null;
    }

    @Override
    public IndexVolume update(UpdateVolumeDTO updateVolumeDTO) {
        return null;
    }

    @Override
    public IndexVolume getById(int id) {
        return null;
    }

    @Override
    public void delete(int id) {

    }

    @Override
    public List<IndexVolume> getAll() {
        return null;
    }

//    @Override
//    public void addVolumeToGroup(int volumeId, String name) {
//
//    }
//
//    @Override
//    public void removeVolumeFromGroup(int volumeId, String name) {
//
//    }
//
//    @Override
//    public List<IndexVolume> getVolumesInGroup(String groupName) {
//        return Collections.emptyList();
//    }
//
//    @Override
//    public List<IndexVolumeGroup> getGroupsForVolume(int id) {
//        return Collections.emptyList();
//    }

    @Override
    public void clear() {

    }

    @Override
    public void flush() {

    }
}
