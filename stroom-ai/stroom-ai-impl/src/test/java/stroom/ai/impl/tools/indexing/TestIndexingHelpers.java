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

package stroom.ai.impl.tools.indexing;

import stroom.ai.impl.tools.AgentKnowledge;
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.indexing.IndexingHelpers.Destination;
import stroom.ai.impl.tools.indexing.IndexingHelpers.TemplateTarget;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.tools.validation.HeldSchemasWorld;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// The pure parts of the Stroom MCP server's indexing tools ([IndexingHelpers]): ported from its
/// `tests/test_indexing.py` (a plan marked from the schema), `tests/test_index_names.py` (the plan the tools take
/// from the session), `tests/test_replaced_index.py` and `tests/test_fielddoc.py` (the plan kept with an XSLT, the
/// fields an index lacks), with cases for the helpers their tools' tests reach.
class TestIndexingHelpers {

    private static final EventSchema SCHEMA = EventSchema.parse(HeldSchemasWorld.XSD_4_1_0);
    // Written by hand, as the FortiOS index was: nested maps, the field names in their keys.
    private static final String OLD = """
            <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns="http://www.w3.org/2005/xpath-functions"
                xpath-default-namespace="event-logging:3" version="3.0">
              <xsl:template match="Event">
                <map>
                  <number key="StreamId"><xsl:value-of select="@StreamId"/></number>
                  <xsl:variable name="dst" select="EventDetail/Network/*/Destination"/>
                  <map key="Destination">
                    <string key="IPAddress"><xsl:value-of select="$dst/Device/IPAddress"/></string>
                    <map key="Location"><string key="Country"><xsl:value-of select="$dst/Device/Location/Country"/>\
            </string></map>
                  </map>
                  <string key="Rule"><xsl:value-of select="EventDetail/Network/*/Rule"/></string>
                </map>
              </xsl:template>
            </xsl:stylesheet>""";

    @Test
    void aPlanIsMarkedFromTheSchema() {
        assertThat(SCHEMA.canRepeat("EventDetail/Authenticate/Data[@Name='port']/@Value")).isTrue();
        assertThat(SCHEMA.canRepeat("EventSource/User/Id")).isFalse();
        assertThat(SCHEMA.canRepeat("@StreamId")).isFalse();
        final FieldPlan plan = FieldPlan.of("elasticsearch", "x", "@timestamp", List.of(
                PlannedField.of("StreamId", "id", "@StreamId"),
                PlannedField.of("user.name", "keyword", "EventSource/User/Id"),
                PlannedField.of("acme.port", "long", "EventDetail/Authenticate/Data[@Name='port']/@Value")));
        final FieldPlan marked = IndexingHelpers.withCardinality(plan, SCHEMA);
        assertThat(marked.fields()).extracting(PlannedField::multiple).containsExactly(false, false, true);
        // Unread: as before.
        assertThat(IndexingHelpers.withCardinality(plan, null)).isEqualTo(plan);
        // A discovery plan's fields are its own.
        final FieldPlan discovery = FieldPlan.forDiscovery("d", Discovery.of("json", "ts"));
        assertThat(IndexingHelpers.withCardinality(discovery, SCHEMA)).isSameAs(discovery);
    }

    @Test
    void thePlanGivenComesFirstThenTheOneKeptWithThePipelineThenTheSessions() {
        // Seen in Ask Stroom AI: past the client's step cap the agent had lost its earlier extra_fields, and the
        // 50-field plan went inline to save_xslt, create_index_doc and propose_index_template, five times in all.
        final FieldPlan given = plan("given-v1");
        final FieldPlan kept = plan("kept-v1");
        final FieldPlan drafted = plan("ecs-firewall-v1");
        final Map<String, FieldPlan> session = Map.of("ecs-firewall-v1", drafted);
        assertThat(IndexingHelpers.planGiven(given, kept, session, List.of())).isSameAs(given);
        assertThat(IndexingHelpers.planGiven(null, kept, session, List.of())).isSameAs(kept);
        assertThat(IndexingHelpers.planGiven(null, null, session, List.of("ecs-firewall-v1"))).isSameAs(drafted);
        assertThat(IndexingHelpers.planGiven(null, null, session, List.of("ecs-elsewhere-v1"))).isNull();
        // The only one drafted, with no name to go by.
        assertThat(IndexingHelpers.planGiven(null, null, session, java.util.Arrays.asList(null, ""))).isSameAs(drafted);
        final Map<String, FieldPlan> two = Map.of("a", plan("a"), "b", plan("b"));
        assertThat(IndexingHelpers.planGiven(null, null, two, List.of())).isNull();
    }

    @Test
    void thePlanKeptWithAnIndexingXsltIsReadBack() {
        // The plan kept with a pipeline's indexing XSLT is what the pipeline writes.
        final FieldPlan plan = plan("ecs-firewall-v1").withDropWhen(List.of("x"));
        final String description = KeptMapping.withMapping("Indexes the firewall.", "index", plan.toJson());
        assertThat(description).startsWith("Indexes the firewall.");
        assertThat(IndexingHelpers.keptPlan(description)).contains(plan);
        assertThat(IndexingHelpers.keptPlan("plain text")).isEmpty();
        assertThat(IndexingHelpers.keptPlan(KeptMapping.withMapping("", "translation", Map.of("a", 1)))).isEmpty();
    }

    @Test
    void aDraftSaysWhatTheIndexItReplacesWroteThatItDoesnt() {
        // Asked for by the user, after an agent fell into a loop reading the old indexing XSLT from a file its
        // client spilled describe_document's output to.
        final FieldPlan plan = FieldPlan.of("elasticsearch", "ecs-fortigate", "@timestamp", List.of(
                PlannedField.of("StreamId", "id", "@StreamId"),
                PlannedField.of("destination.ip", "ip", "EventDetail/Network/*/Destination/Device/IPAddress")))
                .withConvention("ecs", List.of());
        final Map<String, Double> populated = Map.of("EventDetail/Network/Deny/Destination/Device/Location/Country",
                40.0);
        final Map<String, Object> replaced = IndexingHelpers.replaced("FORTIGATE-Index-V1.0", "", OLD, plan,
                populated);
        assertThat(replaced.get("xslt")).isEqualTo("FORTIGATE-Index-V1.0");
        assertThat(replaced.get("fields")).isEqualTo(4L);
        assertThat(replaced.get("not_in_draft")).isEqualTo(List.of(
                Map.of("field", "Destination.Location.Country",
                        "source", "EventDetail/Network/*/Destination/Device/Location/Country", "populated", "40%"),
                Map.of("field", "Rule", "source", "EventDetail/Network/*/Rule", "populated", "not in the sample")));
        assertThat((String) replaced.get("hint")).contains("extra_fields");
        // Kept with a plan, the old XSLT's fields are the plan's.
        final FieldPlan kept = FieldPlan.of("elasticsearch", "old", "@timestamp", List.of(
                PlannedField.of("Rule", "keyword", "EventDetail/Network/*/Rule")));
        final Map<String, Object> fromPlan = IndexingHelpers.replaced("Old", KeptMapping.withMapping("", "index",
                kept.toJson()), "<x/>", plan, populated);
        assertThat(fromPlan.get("not_in_draft")).isEqualTo(List.of(Map.of("field", "Rule", "source",
                "EventDetail/Network/*/Rule", "populated", "not in the sample")));
        // Nothing to read: said so.
        assertThat(IndexingHelpers.replaced("Other", "", "not xml", plan, populated).get("note"))
                .isEqualTo("No fields read from it: not an indexing XSLT this server can read.");
    }

    @Test
    void fieldsAPipelineWritesThatTheIndexLacksAreNamed() {
        // Asked for by the user: an error to put to them (reindex, or check again), not a doc that leaves the
        // field out.
        final Map<String, List<String>> expected = Map.of("Web - Indexing", List.of("user.name", "user.role"));
        assertThat(IndexingHelpers.fieldsNotListed(expected, Set.of("user.name", "user.role"))).isEmpty();
        final Map<String, List<String>> missing = IndexingHelpers.fieldsNotListed(expected, Set.of("user.name"));
        assertThat(missing).isEqualTo(Map.of("Web - Indexing", List.of("user.role")));
        final String said = IndexingHelpers.missingFieldsSaid(missing, "web-v1");
        assertThat(said).contains("has no field user.role (written by 'Web - Indexing')").contains("reindex")
                .contains("check again");
        assertThat(IndexingHelpers.missingFieldsSaid(missing, null)).startsWith("The index has no field");
    }

    @Test
    void aPipelinesIndexNameWritesToTheIndexItsDocNames() {
        assertThat(IndexingHelpers.writesToIndex("foo-v1", "foo-v1")).isTrue();
        assertThat(IndexingHelpers.writesToIndex("foo-v10", "foo-v1")).isFalse();
        // Built from values, and a pattern or alias.
        assertThat(IndexingHelpers.writesToIndex("ecs-windows{_suffix}v1", "ecs-windows-dc-v1")).isTrue();
        assertThat(IndexingHelpers.writesToIndex("ecs-windows-v1", "ecs-windows*")).isTrue();
        assertThat(IndexingHelpers.writesToIndex("foo-v1", null)).isFalse();
    }

    @Test
    void aPipelineWritingToNoDocOfItsBuildIsSaidToMissTheIndexWaiting() {
        final Map<String, String> docs = new LinkedHashMap<>();
        docs.put("Acme Index", "ecs-acme-v1");
        final String said = IndexingHelpers.otherIndex("ecs-acmee-v1", docs, List.of(
                new Destination("Acme - Indexing", "p1", "ecs-acmee-v1", "elasticIndexingFilter")), "p1");
        assertThat(said).isEqualTo("The indexing pipeline writes to 'ecs-acmee-v1', and the build's Elastic Index doc "
                                   + "('Acme Index' names 'ecs-acme-v1'), which no other pipeline of the build writes "
                                   + "to, doesn't: an index template for 'ecs-acmee-v1' would be for an index Stroom "
                                   + "doesn't search.");
        // The right name; another pipeline writing to the doc; no doc at all: nothing to say.
        assertThat(IndexingHelpers.otherIndex("ecs-acme-v1", docs, List.of(), "p1")).isNull();
        assertThat(IndexingHelpers.otherIndex("ecs-acmee-v1", docs, List.of(
                new Destination("Other", "p2", "ecs-acme-v1", "e")), "p1")).isNull();
        assertThat(IndexingHelpers.otherIndex("ecs-acmee-v1", Map.of(), List.of(), "p1")).isNull();
    }

    @Test
    void anIndexNameBuiltFromValuesIsAPatternAndANameWithoutThem() {
        // Seen: a literal pattern 'ecs-windows{_suffix}v1*' that matched no index, the self-check passing it, the
        // commit button dropped.
        assertThat(IndexingHelpers.templateTarget("ecs-windows{_suffix}v1"))
                .isEqualTo(new TemplateTarget("ecs-windowsv1", "ecs-windows*v1"));
        assertThat(IndexingHelpers.templateTarget("ecs-{a}-{b}-v1"))
                .isEqualTo(new TemplateTarget("ecs-v1", "ecs-*-*-v1"));
        assertThat(IndexingHelpers.templateTarget("{all}")).isEqualTo(new TemplateTarget("index", "*"));
        assertThat(IndexingHelpers.templateTarget("plain-v1")).isEqualTo(new TemplateTarget("plain-v1", "plain-v1"));
    }

    @Test
    void componentTemplatesNotGivenAreSaidAndTheEcsBaseIsTheExamples() {
        assertThat(IndexingHelpers.componentNotes(List.of())).isEmpty();
        assertThat(IndexingHelpers.componentNotes(List.of("ecs-base")).getFirst())
                .startsWith("composed_of ['ecs-base'] not given, so not checked");
        // Seen in testing: a PascalCase example (User.Id), drafted with the ECS profile picking its paths, got
        // ecs@mappings and dynamic mapping for @timestamp: only an example that composes it leaves fields to it.
        assertThat(IndexingHelpers.ecsBase(Map.of("composed_of", List.of("ecs@mappings")))).isTrue();
        assertThat(IndexingHelpers.ecsBase(Map.of("composed_of", List.of("org-base")))).isFalse();
        assertThat(IndexingHelpers.ecsBase(Map.of())).isFalse();
    }

    @Test
    void populatedPathsNoFieldNamesAreListedOnceWhicheverAction() {
        final FieldPlan plan = FieldPlan.of("elasticsearch", "fw", "@timestamp", List.of(
                PlannedField.of("source.ip", "ip", "EventDetail/Network/*/Source/Device/IPAddress"),
                PlannedField.of("firewall.configuration", "keyword", "EventDetail//Configuration/Type")));
        final Map<String, Double> populated = new LinkedHashMap<>();
        populated.put("EventDetail/Network/Permit/Source/Device/IPAddress", 40.0);
        populated.put("EventDetail/Network/Permit/Source/Port", 40.0);
        populated.put("EventDetail/Network/Deny/Source/Port", 30.0);
        populated.put("EventDetail/Update/After/Configuration/Type", 10.0);
        populated.put("EventDetail/Alert/Type", 0.0);
        // A // source covers the paths below it: not reported as unmapped.
        assertThat(IndexingHelpers.populatedNotMapped(plan, populated))
                .containsExactly("EventDetail/Network/*/Source/Port");
    }

    @Test
    void theConventionProfilesAreReadByName() {
        final AgentKnowledge knowledge = Mockito.mock(AgentKnowledge.class);
        Mockito.when(knowledge.conventionNames()).thenReturn(List.of("stroom-flat", "ecs", "gone"));
        Mockito.when(knowledge.convention("ecs")).thenReturn(Optional.of(IndexingFixtures.bundled("ecs.yaml")));
        Mockito.when(knowledge.convention("stroom-flat")).thenReturn(Optional.of("description: flat\n"));
        Mockito.when(knowledge.convention("gone")).thenReturn(Optional.empty());
        final Map<String, Map<String, Object>> profiles = IndexingHelpers.conventions(knowledge);
        assertThat(profiles).containsOnlyKeys("ecs", "stroom-flat");
        assertThat(profiles.get("ecs").get("structure")).isEqualTo("nested");
        assertThat(profiles.get("stroom-flat").get("description")).isEqualTo("flat");
    }

    private static FieldPlan plan(final String indexName) {
        return FieldPlan.of("elasticsearch", indexName, "@timestamp", List.of(
                PlannedField.of("StreamId", "id", "@StreamId")));
    }
}
