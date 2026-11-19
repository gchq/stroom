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

package stroom.floormap.client.component;

import stroom.floormap.client.model.FloorMapEntityList.EntityEntry;
import stroom.floormap.client.model.MemberCandidate;
import stroom.floormap.shared.FloorMapGroup;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class TestFloorMapGroupEditController {

    @Test
    void everyRosterEntityIsACandidateOnTheMap() {
        final FloorMapGroup group = new FloorMapGroup("g1", "Rooms", null, Collections.emptyList());
        final List<EntityEntry> roster = Arrays.asList(
                new EntityEntry("room-1", "Room 1", "room", true),
                new EntityEntry("gate-3", "Gate 3", "gate", true));

        final List<MemberCandidate> candidates =
                FloorMapGroupEditController.candidates(group, roster, null);

        assertThat(candidates)
                .extracting(MemberCandidate::getId, MemberCandidate::getName,
                        MemberCandidate::getType, MemberCandidate::isOnTheMap)
                .containsExactly(
                        tuple("room-1", "Room 1", "room", true),
                        tuple("gate-3", "Gate 3", "gate", true));
    }

    @Test
    void aMemberTheRosterHasNotSeenIsKeptAfterIt() {
        final FloorMapGroup group = new FloorMapGroup("g1", "People", null,
                Arrays.asList("sue", "room-1"));
        final List<EntityEntry> roster = Collections.singletonList(
                new EntityEntry("room-1", "Room 1", "room", true));

        final List<MemberCandidate> candidates =
                FloorMapGroupEditController.candidates(group, roster, id -> "Sue Smith");

        assertThat(candidates).extracting(MemberCandidate::getId).containsExactly("room-1", "sue");
        final MemberCandidate sue = candidates.get(1);
        assertThat(sue.getName()).isEqualTo("Sue Smith");
        assertThat(sue.getType()).isEmpty();
        assertThat(sue.isOnTheMap()).isFalse();
    }

    @Test
    void aMemberWithNoKnownNameIsNamedByItsId() {
        final FloorMapGroup group = new FloorMapGroup("g1", "People", null,
                Arrays.asList("sue", "bob"));

        final List<MemberCandidate> candidates = FloorMapGroupEditController.candidates(
                group, null, id -> "bob".equals(id)
                        ? ""
                        : null);

        assertThat(candidates).extracting(MemberCandidate::getName).containsExactly("sue", "bob");
    }

    @Test
    void aDuplicateOrNullRosterEntryIsSkipped() {
        final FloorMapGroup group = new FloorMapGroup("g1", "Rooms", null, Collections.emptyList());
        final List<EntityEntry> roster = Arrays.asList(
                new EntityEntry("room-1", "Room 1", "room", true),
                null,
                new EntityEntry(null, "Nobody", "room", true),
                new EntityEntry("room-1", "Room 1 again", "room", true));

        final List<MemberCandidate> candidates =
                FloorMapGroupEditController.candidates(group, roster, null);

        assertThat(candidates).extracting(MemberCandidate::getName).containsExactly("Room 1");
    }
}
