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

import stroom.ai.impl.tools.sample.SourceTypeRows;
import stroom.ai.impl.tools.sample.SourceTypeScope;
import stroom.ai.impl.tools.sample.SurveyDoc;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// record_source_types' scope against what the survey read: a scope that adds source types some stream wasn't read
/// for starts the survey's coverage again.
class TestRecordSourceTypesTool {

    private static final List<SourceTypeRows.Row> ROWS = List.of(
            new SourceTypeRows.Row("db", SourceTypeRows.VALUE, "db"),
            new SourceTypeRows.Row("web", SourceTypeRows.VALUE, "web"));

    @Test
    void aScopeWithinWhatEveryStreamWasReadForLeavesTheSurveyCovered() {
        // Read with no scope, or before scopes were kept: for every source type.
        final Map<String, Object> survey = survey(Map.of("2", List.of(SourceTypeScope.ALL)));

        assertThat(RecordSourceTypesTool.restartsCoverage(survey, scope(List.of("db", "web")))).isFalse();
        assertThat(survey).containsEntry("saturated", true).containsKey(SurveyDoc.ACCEPTED);
    }

    @Test
    void aScopeThatAddsSourceTypesAStreamWasntReadForStartsCoverageAgain() {
        final Map<String, Object> survey = survey(Map.of("2", List.of("db")));

        assertThat(RecordSourceTypesTool.restartsCoverage(survey, scope(List.of("db")))).isFalse();
        assertThat(RecordSourceTypesTool.restartsCoverage(survey, scope(List.of("db", "web")))).isTrue();
        assertThat(survey).containsEntry("saturated", false).doesNotContainKey(SurveyDoc.ACCEPTED);
    }

    private static SourceTypeScope scope(final List<String> names) {
        return new SourceTypeScope("b", "source", ROWS, names);
    }

    private static Map<String, Object> survey(final Map<String, Object> signed) {
        final Map<String, Object> survey = new LinkedHashMap<>();
        survey.put("streams_read", List.of(1L, 2L));
        survey.put("saturated", true);
        survey.put(SurveyDoc.ACCEPTED, Map.of("at", "now"));
        survey.put(SurveyDoc.SCOPE_READ, signed);
        return survey;
    }
}
