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

package stroom.ai.impl.tools.processing;

import stroom.ai.impl.tools.ToolTestFixture;
import stroom.ai.impl.tools.processing.XsltStages.How;
import stroom.ai.impl.tools.processing.XsltStages.Stage;
import stroom.ai.impl.tools.processing.XsltStages.StagedXslt;
import stroom.ai.impl.tools.streams.SteppingWalks;
import stroom.ai.impl.tools.validation.HeldSchemas;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineLink;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.pipeline.shared.stepping.SharedStepData;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.pipeline.stepping.SteppingService;
import stroom.xmlschema.shared.XmlSchemaDoc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/// Each XSLT of a pipeline classified as pre-process, translation, decoration or indexing: by content, by its place
/// in the chain, and exactly by stepping a sample record.
class TestXsltStages {

    private static final String XSL = "xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"3.0\"";
    private static final String JSON = "http://www.w3.org/2013/XSL/json";
    private static final String EVENTS = "event-logging:3";
    private static final String FORK = "file://xml/schema/accounting/events";

    /// Unpacks a nested JSON string: JSON to JSON.
    static final String PRE_PROCESS = "<xsl:stylesheet " + XSL + " xpath-default-namespace=\"" + JSON + "\" "
                                      + "xmlns=\"" + JSON + "\"><xsl:template match=\"/\"><map>"
                                      + "<xsl:copy-of select=\"*\"/></map></xsl:template></xsl:stylesheet>";
    /// JSON to Events.
    static final String TRANSLATION = "<xsl:stylesheet " + XSL + " xpath-default-namespace=\"" + JSON + "\" "
                                      + "xmlns=\"" + EVENTS + "\"><xsl:template match=\"/map\"><Events><Event>"
                                      + "<EventTime><TimeCreated>2026-01-01T00:00:00.000Z</TimeCreated></EventTime>"
                                      + "</Event></Events></xsl:template></xsl:stylesheet>";
    /// Events to Events: the user's id added.
    static final String DECORATION = "<xsl:stylesheet " + XSL + " xpath-default-namespace=\"" + EVENTS + "\" "
                                     + "xmlns=\"" + EVENTS + "\"><xsl:template match=\"@*|node()\"><xsl:copy>"
                                     + "<xsl:apply-templates select=\"@*|node()\"/></xsl:copy></xsl:template>"
                                     + "<xsl:template match=\"User\"><xsl:copy><xsl:apply-templates/><UserDetails>"
                                     + "<Id>1</Id></UserDetails></xsl:copy></xsl:template></xsl:stylesheet>";
    /// Events to index documents.
    static final String INDEXING = "<xsl:stylesheet " + XSL + " xpath-default-namespace=\"" + EVENTS + "\" "
                                   + "xmlns=\"records:2\"><xsl:template match=\"/Events\"><records>"
                                   + "<xsl:for-each select=\"Event\"><record/></xsl:for-each></records>"
                                   + "</xsl:template></xsl:stylesheet>";

    private static final DocRef TEMPLATE = new DocRef(PipelineDoc.TYPE, "t", "Event Data (JSON)");
    private static final DocRef CHILD = new DocRef(PipelineDoc.TYPE, "p", "Acme-V1.0-EVENTS");

    @TempDir
    Path tempDir;

    private ToolTestFixture fixture;
    private final HeldSchemas heldSchemas = Mockito.mock(HeldSchemas.class);
    private final SteppingService steppingService = Mockito.mock(SteppingService.class);
    private XsltStages stages;

    @BeforeEach
    void setUp() {
        fixture = new ToolTestFixture(tempDir);
        Mockito.when(heldSchemas.forks()).thenReturn(List.of());
        stages = new XsltStages(fixture.documentAccess(), heldSchemas, new SteppingWalks(() -> steppingService));
        xslt("pre", "Unpack nested JSON", PRE_PROCESS, true);
        xslt("tr", "Acme-V1.0-EVENTS", TRANSLATION, true);
        xslt("dec", "Decorate user details", DECORATION, true);
        xslt("idx", "Acme indexing", INDEXING, true);
    }

    @Test
    void aPreProcessorBeforeTheTranslationIsNotTheTranslation() {
        // The template unpacks and decorates; the child sets its own translation between them.
        pipeline(TEMPLATE, null, List.of("preFilter", "translationFilter", "decorationFilter"),
                Map.of("preFilter", "pre", "decorationFilter", "dec"));
        pipeline(CHILD, TEMPLATE, List.of(), Map.of("translationFilter", "tr"));

        final List<StagedXslt> staged = stages.classify(merged("p"));

        assertThat(staged)
                .extracting(StagedXslt::element, StagedXslt::stage, StagedXslt::inherited, StagedXslt::how)
                .containsExactly(tuple("preFilter", Stage.PRE_PROCESS, true, How.CONTENT),
                        tuple("translationFilter", Stage.TRANSLATION, false, How.CONTENT),
                        tuple("decorationFilter", Stage.DECORATION, true, How.CONTENT));
        assertThat(XsltStages.first(staged, Stage.TRANSLATION).orElseThrow().xslt().getUuid()).isEqualTo("tr");
        assertThat(XsltStages.first(staged, Stage.INDEXING)).isEmpty();
    }

    @Test
    void aTranslationInheritedFromAParentIsTheTranslation() {
        pipeline(TEMPLATE, null, List.of("translationFilter"), Map.of("translationFilter", "tr"));
        pipeline(CHILD, TEMPLATE, List.of(), Map.of());

        final StagedXslt translation = XsltStages.first(stages.classify(merged("p")), Stage.TRANSLATION)
                .orElseThrow();

        assertThat(translation.xslt().getUuid()).isEqualTo("tr");
        assertThat(translation.inherited()).isTrue();
        assertThat(translation.setBy()).isEqualTo(TEMPLATE.getName());
        assertThat(translation.toMap()).containsEntry("stage", "translation").containsEntry("how", "content");
    }

    @Test
    void anEventsFedPipelinesXsltWritingDocumentsIndexes() {
        pipeline(CHILD, null, List.of("decorationFilter", "indexingFilter", "elasticIndexingFilter"),
                Map.of("decorationFilter", "dec", "indexingFilter", "idx"));

        assertThat(stages.classify(merged("p")))
                .extracting(StagedXslt::element, StagedXslt::stage)
                .containsExactly(tuple("decorationFilter", Stage.DECORATION),
                        tuple("indexingFilter", Stage.INDEXING));
    }

    @Test
    void anIndexingXsltReadingEventsByWildcardIndexesWhereThePipelineWritesToAnIndex() {
        xslt("wild", "Wildcard indexing", "<xsl:stylesheet " + XSL + "><xsl:template match=\"/*:Events\">"
                                          + "<records xmlns=\"records:2\"/></xsl:template></xsl:stylesheet>", true);
        pipeline(CHILD, null, List.of("indexingFilter", "elasticIndexingFilter"), Map.of("indexingFilter", "wild"));

        assertThat(stages.classify(merged("p"))).extracting(StagedXslt::stage).containsExactly(Stage.INDEXING);
    }

    @Test
    void rawDataStraightToAnIndexIsIndexedByItsLastXsltAndOtherwisePreProcessed() {
        pipeline(CHILD, null, List.of("preFilter", "elasticIndexingFilter"), Map.of("preFilter", "pre"));
        assertThat(stages.classify(merged("p"))).extracting(StagedXslt::stage).containsExactly(Stage.INDEXING);

        pipeline(CHILD, null, List.of("preFilter"), Map.of("preFilter", "pre"));
        assertThat(stages.classify(merged("p"))).extracting(StagedXslt::stage).containsExactly(Stage.PRE_PROCESS);
    }

    @Test
    void anIdentityCopyAfterTheTranslationDecoratesAndTheLastXsltIndexes() {
        xslt("copy", "Identity", "<xsl:stylesheet " + XSL + "><xsl:template match=\"@*|node()\"><xsl:copy>"
                                 + "<xsl:apply-templates select=\"@*|node()\"/></xsl:copy></xsl:template>"
                                 + "</xsl:stylesheet>", true);
        pipeline(CHILD, null, List.of("translationFilter", "copyFilter", "indexingFilter", "indexFilter"),
                Map.of("translationFilter", "tr", "copyFilter", "copy", "indexingFilter", "idx"));

        assertThat(stages.classify(merged("p")))
                .extracting(StagedXslt::stage)
                .containsExactly(Stage.TRANSLATION, Stage.DECORATION, Stage.INDEXING);
    }

    @Test
    void anXsltTheUserCantViewIsUnknownAndTheOthersAreStillClassified() {
        fixture.visible.remove("pre");
        pipeline(CHILD, null, List.of("preFilter", "translationFilter"),
                Map.of("preFilter", "pre", "translationFilter", "tr"));

        assertThat(stages.classify(merged("p")))
                .extracting(StagedXslt::stage, StagedXslt::how)
                .containsExactly(tuple(Stage.UNKNOWN, How.UNREAD), tuple(Stage.TRANSLATION, How.CONTENT));
    }

    @Test
    void eventsInAForkedNamespaceAreEvents() {
        final XmlSchemaDoc fork = Mockito.mock(XmlSchemaDoc.class);
        Mockito.when(fork.getNamespaceURI()).thenReturn(FORK);
        Mockito.when(heldSchemas.forks()).thenReturn(List.of(fork));
        xslt("forked", "Forked translation", TRANSLATION.replace(EVENTS, FORK), true);
        pipeline(CHILD, null, List.of("preFilter", "translationFilter"),
                Map.of("preFilter", "pre", "translationFilter", "forked"));

        assertThat(stages.classify(merged("p")))
                .extracting(StagedXslt::stage)
                .containsExactly(Stage.PRE_PROCESS, Stage.TRANSLATION);
    }

    @Test
    void steppingASampleRecordSaysExactlyWhereTheEventsBegin() {
        // Written with xsl:element, its namespace computed: its content can't tell it writes Events.
        xslt("computed", "Computed translation", "<xsl:stylesheet " + XSL + "><xsl:param name=\"ns\"/>"
                                                 + "<xsl:template match=\"/\"><xsl:element name=\"Events\" "
                                                 + "namespace=\"{$ns}\"/></xsl:template></xsl:stylesheet>", true);
        pipeline(CHILD, null, List.of("preFilter", "translationFilter"),
                Map.of("preFilter", "pre", "translationFilter", "computed"));
        assertThat(stages.classify(merged("p"))).extracting(StagedXslt::stage)
                .containsExactly(Stage.PRE_PROCESS, Stage.PRE_PROCESS);

        final Map<String, SharedElementData> elements = new LinkedHashMap<>();
        elements.put("preFilter", data("<map xmlns=\"" + JSON + "\"/>", "<map xmlns=\"" + JSON + "\"/>"));
        elements.put("translationFilter", data("<map xmlns=\"" + JSON + "\"/>",
                "<Events xmlns=\"" + EVENTS + "\"><Event/></Events>"));
        Mockito.when(steppingService.step(Mockito.any())).thenReturn(new SteppingResult("s", null, null,
                new StepLocation(7L, 0L, 1L), new SharedStepData(null, elements), null, true, null, false, true));

        assertThat(stages.classify(merged("p"), pipelineDoc("p"), 7L))
                .extracting(StagedXslt::stage, StagedXslt::how)
                .containsExactly(tuple(Stage.PRE_PROCESS, How.STEPPED), tuple(Stage.TRANSLATION, How.STEPPED));
        Mockito.verify(steppingService).terminateStepping(Mockito.any());
    }

    @Test
    void stepsThatFailOrShowNoOutputFallBackToTheContent() {
        pipeline(CHILD, null, List.of("preFilter", "translationFilter"),
                Map.of("preFilter", "pre", "translationFilter", "tr"));
        Mockito.when(steppingService.step(Mockito.any())).thenThrow(new IllegalStateException("No permission"));

        assertThat(stages.classify(merged("p"), pipelineDoc("p"), 7L))
                .extracting(StagedXslt::stage, StagedXslt::how)
                .containsExactly(tuple(Stage.PRE_PROCESS, How.CONTENT), tuple(Stage.TRANSLATION, How.CONTENT));
        // No stream: by content.
        assertThat(stages.classify(merged("p"), pipelineDoc("p"), null)).extracting(StagedXslt::how)
                .containsOnly(How.CONTENT);
        // A record dropped at an element shows nothing to tell by.
        assertThat(XsltStages.byStep("<map/>", "")).isNull();
        assertThat(XsltStages.byStep("<map/>", "not xml")).isNull();
        assertThat(XsltStages.byStep("<Events xmlns=\"" + EVENTS + "\"/>", "<records xmlns=\"records:2\"/>"))
                .satisfies(reading -> {
                    assertThat(reading.readsEvents()).isTrue();
                    assertThat(reading.writesEvents()).isFalse();
                });
    }

    @Test
    void anXsltFilterWithNoXsltRunsNothingAndANotWellFormedOneIsNeitherEventsNorDocuments() {
        xslt("broken", "Broken", "<xsl:stylesheet", true);
        pipeline(CHILD, null, List.of("emptyFilter", "brokenFilter", "translationFilter"),
                Map.of("brokenFilter", "broken", "translationFilter", "tr"));

        assertThat(stages.classify(merged("p")))
                .extracting(StagedXslt::element, StagedXslt::stage)
                .containsExactly(tuple("brokenFilter", Stage.PRE_PROCESS),
                        tuple("translationFilter", Stage.TRANSLATION));
    }

    // ------------------------------------------------------------------------------------------------

    private void xslt(final String uuid, final String name, final String code, final boolean canView) {
        fixture.add(new DocRef(XsltDoc.TYPE, uuid, name), XsltDoc.builder().uuid(uuid).name(name).data(code).build(),
                canView);
    }

    // A pipeline of the XSLT filters named, linked in order (an element named ...IndexingFilter or indexFilter is
    // of that type), with the XSLTs given set on them.
    private void pipeline(final DocRef ref,
                          final DocRef parent,
                          final List<String> elements,
                          final Map<String, String> xslts) {
        final PipelineDataBuilder data = new PipelineDataBuilder();
        String previous = null;
        for (final String element : elements) {
            final String type = element.equals("elasticIndexingFilter")
                    ? "ElasticIndexingFilter"
                    : element.equals("indexFilter")
                            ? "IndexingFilter"
                            : "XSLTFilter";
            data.addElement(new PipelineElement(element, type));
            if (previous != null) {
                data.addLink(new PipelineLink(previous, element));
            }
            previous = element;
        }
        xslts.forEach((element, uuid) -> data.addProperty(new PipelineProperty(element, "xslt",
                new PipelinePropertyValue(fixture.docRefs.get(uuid)))));
        fixture.add(ref, PipelineDoc.builder().uuid(ref.getUuid()).name(ref.getName()).parentPipeline(parent)
                .pipelineData(data.build()).build(), true);
    }

    private MergedPipeline merged(final String uuid) {
        return MergedPipeline.load(fixture.documentAccess(), uuid).orElseThrow();
    }

    private PipelineDoc pipelineDoc(final String uuid) {
        return fixture.documentAccess().read(fixture.docRefs.get(uuid), PipelineDoc.class).orElseThrow();
    }

    private static SharedElementData data(final String input, final String output) {
        return new SharedElementData(input, output, null, false, false, true);
    }
}
