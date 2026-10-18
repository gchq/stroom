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

import stroom.ai.client.AiTabSnapshots;
import stroom.ai.shared.AiSteppingContext;
import stroom.ai.shared.AiSteppingElement;
import stroom.ai.shared.AiSteppingError;
import stroom.ai.shared.AiTabSnapshot;
import stroom.ai.shared.AiTabSnapshotPart;
import stroom.docref.DocRef;
import stroom.util.shared.Indicators;
import stroom.util.shared.Location;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// Builds the snapshot of a pipeline's stepping screen that the AI chat is given: where the step is, the
/// pipeline's elements with their documents and error counts, and for the selected element its input and output
/// panes as displayed, its code as edited and its messages.
///
/// This holds nothing from the browser, so it can be unit tested.
public final class SteppingSnapshots {

    /// The name of the inner tab the snapshot says is shown.
    public static final String INNER_TAB = "Stepping";

    static final String XML = "application/xml";
    static final String XSLT = "application/xslt+xml";
    static final String TEXT = "text/plain";

    private SteppingSnapshots() {
        // Static methods only.
    }

    /// Makes the snapshot of a stepping screen, with its hash.
    ///
    /// @param pipeline      The pipeline being stepped.
    /// @param dirty         True if the pipeline or any element's code has unsaved changes.
    /// @param location      Where the step is.
    /// @param elements      The pipeline's elements, in order from the source.
    /// @param selected      The selected element's panes, or null if the source (or nothing) is selected.
    /// @param generalErrors Errors from the step that aren't about one element.
    /// @return The snapshot.
    public static AiTabSnapshot create(final DocRef pipeline,
                                       final boolean dirty,
                                       final StepPosition location,
                                       final List<ElementState> elements,
                                       final SelectedElement selected,
                                       final List<String> generalErrors) {
        final String selectedId = NullSafe.get(selected, SelectedElement::id);
        final List<AiSteppingElement> steppingElements = new ArrayList<>();
        ElementState selectedState = null;
        for (final ElementState element : NullSafe.list(elements)) {
            final boolean isSelected = element.id() != null && element.id().equals(selectedId);
            if (isSelected) {
                selectedState = element;
            }
            steppingElements.add(toElement(element, isSelected));
        }

        final List<AiTabSnapshotPart> parts = new ArrayList<>();
        if (selected != null) {
            // The code first, as it is what a change is made to, so it is the last to be cut for size.
            if (selected.code() != null) {
                parts.add(new AiTabSnapshotPart(AiTabSnapshotPart.STEP_CODE,
                        codeMediaType(NullSafe.get(selectedState, ElementState::docRef, DocRef::getType)),
                        selected.code()));
            }
            if (selected.output() != null) {
                parts.add(new AiTabSnapshotPart(AiTabSnapshotPart.STEP_OUTPUT, mediaType(selected.output()),
                        selected.output()));
            }
            if (selected.input() != null) {
                parts.add(new AiTabSnapshotPart(AiTabSnapshotPart.STEP_INPUT, mediaType(selected.input()),
                        selected.input()));
            }
        }

        final StepPosition position = location == null
                ? new StepPosition(null, null, null, null, null, null)
                : location;
        final AiSteppingContext stepping = new AiSteppingContext(
                position.feedName(),
                position.metaId(),
                position.streamType(),
                position.childStreamType(),
                NullSafe.get(position.partIndex(), index -> index + 1),
                NullSafe.get(position.recordIndex(), index -> index + 1),
                selectedState == null
                        ? null
                        : selectedId,
                steppingElements,
                NullSafe.list(generalErrors));
        return AiTabSnapshots.create(pipeline,
                NullSafe.get(pipeline, DocRef::getName),
                INNER_TAB,
                dirty,
                parts,
                Collections.emptyList(),
                null,
                stepping);
    }

    /// @param text A pane's text.
    /// @return Its media type: XML if it looks like XML, otherwise plain text.
    static String mediaType(final String text) {
        final String start = NullSafe.string(text).stripLeading();
        return start.startsWith("<")
                ? XML
                : TEXT;
    }

    /// @param docType The type of the document whose code it is, or null.
    /// @return The media type of its code.
    static String codeMediaType(final String docType) {
        if ("XSLT".equals(docType)) {
            return XSLT;
        } else if ("TextConverter".equals(docType)) {
            return XML;
        }
        return TEXT;
    }

    /// @param error A message a step raised.
    /// @return The message, as the chat is given it.
    static AiSteppingError toError(final StoredError error) {
        final Location location = error.getLocation();
        final Integer lineNo = location == null || location.getLineNo() <= 0
                ? null
                : location.getLineNo();
        final Integer colNo = location == null || location.getColNo() <= 0
                ? null
                : location.getColNo();
        return new AiSteppingError(
                NullSafe.get(error.getSeverity(), Severity::name),
                NullSafe.get(error.getErrorType(), Enum::name),
                lineNo,
                colNo,
                error.getMessage());
    }

    private static AiSteppingElement toElement(final ElementState element, final boolean selected) {
        final Indicators indicators = element.indicators();
        final int errors = indicators == null
                ? 0
                : indicators.getCount(Severity.ERROR) + indicators.getCount(Severity.FATAL_ERROR);
        final int warnings = indicators == null
                ? 0
                : indicators.getCount(Severity.WARNING);
        List<AiSteppingError> messages = null;
        if (selected) {
            messages = new ArrayList<>();
            if (indicators != null) {
                for (final StoredError error : indicators.getErrorList()) {
                    if (error != null) {
                        messages.add(toError(error));
                    }
                }
            }
        }
        return new AiSteppingElement(element.id(), element.type(), element.docRef(), element.hasCode(),
                element.dirty(), errors, warnings, messages);
    }


    // --------------------------------------------------------------------------------


    /// Where a step is.
    ///
    /// @param feedName        The stream's feed, or null.
    /// @param metaId          The stream's id, or null if none is selected.
    /// @param streamType      The stream's type, or null.
    /// @param childStreamType The child stream type being stepped, or null.
    /// @param partIndex       The zero based part, or null.
    /// @param recordIndex     The zero based record within the part, or null.
    public record StepPosition(String feedName,
                               Long metaId,
                               String streamType,
                               String childStreamType,
                               Long partIndex,
                               Long recordIndex) {

    }

    /// An element of the pipeline, as the stepping screen has it.
    ///
    /// @param id         The element's id.
    /// @param type       The element's type.
    /// @param docRef     The document it uses, or null.
    /// @param hasCode    True if it has code shown in an editor.
    /// @param dirty      True if its code has unsaved changes.
    /// @param indicators What the current step raised for it, or null.
    public record ElementState(String id,
                               String type,
                               DocRef docRef,
                               boolean hasCode,
                               boolean dirty,
                               Indicators indicators) {

    }

    /// The selected element's panes, as displayed.
    ///
    /// @param id     The element's id.
    /// @param input  Its input pane's text, or null if it has none.
    /// @param output Its output pane's text, or null if it has none.
    /// @param code   Its code as edited, or null if it has none (or it hasn't loaded).
    public record SelectedElement(String id,
                                  String input,
                                  String output,
                                  String code) {

    }
}
