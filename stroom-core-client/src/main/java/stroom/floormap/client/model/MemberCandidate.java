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

package stroom.floormap.client.model;


/// One row of the group edit dialog's member picker ([FloorMapGroupEdit]).
///
/// A plain class rather than a record: nothing else in the GWT-compiled
/// source uses records, so this is not the place to find out whether the
/// compiler in use emulates them.
public final class MemberCandidate {

    private final String id;
    private final String name;
    private final String type;
    private final boolean onTheMap;

    /// @param id       the entity id — what actually gets stored
    /// @param name     the display name
    /// @param type     the entity type, shown so a gate and a person are
    ///         distinguishable at a glance; may be empty
    /// @param onTheMap `false` for a member the roster has not seen this
    ///         session, which the picker flags rather than hiding
    public MemberCandidate(final String id,
                           final String name,
                           final String type,
                           final boolean onTheMap) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.onTheMap = onTheMap;
    }

    /// @return The entity id.
    public String getId() {
        return id;
    }

    /// @return The display name.
    public String getName() {
        return name;
    }

    /// @return The entity type; may be empty.
    public String getType() {
        return type;
    }

    /// @return Whether the roster has seen it this session.
    public boolean isOnTheMap() {
        return onTheMap;
    }
}
