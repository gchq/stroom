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

package stroom.floormap.client.overlay;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestFloorMapHoverDetail {

    /// The panel is headed by the name the rest of the UI uses.
    @Test
    void testCaptionIsTheDisplayName() {
        assertThat(FloorMapHoverDetail.caption("user-42", "Alice")).isEqualTo("Alice");
    }

    /// An unnamed entity is captioned by the id, which is what its grid rows show.
    @Test
    void testCaptionFallsBackToId() {
        assertThat(FloorMapHoverDetail.caption("user-42", null)).isEqualTo("user-42");
        assertThat(FloorMapHoverDetail.caption("user-42", "")).isEqualTo("user-42");
        assertThat(FloorMapHoverDetail.caption("user-42", "   ")).isEqualTo("user-42");
    }

    /// Nothing to identify it by means no caption at all, rather than a blank pill.
    @Test
    void testCaptionWithNothingToSay() {
        assertThat(FloorMapHoverDetail.caption(null, null)).isNull();
        assertThat(FloorMapHoverDetail.caption("  ", "  ")).isNull();
    }

    /// The reported shape of a fully-populated panel, in reading order.
    @Test
    void testFullDetail() {
        assertThat(FloorMapHoverDetail.lines(
                "person",
                Collections.singletonList("Loading Bay"),
                "X 4.5 m, Y 2.1 m",
                "user-42",
                "Alice"))
                .containsExactly(
                        "Type: person",
                        "Inside Loading Bay",
                        "Position: X 4.5 m, Y 2.1 m",
                        "Id: user-42");
    }

    /// Every containing area is named, innermost first — none is summarised away.
    @Test
    void testEveryContainingAreaIsNamed() {
        assertThat(FloorMapHoverDetail.areaLines(
                Arrays.asList("Server Rack", "Server Room", "East Wing")))
                .containsExactly(
                        "Inside 3 areas (innermost first):",
                        "• Server Rack",
                        "• Server Room",
                        "• East Wing");
    }

    /// One area is stated plainly — a count and a bulleted list of one would be silly.
    @Test
    void testSingleAreaIsStatedPlainly() {
        assertThat(FloorMapHoverDetail.areaLines(Collections.singletonList("Office")))
                .containsExactly("Inside Office");
    }

    /// Where areas exist, being in none of them is worth saying.
    @Test
    void testInNoAreaIsSaidWhenTheMapHasAreas() {
        assertThat(FloorMapHoverDetail.areaLines(Collections.emptyList()))
                .containsExactly("Not inside an area");
    }

    /// On a map with no areas at all the subject is dropped: "not inside an area"
    /// on every hover of every entity is noise, not information.
    @Test
    void testAreasAreUnmentionedOnAMapWithNone() {
        assertThat(FloorMapHoverDetail.areaLines(null)).isEmpty();
        assertThat(FloorMapHoverDetail.lines("person", null, "X 1 m, Y 1 m", "id", "Alice"))
                .containsExactly(
                        "Type: person",
                        "Position: X 1 m, Y 1 m",
                        "Id: id");
    }

    /// Blank area names are skipped rather than rendered as empty bullets.
    @Test
    void testBlankAreaNamesAreSkipped() {
        assertThat(FloorMapHoverDetail.areaLines(Arrays.asList("Office", "", null, "  ")))
                .containsExactly("Inside Office");
        assertThat(FloorMapHoverDetail.areaLines(Arrays.asList("", null)))
                .containsExactly("Not inside an area");
    }

    /// The id line is dropped when the caption already is the id.
    @Test
    void testIdIsNotRepeatedUnderItself() {
        assertThat(FloorMapHoverDetail.lines(
                "device", Collections.emptyList(), null, "dev-7", "dev-7"))
                .containsExactly("Type: device", "Not inside an area");
    }

    /// Each line stands or falls on its own input; a bare entity still reads.
    @Test
    void testMissingDetailOmitsItsLine() {
        assertThat(FloorMapHoverDetail.lines(null, null, null, null, null)).isEmpty();
        assertThat(FloorMapHoverDetail.lines("  ", null, "  ", "  ", null)).isEmpty();
        assertThat(FloorMapHoverDetail.lines(null, null, null, "fact-1", "Door"))
                .containsExactly("Id: fact-1");
    }

    /// The returned lists are the caller's to render, not to mutate.
    @Test
    void testResultsAreImmutable() {
        final List<String> lines = FloorMapHoverDetail.lines(
                "person", Arrays.asList("A", "B"), "X 0 m, Y 0 m", "id", "Alice");
        //noinspection DataFlowIssue
        assertThatThrownBy(() -> lines.add("nope"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
