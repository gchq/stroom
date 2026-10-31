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

import stroom.pipeline.shared.XsltDoc;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// Standing instructions (AGENTS docs), driving the real tools. Covered whole: three AGENTS Documentation docs, one
/// directly under System (applies everywhere), one in a build's folder (applies to the feed in it) and one in another
/// build's folder (does not); get_instructions for the feed returns the first two, general first, and lists the third,
/// as start_build hands them back; and the required imports the build's doc names: the generated translation imports
/// the shared XSLT, the reply names what it offers, it steps clean, and a hand-written translation without the import
/// is refused.
///
/// The database is cleared after each test.
@SuppressWarnings("unchecked")
class TestE2eInstructions extends AbstractE2eTest {

    private static final String STAMP = "E2E1";
    private static final String WORKSPACE = "System/MCP Workspace/";

    @Override
    boolean followsNext() {
        return true;
    }

    @Test
    void standingInstructions() {
        final String build = "instr";
        final String other = "instr-other";
        final String feed = "INSTR";
        createFeed(build, feed);
        final String buildFolder = WORKSPACE + build;
        folder(WORKSPACE + other);
        documentationDoc("System", "AGENTS", "Global " + STAMP + ": map the acting user to EventSource/User/Id.");
        final String shared = "E2E-Stream-Meta-" + STAMP;
        documentationDoc(buildFolder, "AGENTS", "Build " + STAMP + ": TypeId is the vendor event code.\n\n"
                                                + "## Required imports\n- translation: " + shared + "\n");
        final String otherDoc = documentationDoc(WORKSPACE + other, "AGENTS", "Other " + STAMP
                                                                               + ": not for this feed.");

        final Map<String, Object> result = json("get_instructions", args("feeds", List.of(feed)));
        dump("get_instructions", result);
        final List<Map<String, Object>> mine = list(result, "instructions").stream()
                .filter(instruction -> String.valueOf(instruction.get("instructions")).contains(STAMP))
                .toList();
        assertThat(mine).as("the root doc and the build folder doc apply, general first")
                .extracting(instruction -> String.valueOf(instruction.get("instructions")).split(":")[0])
                .containsExactly("Global " + STAMP, "Build " + STAMP);
        assertThat(mine.get(0).get("applies_to")).as("the root doc's scope").isEqualTo("everything");
        assertThat((String) mine.get(1).get("applies_to")).as("the build folder doc's scope")
                .endsWith(build + " and below");
        assertThat(list(result, "other_instruction_docs")).as("the other build's doc is listed, not applied")
                .anyMatch(doc -> otherDoc.equals(doc.get("uuid")));
        final Map<String, Object> started = json("start_build", args("build", build, "feeds", List.of(feed)));
        final List<String> handed = list(map(started, "standing_instructions"), "instructions").stream()
                .map(instruction -> String.valueOf(instruction.get("instructions")))
                .filter(text -> text.contains(STAMP))
                .map(text -> text.split(":")[0])
                .toList();
        assertThat(handed).as("start_build hands back the same standing instructions")
                .containsExactly("Global " + STAMP, "Build " + STAMP);

        // ### required imports (asked for by the user: every translation imports a Stream-Meta XSLT)
        // The shared XSLT the build's AGENTS doc requires, as an environment keeps one: a named template.
        final String lib = create(XsltDoc.TYPE, shared, "System").getUuid();
        editXslt(lib, doc -> doc.copy().data("<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
                                             + "xmlns=\"event-logging:3\" xmlns:stroom=\"stroom\" version=\"3.0\">"
                                             + "<xsl:template name=\"streamFeed\"><Data Name=\"Feed\" "
                                             + "Value=\"{stroom:feed-name()}\"/></xsl:template></xsl:stylesheet>")
                .build());
        final long raw = upload(feed, "{\"ts\": \"2026-10-01T08:00:00Z\", \"u\": \"alice\"}\n"
                                      + "{\"ts\": \"2026-10-01T08:05:00Z\", \"u\": \"bob\"}\n");
        final Map<String, Object> mapping = Map.of("input", "json", "json_layout", "lines",
                "common", List.of(
                        Map.of("path", "EventTime/TimeCreated", "field", "ts", "time_format",
                                "yyyy-MM-dd'T'HH:mm:ssX"),
                        Map.of("path", "EventSource/System/Name", "value", "Instr"),
                        Map.of("path", "EventSource/System/Environment", "value", "Test"),
                        Map.of("path", "EventSource/Generator", "value", "e2e"),
                        Map.of("path", "EventSource/Device/HostName", "value", "h1"),
                        Map.of("path", "EventSource/User/Id", "field", "u"),
                        Map.of("path", "EventDetail/TypeId", "value", "Logon")),
                "events", List.of(Map.of("name", "logon", "fields", List.of(
                        Map.of("path", "EventDetail/Authenticate/Action", "value", "Logon"),
                        Map.of("path", "EventDetail/Authenticate/User/Id", "field", "u")))));
        final Map<String, Object> built = agreed("build_translation_xslt", args("mapping", mapping, "stream_ids",
                List.of(raw), "build", build, "name", feed + "-Events", "feeds", List.of(feed),
                "schema_version", VERSION));
        dump("build_translation_xslt", built);
        final String xsltUuid = built.get("saved") instanceof final Map<?, ?> saved
                ? (String) saved.get("uuid")
                : null;
        assertThat(xsltUuid).as("saved: %s", built.get("problems")).isNotNull();
        assertThat(readXslt(xsltUuid).getData()).as("the translation imports %s, as the build's AGENTS doc "
                                                    + "requires", shared)
                .contains("<xsl:import href=\"" + shared + "\"");
        final Map<String, Object> offered = list(built, "required_imports").stream().findFirst().orElse(Map.of());
        assertThat((Map<String, Object>) offered.getOrDefault("named_templates", Map.of()))
                .as("the reply names what it offers: %s", offered).containsKey("streamFeed");
        assertThat(folderOf(xsltUuid)).as("the XSLT is in the build's Events folder")
                .isEqualTo(buildFolder + "/Events");
        final String pipeline = (String) createPipeline(build, feed + "-Events",
                templateUuid("translation", "Event Data (JSON)")).get("uuid");
        final Map<String, Object> stepped = stepSample(pipeline, List.of(raw));
        assertThat(stepped.get("verdict")).as("stepped clean in Stroom, the import resolved: %s",
                said(list(stepped, "groups"))).isEqualTo("clean");
        final String hand = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
                            + "xmlns=\"event-logging:3\" version=\"3.0\"><xsl:template match=\"/\"><Events/>"
                            + "</xsl:template></xsl:stylesheet>";
        final String refused = refusal("save_xslt", args("build", build, "name", feed + "-Hand", "code", hand));
        assertThat(refused).as("a hand-written translation without it is refused").contains("import '" + shared
                                                                                             + "'");
    }
}
