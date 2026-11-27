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

package stroom.ai.impl.tools.query;

import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestStroomQlToEsql {

    private static final Map<String, FieldType> FIELDS = Map.of(
            "user.name", FieldType.KEYWORD,
            "url.domain", FieldType.KEYWORD,
            "event.action", FieldType.KEYWORD,
            "bytes", FieldType.LONG,
            "duration", FieldType.DOUBLE,
            "message", FieldType.TEXT,
            "@timestamp", FieldType.DATE,
            "StreamId", FieldType.ID,
            "odd name", FieldType.KEYWORD);

    @Test
    void theTopUploadersTranslate() {
        assertThat(translate("""
                from "Proxy uploads"
                where event.action = "allowed" and bytes >= 100
                group by user.name, url.domain
                sort by total desc
                select user.name, url.domain, sum(bytes) as total
                limit 10"""))
                .isEqualTo("| WHERE event.action == \"allowed\" AND bytes >= 100 "
                           + "| STATS total = SUM(bytes) BY user.name, url.domain | SORT total DESC "
                           + "| KEEP user.name, url.domain, total | LIMIT 10");
    }

    @Test
    void theWhereIsGivenAlone() {
        assertThat(StroomQlToEsql.translate("from X where user.name = alice group by url.domain "
                                            + "select url.domain, count() as n", FIELDS))
                .hasValueSatisfying(translation -> {
                    assertThat(translation.where()).isEqualTo("| WHERE user.name == \"alice\"");
                    assertThat(translation.pipeline()).isEqualTo("| WHERE user.name == \"alice\" "
                                                                 + "| STATS n = COUNT(*) BY url.domain "
                                                                 + "| KEEP url.domain, n");
                });
        assertThat(StroomQlToEsql.translate("from X group by url.domain select url.domain, count() as n", FIELDS))
                .hasValueSatisfying(translation -> assertThat(translation.where()).isEmpty());
    }

    @Test
    void eachFunctionTranslates() {
        assertThat(translate("from X group by user.name select user.name, count(), sum(bytes), min(bytes), "
                             + "max(duration), average(duration) as avg, mean(bytes) as m"))
                .isEqualTo("| STATS count = COUNT(*), sum_bytes = SUM(bytes), min_bytes = MIN(bytes), "
                           + "max_duration = MAX(duration), avg = AVG(duration), m = AVG(bytes) BY user.name "
                           + "| KEEP user.name, count, sum_bytes, min_bytes, max_duration, avg, m");
    }

    @Test
    void aGroupedFieldRenamedAndSortedAscending() {
        assertThat(translate("from X group by user.name sort by user, n desc select user.name as user, count() as n"))
                .isEqualTo("| STATS n = COUNT(*) BY user.name | RENAME user.name AS user | SORT user ASC, n DESC "
                           + "| KEEP user, n");
    }

    @Test
    void namesThatNeedQuotingAreQuoted() {
        assertThat(translate("from X where \"odd name\" = \"x\" group by bytes select bytes, count() as n"))
                .isNull();
        assertThat(translate("from X group by bytes select bytes, count() as \"the count\""))
                .isEqualTo("| STATS `the count` = COUNT(*) BY bytes | KEEP bytes, `the count`");
    }

    @Test
    void quotesInAValueAreEscaped() {
        assertThat(translate("from X where user.name = 'say \"hi\"' group by url.domain select url.domain, "
                             + "count() as n"))
                .startsWith("| WHERE user.name == \"say \\\"hi\\\"\" |");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // Not an aggregation.
            "from X select user.name, bytes",
            "from X where bytes > 1 select user.name",
            // No aggregate function.
            "from X group by user.name select user.name",
            // count() without grouping counts each row.
            "from X select count() as n",
            // A wildcard, != (which matches a document without the field), or, not, brackets.
            "from X where user.name = 'adm*' group by url.domain select url.domain, count() as n",
            "from X where user.name != alice group by url.domain select url.domain, count() as n",
            "from X where user.name = alice or user.name = bob group by url.domain select url.domain, count() as n",
            "from X where not user.name = alice group by url.domain select url.domain, count() as n",
            "from X where (user.name = alice) group by url.domain select url.domain, count() as n",
            // A date, a text field, a range on a keyword field, a number compared with text.
            "from X where @timestamp > day() - 1d group by url.domain select url.domain, count() as n",
            "from X where message = error group by url.domain select url.domain, count() as n",
            "from X where user.name > m group by url.domain select url.domain, count() as n",
            "from X where bytes > lots group by url.domain select url.domain, count() as n",
            "from X where nope = 1 group by url.domain select url.domain, count() as n",
            // Grouping by text, a date, a field the index hasn't, or twice.
            "from X group by message select message, count() as n",
            "from X group by @timestamp select @timestamp, count() as n",
            "from X group by nope select nope, count() as n",
            "from X group by user.name group by url.domain select user.name, url.domain, count() as n",
            // Other clauses and functions.
            "from X eval b = bytes * 2 group by user.name select user.name, sum(b) as s",
            "from X group by user.name having n > 2 select user.name, count() as n",
            "from X filter bytes > 1 group by user.name select user.name, count() as n",
            "from X group by user.name select user.name, countUnique(url.domain) as n",
            "from X group by user.name select user.name, sum(message) as n",
            "from X group by user.name select user.name, sum(bytes * 2) as n",
            "from X group by user.name select url.domain, count() as n",
            // Sorting by what isn't selected, or a limit per grouping level.
            "from X group by user.name sort by bytes select user.name, count() as n",
            "from X group by user.name select user.name, count() as n limit 10, 5",
            // Two columns of one name.
            "from X group by user.name select user.name, count() as user.name",
            // Not StroomQL.
            "",
            "group by user.name select user.name, count() as n where",
    })
    void anythingElseGivesNoHint(final String query) {
        assertThat(translate(query)).isNull();
    }

    @Test
    void anExamplePipelineFromTheIndexsFields() {
        assertThat(EsqlHints.examplePipeline(List.of(
                QueryField.createId("StreamId"),
                QueryField.createDate("@timestamp"),
                QueryField.createKeyword("user.name"),
                QueryField.createLong("bytes"))))
                .contains("| STATS n = COUNT(*), total = SUM(bytes) BY user.name | SORT total DESC | LIMIT 10");
        assertThat(EsqlHints.examplePipeline(List.of(QueryField.createKeyword("host"), QueryField.createId(
                "EventId"))))
                .contains("| STATS n = COUNT(*) BY host | SORT n DESC | LIMIT 10");
        assertThat(EsqlHints.examplePipeline(List.of(QueryField.createText("message")))).isEmpty();
        assertThat(EsqlHints.name("odd`name")).isEqualTo("`odd``name`");
    }

    private static String translate(final String query) {
        return StroomQlToEsql.translate(query, FIELDS)
                .map(StroomQlToEsql.Translation::pipeline)
                .orElse(null);
    }
}
