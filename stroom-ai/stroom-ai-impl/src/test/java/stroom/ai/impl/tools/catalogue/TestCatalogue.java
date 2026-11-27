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

import stroom.ai.impl.tools.generation.MappingValidationException;
import stroom.ai.impl.tools.sample.ToolJson;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The catalogue's JSON (the source notes' `event_types` block, as the design's contract writes it): read laxly
/// with every error collected, written back key for key, and the user's confirmations.
class TestCatalogue {

    /// The contract's example, as the design writes it.
    static final String CONTRACT = """
            {"event_types": [
              {"type_id": "Update-Password", "title": "Password change",
               "description": "An operator changed another operator's password",
               "event_detail": "Update", "action": "Modify", "success": "true",
               "actor": "event.userid", "affected": "event.target", "affected_as": "User",
               "recognised_by": [
                 {"when": [{"field": "event.logger_name", "equals": "com.pega.SecurityEventLogger"},
                           {"field": "event.eventType", "equals": "Password change"}],
                  "names": []}],
               "drop": false, "basis": "agent_knowledge", "confidence": "high", "one_type_because": null,
               "status": "proposed"}],
             "catch_all": {"status": "proposed"},
             "scope": {"source_type_field": "event>log_type", "source_types": ["Integration"]}}""";

    @Test
    void theContractsJsonIsReadAndWrittenBackKeyForKey() {
        final Map<String, Object> json = ToolJson.loads(CONTRACT) instanceof final Map<?, ?> map
                ? cast(map)
                : Map.of();

        final Catalogue catalogue = Catalogue.fromJson(json);

        final EventType type = catalogue.eventType("Update-Password");
        assertThat(type.title()).isEqualTo("Password change");
        assertThat(type.recognisedBy()).hasSize(1);
        assertThat(type.recognisedBy().getFirst().when()).extracting(condition -> condition.field())
                .containsExactly("event.logger_name", "event.eventType");
        assertThat(catalogue.catchAll().confirmed()).isFalse();
        assertThat(catalogue.scope().sourceTypeField()).isEqualTo("event>log_type");
        assertThat(ToolJson.dumps(catalogue.toJson())).isEqualTo(ToolJson.dumps(json));
    }

    @Test
    void aDoubtIsOptionalKeptThroughConfirmationAndWrittenOnlyWhenGiven() {
        // Decided by the user 2026-11-20: a medium or low event type says why it is doubtful where the agent said.
        final Catalogue.Read read = Catalogue.parse(Map.of("event_types", List.of(
                Map.of("type_id", "Authenticate-Logon", "confidence", "low", "doubt", "  only seen in the docs  "),
                Map.of("type_id", "Authenticate-Logoff", "doubt", " "))));

        assertThat(read.errors()).isEmpty();
        final EventType doubted = read.catalogue().eventType("Authenticate-Logon");
        assertThat(doubted.doubt()).isEqualTo("only seen in the docs");
        assertThat(doubted.toJson()).containsEntry("doubt", "only seen in the docs");
        assertThat(doubted.confirmedByUser().doubt()).isEqualTo("only seen in the docs");
        assertThat(doubted.withTypeId("Authenticate-Login").doubt()).isEqualTo("only seen in the docs");
        // Blank is none, and none is left out of the JSON (the contract's keys as they were).
        assertThat(read.catalogue().eventType("Authenticate-Logoff").doubt()).isNull();
        assertThat(read.catalogue().eventType("Authenticate-Logoff").toJson()).doesNotContainKey("doubt");
    }

    @Test
    void aProposalIsReadLaxlyWithDefaultsAndEveryErrorCollected() {
        final Catalogue.Read read = Catalogue.parse(Map.of("event_types", List.of(
                Map.of("type_id", "Authenticate-Logon", "drop", "no", "success", true,
                        "recognised_by", List.of(Map.of("when", List.of(Map.of("field", "action",
                                "starts_with", "log"))))))));

        assertThat(read.errors()).isEmpty();
        final EventType type = read.catalogue().eventTypes().getFirst();
        assertThat(type.drop()).isFalse();
        assertThat(type.success()).isEqualTo("true");
        assertThat(type.basis()).isEqualTo(EventType.AGENT_KNOWLEDGE);
        assertThat(type.confidence()).isEqualTo("medium");
        assertThat(type.status()).isEqualTo(EventType.PROPOSED);
        assertThat(read.catalogue().catchAll()).isEqualTo(Catalogue.CatchAll.PROPOSED);
        assertThat(read.catalogue().scope()).isEqualTo(Catalogue.Scope.NONE);

        final Catalogue.Read bad = Catalogue.parse(Map.of("event_types", List.of(
                Map.of("type_id", "A-B", "basis", "guess", "confidence", "certain"),
                Map.of("type_id", "C-D", "recognised_by", List.of(Map.of("when", List.of(Map.of("field", "x",
                        "equals", "1", "contains", "2"))))))));

        assertThat(bad.catalogue()).isNull();
        assertThat(bad.errors()).hasSize(3);
        assertThatThrownBy(() -> Catalogue.fromJson(Map.of("event_types", "none")))
                .isInstanceOf(MappingValidationException.class);
    }

    @Test
    void theCatchAllCarriesTheUsersAgreementForUncataloguedRecords() {
        final Catalogue.Read read = Catalogue.parse(Map.of("event_types", List.of(), "catch_all",
                Map.of("status", "confirmed", "allow_unknown", "Kept as Unknown: the records are rare")));

        assertThat(read.errors()).isEmpty();
        assertThat(read.catalogue().catchAll().confirmed()).isTrue();
        assertThat(read.catalogue().catchAll().allowUnknown()).isEqualTo("Kept as Unknown: the records are rare");
        assertThat(read.catalogue().toJson().get("catch_all")).isEqualTo(Map.of("status", "confirmed",
                "allow_unknown", "Kept as Unknown: the records are rare"));

        final Catalogue agreed = new Catalogue(List.of(), null, null).withCatchAllConfirmedByUser("The user's reason");
        assertThat(agreed.catchAll()).isEqualTo(new Catalogue.CatchAll(EventType.CONFIRMED, "The user's reason",
                null));
        // Without a reason given, none is written.
        assertThat(Catalogue.CatchAll.PROPOSED.toJson()).isEqualTo(Map.of("status", "proposed"));
    }

    @Test
    void theUsersConfirmationMakesTheSeedTheUsersAndKeepsOtherBases() {
        final EventType seed = type("Update-Password", EventType.DRAFT);
        final EventType documented = type("Authenticate-Logon", EventType.DOCUMENTATION);
        final Catalogue catalogue = new Catalogue(List.of(seed, documented), null, null);

        final Catalogue confirmed = catalogue.confirmedByUser(Set.of("Update-Password", "Authenticate-Logon"))
                .withCatchAllConfirmedByUser();

        assertThat(confirmed.eventType("Update-Password").basis()).isEqualTo(EventType.USER_CONFIRMED);
        assertThat(confirmed.eventType("Update-Password").confirmed()).isTrue();
        assertThat(confirmed.eventType("Authenticate-Logon").basis()).isEqualTo(EventType.DOCUMENTATION);
        assertThat(confirmed.catchAll().confirmed()).isTrue();
        assertThat(catalogue.confirmedByUser(Set.of()).eventType("Update-Password").confirmed()).isFalse();
        final EventType dropped = seed.droppedByUser();
        assertThat(List.of(dropped.drop(), dropped.basis(), dropped.status())).containsExactly(true,
                EventType.USER_CONFIRMED, EventType.CONFIRMED);
    }

    static EventType type(final String typeId, final String basis) {
        return new EventType(typeId, typeId, null, "Update", null, null, null, null, null, List.of(), false, basis,
                "high", null, EventType.PROPOSED);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(final Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}
