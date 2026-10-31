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

package stroom.ai.impl;

import stroom.ai.shared.AiSteppingContext;
import stroom.ai.shared.AiSteppingElement;
import stroom.ai.shared.AiSteppingError;
import stroom.docref.DocRef;
import stroom.util.shared.NullSafe;

import java.util.List;

/// Describes what a pipeline's stepping screen shows, for the model: where the step is, the pipeline's elements
/// with the documents they use and their error counts, and the selected element's messages.
public final class SteppingDescriptions {

    /// The most messages listed for the selected element, and general errors.
    static final int MAX_MESSAGES = 200;
    /// The most characters of a message listed.
    static final int MAX_MESSAGE_CHARS = 1_000;

    private SteppingDescriptions() {
        // Static methods only.
    }

    /// @param stepping What the stepping screen shows.
    /// @return Where the step is, e.g. "stream 1234 (feed ACME-EVENTS, Raw Events), part 1, record 5", or
    /// "no stream selected yet".
    public static String location(final AiSteppingContext stepping) {
        if (stepping.getMetaId() == null) {
            return "no stream selected yet";
        }
        final StringBuilder sb = new StringBuilder("stream ").append(stepping.getMetaId());
        final StringBuilder details = new StringBuilder();
        if (NullSafe.isNonBlankString(stepping.getFeedName())) {
            details.append("feed ").append(stepping.getFeedName());
        }
        if (NullSafe.isNonBlankString(stepping.getStreamType())) {
            appendSeparator(details).append(stepping.getStreamType());
        }
        if (NullSafe.isNonBlankString(stepping.getChildStreamType())) {
            appendSeparator(details).append("stepping its ").append(stepping.getChildStreamType());
        }
        if (!details.isEmpty()) {
            sb.append(" (").append(details).append(')');
        }
        if (stepping.getPartNo() != null) {
            sb.append(", part ").append(stepping.getPartNo());
        }
        if (stepping.getRecordNo() != null) {
            sb.append(", record ").append(stepping.getRecordNo());
        }
        return sb.toString();
    }

    /// @param element An element.
    /// @return The element, for the model, e.g. "'xsltFilter' (XSLTFilter, XSLT 'Event translation', unsaved)".
    public static String element(final AiSteppingElement element) {
        final StringBuilder sb = new StringBuilder("'").append(element.getId()).append("' (")
                .append(NullSafe.nonBlankStringElse(element.getType(), "?"));
        if (element.getDocRef() != null) {
            sb.append(", ").append(doc(element.getDocRef()));
        }
        if (element.isDirty()) {
            sb.append(", unsaved changes");
        }
        return sb.append(')').toString();
    }

    /// @param pipelineName The pipeline's name.
    /// @param stepping     What the stepping screen shows.
    /// @return A Markdown description of the step: where it is, the pipeline's elements in order, and the
    /// messages of the selected element and the step as a whole.
    public static String structure(final String pipelineName, final AiSteppingContext stepping) {
        final StringBuilder sb = new StringBuilder("# Stepping pipeline '")
                .append(NullSafe.nonBlankStringElse(pipelineName, "?")).append("'\n\n");
        sb.append("At ").append(location(stepping)).append(".\n\n");
        final AiSteppingElement selected = stepping.getSelectedElement();
        sb.append(selected == null
                ? "The source is selected, so no element's panes are shared.\n\n"
                : "Selected element: " + element(selected) + ".\n\n");

        sb.append("## Elements, in order from the source\n\n");
        final List<AiSteppingElement> elements = stepping.getElements();
        if (elements.isEmpty()) {
            sb.append("None.\n\n");
        } else {
            sb.append("| Element | Type | Document | Errors | Warnings |\n");
            sb.append("|---|---|---|---|---|\n");
            for (final AiSteppingElement element : elements) {
                sb.append("| ").append(cell(element.getId()));
                if (element == selected) {
                    sb.append(" (selected)");
                }
                sb.append(" | ").append(cell(element.getType()))
                        .append(" | ").append(element.getDocRef() == null
                                ? ""
                                : cell(doc(element.getDocRef())) + (element.isDirty()
                                        ? " (unsaved)"
                                        : ""))
                        .append(" | ").append(element.getErrorCount())
                        .append(" | ").append(element.getWarningCount())
                        .append(" |\n");
            }
            sb.append('\n');
        }

        if (selected != null) {
            sb.append("## Messages for '").append(selected.getId()).append("'\n\n");
            appendErrors(sb, selected.getErrors());
        }
        if (!stepping.getGeneralErrors().isEmpty()) {
            sb.append("## Errors for the step as a whole\n\n");
            int count = 0;
            for (final String error : stepping.getGeneralErrors()) {
                if (count++ >= MAX_MESSAGES) {
                    sb.append("- … and ").append(stepping.getGeneralErrors().size() - MAX_MESSAGES)
                            .append(" more\n");
                    break;
                }
                sb.append("- ").append(shorten(error)).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString().stripTrailing() + "\n";
    }

    private static void appendErrors(final StringBuilder sb, final List<AiSteppingError> errors) {
        if (errors.isEmpty()) {
            sb.append("None.\n\n");
            return;
        }
        int count = 0;
        for (final AiSteppingError error : errors) {
            if (count++ >= MAX_MESSAGES) {
                sb.append("- … and ").append(errors.size() - MAX_MESSAGES).append(" more\n");
                break;
            }
            sb.append("- ").append(NullSafe.nonBlankStringElse(error.getSeverity(), "MESSAGE"));
            if (NullSafe.isNonBlankString(error.getPane())) {
                sb.append(" in the ").append(paneName(error.getPane()));
            }
            if (error.getLineNo() != null) {
                sb.append(" at line ").append(error.getLineNo());
                if (error.getColNo() != null) {
                    sb.append(", column ").append(error.getColNo());
                }
            }
            sb.append(": ").append(shorten(error.getMessage())).append('\n');
        }
        sb.append('\n');
    }

    private static String paneName(final String pane) {
        return switch (pane) {
            case "CODE" -> "code";
            case "INPUT" -> "input";
            case "OUTPUT" -> "output";
            default -> "element";
        };
    }

    private static String doc(final DocRef docRef) {
        return docRef.getType() + " '" + NullSafe.nonBlankStringElse(docRef.getName(), "?") + "' (uuid "
               + docRef.getUuid() + ")";
    }

    private static String cell(final String text) {
        return NullSafe.string(text).replace("|", "\\|").replace('\n', ' ');
    }

    private static String shorten(final String text) {
        final String value = NullSafe.string(text).strip();
        return value.length() > MAX_MESSAGE_CHARS
                ? value.substring(0, MAX_MESSAGE_CHARS) + "…"
                : value;
    }

    private static StringBuilder appendSeparator(final StringBuilder sb) {
        if (!sb.isEmpty()) {
            sb.append(", ");
        }
        return sb;
    }
}
