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

import stroom.query.api.ResultRequest.Fetch;
import stroom.query.api.ResultRequest.ResultStyle;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestResultRequest {

    private static Stream<Arguments> openGroups() {
        return Stream.of(
            Arguments.of(null, null, Set.of()),
            Arguments.of(Set.of("1", "2"), null, Set.of("1", "2")),
            Arguments.of(Set.of("1", "2"), GroupSelection.builder().build(), Set.of()),
            Arguments.of(Set.of("1", "2"), GroupSelection.builder().openGroups(Set.of()).build(), Set.of()),
            Arguments.of(Set.of("1", "2"), GroupSelection.builder().openGroups(Set.of("3", "4")).build(),
                    Set.of("3", "4")),
            Arguments.of(Set.of(), GroupSelection.builder().openGroups(Set.of("3", "4")).build(), Set.of("3", "4")),
            Arguments.of(null, GroupSelection.builder().openGroups(Set.of("3", "4")).build(),
                    Set.of("3", "4")),
            Arguments.of(null, GroupSelection.builder().openGroups(null).build(), Set.of())
        );
    }

    @ParameterizedTest
    @MethodSource("openGroups")
    void testConstructorOpenGroups(final Set<String> openGroups, final GroupSelection groupSelection,
                                   final Set<String> expected) {

        final ResultRequest request = new ResultRequest("1", null, List.of(), OffsetRange.UNBOUNDED,
                new TimeFilter(12, 1), openGroups, ResultStyle.TABLE, Fetch.ALL, groupSelection, "tableName");

        assertThat(request.getGroupSelection()).isNotNull();
        assertThat(request.getGroupSelection().getOpenGroups()).isEqualTo(expected);
    }
}
