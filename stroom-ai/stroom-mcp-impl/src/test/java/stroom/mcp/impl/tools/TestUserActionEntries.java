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

import stroom.ai.impl.tools.StroomLinks;
import stroom.docref.DocRef;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// Steps that are the user's, as buttons: enabling a filter, and a request to Elasticsearch.
class TestUserActionEntries {

    @Test
    void anEnableButtonNamesTheFilterAndItsPipeline() {
        final Map<String, Object> action = UserActionEntries.enableFilter(StroomLinks.absolute(
                        "https://stroom.example"), 9, new DocRef("Pipeline", "p-1", "Acme-Events"),
                "processes feed ACME (Raw Events)", List.of("drop-index"));

        final Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("type", "Pipeline");
        doc.put("uuid", "p-1");
        doc.put("name", "Acme-Events");
        assertThat(action.get("id")).isEqualTo("enable-9");
        assertThat(action.get("action")).isEqualTo("enable_processor_filter");
        assertThat(action.get("filter_id")).isEqualTo(9);
        assertThat(action.get("doc")).isEqualTo(doc);
        assertThat(String.valueOf(action.get("link"))).endsWith("action=open-doc&docType=Pipeline&docUuid=p-1");
        assertThat(action.get("summary")).isEqualTo("Enable filter 9 on pipeline 'Acme-Events': processes feed ACME "
                                                    + "(Raw Events)");
        assertThat(action.get("after")).isEqualTo(List.of("drop-index"));
    }

    @ParameterizedTest
    @MethodSource("requests")
    void onlyATemplateCommitOrOneIndexDroppedIsOffered(final String request, final boolean offered) {
        final Map<String, Object> action = UserActionEntries.esRequest("x", request, "why");

        assertThat(action != null).isEqualTo(offered);
        if (offered) {
            assertThat(action.get("by")).isEqualTo("cluster admin");
            assertThat(action.get("request")).isEqualTo(request);
        }
    }

    @Test
    void leftOutEntriesAreDropped() {
        final Map<String, Object> result = UserActionEntries.withEntries(new LinkedHashMap<>(),
                Arrays.asList(null, null));
        assertThat(result).isEmpty();
        final Map<String, Object> kept = UserActionEntries.withEntries(new LinkedHashMap<>(), Arrays.asList(
                UserActionEntries.esRequest("x", "DELETE _all", "why"),
                UserActionEntries.enableFilter(StroomLinks.CHAT, 1, new DocRef("Pipeline", "p-1", "P"), "")));
        assertThat(TestCreateProcessorFilterTool.actions(kept).getFirst().get("id")).isEqualTo("enable-1");
    }

    static Stream<Arguments> requests() {
        return Stream.of(
                Arguments.of("PUT _index_template/ecs-acme-v1\n{\"index_patterns\": [\"ecs-acme-v1*\"]}", true),
                Arguments.of("DELETE _data_stream/ecs-acme-v1", true),
                Arguments.of("DELETE ecs-acme-v1", true),
                // Never a button: a wildcard, every index, several, or any other request.
                Arguments.of("DELETE ecs-*", false),
                Arguments.of("DELETE _all", false),
                Arguments.of("DELETE ecs-acme-v1,ecs-other-v1", false),
                Arguments.of("POST ecs-acme-v1/_delete_by_query {\"query\": {\"match_all\": {}}}", false),
                Arguments.of("PUT _cluster/settings\n{}", false));
    }
}
