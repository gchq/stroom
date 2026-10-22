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

package stroom.ai.impl.tools;

import stroom.ai.impl.tools.MisleadingSearches.Exact;
import stroom.ai.impl.tools.MisleadingSearches.Search;
import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// The searches through Stroom whose answer would mislead, ported from the Stroom MCP server's tests of
/// `_searchable` (`tests/test_indexing.py`, `tests/test_templatecheck.py`, `tests/test_off_path.py`).
class TestMisleadingSearches {

    private static final String ELASTIC = MisleadingSearches.ELASTICSEARCH;
    private static final String LUCENE = MisleadingSearches.LUCENE;

    @Test
    void searchesThatWouldMisleadOnElasticsearchAreRefusedWithWhatToUse() {
        final String text = refusal(ELASTIC, List.of(
                new Search("user", Condition.STARTS_WITH, "al"),
                new Search("msg", Condition.CONTAINS, "err"),
                new Search("tags", Condition.IS_NULL, ""),
                new Search("tags", Condition.IS_NOT_NULL, ""),
                new Search("user", Condition.IN, "alice bob")), Set.of(), Map.of());

        assertThat(text).startsWith(MisleadingSearches.REFUSAL)
                .contains("use EQUALS 'al*'")
                .contains("use EQUALS '*err*'")
                .contains("commas")
                .contains("tags IS_NULL")
                .contains("tags IS_NOT_NULL: Stroom matches every document")
                .contains("EQUALS '*'");
    }

    @Test
    void searchesThatWouldMisleadOnLuceneAreRefusedWithWhatToUse() {
        // On Lucene (eval case 22): STARTS_WITH and ENDS_WITH, CONTAINS on a keyword field, a range on a text field.
        final Map<String, String> text = Map.of("UserId", "KEYWORD", "ClientIPAddress", "KEYWORD",
                "Description", "ALPHA_NUMERIC");

        final String said = refusal(LUCENE, List.of(
                new Search("UserId", Condition.STARTS_WITH, "ca"),
                new Search("HostName", Condition.STARTS_WITH, "sw*"),
                new Search("UserId", Condition.ENDS_WITH, "ol"),
                new Search("UserId", Condition.CONTAINS, "aro"),
                new Search("ClientIPAddress", Condition.BETWEEN, "10.1.0.0,10.1.255.255")), Set.of(), text);

        assertThat(said).contains("use EQUALS 'ca*'")
                .contains("use EQUALS '*ol'")
                .contains("use EQUALS '*aro*'")
                .contains("HostName STARTS_WITH 'sw*': Stroom finds nothing with STARTS_WITH on a Lucene index; "
                          + "use EQUALS 'sw*'")
                .contains("ClientIPAddress BETWEEN '10.1.0.0,10.1.255.255': ClientIPAddress is a text field in "
                          + "Lucene");
    }

    @Test
    void inOnLuceneFindsNothingWhenAValueHoldsASpace() {
        // Seen (eval case 15): IN 'Main entrance,Server room' found 0 where EQUALS 'Server room' found 2.
        final String said = refusal(LUCENE, List.of(
                new Search("Door", Condition.IN, "Main entrance,Server room"),
                new Search("Door", Condition.IN, "Main entrance")), Set.of(), Map.of());

        assertThat(said).contains("Door IN 'Main entrance,Server room': Stroom finds nothing with IN on a Lucene "
                                  + "index")
                .contains("Door IN 'Main entrance': separate the values with commas (one value holding a space is "
                          + "EQUALS 'Main entrance')");
    }

    @Test
    void wholeWordsRangesOnNumbersAndDatesAndWildcardsWork() {
        // Whole words in an analysed text field, and ranges on numbers and dates, work.
        final Map<String, String> text = Map.of("UserId", "KEYWORD", "Description", "ALPHA_NUMERIC");

        assertThat(MisleadingSearches.refusal(LUCENE, List.of(
                new Search("Description", Condition.CONTAINS, "Session"),
                new Search("EventTime", Condition.BETWEEN, "2026-10-01,2026-10-02"),
                new Search("UserId", Condition.EQUALS, "ca*")), Set.of(), List.of(), text, Map.of())).isEmpty();
        assertThat(MisleadingSearches.refusal(ELASTIC, List.of(
                new Search("user", Condition.EQUALS, "al*"),
                new Search("status", Condition.BETWEEN, "100,300")), Set.of(), List.of(), Map.of(), Map.of()))
                .isEmpty();
    }

    @Test
    void aContainsOnElasticsearchIsAMisleadingSearch() {
        assertThat(refusal(ELASTIC, List.of(new Search("User.Id", Condition.CONTAINS, "ali")), Set.of(), Map.of()))
                .isEqualTo(MisleadingSearches.REFUSAL + "User.Id CONTAINS 'ali': Stroom finds nothing with CONTAINS "
                           + "on Elasticsearch; use EQUALS '*ali*' (a wildcard) or MATCHES_REGEX");
    }

    @Test
    void aWildcardOnAnAddressIsRefusedWithItsCidrRange() {
        // Seen in VS Code: IpAddress EQUALS 192.0.2.* found nothing on Elasticsearch, and verify_index failed.
        final Optional<String> said = MisleadingSearches.refusal(ELASTIC,
                List.of(new Search("IpAddress", Condition.EQUALS, "192.0.2.*")), Set.of("IpAddress"),
                List.of(new Exact("IpAddress", "10.0.*")), Map.of(), Map.of());

        assertThat(said).contains(MisleadingSearches.REFUSAL
                                  + "IpAddress = '10.0.*': IpAddress is an ip field, where a wildcard finds nothing; "
                                  + "use the CIDR range '10.0.0.0/16'; IpAddress EQUALS '192.0.2.*': IpAddress is an "
                                  + "ip field, where a wildcard finds nothing; use EQUALS '192.0.2.0/24' (a CIDR "
                                  + "range)");
        // On Lucene an address is text, and a wildcard on it works.
        assertThat(MisleadingSearches.refusal(LUCENE, List.of(new Search("IpAddress", Condition.EQUALS,
                "192.0.2.*")), Set.of("IpAddress"), List.of(), Map.of(), Map.of())).isEmpty();
    }

    @Test
    void aCheckOnAFieldNoSearchFindsIsRefusedSayingWhy() {
        final Map<String, String> said = Map.of("secret", "the index template agreed for it doesn't index it "
                                                          + "(index: false, or an object with enabled: false)");

        assertThat(MisleadingSearches.refusal(ELASTIC, List.of(), Set.of(), List.of(), Map.of(), said))
                .hasValueSatisfying(text -> assertThat(text).matches(
                        ".*secret: the index template agreed .* so no search through Stroom finds it.*"));
    }

    @Test
    void isNotNullIsSearchedAsAnyValueOnElasticsearch() {
        // Seen in testing: refused with the advice to use EQUALS '*', the agent sent IS_NOT_NULL again and again.
        assertThat(MisleadingSearches.elasticsearchNullAs(Condition.IS_NOT_NULL)).contains(Condition.EQUALS);
        assertThat(MisleadingSearches.elasticsearchNullAs(Condition.IS_NULL)).contains(Condition.NOT_EQUALS);
        assertThat(MisleadingSearches.elasticsearchNullAs(Condition.EQUALS)).isEmpty();
        // Nothing left to refuse once searched as what it means.
        assertThat(MisleadingSearches.refusal(ELASTIC, List.of(new Search("tags", Condition.EQUALS, "*"),
                new Search("user.name", Condition.EQUALS, "bob")), Set.of(), List.of(), Map.of(), Map.of())).isEmpty();
    }

    @Test
    void aWildcardAddressIsGivenAsTheCidrRangeItMeans() {
        assertThat(MisleadingSearches.cidr("192.0.2.*")).isEqualTo("192.0.2.0/24");
        assertThat(MisleadingSearches.cidr("10.0.*")).isEqualTo("10.0.0.0/16");
        assertThat(MisleadingSearches.cidr("10.*")).isEqualTo("10.0.0.0/8");
        assertThat(MisleadingSearches.cidr("*")).isEqualTo("10.0.0.0/8");
        assertThat(MisleadingSearches.cidr("1.2.3.4*")).isEqualTo("1.2.3.0/24");
    }

    @Test
    void theBackendTheFieldsAndTheSearchesOfAQueryAreFound() {
        assertThat(MisleadingSearches.backendOf(new DocRef("ElasticIndex", "e"))).contains(ELASTIC);
        assertThat(MisleadingSearches.backendOf(new DocRef("Index", "l"))).contains(LUCENE);
        assertThat(MisleadingSearches.backendOf(new DocRef("View", "v"))).isEmpty();
        assertThat(MisleadingSearches.backendOf(null)).isEmpty();
        final List<QueryField> fields = List.of(QueryField.createIpV4Address("source.ip"),
                QueryField.createText("user.name"),
                QueryField.builder().fldName("raw").fldType(FieldType.TEXT).queryable(false).build());
        assertThat(MisleadingSearches.ipFields(fields)).containsExactly("source.ip");
        assertThat(MisleadingSearches.unindexed(new DocRef("ElasticIndex", "e"), fields, List.of("raw", "user.name")))
                .isEqualTo(Map.of("raw", "Stroom lists it as not searchable"));
        assertThat(MisleadingSearches.unindexed(new DocRef("Index", "l"), fields, List.of("raw")))
                .isEqualTo(Map.of("raw", "the index doc's field isn't indexed"));

        final ExpressionOperator expression = ExpressionOperator.builder().op(Op.AND)
                .addTerm("user.name", Condition.EQUALS, "bob")
                .addOperator(ExpressionOperator.builder().op(Op.OR)
                        .addTerm("tags", Condition.IS_NULL, null)
                        .addTerm(ExpressionTerm.builder().enabled(false).field("off").condition(Condition.CONTAINS)
                                .value("x").build())
                        .build())
                .build();
        assertThat(MisleadingSearches.searches(expression)).containsExactly(
                new Search("user.name", Condition.EQUALS, "bob"), new Search("tags", Condition.IS_NULL, ""));
        assertThat(MisleadingSearches.searches(null)).isEmpty();
    }

    private static String refusal(final String backend,
                                  final List<Search> searches,
                                  final Set<String> ipFields,
                                  final Map<String, String> luceneText) {
        return MisleadingSearches.refusal(backend, searches, ipFields, List.of(), luceneText, Map.of())
                .orElseThrow();
    }
}
