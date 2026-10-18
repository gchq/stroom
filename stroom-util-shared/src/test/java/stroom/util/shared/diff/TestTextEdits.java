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

package stroom.util.shared.diff;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestTextEdits {

    private static final List<String> LINES = Arrays.asList("a", "bb", "ccc", "dd", "e");

    @Test
    void testReplaceLines() {
        final List<TextEdit> edits = TextEdits.fromLineReplacements(LINES, Collections.singletonList(
                new LineReplacement(1, 3, Arrays.asList("x", "y", "z"))));
        assertThat(edits).hasSize(1);
        final TextEdit edit = edits.get(0);
        assertThat(edit.getFrom()).isEqualTo(new TextPosition(1, 0));
        assertThat(edit.getTo()).isEqualTo(new TextPosition(2, 3));
        assertThat(edit.getText()).isEqualTo("x\ny\nz");
        assertThat(TextEdits.apply(LINES, edits)).isEqualTo("a\nx\ny\nz\ndd\ne");
    }

    @Test
    void testDeleteMiddleLines() {
        final List<TextEdit> edits = TextEdits.fromLineReplacements(LINES, Collections.singletonList(
                new LineReplacement(1, 3, Collections.emptyList())));
        // Deletes the lines and the line break after them.
        assertThat(edits.get(0).getFrom()).isEqualTo(new TextPosition(1, 0));
        assertThat(edits.get(0).getTo()).isEqualTo(new TextPosition(3, 0));
        assertThat(edits.get(0).getText()).isEmpty();
        assertThat(TextEdits.apply(LINES, edits)).isEqualTo("a\ndd\ne");
    }

    @Test
    void testDeleteLastLines() {
        final List<TextEdit> edits = TextEdits.fromLineReplacements(LINES, Collections.singletonList(
                new LineReplacement(3, 5, Collections.emptyList())));
        // Deletes the lines and the line break before them.
        assertThat(edits.get(0).getFrom()).isEqualTo(new TextPosition(2, 3));
        assertThat(edits.get(0).getTo()).isEqualTo(new TextPosition(4, 1));
        assertThat(TextEdits.apply(LINES, edits)).isEqualTo("a\nbb\nccc");
    }

    @Test
    void testDeleteEverything() {
        final List<TextEdit> edits = TextEdits.fromLineReplacements(LINES, Collections.singletonList(
                new LineReplacement(0, 5, Collections.emptyList())));
        assertThat(TextEdits.apply(LINES, edits)).isEmpty();
    }

    @Test
    void testInsertNeverHasAnEmptyRange() {
        final List<TextEdit> edits = TextEdits.fromLineReplacements(LINES, Collections.singletonList(
                new LineReplacement(2, 2, Collections.singletonList("new"))));
        final TextEdit edit = edits.get(0);
        // Takes in the first character of the line it is inserted before, and puts it back.
        assertThat(edit.getFrom()).isEqualTo(new TextPosition(2, 0));
        assertThat(edit.getTo()).isEqualTo(new TextPosition(2, 1));
        assertThat(edit.getText()).isEqualTo("new\nc");
        assertThat(TextEdits.apply(LINES, edits)).isEqualTo("a\nbb\nnew\nccc\ndd\ne");
    }

    @Test
    void testInsertBeforeEmptyLine() {
        final List<String> lines = Arrays.asList("a", "", "b");
        final List<TextEdit> edits = TextEdits.fromLineReplacements(lines, Collections.singletonList(
                new LineReplacement(1, 1, Collections.singletonList("new"))));
        // The empty line has no character, so the line break after it is taken in.
        assertThat(edits.get(0).getFrom()).isEqualTo(new TextPosition(1, 0));
        assertThat(edits.get(0).getTo()).isEqualTo(new TextPosition(2, 0));
        assertThat(TextEdits.apply(lines, edits)).isEqualTo("a\nnew\n\nb");
    }

    @Test
    void testAppendAtEnd() {
        final List<TextEdit> edits = TextEdits.fromLineReplacements(LINES, Collections.singletonList(
                new LineReplacement(5, 5, Arrays.asList("f", "g"))));
        final TextEdit edit = edits.get(0);
        // Takes in the last character of the text.
        assertThat(edit.getFrom()).isEqualTo(new TextPosition(4, 0));
        assertThat(edit.getTo()).isEqualTo(new TextPosition(4, 1));
        assertThat(edit.getText()).isEqualTo("e\nf\ng");
        assertThat(TextEdits.apply(LINES, edits)).isEqualTo("a\nbb\nccc\ndd\ne\nf\ng");
    }

    @Test
    void testAppendAfterTrailingEmptyLine() {
        final List<String> lines = Arrays.asList("a", "");
        final List<TextEdit> edits = TextEdits.fromLineReplacements(lines, Collections.singletonList(
                new LineReplacement(2, 2, Collections.singletonList("b"))));
        assertThat(edits.get(0).getFrom()).isEqualTo(new TextPosition(0, 1));
        assertThat(edits.get(0).getTo()).isEqualTo(new TextPosition(1, 0));
        assertThat(TextEdits.apply(lines, edits)).isEqualTo("a\n\nb");
    }

    @Test
    void testIntoEmptyText() {
        final List<TextEdit> edits = TextEdits.fromLineReplacements(Collections.emptyList(),
                Collections.singletonList(new LineReplacement(0, 1, Arrays.asList("x", "y"))));
        // Nothing to take in, so the range is empty.
        assertThat(edits.get(0).getFrom()).isEqualTo(new TextPosition(0, 0));
        assertThat(edits.get(0).getTo()).isEqualTo(new TextPosition(0, 0));
        assertThat(TextEdits.apply(Collections.emptyList(), edits)).isEqualTo("x\ny");
    }

    @Test
    void testChangeEmptyLine() {
        final List<String> lines = Arrays.asList("a", "", "b");
        final List<TextEdit> edits = TextEdits.fromLineReplacements(lines, Collections.singletonList(
                new LineReplacement(1, 2, Collections.singletonList(">"))));
        assertThat(edits.get(0).getFrom()).isNotEqualTo(edits.get(0).getTo());
        assertThat(TextEdits.apply(lines, edits)).isEqualTo("a\n>\nb");
    }

    @Test
    void testSeveralReplacementsInAnyOrder() {
        final List<LineReplacement> replacements = new ArrayList<>();
        replacements.add(new LineReplacement(4, 5, Collections.singletonList("E")));
        replacements.add(new LineReplacement(0, 0, Collections.singletonList("start")));
        replacements.add(new LineReplacement(2, 3, Collections.emptyList()));
        final List<TextEdit> edits = TextEdits.fromLineReplacements(LINES, replacements);
        assertThat(edits).hasSize(3);
        for (int i = 1; i < edits.size(); i++) {
            assertThat(edits.get(i).getStartOffset()).isGreaterThanOrEqualTo(edits.get(i - 1).getEndOffset());
        }
        assertThat(TextEdits.apply(LINES, edits)).isEqualTo("start\na\nbb\ndd\nE");
    }

    @Test
    void testTouchingReplacementsAreMerged() {
        final List<TextEdit> edits = TextEdits.fromLineReplacements(LINES, Arrays.asList(
                new LineReplacement(1, 2, Collections.singletonList("B")),
                new LineReplacement(2, 3, Collections.singletonList("C"))));
        assertThat(edits).hasSize(1);
        assertThat(TextEdits.apply(LINES, edits)).isEqualTo("a\nB\nC\ndd\ne");
    }

    @Test
    void testNoOpReplacementIsDropped() {
        final List<TextEdit> edits = TextEdits.fromLineReplacements(LINES, Collections.singletonList(
                new LineReplacement(2, 2, Collections.emptyList())));
        assertThat(edits).isEmpty();
    }

    @Test
    void testInvalidReplacements() {
        assertThatThrownBy(() -> TextEdits.fromLineReplacements(LINES, Arrays.asList(
                new LineReplacement(1, 3, Collections.emptyList()),
                new LineReplacement(2, 4, Collections.emptyList()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlap");
        assertThatThrownBy(() -> TextEdits.fromLineReplacements(LINES, Collections.singletonList(
                new LineReplacement(4, 6, Collections.emptyList()))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LineReplacement(3, 2, Collections.emptyList()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testAdjustPositionAfterAddedLines() {
        // Two lines replace one above the cursor, so it moves down a line.
        final List<TextEdit> edits = TextEdits.fromLineReplacements(LINES, Collections.singletonList(
                new LineReplacement(1, 2, Arrays.asList("x", "y"))));
        assertThat(TextEdits.adjustPosition(LINES, edits, new TextPosition(3, 1)))
                .isEqualTo(new TextPosition(4, 1));
        // Before the change it doesn't move.
        assertThat(TextEdits.adjustPosition(LINES, edits, new TextPosition(0, 1)))
                .isEqualTo(new TextPosition(0, 1));
    }

    @Test
    void testAdjustPositionAfterRemovedLines() {
        final List<TextEdit> edits = TextEdits.fromLineReplacements(LINES, Collections.singletonList(
                new LineReplacement(0, 2, Collections.emptyList())));
        assertThat(TextEdits.adjustPosition(LINES, edits, new TextPosition(3, 2)))
                .isEqualTo(new TextPosition(1, 2));
    }

    @Test
    void testAdjustPositionAtInsertionMovesAfterIt() {
        final List<TextEdit> edits = TextEdits.fromLineReplacements(LINES, Collections.singletonList(
                new LineReplacement(2, 2, Collections.singletonList("new"))));
        // The cursor at the start of the line stays with that line.
        assertThat(TextEdits.adjustPosition(LINES, edits, new TextPosition(2, 0)))
                .isEqualTo(new TextPosition(3, 0));
        assertThat(TextEdits.adjustPosition(LINES, edits, new TextPosition(2, 2)))
                .isEqualTo(new TextPosition(3, 2));
    }

    @Test
    void testAdjustPositionInsideChange() {
        final List<TextEdit> edits = TextEdits.fromLineReplacements(LINES, Collections.singletonList(
                new LineReplacement(2, 3, Collections.singletonList("c"))));
        // The cursor was at the end of "ccc"; the replacement is shorter, so it goes to its end.
        assertThat(TextEdits.adjustPosition(LINES, edits, new TextPosition(2, 3)))
                .isEqualTo(new TextPosition(2, 1));
    }

    @Test
    void testAdjustPositionBeyondTextIsLimited() {
        final List<TextEdit> edits = TextEdits.fromLineReplacements(LINES, Collections.emptyList());
        assertThat(TextEdits.adjustPosition(LINES, edits, new TextPosition(10, 10)))
                .isEqualTo(new TextPosition(4, 1));
    }
}
