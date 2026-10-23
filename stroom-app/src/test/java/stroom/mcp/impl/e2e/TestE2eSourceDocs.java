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

import stroom.mcp.impl.McpTestClient;
import stroom.util.json.JsonUtil;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The Stroom MCP server's `dev/e2e_source_docs.py`: the user's vendor and event reference documentation, kept in
/// Stroom and used, driving the real tools. Every numbered part is covered:
///
/// 1. Documentation small enough to pass along: record_source_notes keeps it verbatim, the draft follows its
///    notes, the stepped events carry what it says, a contradicting mapping is reported, the documentation lists
///    the source fields with their meanings, and promotion puts the notes and the reference beside the feed.
/// 2. A long vendor manual kept in parts, read a passage at a time, every reply within a budget; and a status-code
///    table already in Stroom registered by uuid.
/// 3. An event catalogue guessed from a firewall sample is refused, then followed once corrected; a hand-written
///    XSLT writing every record as Unknown is blocked, and the draft's mapping saved over it steps clean.
///
/// A long Documentation doc read whole comes back as Stroom's read_document gives one (saved to the session's
/// workspace, with its outline), where describe_document cut it short with its outline: the merge TOOLS.md
/// describes.
@SuppressWarnings("unchecked")
class TestE2eSourceDocs extends AbstractE2eTest {

    private static final String SAMPLE = """
            ts,host,evt,usr,src,res
            2026-10-01T08:00:00Z,dc01,4624,alice,10.0.0.1,0x0
            2026-10-01T08:05:00Z,dc01,4625,bob,10.0.0.2,0xC000006A
            2026-10-01T08:10:00Z,dc01,4634,alice,10.0.0.1,0x0
            """;
    private static final String REFERENCE = """
            # Acme directory audit log reference

            ## Fields

            | Field | Meaning |
            | --- | --- |
            | evt | Event id: what happened (see Events) |
            | usr | The account the event is about |
            | src | The workstation's IP address |
            | res | Status: 0x0 success, 0xC000006A failed (bad password) |

            ## Events

            | evt | Meaning |
            | --- | --- |
            | 4624 | An account logged on |
            | 4625 | An account failed to log on |
            | 4634 | An account logged off |
            """;
    private static final List<Map<String, Object>> FIELDS = List.of(
            Map.of("field", "usr", "meaning", "The account the event is about", "event_logging_path",
                    "EventSource/User/Id"),
            Map.of("field", "src", "meaning", "The workstation's IP address", "event_logging_path",
                    "EventSource/Client/IPAddress"),
            Map.of("field", "res", "meaning", "Status", "values", Map.of("0x0", "success", "0xC000006A",
                    "failed: bad password")),
            Map.of("field", "evt", "meaning", "Event id: what happened"));
    private static final List<Map<String, Object>> EVENTS = List.of(
            Map.of("event", "4624", "description", "An account logged on", "event_detail", "Authenticate",
                    "type_id", "Logon", "field", "evt", "value", "4624", "action", "Logon", "success", true),
            Map.of("event", "4625", "description", "An account failed to log on", "event_detail", "Authenticate",
                    "type_id", "Logon failed", "field", "evt", "value", "4625", "action", "Logon", "success", false),
            Map.of("event", "4634", "description", "An account logged off", "event_detail", "Authenticate",
                    "type_id", "Logoff", "field", "evt", "value", "4634", "action", "Logoff"));
    private static final int REPLY_BUDGET = 64_000;
    // Characters the agent sends per record_source_notes call for a long document.
    private static final int PART = 400_000;
    private static final String CODES = """
            # Status codes

            | res | Meaning |
            | --- | --- |
            | 0xC000006A | Bad password |
            | 0xC0000234 | Account locked out |
            """;
    private static final String FIREWALL = """
            timestamp,device,event_type,src_ip,src_port,dst_ip,dst_port,protocol,action,rule_id,username,message
            2026-10-01T09:00:12+10:00,FW1,TRAFFIC,192.0.2.10,54321,198.51.100.20,443,TCP,ALLOW,1001,,HTTPS allowed
            2026-10-01T09:01:05+10:00,FW1,TRAFFIC,203.0.113.45,49822,192.0.2.25,22,TCP,DENY,2003,,SSH blocked
            2026-10-01T09:02:17+10:00,FW1,TRAFFIC,192.0.2.11,53002,198.51.100.53,53,UDP,ALLOW,1002,,DNS allowed
            2026-10-01T09:10:00+10:00,FW1,ADMIN,192.0.2.5,50000,192.0.2.1,443,HTTPS,LOGIN_SUCCESS,0,admin,Logged in
            2026-10-01T09:11:00+10:00,FW1,ADMIN,192.0.2.5,50000,192.0.2.1,443,HTTPS,LOGIN_FAILED,0,root,Bad password
            """;
    private static final List<Map<String, Object>> GUESSED = List.of(
            Map.of("event", "TRAFFIC-ALLOW", "description", "Network traffic allowed", "field", "event_type",
                    "value", "TRAFFIC", "event_detail", "Allow"),
            Map.of("event", "TRAFFIC-DENY", "description", "Network traffic blocked", "field", "event_type",
                    "value", "TRAFFIC", "event_detail", "Deny"),
            Map.of("event", "LOGIN-SUCCESS", "description", "Administrator logged in", "field", "action",
                    "value", "LOGIN_SUCCESS", "event_detail", "Authenticate", "success", true),
            Map.of("event", "LOGIN-FAILED", "description", "Administrator failed to log in", "field", "action",
                    "value", "LOGIN_FAILED", "event_detail", "Authenticate", "success", false));
    // Every record as an empty Unknown, as the agent's XSLT wrote the TRAFFIC records.
    private static final String UNKNOWN_XSLT = """
            <?xml version="1.1" encoding="UTF-8"?>
            <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns="event-logging:3" \
            xmlns:stroom="stroom"
                xpath-default-namespace="records:2" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
            version="2.0">
              <xsl:template match="records">
                <Events xsi:schemaLocation="event-logging:3 file://event-logging-vVERSION.xsd" Version="VERSION">
                  <xsl:apply-templates/>
                </Events>
              </xsl:template>
              <xsl:template match="record">
                <Event>
                  <EventTime><TimeCreated><xsl:value-of select="stroom:format-date(data[@name='timestamp']/@value, \
            'yyyy-MM-dd''T''HH:mm:ssXXX')"/></TimeCreated></EventTime>
                  <EventSource><System><Name>FW</Name><Environment>Eval</Environment></System><Generator>hand\
            </Generator>
                    <Device><HostName><xsl:value-of select="data[@name='device']/@value"/></HostName></Device>\
            </EventSource>
                  <EventDetail><TypeId><xsl:value-of select="data[@name='event_type']/@value"/></TypeId><Unknown/>\
            </EventDetail>
                </Event>
              </xsl:template>
            </xsl:stylesheet>
            """;

    @Override
    boolean followsNext() {
        return true;
    }

    @Test
    void small() {
        final String build = "e2e-srcdocs";
        final String feed = "E2E-SRCDOCS";
        // ### 1. documentation kept in Stroom, and the notes from it used
        createFeed(build, feed);
        final long raw = upload(feed, SAMPLE);
        final Map<String, Object> noted = agreed("record_source_notes", args("build", build, "source",
                "Acme directory", "summary", "The directory audit log.", "fields", FIELDS, "events", EVENTS,
                "documents", List.of(Map.of("title", "audit log reference", "text", REFERENCE))));
        final List<Map<String, Object>> kept = list(noted, "documents_kept");
        assertThat(kept).as("the reference kept verbatim in Stroom").hasSize(1);
        assertThat(kept.getFirst()).containsEntry("name", "Acme directory reference - audit log reference");
        assertThat(documentation((String) kept.getFirst().get("uuid"))).as("its tables as the vendor wrote them")
                .contains("| 4625 | An account failed to log on |");

        final Map<String, Object> draft = json("draft_translation_mapping", args("stream_ids", List.of(raw),
                "source_name", "Acme directory", "system_name", "Acme directory", "environment", "Eval",
                "build", build, "schema_version", VERSION));
        assertThat(strings(draft, "notes").getFirst()).as("drafted from the notes")
                .startsWith("Drafted from the source documentation");
        saveTextConverter(build, feed, CSV_SPLITTER);
        final Map<String, Object> saved = agreed("build_translation_xslt", args("mapping", draft.get("mapping"),
                "stream_ids", List.of(raw), "build", build, "name", feed + "-Events", "schema_version", VERSION));
        assertThat(saved.get("source_notes_check")).as("checked against the catalogue: %s", saved)
                .isEqualTo("the mapping agrees with the event catalogue");
        final String pipeline = (String) createPipeline(build, feed + "-Events",
                templateUuid("translation", "Event Data (Text)")).get("uuid");
        final Map<String, Object> stepped = stepSample(pipeline, List.of(raw));
        assertThat(stepped.get("verdict")).as("stepped clean: %s", said(list(stepped, "groups")))
                .isEqualTo("clean");
        assertThat(translationOutput(pipeline, raw, 1)).as("the failed logon (evt=4625, record 2) comes out as the "
                                                 + "documentation says: TypeId 'Logon failed', a Logon that did not "
                                                 + "succeed")
                .contains("<TypeId>Logon failed</TypeId>", "<Success>false</Success>", "<Action>Logon</Action>");

        // ### a mapping that contradicts the documentation is reported
        final Map<String, Object> wrong = copy((Map<String, Object>) draft.get("mapping"));
        for (final Map<String, Object> rule : E2eFixtures.rules(wrong)) {
            if ("4625".equals(rule.get("name"))) {
                final List<Map<String, Object>> fields = new ArrayList<>(E2eFixtures.entries(rule).stream()
                        .filter(field -> !"EventDetail/TypeId".equals(field.get("path")))
                        .toList());
                fields.add(Map.of("path", "EventDetail/TypeId", "value", "Logon"));
                rule.put("fields", fields);
            }
        }
        // The check runs where the agent saves (build=): this mapping is saved apart, linked to nothing.
        final Map<String, Object> checked = agreed("build_translation_xslt", args("mapping", wrong, "stream_ids",
                List.of(raw), "build", build, "name", feed + "-Events-contradicting", "schema_version", VERSION));
        assertThat(strings(checked, "warnings")).as("reported")
                .anyMatch(warning -> warning.contains("TypeId is 'Logon failed'"));

        // ### documented with the source fields, promoted beside the feed
        final Map<String, Object> written = agreed("write_documentation", args("build", build, "pipeline_uuid",
                pipeline, "markdown", "## Purpose and data\n\nDirectory logons.\n", "change", "Created",
                "stream_ids", List.of(raw)));
        final String section = String.valueOf(written.getOrDefault("field_mapping", ""));
        assertThat(section).as("the field mapping lists each source field with what the documentation says")
                .contains("### Source fields", "`0xC000006A`: failed: bad password");
        assertThat(section.lines().findFirst().orElse("")).as("the section says which records its counts are of")
                .startsWith("Counts are of ").contains("of stream " + raw);
        final String folder = "System/E2E Feeds/srcdocs";
        final Map<String, Object> result = agreed("promote_build", args("build", build, "destinations", Map.of(
                "Feed", folder, "Pipeline", folder, "XSLT", folder, "TextConverter", folder)));
        dump("promote_build", result.get("promoted"));
        for (final String name : List.of("Acme directory source notes",
                "Acme directory reference - audit log reference")) {
            assertThat(documentsIn(folder, "Documentation", name)).as("'%s' beside the feed, in %s", name, folder)
                    .isNotEmpty();
        }
    }

    @Test
    void large() {
        // ### 2. a long manual kept in parts, read a passage at a time; a table already in Stroom registered
        final String build = "e2e-srcdocs-large";
        final String text = manual(4000);
        final List<String> parts = new ArrayList<>();
        for (int i = 0; i < text.length(); i += PART) {
            parts.add(text.substring(i, Math.min(text.length(), i + PART)));
        }
        final Map<String, Integer> replies = new LinkedHashMap<>();
        final List<Map<String, Object>> kept = new ArrayList<>();
        for (int n = 1; n <= parts.size(); n++) {
            final Map<String, Object> noted = agreed("record_source_notes", args("build", build, "source",
                    "Acme directory", "summary", "The directory event reference.", "fields", List.of(),
                    "events", List.of(), "documents", List.of(Map.of("title", "event reference part " + n, "text",
                            parts.get(n - 1), "source", "event-reference.md"))));
            replies.put("record_source_notes part " + n, jsonOf(noted).length());
            kept.addAll(list(noted, "documents_kept"));
        }
        assertThat(text.length()).as("a manual of over a million characters").isGreaterThan(1_000_000);
        assertThat(kept).as("kept in %s parts, one call each", parts.size()).hasSize(parts.size());
        // A table the user had already made in Stroom (in the UI, say): registered by uuid, left where it is.
        final String table = documentationDoc("System/MCP Workspace/" + build, "status codes", CODES);
        final Map<String, Object> noted = agreed("record_source_notes", args("build", build, "source",
                "Acme directory", "summary", "The directory event reference.", "fields", List.of(), "events",
                List.of(), "documents", List.of(Map.of("title", "status codes", "uuid", table))));
        replies.put("record_source_notes by uuid", jsonOf(noted).length());
        assertThat(list(noted, "documents_kept")).as("a document already in Stroom registered by uuid")
                .isNotEmpty();
        assertThat(list(noted, "documents_kept").getFirst().get("already_in_stroom"))
                .as("named in the notes as already in Stroom").isNotIn(null, false, "");
        // The part the event's section landed in.
        final Map<String, Object> holder = kept.stream()
                .filter(part -> documentation((String) part.get("uuid")).contains("4625"))
                .findFirst()
                .orElseThrow();
        final CallToolResult whole = client.call("read_document", args("uuid", holder.get("uuid")));
        final String wholeText = McpTestClient.text(whole);
        replies.put("read_document", wholeText.length());
        assertThat(wholeText).as("read whole, a long part isn't returned whole, but with its outline")
                .contains("too large to show in full", "Its outline:");
        final Map<String, Object> passage = json("read_document", args("uuid", holder.get("uuid"), "find",
                "Event 4625"));
        replies.put("read_document find", jsonOf(passage).length());
        assertThat(((Number) passage.get("matches")).intValue()).as("find='Event 4625' finds it")
                .isGreaterThanOrEqualTo(1);
        assertThat(list(passage, "passages")).as("and returns the passage about it")
                .anyMatch(found -> String.valueOf(found.get("text")).contains("0xC000006A"));
        final Map.Entry<String, Integer> worst = replies.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElseThrow();
        assertThat(worst.getValue()).as("the largest reply is %s's", worst.getKey()).isLessThanOrEqualTo(
                REPLY_BUDGET);
    }

    @Test
    void guessed() {
        final String build = "e2e-fwnotes";
        final String feed = "E2E-FWNOTES";
        // ### 3. a catalogue guessed from the sample, and a hand-written XSLT writing Unknown
        createFeed(build, feed);
        final long raw = upload(feed, FIREWALL);
        final String said = refusal("record_source_notes", args("build", build, "source", "Firewall", "summary",
                "A firewall.", "fields", List.of(), "events", GUESSED));
        assertThat(said).as("the guessed catalogue is refused, naming Network/Permit and the twin events")
                .contains("'Allow' is not an EventDetail action element", "Network/Permit",
                        "TRAFFIC-ALLOW and TRAFFIC-DENY are both event_type=TRAFFIC");
        final List<Map<String, Object>> fixed = new ArrayList<>();
        fixed.add(with(GUESSED.get(0), Map.of("field", "action", "value", "ALLOW", "event_detail",
                "Network/Permit")));
        fixed.add(with(GUESSED.get(1), Map.of("field", "action", "value", "DENY", "event_detail", "Network/Deny")));
        fixed.addAll(GUESSED.subList(2, GUESSED.size()));
        agreed("record_source_notes", args("build", build, "source", "Firewall", "summary", "A firewall.",
                "fields", List.of(), "events", fixed));

        saveTextConverter(build, feed, CSV_SPLITTER);
        final String hand = saveXslt(build, feed + "-Events", UNKNOWN_XSLT.replace("VERSION", VERSION));
        final String pipeline = (String) createPipeline(build, feed + "-Events",
                templateUuid("translation", "Event Data (Text)")).get("uuid");
        Map<String, Object> stepped = stepSample(pipeline, List.of(raw));
        final Map<String, Object> first = list(stepped, "groups").stream().findFirst().orElse(Map.of());
        assertThat(stepped.get("verdict")).as("the hand-written XSLT's Unknown events block").isEqualTo("blocking");
        assertThat(String.valueOf(first.get("reason"))).as("nobody agreed to them").contains("nobody agreed");
        assertThat(String.valueOf(list(first, "examples").getFirst().get("message")))
                .startsWith("5 of 5 records come out as EventDetail/Unknown");

        final Map<String, Object> draft = json("draft_translation_mapping", args("stream_ids", List.of(raw),
                "source_name", "Firewall", "system_name", "FW", "environment", "Eval", "build", build,
                "schema_version", VERSION));
        dump("draft", draft.get("mapping"));
        agreed("build_translation_xslt", args("mapping", draft.get("mapping"), "stream_ids", List.of(raw),
                "build", build, "name", feed + "-Events", "uuid", hand, "schema_version", VERSION));
        stepped = stepSample(pipeline, List.of(raw));
        assertThat(stepped.get("verdict")).as("saved from the mapping, it steps clean: %s",
                said(list(stepped, "groups"))).isEqualTo("clean");
        final String allowed = translationOutput(pipeline, raw, 0);
        final String denied = translationOutput(pipeline, raw, 1);
        assertThat(allowed).as("the allowed connection is Network/Permit").contains("<Permit>")
                .doesNotContain("<Unknown");
        assertThat(denied).as("the blocked one Network/Deny").contains("<Deny>").doesNotContain("<Unknown");
    }

    // A vendor manual far larger than a model's context: a section per event id.
    private static String manual(final int events) {
        final List<String> parts = new ArrayList<>();
        parts.add("# Acme directory event reference\n\nEvery event the directory writes, by id.\n");
        for (int n = 1000; n < 1000 + events; n++) {
            parts.add("## Event " + n + "\n\nWritten when operation " + n + " completes. "
                      + "Background detail. ".repeat(30) + "\n");
        }
        parts.add(parts.size() / 2, "## Event 4625\n\nAn account failed to log on. Status 0xC000006A means the "
                                    + "password was wrong; 0xC0000234 means the account is locked out.\n");
        return String.join("\n", parts);
    }

    private static Map<String, Object> copy(final Map<String, Object> mapping) {
        return JsonUtil.readValue(jsonOf(mapping), Map.class);
    }

    private static Map<String, Object> with(final Map<String, Object> entry, final Map<String, Object> changes) {
        final Map<String, Object> changed = new LinkedHashMap<>(entry);
        changed.putAll(changes);
        return changed;
    }
}
