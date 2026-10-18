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

package stroom.pipeline.stepping.client.presenter;

import stroom.pipeline.stepping.client.presenter.SideBySideDiff.Side;
import stroom.util.shared.diff.CharRange;
import stroom.util.shared.diff.LineDiff;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestSideBySideDiff {

    @Test
    void testIdentical() {
        final SideBySideDiff diff = SideBySideDiff.of(LineDiff.diff("a\nb", "a\nb"));
        assertThat(diff.getRowCount()).isEqualTo(2);
        assertThat(diff.getLeft()).containsOnly(Side.EQUAL);
        assertThat(diff.getRight()).containsOnly(Side.EQUAL);
        assertThat(diff.getChangeStartRows()).isEmpty();
        assertThat(diff.getLeftText()).isEqualTo("a\nb");
        assertThat(diff.getRightText()).isEqualTo("a\nb");
        assertThat(diff.getNextChange(-1)).isEqualTo(-1);
    }

    @Test
    void testFillersLineUpInsertionsAndDeletions() {
        // Delete b, insert x and y after c.
        final SideBySideDiff diff = SideBySideDiff.of(LineDiff.diff(
                "a\nb\nc\nd",
                "a\nc\nx\ny\nd"));

        assertThat(diff.getRowCount()).isEqualTo(6);
        assertThat(diff.getLeft()).containsExactly(
                Side.EQUAL, Side.REMOVED, Side.EQUAL, Side.FILLER, Side.FILLER, Side.EQUAL);
        assertThat(diff.getRight()).containsExactly(
                Side.EQUAL, Side.FILLER, Side.EQUAL, Side.ADDED, Side.ADDED, Side.EQUAL);
        assertThat(diff.getLeftText()).isEqualTo("a\nb\nc\n\n\nd");
        assertThat(diff.getRightText()).isEqualTo("a\n\nc\nx\ny\nd");

        // Real line numbers, blank for fillers.
        assertThat(diff.getLeftLabels()).containsExactly("1", "2", "3", "", "", "4");
        assertThat(diff.getRightLabels()).containsExactly("1", "", "2", "3", "4", "5");

        assertThat(diff.getChangeStartRows()).containsExactly(1, 3);
    }

    @Test
    void testChangedLinesWithExtraLines() {
        final SideBySideDiff diff = SideBySideDiff.of(LineDiff.diff(
                "<a>\n  <b>one</b>\n</a>",
                "<a>\n  <b>two</b>\n  <c/>\n</a>"));

        assertThat(diff.getLeft()).containsExactly(Side.EQUAL, Side.CHANGED, Side.FILLER, Side.EQUAL);
        assertThat(diff.getRight()).containsExactly(Side.EQUAL, Side.CHANGED, Side.ADDED, Side.EQUAL);
        assertThat(diff.getLeftCharChanges().get(1)).containsExactly(new CharRange(5, 8));
        assertThat(diff.getRightCharChanges().get(1)).containsExactly(new CharRange(5, 8));
        assertThat(diff.getLeftCharChanges().get(2)).isEmpty();
        // One change block, even though it spans a changed and an added row.
        assertThat(diff.getChangeStartRows()).containsExactly(1);
    }

    @Test
    void testNavigation() {
        final SideBySideDiff diff = SideBySideDiff.of(LineDiff.diff(
                "a\nb\nc\nd\ne\nf",
                "A\nb\nc\nD\ne\nF"));
        assertThat(diff.getChangeStartRows()).containsExactly(0, 3, 5);

        assertThat(diff.getNextChange(-1)).isEqualTo(0);
        assertThat(diff.getNextChange(0)).isEqualTo(1);
        assertThat(diff.getNextChange(4)).isEqualTo(2);
        assertThat(diff.getNextChange(5)).isEqualTo(-1);

        assertThat(diff.getPreviousChange(5)).isEqualTo(1);
        assertThat(diff.getPreviousChange(1)).isEqualTo(0);
        assertThat(diff.getPreviousChange(0)).isEqualTo(-1);
    }

    @Test
    void testEmptyToText() {
        final SideBySideDiff diff = SideBySideDiff.of(LineDiff.diff("", "x\ny"));
        assertThat(diff.getLeft()).containsExactly(Side.CHANGED, Side.FILLER);
        assertThat(diff.getRight()).containsExactly(Side.CHANGED, Side.ADDED);
        assertThat(diff.getLeftText()).isEqualTo("\n");
    }
}
