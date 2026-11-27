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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.documents.KeptMapping;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/// check_index_template warns of another agreed template whose index_patterns also cover the index at the same
/// priority: Elasticsearch refuses or mis-applies overlapping templates, e.g. an old version's `acme-events*` over a
/// new `acme-events-v2`.
class TestTemplateOverlaps {

    private static final Map<String, Object> NEW_BODY = Map.of("index_patterns", List.of("acme-events-v2"));

    @Test
    void anOldVersionsWiderPatternCoveringTheNewIndexIsWarnedOf() {
        final ProcessingWorld w = new ProcessingWorld();
        w.listsEveryPipelineAndIndexDoc();
        w.pipeline(new DocRef(PipelineDoc.TYPE, "old", "Acme-Events-V1.0-Indexing"), false,
                KeptMapping.withAgreedTemplate("Indexes Acme.", agreed("acme-events", "acme-events-v1",
                        "\"index_patterns\": [\"acme-events*\"]")));
        // This pipeline's own agreement, of the same patterns, is left out.
        w.pipeline(ProcessingWorld.P1, true, KeptMapping.withAgreedTemplate("Indexes Acme.", agreed("other",
                "acme-events-v2", "\"index_patterns\": [\"acme-events*\"]")));

        final List<String> warnings = new TemplateOverlaps(new PipelineIndex(() -> w.feeds.docFinder,
                w.world.documentAccess), w.world.documentAccess).warnings("p1", "acme-events-v2", "ES_DEV",
                "acme-events-v2", NEW_BODY);

        assertThat(warnings).singleElement().asString()
                .contains("index template 'acme-events', agreed for pipeline 'Acme-Events-V1.0-Indexing' (index "
                          + "'acme-events-v1'), has index_patterns ['acme-events*']")
                .contains("also cover 'acme-events-v2' at the same priority")
                .contains("narrow its patterns")
                .contains("higher priority");
    }

    @Test
    void anExactOrNonOverlappingOrHigherPriorityTemplateIsNotWarnedOf() {
        assertThat(overlap(agreed("acme-events-v1", "acme-events-v1", "\"index_patterns\": [\"acme-events-v1\"]")))
                .isEmpty();
        assertThat(overlap(agreed("other", "other-v1", "\"index_patterns\": [\"other*\"]"))).isEmpty();
        assertThat(overlap(agreed("acme-events", "acme-events-v1", "\"index_patterns\": [\"acme-events*\"], "
                                                                   + "\"priority\": 200"))).isEmpty();
        final Map<String, Object> elsewhere = agreed("acme-events", "acme-events-v1", "\"index_patterns\": "
                                                                                      + "[\"acme-events*\"]");
        elsewhere.put("cluster", "ES_PROD");
        assertThat(overlap(elsewhere)).isEmpty();
        // The same priority, given or not.
        assertThat(overlap(agreed("acme-events", "acme-events-v1", "\"index_patterns\": [\"acme-*\"], "
                                                                   + "\"priority\": 0"))).isPresent();
    }

    @Test
    void anotherPipelinesAgreementOfTheSameNameIsNoLongerTakenForTheSameTemplate() {
        // Another pipeline writing to the same index: replaced by this one, merged from both plans.
        assertThat(overlap(agreed("acme-events-v2", "acme-events-v2", "\"index_patterns\": [\"acme-*\"]")))
                .hasValueSatisfying(warning -> assertThat(warning).contains("also agreed for pipeline 'Old', which "
                                                                            + "writes to the same index "
                                                                            + "'acme-events-v2'",
                        "merges the plans of every pipeline writing to the index"));
        // Another index's template of the same name: a clash.
        assertThat(overlap(agreed("acme-events-v2", "acme-mail-v1", "\"index_patterns\": [\"acme-mail-v1\"]")))
                .hasValueSatisfying(warning -> assertThat(warning).contains("already agreed for pipeline 'Old' "
                                                                            + "(index 'acme-mail-v1')",
                        "template_name="));
        // On another cluster: none.
        final Map<String, Object> elsewhere = agreed("acme-events-v2", "acme-events-v2", "\"index_patterns\": "
                                                                                          + "[\"acme-*\"]");
        elsewhere.put("cluster", "ES_PROD");
        assertThat(overlap(elsewhere)).isEmpty();
    }

    @Test
    void anIndexNameWithVariablesIsCoveredAsItsPattern() {
        assertThat(TemplateOverlaps.overlap("Old", agreed("acme", "acme-v1", "\"index_patterns\": [\"acme-*\"]"),
                "acme-{_year}", "ES_DEV", "acme-new", Map.of())).isPresent();
    }

    private static Optional<String> overlap(final Map<String, Object> agreed) {
        return TemplateOverlaps.overlap("Old", agreed, "acme-events-v2", "ES_DEV", "acme-events-v2", NEW_BODY);
    }

    private static Map<String, Object> agreed(final String name, final String index, final String body) {
        final Map<String, Object> agreed = new LinkedHashMap<>(ProcessingWorld.agreed("PUT _index_template/" + name
                                                                                      + "\n{" + body + "}"));
        agreed.put("name", name);
        agreed.put("index", index);
        return agreed;
    }
}
