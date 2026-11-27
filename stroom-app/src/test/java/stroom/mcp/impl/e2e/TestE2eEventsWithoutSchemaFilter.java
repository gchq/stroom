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

package stroom.mcp.impl.e2e;

import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.processing.MergedPipeline;
import stroom.docref.DocRef;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineLink;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// A hand-written events pipeline with no schema filter after its translation, through the REAL tools on a real
/// Stroom (decided 2026-11-25): stepping checks the translation's Events against the event-logging schema Stroom
/// holds and says so; Events that fail it block the step, as a schema filter's errors would. Before, stepping left
/// them to check_events, and processing wrote them invalid.
class TestE2eEventsWithoutSchemaFilter extends AbstractE2eTest {

    private static final String STAMP = String.valueOf(System.currentTimeMillis() % 100_000);
    private static final String SCHEMA_FILTER = "schemaFilter";

    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private DocumentAccess documentAccess;

    @Test
    void theTranslationsEventsAreCheckedAgainstTheEventLoggingSchemaWhenNoSchemaFilterFollowsIt() {
        final String build = "noschema-" + STAMP;
        final String feed = "E2E-NOSCHEMA-" + STAMP;
        createFeed(build, feed);
        final long raw = upload(feed, CSV_SAMPLE);
        final String converter = saveTextConverter(build, feed, CSV_SPLITTER);
        final String translation = saveXslt(build, feed + "-Events", CSV_XSLT);
        final String pipeline = uuidOf(createPipeline(build, feed + "-Events",
                templateUuid("translation", "Event Data (Text)"),
                docProperty("dsParser", "textConverter", converter, "TextConverter"),
                docProperty("translationFilter", "xslt", translation, XsltDoc.TYPE)));
        withoutSchemaFilter(pipeline);

        // Valid Events: clean, and the check said.
        final Map<String, Object> valid = json("step_sample", args("pipeline_uuid", pipeline, "stream_ids",
                List.of(raw)));
        dump("step_sample valid Events, no schema filter", valid);
        assertThat(valid.get("verdict")).isEqualTo("clean");
        assertThat(String.valueOf(valid.get("events_schema_check"))).contains(
                "No schema filter follows translationFilter", "file://event-logging-v4.1.0.xsd",
                "all 3 record(s) valid");

        // Outcome/Success holding the source's 'ok' or 'fail', which no element-name check sees: blocking.
        agreed("save_xslt", args("build", build, "uuid", translation, "code", CSV_XSLT.replace(
                "<xsl:value-of select=\"data[@name='result']/@value = 'ok'\" />",
                "<xsl:value-of select=\"data[@name='result']/@value\" />")));
        final Map<String, Object> invalid = json("step_sample", args("pipeline_uuid", pipeline, "stream_ids",
                List.of(raw)));
        dump("step_sample invalid Events, no schema filter", invalid);
        assertThat(invalid.get("verdict")).isEqualTo("blocking");
        assertThat(String.valueOf(invalid)).contains("no schema filter after the translation checks its Events",
                "The translation's Events fail the event-logging schema");
        assertThat(String.valueOf(invalid.get("events_schema_check"))).contains("3 of 3 record(s) failed it");
    }

    // The template's schema filter taken out of the chain in the pipeline's own layer: the translation writes
    // straight to the element after it.
    private void withoutSchemaFilter(final String pipeline) {
        final MergedPipeline merged = asUser(admin, () -> MergedPipeline.load(documentAccess, pipeline)
                .orElseThrow());
        final List<String> chain = merged.chain();
        final int schema = chain.indexOf(SCHEMA_FILTER);
        assertThat(schema).as("a schema filter in %s", chain).isGreaterThan(0);
        final String before = chain.get(schema - 1);
        final String after = chain.get(schema + 1);
        asUser(admin, () -> {
            final PipelineDoc doc = pipelineStore.readDocument(new DocRef(PipelineDoc.TYPE, pipeline));
            pipelineStore.writeDocument(doc.copy().pipelineData(new PipelineDataBuilder(doc.getPipelineData())
                    .removeLink(new PipelineLink(before, SCHEMA_FILTER))
                    .removeLink(new PipelineLink(SCHEMA_FILTER, after))
                    .addLink(new PipelineLink(before, after))
                    .build()).build());
        });
        // Left in the pipeline unlinked: nothing reaches it from the translation.
        assertThat(asUser(admin, () -> MergedPipeline.load(documentAccess, pipeline).orElseThrow())
                .downstream("translationFilter")).as("what reads the translation's output")
                .isNotEmpty()
                .doesNotContain(SCHEMA_FILTER);
    }
}
