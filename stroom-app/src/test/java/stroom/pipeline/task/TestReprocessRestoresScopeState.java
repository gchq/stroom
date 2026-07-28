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
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.stepping.SteppingService;
import stroom.pipeline.xslt.XsltStore;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared pipeline scope must survive the split. A reprocess deliberately does not re-run the elements above
 * the edit, so their {@code stroom:put} calls never happen; without the captured scope snapshot being restored
 * a {@code stroom:get} below the edit would silently return nothing where the full sweep gave it a value -
 * wrong output served as though it were correct.
 * <p>
 * No sample feed chains two XSLTs, so this test builds the topology it needs: two extra {@code XSLTFilter}s
 * hung off {@code translationFilter}, the first putting a known value and the second getting it. They are
 * added to an <b>in-memory copy</b> of the pipeline carried on the step request, so the stored sample pipeline
 * (and every other test that steps it) is untouched.
 * <p>
 * The edit is applied to the <em>getting</em> element, so the reprocess starts there and is fed from the
 * putting element's stored output - meaning the put itself is never re-executed. Remove the
 * {@code taskScopeMap.restore} call in {@code ReprocessDriver} and this test fails with an empty probe.
 */
class TestReprocessRestoresScopeState extends TranslationTest {

    private static final AtomicBoolean DONE_SETUP = new AtomicBoolean();

    private static final String FEED = "XML-EVENTS";
    private static final String PUT_ELEMENT_ID = "scopePutFilter";
    private static final String GET_ELEMENT_ID = "scopeGetFilter";
    private static final String KEY = "scopeTestKey";
    private static final String VALUE = "scopeTestValue";

    /**
     * Copies its input through unchanged, putting a known value into shared scope as it goes. The put is made
     * inside the copied root element so it is evaluated while output is being written - an
     * {@code xsl:variable} would be evaluated lazily and might never run at all.
     */
    private static final String PUT_XSLT = """
            <?xml version="1.0" encoding="UTF-8" ?>
            <xsl:stylesheet version="2.0"
                            xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                            xmlns:stroom="stroom">
              <xsl:template match="/*">
                <xsl:copy>
                  <xsl:value-of select="stroom:put('%s', '%s')"/>
                  <xsl:apply-templates select="@*|node()"/>
                </xsl:copy>
              </xsl:template>
              <xsl:template match="@*|node()">
                <xsl:copy>
                  <xsl:apply-templates select="@*|node()"/>
                </xsl:copy>
              </xsl:template>
            </xsl:stylesheet>
            """.formatted(KEY, VALUE);

    /**
     * Emits nothing but what it can read back out of shared scope, so its output is a direct probe of whether
     * the upstream put is visible.
     */
    private static final String GET_XSLT = """
            <?xml version="1.0" encoding="UTF-8" ?>
            <xsl:stylesheet version="2.0"
                            xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                            xmlns:stroom="stroom">
              <xsl:template match="/">
                <ScopeProbe><xsl:value-of select="stroom:get('%s')"/></ScopeProbe>
              </xsl:template>
            </xsl:stylesheet>
            """.formatted(KEY);

    @Inject
    private SteppingService steppingService;
    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private DocFinder docFinder;
    @Inject
    private XsltStore xsltStore;

    @BeforeEach
    void setup() {
        if (!DONE_SETUP.get()) {
            importConfig();
            loadAllRefData();
            DONE_SETUP.set(true);
        }
    }

    @Override
    protected boolean cleanupBetweenTests() {
        return false;
    }

    @Test
    void upstreamPutIsVisibleToAGetBelowTheEdit() {
        testTranslationTask(FEED, false, false);

        final PipelineDoc pipelineDoc = withScopeProbeElements();

        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTextTerm(MetaFields.FEED, Condition.EQUALS, FEED)
                .addOperator(ExpressionOperator.builder().op(Op.OR)
                        .addTextTerm(MetaFields.TYPE, Condition.EQUALS, StreamTypeNames.RAW_REFERENCE)
                        .addTextTerm(MetaFields.TYPE, Condition.EQUALS, StreamTypeNames.RAW_EVENTS)
                        .build())
                .build();
        final PipelineStepRequest base = PipelineStepRequest.builder()
                .pipelineDoc(pipelineDoc)
                .criteria(new FindMetaCriteria(expression))
                .timeout(Long.MAX_VALUE)
                .build();

        final long reprocessBefore = steppingService.getReprocessLaunchCount();
        final long fullSweepBefore = steppingService.getFullSweepLaunchCount();

        SteppingResult afterEdit = null;
        try {
            // 1) LAST -> a full sweep that runs both new elements for real, so the put happens and the get
            // sees it. LAST waits for its stream to be fully captured, so the sweep is complete and reusable.
            final SteppingResult last = steppingService.step(base.copy().stepType(StepType.LAST).build());
            assertThat(last.isFoundRecord()).as("LAST found a record").isTrue();
            assertThat(steppingService.getFullSweepLaunchCount())
                    .as("first step was a full sweep").isEqualTo(fullSweepBefore + 1);

            final StepLocation record0 = new StepLocation(
                    last.getFoundLocation().getMetaId(), last.getFoundLocation().getPartIndex(), 0);

            // 2) REFRESH record 0 with no edit -> served from the completed sweep. This is the baseline: with
            // every element running normally, the get must see what the put put.
            final SteppingResult swept = steppingService.step(base.copy()
                    .stepType(StepType.REFRESH).stepLocation(record0).sessionUuid(last.getSessionUuid()).build());
            assertThat(swept.isFoundRecord()).as("REFRESH found record 0").isTrue();
            assertThat(swept.getStepData().getElementData(GET_ELEMENT_ID))
                    .as(GET_ELEMENT_ID + " was captured").isNotNull();
            final String sweptOutput = swept.getStepData().getElementData(GET_ELEMENT_ID).getOutput();
            assertThat(sweptOutput)
                    .as("under a full sweep the get sees the upstream put").contains(VALUE);

            // 3) Edit ONLY the getting element (inject its own XSLT, which changes just its fingerprint) ->
            // the planner routes to a reprocess starting there, fed from the putting element's stored output.
            // The putting element is therefore NOT re-run, and its stroom:put never executes on this pass.
            afterEdit = steppingService.step(base.copy()
                    .stepType(StepType.REFRESH)
                    .stepLocation(record0)
                    .sessionUuid(swept.getSessionUuid())
                    .code(Map.of(GET_ELEMENT_ID, GET_XSLT))
                    .build());

            assertThat(steppingService.getReprocessLaunchCount())
                    .as("the edit routed to a reprocess").isEqualTo(reprocessBefore + 1);
            assertThat(steppingService.getFullSweepLaunchCount())
                    .as("the edit did not re-run the pipeline from source").isEqualTo(fullSweepBefore + 1);
            assertThat(afterEdit.isFoundRecord()).as("REFRESH after the edit found record 0").isTrue();
            assertThat(afterEdit.getStepData().getElementData(GET_ELEMENT_ID))
                    .as("the reprocessed element was served").isNotNull();

            // The point of the test: the restored scope snapshot stands in for the put that never ran, so
            // the re-run element reads exactly what it read under the full sweep.
            assertThat(afterEdit.getStepData().getElementData(GET_ELEMENT_ID).getOutput())
                    .as("the get still sees the upstream put after a reprocess that never re-ran it")
                    .isEqualTo(sweptOutput);
        } finally {
            if (afterEdit != null && afterEdit.getSessionUuid() != null) {
                steppingService.terminateStepping(base.copy().sessionUuid(afterEdit.getSessionUuid()).build());
            }
        }
    }

    /**
     * @return an in-memory copy of the feed's pipeline with a put/get pair of XSLT filters hung off
     * {@code translationFilter}. Nothing is written back to the pipeline store: the step request carries the
     * pipeline, so the extra elements exist only for this test's steps.
     */
    private PipelineDoc withScopeProbeElements() {
        final DocRef pipelineRef = docFinder.findByName(PipelineDoc.TYPE, FEED).getFirst();
        final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);

        final PipelineDataBuilder builder = new PipelineDataBuilder(pipelineDoc.getPipelineData());
        builder.addElement(PipelineDataUtil.createElement(PUT_ELEMENT_ID, "XSLTFilter", null, null));
        builder.addElement(PipelineDataUtil.createElement(GET_ELEMENT_ID, "XSLTFilter", null, null));
        builder.addProperty(PipelineDataUtil.createProperty(
                PUT_ELEMENT_ID, "xslt", createXslt("Scope Put XSLT", PUT_XSLT)));
        builder.addProperty(PipelineDataUtil.createProperty(
                GET_ELEMENT_ID, "xslt", createXslt("Scope Get XSLT", GET_XSLT)));
        // A branch off translationFilter rather than a splice into the existing chain, so the elements the
        // golden corpus asserts on keep exactly the topology they had.
        builder.addLink(PipelineDataUtil.createLink("translationFilter", PUT_ELEMENT_ID));
        builder.addLink(PipelineDataUtil.createLink(PUT_ELEMENT_ID, GET_ELEMENT_ID));

        return pipelineDoc.copy().pipelineData(builder.build()).build();
    }

    private DocRef createXslt(final String name, final String data) {
        final DocRef docRef = xsltStore.createDocument(name);
        final XsltDoc doc = xsltStore.readDocument(docRef).copy().data(data).build();
        xsltStore.writeDocument(doc);
        return docRef;
    }
}
