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

import stroom.ai.shared.AiSteppingContext;
import stroom.ai.shared.AiSteppingElement;
import stroom.ai.shared.AiSteppingError;
import stroom.ai.shared.AiTabSnapshot;
import stroom.ai.shared.AiTabSnapshotPart;
import stroom.docref.DocRef;
import stroom.pipeline.stepping.client.presenter.SteppingSnapshots.ElementState;
import stroom.pipeline.stepping.client.presenter.SteppingSnapshots.SelectedElement;
import stroom.pipeline.stepping.client.presenter.SteppingSnapshots.StepPosition;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.ElementId;
import stroom.util.shared.ErrorType;
import stroom.util.shared.Indicators;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/// The snapshot of a stepping screen that the AI chat is given: the step's location, the pipeline's elements with
/// their documents and error counts, and the selected element's panes as displayed, code first.
class TestSteppingSnapshots {

    private static final DocRef PIPELINE = new DocRef("Pipeline", "p-uuid", "Acme-Events");
    private static final DocRef XSLT = new DocRef("XSLT", "x-uuid", "Event translation");
    private static final DocRef CONVERTER = new DocRef("TextConverter", "t-uuid", "CSV");
    private static final StepPosition POSITION = new StepPosition("ACME", 1234L, "Raw Events", null, 0L, 4L);

    @Test
    void theSelectedElementIsSharedInFullAndTheOthersSummarised() {
        final Indicators indicators = new Indicators();
        indicators.add(new StoredError(Severity.ERROR, new DefaultLocation(12, 5), new ElementId("xsltFilter"),
                "Unknown function", ErrorType.CODE));
        indicators.add(new StoredError(Severity.WARNING, null, new ElementId("xsltFilter"), "Empty output",
                ErrorType.OUTPUT));
        final Indicators parserIndicators = new Indicators();
        parserIndicators.add(new StoredError(Severity.FATAL_ERROR, null, new ElementId("dsParser"), "Bad input",
                ErrorType.INPUT));

        final AiTabSnapshot snapshot = SteppingSnapshots.create(PIPELINE, true, POSITION, List.of(
                        new ElementState("dsParser", "DSParser", CONVERTER, true, false, parserIndicators),
                        new ElementState("xsltFilter", "XSLTFilter", XSLT, true, true, indicators),
                        new ElementState("writer", "XMLWriter", null, false, false, null)),
                new SelectedElement("xsltFilter", "<records/>", "<Events/>", "<xsl:stylesheet/>"),
                List.of("Ended early"));

        assertThat(snapshot.getDocRef()).isEqualTo(PIPELINE);
        assertThat(snapshot.getInnerTab()).isEqualTo(SteppingSnapshots.INNER_TAB);
        assertThat(snapshot.isDirty()).isTrue();
        assertThat(snapshot.getHash()).isNotBlank();
        // The code first, so it is the last to be cut for size.
        assertThat(snapshot.getParts())
                .extracting(AiTabSnapshotPart::getName, AiTabSnapshotPart::getMediaType, AiTabSnapshotPart::getText)
                .containsExactly(
                        tuple(AiTabSnapshotPart.STEP_CODE, SteppingSnapshots.XSLT, "<xsl:stylesheet/>"),
                        tuple(AiTabSnapshotPart.STEP_OUTPUT, SteppingSnapshots.XML, "<Events/>"),
                        tuple(AiTabSnapshotPart.STEP_INPUT, SteppingSnapshots.XML, "<records/>"));

        final AiSteppingContext stepping = snapshot.getStepping();
        assertThat(stepping.getFeedName()).isEqualTo("ACME");
        assertThat(stepping.getMetaId()).isEqualTo(1234L);
        // One based, as the stepping screen shows them.
        assertThat(stepping.getPartNo()).isEqualTo(1L);
        assertThat(stepping.getRecordNo()).isEqualTo(5L);
        assertThat(stepping.getSelectedElementId()).isEqualTo("xsltFilter");
        assertThat(stepping.getGeneralErrors()).containsExactly("Ended early");
        assertThat(stepping.getElements())
                .extracting(AiSteppingElement::getId, AiSteppingElement::getDocRef, AiSteppingElement::isDirty,
                        AiSteppingElement::getErrorCount, AiSteppingElement::getWarningCount)
                .containsExactly(
                        tuple("dsParser", CONVERTER, false, 1, 0),
                        tuple("xsltFilter", XSLT, true, 1, 1),
                        tuple("writer", null, false, 0, 0));
        // Only the selected element's messages are given.
        assertThat(stepping.getElements().get(0).getErrors()).isEmpty();
        assertThat(stepping.getSelectedElement().getErrors()).containsExactly(
                new AiSteppingError("ERROR", "CODE", 12, 5, "Unknown function"),
                new AiSteppingError("WARNING", "OUTPUT", null, null, "Empty output"));
    }

    @Test
    void withTheSourceSelectedNoPanesAreShared() {
        final AiTabSnapshot snapshot = SteppingSnapshots.create(PIPELINE, false, null, List.of(
                new ElementState("xsltFilter", "XSLTFilter", XSLT, true, false, null)), null, List.of());

        assertThat(snapshot.getParts()).isEmpty();
        assertThat(snapshot.getStepping().getSelectedElementId()).isNull();
        assertThat(snapshot.getStepping().getMetaId()).isNull();
        assertThat(snapshot.getStepping().getElements()).hasSize(1);
    }

    @Test
    void anElementWithNoCodeOrOutputSharesOnlyWhatItHas() {
        final AiTabSnapshot snapshot = SteppingSnapshots.create(PIPELINE, false, POSITION, List.of(
                        new ElementState("dsParser", "DSParser", CONVERTER, true, false, null)),
                new SelectedElement("dsParser", "a,b,c", null, null), List.of());

        assertThat(snapshot.getParts())
                .extracting(AiTabSnapshotPart::getName, AiTabSnapshotPart::getMediaType)
                .containsExactly(tuple(AiTabSnapshotPart.STEP_INPUT, SteppingSnapshots.TEXT));
    }

    @Test
    void theHashChangesWithWhatTheSteppingScreenShows() {
        final List<ElementState> elements = List.of(
                new ElementState("xsltFilter", "XSLTFilter", XSLT, true, false, null));
        final SelectedElement selected = new SelectedElement("xsltFilter", "<a/>", "<b/>", "<xsl/>");
        final String hash = SteppingSnapshots.create(PIPELINE, false, POSITION, elements, selected, List.of())
                .getHash();

        assertThat(SteppingSnapshots.create(PIPELINE, false, POSITION, elements, selected, List.of()).getHash())
                .isEqualTo(hash);
        // Another record.
        assertThat(SteppingSnapshots.create(PIPELINE, false,
                new StepPosition("ACME", 1234L, "Raw Events", null, 0L, 5L), elements, selected, List.of())
                .getHash()).isNotEqualTo(hash);
        // A general error.
        assertThat(SteppingSnapshots.create(PIPELINE, false, POSITION, elements, selected, List.of("Oops"))
                .getHash()).isNotEqualTo(hash);
        // Edited code.
        assertThat(SteppingSnapshots.create(PIPELINE, false, POSITION, elements,
                new SelectedElement("xsltFilter", "<a/>", "<b/>", "<xsl />"), List.of()).getHash())
                .isNotEqualTo(hash);
    }

    @Test
    void mediaTypes() {
        assertThat(SteppingSnapshots.mediaType("  <a/>")).isEqualTo(SteppingSnapshots.XML);
        assertThat(SteppingSnapshots.mediaType("a,b")).isEqualTo(SteppingSnapshots.TEXT);
        assertThat(SteppingSnapshots.mediaType(null)).isEqualTo(SteppingSnapshots.TEXT);
        assertThat(SteppingSnapshots.codeMediaType("XSLT")).isEqualTo(SteppingSnapshots.XSLT);
        assertThat(SteppingSnapshots.codeMediaType("TextConverter")).isEqualTo(SteppingSnapshots.XML);
        assertThat(SteppingSnapshots.codeMediaType(null)).isEqualTo(SteppingSnapshots.TEXT);
    }
}
