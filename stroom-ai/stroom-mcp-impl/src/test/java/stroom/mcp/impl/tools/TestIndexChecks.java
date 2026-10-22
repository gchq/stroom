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

import stroom.ai.impl.tools.IndexSearches;
import stroom.ai.impl.tools.IndexSearches.Found;
import stroom.ai.impl.tools.IndexSearches.Probe;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.StreamReader.Fetched;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.indexing.EcsSchema;
import stroom.ai.impl.workspace.ToolDeadline;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.DashboardConfig;
import stroom.dashboard.shared.QueryComponentSettings;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.dashboard.shared.TextComponentSettings;
import stroom.docref.DocRef;
import stroom.mcp.impl.BundledKnowledge;
import stroom.mcp.impl.tools.IndexChecks.Dashboard;
import stroom.mcp.impl.tools.IndexChecks.SearchCheck;
import stroom.mcp.impl.tools.IndexChecks.Stepped;
import stroom.query.api.Column;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.security.api.SecurityContext;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;

/// verify_index's checks, ported from the Stroom MCP server's `tests/test_indexing.py` (`_searchable`,
/// `_as_searched`, `_same_value`, `_traced`, `_trace_checks` and `run_test_searches`).
class TestIndexChecks {

    private static final EcsSchema ECS = EcsSchema.from(new BundledKnowledge()).orElseThrow();
    private static final DocRef WEB = new DocRef("ElasticIndex", "i", "web-v1");
    private static final String STEP_OUTPUT = "<array xmlns=\"http://www.w3.org/2005/xpath-functions\"><map>"
                                              + "<number key=\"StreamId\">9</number><number key=\"EventId\">2</number>"
                                              + "<map key=\"user\"><string key=\"name\">bob</string></map></map>"
                                              + "</array>";

    private final IndexSearches indexSearches = Mockito.mock(IndexSearches.class);
    private final StreamReader streamReader = Mockito.mock(StreamReader.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final IndexingMemory memory = new IndexingMemory(securityContext);
    private final List<Duration> slept = new ArrayList<>();
    private final List<Probe> asked = new ArrayList<>();
    private final List<String> doing = new ArrayList<>();
    private final SteppingWorld steppingWorld = new SteppingWorld();
    private final IndexChecks checks = new IndexChecks(indexSearches, steppingWorld.stepping(), streamReader,
            memory, Mockito.mock(IndexTemplateAgreements.class), slept::add);

    private static SearchCheck search(final String field, final String condition, final String value) {
        return new SearchCheck(field, condition, value, null, null);
    }

    @Test
    void searchesThatWouldMisleadOnElasticsearchAreRefusedWithWhatToUse() {
        assertThatThrownBy(() -> IndexChecks.searchable("elasticsearch", List.of(
                search("user", "STARTS_WITH", "al"), search("msg", "CONTAINS", "err"), search("tags", "IS_NULL", ""),
                search("tags", "IS_NOT_NULL", ""), search("user", "IN", "alice bob")), Set.of(), List.of(), Map.of(),
                null, Map.of(), null))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("use EQUALS 'al*'")
                .hasMessageContaining("use EQUALS '*err*'")
                .hasMessageContaining("commas")
                .hasMessageContaining("tags IS_NULL")
                .hasMessageContaining("tags IS_NOT_NULL: Stroom matches every document")
                .hasMessageContaining("EQUALS '*'");
        // On Lucene (eval case 22): STARTS_WITH and ENDS_WITH, CONTAINS on a keyword field, a range on a text field.
        final Map<String, String> text = Map.of("UserId", "KEYWORD", "ClientIPAddress", "KEYWORD", "Description",
                "ALPHA_NUMERIC");
        assertThatThrownBy(() -> IndexChecks.searchable("lucene", List.of(search("UserId", "STARTS_WITH", "ca"),
                search("HostName", "STARTS_WITH", "sw*"), search("UserId", "ENDS_WITH", "ol"),
                search("UserId", "CONTAINS", "aro"), search("ClientIPAddress", "BETWEEN", "10.1.0.0,10.1.255.255")),
                Set.of(), List.of(), text, null, Map.of(), null))
                .hasMessageContaining("use EQUALS 'ca*'")
                .hasMessageContaining("use EQUALS '*ol'")
                .hasMessageContaining("use EQUALS '*aro*'")
                .hasMessageContaining("HostName STARTS_WITH 'sw*': Stroom finds nothing with STARTS_WITH on a Lucene "
                                      + "index; use EQUALS 'sw*'")
                .hasMessageContaining("ClientIPAddress BETWEEN '10.1.0.0,10.1.255.255': ClientIPAddress is a text "
                                      + "field in Lucene");
        assertThatThrownBy(() -> IndexChecks.searchable("lucene", List.of(
                search("Door", "IN", "Main entrance,Server room"), search("Door", "IN", "Main entrance")), Set.of(),
                List.of(), Map.of(), null, Map.of(), null))
                .hasMessageContaining("Door IN 'Main entrance,Server room': Stroom finds nothing with IN on a Lucene "
                                      + "index")
                .hasMessageContaining("Door IN 'Main entrance': separate the values with commas (one value holding a "
                                      + "space is EQUALS 'Main entrance')");
        // Whole words in an analysed text field, and ranges on numbers and dates, work.
        IndexChecks.searchable("lucene", List.of(search("Description", "CONTAINS", "Session"),
                search("EventTime", "BETWEEN", "2026-10-01,2026-10-02"), search("UserId", "EQUALS", "ca*")),
                Set.of(), List.of(), text, null, Map.of(), null);
        IndexChecks.searchable("elasticsearch", List.of(search("user", "EQUALS", "al*"),
                search("status", "BETWEEN", "100,300")), Set.of(), List.of(), Map.of(), null, Map.of(), null);
    }

    @Test
    void aSearchOnAFieldStroomDoesntListSaysWhy() {
        // Seen in production: message EQUALS '*connection*', MATCHES_REGEX and the rest all found 0, as Stroom lists
        // no match_only_text field; the agent dropped the check after three tries without knowing why.
        final Set<String> listed = Set.of("@timestamp", "event.code", "source.ip");
        assertThatThrownBy(() -> IndexChecks.searchable("elasticsearch",
                List.of(search("message", "EQUALS", "*connection*")), Set.of(), List.of(), Map.of(), listed, Map.of(),
                ECS))
                .hasMessageMatching("(?s).*message isn't among the index's fields in Stroom: ecs@mappings maps it as "
                                    + "match_only_text.*Map it as text.*");
        assertThatThrownBy(() -> IndexChecks.searchable("elasticsearch", List.of(), Set.of(),
                List.of(Map.of("field", "acme.ticket", "value", "T-1")), Map.of(), listed, Map.of(),
                ECS))
                .hasMessageContaining("acme.ticket isn't among the index's fields in Stroom: either no document has "
                                      + "it yet");
        IndexChecks.searchable("elasticsearch", List.of(search("event.code", "EQUALS", "TrafficDeny")), Set.of(),
                List.of(), Map.of(), listed, Map.of(), ECS);
    }

    @Test
    void isNotNullIsSearchedAsAnyValueOnElasticsearch() {
        // Seen in testing: refused with the advice to use EQUALS '*', the agent sent IS_NOT_NULL again and again.
        final List<SearchCheck> searched = IndexChecks.asSearched("elasticsearch", List.of(
                search("tags", "IS_NOT_NULL", ""), search("user.name", "EQUALS", "bob")));
        assertThat(searched.getFirst().condition()).isEqualTo("EQUALS");
        assertThat(searched.getFirst().value()).isEqualTo("*");
        assertThat(searched.getFirst().searchedAs()).contains("IS_NOT_NULL");
        assertThat(searched.get(1).condition()).isEqualTo("EQUALS");
        assertThat(searched.get(1).searchedAs()).isNull();
        // Nothing left to refuse.
        IndexChecks.searchable("elasticsearch", searched, Set.of(), List.of(), Map.of(), null, Map.of(), null);
        final SearchCheck none = IndexChecks.asSearched("elasticsearch",
                List.of(new SearchCheck("tags", "IS_NULL", "", 0L, null))).getFirst();
        assertThat(List.of(none.condition(), none.value(), none.expected())).containsExactly("NOT_EQUALS", "*", 0L);
        assertThat(none.searchedAs()).contains("IS_NULL");
        assertThat(IndexChecks.asSearched("lucene", List.of(search("tags", "IS_NOT_NULL", ""))).getFirst()
                .condition()).isEqualTo("IS_NOT_NULL");
    }

    @Test
    void aCheckOnAFieldNoSearchFindsIsRefusedSayingWhy() {
        final Map<String, String> unindexed = Map.of("secret", "the index template agreed for it doesn't index it "
                                                               + "(index: false, or an object with enabled: false)");
        assertThatThrownBy(() -> IndexChecks.searchable("elasticsearch", List.of(), Set.of(), List.of(), Map.of(),
                null, unindexed, null))
                .hasMessageMatching("(?s).*secret: the index template agreed .* so no search through Stroom finds "
                                    + "it.*");
    }

    @Test
    void aFieldOfSeveralValuesIsComparedByEachValue() {
        // Asked about by the user: a dashboard shows a field of several values separated by commas.
        assertThat(IndexChecks.sameValue("80, 443", "443")).isTrue();
        assertThat(IndexChecks.sameValue("80,443", "80")).isTrue();
        assertThat(IndexChecks.sameValue("[80, 443]", "443")).isTrue();
        assertThat(IndexChecks.sameValue("80, 443", "8080")).isFalse();
        assertThat(IndexChecks.sameValue("2026-10-20T08:00:00.000Z, 2026-10-20T09:00:00Z",
                "2026-10-20T09:00:00.000Z")).isTrue();
        assertThat(IndexChecks.sameValue("retry, timeout", "timeout")).isTrue();
        assertThat(IndexChecks.sameValue("Smith, John", "Smith, John")).isTrue();
    }

    @Test
    void aHitIsTracedToItsRecordAndAMismatchFails() {
        final List<long[]> steps = new ArrayList<>();
        final Long[] record = {null};
        final IndexChecks.Stepper stepper = (stream, at) -> {
            steps.add(new long[]{stream, at});
            return new Stepped(record[0], STEP_OUTPUT);
        };
        final Map<String, Object> ok = IndexChecks.traced(Map.of("StreamId", "9", "EventId", "2"),
                Map.of("field", "user.name", "condition", "EQUALS", "value", "bob"), stepper, "xsltFilter");
        final Map<String, Object> wrongValue = IndexChecks.traced(Map.of("StreamId", "9", "EventId", "2"),
                Map.of("field", "user.name", "condition", "EQUALS", "value", "alice"), stepper, "xsltFilter");
        final Map<String, String> carol = new LinkedHashMap<>();
        carol.put("StreamId", "9");
        carol.put("EventId", "5");
        carol.put("user.name", "carol");
        final Map<String, Object> wrongRecord = IndexChecks.traced(carol, Map.of(), stepper, "xsltFilter");
        record[0] = 7L;
        final Map<String, Object> elsewhere = IndexChecks.traced(Map.of("StreamId", "9", "EventId", "2"), Map.of(),
                stepper, "xsltFilter");

        assertThat(ok).isEqualTo(Map.of("traced", true, "stream", 9L, "event", 2L));
        // EventId 2 is the second record, index 1.
        assertThat(steps.getFirst()).containsExactly(9L, 1L);
        assertThat(wrongValue.get("traced")).isEqualTo(false);
        assertThat((String) wrongValue.get("why")).contains("user.name = ['bob'], not alice");
        // What was compared is said: the record stepped, its document's EventId, and the column that differs.
        assertThat(wrongRecord.get("traced")).isEqualTo(false);
        assertThat(wrongRecord.get("why")).isEqualTo("stepping record index 4 of stream 9 (the hit's EventId 5, "
                                                     + "counted from 1) gives one document, numbered EventId 2 by "
                                                     + "stepping, and it isn't the hit's: its user.name is ['bob'], "
                                                     + "where the hit has carol");
        assertThat(elsewhere.get("traced")).isEqualTo(false);
        assertThat((String) elsewhere.get("why")).contains("Stroom stepped record index 7");
    }

    @Test
    void aHitTracesWhenSteppingOneRecordNumbersIt1() {
        // Seen on the live Stroom: verify_index failed every check but the one whose hit was EventId 1, as each
        // record stepped alone came out as EventId 1; the agent then told the user the tracer was off by one.
        final Function<Integer, String> output = id -> "<array xmlns=\"http://www.w3.org/2005/xpath-functions\">"
                                                       + "<map><number key=\"StreamId\">9</number><number "
                                                       + "key=\"EventId\">" + id + "</number><string "
                                                       + "key=\"@timestamp\">2026-09-28T22:01:55Z</string><map "
                                                       + "key=\"user\"><string key=\"name\">joe.bloggs</string></map>"
                                                       + "</map></array>";
        final Map<String, String> hit = new LinkedHashMap<>();
        hit.put("@timestamp", "2026-09-28T22:01:55.000Z");
        hit.put("user.name", "joe.bloggs");
        hit.put("source.ip", null);
        hit.put("StreamId", "9");
        hit.put("EventId", "826");
        final List<Long> records = new ArrayList<>();
        final IndexChecks.Stepper first = (stream, at) -> {
            records.add(at);
            return new Stepped(825L, output.apply(1));
        };
        final Map<String, Object> traced = IndexChecks.traced(hit, Map.of("field", "user.name", "value",
                "joe.bloggs"), first, "xsltFilter");
        final Map<String, Object> numbered = IndexChecks.traced(hit, Map.of("field", "user.name", "value",
                "joe.bloggs"), (stream, at) -> new Stepped(825L, output.apply(826)), "xsltFilter");

        assertThat(records).containsExactly(825L);
        assertThat(traced).containsExactly(Map.entry("traced", true), Map.entry("stream", 9L),
                Map.entry("event", 826L), Map.entry("stepped_event_id", "1"),
                Map.entry("matched_on", List.of("@timestamp", "user.name")));
        assertThat(numbered).isEqualTo(Map.of("traced", true, "stream", 9L, "event", 826L));
    }

    @Test
    void aHitMatchedOnAFieldOfSeveralNumbersIsTraced() {
        final String output = "<array xmlns=\"http://www.w3.org/2005/xpath-functions\"><map><number "
                              + "key=\"StreamId\">9</number><number key=\"EventId\">1</number><array key=\"ports\">"
                              + "<number>80</number><number>443</number></array></map></array>";
        final Map<String, Object> traced = IndexChecks.traced(Map.of("StreamId", "9", "EventId", "7", "ports",
                        "80, 443"), Map.of("field", "ports", "condition", "EQUALS", "value", "443"),
                (stream, at) -> new Stepped(6L, output), "xsltFilter");
        assertThat(traced.get("traced")).isEqualTo(true);
        assertThat(traced.get("matched_on")).isEqualTo(List.of("ports"));
    }

    @Test
    void hitsOfALongStreamAreTracedInOneSteppingSession() {
        // Seen in testing: verifying a long stream took over five minutes, 'Tracing the first hit' check after
        // check: each trace stepped in a session of its own, and Stroom captures the whole stream for every
        // session.
        steppingWorld.stepper = (stream, at) -> SteppingWorld.step(stream, 0, at.getRecordIndex(),
                "<array xmlns=\"http://www.w3.org/2005/xpath-functions\"><map><number key=\"StreamId\">6</number>"
                + "<number key=\"EventId\">" + (at.getRecordIndex() + 1) + "</number><string key=\"user.name\">"
                + "alice</string></map></array>");
        final List<Map<String, Object>> found = List.of(
                check("a", Map.of(), List.of(Map.of("StreamId", "6", "EventId", "9000"),
                        Map.of("StreamId", "6", "EventId", "40"))),
                check("b", Map.of("field", "user.name", "condition", "EQUALS", "value", "alice"),
                        List.of(Map.of("StreamId", "6", "EventId", "40"))),
                check("c", Map.of(), List.of(Map.of("StreamId", "6", "EventId", "7"))));
        final ToolCallContext context = steppingWorld.context();

        final List<String> untraced = checks.traceChecks(context, SteppingWorld.UUID, found, () -> false);

        // One session for the stream; each record once (the lowest EventId of each check's hits), in order.
        assertThat(steppingWorld.opened).hasSize(1);
        assertThat(steppingWorld.steps).containsExactly("REFRESH 0:6", "REFRESH 0:39");
        assertThat(untraced).isEmpty();
        assertThat(found).allSatisfy(check -> {
            assertThat(check.get("pass")).isEqualTo(true);
            assertThat(((Map<?, ?>) check.get("trace")).get("traced")).isEqualTo(true);
        });
        assertThat(steppingWorld.said.getLast()).isEqualTo("Tracing hits back to their records: record 39 of stream "
                                                           + "6 (2 of 2)");
        // Out of time: nothing more stepped, the check left untraced and said so.
        steppingWorld.steps.clear();
        final List<Map<String, Object>> one = List.of(check("a", Map.of(),
                List.of(Map.of("StreamId", "6", "EventId", "7"))));
        assertThat(checks.traceChecks(context, SteppingWorld.UUID, one, () -> true)).containsExactly("a");
        assertThat(steppingWorld.steps).isEmpty();
        assertThat(((Map<?, ?>) one.getFirst().get("trace")).get("traced")).isNull();
        assertThat(one.getFirst().get("pass")).isEqualTo(false);
    }

    @Test
    void documentsAreCountedNotTheRowsOfOnePage() {
        // Seen: 32,000 indexed events verified as 100, the first page of a search, and the index failed
        // verification.
        answer(probe -> counting(probe)
                ? found(List.of(Map.of("All", "all", "Count", "32000")), true)
                : found(rows(100), true));
        final Map<Long, Long> raw = new LinkedHashMap<>();
        raw.put(706L, null);
        raw.put(707L, null);
        events(raw);

        final Map<String, Object> result = checks.runTestSearches(steppingWorld.context(), dashboard(), List.of(706L,
                707L), 32000, List.of(), null, 0, List.of(), null);

        assertThat(result.get("passed")).isEqualTo(true);
        final Map<?, ?> first = (Map<?, ?>) ((List<?>) result.get("checks")).getFirst();
        assertThat(first.get("returned")).isEqualTo(32000L);
        assertThat((List<?>) first.get("sample")).hasSize(3);
    }

    @Test
    void verificationChecksOnlyTheSampleUnsortedAndALargeStreamByItsEnds() {
        // Asked for by the user: an index of millions of documents, a stream of 100,000. Checks of the whole index
        // answered for other streams' documents, sorted searches read every match, and the count read every
        // document, 7 times.
        answer(probe -> {
            if (counting(probe)) {
                final Map<String, String> terms = new LinkedHashMap<>();
                probe.expression().getChildren().stream().skip(1).forEach(item -> terms.put(
                        ((ExpressionTerm) item).getField() + " " + ((ExpressionTerm) item).getCondition(),
                        ((ExpressionTerm) item).getValue()));
                final long n;
                if (terms.containsKey("EventId GREATER_THAN")) {
                    n = 0;
                } else if ("1".equals(terms.get("EventId GREATER_THAN_OR_EQUAL_TO"))
                           || "99001".equals(terms.get("EventId GREATER_THAN_OR_EQUAL_TO"))) {
                    n = 1000;
                } else {
                    // Stream 8, counted whole.
                    n = 7;
                }
                return found(List.of(Map.of("All", "all", "Count", String.valueOf(n))), true);
            }
            return found(List.of(Map.of("StreamId", "6", "EventId", "1", "user.name", "alice")), true);
        });
        events(Map.of(6L, 100_000L, 8L, 7L));

        final Map<String, Object> result = checks.runTestSearches(steppingWorld.context(), dashboard(), List.of(6L,
                8L), 100_007, List.of(Map.of("field", "user.name", "value", "alice")), null, 0, List.of(), null);

        assertThat(result.get("passed")).isEqualTo(true);
        final List<?> parts = (List<?>) ((Map<?, ?>) ((List<?>) result.get("checks")).getFirst()).get("parts");
        assertThat(parts.stream().map(part -> List.of(((Map<?, ?>) part).get("streams"),
                String.valueOf(((Map<?, ?>) part).get("of")), ((Map<?, ?>) part).get("expected"))).toList())
                .containsExactly(List.of(List.of(8L), "null", 7L), List.of(List.of(6L), "events 1 to 1000", 1000L),
                        List.of(List.of(6L), "events 99001 to 100000", 1000L),
                        List.of(List.of(6L), "none past event 100000", 0L));
        // Every search of the sample's streams, and none sorted.
        final ExpressionOperator streams = IndexChecks.byStream(List.of(6L, 8L));
        assertThat(asked).allSatisfy(probe -> {
            assertThat(probe.columns()).allSatisfy(column -> assertThat(column.getSort()).isNull());
            final ExpressionItem firstChild = probe.expression().getChildren().getFirst();
            assertThat(probe.expression().equals(streams)
                       || ((ExpressionOperator) firstChild).getOp() == ExpressionOperator.Op.OR).isTrue();
        });
        assertThat(asked.getLast().expression()).isEqualTo(ExpressionOperator.builder().addOperator(streams)
                .addTerm(IndexChecks.term("user.name", ExpressionTerm.Condition.EQUALS, "alice")).build());
    }

    @Test
    void aCountStroomHadNotFinishedIsUnprovenNotMissingDocuments() {
        answer(probe -> counting(probe)
                ? found(List.of(Map.of("All", "all", "Count", "4000")), false)
                : found(List.of(), true));
        events(Map.of(6L, 9000L));

        final Map<String, Object> result = checks.runTestSearches(steppingWorld.context(), dashboard(), List.of(6L),
                9000, List.of(), null, 0, List.of(), null);

        final Map<?, ?> check = (Map<?, ?>) ((List<?>) result.get("checks")).getFirst();
        assertThat(result.get("passed")).isEqualTo(false);
        assertThat(check.get("returned")).isEqualTo(4000L);
        assertThat((String) check.get("unproven")).contains("not missing documents");
    }

    @Test
    void verificationOutOfTimeSaysWhatIsLeftAndTheNextCallRunsOnlyThat() {
        // Seen in testing: on a long stream verification outlasted the user's token, and the call failed part way.
        answer(probe -> {
            if (counting(probe)) {
                return found(List.of(Map.of("All", "all", "Count", "3")), true);
            }
            if (doing.getLast() != null && doing.getLast().contains("user.name")) {
                // The call's time runs out during this search.
                pauseMs(400);
            }
            return found(List.of(Map.of("StreamId", "6", "EventId", "1", "user.name", "alice")), true);
        });
        events(Map.of(6L, 3L));
        final List<Map<String, String>> exact = List.of(Map.of("field", "user.name", "value", "alice"),
                Map.of("field", "host.name", "value", "ws01"));

        final Map<String, Object> first = ToolDeadline.run(Duration.ofMillis(300), () -> checks.runTestSearches(
                steppingWorld.context(), dashboard(), List.of(6L), 3, exact, null, 0, List.of(), null));
        assertThat(first.get("passed")).isEqualTo(false);
        assertThat(first.get("unfinished")).isEqualTo(List.of("exact host.name=ws01"));
        assertThat((String) first.get("next")).contains("call verify_index again with the same arguments");
        doing.clear();
        final Map<String, Object> second = checks.runTestSearches(steppingWorld.context(), dashboard(), List.of(6L),
                3, exact, null, 0, List.of(), null);

        assertThat(second.get("passed")).isEqualTo(true);
        assertThat(second).doesNotContainKey("unfinished");
        // Only host.name searched again: the documents and user.name checks passed in the first call.
        assertThat(doing.stream().filter(said -> said != null && said.contains("Searching")).toList())
                .containsExactly("Searching web-v1: host.name = ws01");
        final List<Object> checked = new ArrayList<>();
        ((List<?>) second.get("checks")).forEach(check -> checked.add(((Map<?, ?>) check).get("checked")));
        assertThat(checked).containsExactly("earlier in this session", "earlier in this session", null);
    }

    @Test
    void waitingForTheIndexStopsWhenTheCountStopsMoving() {
        final AtomicInteger counted = new AtomicInteger();
        answer(probe -> {
            if (counting(probe)) {
                counted.incrementAndGet();
                return found(List.of(Map.of("All", "all", "Count", "2")), true);
            }
            return found(List.of(), true);
        });
        events(Map.of(6L, 3L));

        final Map<String, Object> result = checks.runTestSearches(steppingWorld.context(), dashboard(), List.of(6L),
                3, List.of(), null, 6, List.of(), null);

        // Counted twice (2, then 2 again), not seven times.
        assertThat(result.get("passed")).isEqualTo(false);
        assertThat(slept).hasSize(1);
        assertThat(counted.get()).isEqualTo(2);
    }

    @Test
    void anExactValueOfSeveralValuesSaysToSearchOne() {
        answer(probe -> {
            if (counting(probe)) {
                return found(List.of(Map.of("All", "all", "Count", "3")), true);
            }
            return found(doing.getLast() != null && doing.getLast().contains("tags")
                    ? List.of()
                    : List.of(Map.of("StreamId", "6", "EventId", "1")), true);
        });
        events(Map.of(6L, 3L));

        final Map<String, Object> result = checks.runTestSearches(steppingWorld.context(), dashboard(), List.of(6L),
                3, List.of(Map.of("field", "tags", "value", "retry, timeout")), null, 0, List.of(), null);

        final Map<?, ?> check = (Map<?, ?>) ((List<?>) result.get("checks")).get(1);
        assertThat(check.get("pass")).isEqualTo(false);
        assertThat((String) check.get("why")).contains("search one of them, e.g. 'retry'");
    }

    @Test
    void theDashboardIsLaidOutAsStroomsUiMakesIt() {
        final DashboardConfig config = IndexChecks.dashboardConfig(WEB, List.of("user.name", "StreamId"),
                "@timestamp", "2026-09-01T00:00:00.000Z");
        final List<ComponentConfig> components = config.getComponents();
        assertThat(components.stream().map(ComponentConfig::getType)).containsExactly("query", "table", "text");
        final QueryComponentSettings query = (QueryComponentSettings) components.get(0).getSettings();
        assertThat(query.getExpression().getChildren()).containsExactly(IndexChecks.term("@timestamp",
                ExpressionTerm.Condition.BETWEEN, "2026-09-01T00:00:00.000Z,day()+1d"));
        assertThat(query.getAutomate().isOpen()).isTrue();
        final TableComponentSettings table = (TableComponentSettings) components.get(1).getSettings();
        assertThat(table.getColumns().stream().map(Column::getName))
                .containsExactly("user.name", "@timestamp", "StreamId", "EventId");
        assertThat(table.getColumns().stream().map(Column::isVisible)).containsExactly(true, false, false, false);
        assertThat(table.getColumns().get(1).getSort().getDirection().name()).isEqualTo("DESCENDING");
        final TextComponentSettings text = (TextComponentSettings) components.get(2).getSettings();
        assertThat(text.getStreamIdField().getId()).isEqualTo(table.getColumns().get(2).getId());
        assertThat(config.getLayoutConstraints()).isNotNull();
        assertThat(IndexChecks.windowStart(List.of("2026-10-11T08:00:00.000Z", "not a time")))
                .isEqualTo("2026-10-04T00:00:00.000Z");
    }

    private Dashboard dashboard() {
        return IndexChecks.Dashboard.of("d", "V", IndexChecks.dashboardConfig(WEB, List.of("user.name"),
                "@timestamp", null));
    }

    private void answer(final Function<Probe, Found> answer) {
        Mockito.when(indexSearches.search(any(), any(), anyInt(), any())).thenAnswer(inv -> {
            final Probe probe = inv.getArgument(1);
            asked.add(probe);
            doing.add(inv.getArgument(3));
            return answer.apply(probe);
        });
        Mockito.when(indexSearches.count(any(), any(), any())).thenCallRealMethod();
    }

    private void events(final Map<Long, Long> sizes) {
        Mockito.when(streamReader.fetch(anyLong(), anyLong(), Mockito.nullable(String.class))).thenAnswer(inv -> {
            final Long size = sizes.get((Long) inv.getArgument(0));
            return new Fetched("ACME", "Events", "", size != null, size, List.of(), List.of());
        });
    }

    private static boolean counting(final Probe probe) {
        return probe.columns().stream().map(Column::getName).toList().equals(List.of("All", "Count"));
    }

    private static Found found(final List<Map<String, String>> rows, final boolean complete) {
        return new Found(rows, List.of(), complete);
    }

    private static List<Map<String, String>> rows(final int n) {
        final List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            rows.add(Map.of("StreamId", "706", "EventId", String.valueOf(i)));
        }
        return rows;
    }

    private static Map<String, Object> check(final String name,
                                             final Map<String, String> spec,
                                             final List<Map<String, String>> rows) {
        final Map<String, Object> check = new LinkedHashMap<>();
        check.put("check", name);
        check.put("pass", true);
        check.put("_spec", spec);
        check.put("_rows", rows);
        return check;
    }

    private static void pauseMs(final long ms) {
        try {
            Thread.sleep(ms);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
