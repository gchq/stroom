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
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestLineDiff {

    @Test
    void testSplitLines() {
        assertThat(LineDiff.splitLines(null)).containsExactly("");
        assertThat(LineDiff.splitLines("")).containsExactly("");
        assertThat(LineDiff.splitLines("a")).containsExactly("a");
        assertThat(LineDiff.splitLines("a\n")).containsExactly("a", "");
        assertThat(LineDiff.splitLines("a\nb")).containsExactly("a", "b");
        assertThat(LineDiff.splitLines("a\r\nb\r\n")).containsExactly("a", "b", "");
        assertThat(LineDiff.splitLines("a\rb")).containsExactly("a", "b");
        assertThat(LineDiff.splitLines("a\n\nb")).containsExactly("a", "", "b");
        assertThat(LineDiff.splitLines("a\r\rb")).containsExactly("a", "", "b");
        assertThat(LineDiff.splitLines("\n")).containsExactly("", "");
    }

    @Test
    void testIdentical() {
        final LineDiffResult result = LineDiff.diff("a\nb\nc", "a\nb\nc");
        assertThat(result.isIdentical()).isTrue();
        assertThat(result.getChangedBlocks()).isEmpty();
        assertThat(result.getAddedLineCount()).isZero();
        assertThat(result.getRemovedLineCount()).isZero();
        assertThat(result.getHunks(3)).isEmpty();
        assertThat(result.toUnifiedDiff(3)).isEmpty();
        assertThat(result.getLineReplacements()).isEmpty();
        assertThat(result.getBlocks()).containsExactly(new DiffBlock(DiffType.EQUAL, 0, 3, 0, 3));
    }

    @Test
    void testBothEmpty() {
        final LineDiffResult result = LineDiff.diff("", null);
        assertThat(result.isIdentical()).isTrue();
        assertThat(result.getOldLines()).containsExactly("");
    }

    @Test
    void testEmptyToText() {
        final LineDiffResult result = LineDiff.diff("", "a\nb");
        assertThat(result.isIdentical()).isFalse();
        // The single empty line becomes the two new lines.
        assertThat(result.getBlocks()).containsExactly(new DiffBlock(DiffType.CHANGE, 0, 1, 0, 2));
        assertThat(result.getAddedLineCount()).isEqualTo(2);
        assertThat(result.getRemovedLineCount()).isEqualTo(1);
    }

    @Test
    void testTextToEmpty() {
        final LineDiffResult result = LineDiff.diff("a\nb", "");
        assertThat(result.getBlocks()).containsExactly(new DiffBlock(DiffType.CHANGE, 0, 2, 0, 1));
        assertThat(applyReplacements(result)).isEqualTo(result.getNewLines());
    }

    @Test
    void testLineEndingsIgnored() {
        final LineDiffResult result = LineDiff.diff("a\r\nb\r\nc\r\n", "a\nb\nc\n");
        assertThat(result.isIdentical()).isTrue();

        final LineDiffResult oldMac = LineDiff.diff("a\rb", "a\nb");
        assertThat(oldMac.isIdentical()).isTrue();
    }

    @Test
    void testTrailingNewlineIsALine() {
        final LineDiffResult result = LineDiff.diff("a\nb", "a\nb\n");
        assertThat(result.getBlocks()).containsExactly(
                new DiffBlock(DiffType.EQUAL, 0, 2, 0, 2),
                new DiffBlock(DiffType.INSERT, 2, 2, 2, 3));
        assertThat(result.getAddedLineCount()).isEqualTo(1);
    }

    @Test
    void testInsertion() {
        final LineDiffResult result = LineDiff.diff(
                "a\nb\nc",
                "a\nb\nx\ny\nc");
        assertThat(result.getChangedBlocks()).containsExactly(new DiffBlock(DiffType.INSERT, 2, 2, 2, 4));
        assertThat(result.getAddedLineCount()).isEqualTo(2);
        assertThat(result.getRemovedLineCount()).isZero();
        assertThat(result.getLineReplacements()).containsExactly(
                new LineReplacement(2, 2, Arrays.asList("x", "y")));
        assertThat(applyReplacements(result)).isEqualTo(result.getNewLines());
    }

    @Test
    void testInsertionAtStartAndEnd() {
        final LineDiffResult result = LineDiff.diff("b", "a\nb\nc");
        assertThat(result.getChangedBlocks()).containsExactly(
                new DiffBlock(DiffType.INSERT, 0, 0, 0, 1),
                new DiffBlock(DiffType.INSERT, 1, 1, 2, 3));
        assertThat(applyReplacements(result)).isEqualTo(result.getNewLines());
    }

    @Test
    void testDeletion() {
        final LineDiffResult result = LineDiff.diff(
                "a\nb\nc\nd",
                "a\nd");
        assertThat(result.getChangedBlocks()).containsExactly(new DiffBlock(DiffType.DELETE, 1, 3, 1, 1));
        assertThat(result.getAddedLineCount()).isZero();
        assertThat(result.getRemovedLineCount()).isEqualTo(2);
        assertThat(result.getLineReplacements()).containsExactly(
                new LineReplacement(1, 3, List.of()));
        assertThat(applyReplacements(result)).isEqualTo(result.getNewLines());
    }

    @Test
    void testChange() {
        final LineDiffResult result = LineDiff.diff(
                "<a>\n  <b>one</b>\n</a>",
                "<a>\n  <b>two</b>\n  <c/>\n</a>");
        assertThat(result.getChangedBlocks()).containsExactly(new DiffBlock(DiffType.CHANGE, 1, 2, 1, 3));
        assertThat(result.getAddedLineCount()).isEqualTo(2);
        assertThat(result.getRemovedLineCount()).isEqualTo(1);

        final List<DiffLine> lines = result.getLines();
        assertThat(lines).extracting(DiffLine::getType).containsExactly(
                DiffType.EQUAL, DiffType.CHANGE, DiffType.INSERT, DiffType.EQUAL);

        final DiffLine changed = lines.get(1);
        assertThat(changed.getOldIndex()).isEqualTo(1);
        assertThat(changed.getNewIndex()).isEqualTo(1);
        assertThat(changed.getOldText()).isEqualTo("  <b>one</b>");
        assertThat(changed.getNewText()).isEqualTo("  <b>two</b>");
        // "one" and "two" share only the 'o', which is joined into the change as the gap is short.
        assertThat(changed.getOldChanges()).containsExactly(new CharRange(5, 8));
        assertThat(changed.getNewChanges()).containsExactly(new CharRange(5, 8));

        final DiffLine inserted = lines.get(2);
        assertThat(inserted.hasOld()).isFalse();
        assertThat(inserted.getNewIndex()).isEqualTo(2);
        assertThat(inserted.getNewText()).isEqualTo("  <c/>");

        assertThat(applyReplacements(result)).isEqualTo(result.getNewLines());
    }

    @Test
    void testChangeWithFewerNewLines() {
        final LineDiffResult result = LineDiff.diff("a\nb\nc\nd", "a\nB\nd");
        assertThat(result.getChangedBlocks()).containsExactly(new DiffBlock(DiffType.CHANGE, 1, 3, 1, 2));
        assertThat(result.getLines()).extracting(DiffLine::getType).containsExactly(
                DiffType.EQUAL, DiffType.CHANGE, DiffType.DELETE, DiffType.EQUAL);
        assertThat(applyReplacements(result)).isEqualTo(result.getNewLines());
    }

    @Test
    void testIntraLineChange() {
        final LineDiffResult result = LineDiff.diff(
                "<xsl:value-of select=\"$user\"/>",
                "<xsl:value-of select=\"lower-case($user)\"/>");
        final DiffLine line = result.getLines().get(0);
        assertThat(line.getType()).isEqualTo(DiffType.CHANGE);
        // Pure insertions: nothing changed on the old side.
        assertThat(line.getOldChanges()).isEmpty();
        assertThat(line.getNewChanges()).containsExactly(
                new CharRange(22, 33),
                new CharRange(38, 39));
    }

    @Test
    void testHunksAndUnifiedDiff() {
        final List<String> oldLines = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            oldLines.add("line " + i);
        }
        final List<String> newLines = new ArrayList<>(oldLines);
        // Change line 2, delete line 10, insert after line 18.
        newLines.set(1, "line 2 changed");
        newLines.remove(9);
        newLines.add(17, "new line");

        final LineDiffResult result = LineDiff.diff(oldLines, newLines);
        assertThat(result.getAddedLineCount()).isEqualTo(2);
        assertThat(result.getRemovedLineCount()).isEqualTo(2);

        final List<DiffHunk> hunks = result.getHunks(3);
        assertThat(hunks).hasSize(3);
        assertThat(hunks.get(0).getHeader()).isEqualTo("@@ -1,5 +1,5 @@");
        assertThat(hunks.get(1).getHeader()).isEqualTo("@@ -7,7 +7,6 @@");
        assertThat(hunks.get(2).getHeader()).isEqualTo("@@ -16,5 +15,6 @@");
        assertThat(hunks.get(1).getRemovedLineCount()).isEqualTo(1);
        assertThat(hunks.get(1).getAddedLineCount()).isZero();

        assertThat(result.toUnifiedDiff(1)).isEqualTo(
                "@@ -1,3 +1,3 @@\n"
                + " line 1\n"
                + "-line 2\n"
                + "+line 2 changed\n"
                + " line 3\n"
                + "@@ -9,3 +9,2 @@\n"
                + " line 9\n"
                + "-line 10\n"
                + " line 11\n"
                + "@@ -18,2 +17,3 @@\n"
                + " line 18\n"
                + "+new line\n"
                + " line 19");

        // With enough context the hunks join up.
        assertThat(result.getHunks(4)).hasSize(1);
        assertThat(result.getHunks(4).get(0).getHeader()).isEqualTo("@@ -1,20 +1,20 @@");
        // No context.
        assertThat(result.getHunks(0)).hasSize(3);
        assertThat(result.getHunks(0).get(1).getHeader()).isEqualTo("@@ -10 +9,0 @@");
        assertThat(result.getHunks(0).get(2).getHeader()).isEqualTo("@@ -18,0 +18 @@");
    }

    @Test
    void testUnifiedDiffChangeOrder() {
        final LineDiffResult result = LineDiff.diff("a\nb\nc", "x\ny");
        assertThat(result.toUnifiedDiff(3)).isEqualTo(
                "@@ -1,3 +1,2 @@\n"
                + "-a\n"
                + "-b\n"
                + "-c\n"
                + "+x\n"
                + "+y");
    }

    @Test
    void testDuplicateLines() {
        final LineDiffResult result = LineDiff.diff(
                "}\n}\n}\nx\n}",
                "}\nx\n}\n}\n}");
        assertThat(applyReplacements(result)).isEqualTo(result.getNewLines());
        assertThat(result.getAddedLineCount() + result.getRemovedLineCount()).isEqualTo(2);
    }

    @Test
    void testRandomRoundTrip() {
        final Random random = new Random(123);
        final String[] vocabulary = {"<a>", "</a>", "", "", "text", "  <c>x</c>"};
        for (int run = 0; run < 5000; run++) {
            final List<String> oldLines = randomLines(random, vocabulary);
            final List<String> newLines = randomLines(random, vocabulary);
            final LineDiffResult result = LineDiff.diff(oldLines, newLines);
            assertThat(applyReplacements(result)).as("run %s", run).isEqualTo(newLines);

            // The side by side rows cover every line of both texts in order.
            final List<String> oldFromRows = new ArrayList<>();
            final List<String> newFromRows = new ArrayList<>();
            for (final DiffLine line : result.getLines()) {
                if (line.hasOld()) {
                    assertThat(line.getOldIndex()).isEqualTo(oldFromRows.size());
                    oldFromRows.add(line.getOldText());
                }
                if (line.hasNew()) {
                    assertThat(line.getNewIndex()).isEqualTo(newFromRows.size());
                    newFromRows.add(line.getNewText());
                }
            }
            assertThat(oldFromRows).isEqualTo(oldLines);
            assertThat(newFromRows).isEqualTo(newLines);

            // Applying through editor edits gives the same text.
            final List<TextEdit> edits = TextEdits.fromLineReplacements(oldLines, result.getLineReplacements());
            assertThat(TextEdits.apply(oldLines, edits)).isEqualTo(String.join("\n", newLines));
            final boolean oldIsEmpty = String.join("\n", oldLines).isEmpty();
            for (int i = 0; i < edits.size(); i++) {
                final TextEdit edit = edits.get(i);
                // A selection is never empty unless there is nothing to select.
                if (!oldIsEmpty) {
                    assertThat(edit.getEndOffset()).as("run %s", run).isGreaterThan(edit.getStartOffset());
                }
                if (i > 0) {
                    assertThat(edit.getStartOffset()).isGreaterThanOrEqualTo(edits.get(i - 1).getEndOffset());
                }
            }
        }
    }

    private static List<String> randomLines(final Random random, final String[] vocabulary) {
        final int count = 1 + random.nextInt(12);
        final List<String> lines = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            lines.add(vocabulary[random.nextInt(vocabulary.length)]);
        }
        return lines;
    }

    // Applies the replacements to the old lines, last first, to check they give the new lines.
    private static List<String> applyReplacements(final LineDiffResult result) {
        final List<String> lines = new ArrayList<>(result.getOldLines());
        final List<LineReplacement> replacements = result.getLineReplacements();
        for (int i = replacements.size() - 1; i >= 0; i--) {
            final LineReplacement replacement = replacements.get(i);
            final List<String> range = lines.subList(replacement.getFromLine(), replacement.getToLine());
            range.clear();
            range.addAll(replacement.getNewLines());
        }
        return lines;
    }

    @Test
    void someChangesCanBeLeftOut() {
        final String oldText = "<a>\n  <b>Test</b>\n</a>\n";
        final String newText = "<a>\n  <b>Development</b>\n</a>";
        final LineDiffResult diff = LineDiff.diff(oldText, newText);
        assertThat(diff.getChangedBlocks()).hasSize(2);

        // All of them, none of them, and all but the removal of the final line break.
        assertThat(diff.withChangesExcept(Set.of(), "\n")).isEqualTo(newText);
        assertThat(diff.withChangesExcept(Set.of(0, 1), "\n")).isEqualTo(oldText);
        assertThat(diff.withChangesExcept(Set.of(1), "\n")).isEqualTo("<a>\n  <b>Development</b>\n</a>\n");
        // Indexes that aren't changes are ignored.
        assertThat(diff.withChangesExcept(Set.of(7), "\n")).isEqualTo(newText);
    }

    @Test
    void theLineSeparatorOfATextIsFound() {
        assertThat(LineDiff.lineSeparatorOf("a\r\nb")).isEqualTo("\r\n");
        assertThat(LineDiff.lineSeparatorOf("a\rb")).isEqualTo("\r");
        assertThat(LineDiff.lineSeparatorOf("a\nb")).isEqualTo("\n");
        assertThat(LineDiff.lineSeparatorOf("a")).isEqualTo("\n");
        assertThat(LineDiff.lineSeparatorOf(null)).isEqualTo("\n");
        // Kept when only some changes are made.
        assertThat(LineDiff.diff("a\r\nb", "a\r\nc").withChangesExcept(Set.of(), "\r\n")).isEqualTo("a\r\nc");
    }
}
