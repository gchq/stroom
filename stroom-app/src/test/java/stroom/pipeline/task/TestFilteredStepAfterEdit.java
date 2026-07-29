/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.pipeline.task;

import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.SteppingFilterSettings;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.stepping.SteppingService;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.shared.OutputState;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A filtered step taken <b>after an edit</b>, which is the case the windowed on-demand scan exists for.
 * <p>
 * Navigating under a filter cannot be answered from the upstream range alone: whether a record matches depends
 * on the <em>edited</em> element's output, which for a mid-stream record has not been produced yet. The scan
 * therefore materialises records ahead of the current position a window at a time and evaluates the filter
 * against what it just wrote. The failure this guards against is the permissive one - treating a record whose
 * filtered element was never materialised as a match, which lands the user on a record that does not match and
 * shows a blank pane.
 * <p>
 * The edit is injected as {@code code} for {@code translationFilter}, so the element under filter is one the
 * sample pipeline already wires up. Injecting code (rather than adding a new element) also means the
 * fingerprint changes exactly as it does when a user types, which is what routes the step away from the
 * completed sweep.
 * <p>
 * {@code stroom:record-no()} returns a <b>string</b> ({@code RecordNo} declares {@code OPTIONAL_STRING}), so it
 * must be cast before arithmetic. Left uncast, {@code mod} is a static type error, the stylesheet fails to
 * compile, and {@code XsltFilter} - which keeps {@code passThrough} on while stepping - silently forwards its
 * input unchanged. That looks exactly like a working filter matching everything, so
 * {@link #theProbeXsltIsActuallyApplied()} pins it down before any filtering is asserted.
 */
class TestFilteredStepAfterEdit extends TranslationTest {

    private static final AtomicBoolean DONE_SETUP = new AtomicBoolean();

    private static final String FEED = "XML-EVENTS";
    private static final String FILTERED_ELEMENT_ID = "translationFilter";

    /** The sample stream holds 10 records, so record-nos 3, 6 and 9 emit a Hit and the rest do not. */
    private static final int HIT_EVERY = 3;
    /** The first matching record, as an index: record-no 3. */
    private static final long FIRST_HIT_INDEX = 2;

    /**
     * Replaces the translation with something whose output is trivially predictable per record: a {@code Probe}
     * root that is childless except on every third record. "Has children" is what
     * {@link OutputState#NOT_EMPTY} tests (the persisted form of the recorder's {@code maxElementDepth > 1}),
     * so this gives a filter that matches a known, sparse set of records.
     */
    private static final String PROBE_XSLT = """
            <?xml version="1.0" encoding="UTF-8" ?>
            <xsl:stylesheet version="2.0"
                            xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                            xmlns:stroom="stroom">
              <xsl:template match="/">
                <Probe>
                  <xsl:if test="number(stroom:record-no()) mod %d = 0">
                    <Hit><xsl:value-of select="stroom:record-no()"/></Hit>
                  </xsl:if>
                </Probe>
              </xsl:template>
            </xsl:stylesheet>
            """.formatted(HIT_EVERY);

    @Inject
    private SteppingService steppingService;
    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private DocFinder docFinder;

    /**
     * {@code testTranslationTask} belongs here rather than in a test body because it can only run <b>once</b>
     * per class: a second call finds the processor tasks the first one queued and fails asserting there is
     * exactly one. Guarded like the rest of the setup, it lets this class hold more than a single test.
     * {@code cleanupBetweenTests} is false, so the data it loads survives between them.
     */
    @BeforeEach
    void setup() {
        if (!DONE_SETUP.get()) {
            importConfig();
            loadAllRefData();
            testTranslationTask(FEED, false, false);
            DONE_SETUP.set(true);
        }
    }

    @Override
    protected boolean cleanupBetweenTests() {
        return false;
    }

    /**
     * The fixture's own guard. A filter test built on a probe that is not actually running would pass for the
     * wrong reason - pass-through output is non-empty for every record, so a NOT_EMPTY filter would "match"
     * everywhere and any landing looks like a hit. So: prove the probe transforms, prove it compiled cleanly,
     * and prove it discriminates between records, before {@link #aFilteredStepAfterAnEditLandsOnAMatch()}
     * relies on any of that.
     */
    @Test
    void aFilteredStepAfterAnEditLandsOnAMatch() {
        final PipelineStepRequest base = baseRequest();
        SteppingResult last = null;
        try {
            last = steppingService.step(base.copy().stepType(StepType.LAST).build());
            assertThat(last.isFoundRecord()).as("LAST found a record").isTrue();
            final StepLocation found = last.getFoundLocation();

            // A non-matching record: record-no 1, so Probe has no children.
            final SharedElementData miss = elementDataAt(base, last, found, 0);
            assertThat(indicatorText(miss))
                    .as("the probe XSLT compiled and ran without error")
                    .isEmpty();
            assertThat(miss.getOutput())
                    .as("the probe transformed its input - if this is Events XML the XSLT was not applied")
                    .contains("<Probe");
            assertThat(miss.getOutput()).as("record-no 1 is not a multiple of " + HIT_EVERY)
                    .doesNotContain("<Hit");
            assertThat(miss.isHasOutput())
                    .as("a childless Probe is what OutputState.NOT_EMPTY must NOT match")
                    .isFalse();

            // A matching record: record-no 3.
            final SharedElementData hit = elementDataAt(base, last, found, FIRST_HIT_INDEX);
            assertThat(hit.getOutput()).as("record-no 3 is a multiple of " + HIT_EVERY)
                    .contains("<Hit>3</Hit>");
            assertThat(hit.isHasOutput())
                    .as("and it is what OutputState.NOT_EMPTY must match")
                    .isTrue();

            // ---- the behaviour under test -----------------------------------------------------------
            // Stepping FORWARD from record 0 under a NOT_EMPTY filter on the edited element must skip the two
            // non-matching records and land on record-no 3, serving output that really does match. Landing on
            // record 1 would mean the scan treated an unmaterialised record as a match.
            final StepLocation record0 = new StepLocation(found.getMetaId(), found.getPartIndex(), 0);

            // Negative control. "Landed on record 2" only means "the filter made it skip" if the same step
            // without a filter lands on record 1. Without this, a test that asserts a landing proves nothing
            // about filtering - it could be asserting ordinary forward navigation.
            final SteppingResult unfiltered = steppingService.step(base.copy()
                    .stepType(StepType.FORWARD)
                    .stepLocation(record0)
                    .sessionUuid(last.getSessionUuid())
                    .code(Map.of(FILTERED_ELEMENT_ID, PROBE_XSLT))
                    .build());
            assertThat(unfiltered.isFoundRecord()).as("the unfiltered FORWARD found a record").isTrue();
            assertThat(unfiltered.getFoundLocation().getRecordIndex())
                    .as("unfiltered, FORWARD from record 0 goes to the very next record")
                    .isEqualTo(1);

            final long onDemandBefore = steppingService.getOnDemandLaunchCount();

            final SteppingResult forward = steppingService.step(base.copy()
                    .stepType(StepType.FORWARD)
                    .stepLocation(record0)
                    .sessionUuid(last.getSessionUuid())
                    .code(Map.of(FILTERED_ELEMENT_ID, PROBE_XSLT))
                    .stepFilterMap(Map.of(FILTERED_ELEMENT_ID,
                            new SteppingFilterSettings(null, OutputState.NOT_EMPTY, List.of())))
                    .build());

            // The mechanism first: a filtered step under an edit must go through the windowed scan, not be
            // answered from the pre-edit sweep. Asserting only the landing would pass even if the step had
            // fallen back to something else that happened to give the same answer.
            assertThat(steppingService.getOnDemandLaunchCount())
                    .as("the filtered step was answered by the on-demand windowed scan, not by reusing the "
                        + "unfiltered step's sweep (it landed on " + forward.getFoundLocation() + ")")
                    .isGreaterThan(onDemandBefore);

            assertThat(forward.isFoundRecord()).as("the filtered FORWARD found a record").isTrue();
            assertThat(forward.getFoundLocation().getMetaId())
                    .as("and stayed in the stream it started in, rather than running off the end")
                    .isEqualTo(found.getMetaId());
            assertThat(forward.getFoundLocation().getRecordIndex())
                    .as("skipping the two records that do not match")
                    .isEqualTo(FIRST_HIT_INDEX);

            // ...and the record it landed on genuinely matches. This is the permissive failure: a record whose
            // filtered element was never materialised must not be taken for a match.
            final SharedElementData served = forward.getStepData().getElementData(FILTERED_ELEMENT_ID);
            assertThat(served).as(FILTERED_ELEMENT_ID + " was served").isNotNull();
            assertThat(served.getOutput())
                    .as("the served output actually satisfies the filter it was selected by")
                    .contains("<Hit>3</Hit>");
        } finally {
            terminate(base, last);
        }
    }

    /**
     * The awkward ends. FIRST and LAST name no record at all, so unlike FORWARD there is no position to scan
     * away from - the scan has to start at an end of the stream and work inwards, and LAST has to do it
     * backwards. An off-by-one in that direction reversal is invisible to a FORWARD test.
     * <p>
     * Both are pinned to a single stream, because FIRST and LAST over a multi-stream selection would be free
     * to answer from a different stream and the record index alone would not say which.
     */
    @Test
    void filteredFirstAndLastLandOnTheOuterMatches() {
        final PipelineStepRequest base = baseRequest();
        SteppingResult probe = null;
        SteppingResult session = null;
        try {
            probe = steppingService.step(base.copy().stepType(StepType.LAST).build());
            assertThat(probe.isFoundRecord()).as("LAST found a record").isTrue();
            final long metaId = probe.getFoundLocation().getMetaId();

            final PipelineStepRequest pinned = pinnedTo(metaId);

            // Negative controls first. Unfiltered, these two are the ends of the stream; if they were already
            // the matching records, the filtered assertions below would prove nothing.
            final SteppingResult firstUnfiltered = steppingService.step(pinned.copy()
                    .stepType(StepType.FIRST)
                    .code(Map.of(FILTERED_ELEMENT_ID, PROBE_XSLT))
                    .build());
            session = firstUnfiltered;
            assertThat(firstUnfiltered.getFoundLocation().getRecordIndex())
                    .as("unfiltered FIRST is record 0").isEqualTo(0);

            final SteppingResult lastUnfiltered = steppingService.step(pinned.copy()
                    .stepType(StepType.LAST)
                    .sessionUuid(firstUnfiltered.getSessionUuid())
                    .code(Map.of(FILTERED_ELEMENT_ID, PROBE_XSLT))
                    .build());
            final long lastIndex = lastUnfiltered.getFoundLocation().getRecordIndex();
            assertThat(lastIndex).as("unfiltered LAST is the end of the stream").isEqualTo(9);

            final long fullSweepsBefore = steppingService.getFullSweepLaunchCount();

            // FIRST must skip forwards over the two leading non-matches.
            final SteppingResult filteredFirst = steppingService.step(pinned.copy()
                    .stepType(StepType.FIRST)
                    .sessionUuid(lastUnfiltered.getSessionUuid())
                    .code(Map.of(FILTERED_ELEMENT_ID, PROBE_XSLT))
                    .stepFilterMap(Map.of(FILTERED_ELEMENT_ID,
                            new SteppingFilterSettings(null, OutputState.NOT_EMPTY, List.of())))
                    .build());
            assertThat(filteredFirst.isFoundRecord()).as("filtered FIRST found a record").isTrue();
            assertThat(filteredFirst.getFoundLocation().getMetaId()).isEqualTo(metaId);
            assertThat(filteredFirst.getFoundLocation().getRecordIndex())
                    .as("filtered FIRST is the first match, not record 0")
                    .isEqualTo(FIRST_HIT_INDEX);
            assertThat(filteredFirst.getStepData().getElementData(FILTERED_ELEMENT_ID).getOutput())
                    .as("and the record it landed on really matches")
                    .contains("<Hit>3</Hit>");

            // LAST must skip backwards over the trailing non-match: record-no 10 is not a multiple of 3, so
            // the answer is record-no 9 - one back from the end, which a forwards scan would never reach.
            final SteppingResult filteredLast = steppingService.step(pinned.copy()
                    .stepType(StepType.LAST)
                    .sessionUuid(filteredFirst.getSessionUuid())
                    .code(Map.of(FILTERED_ELEMENT_ID, PROBE_XSLT))
                    .stepFilterMap(Map.of(FILTERED_ELEMENT_ID,
                            new SteppingFilterSettings(null, OutputState.NOT_EMPTY, List.of())))
                    .build());
            assertThat(filteredLast.isFoundRecord()).as("filtered LAST found a record").isTrue();
            assertThat(filteredLast.getFoundLocation().getMetaId()).isEqualTo(metaId);
            assertThat(filteredLast.getFoundLocation().getRecordIndex())
                    .as("filtered LAST is the last match, one back from the end of the stream")
                    .isEqualTo(lastIndex - 1);
            assertThat(filteredLast.getStepData().getElementData(FILTERED_ELEMENT_ID).getOutput())
                    .as("and that record really matches too")
                    .contains("<Hit>9</Hit>");

            // Neither end re-swept the stream. Landing correctly is not enough on its own: a filtered FIRST or
            // LAST that fell back to sweeping from source would give the same answer, just at the cost this
            // whole engine exists to remove.
            assertThat(steppingService.getFullSweepLaunchCount())
                    .as("filtered FIRST and LAST were answered without sweeping the stream again")
                    .isEqualTo(fullSweepsBefore);
        } finally {
            terminate(base, probe);
            terminate(base, session);
        }
    }

    /**
     * @return the same request narrowed to one stream, so FIRST and LAST cannot be answered from another.
     */
    private PipelineStepRequest pinnedTo(final long metaId) {
        return baseRequest().copy()
                .criteria(new FindMetaCriteria(ExpressionOperator.builder()
                        .addIdTerm(MetaFields.ID, Condition.EQUALS, metaId)
                        .build()))
                .build();
    }

    /**
     * REFRESHes a single record with the probe injected but no filter applied, and returns what the filtered
     * element served.
     */
    private SharedElementData elementDataAt(final PipelineStepRequest base,
                                            final SteppingResult session,
                                            final StepLocation found,
                                            final long recordIndex) {
        final SteppingResult result = steppingService.step(base.copy()
                .stepType(StepType.REFRESH)
                .stepLocation(new StepLocation(found.getMetaId(), found.getPartIndex(), recordIndex))
                .sessionUuid(session.getSessionUuid())
                .code(Map.of(FILTERED_ELEMENT_ID, PROBE_XSLT))
                .build());
        assertThat(result.isFoundRecord()).as("REFRESH found record " + recordIndex).isTrue();
        final SharedElementData data = result.getStepData().getElementData(FILTERED_ELEMENT_ID);
        assertThat(data).as(FILTERED_ELEMENT_ID + " captured at record " + recordIndex).isNotNull();
        return data;
    }

    /**
     * @return the element's errors and fatal errors as text, so a failure says <em>why</em> the XSLT did not
     * run rather than just that the output looked wrong. Warnings and info are ignored - a schema validator
     * downstream of the probe will legitimately complain about {@code Probe} not being in its schema.
     */
    private String indicatorText(final SharedElementData data) {
        if (data.getIndicators() == null) {
            return "";
        }
        final Severity maxSeverity = data.getIndicators().getMaxSeverity();
        if (maxSeverity == null || !maxSeverity.greaterThanOrEqual(Severity.ERROR)) {
            return "";
        }
        return data.getIndicators().toString();
    }

    private PipelineStepRequest baseRequest() {
        final DocRef pipelineRef = docFinder.findByName(PipelineDoc.TYPE, FEED).getFirst();
        final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTextTerm(MetaFields.FEED, Condition.EQUALS, FEED)
                .addOperator(ExpressionOperator.builder().op(Op.OR)
                        .addTextTerm(MetaFields.TYPE, Condition.EQUALS, StreamTypeNames.RAW_REFERENCE)
                        .addTextTerm(MetaFields.TYPE, Condition.EQUALS, StreamTypeNames.RAW_EVENTS)
                        .build())
                .build();
        return PipelineStepRequest.builder()
                .pipelineDoc(pipelineDoc)
                .criteria(new FindMetaCriteria(expression))
                .timeout(Long.MAX_VALUE)
                .build();
    }

    private void terminate(final PipelineStepRequest base, final SteppingResult result) {
        if (result != null && result.getSessionUuid() != null) {
            steppingService.terminateStepping(base.copy().sessionUuid(result.getSessionUuid()).build());
        }
    }
}
