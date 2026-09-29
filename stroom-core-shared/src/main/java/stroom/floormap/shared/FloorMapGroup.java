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

package stroom.floormap.shared;

import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/// A user-created group of floor-map entities — "Maintenance", "Security" — held
/// on the [FloorMapDoc] as an ordered list.
///
/// A group is deliberately **generic over ids**: a member is any
/// `memberId` from the one id namespace the map already uses, so a group can
/// hold event-stream entities (people, vehicles), static object facts (a gate, a
/// camera), or even areas and backgrounds, freely mixed. Nothing here knows or
/// cares which — that is what lets the tracking panel, the roster
/// (`FloorMapEntityList`) and the canvas highlight all key on the same
/// string.
///
/// **Identity is [#getId()], never the name.** The name is
/// display-only and freely renamable, and two groups may legitimately share one:
/// an id keeps them distinct, so a rename can never orphan membership, drop a
/// canvas highlight, or confuse a future reference to a group. This differs from [TypeStyle], whose identity
/// *is* its `type` — a type name already exists in the data, whereas
/// a group is invented in the UI and has no natural key.
///
/// Immutable, and every mutation is a static helper returning a new value, so
/// all list surgery is unit-testable off the GWT presenters (the shape
/// [TypeStyle#merge] already established). Holds no GWT or DOM types.
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class FloorMapGroup {

    /// The colour a brand-new group starts with, which the user then changes at
    /// will — the same one-default shape area creation uses for its
    /// `"area"` fill.
    ///
    /// Purple, chosen to avoid every colour the canvas already means something
    /// with: `#1e88e5` blue (selection handles, and the default area fill),
    /// `#ff9800` orange (selected) and `#00c853` green (area-related).
    /// A default that collided with one of those would make a user's very first
    /// group look like a selection or a containment hint.
    public static final String DEFAULT_COLOUR = "#8e24aa";

    /// Prefix for generated group ids, mirroring the fact-key idiom.
    private static final String ID_PREFIX = "group";

    /// Base name new groups are numbered from ("Group", "Group 2", …).
    public static final String DEFAULT_NAME = "Group";

    @JsonProperty
    private final String id;
    @JsonProperty
    private final String name;
    @JsonProperty
    private final String colour;
    @JsonProperty
    private final List<String> memberIds;

    @JsonCreator
    public FloorMapGroup(@JsonProperty("id") final String id,
                         @JsonProperty("name") final String name,
                         @JsonProperty("colour") final String colour,
                         @JsonProperty("memberIds") final List<String> memberIds) {
        this.id = id;
        this.name = name;
        this.colour = colour;
        // Order-preserving and duplicate-free: members are added one at a time
        // from the UI, and a repeated id must not produce a second row.
        this.memberIds = memberIds != null
                ? Collections.unmodifiableList(new ArrayList<>(new LinkedHashSet<>(memberIds)))
                : Collections.emptyList();
    }

    /// The group's stable identity.
    ///
    /// Falls back to the [name][#getName()] when the stored id is absent
    /// or blank. No document has ever been written without ids — this is purely so
    /// a hand-edited document still opens rather than throwing. Note the fallback is not a
    /// non-null guarantee: `name` is itself nullable, so a group with neither an id nor a
    /// name — the same hand-edited case — still yields `null`.
    public String getId() {
        return NullSafe.isNonEmptyString(id)
                ? id
                : name;
    }

    /// The user-facing name; display only, and not required to be unique.
    public String getName() {
        return name;
    }

    /// The highlight colour for this group's members, or `null` when unset.
    /// Callers wanting a colour to actually draw with should use
    /// [#findColourOrDefault()].
    public String getColour() {
        return colour;
    }

    /// The group's colour, or [#DEFAULT_COLOUR] when it has none.
    ///
    /// Name structured to avoid triggering TestJsonSerialisation.testNoExtraProps()
    public String findColourOrDefault() {
        return NullSafe.isNonEmptyString(colour)
                ? colour
                : DEFAULT_COLOUR;
    }

    /// The member ids, in insertion order, duplicate-free; never `null`.
    public List<String> getMemberIds() {
        return memberIds;
    }

    public boolean contains(final String memberId) {
        return memberId != null && memberIds.contains(memberId);
    }

    /// The number of members, however many of them are currently on the map.
    ///
    /// Name structured to avoid triggering TestJsonSerialisation.testNoExtraProps()
    public int countMembers() {
        return memberIds.size();
    }

    /// Returns a copy of this group with the given name.
    public FloorMapGroup withName(final String newName) {
        return new FloorMapGroup(getId(), newName, colour, memberIds);
    }

    /// Returns a copy of this group with the given membership.
    public FloorMapGroup withMembers(final List<String> newMemberIds) {
        return new FloorMapGroup(getId(), name, colour, newMemberIds);
    }

    /// Returns a copy with `memberId` added at the end, or this group
    /// unchanged if the id is null/blank or already a member.
    public FloorMapGroup withMember(final String memberId) {
        if (NullSafe.isEmptyString(memberId) || memberIds.contains(memberId)) {
            return this;
        }
        final List<String> next = new ArrayList<>(memberIds);
        next.add(memberId);
        return withMembers(next);
    }

    /// Returns a copy without `memberId`, or this group unchanged if it was
    /// not a member.
    public FloorMapGroup withoutMember(final String memberId) {
        if (memberId == null || !memberIds.contains(memberId)) {
            return this;
        }
        final List<String> next = new ArrayList<>(memberIds);
        next.remove(memberId);
        return withMembers(next);
    }

    // ------------------------------------------------------------------------
    // List helpers — all matched on group id, never on name
    // ------------------------------------------------------------------------

    /// Returns a copy of `groups` with the entry sharing `group`'s id
    /// replaced, keeping its position. Appends when no entry has that id, so this
    /// doubles as "save this group".
    ///
    /// @param groups the current list; may be `null`
    /// @param group  the replacement; must not be `null`
    /// @return a new list; never `null`
    public static List<FloorMapGroup> replace(final List<FloorMapGroup> groups,
                                              final FloorMapGroup group) {
        final List<FloorMapGroup> result = new ArrayList<>();
        boolean replaced = false;
        if (groups != null) {
            for (final FloorMapGroup existing : groups) {
                if (existing != null && Objects.equals(existing.getId(), group.getId())) {
                    result.add(group);
                    replaced = true;
                } else {
                    result.add(existing);
                }
            }
        }
        if (!replaced) {
            result.add(group);
        }
        return result;
    }

    /// Returns a copy of `groups` without the group having `groupId`.
    ///
    /// @param groups  the current list; may be `null`
    /// @param groupId the id to remove; may be `null` (a no-op copy)
    /// @return a new list; never `null`
    public static List<FloorMapGroup> without(final List<FloorMapGroup> groups,
                                              final String groupId) {
        final List<FloorMapGroup> result = new ArrayList<>();
        if (groups != null) {
            for (final FloorMapGroup existing : groups) {
                if (existing != null && !Objects.equals(existing.getId(), groupId)) {
                    result.add(existing);
                }
            }
        }
        return result;
    }

    /// Finds the group with the given id.
    ///
    /// @param groups  the list to search; may be `null`
    /// @param groupId the id to look for; may be `null`
    /// @return the group, or `null` when absent
    public static FloorMapGroup find(final List<FloorMapGroup> groups,
                                     final String groupId) {
        if (groups != null && groupId != null) {
            for (final FloorMapGroup group : groups) {
                if (group != null && groupId.equals(group.getId())) {
                    return group;
                }
            }
        }
        return null;
    }

    /// Generates an id that no group in `groups` is using.
    ///
    /// Same idiom as `FloorMapEditorModel.generateObjectKey(String)`:
    /// `group-NNNNN` from a random int, retried on collision, with a
    /// timestamp suffix as the never-expected fallback. The [Random] is a
    /// parameter so the collision path is testable with a seeded generator
    /// instead of left to chance.
    ///
    /// @param groups the existing groups; may be `null`
    /// @param random the generator to draw from; may be `null` for a fresh one
    /// @return an unused group id; never `null`
    public static String generateId(final List<FloorMapGroup> groups,
                                    final Random random) {
        final Random rng = random != null
                ? random
                : new Random();
        final Set<String> used = new LinkedHashSet<>();
        if (groups != null) {
            for (final FloorMapGroup group : groups) {
                if (group != null) {
                    used.add(group.getId());
                }
            }
        }
        for (int i = 0; i < 1_000; i++) {
            final String candidate = ID_PREFIX + "-" + rng.nextInt(99999);
            if (!used.contains(candidate)) {
                return candidate;
            }
        }
        return ID_PREFIX + "-" + System.currentTimeMillis();
    }

    /// A display name not already in use — `"Group"`, else `"Group 2"`,
    /// `"Group 3"`, and so on.
    ///
    /// Duplicate names are *allowed* (ids are identity), so this is only
    /// about the new-group default reading well without the user having to fix it.
    /// A name the user types is never adjusted.
    ///
    /// @param groups  the existing groups; may be `null`
    /// @param desired the base name; may be `null` for [#DEFAULT_NAME]
    /// @return an unused name; never `null`
    public static String uniqueName(final List<FloorMapGroup> groups,
                                    final String desired) {
        final String base = NullSafe.isNonEmptyString(desired)
                ? desired
                : DEFAULT_NAME;
        final Set<String> used = new LinkedHashSet<>();
        if (groups != null) {
            for (final FloorMapGroup group : groups) {
                if (group != null && group.getName() != null) {
                    used.add(group.getName());
                }
            }
        }
        if (!used.contains(base)) {
            return base;
        }
        int n = 2;
        while (used.contains(base + " " + n)) {
            n++;
        }
        return base + " " + n;
    }

    /// Creates a new group, ready to be appended to `groups`: a generated
    /// id, a non-colliding default name and the default colour, with no members.
    ///
    /// @param groups the existing groups; may be `null`
    /// @param random the generator for the id; may be `null`
    /// @return the new group; never `null`
    public static FloorMapGroup create(final List<FloorMapGroup> groups,
                                       final Random random) {
        return new FloorMapGroup(
                generateId(groups, random),
                uniqueName(groups, DEFAULT_NAME),
                DEFAULT_COLOUR,
                null);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FloorMapGroup that = (FloorMapGroup) o;
        // Full-content equality, not id-only: the document's dirty check diffs
        // whole FloorMapDocs, so renaming a group or moving a member has to
        // register as a change.
        return Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && Objects.equals(colour, that.colour)
                && Objects.equals(memberIds, that.memberIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, colour, memberIds);
    }

    @Override
    public String toString() {
        return "FloorMapGroup{id='" + id + "', name='" + name + "', colour='" + colour
                + "', memberIds=" + memberIds + "}";
    }
}
