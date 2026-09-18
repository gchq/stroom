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
import stroom.data.store.api.OutputStreamProvider;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.feed.shared.FeedDoc;
import stroom.meta.api.MetaProperties;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.pipeline.stepping.SteppingService;
import stroom.pipeline.xslt.XsltStore;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.test.StoreCreationTool;
import stroom.test.common.StroomCoreServerTestFileUtil;
import stroom.test.common.StroomPipelineTestFileUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Context reference data under an on-demand, single-record replay.
 * <p>
 * {@code stroom:lookup} against a stream's own <em>context</em> child stream is the last consumer of
 * {@link stroom.pipeline.state.MetaHolder#getInputStreamProvider()} that stepping had never exercised.
 * {@code ReferenceData.getValueFromNestedContextStream} reaches for that provider to load the context data,
 * and a replay runs the edited element outside the sweep that would normally have set it - the same shape as
 * the {@code stroom:put}/{@code get} defect, which was real. This asserts it works rather than assuming it
 * does.
 * <p>
 * Structured as a vacuity guard followed by the actual question. A lookup that returns nothing under a plain
 * full sweep would make the replay assertion meaningless - two empty strings compare equal - so the sweep
 * value is asserted to be the planted one first, and the replay is then required to produce the same thing.
 * <p>
 * No sample feed carries a context stream, so the stream is loaded here with one attached, reusing the
 * context data and context transform that {@code TestTranslationTaskContextAndFlattening} left behind (that
 * test is entirely commented out, but its resources are good).
 */
class TestSteppingContextLookup extends TranslationTest {

    private static final AtomicBoolean DONE_SETUP = new AtomicBoolean();

    private static final String FEED = "XML-EVENTS";
    private static final String PROBE_ELEMENT_ID = "contextProbeFilter";

    private static final String CONTEXT_DIR = "TestTranslationTaskContextAndFlattening/";
    private static final Path CONTEXT_DATA =
            StroomPipelineTestFileUtil.getTestResourcesFile(CONTEXT_DIR + "TestTaskContext.ctx");
    private static final Path CONTEXT_XSLT =
            StroomPipelineTestFileUtil.getTestResourcesFile(CONTEXT_DIR + "TestTaskContext_ContextTransform.xsl");

    /** What the context transform above puts into the on-heap store: map CONTEXT, key Machine. */
    private static final String CONTEXT_VALUE = "MyMachine";

    /** A mid-stream record, so the replay is not the trivially-first one. */
    private static final long MID_RECORD = 5;

    /**
     * Reads the stream's own context data. Nothing about this is reachable from the element's input - the
     * context stream is found through the pipeline-scoped input stream provider - which is exactly why a
     * replayed record is at risk of seeing nothing here.
     */
    private static final String PROBE_XSLT = """
            <?xml version="1.0" encoding="UTF-8" ?>
            <xsl:stylesheet version="2.0"
                            xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                            xmlns:stroom="stroom">
              <xsl:template match="/">
                <ContextProbe>
                  <Machine><xsl:value-of select="stroom:lookup('CONTEXT', 'Machine')"/></Machine>
                </ContextProbe>
              </xsl:template>
            </xsl:stylesheet>
            """;

    @Inject
    private SteppingService steppingService;
    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private DocFinder docFinder;
    @Inject
    private XsltStore xsltStore;
    @Inject
    private StoreCreationTool storeCreationTool;
    @Inject
    private Store store;

    /**
     * The "swept truth" this class compares a single-record replay against must be produced by
     * {@code StreamCaptureDriver}'s whole-pipeline sweep - since {@code stepping.skeletonSweep} became the
     * default, an unpinned run would produce it via an eager materialisation through
     * {@code ReprocessDriver}, the very component under test comparing against itself.
     */
    @BeforeEach
    void pinFullSweepMode() {
        setConfigValueMapper(stroom.pipeline.stepping.store.SteppingConfig.class,
                config -> config.withSkeletonSweep(false));
    }

    @AfterEach
    void unpinFullSweepMode() {
        clearConfigValueMapper();
    }

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
    void contextLookupSurvivesASingleRecordReplay() {
        testTranslationTask(FEED, false, false);

        final long metaId = loadStreamCarryingContext();
        final PipelineDoc pipelineDoc = withContextProbe();

        final PipelineStepRequest base = PipelineStepRequest.builder()
                .pipelineDoc(pipelineDoc)
                .criteria(new FindMetaCriteria(ExpressionOperator.builder()
                        .addIdTerm(MetaFields.ID, Condition.EQUALS, metaId)
                        .build()))
                .timeout(Long.MAX_VALUE)
                .build();

        SteppingResult swept = null;
        try {
            // 1) LAST first, and deliberately: it has to know the true last record, so it waits for the whole
            // stream to be captured. A REFRESH would return the moment its own record was available, leaving
            // the sweep still running - and an incomplete upstream is not reusable, so the edit below would
            // simply sweep the stream again instead of replaying the one record.
            final SteppingResult last = steppingService.step(base.copy().stepType(StepType.LAST).build());
            assertThat(last.isFoundRecord()).as("LAST found a record").isTrue();

            // 2) The vacuity guard, served from that completed sweep: if the lookup cannot be shown to work
            // here, it cannot be shown to have survived anything.
            swept = steppingService.step(base.copy()
                    .stepType(StepType.REFRESH)
                    .stepLocation(new StepLocation(metaId, 0, MID_RECORD))
                    .sessionUuid(last.getSessionUuid())
                    .build());
            assertThat(swept.isFoundRecord()).as("the sweep found record " + MID_RECORD).isTrue();
            final SharedElementData sweptProbe = probeOf(swept);
            assertThat(sweptProbe.getOutput())
                    .as("the probe XSLT ran - if this is Events XML the stylesheet did not compile")
                    .contains("<ContextProbe");
            assertThat(sweptProbe.getOutput())
                    .as("the context lookup resolves under a full sweep, so this fixture tests something")
                    .contains(CONTEXT_VALUE);

            final long reprocessBefore = steppingService.getReprocessLaunchCount();
            final long onDemandBefore = steppingService.getOnDemandLaunchCount();
            final long fullSweepBefore = steppingService.getFullSweepLaunchCount();

            // 3) "Edit" the probe (inject its own XSLT, which changes its fingerprint) and refresh the same
            // record. That routes to a single-record replay: the probe is re-run on its own, fed from stored
            // upstream output, with no sweep running alongside it to have set the input stream provider.
            final SteppingResult replayed = steppingService.step(base.copy()
                    .stepType(StepType.REFRESH)
                    .stepLocation(new StepLocation(metaId, 0, MID_RECORD))
                    .sessionUuid(swept.getSessionUuid())
                    .code(Map.of(PROBE_ELEMENT_ID, PROBE_XSLT))
                    .build());

            // The mechanism, before the outcome: assert this really took the replay path. Were it served from
            // the sweep, the lookup below would pass while proving nothing about replay.
            assertThat(steppingService.getOnDemandLaunchCount() + steppingService.getReprocessLaunchCount())
                    .as("the edit was answered by re-running the probe, not by the completed sweep"
                        + " (onDemand " + onDemandBefore + "->" + steppingService.getOnDemandLaunchCount()
                        + ", reprocess " + reprocessBefore + "->" + steppingService.getReprocessLaunchCount()
                        + ", fullSweep " + fullSweepBefore + "->" + steppingService.getFullSweepLaunchCount()
                        + ")")
                    .isGreaterThan(onDemandBefore + reprocessBefore);

            assertThat(replayed.isFoundRecord()).as("the replay found record " + MID_RECORD).isTrue();
            final SharedElementData replayedProbe = probeOf(replayed);
            assertThat(replayedProbe.getOutput())
                    .as("the replayed probe still reaches the stream's context data")
                    .contains(CONTEXT_VALUE);
            assertThat(replayedProbe.getOutput())
                    .as("and produces exactly what the sweep did")
                    .isEqualTo(sweptProbe.getOutput());
        } finally {
            if (swept != null && swept.getSessionUuid() != null) {
                steppingService.terminateStepping(
                        base.copy().sessionUuid(swept.getSessionUuid()).build());
            }
        }
    }

    private SharedElementData probeOf(final SteppingResult result) {
        final SharedElementData data = result.getStepData().getElementData(PROBE_ELEMENT_ID);
        assertThat(data).as(PROBE_ELEMENT_ID + " was captured").isNotNull();
        return data;
    }

    /**
     * Loads a stream of the feed's normal data with a {@code Context} child stream attached. No sample feed
     * has one, which is why context lookups have never been steppable in a test.
     */
    private long loadStreamCarryingContext() {
        final Path data = StroomCoreServerTestFileUtil.getTestResourcesDir()
                .resolve("samples")
                .resolve("input")
                .resolve(FEED + "~1.in");
        assertThat(Files.isRegularFile(data)).as("sample input " + data + " exists").isTrue();
        assertThat(Files.isRegularFile(CONTEXT_DATA)).as("context data " + CONTEXT_DATA + " exists").isTrue();

        try (final Target target = store.openTarget(MetaProperties.builder()
                .feedName(FEED)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .build())) {
            try (final OutputStreamProvider outputStreamProvider = target.next()) {
                try (final OutputStream outputStream = outputStreamProvider.get()) {
                    outputStream.write(Files.readAllBytes(data));
                }
                try (final OutputStream contextStream =
                        outputStreamProvider.get(StreamTypeNames.CONTEXT)) {
                    contextStream.write(Files.readAllBytes(CONTEXT_DATA));
                }
            }
            return target.getMeta().getId();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @return an in-memory copy of the feed's pipeline with a probe below {@code translationFilter}, carrying
     * a {@code pipelineReference} to a context-data pipeline for this feed's {@code Context} streams. The
     * reference sits on the <b>probe</b> because that is the element whose XSLT calls {@code stroom:lookup}.
     */
    private PipelineDoc withContextProbe() {
        final DocRef pipelineRef = docFinder.findByName(PipelineDoc.TYPE, FEED).getFirst();
        final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);
        final DocRef feedRef = docFinder.findByName(FeedDoc.TYPE, FEED).getFirst();

        // The context stream is XML already, so no text converter - the parser reads it directly.
        final DocRef contextPipeline = storeCreationTool.getContextPipeline(
                "SteppingContextFixture", null, null, CONTEXT_XSLT);

        final PipelineDataBuilder builder = new PipelineDataBuilder(pipelineDoc.getPipelineData());
        builder.addElement(PipelineDataUtil.createElement(PROBE_ELEMENT_ID, "XSLTFilter", null, null));
        builder.addProperty(PipelineDataUtil.createProperty(
                PROBE_ELEMENT_ID, "xslt", createXslt("Context Probe XSLT", PROBE_XSLT)));
        builder.addPipelineReference(PipelineDataUtil.createReference(
                PROBE_ELEMENT_ID, "pipelineReference", contextPipeline, feedRef, StreamTypeNames.CONTEXT));
        builder.addLink(PipelineDataUtil.createLink("translationFilter", PROBE_ELEMENT_ID));

        return pipelineDoc.copy().pipelineData(builder.build()).build();
    }

    private DocRef createXslt(final String name, final String data) {
        final DocRef docRef = xsltStore.createDocument(name);
        final XsltDoc doc = xsltStore.readDocument(docRef).copy().data(data).build();
        xsltStore.writeDocument(doc);
        return docRef;
    }
}
