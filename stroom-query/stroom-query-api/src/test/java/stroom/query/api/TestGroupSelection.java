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

package stroom.query.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestGroupSelection {

    private GroupSelection groupSelection;

    @Test
    void testOpen() {
        groupSelection = GroupSelection.builder().build();
        groupSelection.open("g1");
        assertThat(groupSelection.getOpenGroups()).containsExactlyInAnyOrder("g1");
        assertThat(groupSelection.getClosedGroups()).isEmpty();

        groupSelection = GroupSelection.builder().openGroups(Set.of("g1")).closedGroups(Set.of("g2")).build();
        groupSelection.open("g2");
        assertThat(groupSelection.getOpenGroups()).containsExactlyInAnyOrder("g1", "g2");
        assertThat(groupSelection.getClosedGroups()).isEmpty();
    }

    @Test
    void testClose() {
        groupSelection = GroupSelection.builder().build();
        groupSelection.close("g1");
        assertThat(groupSelection.getClosedGroups()).containsExactlyInAnyOrder("g1");
        assertThat(groupSelection.getOpenGroups()).isEmpty();

        groupSelection = GroupSelection.builder().openGroups(Set.of("g1")).closedGroups(Set.of("g2")).build();
        groupSelection.close("g1");
        assertThat(groupSelection.getClosedGroups()).containsExactlyInAnyOrder("g1", "g2");
        assertThat(groupSelection.getOpenGroups()).isEmpty();
    }

    @Test
    void testIsGroupOpen() {
        groupSelection = GroupSelection.builder()
                .expandedDepth(2).openGroups(Set.of("g1")).closedGroups(Set.of("g2")).build();

        assertThat(groupSelection.isGroupOpen("g3", 0)).isTrue();
        assertThat(groupSelection.isGroupOpen("g1", 3)).isTrue();

        assertThat(groupSelection.isGroupOpen("g2", 0)).isFalse();
        assertThat(groupSelection.isGroupOpen("g3", 4)).isFalse();
    }

    private static Stream<Arguments> hasGroupSelected() {
        return Stream.of(
            Arguments.of(new GroupSelection(), false),
            Arguments.of(GroupSelection.builder().build(), false),
            Arguments.of(GroupSelection.builder().closedGroups(Set.of()).openGroups(Set.of()).build(), false),
            Arguments.of(GroupSelection.builder().expandedDepth(1).build(), true),
            Arguments.of(GroupSelection.builder().openGroups(Set.of("1")).build(), true),
            Arguments.of(GroupSelection.builder().closedGroups(Set.of("2")).build(), true)
        );
    }

    @ParameterizedTest
    @MethodSource("hasGroupSelected")
    void testHasGroupsSelected(final GroupSelection groupSelection, final boolean hasGroupSelected) {
        assertThat(groupSelection.hasGroupsSelected()).isEqualTo(hasGroupSelected);
    }

    @Test
    void testCollapse() {
        groupSelection = GroupSelection.builder().build();
        assertThat(groupSelection.copy().collapse().build().getExpandedDepth()).isEqualTo(0);

        groupSelection = GroupSelection.builder().expandedDepth(2).build();
        assertThat(groupSelection.copy().collapse().build().getExpandedDepth()).isEqualTo(1);
    }

    @Test
    void testExpand() {
        groupSelection = GroupSelection.builder().build();
        assertThat(groupSelection.copy().expand(2).build().getExpandedDepth()).isEqualTo(1);

        groupSelection = GroupSelection.builder().expandedDepth(2).build();
        assertThat(groupSelection.copy().expand(2).build().getExpandedDepth()).isEqualTo(2);
    }
}
