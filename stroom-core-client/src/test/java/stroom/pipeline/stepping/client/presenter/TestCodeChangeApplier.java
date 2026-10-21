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

import stroom.util.shared.diff.LineDiff;
import stroom.util.shared.diff.LineReplacement;
import stroom.util.shared.diff.TextEdit;
import stroom.util.shared.diff.TextEdits;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestCodeChangeApplier {

    private static final String CODE = String.join("\n",
            "<xsl:stylesheet>",
            "  <xsl:template match=\"User\">",
            "    <Name><xsl:value-of select=\"Email\"/></Name>",
            "  </xsl:template>",
            "</xsl:stylesheet>");

    private static final String PROPOSED = String.join("\n",
            "<xsl:stylesheet>",
            "  <xsl:template match=\"User\">",
            "    <Name><xsl:value-of select=\"lower-case(substring-before(Email, '@'))\"/></Name>",
            "  </xsl:template>",
            "</xsl:stylesheet>");

    @Test
    void testApplyChangesOnlyTheChangedLines() {
        final FakeEditor editor = new FakeEditor(CODE);
        final CodeChangeApplier applier = new CodeChangeApplier(editor);

        assertThat(applier.apply(PROPOSED)).isEqualTo(ApplyCodeChangeResult.APPLIED);

        assertThat(editor.text).isEqualTo(PROPOSED);
        assertThat(editor.replacementCalls).hasSize(1);
        assertThat(editor.replacementCalls.get(0)).containsExactly(new LineReplacement(2, 3, List.of(
                "    <Name><xsl:value-of select=\"lower-case(substring-before(Email, '@'))\"/></Name>")));
        assertThat(editor.markers).hasSize(1);
        assertThat(editor.markers.get(0)).containsExactly(new int[]{2, 3});
        assertThat(applier.isShowingMarkers()).isTrue();
        assertThat(applier.getAppliedChange().getPreviousCode()).isEqualTo(CODE);
        assertThat(applier.getAppliedChange().getAppliedCode()).isEqualTo(PROPOSED);
    }

    @Test
    void testIdenticalProposalIsNotApplied() {
        final FakeEditor editor = new FakeEditor(CODE);
        final CodeChangeApplier applier = new CodeChangeApplier(editor);

        assertThat(applier.apply(CODE)).isEqualTo(ApplyCodeChangeResult.NO_CHANGE);
        // Only the line endings differ.
        assertThat(applier.apply(CODE.replace("\n", "\r\n"))).isEqualTo(ApplyCodeChangeResult.NO_CHANGE);

        assertThat(editor.replacementCalls).isEmpty();
        assertThat(applier.getAppliedChange()).isNull();
        assertThat(applier.canUndo()).isFalse();
    }

    @Test
    void testChangedEditorIsNotAppliedTo() {
        final FakeEditor editor = new FakeEditor(CODE + "\n<!-- edited -->");
        final CodeChangeApplier applier = new CodeChangeApplier(editor);

        assertThat(applier.apply(CODE, PROPOSED)).isEqualTo(ApplyCodeChangeResult.CODE_CHANGED);
        assertThat(editor.text).isEqualTo(CODE + "\n<!-- edited -->");
        assertThat(editor.replacementCalls).isEmpty();
    }

    @Test
    void testBaseCodeLineEndingsAreIgnored() {
        final FakeEditor editor = new FakeEditor(CODE);
        final CodeChangeApplier applier = new CodeChangeApplier(editor);

        assertThat(applier.apply(CODE.replace("\n", "\r\n"), PROPOSED)).isEqualTo(ApplyCodeChangeResult.APPLIED);
        assertThat(editor.text).isEqualTo(PROPOSED);
    }

    @Test
    void testEditorNotReady() {
        final FakeEditor editor = new FakeEditor(CODE);
        editor.ready = false;
        final CodeChangeApplier applier = new CodeChangeApplier(editor);

        assertThat(applier.apply(PROPOSED)).isEqualTo(ApplyCodeChangeResult.NOT_READY);
        assertThat(applier.getAppliedChange()).isNull();
        assertThat(editor.markers).isEmpty();
    }

    @Test
    void testUndoRestoresExactPreviousTextIncludingUnsavedEdits() {
        // The editor holds unsaved edits the user made before the proposal.
        final String edited = CODE.replace("User", "Person") + "\n";
        final String proposed = edited.replace("Email", "Mail");
        final FakeEditor editor = new FakeEditor(edited);
        final CodeChangeApplier applier = new CodeChangeApplier(editor);

        assertThat(applier.apply(proposed)).isEqualTo(ApplyCodeChangeResult.APPLIED);
        assertThat(applier.canUndo()).isTrue();

        assertThat(applier.undo()).isTrue();
        assertThat(editor.text).isEqualTo(edited);
        assertThat(applier.getAppliedChange()).isNull();
        assertThat(applier.isShowingMarkers()).isFalse();
        // The highlighting was cleared.
        assertThat(editor.markers.get(editor.markers.size() - 1)).isEmpty();
        // Undoing again does nothing.
        assertThat(applier.undo()).isFalse();
    }

    @Test
    void testUndoIsWithdrawnOnceEdited() {
        final FakeEditor editor = new FakeEditor(CODE);
        final CodeChangeApplier applier = new CodeChangeApplier(editor);
        applier.apply(PROPOSED);

        // The user types something.
        editor.text = PROPOSED + "x";
        applier.onEditorChange();

        assertThat(applier.canUndo()).isFalse();
        assertThat(applier.undo()).isFalse();
        assertThat(editor.text).isEqualTo(PROPOSED + "x");
        // The edit ended the highlighting.
        assertThat(applier.isShowingMarkers()).isFalse();

        // Ctrl+Z back to the applied text offers it again.
        editor.text = PROPOSED;
        applier.onEditorChange();
        assertThat(applier.canUndo()).isTrue();
        assertThat(applier.undo()).isTrue();
        assertThat(editor.text).isEqualTo(CODE);
    }

    @Test
    void testOwnEditsDoNotClearHighlighting() {
        final FakeEditor editor = new FakeEditor(CODE);
        final CodeChangeApplier applier = new CodeChangeApplier(editor);
        // The editor reports each of its changes while the replacements are made, as Ace does.
        editor.onChange = applier::onEditorChange;

        applier.apply(PROPOSED);

        assertThat(applier.isShowingMarkers()).isTrue();
        assertThat(editor.markers).hasSize(1);
    }

    @Test
    void testHighlightingUntilNextStep() {
        final FakeEditor editor = new FakeEditor(CODE);
        final CodeChangeApplier applier = new CodeChangeApplier(editor);
        applier.apply(PROPOSED);

        applier.onBeforeStep();
        assertThat(applier.isShowingMarkers()).isFalse();
        assertThat(editor.markers.get(editor.markers.size() - 1)).isEmpty();
    }

    @Test
    void testHighlightingKeptThroughApplyAndStep() {
        final FakeEditor editor = new FakeEditor(CODE);
        final CodeChangeApplier applier = new CodeChangeApplier(editor);
        applier.apply(PROPOSED);
        applier.keepMarkersForNextStep();

        // The step started by "Apply and step" keeps them.
        applier.onBeforeStep();
        assertThat(applier.isShowingMarkers()).isTrue();

        // The one after doesn't.
        applier.onBeforeStep();
        assertThat(applier.isShowingMarkers()).isFalse();
    }

    @Test
    void testDeletionOnlyHasNothingToHighlight() {
        final FakeEditor editor = new FakeEditor(CODE);
        final CodeChangeApplier applier = new CodeChangeApplier(editor);
        final String proposed = CODE.replace("    <Name><xsl:value-of select=\"Email\"/></Name>\n", "");

        assertThat(applier.apply(proposed)).isEqualTo(ApplyCodeChangeResult.APPLIED);
        assertThat(editor.text).isEqualTo(proposed);
        assertThat(applier.isShowingMarkers()).isFalse();
        assertThat(applier.canUndo()).isTrue();
    }

    @Test
    void testSeveralBlocksAppliedAndUndone() {
        final String code = "a\nb\nc\nd\ne\nf\ng";
        final String proposed = "A\nb\nc\nd\nE\nE2\nf\ng\nh";
        final FakeEditor editor = new FakeEditor(code);
        final CodeChangeApplier applier = new CodeChangeApplier(editor);

        assertThat(applier.apply(proposed)).isEqualTo(ApplyCodeChangeResult.APPLIED);
        assertThat(editor.text).isEqualTo(proposed);
        assertThat(editor.replacementCalls.get(0)).hasSize(3);
        assertThat(editor.markers.get(0)).containsExactly(
                new int[]{0, 1},
                new int[]{4, 6},
                new int[]{8, 9});

        assertThat(applier.undo()).isTrue();
        assertThat(editor.text).isEqualTo(code);
    }

    @Test
    void testSecondProposalAgainstTheCodeBeforeAnAppliedChangeIsAppliedOnTopOfIt() {
        // Regression: two changes proposed against the same code (e.g. in the same answer, or with the chat's
        // snapshot of the tab taken before the first was applied). Applying the first must not block the second
        // when they change different lines: the second is applied on top of the first.
        final String renamed = CODE.replace("match=\"User\"", "match=\"Person\"");
        final String both = PROPOSED.replace("match=\"User\"", "match=\"Person\"");
        final FakeEditor editor = new FakeEditor(CODE);
        final CodeChangeApplier applier = new CodeChangeApplier(editor);

        assertThat(applier.apply(CODE, PROPOSED)).isEqualTo(ApplyCodeChangeResult.APPLIED);
        assertThat(applier.apply(CODE, renamed)).isEqualTo(ApplyCodeChangeResult.APPLIED);

        assertThat(editor.text).isEqualTo(both);
    }

    @Test
    void testSecondProposalThatClashesWithAnAppliedChangeIsRefused() {
        // The second change rewrites the line the first changed, so it can't be put on top of it.
        final String clashing = CODE.replace("select=\"Email\"", "select=\"upper-case(Email)\"");
        final FakeEditor editor = new FakeEditor(CODE);
        final CodeChangeApplier applier = new CodeChangeApplier(editor);

        assertThat(applier.apply(CODE, PROPOSED)).isEqualTo(ApplyCodeChangeResult.APPLIED);
        assertThat(applier.apply(CODE, clashing)).isEqualTo(ApplyCodeChangeResult.CODE_CHANGED);

        assertThat(editor.text).isEqualTo(PROPOSED);
    }

    @Test
    void testThirdProposalAgainstTheOriginalCodeIsAppliedOnTopOfBoth() {
        final String renamed = CODE.replace("match=\"User\"", "match=\"Person\"");
        final String commented = CODE.replace("</xsl:stylesheet>", "<!-- done -->\n</xsl:stylesheet>");
        final FakeEditor editor = new FakeEditor(CODE);
        final CodeChangeApplier applier = new CodeChangeApplier(editor);

        assertThat(applier.apply(CODE, PROPOSED)).isEqualTo(ApplyCodeChangeResult.APPLIED);
        assertThat(applier.apply(CODE, renamed)).isEqualTo(ApplyCodeChangeResult.APPLIED);
        assertThat(applier.apply(CODE, commented)).isEqualTo(ApplyCodeChangeResult.APPLIED);

        assertThat(editor.text).isEqualTo(PROPOSED.replace("match=\"User\"", "match=\"Person\"")
                .replace("</xsl:stylesheet>", "<!-- done -->\n</xsl:stylesheet>"));
        // Undo apply still undoes only the last change.
        assertThat(applier.undo()).isTrue();
        assertThat(editor.text).isEqualTo(PROPOSED.replace("match=\"User\"", "match=\"Person\""));
        assertThat(applier.canUndo()).isFalse();
    }

    @Test
    void testSecondProposalIsRefusedOnceTheUserEditedTheAppliedChange() {
        final String renamed = CODE.replace("match=\"User\"", "match=\"Person\"");
        final FakeEditor editor = new FakeEditor(CODE);
        final CodeChangeApplier applier = new CodeChangeApplier(editor);

        assertThat(applier.apply(CODE, PROPOSED)).isEqualTo(ApplyCodeChangeResult.APPLIED);
        editor.text = editor.text + "\n<!-- edited -->";

        assertThat(applier.rebase(CODE, renamed)).isNull();
        assertThat(applier.apply(CODE, renamed)).isEqualTo(ApplyCodeChangeResult.CODE_CHANGED);
        assertThat(editor.text).isEqualTo(PROPOSED + "\n<!-- edited -->");
    }

    @Test
    void testUndoneChangeIsNotBuiltOn() {
        final String renamed = CODE.replace("match=\"User\"", "match=\"Person\"");
        final String other = PROPOSED.replace("</xsl:stylesheet>", "<!-- done -->\n</xsl:stylesheet>");
        final FakeEditor editor = new FakeEditor(CODE);
        final CodeChangeApplier applier = new CodeChangeApplier(editor);

        assertThat(applier.apply(CODE, PROPOSED)).isEqualTo(ApplyCodeChangeResult.APPLIED);
        assertThat(applier.undo()).isTrue();

        // Proposed against the code the undone change made, which is no longer there.
        assertThat(applier.rebase(PROPOSED, other)).isNull();
        // Proposed against the code there now.
        assertThat(applier.rebase(CODE, renamed)).isEqualTo(renamed);
    }


    // --------------------------------------------------------------------------------


    // Applies replacements the way the real editor does, through TextEdits.
    private static final class FakeEditor implements CodeChangeApplier.CodeEditor {

        private String text;
        private boolean ready = true;
        private Runnable onChange;
        private final List<List<LineReplacement>> replacementCalls = new ArrayList<>();
        private final List<List<int[]>> markers = new ArrayList<>();

        private FakeEditor(final String text) {
            this.text = text;
        }

        @Override
        public String getCode() {
            return text;
        }

        @Override
        public boolean applyLineReplacements(final List<LineReplacement> replacements) {
            if (!ready) {
                return false;
            }
            replacementCalls.add(new ArrayList<>(replacements));
            final List<String> lines = LineDiff.splitLines(text);
            final List<TextEdit> edits = TextEdits.fromLineReplacements(lines, replacements);
            text = TextEdits.apply(lines, edits);
            if (onChange != null) {
                for (int i = 0; i < edits.size(); i++) {
                    onChange.run();
                }
            }
            return true;
        }

        @Override
        public void setChangedLineMarkers(final List<int[]> ranges) {
            markers.add(new ArrayList<>(Arrays.asList(ranges.toArray(new int[0][]))));
        }
    }
}
