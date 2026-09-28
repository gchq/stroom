/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.floormap.client.cluster;

import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One row of the cluster member list: everything the dialog shows about a
 * single member, resolved up front.
 *
 * <p>Resolved up front so the grid's columns and
 * {@link FloorMapClusterFilter} do no lookups — the filter re-runs on every
 * keystroke over a list that can hold hundreds of members, and a name or area
 * resolver called per cell per keystroke is the difference between a search box
 * that keeps up and one that does not.</p>
 *
 * <p>A plain immutable class rather than a record, matching the rest of the
 * GWT-compiled source. Holds no GWT or DOM types so it can be unit-tested on
 * the JVM.</p>
 */
@SuppressWarnings("ClassCanBeRecord") // Must compile under GWT
public final class FloorMapClusterMember {

    private final String id;
    private final String name;
    private final String type;
    private final List<String> areaNames;
    private final List<String> groupNames;

    /**
     * @param id         the entity id; the row's identity
     * @param name       the display name shown to the user; callers should
     *                   already have fallen back to the id when the entity has
     *                   no name
     * @param type       the entity type; the same for every member of one
     *                   cluster, since clustering runs per type
     * @param areaNames  the containing area names, innermost first; may be
     *                   {@code null} or empty
     * @param groupNames the names of the groups this member belongs to; may be
     *                   {@code null} or empty
     */
    public FloorMapClusterMember(final String id,
                                 final String name,
                                 final String type,
                                 final List<String> areaNames,
                                 final List<String> groupNames) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.areaNames = copyOf(areaNames);
        this.groupNames = copyOf(groupNames);
    }

    private static List<String> copyOf(final List<String> in) {
        return NullSafe.isEmptyCollection(in)
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(in));
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    /** Containing area names, innermost first; empty when in none. */
    public List<String> getAreaNames() {
        return areaNames;
    }

    /** Names of the groups this member belongs to; empty when in none. */
    public List<String> getGroupNames() {
        return groupNames;
    }

    @Override
    public String toString() {
        return name + " (" + id + ")";
    }
}
