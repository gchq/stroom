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
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.MetaProperties;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.pipeline.stepping.SteppingService;
import stroom.pipeline.xslt.XsltStore;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.test.common.StroomCoreServerTestFileUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A stepping fixture that actually carries the state stepping can get wrong.
 * <p>
 * The sample feeds exercise almost none of it: no feed uses {@code stroom:put}/{@code get}, the location
 * functions, or stream metadata, and none of the loaded streams even has a {@code Meta} child stream. That is
 * a problem for the concurrent-stage work, because a stage runs an element detached from the pipeline above
 * it, and every one of these is reached from shared pipeline scope rather than from the element's own input -
 * exactly the things that go quietly wrong when an element is re-run in isolation. A test that says
 * "the reprocessed output matches the swept output" proves nothing if both are empty.
 * <p>
 * So this fixture builds the topology and loads a stream that carries the state, then asserts under a plain
 * full sweep - no edit, no reprocess - that <b>every probe reads back something</b>. Later work can build on
 * it knowing the probes are live; if a probe ever goes empty, this test fails rather than some downstream
 * comparison silently succeeding.
 * <p>
 * Context reference data ({@code stroom:lookup} against the stream's own context child stream, the other
 * consumer of {@code metaHolder.getInputStreamProvider()}) is covered separately by
 * {@link TestSteppingContextLookup}, which has to load a stream with a context child stream and build a
 * context pipeline of its own because no sample feed has either.
 */
class TestSteppingStateFixture extends TranslationTest {

    private static final AtomicBoolean DONE_SETUP = new AtomicBoolean();

    private static final String FEED = "XML-EVENTS";
    private static final String PUT_ELEMENT_ID = "statePutFilter";
    private static final String PROBE_ELEMENT_ID = "stateProbeFilter";

    private static final String KEY = "stateFixtureKey";
    private static final String VALUE = "stateFixtureValue";
    private static final String META_KEY = "StateFixtureAttribute";
    private static final String META_VALUE = "stateFixtureMetaValue";

    /**
     * Copies its input through unchanged, putting a known value into shared scope as it goes. The put sits
     * inside the copied root element so it is evaluated while output is being written; an {@code xsl:variable}
     * would be evaluated lazily and might never run.
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
     * Reads back one piece of shared scope per element, so a single served output says which kinds of state
     * are live. Each is reached from pipeline scope, not from this element's input, which is why a stage that
     * re-runs this element in isolation has to reconstruct all of them.
     */
    private static final String PROBE_XSLT = """
            <?xml version="1.0" encoding="UTF-8" ?>
            <xsl:stylesheet version="2.0"
                            xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                            xmlns:stroom="stroom">
              <xsl:template match="/">
                <StateProbe>
                  <Put><xsl:value-of select="stroom:get('%s')"/></Put>
                  <RecordNo><xsl:value-of select="stroom:record-no()"/></RecordNo>
                  <LineFrom><xsl:value-of select="stroom:line-from()"/></LineFrom>
                  <Meta><xsl:value-of select="stroom:meta('%s')"/></Meta>
                </StateProbe>
              </xsl:template>
            </xsl:stylesheet>
            """.formatted(KEY, META_KEY);

    @Inject
    private SteppingService steppingService;
    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private DocFinder docFinder;
    @Inject
    private XsltStore xsltStore;
    @Inject
    private Store store;

    /**
     * The fixture's whole point is a baseline produced by a plain <b>full sweep</b> - since
     * {@code stepping.skeletonSweep} became the default, an unpinned run would serve the FIRST step from a
     * backbone plus a materialisation through {@code ReprocessDriver}, the very re-run path other tests
     * compare against this baseline. Pin the fallback so the baseline stays independent.
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
    void everyKindOfSharedStateIsLiveUnderAFullSweep() {
        testTranslationTask(FEED, false, false);

        final long metaId = loadStreamCarryingMetadata();
        final PipelineDoc pipelineDoc = withProbeElements();

        final PipelineStepRequest request = PipelineStepRequest.builder()
                .pipelineDoc(pipelineDoc)
                .criteria(new FindMetaCriteria(ExpressionOperator.builder()
                        .addIdTerm(MetaFields.ID, Condition.EQUALS, metaId)
                        .build()))
                .timeout(Long.MAX_VALUE)
                .stepType(StepType.FIRST)
                .build();

        SteppingResult result = null;
        try {
            result = steppingService.step(request);

            assertThat(result.isFoundRecord()).as("FIRST found a record").isTrue();
            assertThat(result.getStepData().getElementData(PROBE_ELEMENT_ID))
                    .as(PROBE_ELEMENT_ID + " was captured").isNotNull();
            final String probe = result.getStepData().getElementData(PROBE_ELEMENT_ID).getOutput();

            // Each of these is a separate kind of shared state, and each has to survive being read by an
            // element that a stage may run on its own. An empty element here means the fixture has stopped
            // testing that kind - which is the failure this whole class exists to make loud.
            assertProbeIsLive(probe, "Put", "stroom:put/get across elements");
            assertProbeIsLive(probe, "RecordNo", "stroom:record-no (LocationHolder)");
            assertProbeIsLive(probe, "LineFrom", "stroom:line-from (LocationHolder)");
            assertProbeIsLive(probe, "Meta", "stroom:meta (stream metadata child stream)");

            // ...and the values are the ones we planted, not incidental non-emptiness.
            assertThat(probe).as("the put value round-trips").contains(VALUE);
            assertThat(probe).as("the stream metadata value round-trips").contains(META_VALUE);
        } finally {
            if (result != null && result.getSessionUuid() != null) {
                steppingService.terminateStepping(
                        request.copy().sessionUuid(result.getSessionUuid()).build());
            }
        }
    }

    /**
     * Asserts the probe element has actual text in it. Deliberately extracts the text rather than looking for
     * {@code <X/>}: an empty value can serialise either as {@code <X/>} or as {@code <X></X>}, and a check
     * that only catches one of those is a check that can quietly stop working.
     */
    private void assertProbeIsLive(final String probe, final String element, final String what) {
        final String open = "<" + element + ">";
        final String close = "</" + element + ">";
        final int start = probe.indexOf(open);
        final int end = start < 0 ? -1 : probe.indexOf(close, start);
        final String text = (start < 0 || end < 0) ? "" : probe.substring(start + open.length(), end);
        assertThat(text)
                .as(what + " must be readable under a full sweep, or this fixture is not testing it")
                .isNotBlank();
    }

    /**
     * The sample loader writes no {@code Meta} child stream, so stream metadata is invisible to every existing
     * feed. Load one that has it, alongside the data the feed's pipeline already knows how to parse.
     */
    private long loadStreamCarryingMetadata() {
        final Path dataLocation = StroomCoreServerTestFileUtil.getTestResourcesDir()
                .resolve("samples")
                .resolve("input")
                .resolve(FEED + "~1.in");
        assertThat(Files.isRegularFile(dataLocation))
                .as("sample input " + dataLocation + " exists").isTrue();

        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(META_KEY, META_VALUE);
        attributeMap.put("StateFixture", "true");

        try (final Target target = store.openTarget(MetaProperties.builder()
                .feedName(FEED)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .build())) {
            try (final OutputStreamProvider outputStreamProvider = target.next()) {
                try (final OutputStream outputStream = outputStreamProvider.get()) {
                    outputStream.write(Files.readAllBytes(dataLocation));
                }
                try (final OutputStream metaStream = outputStreamProvider.get(StreamTypeNames.META)) {
                    AttributeMapUtil.write(attributeMap, metaStream);
                }
            }
            return target.getMeta().getId();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @return an in-memory copy of the feed's pipeline with the put and probe filters chained below
     * {@code translationFilter}. Nothing is written back to the pipeline store - the step request carries the
     * pipeline - so the sample pipeline every other test steps is untouched.
     */
    private PipelineDoc withProbeElements() {
        final DocRef pipelineRef = docFinder.findByName(PipelineDoc.TYPE, FEED).getFirst();
        final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);

        final PipelineDataBuilder builder = new PipelineDataBuilder(pipelineDoc.getPipelineData());
        builder.addElement(PipelineDataUtil.createElement(PUT_ELEMENT_ID, "XSLTFilter", null, null));
        builder.addElement(PipelineDataUtil.createElement(PROBE_ELEMENT_ID, "XSLTFilter", null, null));
        builder.addProperty(PipelineDataUtil.createProperty(
                PUT_ELEMENT_ID, "xslt", createXslt("State Fixture Put XSLT", PUT_XSLT)));
        builder.addProperty(PipelineDataUtil.createProperty(
                PROBE_ELEMENT_ID, "xslt", createXslt("State Fixture Probe XSLT", PROBE_XSLT)));
        builder.addLink(PipelineDataUtil.createLink("translationFilter", PUT_ELEMENT_ID));
        builder.addLink(PipelineDataUtil.createLink(PUT_ELEMENT_ID, PROBE_ELEMENT_ID));

        return pipelineDoc.copy().pipelineData(builder.build()).build();
    }

    private DocRef createXslt(final String name, final String data) {
        final DocRef docRef = xsltStore.createDocument(name);
        final XsltDoc doc = xsltStore.readDocument(docRef).copy().data(data).build();
        xsltStore.writeDocument(doc);
        return docRef;
    }
}
