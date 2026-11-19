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

package stroom.ai.impl.tools.catalogue;

import stroom.ai.impl.tools.generation.Condition;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.tools.validation.HeldSchemasWorld;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The catalogue's checks that need no records.
class TestCatalogueValidation {

    private static final EventSchema SCHEMA = EventSchema.parse(HeldSchemasWorld.XSD_4_1_0);

    @Test
    void aGoodCatalogueHasNoProblems() {
        final Catalogue catalogue = catalogue(null,
                type("Update-Password", "Password change", way(equalsTo("event.eventType", "Password change"))),
                type("Authenticate-Logon", "Logon", way(equalsTo("event.eventType", "Logon")),
                        way(startsWith("message", "Login:"))));

        assertThat(CatalogueValidation.problems(catalogue, SCHEMA)).isEmpty();
    }

    @Test
    void typeIdsAreVerbObjectAndUniqueAndTitlesUnique() {
        final Catalogue catalogue = catalogue(null,
                type("Update-Password", "Password change", way(equalsTo("a", "1"))),
                type("Update-Password", "password change", way(equalsTo("a", "2"))),
                type("Logon", "Logon", way(equalsTo("a", "3"))));

        assertThat(said(catalogue)).containsExactly(
                "Update-Password: type_id 'Update-Password' is given to two event types: each has its own",
                "Update-Password: title 'password change' is Update-Password's too: each event type has its own",
                "Logon: type_id 'Logon' isn't Verb-Object: a verb and what it acts on, each starting with a capital "
                + "letter, letters and digits only, joined by '-' (Update-Password, Process-SOAPRequest)");
    }

    @Test
    void onlyASoleEventTypeIsRecognisedByEveryRecord() {
        assertThat(said(catalogue(null, type("Process-Request", "Request")))).isEmpty();
        assertThat(said(catalogue(null, type("Process-Request", "Request", new Way(List.of(), List.of())))))
                .isEmpty();

        assertThat(said(catalogue(null, type("Process-Request", "Request"),
                type("Process-Response", "Response", new Way(List.of(), List.of()), way(equalsTo("a", "1"))))))
                .containsExactly("Process-Request: recognised_by is empty: only an event type that is its source's "
                                 + "only one is recognised by every record; give the conditions its records meet",
                        "Process-Response#0: this way of recognising it has no conditions: only a sole event type is "
                        + "recognised by every record");
    }

    @ParameterizedTest
    @ValueSource(strings = {"event.log_type", "event/log_type", "event//log_type", "event/Log/log_type",
            "event>log_type", "system"})
    void noConditionIsOnTheSourceTypeFieldInAnyOfItsSpellings(final String field) {
        final Catalogue catalogue = catalogue("system + event>log_type | event>xml:@type",
                type("Process-Request", "Request", way(equalsTo(field, "Integration"), equalsTo("a", "1"))),
                type("Process-Response", "Response", way(equalsTo("a", "2"))));

        assertThat(said(catalogue)).singleElement().asString().startsWith("Process-Request#0: condition on " + field
                                                                          + ", the build's source type field");
        assertThat(CatalogueValidation.onSourceTypeField("system + event>log_type | event>xml:@type",
                "event//@type")).isTrue();
        assertThat(CatalogueValidation.onSourceTypeField("event>log_type", "event.level")).isFalse();
        assertThat(CatalogueValidation.onSourceTypeField("event>log_type", "log_type")).isFalse();
        assertThat(CatalogueValidation.onSourceTypeField(null, "event.log_type")).isFalse();
    }

    @Test
    void captureNamesAreOneARegexGroupAndUniqueInTheBuild() {
        final Catalogue catalogue = catalogue(null,
                type("Process-Request", "Request", new Way(List.of(matches("message", "^Request: (\\w+) (\\S+)")),
                        List.of("method", "path"))),
                type("Process-Response", "Response", new Way(List.of(matches("message", "^Response: (\\d+)")),
                        List.of("status", "path"))),
                type("Process-Fault", "Fault", new Way(List.of(equalsTo("level", "ERROR")), List.of("code"))));

        assertThat(said(catalogue)).containsExactly(
                "Process-Response#0: names has 2 name(s) for the regex's 1 capture group(s): one name a group",
                "Process-Response#0: name 'path' is Process-Request's too: capture names are unique in the build",
                "Process-Fault#0: names name a matches condition's capture groups: this way has 0 matches "
                + "conditions, not one");
    }

    @Test
    void regexesAreXPathsAndShort() {
        final Catalogue catalogue = catalogue(null,
                type("Process-Request", "Request", way(matches("message", "^(?<m>\\w+)"))),
                type("Process-Response", "Response", way(matches("message", "x".repeat(501)))));

        final List<String> said = said(catalogue);
        assertThat(said.getFirst()).startsWith("Process-Request#0: the regex of message XPath regular expressions "
                                               + "have no named groups and no lookaround");
        assertThat(said.get(1)).startsWith("Process-Response#0: the regex of message is 501 characters long, at "
                                           + "most 500");
    }

    @Test
    void droppingIsTheUsersWordAndTheSeedIsNeverConfirmedButByTheUser() {
        final EventType noise = new EventType("Process-Heartbeat", "Heartbeat", null, null, null, null, null, null,
                null, List.of(way(equalsTo("a", "hb"))), true, EventType.AGENT_KNOWLEDGE, "high", null,
                EventType.PROPOSED);
        final EventType seed = new EventType("Update-Password", "Password", null, "Update", null, null, null, null,
                null, List.of(way(equalsTo("a", "pw"))), false, EventType.DRAFT, "high", null, EventType.CONFIRMED);

        assertThat(said(catalogue(null, noise, seed))).containsExactly(
                "Process-Heartbeat: drop is the user's word only: an event type is dropped when the user says so in "
                + "the form (basis user_confirmed)",
                "Update-Password: basis draft is the draft's seed, never confirmed but by the user in the form");
        assertThat(said(catalogue(null, noise.droppedByUser(), seed.confirmedByUser()))).isEmpty();
    }

    @Test
    void whatItMeansIsCheckedAgainstTheSchema() {
        final EventType alert = new EventType("Alert-Intrusion", "Intrusion", null, "Alert", "Modify", "true", null,
                null, null, List.of(way(equalsTo("a", "1"))), false, EventType.AGENT_KNOWLEDGE, "high", null,
                EventType.PROPOSED);
        final EventType authorise = new EventType("Authorise-Role", "Role", null, "Authorise", "Delete", null, null,
                null, "Mailbox", List.of(way(equalsTo("a", "2"))), false, EventType.AGENT_KNOWLEDGE, "high", null,
                EventType.PROPOSED);
        final EventType nonsense = new EventType("Frobnicate-Thing", "Thing", null, "Frobnicate", null, null, null,
                null, null, List.of(way(equalsTo("a", "3"))), false, EventType.AGENT_KNOWLEDGE, "high", null,
                EventType.PROPOSED);

        final List<String> said = said(catalogue(null, alert, authorise, nonsense));

        assertThat(said).anySatisfy(problem -> assertThat(problem).isEqualTo("Alert-Intrusion: event type "
                                                                             + "Alert-Intrusion: EventDetail/Alert has "
                                                                             + "no Outcome, so leave success out"));
        assertThat(said).anySatisfy(problem -> assertThat(problem).contains("Authorise-Role: event type "
                                                                            + "Authorise-Role: EventDetail/Authorise/"
                                                                            + "Action takes only"));
        assertThat(said).anySatisfy(problem -> assertThat(problem).contains("affected_as 'Mailbox' isn't under "
                                                                            + "Authorise"));
        assertThat(said).anySatisfy(problem -> assertThat(problem).startsWith("Frobnicate-Thing: event type "
                                                                              + "Frobnicate-Thing: 'Frobnicate' is "
                                                                              + "not an EventDetail action element"));
        // No schema, no meaning checks.
        assertThat(CatalogueValidation.problems(catalogue(null, alert, authorise, nonsense), null)).isEmpty();
    }

    private static List<String> said(final Catalogue catalogue) {
        final List<String> out = new ArrayList<>();
        CatalogueValidation.problems(catalogue, SCHEMA).forEach(problem -> out.add(problem.typeId()
                                                                                  + (problem.way() >= 0
                ? "#" + problem.way()
                : "") + ": " + problem.problem()));
        return out;
    }

    private static Catalogue catalogue(final String sourceTypeField, final EventType... types) {
        return new Catalogue(List.of(types), null, new Catalogue.Scope(sourceTypeField, List.of()));
    }

    private static EventType type(final String typeId, final String title, final Way... ways) {
        return new EventType(typeId, title, null, "Process", null, null, null, null, null, List.of(ways), false,
                EventType.AGENT_KNOWLEDGE, "high", null, EventType.PROPOSED);
    }

    private static Way way(final Condition... conditions) {
        return new Way(List.of(conditions), List.of());
    }

    private static Condition equalsTo(final String field, final String value) {
        return Condition.fromJson(Map.of("field", field, "equals", value));
    }

    private static Condition startsWith(final String field, final String value) {
        return Condition.fromJson(Map.of("field", field, "starts_with", value));
    }

    private static Condition matches(final String field, final String regex) {
        return Condition.fromJson(Map.of("field", field, "matches", regex));
    }
}
