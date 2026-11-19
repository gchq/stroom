/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.analytics.shared;

import stroom.test.common.TestUtil;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class TestAnalyticRuleDoc {

    @Test
    void testSerialisation_levelAndStatus() {
        final AnalyticRuleDoc doc = AnalyticRuleDoc.builder()
                .uuid("test-uuid")
                .level(AnalyticRuleLevel.HIGH)
                .status(AnalyticRuleStatus.STABLE)
                .build();

        final AnalyticRuleDoc result = TestUtil.testSerialisation(doc, AnalyticRuleDoc.class);

        assertThat(result.getLevel())
                .isEqualTo(AnalyticRuleLevel.HIGH);
        assertThat(result.getStatus())
                .isEqualTo(AnalyticRuleStatus.STABLE);
    }

    @Test
    void testSerialisation_noLevelOrStatus() {
        // Neither is mandatory, which is every rule that exists today, so both must survive a round trip
        // as null rather than picking up a default.
        final AnalyticRuleDoc doc = AnalyticRuleDoc.builder()
                .uuid("test-uuid")
                .build();

        final AnalyticRuleDoc result = TestUtil.testSerialisation(doc, AnalyticRuleDoc.class);

        assertThat(result.getLevel())
                .isNull();
        assertThat(result.getStatus())
                .isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ENABLED", "", "Not a status"})
    void testDeserialisation_unknownStatus(final String status) {
        // Regression test (gwt-bugs #35): a rule whose stored status isn't one of the statuses (it was
        // once free text) failed to deserialise, so the rule couldn't be opened. It now reads as no
        // status, and the rest of the rule is kept.
        final String json = "{\"uuid\": \"test-uuid\", \"name\": \"My rule\", \"status\": \""
                            + status + "\"}";

        final AnalyticRuleDoc result = JsonUtil.readValue(json, AnalyticRuleDoc.class);

        assertThat(result.getStatus())
                .isNull();
        assertThat(result.getName())
                .isEqualTo("My rule");
    }

    @Test
    void testDeserialisation_statusInAnyCase() {
        final AnalyticRuleDoc result = JsonUtil.readValue(
                "{\"uuid\": \"test-uuid\", \"status\": \"stable\"}",
                AnalyticRuleDoc.class);

        assertThat(result.getStatus())
                .isEqualTo(AnalyticRuleStatus.STABLE);
        // It is still written by its name
        assertThat(JsonUtil.writeValueAsString(result, false))
                .contains("\"status\":\"STABLE\"");
    }

    @Test
    void testLevelAndStatusAffectEquality() {
        // The UI detects an edit by comparing the document it read with the one it would write, so a change
        // of level or status has to make the documents unequal or the edit would be silently dropped.
        final AnalyticRuleDoc doc = AnalyticRuleDoc.builder()
                .uuid("test-uuid")
                .build();

        assertThat(doc.copy().level(AnalyticRuleLevel.LOW).build())
                .isNotEqualTo(doc);
        assertThat(doc.copy().status(AnalyticRuleStatus.TESTING).build())
                .isNotEqualTo(doc);
    }

    @Test
    void testSerialisation_report() {
        final ReportDoc doc = ReportDoc.builder()
                .uuid("test-uuid")
                .build();

        TestUtil.testSerialisation(doc, ReportDoc.class, (mapper, json) -> {
            assertThat(json)
                    .doesNotContain("level")
                    .doesNotContain("status");

            // A report has no level or status. Content carrying them, e.g. hand written or produced
            // elsewhere, must still import rather than failing on the unknown properties.
            final String withStrayFields = json.replaceFirst("\\{",
                    "{\"level\":\"HIGH\",\"status\":\"STABLE\",");
            assertThatNoException()
                    .isThrownBy(() -> mapper.readValue(withStrayFields, ReportDoc.class));
        });
    }
}
