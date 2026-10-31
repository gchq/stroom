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

import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.docref.DocRef;
import stroom.pipeline.shared.XsltDoc;
import stroom.search.elastic.shared.ElasticIndexDoc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// What read_document gives of an index doc and an XSLT from the analysis helpers.
class TestDocumentAnalysesImpl {

    private static final DocRef INDEX = new DocRef(ElasticIndexDoc.TYPE, "i", "Acme index");
    private static final DocRef XSLT = new DocRef(XsltDoc.TYPE, "x", "Acme");

    private final IndexSurveys indexSurveys = Mockito.mock(IndexSurveys.class);
    private final XsltSaves xsltSaves = Mockito.mock(XsltSaves.class);
    private final ToolCallContext context = Mockito.mock(ToolCallContext.class);
    private final DocumentAnalysesImpl analyses = new DocumentAnalysesImpl(() -> indexSurveys, () -> xsltSaves);

    @Test
    void theSurveyLeavesOutTheIndexsTotalsAndItsDocumentsAndSaysWhetherEachFeederKeepsAPlan() {
        final Map<String, Object> withPlan = new LinkedHashMap<>();
        withPlan.put("uuid", "p1");
        withPlan.put("plan", Map.of("index", "acme"));
        final Map<String, Object> withoutPlan = new HashMap<>();
        withoutPlan.put("uuid", "p2");
        withoutPlan.put("plan", null);
        final Map<String, Object> survey = new LinkedHashMap<>();
        survey.put("fields", List.of());
        survey.put("fed_by", List.of(withPlan, withoutPlan));
        survey.put("documents", List.of(Map.of("StreamId", List.of("1"))));
        // Without the totals: counting them read every document through Stroom (seen: three minutes before the
        // agent could go on).
        Mockito.when(indexSurveys.surveyIndex(context, INDEX, 100, false)).thenReturn(survey);

        final Map<String, Object> surveyed = analyses.surveyIndex(context, INDEX);

        assertThat(surveyed).doesNotContainKey("documents").containsKey("fields");
        assertThat(surveyed.get("fed_by")).isEqualTo(List.of(Map.of("uuid", "p1", "plan", true),
                Map.of("uuid", "p2", "plan", false)));
    }

    @Test
    void anIndexThatCantBeSurveyedSaysWhy() {
        Mockito.when(indexSurveys.surveyIndex(context, INDEX, 100, false))
                .thenThrow(new ToolRefusal("ElasticIndex i can't be read"));

        assertThatThrownBy(() -> analyses.surveyIndex(context, INDEX)).hasMessage("ElasticIndex i can't be read");
    }

    @Test
    void aLostMappingIsSaidAsTheSavesSayIt() {
        Mockito.when(xsltSaves.lostMapping(XSLT, "")).thenReturn(Optional.of("lost"));

        assertThat(analyses.lostMapping(XSLT, "")).contains("lost");
        assertThat(analyses.lostMapping(XSLT, "kept")).isEmpty();
    }
}
