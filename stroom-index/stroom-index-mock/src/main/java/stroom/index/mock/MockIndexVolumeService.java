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

package stroom.index.mock;

import stroom.entity.shared.ExpressionCriteria;
import stroom.index.impl.IndexVolumeService;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.ValidationResult;
import stroom.util.shared.ResultPage;

public class MockIndexVolumeService implements IndexVolumeService {

    @Override
    public ResultPage<IndexVolume> find(final ExpressionCriteria criteria) {
        return null;
    }

    @Override
    public ValidationResult validate(final IndexVolume request) {
        return null;
    }

    @Override
    public IndexVolume create(final IndexVolume indexVolume) {
        return null;
    }

    @Override
    public IndexVolume read(final int id) {
        return null;
    }

    @Override
    public IndexVolume update(final IndexVolume indexVolume) {
        return null;
    }

    @Override
    public Boolean delete(final int id) {
        return true;
    }

    @Override
    public void rescan() {
    }

    @Override
    public IndexVolume selectVolume(final String groupName, final String nodeName) {
        return null;
    }
}
