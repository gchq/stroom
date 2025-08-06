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

        final ResultRequest request = new ResultRequest("1", "name", List.of(), OffsetRange.UNBOUNDED,
                new TimeFilter(12, 1), openGroups, ResultStyle.TABLE, Fetch.ALL, groupSelection);

        assertThat(request.getGroupSelection()).isNotNull();
        assertThat(request.getGroupSelection().getOpenGroups()).isEqualTo(expected);
    }
}
