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

package stroom.search.elastic;

import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.search.elastic.ElasticEndToEndTestHelper.SearchResult;
import stroom.search.elastic.search.ElasticSearchProvider;
import stroom.test.AbstractCoreIntegrationTest;

import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/// Indexes events to a real Elasticsearch node, then searches them through Stroom's search API, as a
/// dashboard or query would. See [ElasticTestNode] for how the node is found.
class TestElasticSearch_EndToEnd extends AbstractCoreIntegrationTest {

    private static final String USER_ID = "UserId";
    private static final String HOST_NAME = "HostName";
    private static final String EVENT_TIME = "EventTime";
    private static final String COMMAND = "Command";
    private static final String HOST = "Host";
    private static final String BYTES = "Bytes";
    private static final String TIME = "Time";
    private static final String IP = "Ip";
    private static final String USERS = "Users";
    private static final String USER_NAME = "Users.Name";
    private static final String USER_ROLE = "Users.Role";
    private static final String USER_SESSIONS = "Users.Sessions";
    private static final String USER_DEVICE = "Users.Sessions.Device";
    // Small enough that a search for every network event takes several scroll requests
    private static final int SCROLL_SIZE = 5;

    @Inject
    private ElasticEndToEndTestHelper helper;
    @Inject
    private ElasticIndexStore elasticIndexStore;
    @Inject
    private ElasticSearchProvider elasticSearchProvider;

    private ElasticTestNode node;
    private DocRef clusterRef;
    private final List<String> indexPatternsToDelete = new ArrayList<>();
    private final List<String> templatesToDelete = new ArrayList<>();

    @BeforeEach
    void setUp() {
        node = ElasticTestNode.getOrSkip();
        clusterRef = helper.createCluster(node);
    }

    @AfterEach
    void tearDown() {
        if (node != null) {
            indexPatternsToDelete.forEach(node::deleteIndices);
            templatesToDelete.forEach(node::deleteIndexTemplate);
        }
    }

    @Test
    void testTermQuery() {
        final DocRef indexRef = indexNetworkEvents();

        final SearchResult result = search(indexRef, ExpressionOperator.builder()
                .addTerm(USER_ID, Condition.EQUALS, "user5"), USER_ID, HOST_NAME);

        assertThat(result.rows()).hasSize(5);
        assertThat(result.column(0)).containsOnly("user5");
        assertThat(result.column(1)).containsExactlyInAnyOrder("server1", "server4", "server4", "server4", "server8");
    }

    @Test
    void testBooleanQueries() {
        final DocRef indexRef = indexNetworkEvents();

        final SearchResult or = search(indexRef, ExpressionOperator.builder()
                .op(Op.OR)
                .addTerm(USER_ID, Condition.EQUALS, "user6")
                .addTerm(USER_ID, Condition.EQUALS, "user1"), USER_ID);
        assertThat(or.column(0))
                .hasSize(6)
                .containsOnly("user1", "user6");

        final SearchResult and = search(indexRef, ExpressionOperator.builder()
                .addTerm(USER_ID, Condition.EQUALS, "user1")
                .addTerm(HOST_NAME, Condition.EQUALS, "server6"), USER_ID, HOST_NAME);
        assertThat(and.rows())
                .hasSize(2)
                .containsOnly(List.of("user1", "server6"));

        final SearchResult not = search(indexRef, ExpressionOperator.builder()
                .addOperator(ExpressionOperator.builder()
                        .op(Op.NOT)
                        .addTerm(USER_ID, Condition.EQUALS, "user2")
                        .build()), USER_ID);
        assertThat(not.column(0))
                .hasSize(ElasticEndToEndTestHelper.NETWORK_EVENT_COUNT - 6)
                .doesNotContain("user2");
    }

    @Test
    void testDateRangeQuery() {
        final DocRef indexRef = indexNetworkEvents();

        final SearchResult result = search(indexRef, ExpressionOperator.builder()
                .addTerm(EVENT_TIME,
                        Condition.BETWEEN,
                        "2007-08-18T13:21:48.000Z,2007-08-18T13:23:49.000Z"), USER_ID, HOST_NAME);

        assertThat(result.rows()).containsExactlyInAnyOrder(
                List.of("user2", "server5"),
                List.of("user3", "server6"));
    }

    @Test
    void testTextAndMultiFieldQueries() {
        final DocRef indexRef = indexNetworkEvents();

        // A word in the analysed text field
        final SearchResult text = search(indexRef, ExpressionOperator.builder()
                .addTerm(COMMAND, Condition.EQUALS, "foo"), COMMAND);
        assertThat(text.column(0))
                .hasSize(19)
                .containsOnly("msg=foo bar");

        // The whole value, in the text field's keyword multi-field
        final SearchResult keyword = search(indexRef, ExpressionOperator.builder()
                .addTerm(COMMAND + ".keyword", Condition.EQUALS, "some message"), USER_ID, HOST_NAME);
        assertThat(keyword.rows()).containsExactly(List.of("user1", "server6"));
    }

    @Test
    void testInDictionaryQuery() {
        final DocRef indexRef = indexNetworkEvents();
        final DocRef dictionaryRef = helper.createDictionary("Users", "user1\nuser6");

        // The term has a dictionary but no value, as the query editor creates it
        final SearchResult result = search(indexRef, ExpressionOperator.builder()
                .addTerm(ExpressionTerm.builder()
                        .field(USER_ID)
                        .condition(Condition.IN_DICTIONARY)
                        .docRef(dictionaryRef)
                        .build()), USER_ID);

        assertThat(result.column(0))
                .hasSize(6)
                .containsOnly("user1", "user6");
    }

    // Only a search that completes normally, as the scroll context must also be cleared when a search fails part
    // way through. TestElasticSearchTaskHandlerWireMock covers that, as a real node can't easily be made to fail.
    @Test
    void testSearchAcrossScrollPagesLeavesNoScrollContextOpen() {
        final DocRef indexRef = indexNetworkEvents();
        final long contextsBefore = node.getOpenSearchContexts();

        final SearchResult result = search(indexRef, ExpressionOperator.builder(), USER_ID);

        assertThat(result.rows()).hasSize(ElasticEndToEndTestHelper.NETWORK_EVENT_COUNT);
        assertThat(node.getOpenSearchContexts())
                .as("Open search contexts after the search")
                .isLessThanOrEqualTo(contextsBefore);
    }

    @Test
    void testNetworkFieldInfo() {
        final DocRef indexRef = indexNetworkEvents();

        final Map<String, QueryField> fields = getFields(indexRef);

        assertField(fields, USER_ID, FieldType.KEYWORD, true);
        assertField(fields, EVENT_TIME, FieldType.DATE, true);
        assertField(fields, "IPAddress", FieldType.IPV4_ADDRESS, true);
        assertField(fields, COMMAND, FieldType.TEXT, true);
        // The text field's multi-field is offered as a field in its own right
        assertField(fields, COMMAND + ".keyword", FieldType.KEYWORD, true);
    }

    @Test
    void testUnsignedLongValueWithinTheRangeOfALong() {
        final DocRef indexRef = indexTypedEvents();

        final SearchResult result = search(indexRef, ExpressionOperator.builder()
                .addTerm(HOST, Condition.EQUALS, "gamma"), HOST, BYTES);

        assertThat(result.errors()).isEmpty();
        assertThat(result.rows()).containsExactly(List.of("gamma", "42"));
    }

    @Test
    void testUnsignedLongValuesBeyondTheRangeOfALong() {
        final DocRef indexRef = indexTypedEvents();

        // No Val type can hold these values, so returning them is an error rather than a value
        final SearchResult result = search(indexRef, ExpressionOperator.builder()
                .addTerm(HOST, Condition.EQUALS, "alpha"), HOST, BYTES);

        assertThat(result.rows()).isEmpty();
        assertThat(result.errors())
                .anySatisfy(error -> assertThat(error)
                        .contains("field 'Bytes'")
                        .contains("Value 18446744073709551615 is outside the range of a long"));
    }

    @Test
    void testDateNanosRangeQuery() {
        final DocRef indexRef = indexTypedEvents();

        final SearchResult result = search(indexRef, ExpressionOperator.builder()
                .addTerm(TIME, Condition.BETWEEN, "2026-01-01T00:00:00.000Z,2026-01-02T00:00:00.000Z"), HOST);

        assertThat(result.column(0)).containsExactlyInAnyOrder("beta", "gamma");
    }

    @Test
    void testAliasQuery() {
        final DocRef indexRef = indexTypedEvents();

        final SearchResult result = search(indexRef, ExpressionOperator.builder()
                .addTerm("HostAlias", Condition.EQUALS, "gamma"), HOST);

        assertThat(result.column(0)).containsExactly("gamma");
    }

    @Test
    void testIpAddressQueries() {
        final DocRef indexRef = indexTypedEvents();

        final SearchResult match = search(indexRef, ExpressionOperator.builder()
                .addTerm(IP, Condition.EQUALS, "10.0.0.1"), HOST);
        assertThat(match.errors()).isEmpty();
        assertThat(match.column(0)).containsExactly("alpha");

        // An address with an octet out of range is rejected rather than wrapping round to another address
        final SearchResult outOfRange = search(indexRef, ExpressionOperator.builder()
                .addTerm(IP, Condition.EQUALS, "266.0.0.1"), HOST);
        assertThat(outOfRange.rows()).isEmpty();
        // Stroom rejects it itself, rather than sending it to Elasticsearch
        assertThat(outOfRange.errors())
                .anySatisfy(error -> assertThat(error).contains("Invalid IPv4 address format: 266.0.0.1"));
    }

    @Test
    void testNestedFieldQueries() {
        final DocRef indexRef = indexTypedEvents();

        // A field of a nested object
        assertThat(search(indexRef, ExpressionOperator.builder()
                .addTerm(USER_NAME, Condition.EQUALS, "alice"), HOST).column(0))
                .containsExactlyInAnyOrder("alpha", "beta");

        // A field of an object nested within another nested object
        assertThat(search(indexRef, ExpressionOperator.builder()
                .addTerm(USER_DEVICE, Condition.EQUALS, "tablet"), HOST).column(0))
                .containsExactly("alpha");

        // A nested condition combined with a condition on a field that isn't nested
        assertThat(search(indexRef, ExpressionOperator.builder()
                .addTerm(HOST, Condition.EQUALS, "beta")
                .addTerm(USER_ROLE, Condition.EQUALS, "admin"), HOST).column(0))
                .containsExactly("beta");

        // Negating a nested condition excludes every event with any nested object that matches it
        assertThat(search(indexRef, ExpressionOperator.builder()
                .addOperator(ExpressionOperator.builder()
                        .op(Op.NOT)
                        .addTerm(USER_NAME, Condition.EQUALS, "bob")
                        .build()), HOST).column(0))
                .containsExactly("beta");
    }

    @Test
    void testNestedConditionsMatchTheSameObject() {
        // alpha has users alice (admin) and bob (user, with phone and tablet sessions), beta has alice (user)
        // and carol (admin), and gamma has bob (user)
        final DocRef indexRef = indexTypedEvents();

        // One user who is both alice and an admin, so not beta, whose admin is carol
        assertThat(search(indexRef, ExpressionOperator.builder()
                .addTerm(USER_NAME, Condition.EQUALS, "alice")
                .addTerm(USER_ROLE, Condition.EQUALS, "admin"), HOST).column(0))
                .containsExactly("alpha");

        // A != condition applies to the same user as the others
        assertThat(search(indexRef, ExpressionOperator.builder()
                .addTerm(USER_NAME, Condition.EQUALS, "alice")
                .addTerm(USER_ROLE, Condition.NOT_EQUALS, "admin"), HOST).column(0))
                .containsExactly("beta");

        // On its own, a != condition still means that no user has the value
        assertThat(search(indexRef, ExpressionOperator.builder()
                .addTerm(USER_ROLE, Condition.NOT_EQUALS, "admin"), HOST).column(0))
                .containsExactly("gamma");

        // A NOT excludes events matching any of its conditions, so those with an alice or an admin
        assertThat(search(indexRef, ExpressionOperator.builder()
                .addOperator(ExpressionOperator.builder()
                        .op(Op.NOT)
                        .addTerm(USER_NAME, Condition.EQUALS, "alice")
                        .addTerm(USER_ROLE, Condition.EQUALS, "admin")
                        .build()), HOST).column(0))
                .containsExactly("gamma");

        // A NOT of an AND excludes only events with a user who matches all of its conditions
        assertThat(search(indexRef, ExpressionOperator.builder()
                .addOperator(ExpressionOperator.builder()
                        .op(Op.NOT)
                        .addOperator(ExpressionOperator.builder()
                                .addTerm(USER_NAME, Condition.EQUALS, "alice")
                                .addTerm(USER_ROLE, Condition.EQUALS, "admin")
                                .build())
                        .build()), HOST).column(0))
                .containsExactlyInAnyOrder("beta", "gamma");

        // A session of the same user, two levels down
        assertThat(search(indexRef, ExpressionOperator.builder()
                .addTerm(USER_NAME, Condition.EQUALS, "bob")
                .addTerm(USER_DEVICE, Condition.EQUALS, "tablet"), HOST).column(0))
                .containsExactly("alpha");
        assertThat(search(indexRef, ExpressionOperator.builder()
                .addTerm(USER_NAME, Condition.EQUALS, "alice")
                .addTerm(USER_DEVICE, Condition.EQUALS, "tablet"), HOST).column(0))
                .isEmpty();

        // An OR of conditions on the same user, within a group
        assertThat(search(indexRef, ExpressionOperator.builder()
                .addTerm(USER_ROLE, Condition.EQUALS, "admin")
                .addOperator(ExpressionOperator.builder()
                        .op(Op.OR)
                        .addTerm(USER_NAME, Condition.EQUALS, "carol")
                        .addTerm(USER_NAME, Condition.EQUALS, "bob")
                        .build()), HOST).column(0))
                .containsExactly("beta");
    }

    @Test
    void testNestedFieldValues() {
        final DocRef indexRef = indexTypedEvents();

        // The values of a field from every nested object of an event, in document order, at both levels
        final SearchResult result = search(indexRef, ExpressionOperator.builder()
                .addTerm(USER_DEVICE, Condition.EQUALS, "phone"), HOST, USER_NAME, USER_DEVICE);

        assertThat(result.rows()).containsExactlyInAnyOrder(
                List.of("alpha", "alice, bob", "laptop, phone, tablet"),
                List.of("beta", "alice, carol", "phone"));
    }

    @Test
    void testTypedFieldInfo() {
        final DocRef indexRef = indexTypedEvents();

        final Map<String, QueryField> fields = getFields(indexRef);

        assertField(fields, TIME, FieldType.DATE, true);
        // A date_nanos field that isn't indexed can't be queried
        assertField(fields, "TimeNotIndexed", FieldType.DATE, false);
        // An alias is only queryable if the field it points to is
        assertField(fields, "HostAlias", FieldType.KEYWORD, true);
        assertField(fields, "TimeNotIndexedAlias", FieldType.DATE, false);
        assertField(fields, IP, FieldType.IPV4_ADDRESS, true);
        assertField(fields, BYTES, FieldType.LONG, true);
        assertField(fields, "Message.keyword", FieldType.KEYWORD, true);
        // A nested object can't be queried itself, only the fields within it, at any level of nesting
        assertField(fields, USERS, FieldType.NESTED, false);
        assertField(fields, USER_NAME, FieldType.KEYWORD, true);
        assertField(fields, USER_SESSIONS, FieldType.NESTED, false);
        assertField(fields, USER_DEVICE, FieldType.KEYWORD, true);
    }

    private DocRef indexNetworkEvents() {
        final String indexName = ElasticTestNode.createIndexName("network");
        indexPatternsToDelete.add(indexName);
        helper.indexNetworkEvents(node, clusterRef, indexName);
        return helper.createIndexDoc(clusterRef, indexName, EVENT_TIME, SCROLL_SIZE);
    }

    private DocRef indexTypedEvents() {
        final String indexBaseName = ElasticTestNode.createIndexName("typed");
        templatesToDelete.add(indexBaseName);
        indexPatternsToDelete.add(indexBaseName + "-*");
        helper.indexTypedEvents(node, clusterRef, indexBaseName);
        return helper.createIndexDoc(clusterRef, indexBaseName + "-*", TIME, SCROLL_SIZE);
    }

    private SearchResult search(final DocRef indexRef,
                                final ExpressionOperator.Builder expression,
                                final String... fieldNames) {
        return helper.search(indexRef, expression.build(), List.of(fieldNames));
    }

    private Map<String, QueryField> getFields(final DocRef indexRef) {
        return elasticSearchProvider.getDataSourceFields(elasticIndexStore.readDocument(indexRef))
                .stream()
                .collect(Collectors.toMap(QueryField::getFldName, Function.identity()));
    }

    private static void assertField(final Map<String, QueryField> fields,
                                    final String fieldName,
                                    final FieldType expectedType,
                                    final boolean expectedQueryable) {
        assertThat(fields).containsKey(fieldName);
        final QueryField field = fields.get(fieldName);
        assertThat(field.getFldType())
                .as("Type of field %s", fieldName)
                .isEqualTo(expectedType);
        assertThat(field.queryable())
                .as("Field %s is queryable", fieldName)
                .isEqualTo(expectedQueryable);
    }
}
