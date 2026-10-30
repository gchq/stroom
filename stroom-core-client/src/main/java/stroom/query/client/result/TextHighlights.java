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

package stroom.query.client.result;

import stroom.util.shared.DefaultLocation;
import stroom.util.shared.NullSafe;
import stroom.util.shared.TextRange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/// Finds where a selected row's highlight terms are in the source text a text pane shows (a
/// dashboard's Text pane or a query's), ignoring case and skipping markup (`<...>` and
/// `&lt;...&gt;`).
public final class TextHighlights {

    private TextHighlights() {
        // Static methods only
    }

    /// Finds the highlights. The ranges are line/col positions in the input text, so the text
    /// must not be changed or formatted after they are found.
    ///
    /// @param input            The text, or `null`.
    /// @param highlightStrings The terms to find, or `null` or empty for none.
    /// @return The ranges found (one based and inclusive), in order; empty for none.
    public static List<TextRange> find(final String input, final Set<String> highlightStrings) {
        final List<TextRange> highlights = new ArrayList<>();

        // See if we are going to add highlights.
        if (input != null && NullSafe.hasItems(highlightStrings)) {
            final char[] inputChars = input.toLowerCase().toCharArray();
            final int inputLength = inputChars.length;

            // Find out where the highlight elements need to be placed.
            for (final String highlight : highlightStrings) {
                if (highlight == null) {
                    continue;
                }

                final char[] highlightChars = highlight.toLowerCase().toCharArray();
                final int highlightLength = highlightChars.length;

                boolean inElementTag = false;
                boolean inEscapedElement = false;
                int lineNo = 1;
                int colNo = 1;
                char lastInputChar = 0;
                for (int i = 0; i < inputLength; i++) {
                    final char inputChar = inputChars[i];

                    if (lastInputChar == '\n') {
                        lineNo++;
                        colNo = 1;
                    }
                    lastInputChar = inputChar;

                    if (!inElementTag && !inEscapedElement) {
                        if (inputChar == '<') {
                            inElementTag = true;
                        } else if (inputChar == '&'
                                   && i + 3 < inputLength
                                   && inputChars[i + 1] == 'l'
                                   && inputChars[i + 2] == 't'
                                   && inputChars[i + 3] == ';') {
                            inEscapedElement = true;
                        } else {
                            // If we aren't in an element or escaped element
                            // then try to match.
                            boolean found = false;
                            for (int j = 0; j < highlightLength && i + j < inputLength; j++) {
                                final char highlightChar = highlightChars[j];
                                if (inputChars[i + j] != highlightChar) {
                                    break;
                                } else if (j == highlightLength - 1) {
                                    found = true;
                                }
                            }

                            if (found) {
                                // All one based and inclusive
                                final TextRange hl = new TextRange(
                                        new DefaultLocation(lineNo, colNo),
                                        new DefaultLocation(lineNo, colNo + highlightLength - 1)); // inclusive
                                highlights.add(hl);

                                // Skip over the found highlight term
                                i += highlightLength;
                                colNo += highlightLength;
                            }
                        }
                    } else if (inElementTag && inputChar == '>') {
                        inElementTag = false;

                    } else if (inEscapedElement
                               && inputChar == '&'
                               && i + 3 < inputLength
                               && inputChars[i + 1] == 'g'
                               && inputChars[i + 2] == 't'
                               && inputChars[i + 3] == ';') {
                        inEscapedElement = false;
                    }

                    colNo++;
                }
            }
        }

        Collections.sort(highlights);

        return highlights;
    }
}
