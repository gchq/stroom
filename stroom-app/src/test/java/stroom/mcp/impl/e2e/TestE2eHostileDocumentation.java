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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/// Documentation generated from hostile data, driving the REAL tools on a real Stroom: Stroom renders a
/// Documentation doc's Markdown with showdown, unsanitised (HTML, images and links render), so a value from the data
/// written as it is would be markup. The Azure audit sample's bodies (a mail's HTML, an XSLT fragment, a CDATA
/// section, entities, SharePoint's XML) are translated, and the pipeline documented with write_documentation: no tag
/// is left outside code anywhere in the doc Stroom keeps, the sampled bodies are shown as code, and the agent's own
/// HTML is escaped.
class TestE2eHostileDocumentation extends AbstractE2eTest {

    private static final String BUILD = "hostile-docs";
    private static final String FEED = "E2E-HOSTILE-DOCS";
    private static final List<String> OPERATIONS = List.of("\"Operation\": \"Send\"",
            "\"Operation\": \"MessageSent\"", "\"Operation\": \"SharingSet\"");
    // A tag's start, a comment's, a processing instruction's or a CDATA section's.
    private static final Pattern TAG = Pattern.compile("<[A-Za-z/!?][^\\n]{0,40}");

    @Test
    void noTagFromTheDataOrTheAgentIsLeftOutsideCode() {
        // The records holding the bodies: mail sent (HTML bodies, the subject with markup), Teams messages (HTML,
        // entities, backticks) and sharing (SharePoint's XML).
        final List<String> records = TestE2eSourceTypeGroups.lines("azure-audit/azure-audit-1.json", 200).lines()
                .filter(line -> OPERATIONS.stream().anyMatch(line::contains))
                .limit(60)
                .toList();
        assertThat(String.join("\n", records)).as("the hostile bodies are in the sample")
                .contains("<html><head>", "<xsl:value-of select=", "<![CDATA[", "&lt;user&gt;",
                        "<PermissionsGranted>");
        createFeed(BUILD, FEED);
        final long raw = upload(FEED, String.join("\n", records) + "\n");
        final Map<String, Object> built = agreed("build_translation_xslt", args("mapping", mapping(),
                "stream_ids", List.of(raw), "build", BUILD, "name", FEED + "-Events", "schema_version", VERSION));
        assertThat(built.get("ok")).as("XSLT saved: %s", built.get("problems")).isEqualTo(true);
        // The mail bodies run past the JSON parser's 10,000 characters (its default stringTruncateLength), whose cut is
        // for review since 2026-11-18 (it had been a benign Warning, the bodies cut): read whole, as the review says.
        final String pipeline = uuidOf(createPipeline(BUILD, FEED + "-Events",
                templateUuid("translation", "Event Data (JSON)"),
                docProperty("translationFilter", "xslt", uuidOf(map(built, "saved")), "XSLT"),
                valueProperty("jsonParser", "stringTruncateLength", 100_000)));
        assertThat(stepSample(pipeline, List.of(raw)).get("verdict")).as("the sample steps clean")
                .isEqualTo("clean");

        // The agent's markdown holds HTML too.
        final Map<String, Object> documented = agreed("write_documentation", args("build", BUILD, "pipeline_uuid",
                pipeline, "stream_ids", List.of(raw), "change", "Created", "markdown", "# " + FEED + "\n\n"
                        + "## Purpose and data\n\nMicrosoft 365 audit records. <img src=x onerror=alert(1)> Bodies "
                        + "hold HTML such as <b>bold</b>; in code `<b>` stays as it is.\n\n"
                        + "```html\n<p>an example body</p>\n```\n\n## Field mapping\n\nGenerated.\n"));
        dump("write_documentation", documented);
        final String doc = documentation(uuidOf(documented));

        final String outside = outsideCode(doc);
        final Matcher tag = TAG.matcher(outside);
        final boolean found = tag.find();
        assertThat(found).as("a tag outside code: %s", found
                ? tag.group()
                : "").isFalse();
        // The agent's HTML shown as text; its code as it was.
        assertThat(doc).contains("&lt;img src=x onerror=alert(1)>", "&lt;b>bold&lt;/b>", "in code `<b>` stays",
                "```html\n<p>an example body</p>\n```");
        // The bodies sampled are shown as code, readable.
        assertThat(doc).contains("`<html><head><meta charset=\"utf-8\"></head>", "`<PermissionsGranted>");
    }

    // The mapping an agent would send: each operation's bodies written where a reader of the Events sees them.
    private static Map<String, Object> mapping() {
        return Map.of("input", "json", "json_layout", "lines",
                "common", List.of(
                        Map.of("path", "EventTime/TimeCreated", "field", "CreationTime", "time_format",
                                "yyyy-MM-dd'T'HH:mm:ss"),
                        Map.of("path", "EventSource/System/Name", "value", "Microsoft 365"),
                        Map.of("path", "EventSource/System/Environment", "value", "Test"),
                        Map.of("path", "EventSource/Generator", "value", "Unified audit log"),
                        Map.of("path", "EventSource/Device/HostName", "value", "m365"),
                        Map.of("path", "EventSource/User/Id", "field", "UserId")),
                "events", List.of(
                        Map.of("name", "mail sent", "when", List.of(Map.of("field", "Operation", "equals", "Send")),
                                "fields", List.of(
                                        Map.of("path", "EventDetail/TypeId", "value", "Send"),
                                        Map.of("path", "EventDetail/Description", "field", "event.Item.Subject"),
                                        Map.of("path", "EventDetail/Send/Source/User/Id", "field", "UserId"),
                                        Map.of("path", "EventDetail/Send/Source/User/Data", "data_name", "body",
                                                "field", "event.Item.Body.Content"),
                                        Map.of("path", "EventDetail/Send/Destination/User/Id", "value",
                                                "recipients"))),
                        Map.of("name", "teams message", "when", List.of(Map.of("field", "Operation", "equals",
                                        "MessageSent")),
                                "fields", List.of(
                                        Map.of("path", "EventDetail/TypeId", "value", "MessageSent"),
                                        Map.of("path", "EventDetail/Description", "field", "event.Body.Content"),
                                        Map.of("path", "EventDetail/Send/Source/User/Id", "field", "UserId"),
                                        Map.of("path", "EventDetail/Send/Destination/User/Id", "field",
                                                "event.ChannelName"))),
                        Map.of("name", "sharing", "allow_unknown", "sharing changes, for a later build",
                                "fields", List.of(
                                        Map.of("path", "EventDetail/TypeId", "field", "Operation"),
                                        Map.of("path", "EventDetail/Description", "field", "event.EventData"),
                                        Map.of("path", "EventDetail/Unknown/Data", "data_name", "target",
                                                "field", "event.TargetUserOrGroupName")))));
    }

    // The Markdown without its fenced code blocks, code spans, the tools' hidden blocks, <br>s and autolinks: what
    // showdown would read as markup.
    private static String outsideCode(final String markdown) {
        return markdown.replaceAll("(?ms)^ {0,3}(`{3,}|~{3,}).*?^ {0,3}\\1[^\\n]*$", "")
                .replaceAll("(`+)[^`]*?(?:`(?!\\1)[^`]*?)*?\\1", "")
                .replaceAll("(?s)<!-- stroom-mcp.*?-->", "")
                .replaceAll("(?i)<br\\s*/?>", "")
                .replaceAll("<(?:https?|mailto):[^\\s<>]*>", "");
    }
}
