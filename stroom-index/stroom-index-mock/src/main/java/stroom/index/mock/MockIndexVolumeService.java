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

import stroom.index.impl.IndexVolumeService;
import stroom.index.shared.CreateVolumeRequest;
import stroom.index.shared.IndexVolume;

import java.util.List;

public class MockIndexVolumeService implements IndexVolumeService {
    @Override
    public IndexVolume create(CreateVolumeRequest createVolumeRequest) {
        return null;
    }

    @Override
    public IndexVolume update(IndexVolume updateVolumeDTO) {
        return null;
    }

    @Override
    public IndexVolume getById(int id) {
        return null;
    }

    @Override
    public Boolean delete(int id) {
        return true;
    }

    @Override
    public List<IndexVolume> getAll() {
        return null;
    }

    @Override
    public void rescan() {
    }
}
