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

import stroom.docref.DocRef;
import stroom.mcp.impl.tools.WriteGuard;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.util.json.JsonUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// A pipeline writing Records, built in a build, through the REAL tools on a real Stroom. A translation's build
/// subfolder is named for what it writes, Events or Records. The fixture mirrors live's 'Stroom Proxy Receive Events'
/// template: a child of 'Event Data (Text)' with the RECORDS schema group and the Records stream type, made under
/// Template Pipelines. In five parts:
///
/// 1. The fixture is found as a records-stage template.
/// 2. A build: a Raw Events feed and a CSV sample; its Data Splitter, saved before any pipeline, goes in Events.
/// 3. An XSLT writing records:2 goes in Records.
/// 4. The pipeline from the fixture goes in Records, and takes the Data Splitter with it.
/// 5. Stepped in Stroom: clean, its records valid against the records schema.
class TestE2eRecordsStage extends AbstractE2eTest {

    private static final String FIXTURE = "E2E Records (Text)";
    private static final String BUILD = "records-stage-1";
    private static final String FEED = "RECORDS-STAGE-1";
    private static final String SAMPLE = """
            time,user,host,action
            2026-10-01T08:00:00Z,alice,ws01,start
            2026-10-01T08:05:00Z,bob,ws02,stop
            2026-10-01T08:07:00Z,carol,ws03,start
            """;
    // Records in, records out: the source's own fields as records:2 data, as a Records-writing translation does.
    private static final String RECORDS_XSLT = """
            <?xml version="1.1" encoding="UTF-8"?>
            <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns="records:2"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xpath-default-namespace="records:2" \
            version="3.0">
              <xsl:template match="records">
                <records xsi:schemaLocation="records:2 file://records-v2.0.xsd" version="2.0">
                  <xsl:apply-templates select="record" />
                </records>
              </xsl:template>
              <xsl:template match="record">
                <record>
                  <xsl:for-each select="data[normalize-space(@value)]">
                    <data name="{@name}" value="{@value}" />
                  </xsl:for-each>
                </record>
              </xsl:template>
            </xsl:stylesheet>
            """;
    private static final String FIXTURE_DATA = """
            {"properties": {"add": [
              {"element": "schemaFilter", "name": "schemaGroup", "value": {"string": "RECORDS"}},
              {"element": "streamAppender", "name": "streamType", "value": {"string": "Records"}}]}}
            """;

    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private WriteGuard guard;

    @Override
    boolean enablesDisabledFilters() {
        return true;
    }

    @Test
    void aPipelineWritingRecordsIsBuiltInTheBuildsRecordsFolder() {
        // 1. The Records template.
        final DocRef template = fixture();
        assertThat(list(client.json("find_pipeline_templates", args("stage", "records")), "candidates"))
                .as("found as a records-stage template")
                .anyMatch(candidate -> template.getUuid().equals(candidate.get("uuid")));

        // 2. A build writing Records: its Data Splitter, made before any pipeline, is in Events.
        agreed("create_feed", args("build", BUILD, "name", FEED));
        final long raw = number(client.json("upload_sample", args("feed", FEED, "sample", SAMPLE)).get("stream_id"));
        final Map<String, Object> splitter = client.json("build_data_splitter", args("stream_ids", List.of(raw),
                "save_as", FEED, "build", BUILD));
        dump("build_data_splitter", splitter);
        assertThat(splitter).as("the Data Splitter, saved").containsEntry("records", 3);
        final String converter = (String) map(splitter, "saved").get("uuid");
        assertThat(roleOf(converter)).as("made before any pipeline, the Data Splitter is in Events")
                .isEqualTo("Events");

        // 3. The XSLT writing records:2 is in Records.
        final Map<String, Object> xslt = client.json("save_xslt", args("build", BUILD, "name", FEED + "-Records",
                "code", RECORDS_XSLT));
        assertThat(roleOf((String) xslt.get("uuid"))).as("the XSLT writing records:2 is in Records")
                .isEqualTo("Records");

        // 4. The pipeline from the fixture is in Records, and the Data Splitter it runs went with it.
        final Map<String, Object> pipeline = agreed("create_pipeline", args("build", BUILD,
                "name", FEED + "-Records", "template_uuid", template.getUuid()));
        final String pipelineUuid = (String) pipeline.get("uuid");
        assertThat(roleOf(pipelineUuid)).as("the pipeline writing Records is in Records").isEqualTo("Records");
        assertThat(roleOf(converter)).as("and the Data Splitter it runs went with it").isEqualTo("Records");

        // 5. Stepped in Stroom.
        final Map<String, Object> stepped = client.json("step_sample", args("pipeline_uuid", pipelineUuid,
                "stream_ids", List.of(raw)));
        dump("step_sample", stepped);
        assertThat(stepped).containsEntry("verdict", "clean").containsEntry("records_stepped", 3);
        final String output = stepOutput(pipelineUuid, raw, 0, "translationFilter");
        assertThat(output).as("its output is records:2, the source's fields kept")
                .contains("<records", "name=\"user\" value=\"alice\"");
    }

    // The Records template, as live's 'Stroom Family App Events Base' is: beside the stock templates.
    private DocRef fixture() {
        final DocRef text = asUser(admin, () -> pipelineStore.list().stream()
                .filter(ref -> "Event Data (Text)".equals(ref.getName()))
                .findFirst()
                .orElseThrow());
        final DocRef ref = create(PipelineDoc.TYPE, FIXTURE, folder(folderOf(text.getUuid()))).getDocRef();
        asUser(admin, () -> pipelineStore.writeDocument(pipelineStore.readDocument(ref).copy()
                .parentPipeline(text)
                .description("Fixture for dev/e2e_records_stage.py: writes Records, as live Stroom Family App "
                             + "Events Base")
                .pipelineData(JsonUtil.readValue(FIXTURE_DATA, PipelineData.class))
                .build()));
        return ref;
    }

    private String roleOf(final String uuid) {
        return asUser(admin, () -> guard.folderContents(BUILD).stream()
                .filter(doc -> uuid.equals(doc.uuid()))
                .map(doc -> String.valueOf(doc.role()))
                .findFirst()
                .orElse("missing"));
    }
}
