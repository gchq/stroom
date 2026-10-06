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

package stroom.gwt.workbench.client.app.query;

import stroom.gwt.workbench.client.app.rest.JsonValues;
import stroom.gwt.workbench.client.app.rest.RecordedRequest;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestQueryFixtures {

    @Test
    void testResponse() {
        final String table = QueryFixtures.tableResult("table", "[{\"id\": \"a\", \"name\": \"A\"}]",
                "[{\"values\": [\"x\"], \"depth\": 0}]", 1);
        final Map<?, ?> response = (Map<?, ?>) JsonValues.parse(QueryFixtures.response(false, table));

        assertThat(response.get("complete")).isEqualTo(false);
        assertThat(response.get("node")).isEqualTo(QueryFixtures.NODE);
        assertThat(((Map<?, ?>) response.get("queryKey")).get("uuid")).isEqualTo(QueryFixtures.QUERY_KEY);
        final List<?> results = (List<?>) response.get("results");
        assertThat(results).hasSize(1);
        assertThat(JsonValues.contains(results.get(0), JsonValues.parse(
                "{\"type\": \"table\", \"componentId\": \"table\", \"totalResults\": 1, "
                + "\"resultRange\": {\"offset\": 0, \"length\": 1}}"))).isTrue();
    }

    @Test
    void testResponse_noResults() {
        final Map<?, ?> response = (Map<?, ?>) JsonValues.parse(QueryFixtures.response(true));

        assertThat(response.get("complete")).isEqualTo(true);
        assertThat((List<?>) response.get("results")).isEmpty();
    }

    @Test
    void testVisResult() {
        final Object result = JsonValues.parse(QueryFixtures.visResult("vis", "{\"json\": \"{}\"}", "[1]"));

        assertThat(JsonValues.contains(result, JsonValues.parse(
                "{\"type\": \"ql_vis\", \"componentId\": \"vis\", \"jsonData\": \"[1]\"}"))).isTrue();
    }

    @Test
    void testHelpItems() {
        final Map<?, ?> page = (Map<?, ?>) JsonValues.parse(QueryFixtures.helpItems(
                QueryFixtures.helpRow("TITLE", "functions", "Functions", true),
                QueryFixtures.helpRow("FIELD", "f", "f", false)));

        assertThat((List<?>) page.get("values")).hasSize(2);
        assertThat(JsonValues.contains(page.get("pageResponse"), JsonValues.parse(
                "{\"offset\": 0, \"length\": 2, \"total\": 2, \"exact\": true}"))).isTrue();
        assertThat(JsonValues.parse(QueryFixtures.helpItems())).isNotNull();
    }

    @Test
    void testUiConfigWith() {
        final Map<?, ?> uiConfig = (Map<?, ?>) JsonValues.parse(
                QueryFixtures.uiConfigWith("\"analyticUiDefaultConfig\": {\"defaultNode\": \"node9\"}"));

        assertThat(((Map<?, ?>) uiConfig.get("analyticUiDefaultConfig")).get("defaultNode")).isEqualTo("node9");
        // The defaults are kept
        assertThat(uiConfig.get("htmlTitle")).isEqualTo("Stroom");
    }

    @Test
    void testEditorRoutes() {
        final RestFixtures fixtures = QueryFixtures.editorRoutes(RestFixtures.builder()
                        .post("/query/v1/fetchDataSourceFromQueryString", RestReply.json("{}")))
                .build();

        assertThat(fixtures.isHandled(new RecordedRequest("GET", "/activity/v1/current", null, null))).isTrue();
        assertThat(fixtures.isHandled(new RecordedRequest("POST", "/result-store/v1/destroy/node1", null, "{}")))
                .isTrue();
        assertThat(fixtures.isHandled(new RecordedRequest("POST", "/query/v1/search/node1", null, "{}"))).isFalse();
    }
}
