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

import stroom.data.shared.StreamTypeNames;
import stroom.explorer.shared.ExplorerNode;
import stroom.pipeline.shared.XsltDoc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// CEF pipelines that write their lines as text to a file, as environments have them, over the real tools: told
/// by their XSLT (stage `forwarding`, backend `file`), documented and evaluated as one sending to Kafka is.
///
/// 1. A thin pipeline XSLT that imports the team's shared CEF XSLT (the `CEF:0|` literal only in the shared one),
///    its vendor a TODO constant, one Event with no user logged as an error: evaluate_pipeline classes it and
///    reviews its lines; write_documentation generates its Field mapping section; processed, its file holds the
///    lines and its Error stream is triaged in the processing section.
/// 2. An XSLT writing XML that the TextWriter turns into text (a line element per Event): its lines are read
///    without the tags.
@SuppressWarnings("unchecked")
class TestE2eCefToFile extends AbstractE2eTest {

    private static final String STAMP = String.valueOf(System.currentTimeMillis() % 100_000);
    // Two Events: the second has no user, which the shared XSLT logs as an error.
    private static final String EVENTS = """
            <?xml version="1.1" encoding="UTF-8"?>
            <Events xmlns="event-logging:3" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" Version="3.5.2" \
            xsi:schemaLocation="event-logging:3 file://event-logging-v3.5.2.xsd">
            <Event><EventTime><TimeCreated>2026-09-27T23:48:10.000Z</TimeCreated></EventTime>
             <EventSource><System><Name>SecretServer</Name><Environment>Prod</Environment></System>\
            <Generator>SecretServer Audit</Generator>
              <Device><HostName>ss-web-02</HostName></Device><User><Id>priya.patel</Id></User></EventSource>
             <EventDetail><TypeId>User-Login</TypeId><Description>User logged in</Description><Authenticate>\
            <Action>Logon</Action><User><Id>priya.patel</Id></User></Authenticate></EventDetail></Event>
            <Event><EventTime><TimeCreated>2026-09-27T23:56:15.000Z</TimeCreated></EventTime>
             <EventSource><System><Name>SecretServer</Name><Environment>Prod</Environment></System>\
            <Generator>SecretServer Audit</Generator>
              <Device><HostName>ss-web-01</HostName></Device></EventSource>
             <EventDetail><TypeId>Secret-View</TypeId><Description>Secret viewed</Description><View><Resource>\
            <Type>Secret</Type><Name>Firewall</Name></Resource></View></EventDetail></Event>
            </Events>
            """;
    // The team's shared CEF XSLT: the only one holding the CEF literal.
    private static final String SHARED = """
            <?xml version="1.1" encoding="UTF-8"?>
            <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:stroom="stroom" \
            xpath-default-namespace="event-logging:3" version="2.0">
              <xsl:template name="cef-line">
                <xsl:if test="not(EventSource/User/Id)">
                  <xsl:sequence select="stroom:log('ERROR', concat('No user to send for event ', \
            EventDetail/TypeId))"/>
                </xsl:if>
                <xsl:value-of select="concat('CEF:0|TODO vendor: ask the user|SecretServer|11.4|', \
            EventDetail/TypeId, '|', EventDetail/Description, '|3|suser=', EventSource/User/Id, ' dvchost=', \
            EventSource/Device/HostName)"/>
                <xsl:text>&#10;</xsl:text>
              </xsl:template>
            </xsl:stylesheet>
            """;
    // The pipeline's own XSLT: thin, importing the shared one by name.
    private static final String THIN = """
            <?xml version="1.1" encoding="UTF-8"?>
            <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" \
            xpath-default-namespace="event-logging:3" version="2.0">
              <xsl:import href="SHARED_NAME"/>
              <xsl:output method="text"/>
              <xsl:template match="/Events"><xsl:apply-templates select="Event"/></xsl:template>
              <xsl:template match="Event"><xsl:call-template name="cef-line"/></xsl:template>
            </xsl:stylesheet>
            """;
    // XML for the TextWriter: a line element per Event, the writer writing its text.
    private static final String XML_LINES = """
            <?xml version="1.1" encoding="UTF-8"?>
            <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" \
            xpath-default-namespace="event-logging:3" version="2.0">
              <xsl:template match="/Events"><lines><xsl:apply-templates select="Event"/></lines></xsl:template>
              <xsl:template match="Event">
                <line><xsl:value-of select="concat('CEF:0|Delinea|SecretServer|11.4|', EventDetail/TypeId, '|', \
            EventDetail/Description, '|3|dvchost=', EventSource/Device/HostName)"/></line>
                <xsl:text>&#10;</xsl:text>
              </xsl:template>
            </xsl:stylesheet>
            """;

    @Test
    void aCefPipelineWritingAFileThroughASharedXsltIsEvaluatedDocumentedAndProcessed() throws IOException {
        final String build = "cef-file-" + STAMP;
        final String feed = "CEF-FILE-" + STAMP;
        final long eventsId = eventsFeed(build, feed);
        final String sharedName = "E2E-CEF-COMMON-" + STAMP;
        sharedXslt(sharedName, SHARED);
        final String thin = saveXslt(build, feed + "-CEF", THIN.replace("SHARED_NAME", sharedName));
        final Path output = Path.of(System.getProperty("java.io.tmpdir"), "e2e-cef-" + STAMP + ".log");
        Files.deleteIfExists(output);
        final ExplorerNode folder = folder("System/E2E CEF File/" + STAMP);
        final String template = textFileTemplate(folder, "E2E CEF to file " + STAMP, output.toString());
        final String pipeline = uuidOf(createPipeline(build, feed + "-File", template,
                docProperty("xsltFilter", "xslt", thin, XsltDoc.TYPE)));

        // What stepping gives for a text XSLT's output: the lines, after Stroom's XML declaration.
        final String stepped = stepOutput(pipeline, eventsId, 0, "xsltFilter");
        dump("stepped output of the text XSLT", stepped);
        assertThat(stepped).contains("CEF:0|TODO vendor: ask the user|SecretServer|11.4|User-Login");

        final Map<String, Object> result = json("evaluate_pipeline", args("pipeline_uuid", pipeline,
                "stream_ids", List.of(eventsId)));
        dump("evaluate_pipeline file cef", result);
        assertThat(result.keySet()).containsExactly("pipeline", "processing", "stepping", "cef", "report");
        assertThat(map(result, "pipeline")).as("told by the CEF literal in the XSLT it imports")
                .containsEntry("stage", "forwarding")
                .containsEntry("backend", "file");
        final Map<String, Object> cef = map(result, "cef");
        assertThat(cef).containsEntry("status", "checked").containsEntry("lines_stepped", 2)
                .containsEntry("output", Map.of("output", "text"));
        assertThat(list(cef, "header_todo")).singleElement()
                .satisfies(todo -> assertThat(todo).containsEntry("field", "Device Vendor")
                        .containsEntry("value", "TODO vendor: ask the user"));
        assertThat(jsonOf(cef.get("notes"))).contains("writes its lines as text").doesNotContain("Kafka");
        assertThat((String) map(result, "report").get("outline")).contains("## CEF output");

        final Map<String, Object> doc = agreed("write_documentation", args("build", build, "pipeline_uuid", pipeline,
                "markdown", "# " + feed + "-File\n\n## Purpose and data\n\nSecretServer Events as CEF lines in a "
                            + "file.\n",
                "change", "Created", "stream_ids", List.of(eventsId)));
        dump("write_documentation file cef", doc);
        assertThat((String) doc.get("field_mapping")).as("the mapping its lines imply")
                .contains("Device Vendor", "suser", "dvchost");
        assertThat(documentation(uuidOf(doc))).contains("## Field mapping", "dvchost");

        feedFilter(feed, StreamTypeNames.EVENTS, pipeline);
        processAll();
        assertThat(Files.readString(output, StandardCharsets.UTF_8)).as("the file it writes")
                .contains("CEF:0|TODO vendor: ask the user|SecretServer|11.4|User-Login");
        final Map<String, Object> processed = json("evaluate_pipeline", args("pipeline_uuid", pipeline));
        dump("evaluate_pipeline file cef processed", processed);
        final Map<String, Object> processing = map(processed, "processing");
        assertThat(processing).containsEntry("status", "processed").containsEntry("error_streams", 1);
        assertThat(jsonOf(processing.get("groups"))).as("the error its shared XSLT logged")
                .contains("No user to send for event Secret-View");
        final Map<String, Object> stepping = map(processed, "stepping");
        assertThat(stepping).containsEntry("streams_from", "the pipeline's newest inputs");
        assertThat(ids(stepping, "streams")).as("the Error stream's input").containsExactly(eventsId);
        assertThat(map(processed, "cef")).containsEntry("status", "checked").containsEntry("lines_stepped", 2);
    }

    @Test
    void linesATextWriterWritesFromXmlAreReadWithoutTheTags() {
        final String build = "cef-xml-" + STAMP;
        final String feed = "CEF-XML-" + STAMP;
        final long eventsId = eventsFeed(build, feed);
        final String xslt = saveXslt(build, feed + "-CEF", XML_LINES);
        final ExplorerNode folder = folder("System/E2E CEF File/xml-" + STAMP);
        final Path output = Path.of(System.getProperty("java.io.tmpdir"), "e2e-cef-xml-" + STAMP + ".log");
        final String template = textFileTemplate(folder, "E2E CEF XML to file " + STAMP, output.toString());
        final String pipeline = uuidOf(createPipeline(build, feed + "-File", template,
                docProperty("xsltFilter", "xslt", xslt, XsltDoc.TYPE)));

        // What stepping gives for an XSLT writing XML: the XML, which the TextWriter writes as text.
        final String stepped = stepOutput(pipeline, eventsId, 0, "xsltFilter");
        dump("stepped output of the XML XSLT", stepped);
        assertThat(stepped).contains("<line>CEF:0|Delinea|SecretServer|11.4|User-Login");

        final Map<String, Object> result = json("evaluate_pipeline", args("pipeline_uuid", pipeline,
                "stream_ids", List.of(eventsId)));
        dump("evaluate_pipeline xml cef", result);
        assertThat(map(result, "pipeline")).containsEntry("stage", "forwarding").containsEntry("backend", "file");
        final Map<String, Object> cef = map(result, "cef");
        assertThat(cef).containsEntry("status", "checked").containsEntry("lines_stepped", 2);
        assertThat(list(cef, "header_todo")).isEmpty();
        assertThat(jsonOf(cef)).as("read without the tags").doesNotContain("<line>", "</line>");
    }

    // A feed of Events in the build, with the sample uploaded: the Events stream's id.
    private long eventsFeed(final String build, final String feed) {
        agreed("create_feed", args("build", build, "name", feed, "stream_type", "Events"));
        return number(client.json("upload_sample", args("feed", feed, "sample", EVENTS, "stream_type", "Events"))
                .get("stream_id"));
    }
}
