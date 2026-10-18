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

import stroom.util.shared.diff.DiffHunk;
import stroom.util.shared.diff.DiffLine;
import stroom.util.shared.diff.DiffType;
import stroom.util.shared.diff.LineDiffResult;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import java.util.List;

/// Renders the body of a compact diff card for a proposed code change, as shown in the chat: a summary
/// of the line counts, then only the changed hunks with a few lines of context, removed lines red and
/// added lines green. Long diffs show the first few hunks and fold the rest away behind a count.
public final class CodeDiffCardRenderer {

    /// The number of hunks shown before the rest are folded away.
    public static final int DEFAULT_VISIBLE_HUNKS = 3;

    private static final String MINUS = "−";
    private static final SafeHtml DETAILS = SafeHtmlUtils.fromSafeConstant("details");
    private static final SafeHtml SUMMARY = SafeHtmlUtils.fromSafeConstant("summary");

    private CodeDiffCardRenderer() {
        // Utility class.
    }

    /// A short summary of the line counts, e.g. "+3 −1 lines", or "No changes" if there are none.
    ///
    /// @param diff The diff.
    /// @return The summary.
    public static String summary(final LineDiffResult diff) {
        if (diff.isIdentical()) {
            return "No changes";
        }
        return "+" + diff.getAddedLineCount() + " " + MINUS + diff.getRemovedLineCount() + " lines";
    }

    /// Renders the card body with [LineDiffResult#DEFAULT_CONTEXT_LINES] lines of context and
    /// [#DEFAULT_VISIBLE_HUNKS] hunks before folding.
    ///
    /// @param diff The diff.
    /// @return The HTML.
    public static SafeHtml render(final LineDiffResult diff) {
        return render(diff, LineDiffResult.DEFAULT_CONTEXT_LINES, DEFAULT_VISIBLE_HUNKS);
    }

    /// Renders the card body.
    ///
    /// @param diff          The diff.
    /// @param contextLines  The number of unchanged lines to show around each change.
    /// @param visibleHunks  The number of hunks to show before folding the rest away.
    /// @return The HTML.
    public static SafeHtml render(final LineDiffResult diff,
                                  final int contextLines,
                                  final int visibleHunks) {
        final HtmlBuilder hb = new HtmlBuilder();
        final List<DiffHunk> hunks = diff.getHunks(contextLines);
        hb.div(card -> {
            card.div(summary(diff), Attribute.className("codeDiffCard-summary"));
            final int shown = Math.min(hunks.size(), Math.max(1, visibleHunks));
            for (int i = 0; i < shown; i++) {
                appendHunk(card, hunks.get(i));
            }
            final int folded = hunks.size() - shown;
            if (folded > 0) {
                card.elem(details -> {
                    details.elem(foldedSummary(folded), SUMMARY, Attribute.className("codeDiffCard-moreSummary"));
                    for (int i = shown; i < hunks.size(); i++) {
                        appendHunk(details, hunks.get(i));
                    }
                }, DETAILS, Attribute.className("codeDiffCard-more"));
            }
        }, Attribute.className("codeDiffCard"));
        return hb.toSafeHtml();
    }

    /// @param folded The number of hunks folded away.
    /// @return The label for the folded hunks, e.g. "2 more changes".
    static String foldedSummary(final int folded) {
        return folded == 1
                ? "1 more change"
                : folded + " more changes";
    }

    private static void appendHunk(final HtmlBuilder hb, final DiffHunk hunk) {
        hb.div(hunkBuilder -> {
            hunkBuilder.div(hunk.getHeader(), Attribute.className("codeDiffCard-header"));
            final List<DiffLine> lines = hunk.getLines();
            int i = 0;
            while (i < lines.size()) {
                if (lines.get(i).getType() == DiffType.EQUAL) {
                    final DiffLine line = lines.get(i);
                    appendLine(hunkBuilder, "codeDiffCard-line--context", " ",
                            line.getOldIndex(), line.getNewIndex(), line.getOldText());
                    i++;
                } else {
                    // As in a unified diff, all of a change's removed lines come before its added ones.
                    final int changeStart = i;
                    while (i < lines.size() && lines.get(i).getType() != DiffType.EQUAL) {
                        i++;
                    }
                    for (int j = changeStart; j < i; j++) {
                        final DiffLine line = lines.get(j);
                        if (line.hasOld()) {
                            appendLine(hunkBuilder, "codeDiffCard-line--removed", MINUS,
                                    line.getOldIndex(), -1, line.getOldText());
                        }
                    }
                    for (int j = changeStart; j < i; j++) {
                        final DiffLine line = lines.get(j);
                        if (line.hasNew()) {
                            appendLine(hunkBuilder, "codeDiffCard-line--added", "+",
                                    -1, line.getNewIndex(), line.getNewText());
                        }
                    }
                }
            }
        }, Attribute.className("codeDiffCard-hunk"));
    }

    private static void appendLine(final HtmlBuilder hb,
                                   final String lineClass,
                                   final String marker,
                                   final int oldIndex,
                                   final int newIndex,
                                   final String text) {
        hb.div(line -> {
            line.span(lineNumber(oldIndex), Attribute.className("codeDiffCard-lineNo"));
            line.span(lineNumber(newIndex), Attribute.className("codeDiffCard-lineNo"));
            line.span(marker, Attribute.className("codeDiffCard-marker"));
            line.span(text, Attribute.className("codeDiffCard-text"));
        }, Attribute.className("codeDiffCard-line " + lineClass));
    }

    private static String lineNumber(final int index) {
        return index < 0
                ? ""
                : String.valueOf(index + 1);
    }
}
