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

package stroom.index;

import stroom.index.service.IndexVolumeService;
import stroom.index.shared.IndexVolume;

import java.util.Collections;
import java.util.List;

public class MockIndexVolumeService implements IndexVolumeService {
    @Override
    public IndexVolume create(String nodeName, String path) {
        return null;
    }

    @Override
    public IndexVolume getById(Long id) {
        return null;
    }

    @Override
    public void delete(Long id) {

    }

    @Override
    public List<IndexVolume> getAll() {
        return null;
    }

    @Override
    public void addVolumeToGroup(Long volumeId, String name) {

    }

    @Override
    public void removeVolumeFromGroup(Long volumeId, String name) {

    }

    @Override
    public List<IndexVolume> getVolumesInGroup(String groupName) {
        return Collections.emptyList();
    }

    @Override
    public void clear() {

    }

    @Override
    public void flush() {

    }
}
