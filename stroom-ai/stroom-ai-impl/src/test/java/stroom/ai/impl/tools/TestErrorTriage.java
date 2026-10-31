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

import stroom.ai.impl.tools.ErrorTriage.Marker;
import stroom.util.json.JsonUtil;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.ElementId;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestErrorTriage {

    private static final ErrorTriage RULES = ErrorTriage.bundled();

    @ParameterizedTest
    @CsvSource({
            "ERROR, schemaFilter, false, blocking",
            "FATAL, decorationFilter, false, blocking",
            "ERROR, translationFilter, true, blocking",
            "ERROR, decorationFilter, false, review",
            "WARNING, translationFilter, true, review",
            "WARNING, decorationFilter, false, benign",
            "INFO, decorationFilter, false, benign"})
    void theDefaultRulesClassifyBySeverityElementAndOwnership(final String severity,
                                                              final String element,
                                                              final boolean own,
                                                              final String expected) {
        assertThat(RULES.classify(severity, element, own, "x").classification()).isEqualTo(expected);
    }

    @Test
    void aDataSplittersLostRecordsSayHowToKeepThem() {
        // Seen in testing: CSV lines of about 20,000 characters lost, and the agent, told the DSParser had no
        // buffer property, couldn't go on. The parser is inherited from the template: still blocking.
        final ErrorTriage.Classified buffer = RULES.classify("ERROR", "dsParser", false,
                "Top level expression 'split[1] : <split delimiter=\"\\n\">' matched too much content and "
                + "exhausted the buffer. Attempting recovery...");
        assertThat(buffer.classification()).isEqualTo(ErrorTriage.BLOCKING);
        assertThat(buffer.reason()).contains("bufferSize").contains("buffer_size=").contains("not a property of "
                                                                                           + "the DSParser");

        final ErrorTriage.Classified unmatched = RULES.classify("ERROR", "dsParser", false,
                "Expressions failed to match all of the content provided by group: split[0]/group[0] : "
                + "<group id=\"fields\"> unmatched content: [x]");
        assertThat(unmatched.classification()).isEqualTo(ErrorTriage.BLOCKING);
        assertThat(unmatched.reason()).contains("spaced_values").contains("catch_all").contains("stroom_check");
    }

    @Test
    void unmatchedRecordsWithEveryTestedValueEmptyMayBePiecesOfARecord() {
        // Seen in testing: CSV values holding line breaks split each record into pieces, logged with every tested
        // value empty, and the agent nearly added a rule for them.
        for (final String message : List.of("Log - No event mapping matched record 12 (x-action=)",
                "No event mapping matched record 5 (action= | type=)",
                "Log - No event mapping matched record  (x-action=)")) {
            final ErrorTriage.Classified piece = RULES.classify("WARNING", "translationFilter", true, message);
            assertThat(piece.classification()).as(message).isEqualTo(ErrorTriage.REVIEW);
            assertThat(piece.reason()).as(message).contains("quoted_newlines=true").contains("whole records");
        }
        // A value in any tested field: a kind of record no rule takes yet, as before.
        for (final String message : List.of("No event mapping matched record 7 (x-action=useralert)",
                "No event mapping matched record 5 (action= | type=login)")) {
            assertThat(RULES.classify("WARNING", "translationFilter", true, message).reason()).as(message)
                    .doesNotContain("quoted_newlines");
        }
    }

    @Test
    void environmentRulesComeFirst() {
        final ErrorTriage rules = ErrorTriage.withEnvironmentRules(List.of(
                "{element: decorationFilter, message: lookup, class: benign, reason: known miss}"));

        assertThat(rules.classify("ERROR", "decorationFilter", false, "Failed lookup for user x"))
                .isEqualTo(new ErrorTriage.Classified("benign", "known miss"));
        // The bundled rules still apply after them.
        assertThat(rules.classify("ERROR", "schemaFilter", false, "x").classification()).isEqualTo("blocking");
    }

    @Test
    void anEnvironmentRuleAsJsonIsReadToo() {
        final ErrorTriage rules = ErrorTriage.withEnvironmentRules(List.of(
                "{\"severity\": [\"WARN\"], \"class\": \"blocking\", \"reason\": \"no warnings here\"}"));

        assertThat(rules.classify("WARNING", "anything", false, "x").reason()).isEqualTo("no warnings here");
    }

    @Test
    void anEnvironmentRuleWithoutAValidClassIsRefused() {
        assertThatThrownBy(() -> ErrorTriage.withEnvironmentRules(List.of("{class: fine, reason: x}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("class must be one of");
        assertThatThrownBy(() -> ErrorTriage.withEnvironmentRules(List.of("{class: benign}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs a reason");
    }

    @Test
    void messagesThatDifferOnlyInValuesGroupTogether() {
        assertThat(ErrorTriage.normalise("Failed to parse date: \"abc\" at index 0"))
                .isEqualTo(ErrorTriage.normalise("Failed to parse date: \"xyz\" at index 3"));
    }

    @Test
    void aCascadeSeenOnALiveInstanceIsBlockingWithTheCauseUnderReview() {
        // A bad date: the pipeline's own XSLT warns, then schema validation fails twice.
        final List<Marker> markers = List.of(
                marker("WARNING", "translationFilter", "FormatDate - Failed to parse date: \"not-a-date\"", "r1"),
                marker("ERROR", "schemaFilter", "Value '' is not facet-valid with respect to pattern", "r1"),
                marker("ERROR", "schemaFilter", "The value '' of element 'TimeCreated' is not valid.", "r1"));

        final Map<String, Object> result = RULES.triage(markers, Set.of("translationFilter"), 3, 3, List.of());

        assertThat(result.get("verdict")).isEqualTo("blocking");
        assertThat(result.get("groups_by_class")).isEqualTo(Map.of("blocking", 2, "review", 1, "benign", 0));
        assertThat(groups(result).getLast().get("element")).isEqualTo("translationFilter");
    }

    @Test
    void aBenignGroupOnEveryRecordIsRaisedToReview() {
        final List<Marker> markers = Stream.of("a", "b", "c")
                .map(u -> marker("WARNING", "decorationFilter", "No user found for \"" + u + "\"", "r" + u))
                .toList();

        final Map<String, Object> group = groups(RULES.triage(markers, Set.of(), 3, 3, List.of())).getFirst();

        assertThat(List.of(group.get("class"), group.get("count"), group.get("records_affected")))
                .containsExactly("review", 3, 3);
        assertThat(groups(RULES.triage(markers.subList(0, 2), Set.of(), 3, 3, List.of())).getFirst().get("class"))
                .isEqualTo("benign");
    }

    @Test
    void aStoredErrorWithoutARealLocationHasNone() {
        final StoredError stored = new StoredError(Severity.ERROR, DefaultLocation.of(-1, -1),
                new ElementId("schemaFilter"), "bad");

        assertThat(ErrorTriage.fromStoredError(stored))
                .isEqualTo(new Marker("ERROR", "schemaFilter", "bad", null, null, null));
    }

    @Test
    void steppingFatalErrorsAreBlocking() {
        // Stepping indicators say FATAL_ERROR (an XSLT that doesn't compile): the same as FATAL.
        final StoredError stored = new StoredError(Severity.FATAL_ERROR, null, new ElementId("translationFilter"),
                "XsltPool - Variable x has not been declared");

        final Marker marker = ErrorTriage.fromStoredError(stored);

        assertThat(marker.severity()).isEqualTo("FATAL");
        assertThat(RULES.triage(List.of(marker), Set.of("translationFilter")).get("verdict")).isEqualTo("blocking");
    }

    @Test
    void aCallToAFunctionNobodyHasPointsBackToTheMapping() {
        // Seen (Gemma 4 31B): a mapping's fields given as extract(...) calls; each step said only "Fatal error".
        final StoredError stored = new StoredError(Severity.FATAL_ERROR, null, new ElementId("translationFilter"),
                "XsltPool - Cannot find a 2-argument function named "
                + "Q{http://www.w3.org/2005/xpath-functions}extract()");

        final List<Map<String, Object>> groups = groups(RULES.triage(
                List.of(ErrorTriage.fromStoredError(stored)), Set.of("translationFilter")));

        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().get("class")).isEqualTo("blocking");
        assertThat((String) groups.getFirst().get("reason")).contains("the mapping's extract list");
    }

    @Test
    void aRequiredElementLeftOutIsSaidSoNotTheElementTheSchemaNames() {
        // Seen: "starting with element '{Data}'. One of '{Command}' is expected" read as Data put in the wrong
        // place, two rounds of stepping before Command was given a default.
        final ErrorTriage.Classified classified = RULES.classify("ERROR", "schemaFilter", false,
                "Invalid content was found starting with element '{Data}'. One of '{Command}' is expected.");

        assertThat(classified.classification()).isEqualTo("blocking");
        assertThat(classified.reason()).startsWith("Output fails schema validation: an element the schema requires")
                .contains("was left out", "give the entry a default", "rather than moving the element named");
        // Any other schema error as before.
        assertThat(RULES.classify("ERROR", "schemaFilter", false, "cvc-type.3.1.3: The value 'x' is not valid")
                .reason()).isEqualTo("Output fails schema validation");
    }

    @Test
    void cleanWhenThereAreNoMarkers() {
        assertThat(RULES.triage(List.of(), Set.of()).get("verdict")).isEqualTo("clean");
    }

    @Test
    void anElasticsearchBulkFailureIsSplitIntoTheDocumentsItRejected() {
        // As Stroom reported it: one FATAL message holding the whole bulk response; one of three documents rejected.
        final Map<String, Object> response = Map.of("errors", true, "took", 600, "items", List.of(
                Map.of("create", Map.of("_id", "a", "status", 201, "result", "created")),
                Map.of("create", Map.of("_id", "b", "status", 400, "error", Map.of(
                        "type", "document_parsing_exception",
                        "reason", "[1:117] object mapping for [user] tried to parse field [user] as object, but "
                                  + "found a concrete value"))),
                Map.of("create", Map.of("_id", "c", "status", 201, "result", "created"))));

        final Map<String, Object> result = RULES.triage(List.of(ErrorTriage.marker("FATAL", "elasticIndexingFilter",
                "Bulk indexing request failed: BulkResponse: " + JsonUtil.writeValueAsString(response, false),
                null)), Set.of("elasticIndexingFilter"));

        final List<Map<String, Object>> groups = groups(result);
        assertThat(result.get("verdict")).isEqualTo("blocking");
        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().get("count")).isEqualTo(1);
        assertThat(firstMessage(groups.getFirst())).startsWith(
                "Elasticsearch rejected document 2 of 3 in a bulk request (document_parsing_exception): ");
        assertThat((String) groups.getFirst().get("reason"))
                .startsWith("A field is an object in some records and a plain value in others");
    }

    @Test
    void anErrorTheUserAcceptedIsBenignWithTheirReasonAndMatchesItsKind() {
        final List<AcceptedErrors.Entry> accepted = List.of(AcceptedErrors.entry("decorationFilter",
                "No HR record for user svc-backup", "Service accounts have no HR record", "2026-10-04",
                "No HR record for user svc-*"));
        final List<Marker> markers = List.of(
                // The same kind, another value.
                ErrorTriage.marker("ERROR", "decorationFilter", "Log - No HR record for user svc-deploy", null),
                // Another kind.
                ErrorTriage.marker("ERROR", "decorationFilter", "Lookup failed: no map named HR", null));

        final List<Map<String, Object>> groups = groups(RULES.triage(markers, Set.of(), null, 3, accepted));

        final Map<String, Object> known = byMessage(groups, "Log - No HR record for user svc-deploy");
        assertThat(known.get("class")).isEqualTo("benign");
        assertThat(known.get("accepted")).isEqualTo(true);
        assertThat((String) known.get("reason")).contains("Service accounts have no HR record");
        final Map<String, Object> other = byMessage(groups, "Lookup failed: no map named HR");
        assertThat(other.get("class")).isEqualTo("review");
        assertThat(other).doesNotContainKey("accepted");

        // Without matches, the example is the kind: another user name is another error.
        final List<AcceptedErrors.Entry> exact = List.of(AcceptedErrors.entry("decorationFilter",
                "No HR record for user svc-backup", "x", "2026-10-04", null));
        assertThat(groups(RULES.triage(List.of(ErrorTriage.marker("ERROR", "decorationFilter",
                "No HR record for user svc-deploy", null)), Set.of(), null, 3, exact)).getFirst().get("class"))
                .isEqualTo("review");
    }

    @ParameterizedTest
    @MethodSource("refusals")
    void elasticsearchRefusingTheAccountNamesThePrivilegesAndTheIndexNameFirst(final Map<String, Object> error,
                                                                               final String says) {
        // Seen in testing: a 403 (the index name was wrong), called "rejected documents", and the next run refused
        // alike.
        final Map<String, Object> response = Map.of("errors", true, "items", List.of(Map.of("create", Map.of(
                "_index", "ecs-acme-v1", "status", 403, "error", error))));

        final Map<String, Object> result = RULES.triage(List.of(ErrorTriage.marker("FATAL", "elasticIndexingFilter",
                "Bulk indexing request failed: BulkResponse: " + JsonUtil.writeValueAsString(response, false),
                null)), Set.of("elasticIndexingFilter"));

        final List<Map<String, Object>> groups = groups(result);
        assertThat(result.get("verdict")).isEqualTo("blocking");
        assertThat(groups).hasSize(1);
        assertThat((String) groups.getFirst().get("reason"))
                .contains(says)
                .contains("Check the index name")
                .doesNotContain("rejected these documents");
    }

    static Stream<Arguments> refusals() {
        return Stream.of(
                Arguments.of(Map.of("type", "security_exception", "reason",
                                "action [indices:admin/auto_create] is unauthorized for user [stroom] with effective "
                                + "roles [stroom_writer] on indices [ecs-acme-v1], this action is granted by the "
                                + "index privileges [auto_configure,create_index,manage,all]"),
                        "grant the account the index privilege auto_configure"),
                Arguments.of(Map.of("type", "security_exception", "reason",
                                "action [indices:data/write/bulk[s]] is unauthorized for user [stroom] on indices "
                                + "[ecs-acme-v1], this action is granted by the index privileges "
                                + "[create_doc,create,index,write,all]"),
                        "grant the account create_doc (a data stream) or index (an index)"),
                Arguments.of(Map.of("type", "index_not_found_exception",
                        "reason", "no such index [ecs-acme-v1]"), "The index doesn't exist"));
    }

    private static Marker marker(final String severity,
                                 final String element,
                                 final String message,
                                 final String record) {
        return ErrorTriage.marker(severity, element, message, null).withRecord(record);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> groups(final Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("groups");
    }

    @SuppressWarnings("unchecked")
    private static String firstMessage(final Map<String, Object> group) {
        return (String) ((List<Map<String, Object>>) group.get("examples")).getFirst().get("message");
    }

    private static Map<String, Object> byMessage(final List<Map<String, Object>> groups, final String message) {
        return groups.stream()
                .filter(group -> message.equals(firstMessage(group)))
                .findFirst()
                .orElseThrow();
    }
}
