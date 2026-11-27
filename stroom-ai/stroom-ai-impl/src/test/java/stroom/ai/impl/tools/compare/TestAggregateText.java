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

package stroom.ai.impl.tools.compare;

import stroom.ai.api.ElasticKeyStreams.Transform;
import stroom.ai.impl.tools.compare.ActivityProfiles.Fields;
import stroom.ai.impl.tools.compare.ActivityProfiles.Scope;
import stroom.ai.impl.tools.compare.AggregateQuery.Source;
import stroom.docref.DocRef;
import stroom.index.shared.LuceneIndexDoc;
import stroom.search.elastic.shared.ElasticIndexDoc;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestAggregateText {

    private static final Source ELASTIC = new Source(new DocRef(ElasticIndexDoc.TYPE, "e-uuid", "Proxy"),
            "@timestamp", true);
    private static final Source LUCENE = new Source(new DocRef(LuceneIndexDoc.TYPE, "l-uuid", "Endpoint \"A\""),
            "EventTime", false);
    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-02-01T00:00:00Z");
    private static final Scope SCOPE = new Scope("user.id", List.of("p-1001", "a\"b"));
    private static final Fields FIELDS = new Fields(List.of("bytes_out"), List.of("destination.domain"));

    @Test
    void esqlDaily() {
        assertThat(AggregateText.esql(ActivityProfiles.daily(ELASTIC, SCOPE, FIELDS, FROM, TO), null)).isEqualTo(
                "| WHERE `@timestamp` >= TO_DATETIME(\"2026-01-01T00:00:00.000Z\") AND `@timestamp` < "
                + "TO_DATETIME(\"2026-02-01T00:00:00.000Z\") AND `user.id` IN (\"p-1001\", \"a\\\"b\")\n"
                + "| EVAL b = DATE_FORMAT(\"yyyy-MM-dd\", `@timestamp`)\n"
                + "| STATS events = COUNT(*), first_t = MIN(`@timestamp`), last_t = MAX(`@timestamp`), s0 = "
                + "SUM(`bytes_out`), d0 = COUNT_DISTINCT(`destination.domain`) BY b\n"
                + "| LIMIT 10000");
    }

    @Test
    void esqlLogonIdsInOtherWrittenFormsAreMatchedThroughAScriptedFieldOnceTheRangeIsNarrowed() {
        final AggregateQuery query = ActivityProfiles.hourly(ELASTIC, new Scope("user.name", List.of("jsmith")),
                FROM, TO).withTransformed("user.name", List.of(Transform.STRIP_DOMAIN, Transform.LOWERCASE));

        assertThat(AggregateText.esql(query, null)).startsWith(
                "| WHERE `@timestamp` >= TO_DATETIME(\"2026-01-01T00:00:00.000Z\") AND `@timestamp` < "
                + "TO_DATETIME(\"2026-02-01T00:00:00.000Z\")\n"
                + "| EVAL transformed_0 = TO_LOWER(REPLACE(REPLACE(`user.name`, \"^.*\\\\\\\\\", \"\"), "
                + "\"@.*$\", \"\"))\n"
                + "| WHERE transformed_0 IN (\"jsmith\")\n"
                + "| EVAL b = DATE_TRUNC(1 hour, `@timestamp`)");
        // Only a field filtered on is transformed; StroomQL can't.
        assertThat(ActivityProfiles.hourly(ELASTIC, SCOPE, FROM, TO).withTransformed("host.name",
                List.of(Transform.LOWERCASE)).transformed()).isEmpty();
        assertThatThrownBy(() -> AggregateText.stroomQl(ActivityProfiles.hourly(LUCENE, SCOPE, FROM, TO)
                .withTransformed("user.id", List.of(Transform.LOWERCASE)), ZoneOffset.UTC))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("needs ES|QL");
    }

    @Test
    void esqlDailyAtAFixedOffsetWhereTheClusterRefusesTheZone() {
        assertThat(AggregateText.esql(ActivityProfiles.daily(ELASTIC, SCOPE, FIELDS, FROM, TO), 600))
                .contains("| EVAL b = DATE_FORMAT(\"yyyy-MM-dd\", `@timestamp` + 600 minutes)");
    }

    @Test
    void esqlHourlyIsInUtc() {
        assertThat(AggregateText.esql(ActivityProfiles.hourly(ELASTIC, SCOPE, FROM, TO), null))
                .contains("| EVAL b = DATE_TRUNC(1 hour, `@timestamp`)")
                .contains("| STATS events = COUNT(*) BY b");
    }

    @Test
    void esqlActivePeopleWithOneEventEach() {
        assertThat(AggregateText.esql(ActivityProfiles.active(ELASTIC, "user.id", FROM, TO, 500), null))
                .isEqualTo("| WHERE `@timestamp` >= TO_DATETIME(\"2026-01-01T00:00:00.000Z\") AND `@timestamp` < "
                           + "TO_DATETIME(\"2026-02-01T00:00:00.000Z\")\n"
                           + "| EVAL one_event_key = CONCAT(TO_STRING(`StreamId`), \":\", TO_STRING(`EventId`))\n"
                           + "| STATS events = COUNT(*), one_event = MIN(one_event_key) BY g0 = `user.id`\n"
                           + "| LIMIT 500");
    }

    @Test
    void esqlWhatWasDoneToThePersonLeavesOutTheirOwnActs() {
        final ActivityProfiles.TargetScope target = new ActivityProfiles.TargetScope("user.target.id",
                List.of("p-1001"), "user.id", List.of("p-1001"));

        assertThat(AggregateText.esql(ActivityProfiles.targetDaily(ELASTIC, target, "event.action", FROM, TO), null))
                .isEqualTo("| WHERE `@timestamp` >= TO_DATETIME(\"2026-01-01T00:00:00.000Z\") AND `@timestamp` < "
                           + "TO_DATETIME(\"2026-02-01T00:00:00.000Z\") AND `user.target.id` IN (\"p-1001\") AND "
                           + "(`user.id` IS NULL OR `user.id` NOT IN (\"p-1001\"))\n"
                           + "| EVAL b = DATE_FORMAT(\"yyyy-MM-dd\", `@timestamp`)\n"
                           + "| STATS events = COUNT(*) BY b, g0 = `event.action`\n"
                           + "| LIMIT 10000");
        // Everyone's, done by someone else: the target held, the actor none or another.
        assertThat(AggregateText.esql(ActivityProfiles.targetPopulation(ELASTIC, "user.target.id", "user.id", FROM,
                TO), null)).contains("AND `user.target.id` IS NOT NULL AND (`user.id` IS NULL OR `user.id` != "
                                     + "`user.target.id`)\n")
                .contains("| STATS events = COUNT(*), people = COUNT_DISTINCT(`user.target.id`) BY b");
        assertThat(AggregateText.esql(ActivityProfiles.targetPopulation(ELASTIC, "user.target.id", null, FROM, TO),
                null)).contains("AND `user.target.id` IS NOT NULL\n");
        // A re-run's slice keeps them.
        assertThat(ActivityProfiles.targetDaily(ELASTIC, target, null, FROM, TO).between(FROM, FROM.plusSeconds(1))
                .excludes()).isEqualTo(Map.of("user.id", List.of("p-1001")));
        assertThat(CachedAggregates.key(ActivityProfiles.targetDaily(ELASTIC, target, null, FROM, TO)))
                .isNotEqualTo(CachedAggregates.key(ActivityProfiles.daily(ELASTIC, new Scope("user.target.id",
                        List.of("p-1001")), new Fields(List.of(), List.of()), FROM, TO)));
    }

    @Test
    void stroomQlLeavesOutTheirOwnActsButCantCompareTwoFields() {
        final ActivityProfiles.TargetScope target = new ActivityProfiles.TargetScope("TargetUser", List.of("j"),
                "UserId", List.of("j", "adm-j"));

        assertThat(AggregateText.stroomQl(ActivityProfiles.targetDaily(LUCENE, target, null, FROM, TO),
                ZoneOffset.UTC)).contains("  and TargetUser = \"j\"\n  and not (UserId = \"j\" or UserId = "
                                          + "\"adm-j\")\n");
        assertThatThrownBy(() -> AggregateText.stroomQl(
                        ActivityProfiles.targetPopulation(LUCENE, "TargetUser", "UserId", FROM, TO), ZoneOffset.UTC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs ES|QL");
    }

    @Test
    void esqlNameWithABackquote() {
        assertThat(AggregateText.esqlName("odd`name")).isEqualTo("`odd``name`");
    }

    @Test
    void stroomQlDaily() {
        assertThat(AggregateText.stroomQl(ActivityProfiles.daily(LUCENE, new Scope("UserId", List.of("jsmith")),
                FIELDS, FROM, TO), ZoneId.of("Australia/Sydney"))).isEqualTo(
                "from \"Endpoint \\\"A\\\"\"\n"
                + "where EventTime >= \"2026-01-01T00:00:00.000Z\"\n"
                + "  and EventTime < \"2026-02-01T00:00:00.000Z\"\n"
                + "  and UserId = \"jsmith\"\n"
                + "eval b = formatDate(EventTime, \"yyyy-MM-dd\", \"Australia/Sydney\")\n"
                + "group by b\n"
                + "limit 10000\n"
                + "select b as b, count() as events, min(EventTime) as first_t, max(EventTime) as last_t, "
                + "sum(bytes_out) as s0, countUnique(destination.domain) as d0");
    }

    @Test
    void stroomQlSeveralValuesAndBracedNames() {
        final String text = AggregateText.stroomQl(ActivityProfiles.seen(LUCENE, new Scope("User Name",
                List.of("a", "b")), "Host Name", FROM, TO), ZoneOffset.UTC);
        assertThat(text)
                .contains("(${User Name} = \"a\" or ${User Name} = \"b\")")
                .contains("group by ${Host Name}")
                .contains("select ${Host Name} as g0, min(EventTime) as first_t");
    }

    @Test
    void stroomQlHourlyIsInUtc() {
        assertThat(AggregateText.stroomQl(ActivityProfiles.hourly(LUCENE, new Scope("UserId", List.of("j")), FROM,
                TO), ZoneId.of("Europe/London")))
                .contains("eval b = formatDate(EventTime, \"yyyy-MM-dd HH\", \"UTC\")");
    }

    @Test
    void monthsInTheZone() {
        final AggregateQuery query = ActivityProfiles.hourly(ELASTIC, SCOPE, Instant.parse("2026-01-15T00:00:00Z"),
                Instant.parse("2026-03-10T00:00:00Z"));
        final List<AggregateQuery> slices = IndexAggregates.months(query, ZoneId.of("Australia/Sydney"));
        assertThat(slices).extracting(AggregateQuery::from).containsExactly(
                Instant.parse("2026-01-15T00:00:00Z"),
                // Midnight on 1 February in Sydney (UTC+11).
                Instant.parse("2026-01-31T13:00:00Z"),
                Instant.parse("2026-02-28T13:00:00Z"));
        assertThat(slices.getLast().to()).isEqualTo(Instant.parse("2026-03-10T00:00:00Z"));
    }

    @Test
    void monthsOfAnEmptyRange() {
        assertThat(IndexAggregates.months(ActivityProfiles.hourly(ELASTIC, SCOPE, FROM, FROM), ZoneOffset.UTC))
                .isEmpty();
    }

    @Test
    void instantsInEveryForm() {
        assertThat(IndexAggregates.instant("2026-01-01T13:00:00.000+11:00")).isEqualTo("2026-01-01T02:00:00.000Z");
        assertThat(IndexAggregates.instant("2026-01-01 03")).isEqualTo("2026-01-01T03:00:00.000Z");
        assertThat(IndexAggregates.instant("1767225600000")).isEqualTo("2026-01-01T00:00:00.000Z");
        assertThat(IndexAggregates.instant("")).isEmpty();
        assertThat(IndexAggregates.instant("not a time")).isEqualTo("not a time");
    }

    @Test
    void normaliseLeavesADayBucketAsADate() {
        final AggregateQuery query = ActivityProfiles.daily(ELASTIC, SCOPE, FIELDS, FROM, TO);
        final Map<String, String> row = IndexAggregates.normalise(new LinkedHashMap<>(Map.of("b",
                "2026-01-05", "first_t", "2026-01-05T09:00:00.000+11:00")), query);
        assertThat(row).containsEntry("b", "2026-01-05").containsEntry("first_t", "2026-01-04T22:00:00.000Z");
    }
}
