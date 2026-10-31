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

import stroom.ai.impl.tools.validation.XsltVersionHistory;
import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.ExplorerNode;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.ProcessorFilter;
import stroom.security.api.SecurityContext;
import stroom.util.json.JsonUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// Onboarding translations end to end through the REAL tools on a real Stroom, with hand-written XSLTs.
///
/// Covered: 1 for the JSON, XML and syslog samples (and the CSV sample the promotion starts from): profile_sample,
/// feed, upload, template, converter and XSLT, pipeline, stepping, processing (exactly one Events stream per raw
/// stream), validation and documentation; and 3, promotion of the CSV build (layout, created folders, version history,
/// the emptied build folder, the disabled filter with no minimum creation time, tags), the documentation written again
/// after promotion through a working copy, and an in-place fix through a working copy written back on promotion, with a
/// backup.
///
/// Part 2 (the reported field fix, reprocessed once and superseded) and the CSV case's own steps are covered by
/// `stroom.mcp.impl.TestOnboardingEndToEnd`. Left out: the build's record entry, and the promoted doc's
/// `documentation` field (Stroom's Documentation doc has its text only).
@SuppressWarnings("unchecked")
class TestE2eTranslation extends AbstractE2eTest {

    /// The Data Splitter for the RFC 5424 syslog sample.
    static final String SYSLOG_SPLITTER = textResource("syslog_splitter.xml");
    static final Case JSON = new Case("json", "Event Data (JSON)",
            "[{\"ts\": \"2026-09-28T11:00:00Z\", \"user\": \"dave\", \"host\": \"ws04\", \"src\": \"10.0.1.4\", "
            + "\"ok\": true}, {\"ts\": \"2026-09-28T11:02:00Z\", \"user\": \"erin\", \"host\": \"ws05\", \"src\": "
            + "\"10.0.1.5\", \"ok\": false}]", null,
            xslt("http://www.w3.org/2013/XSL/json", "/array", "map",
                    "stroom:format-date(string[@key='ts'], 'yyyy-MM-dd''T''HH:mm:ssX')", "string[@key='host']",
                    "string[@key='user']", "string[@key='src']", "boolean[@key='ok']", "Logon", "User logon"));
    static final Case XML = new Case("xml", "Event Data (XML)", E2eFixtures.XML_SAMPLE, null,
            xslt("", "logons", "logon", "stroom:format-date(when, 'yyyy-MM-dd HH:mm:ss')", "host", "who", "addr",
                    "result = 'SUCCESS'", "Logon", "User logon"));
    static final Case SYSLOG = new Case("syslog", "Event Data (Text)",
            "<38>1 2026-09-28T13:00:00.000Z ws08 sshd 812 - - Accepted password for heidi from 10.0.3.8 port 22 "
            + "ssh2\n<38>1 2026-09-28T13:04:00.000Z ws09 sshd 813 - - Accepted publickey for ivan from 10.0.3.9 "
            + "port 22 ssh2\n", SYSLOG_SPLITTER,
            xslt("records:2", "records", "record",
                    "stroom:format-date(data[@name='time']/@value, 'yyyy-MM-dd''T''HH:mm:ss.SSSX')",
                    "data[@name='host']/@value", "data[@name='user']/@value", "data[@name='ip']/@value", "true()",
                    "SSH-Accepted", "SSH logon accepted"));

    @Inject
    private SecurityContext securityContext;
    @Inject
    private ProcessorFilterService processorFilterService;
    @Inject
    private ExplorerNodeService explorerNodeService;

    @Test
    void aJsonSampleGoesToValidEvents() {
        onboard(JSON);
    }

    @Test
    void anXmlSampleGoesToValidEvents() {
        onboard(XML);
    }

    @Test
    void aSyslogSampleGoesToValidEvents() {
        onboard(SYSLOG);
    }

    @Test
    void theCsvBuildIsPromotedThenFixedInPlaceThroughAWorkingCopy() {
        final Onboarded csv = onboard(CSV_CASE);
        final String dest = "System/E2E Promoted/Events";
        final Map<String, Object> everything = Map.of("Feed", dest, "Pipeline", dest, "XSLT", dest,
                "TextConverter", dest, "Documentation", dest);
        assertThat(tags(csv.pipelineUuid())).containsExactlyInAnyOrder("mcp-generated", "mcp-managed");
        // Stepped clean and documented: nothing outstanding (build_status reads the build's record from Stroom).
        assertThat(list(json("build_status", args("build", csv.build())), "before_promotion")).isEmpty();

        final Map<String, Object> result = promote(csv.build(), everything);
        assertThat(result).doesNotContainKey("promoted_with_warnings");
        final List<String> promoted = strings(result, "promoted");
        assertThat(promoted).hasSizeGreaterThanOrEqualTo(5);
        // Asked for by the user: the same layout they approved, and a link to open each folder in Stroom.
        assertThat(String.valueOf(result.get("layout"))).startsWith(dest + "/");
        assertThat(list(result, "folders")).anySatisfy(folder -> {
            final Map<String, Object> shown = (Map<String, Object>) folder;
            assertThat(shown).containsEntry("folder", dest);
            assertThat(String.valueOf(shown.get("link"))).contains("docType=Folder&docUuid=");
        });
        assertThat(promoted.subList(0, 2)).containsExactly("created folder System/E2E Promoted",
                "created folder " + dest);
        String text = documentation(csv.docUuid());
        assertThat(text).contains("translation e2e test");
        // The build's changes, one version each: a row of the XSLT's history (its Documentation tab, not its
        // code), a row at the end of its documentation.
        final String description = readXslt(csv.xsltUuid()).getDescription();
        final List<XsltVersionHistory.Row> rows = XsltVersionHistory.history(description);
        assertThat(rows).extracting(XsltVersionHistory.Row::version).containsExactly("1");
        assertThat(rows.getFirst().how()).isEqualTo("agent");
        assertThat(text).contains("| 1 |");
        assertThat(readXslt(csv.xsltUuid()).getData()).doesNotContain("version history");
        assertThat(promoted).contains("removed the build's workspace folder, now empty");
        assertThat(findFolder("System/MCP Workspace/" + csv.build())).isEmpty();
        final List<Map<String, Object>> made = list(result, "processing_filters");
        assertThat(made).singleElement().satisfies(filter -> assertThat(filter)
                .containsEntry("pipeline", csv.pipelineName())
                .containsEntry("feed", csv.feed())
                .containsEntry("enabled", false));
        final ProcessorFilter stored = securityContext.asProcessingUserResult(() -> processorFilterService
                .fetch((int) number(made.getFirst().get("filter_id"))).orElseThrow());
        // No minimum creation time (asked for by the user): enabled, it processes the feed's existing data too.
        assertThat(stored.isEnabled()).isFalse();
        assertThat(stored.getMinMetaCreateTimeMs()).isNull();
        assertThat(folderOf(csv.pipelineUuid())).isEqualTo(dest);
        assertThat(tags(csv.pipelineUuid())).containsExactly("mcp-generated");

        // Documented again after promotion (seen in VS Code): through a working copy, written back by promoting
        // the build again, not a second doc of its name in a new workspace folder.
        final Map<String, Object> redo = agreed("write_documentation", args("build", csv.build(),
                "pipeline_uuid", csv.pipelineUuid(),
                "markdown", "# " + csv.pipelineName() + "\n\n## Purpose and data\n\nReworded after promotion.\n\n"
                            + "## Field mapping\n\n| XPath | From |\n| --- | --- |\n"
                            + "| `EventSource/User/Id` | the user field |\n",
                "change", "Purpose reworded after promotion"));
        dump("write_documentation after promotion", redo);
        assertThat(map(redo, "working_copy_of")).containsEntry("uuid", csv.docUuid());
        assertThat(JsonUtil.writeValueAsString(redo.get("next"), false)).contains("promote_build");
        final Map<String, Object> again = promote(csv.build(), Map.of());
        text = documentation(csv.docUuid());
        assertThat(text).contains("Reworded after promotion");
        assertThat(documentsNamed(csv.pipelineName(), "Documentation")).as("the only one of its name")
                .filteredOn(doc -> csv.pipelineName().equals(doc.getName()))
                .hasSize(1);
        assertThat(strings(again, "promoted")).anySatisfy(line -> assertThat(line).contains("back over"));

        // The in-place fix, through a working copy.
        final String fixBuild = "e2e-fix";
        final Map<String, Object> copy = json("copy_pipeline", args("build", fixBuild,
                "source_uuid", csv.pipelineUuid(), "new_name", csv.pipelineName() + "-WORKING",
                "working_copy", true));
        final Map<String, Object> xsltCopy = ((List<Map<String, Object>>) copy.get("copied_documents")).stream()
                .filter(doc -> "XSLT".equals(doc.get("type")))
                .findFirst()
                .orElseThrow();
        final String changed = readXslt((String) xsltCopy.get("uuid")).getData()
                .replace("<Description>User logon</Description>", "<Description>Interactive user logon</Description>");
        final Map<String, Object> diff = json("compare_outputs", args("pipeline_uuid", csv.pipelineUuid(),
                "stream_ids", List.of(csv.raw()), "draft_code", Map.of("translationFilter", changed)));
        assertThat(((List<Map<String, Object>>) diff.get("fields_changed")))
                .extracting(field -> field.get("path"))
                .containsExactly("Event/EventDetail/Description");
        json("save_xslt", args("uuid", xsltCopy.get("uuid"), "code", changed));
        final String direct = refusal("save_xslt", args("uuid", csv.xsltUuid(), "code", changed));
        assertThat(direct).containsAnyOf("not created by this server", "working copy");
        final List<String> before = strings(json("build_status", args("build", fixBuild)), "before_promotion");
        assertThat(before).as("the working copy's changed code was compared but never stepped clean")
                .singleElement()
                .satisfies(warning -> assertThat(warning).contains("no clean step"));
        final Map<String, Object> fixed = promote(fixBuild, Map.of());
        dump("promote_build fix", fixed);
        assertThat(findFolder("System/MCP Workspace/" + fixBuild)).isEmpty();
        assertThat(list(fixed, "promoted_with_warnings")).isEqualTo(before);
        assertThat(readXslt(csv.xsltUuid()).getData()).contains("Interactive user logon");
        final List<DocRef> backups = documentsNamed(csv.pipelineName() + " backup", "XSLT");
        assertThat(backups).isNotEmpty().allSatisfy(backup ->
                assertThat(tags(backup.getUuid())).contains("mcp-generated"));
    }

    /// promote_build names every document without a destination in one refusal (seen in testing: one failed call
    /// per type), and a destination path works with or without `System/` (TOOLS.md, promote_build), pinned on a
    /// real Stroom.
    @Test
    void everyDocumentWithoutADestinationIsNamedInOneRefusal() {
        final String build = "e2e-places";
        final String feed = "E2E-PLACES";
        final String feedUuid = (String) createFeed(build, feed).get("uuid");
        json("save_text_converter", args("build", build, "name", feed, "converter_type", "DATA_SPLITTER",
                "code", CSV_SPLITTER));
        json("save_xslt", args("build", build, "name", feed + "-Events", "code", CSV_XSLT));
        final String refused = refusal("promote_build", args("build", build,
                "destinations", Map.of("Feed", "Feeds/Places")));
        dump("promote_build refused", refused);
        assertThat(refused).as("both, in one refusal").contains("No destination for", "TextConverter '" + feed
                + "'", "XSLT '" + feed + "-Events'");
        // Given without System/, as find_documents gives folders.
        final Map<String, Object> destinations = Map.of("Feed", "Feeds/Places", "TextConverter", "/Feeds/Places",
                "XSLT", "Feeds/Places");
        final Map<String, Object> promoted = promote(build, destinations);
        dump("promote_build", promoted);
        assertThat(folderOf(feedUuid)).isEqualTo("System/Feeds/Places");
    }

    // promote_build: the layout shown first (needs_review), then promoted once the user agrees in the form.
    private Map<String, Object> promote(final String build, final Map<String, Object> destinations) {
        final Map<String, Object> review = json("promote_build", args("build", build,
                "destinations", destinations));
        assertThat(review).as("the layout first: %s", review).containsEntry("status", "needs_review");
        return json("promote_build", args("build", build, "destinations", destinations,
                "reviewed", review.get("review_id")));
    }

    // Documents of a type whose name starts with the given text.
    private List<DocRef> documentsNamed(final String prefix, final String type) {
        return securityContext.asProcessingUserResult(() -> explorerNodeService.getDescendants(
                        explorerNodeService.getRoot().getDocRef()).stream()
                .filter(node -> type.equals(node.getType()) && node.getName().startsWith(prefix))
                .map(ExplorerNode::getDocRef)
                .toList());
    }

    // The hand-written translation: one Event per record, Authenticate.
    private static String xslt(final String defaultNs,
                               final String rootMatch,
                               final String recordMatch,
                               final String timeExpr,
                               final String host,
                               final String user,
                               final String ip,
                               final String success,
                               final String typeId,
                               final String description) {
        return "<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n"
               + "<xsl:stylesheet xpath-default-namespace=\"" + defaultNs + "\" xmlns=\"event-logging:3\" "
               + "xmlns:stroom=\"stroom\"\n    xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
               + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" version=\"3.0\">\n"
               + "  <xsl:template match=\"" + rootMatch + "\">\n"
               + "    <Events xsi:schemaLocation=\"event-logging:3 file://event-logging-v" + VERSION + ".xsd\" "
               + "Version=\"" + VERSION + "\">\n"
               + "      <xsl:apply-templates />\n"
               + "    </Events>\n"
               + "  </xsl:template>\n"
               + "  <xsl:template match=\"" + recordMatch + "\">\n"
               + "    <Event>\n"
               + "      <EventTime><TimeCreated><xsl:value-of select=\"" + timeExpr
               + "\" /></TimeCreated></EventTime>\n"
               + "      <EventSource>\n"
               + "        <System><Name>E2E</Name><Environment>Dev</Environment></System>\n"
               + "        <Generator>e2e-translation</Generator>\n"
               + "        <Device><HostName><xsl:value-of select=\"" + host + "\" /></HostName><IPAddress>"
               + "<xsl:value-of select=\"" + ip + "\" /></IPAddress></Device>\n"
               + "        <User><Id><xsl:value-of select=\"" + user + "\" /></Id></User>\n"
               + "      </EventSource>\n"
               + "      <EventDetail>\n"
               + "        <TypeId>" + typeId + "</TypeId>\n"
               + "        <Description>" + description + "</Description>\n"
               + "        <Authenticate>\n"
               + "          <Action>Logon</Action>\n"
               + "          <User><Id><xsl:value-of select=\"" + user + "\" /></Id></User>\n"
               + "          <Outcome><Success><xsl:value-of select=\"" + success + "\" /></Success></Outcome>\n"
               + "        </Authenticate>\n"
               + "      </EventDetail>\n"
               + "    </Event>\n"
               + "  </xsl:template>\n"
               + "</xsl:stylesheet>\n";
    }


    // --------------------------------------------------------------------------------
}
