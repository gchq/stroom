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

import stroom.ai.impl.tools.documents.KeptMapping;
import stroom.mcp.impl.McpTestClient;

import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/// Hand edits to an aggregated feed's shared modules and its groups' XSLTs, through the REAL tools on a real Stroom,
/// on the synthetic PEGA sample (decided by the user, 2026-11-24):
///
/// * the promoted common module's Generator edited by hand in Stroom, then a later build's group that adds a shared
///   call to the module: the user is asked before anything is written, keeps the edit, and the module's new version
///   (V1.1) holds the edit beside the shared call; the new group's Events and documentation carry the edited value,
///   production's module is left as edited;
/// * a group XSLT's own Generator (its override of the module's part, in a later build's new version of the group)
///   edited by hand: a generated change to that field asks the user, and kept, is refused, nothing written;
///   rebuild_mapping carries the edit into the group's mapping, the group's module block and its import of the
///   common module kept, and the pipeline steps clean.
@SuppressWarnings("unchecked")
class TestE2eModuleHandEdits extends AbstractE2eTest {

    private static final String SECURITY = "PEGA security";
    private static final String INTEGRATION = "PEGA integration";
    private static final String MODULE_FORM = "The common module was edited by hand";
    private static final Pattern GENERATOR = Pattern.compile("<Generator>([^<]*)</Generator>");
    private static final String LIBRARY = "E2E-Module-Edits-Meta";
    private static final String LIBRARY_CODE = """
            <?xml version="1.1" encoding="UTF-8"?>
            <xsl:stylesheet xmlns="event-logging:3" xmlns:stroom="stroom" \
            xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="3.0">
              <!-- The stream's GUID, for tracing an event back to what was received. -->
              <xsl:template name="eventMeta">
                <Meta ContentType="stroom-guid"><xsl:value-of select="stroom:meta('GUID')" /></Meta>
              </xsl:template>
            </xsl:stylesheet>
            """;

    @Test
    void aPromotedCommonModulesHandEditIsKeptInItsNewVersionAndTheNextGroupCarriesIt() {
        final String feed = "E2E-PEGA-ME";
        final String folder = "E2E Feeds/PegaMe";
        final Promoted promoted = securityPromoted(feed, folder, "pega-me");
        final String library = sharedXslt(LIBRARY, LIBRARY_CODE);

        // The user edits the promoted common module's Generator by hand in Stroom.
        final String before = readXslt(promoted.common()).getData();
        final Matcher generator = GENERATOR.matcher(before);
        assertThat(generator.find()).as("the common module writes a Generator: %s", before).isTrue();
        final String edited = before.replace(generator.group(), "<Generator>Pega by hand</Generator>");
        editXslt(promoted.common(), doc -> doc.copy().data(edited).build());

        // A later build's group calls a shared template, which goes in the common module: asked first, kept.
        final String build = "pega-me-2";
        json("start_build", args("build", build, "feeds", List.of(feed)));
        client.answeringWith(answering(List.of("PegaIntegration")));
        json("survey_feed", args("feed", feed, "build", build));
        recordSourceTypes(build, feed, INTEGRATION);
        final Map<String, Object> mapping = withoutGenerator(TestE2eSourceTypeGroups.integrationMapping());
        mapping.put("shared", List.of(Map.of("href", "uuid='" + library + "', name='" + LIBRARY + "'", "template",
                "eventMeta", "at", "Meta")));
        final int formsBefore = client.forms().size();
        final Map<String, Object> integration = group(build, feed, INTEGRATION, "PegaIntegration", mapping,
                promoted.stream());

        assertThat(formsSince(formsBefore)).filteredOn(form -> form.message().startsWith(MODULE_FORM))
                .singleElement().satisfies(form -> assertThat(form.message()).contains("'" + feed + " common' was "
                                                                                       + "changed in Stroom since "
                                                                                       + "the tools saved it",
                        "EventSource/Generator is now \"Pega by hand\"", "shared call(s)"));
        final String version = uuidOf(map(integration, "common"));
        assertThat(version).isNotEqualTo(promoted.common());
        assertThat(String.valueOf(map(integration, "common").get("name"))).isEqualTo(feed + " common V1.1");
        assertThat(readXslt(version).getData()).contains("<Generator>Pega by hand</Generator>", library);
        assertThat(String.valueOf(KeptMapping.readMapping(readXslt(version).getDescription()).orElseThrow()
                .payload().get("mapping"))).contains("Pega by hand");
        // Production's module left as the user edited it.
        assertThat(readXslt(promoted.common()).getData()).isEqualTo(edited);

        // The new group's Events and documentation carry the edited value.
        final String template = templateUuid("translation", "Event Data (JSON)");
        final String pipeline = uuidOf(agreed("create_pipeline", args("build", build, "group", INTEGRATION,
                "template_uuid", template)));
        final int soapAt = TestE2eSourceTypeGroups.indexOf(promoted.records(), "\"PegaIntegration\"",
                "Envelope>\"}");
        assertThat(translationOutput(pipeline, promoted.stream(), soapAt)).contains("Pega by hand");
        final Map<String, Object> documented = agreed("write_documentation", args("build", build, "pipeline_uuid",
                pipeline, "stream_ids", List.of(promoted.stream()), "markdown", "# " + feed + " " + INTEGRATION
                                                                                 + "\n\n## Purpose and data\n\n"
                                                                                 + "Integration calls.\n\n## Field "
                                                                                 + "mapping\n\nGenerated.\n",
                "change", "Created"));
        assertThat(documentation(uuidOf(documented))).contains("Pega by hand");
    }

    @Test
    void aGroupsOverrideEditedByHandIsAskedAboutAndCarriedIntoItsMappingKeepingItsModule() {
        final String feed = "E2E-PEGA-MO";
        final String folder = "E2E Feeds/PegaMo";
        final Promoted promoted = securityPromoted(feed, folder, "pega-mo");

        // A later build changes the group to write its own Generator, in its own XSLT in place of the module's part:
        // a new version of the group's XSLT, run by its pipeline's new version.
        final String build = "pega-mo-2";
        json("start_build", args("build", build, "feeds", List.of(feed)));
        client.answeringWith(answering(List.of()));
        json("survey_feed", args("feed", feed, "build", build));
        recordSourceTypes(build, feed, SECURITY);
        final Map<String, Object> mapping = withGenerator(TestE2eSourceTypeGroups.securityMapping(),
                "Pega integration bus");
        final Map<String, Object> security = group(build, feed, SECURITY, "PegaSecurityEvent", mapping,
                promoted.stream());
        final String xslt = uuidOf(map(security, "xslt"));
        final String pipeline = uuidOf(agreed("copy_pipeline", args("build", build, "source_uuid",
                promoted.pipeline(), "new_name", feed + " " + SECURITY + " v2")));
        final String commonCode = readXslt(promoted.common()).getData();

        // The user edits the group's own Generator by hand.
        final String code = readXslt(xslt).getData();
        assertThat(code).contains("<Generator>Pega integration bus</Generator>");
        editXslt(xslt, doc -> doc.copy().data(code.replace("<Generator>Pega integration bus</Generator>",
                "<Generator>Pega bus (by hand)</Generator>")).build());
        assertThat(stepSample(pipeline, List.of(promoted.stream())).get("verdict")).isEqualTo("clean");

        // A generated change to the same field: asked, the user keeps their edit, so it is refused, nothing written.
        client.answeringWith(choosing("Decide field by field", "Keep my hand edit"));
        final String refused = refusal("build_translation_xslt", args("uuid", xslt, "stream_ids",
                List.of(promoted.stream()), "changes", Map.of("common", List.of(Map.of("path",
                        "EventSource/Generator", "value", "Pega integration bus v2"))), "change", "Generator v2"));
        client.answeringWith(McpTestClient::agree);
        assertThat(refused).contains("edited by hand", "rebuild_mapping uuid='" + xslt);
        assertThat(readXslt(xslt).getData()).contains("Pega bus (by hand)").doesNotContain("v2");
        assertThat(readXslt(promoted.common()).getData()).isEqualTo(commonCode);

        // Carried into the group's mapping, proven on the sample: its module block and its import kept.
        final Map<String, Object> rebuilt = agreed("rebuild_mapping", args("uuid", xslt, "stream_ids",
                List.of(promoted.stream()), "change", "Hand edit of the group's Generator carried into its mapping"));
        assertThat(rebuilt.get("saved")).as("rebuilt, proven on the sample: %s", rebuilt).isNotNull();
        final String after = readXslt(xslt).getData();
        assertThat(after).contains("<Generator>Pega bus (by hand)</Generator>", "uuid='" + promoted.common() + "'",
                "mcp:module kind=\"group\"");
        final Map<String, Object> kept = (Map<String, Object>) KeptMapping.readMapping(readXslt(xslt)
                .getDescription()).orElseThrow().payload().get("mapping");
        assertThat((Map<String, Object>) kept.get("module")).containsEntry("kind", "group")
                .containsEntry("name", SECURITY);
        assertThat(String.valueOf(((Map<String, Object>) kept.get("module")).get("common")))
                .contains(promoted.common());
        assertThat(stepSample(pipeline, List.of(promoted.stream())).get("verdict")).isEqualTo("clean");
    }

    // A feed's first build: the security group, its pipeline, promoted beside the feed.
    private Promoted securityPromoted(final String feed, final String folder, final String name) {
        createFeed(name + "-src", feed);
        final String text = TestE2eSourceTypeGroups.lines("pega/pega-1.json", 1500);
        final long stream = upload(feed, text);
        promote(name + "-src", Map.of("Feed", folder));
        final String template = templateUuid("translation", "Event Data (JSON)");
        final String build = name + "-1";
        json("start_build", args("build", build, "feeds", List.of(feed)));
        client.answeringWith(answering(List.of("PegaSecurityEvent")));
        json("survey_feed", args("feed", feed, "build", build));
        recordSourceTypes(build, feed, SECURITY);
        final Map<String, Object> security = group(build, feed, SECURITY, "PegaSecurityEvent",
                TestE2eSourceTypeGroups.securityMapping(), stream);
        final String common = uuidOf(map(security, "common"));
        final String pipeline = uuidOf(agreed("create_pipeline", args("build", build, "group", SECURITY,
                "template_uuid", template)));
        promote(build, Map.of("Pipeline", folder));
        return new Promoted(stream, text.lines().toList(), common, pipeline);
    }

    // build_translation_xslt group=: the group saved with its mapping.
    private Map<String, Object> group(final String build,
                                      final String feed,
                                      final String name,
                                      final String sourceType,
                                      final Map<String, Object> mapping,
                                      final long stream) {
        final Map<String, Object> saved = agreed("build_translation_xslt", args("build", build, "feeds",
                List.of(feed), "group", name, "source_types", List.of(sourceType), "schema_version", VERSION,
                "stream_ids", List.of(stream), "mapping", mapping));
        dump("build_translation_xslt " + name, saved);
        assertThat(saved).as("group %s saved: %s", name, saved.get("problems")).containsEntry("ok", true);
        return map(saved, "group");
    }

    private Map<String, Object> recordSourceTypes(final String build, final String feed, final String group) {
        final Map<String, Object> shown = json("record_source_types", args("build", build, "feed", feed));
        final Map<String, Object> written = json("record_source_types", args("build", build, "feed", feed,
                "reviewed", shown.get("review_id"), "group", group));
        assertThat(written).as("scoped: %s", written).containsEntry("group", group);
        return written;
    }

    private void promote(final String build, final Map<String, Object> destinations) {
        final Map<String, Object> review = json("promote_build", args("build", build, "destinations",
                destinations));
        assertThat(review).as("the layout first: %s", review).containsEntry("status", "needs_review");
        final Map<String, Object> promoted = json("promote_build", args("build", build, "destinations",
                destinations, "reviewed", review.get("review_id")));
        dump("promote_build " + build, promoted);
        assertThat(promoted).as("promoted: %s", promoted).containsKey("promoted");
    }

    private static Map<String, Object> withGenerator(final Map<String, Object> mapping, final String generator) {
        final Map<String, Object> out = new LinkedHashMap<>(mapping);
        out.put("common", ((List<Map<String, Object>>) mapping.get("common")).stream()
                .map(entry -> "EventSource/Generator".equals(entry.get("path"))
                        ? Map.<String, Object>of("path", "EventSource/Generator", "value", generator)
                        : entry)
                .toList());
        return out;
    }

    private static Map<String, Object> withoutGenerator(final Map<String, Object> mapping) {
        final Map<String, Object> out = new LinkedHashMap<>(mapping);
        out.put("common", ((List<Map<String, Object>>) mapping.get("common")).stream()
                .filter(entry -> !"EventSource/Generator".equals(entry.get("path")))
                .toList());
        return out;
    }

    // A user who chooses these source types for the build's scope and agrees to everything else (the first option:
    // a new group's Events into the feed's, a module's hand edit kept).
    private static Function<ElicitFormRequest, ElicitResult> answering(final List<String> scope) {
        final Function<ElicitFormRequest, ElicitResult> scoping = TestE2eSourceTypeGroups.scopeOf(scope, null);
        return form -> form.message().contains(TestE2eSourceTypeGroups.SCOPE_FORM)
                ? scoping.apply(form)
                : McpTestClient.agree(form);
    }


    // --------------------------------------------------------------------------------


    // A feed's first build promoted: its raw stream, the stream's records, the common module's and the security
    // group's pipeline's UUIDs.
    private record Promoted(long stream, List<String> records, String common, String pipeline) {

    }
}
