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
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineLink;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// An output XSLT (reading Events, writing no `Events` or `Event`) after the translation, through the REAL tools on
/// a real Stroom. Seen in a reference environment: a flattener writing `<data name= value=/>` pairs in the Events
/// namespace, for the XSLT after it, was refused by check_xslt, save_xslt and step_pipeline's draft_code, once per
/// pair: "No element 'data' exists anywhere in the ... event-logging schema". Its pairs are no Events, and nothing
/// validates them against the Events schema.
///
/// A CSV translation to event-logging:3, its schema filter, then a flattener writing its pairs in event-logging:3 and
/// no schema filter after it: check_xslt passes the flattener, save_xslt saves it, and step_pipeline steps a changed
/// draft of it, its pairs in the output.
class TestE2eOutputXslt extends AbstractE2eTest {

    private static final String STAMP = String.valueOf(System.currentTimeMillis() % 100_000);
    private static final String FLATTEN = "flattenFilter";
    // Each Event's fields as data pairs, in the Events namespace, for a later XSLT.
    private static final String FLATTENER = """
            <?xml version="1.1" encoding="UTF-8"?>
            <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns="event-logging:3" \
            xpath-default-namespace="event-logging:3" version="3.0">
              <xsl:template match="/Events"><records><xsl:apply-templates select="Event"/></records></xsl:template>
              <xsl:template match="Event">
                <record>
                  <data name="time" value="{EventTime/TimeCreated}"/>
                  <data name="user" value="{EventSource/User/Id}"/>
                  <data name="host" value="{EventSource/Device/HostName}"/>
                </record>
              </xsl:template>
            </xsl:stylesheet>
            """;

    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private DocumentAccess documentAccess;

    @Test
    void aFlattenerAfterTheTranslationIsCheckedSavedAndSteppedWithoutTheEventsSchemasNames() {
        final String build = "flat-" + STAMP;
        final String feed = "E2E-FLAT-" + STAMP;
        createFeed(build, feed);
        final long raw = upload(feed, CSV_SAMPLE);
        final String converter = saveTextConverter(build, feed, CSV_SPLITTER);
        final String translation = saveXslt(build, feed + "-Events", CSV_XSLT);
        final String pipeline = uuidOf(createPipeline(build, feed + "-Events",
                templateUuid("translation", "Event Data (Text)"),
                docProperty("dsParser", "textConverter", converter, "TextConverter"),
                docProperty("translationFilter", "xslt", translation, XsltDoc.TYPE)));

        // Checked: no element-name errors, nor a warning that they weren't checked.
        final Map<String, Object> checked = json("check_xslt", args("xslt", FLATTENER));
        dump("check_xslt flattener", checked);
        assertThat(checked).as("the flattener passes: %s", checked.get("errors")).containsEntry("ok", true);
        assertThat(String.valueOf(checked.get("warnings"))).doesNotContain("not checked");

        // Saved, and run after the schema filter.
        final String flattener = saveXslt(build, feed + "-Flat", FLATTENER);
        assertThat(readXslt(flattener).getData()).contains("<data name=\"user\"");
        withFlattener(pipeline, flattener);

        // Stepped with a draft of it: no refusal, its pairs in the output.
        final String draft = FLATTENER.replace("<data name=\"host\" value=\"{EventSource/Device/HostName}\"/>",
                "<data name=\"host\" value=\"{EventSource/Device/HostName}\"/>"
                + "<data name=\"type\" value=\"{EventDetail/TypeId}\"/>");
        final Map<String, Object> stepped = json("step_pipeline", args("pipeline_uuid", pipeline,
                "stream_id", raw, "record", 0, "draft_code", Map.of(FLATTEN, draft)));
        dump("step_pipeline flattener draft", stepped);
        final String output = String.valueOf(map(map(stepped, "elements"), FLATTEN).get("output"));
        assertThat(output).as("the draft's pairs").contains("<data name=\"user\" value=\"alice\"",
                "<data name=\"type\"");
        assertThat(String.valueOf(stepped)).doesNotContain("No element 'data'", "is not stepped");
    }

    // The flattener put after the pipeline's schema filter, in the pipeline's own layer, with no schema filter after
    // it.
    private void withFlattener(final String pipeline, final String flattener) {
        final MergedPipeline merged = asUser(admin, () -> MergedPipeline.load(documentAccess, pipeline)
                .orElseThrow());
        final List<String> chain = merged.chain();
        final int schema = chain.indexOf("schemaFilter");
        assertThat(schema).as("a schema filter in %s", chain).isGreaterThanOrEqualTo(0);
        final String after = chain.get(schema + 1);
        asUser(admin, () -> {
            final PipelineDoc doc = pipelineStore.readDocument(new DocRef(PipelineDoc.TYPE, pipeline));
            pipelineStore.writeDocument(doc.copy().pipelineData(new PipelineDataBuilder(doc.getPipelineData())
                    .addElement(new PipelineElement(FLATTEN, "XSLTFilter"))
                    .removeLink(new PipelineLink("schemaFilter", after))
                    .addLink(new PipelineLink("schemaFilter", FLATTEN))
                    .addLink(new PipelineLink(FLATTEN, after))
                    .addProperty(property(FLATTEN, "xslt", new DocRef(XsltDoc.TYPE, flattener)))
                    .build()).build());
        });
    }
}
