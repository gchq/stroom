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

import stroom.ai.impl.tools.indexing.ExampleNames.Named;
import stroom.util.yaml.YamlUtil;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/// Network events indexed whichever action they record (one field for a source address, not one per Permit and
/// Deny), and a source naming the paths below it. Ported from the Stroom MCP server's `tests/test_network_index.py`,
/// case by case.
class TestFieldPlans {

    // A user's example unrelated to the source (it was a Twitter index's), with one field named IpAddress.
    private static final String EXAMPLE = """
            {"Id": {"type": "long"}, "Text": {"type": "text"}, "IpAddress": {"type": "ip"},
             "User.Id": {"type": "keyword"}, "TypeId": {"type": "keyword"}}""";
    private static final Map<String, Double> POPULATED = populated();

    private static Map<String, Double> populated() {
        final Map<String, Double> shares = new LinkedHashMap<>();
        shares.put("EventTime/TimeCreated", 100.0);
        shares.put("EventSource/Device/HostName", 100.0);
        shares.put("EventSource/User/Id", 10.0);
        shares.put("EventDetail/TypeId", 100.0);
        shares.put("EventDetail/Authenticate/Action", 10.0);
        shares.put("EventDetail/Network/Permit/Source/Device/IPAddress", 40.0);
        shares.put("EventDetail/Network/Permit/Source/Port", 40.0);
        shares.put("EventDetail/Network/Permit/Destination/Device/IPAddress", 40.0);
        shares.put("EventDetail/Network/Deny/Source/Device/IPAddress", 30.0);
        shares.put("EventDetail/Network/Deny/Source/Port", 30.0);
        shares.put("EventDetail/Network/Deny/Destination/Device/IPAddress", 30.0);
        shares.put("EventDetail/Network/Deny/Data", 30.0);
        return shares;
    }

    @Test
    void aWildcardSourceNamesEveryNetworkAction() {
        final String source = "EventDetail/Network/*/Source/Device/IPAddress";
        assertThat(FieldPlans.sourceMatches(source, "EventDetail/Network/Deny/Source/Device/IPAddress")).isTrue();
        assertThat(FieldPlans.sourceMatches(source, "EventDetail/Network/Deny/Destination/Device/IPAddress"))
                .isFalse();
        // Permit's 40% and Deny's 30%: an event has one action.
        assertThat(FieldPlans.populationOf(source, POPULATED)).isEqualTo(70.0);
        assertThat(FieldPlans.anyAction("EventDetail/Network/Deny/Data")).isEqualTo("EventDetail/Network/*/Data");
        assertThat(FieldPlans.anyAction("EventDetail/Authenticate/Action")).isEqualTo("EventDetail/Authenticate/"
                                                                                      + "Action");
    }

    @Test
    void aDescendantSourceNamesEveryPathBelowIt() {
        // Seen in Ask Stroom AI: EventDetail//Object/Name in the plan, and View/Object/Name, Delete/Object/Name and
        // the rest reported as populated paths not mapped; the agent went investigating fields it had already
        // mapped.
        final String source = "EventDetail//Object/Name";
        for (final String path : List.of("EventDetail/View/Object/Name", "EventDetail/Export/Source/Object/Name",
                "EventDetail/Object/Name")) {
            assertThat(FieldPlans.sourceMatches(source, path)).as(path).isTrue();
        }
        assertThat(FieldPlans.sourceMatches(source, "EventDetail/View/Object/Id")).isFalse();
        assertThat(FieldPlans.sourceMatches(source, "EventSource/Object/Name")).isFalse();
        assertThat(FieldPlans.sourceMatches("//Object/Name", "EventDetail/Update/After/Object/Name")).isTrue();
        assertThat(FieldPlans.populationOf(source, Map.of("EventDetail/View/Object/Name", 20.0,
                "EventDetail/Delete/Object/Name", 5.0))).isEqualTo(25.0);
        // A predicate is matched as it is, not as a pattern.
        assertThat(FieldPlans.sourceMatches("EventDetail//Data[@Name='a.b']/@Value",
                "EventDetail/X/Data[@Name='a.b']/@Value")).isTrue();
        assertThat(FieldPlans.sourceMatches("EventDetail//Data[@Name='a.b']/@Value",
                "EventDetail/X/Data[@Name='aXb']/@Value")).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void thePlanTakesNetworkFieldsOnceNestedAsTheExampleNests() {
        // Seen: the plan took IpAddress from the Deny destination only (the first path ending IPAddress), permitted
        // traffic's addresses went unindexed, and the agent added SourceIp.Permit, SourceIp.Deny and the like.
        final Map<String, Map<String, Object>> profiles = new LinkedHashMap<>();
        for (final String name : List.of("ecs", "stroom-flat")) {
            profiles.put(name, YamlUtil.getMapper().readValue(IndexingFixtures.bundled(name + ".yaml"), Map.class));
        }
        final List<PlannedField> fields = new ArrayList<>(List.of(PlannedField.of("StreamId", "id", "@StreamId")));
        final Map<String, Map<String, Object>> ecsMap = (Map<String, Map<String, Object>>) profiles.get("ecs")
                .get("field_map");
        ecsMap.forEach((path, spec) -> {
            final Double share = FieldPlans.populationOf(path, POPULATED);
            if (share != null && share != 0
                && fields.stream().noneMatch(f -> f.name().equals(spec.get("name")))) {
                fields.add(PlannedField.of((String) spec.get("name"), (String) spec.get("type"), path));
            }
        });
        final Map<String, Set<String>> known = new LinkedHashMap<>();
        for (final Map<String, Object> profile : profiles.values()) {
            ((Map<String, Map<String, Object>>) profile.get("field_map")).forEach((path, spec) ->
                    known.computeIfAbsent(path, key -> new LinkedHashSet<>()).add((String) spec.get("name")));
        }
        final Named named = ExampleNames.namesFromExample(fields.stream().map(PlannedField::toJson).toList(),
                (Map<String, Map<String, Object>>) (Map<?, ?>) IndexingFixtures.json(EXAMPLE), known,
                new ArrayList<>(new TreeSet<>(POPULATED.keySet())));
        final Map<String, Object> bySource = new LinkedHashMap<>();
        named.fields().forEach(field -> bySource.put((String) field.get("source"), field.get("name")));
        assertThat(bySource).containsEntry("EventDetail/Network/*/Source/Device/IPAddress", "Source.IPAddress")
                .containsEntry("EventDetail/Network/*/Destination/Device/IPAddress", "Destination.IPAddress")
                .containsEntry("EventDetail/Network/*/Source/Port", "Source.Port");
        // No field per action.
        assertThat(bySource.keySet()).noneMatch(s -> s.contains("/Permit/") || s.contains("/Deny/"));
        // Which address the example's name means can't be told.
        assertThat(bySource.values()).doesNotContain("IpAddress");
        final String xslt = FieldPlan.of("elasticsearch", "fw", "@timestamp",
                named.fields().stream().map(PlannedField::fromJson).toList()).xslt();
        // Read once, for whichever action: a variable, as the Events translation reads a field its template reads
        // often.
        assertThat(xslt).contains("select=\"EventDetail/Network/*/Source/Device/IPAddress\"");
    }
}
