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

package stroom.search.elastic.search;

import stroom.query.api.datasource.QueryOptionDefinition;
import stroom.query.api.datasource.QueryOptionSet;
import stroom.query.api.datasource.QueryOptionUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestElasticQueryOptionSets {

    @ParameterizedTest
    @CsvSource(value = {
            "keyword, KEYWORD",
            "wildcard, KEYWORD",
            "text, TEXT",
            "match_only_text, TEXT",
            "constant_keyword, NONE",
            "long, NONE",
            "version, NONE",
            "NULL, NONE"},
            nullValues = "NULL")
    void testGet(final String nativeType, final String expectedSet) {
        final QueryOptionSet expected = switch (expectedSet) {
            case "KEYWORD" -> ElasticQueryOptionSets.KEYWORD;
            case "TEXT" -> ElasticQueryOptionSets.TEXT;
            default -> null;
        };
        assertThat(ElasticQueryOptionSets.get(nativeType))
                .isEqualTo(expected);
    }

    @Test
    void testKeywordOptions() {
        assertThat(ElasticQueryOptionSets.KEYWORD.getDefinitions())
                .extracting(QueryOptionDefinition::getId)
                .containsExactly(
                        ElasticQueryOptionSets.CASE_INSENSITIVE,
                        ElasticQueryOptionSets.FUZZINESS,
                        ElasticQueryOptionSets.FUZZY_PREFIX_LENGTH,
                        ElasticQueryOptionSets.FUZZY_TRANSPOSITIONS);
    }

    @Test
    void testTextOptions() {
        assertThat(ElasticQueryOptionSets.TEXT.getDefinitions())
                .extracting(QueryOptionDefinition::getId)
                .containsExactly(
                        ElasticQueryOptionSets.MATCH_MODE,
                        ElasticQueryOptionSets.PHRASE_SLOP,
                        ElasticQueryOptionSets.DEFAULT_OPERATOR,
                        ElasticQueryOptionSets.FUZZINESS,
                        ElasticQueryOptionSets.FUZZY_PREFIX_LENGTH,
                        ElasticQueryOptionSets.FUZZY_MAX_EXPANSIONS);
    }

    @Test
    void testKeywordCaseInsensitiveAndFuzzinessExcludeEachOther() {
        assertThat(QueryOptionUtil.getDisabledOptionIds(
                ElasticQueryOptionSets.KEYWORD,
                Map.of(ElasticQueryOptionSets.CASE_INSENSITIVE, "true")))
                .contains(ElasticQueryOptionSets.FUZZINESS);
        assertThat(QueryOptionUtil.getDisabledOptionIds(
                ElasticQueryOptionSets.KEYWORD,
                Map.of(ElasticQueryOptionSets.FUZZINESS, ElasticQueryOptionSets.FUZZINESS_AUTO)))
                .containsExactly(ElasticQueryOptionSets.CASE_INSENSITIVE);
    }

    @Test
    void testTextPhraseModeDisablesOperatorAndFuzziness() {
        assertThat(QueryOptionUtil.getDisabledOptionIds(
                ElasticQueryOptionSets.TEXT,
                Map.of(ElasticQueryOptionSets.MATCH_MODE, ElasticQueryOptionSets.MATCH_MODE_PHRASE)))
                .contains(ElasticQueryOptionSets.DEFAULT_OPERATOR, ElasticQueryOptionSets.FUZZINESS)
                .doesNotContain(ElasticQueryOptionSets.PHRASE_SLOP);
    }
}
