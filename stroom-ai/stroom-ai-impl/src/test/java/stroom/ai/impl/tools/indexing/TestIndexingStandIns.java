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

import stroom.ai.impl.tools.generation.SharedTemplate;
import stroom.ai.impl.tools.indexing.IndexingStandIns.FieldPlan;
import stroom.ai.impl.tools.indexing.IndexingStandIns.JsonScalar;
import stroom.ai.impl.tools.indexing.IndexingStandIns.PlannedField;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// STAND-IN until the indexing core port is merged: these test the stand-ins only, as the documentation uses them.
/// The stand-ins for the indexing core's `source_matches`, `population_of`, `any_action`, `json_xml_documents`,
/// `flatten_document` and `FieldPlan.written_by`, as the Stroom MCP server's `utils/fieldplan.py` and
/// `utils/templatecheck.py` have them.
class TestIndexingStandIns {

    @Test
    void aSourceNamesPathsByItsWildcards() {
        assertThat(IndexingStandIns.sourceMatches("EventSource/User/Id", "EventSource/User/Id")).isTrue();
        assertThat(IndexingStandIns.sourceMatches("EventDetail/Network/*/Source/Port",
                "EventDetail/Network/Deny/Source/Port")).isTrue();
        assertThat(IndexingStandIns.sourceMatches("EventDetail/Network/*/Source/Port",
                "EventDetail/Network/Source/Port")).isFalse();
        // Seen: an agent's EventDetail//Object/Name taken as matching nothing, its paths reported as not mapped.
        assertThat(IndexingStandIns.sourceMatches("EventDetail//Object/Name", "EventDetail/View/Object/Name")).isTrue();
        assertThat(IndexingStandIns.sourceMatches("EventDetail//Object/Name",
                "EventDetail/Export/Source/Object/Name")).isTrue();
        assertThat(IndexingStandIns.sourceMatches("//Name", "a/b/Name")).isTrue();
        assertThat(IndexingStandIns.sourceMatches("EventDetail//Object/Name", "EventSource/Object/Name")).isFalse();
    }

    @Test
    void aWildcardsPopulationIsItsPathsSummed() {
        final Map<String, Double> population = new LinkedHashMap<>();
        population.put("EventDetail/Network/Deny/Source/Port", 60.0);
        population.put("EventDetail/Network/Permit/Source/Port", 50.0);
        population.put("EventSource/User/Id", 66.7);
        assertThat(IndexingStandIns.populationOf("EventDetail/Network/*/Source/Port", population)).isEqualTo(100.0);
        assertThat(IndexingStandIns.populationOf("EventSource/User/Id", population)).isEqualTo(66.7);
        assertThat(IndexingStandIns.populationOf("EventDetail/*/User/Id", population)).isNull();
        assertThat(IndexingStandIns.anyAction("EventDetail/Network/Deny/Source/Port"))
                .isEqualTo("EventDetail/Network/*/Source/Port");
    }

    @Test
    void jsonDocumentsAreReadAndFlattened() {
        final List<Map<String, Object>> documents = IndexingStandIns.jsonXmlDocuments(
                "<?xml version=\"1.0\"?><map xmlns=\"http://www.w3.org/2005/xpath-functions\"><number key=\"n\">1"
                + "</number><map key=\"user\"><string key=\"name\">a</string><null key=\"x\"/></map>"
                + "<array key=\"tags\"><string>t1</string><string>t2</string></array></map>");
        assertThat(documents).hasSize(1);
        final Map<String, List<Object>> flat = IndexingStandIns.flattenDocument(documents.getFirst());
        assertThat(flat.keySet()).containsExactly("n", "user", "user.name", "tags");
        assertThat(flat.get("n")).containsExactly(new JsonScalar("number", "1"));
        assertThat(flat.get("user")).containsExactly(new JsonScalar("object", null));
        assertThat(flat.get("tags")).containsExactly(new JsonScalar("string", "t1"), new JsonScalar("string", "t2"));
        assertThat(IndexingStandIns.jsonXmlDocuments("<broken")).isEmpty();
    }

    @Test
    void aFieldIsWrittenByTheSharedTemplateOfItsObject() {
        final FieldPlan plan = new FieldPlan("elasticsearch", "x", "@timestamp",
                List.of(PlannedField.of("stroom.feed", "keyword", "shared:stroomMeta")), List.of(), null,
                List.of(new SharedTemplate("Common-Elastic-V1", "stroomMeta", "stroom", Map.of())));
        assertThat(plan.writtenBy("stroom.feed").template()).isEqualTo("stroomMeta");
        assertThat(plan.writtenBy("stroomx")).isNull();
        assertThat(IndexingStandIns.ELASTIC.get("id")).isEqualTo("long");
    }
}
