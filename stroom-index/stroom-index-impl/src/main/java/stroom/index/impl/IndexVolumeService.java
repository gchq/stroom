/*
 * Copyright 2018 Crown Copyright
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

package stroom.index.impl;

import stroom.entity.shared.ExpressionCriteria;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.ValidationResult;
import stroom.util.shared.ResultPage;

public interface IndexVolumeService {

    ResultPage<IndexVolume> find(ExpressionCriteria criteria);

    ValidationResult validate(final IndexVolume request);

    /// Checks that this node can use the path of a volume, creating the directory if it does not exist.
    /// Every node has to be able to use the path of a shared volume, so this is run on each of them.
    ///
    /// @param indexVolume The volume whose path is to be checked.
    /// @return Ok if this node can write to the path, otherwise an error.
    ValidationResult validatePath(IndexVolume indexVolume);

    IndexVolume create(IndexVolume indexVolume);

    IndexVolume read(int id);

    IndexVolume update(IndexVolume indexVolume);

    Boolean delete(int id);

    void rescan();

    /**
     * Uses the configured volume selector to select an index volume from group
     * groupName that node nodeName can use. The shared volumes of the group are
     * used if it has any that can take a shard, otherwise the local volumes
     * belonging to node nodeName.
     *
     * @throws stroom.index.shared.IndexException When no suitable {@link IndexVolume} can
     *                                            be found.
     */
    IndexVolume selectVolume(final String groupName, final String nodeName);
}
