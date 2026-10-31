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
import stroom.pipeline.shared.XsltDoc;

import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/// Invalid data, and how errors are resolved, accepted and documented, driving the real tools as the agent would.
/// Covered: parts 1 (invalid data the agent's own mapping mishandles, resolved before anything goes to the user), 2 (an
/// inherited error the agent can't resolve, accepted as benign by the user, then reported as such by stepping, the
/// Error streams and the documentation) and 4 (raw data that is not well-formed XML, blocking in the template's
/// parser).
///
/// Part 3 (invalid data for Elasticsearch: a document Elasticsearch rejects, small batches, the batch size
/// restored) is left out: it needs an Elasticsearch cluster, which this environment hasn't got.
@SuppressWarnings("unchecked")
class TestE2eErrors extends AbstractE2eTest {

    private static final String DECORATED = "E2E Decorated Text";
    private static final String DECORATE_XSLT = "E2E-Decorate-Users";
    // The environment's decoration step: events pass through as they are; a user with no HR record is logged as an
    // error.
    private static final String DECORATE = """
            <?xml version="1.1" encoding="UTF-8"?>
            <xsl:stylesheet xmlns="event-logging:3" xpath-default-namespace="event-logging:3" xmlns:stroom="stroom"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="3.0">
              <xsl:template match="@*|node()"><xsl:copy><xsl:apply-templates select="@*|node()" /></xsl:copy>\
            </xsl:template>
              <xsl:template match="Event">
                <xsl:if test="starts-with(EventSource/User/Id, 'svc-')">
                  <xsl:value-of select="stroom:log('ERROR', concat('No HR record for user ', EventSource/User/Id))" />
                </xsl:if>
                <xsl:copy><xsl:apply-templates select="@*|node()" /></xsl:copy>
              </xsl:template>
            </xsl:stylesheet>
            """;
    private static final String SAMPLE = """
            time,user,host,ip,result
            2026-09-28T10:00:00,alice,ws01,10.0.0.1,ok
            2026-09-28T10:05:00,svc-backup,ws02,10.0.0.2,ok
            2026-09-28T10:07:30,carol,ws03,10.0.0.3,locked
            """;
    private static final String MARKDOWN = "## Purpose and data\n\nCSV logons.\n\n## Field mapping\n\n"
                                           + "(see the mapping)\n";

    @Override
    boolean followsNext() {
        return true;
    }

    @Test
    void ownAndAccepted() {
        final String build = "e2e-errors";
        final String feed = "E2E-ERRORS";
        // ### 1. invalid data its own mapping mishandles: the agent resolves it first
        createFeed(build, feed);
        final long raw = upload(feed, SAMPLE);
        final String template = decoratedTemplate();
        saveTextConverter(build, feed, CSV_SPLITTER);
        final Map<String, Object> mapping = E2eFixtures.csvMapping();
        final Map<String, Object> success = E2eFixtures.entries(E2eFixtures.rule(mapping, "logon")).stream()
                .filter(field -> String.valueOf(field.get("path")).endsWith("Outcome/Success"))
                .findFirst()
                .orElseThrow();
        // The agent's mistake: the result column as it is, not a boolean.
        success.remove("map");
        final Map<String, Object> saved = agreed("build_translation_xslt", args("mapping", mapping, "build", build,
                "name", feed + "-Events", "schema_version", VERSION));
        assertThat(saved.get("ok")).as("the translation saved from its mapping: %s", saved.get("problems"))
                .isEqualTo(true);
        final String pipeline = (String) createPipeline(build, feed + "-Events", template).get("uuid");
        final Map<String, Object> first = stepSample(pipeline, List.of(raw));
        dump("first step", first.get("groups"));
        final List<Map<String, Object>> blocking = groups(first, "blocking");
        assertThat(first.get("verdict")).as("the logons' Outcome/Success ('ok', 'locked') fails the schema")
                .isEqualTo("blocking");
        assertThat(blocking).as("blocking, and caused by the mapping").isNotEmpty()
                .allSatisfy(group -> assertThat(group).containsEntry("element", "schemaFilter"));
        // The agent's fix, in its own mapping.
        success.put("map", Map.of("ok", "true", "locked", "false"));
        final Map<String, Object> resaved = agreed("build_translation_xslt", args("mapping", mapping, "build", build,
                "uuid", map(saved, "saved").get("uuid")));
        assertThat(resaved.get("ok")).as("the fixed mapping saved over the XSLT: %s", resaved.get("problems"))
                .isEqualTo(true);
        final Map<String, Object> second = stepSample(pipeline, List.of(raw));
        assertThat(groups(second, "blocking")).as("after the fix nothing blocks: %s",
                said(list(second, "groups"))).isEmpty();

        // ### 2. an error it cannot resolve: inherited; the user accepts it as benign
        final List<Map<String, Object>> review = groups(second, "review");
        assertThat(review).as("left for the user: the template's decoration step").hasSize(1);
        assertThat(review.getFirst()).containsEntry("element", "decorationFilter")
                .containsEntry("own_element", false);
        final String example = (String) list(review.getFirst(), "examples").getFirst().get("message");
        assertThat(example).as("it has no HR record for svc-backup").contains("No HR record for user svc-backup");
        final String reason = "Service accounts have no HR record; expected for svc- accounts";
        final int formsBefore = client.forms().size();
        agreed("write_documentation", args("build", build, "pipeline_uuid", pipeline, "markdown", MARKDOWN,
                "change", "Created", "stream_ids", List.of(raw), "accept_errors", List.of(Map.of(
                        "element", "decorationFilter", "example", example, "reason", reason,
                        "matches", "No HR record for user svc-*"))));
        final List<ElicitFormRequest> asked = client.forms().subList(formsBefore, client.forms().size());
        assertThat(asked).as("the user confirms what is recorded").extracting(ElicitFormRequest::message)
                .anyMatch(message -> message.contains("benign"))
                .anyMatch(message -> message.contains(reason));
        final Map<String, Object> third = stepSample(pipeline, List.of(raw));
        final List<Map<String, Object>> hr = list(third, "groups").stream()
                .filter(group -> "decorationFilter".equals(group.get("element")))
                .toList();
        assertThat(third.get("verdict")).as("stepped again: clean").isEqualTo("clean");
        assertThat(hr).as("reported as benign, with the user's reason").isNotEmpty();
        assertThat(hr.getFirst()).containsEntry("class", "benign").containsEntry("accepted", true);
        assertThat((String) hr.getFirst().get("reason")).contains(reason);

        // ### processed: the Error stream says the same, and the documentation lists it
        final Map<String, Object> done = process(pipeline, List.of(raw));
        final Map<String, Object> stream = list(done, "streams").getFirst();
        assertThat(strings(stream, "events")).as("Events: %s", done.get("streams")).isNotEmpty();
        assertThat(strings(stream, "errors")).as("and an Error stream: %s", done.get("streams")).isNotEmpty();
        final Map<String, Object> summary = json("summarise_streams", args("stream_ids", List.of(raw), "kind",
                "errors"));
        dump("summarise_streams errors", summary);
        final List<Map<String, Object>> errorGroups = groupsOf(summary);
        assertThat(errorGroups).as("its errors are the accepted kind, reported as benign").isNotEmpty()
                .allSatisfy(group -> assertThat(group).containsEntry("class", "benign")
                        .containsEntry("accepted", true));
        final Map<String, Object> written = agreed("write_documentation", args("build", build, "pipeline_uuid",
                pipeline, "markdown", MARKDOWN, "change", "Processed the sample", "stream_ids", List.of(raw)));
        final String text = documentation((String) written.get("uuid"));
        final String errors = text.contains("## Errors")
                ? text.split("## Errors", 2)[1].split("\n## ", 2)[0]
                : "";
        dump("Errors section", errors);
        assertThat(errors).as("the Errors section lists the error, as accepted, with the reason; kept for the next "
                              + "review")
                .contains("| benign | `decorationFilter` |", "No HR record for user svc-backup", reason,
                        "stroom-mcp accepted errors");
    }

    @Test
    void brokenXmlIsCaughtFirstAndBlocksWhenStepped() {
        final Map<String, Object> stepped = brokenXml();
        assertThat(stepped.get("verdict")).as("blocking").isEqualTo("blocking");
        // The groups' class and element: see brokenXmlBlocksInTheTemplatesParser.
        assertThat(list(stepped, "groups")).as("the data, not the agent's content, is at fault").isNotEmpty()
                .allSatisfy(group -> assertThat(group).containsEntry("own_element", false));
    }

    @Disabled("Stroom's stepping engine on this branch (SteppingResultMapper) reports the XMLParser's fatal "
              + "SAXParseException only as the step's general error, not in the parser element's indicators, so "
              + "SteppingWalks.markers gives it element 'pipeline' "
              + "and class review ('Error in an inherited element; check whether our output causes it'). The "
              + "script ran against gchq/stroom:v7.13-beta.17, whose stepping put it on the parser element.")
    @Test
    void brokenXmlBlocksInTheTemplatesParser() {
        final Map<String, Object> stepped = brokenXml();
        final List<Map<String, Object>> parser = groups(stepped, "blocking").stream()
                .filter(group -> String.valueOf(group.get("element")).toLowerCase().contains("parser"))
                .toList();
        assertThat(stepped.get("verdict")).as("blocking").isEqualTo("blocking");
        assertThat(parser).as("in the template's XML parser").isNotEmpty();
        assertThat(parser.getFirst()).as("the data, not the agent's content, is at fault")
                .containsEntry("own_element", false);
    }

    // ### 4. raw data that is not well-formed XML: caught before the pipeline is made, then stepped
    private Map<String, Object> brokenXml() {
        final String build = "e2e-errors-xml";
        final String feed = "E2E-ERRORS-XML";
        createFeed(build, feed);
        final long raw = upload(feed, "<logons><logon><when>2026-09-28 12:00:00</when><who>frank</who>"
                                      + "<host>ws06</host><result>SUCCESS</logon></logons>");
        final Map<String, Object> saved = agreed("build_translation_xslt", args("mapping", E2eFixtures.xmlMapping(),
                "build", build, "name", feed + "-Events", "schema_version", VERSION));
        final String template = templateUuid("translation", "Event Data (XML)");
        final Map<String, Object> xslt = docProperty("translationFilter", "xslt",
                (String) map(saved, "saved").get("uuid"), "XSLT");
        final String refused = refusal("create_pipeline", args("build", build, "name", feed + "-Events",
                "template_uuid", template, "set_properties", List.of(xslt)));
        assertThat(refused).as("caught first: the sample does not read as XML").contains("XMLParser cannot read");
        // The user insists the feed is XML: stepping then shows where it breaks.
        final String pipeline = (String) agreed("create_pipeline", args("build", build, "name", feed + "-Events",
                "template_uuid", template, "set_properties", List.of(xslt), "accept_parser_mismatch", true))
                .get("uuid");
        final Map<String, Object> stepped = stepSample(pipeline, List.of(raw));
        dump("broken XML", stepped);
        return stepped;
    }

    // Event Data (Text) with its decoration step set, as an environment's template has it: inherited by children.
    private String decoratedTemplate() {
        final String xslt = sharedXslt(DECORATE_XSLT, DECORATE);
        return childPipeline(templateFolder(), DECORATED, templateUuid("translation", "Event Data (Text)"),
                List.of(property("decorationFilter", "xslt", new DocRef(XsltDoc.TYPE, xslt, DECORATE_XSLT))));
    }

    private static List<Map<String, Object>> groups(final Map<String, Object> stepped, final String type) {
        return list(stepped, "groups").stream()
                .filter(group -> type.equals(group.get("class")))
                .toList();
    }

    // The groups of an errors summary, at its top or under each stream (`groups_of`; summarise_streams, which
    // summarise_errors is part of now, keys its streams under streams).
    private static List<Map<String, Object>> groupsOf(final Map<String, Object> summary) {
        if (summary.get("groups") != null) {
            return list(summary, "groups");
        }
        if (summary.get("streams") instanceof final Map<?, ?> streams) {
            return groupsOf((Map<String, Object>) streams);
        }
        return summary.values().stream()
                .filter(value -> value instanceof Map<?, ?>)
                .map(value -> (Map<String, Object>) value)
                .flatMap(value -> Optional.ofNullable(value.get("groups")).stream()
                        .flatMap(groups -> ((List<Map<String, Object>>) groups).stream()))
                .toList();
    }
}
