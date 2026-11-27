/*
 * Copyright 2026 Crown Copyright
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

import stroom.data.store.impl.fs.shared.FsVolume;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/// The data volumes and their groups as they stand, for reporting (e.g. to Ask Stroom AI) rather than managing them.
/// No permission is checked: a caller that shows them decides what the user may see.
public interface FsVolumeStates {

    /// @return Each data volume group's name, in name order, with its volumes (each with its last measured state);
    /// a group with no volumes has an empty list.
    Map<String, List<FsVolume>> volumesByGroup();

    /// @return The group that streams are written to when their feed names none, if one is configured.
    Optional<String> defaultGroup();
}
