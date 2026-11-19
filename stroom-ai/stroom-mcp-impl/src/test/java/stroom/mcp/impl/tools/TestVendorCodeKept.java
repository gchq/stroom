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

import stroom.ai.impl.tools.catalogue.Catalogue;
import stroom.ai.impl.tools.catalogue.EventType;
import stroom.ai.impl.tools.catalogue.Way;
import stroom.ai.impl.tools.generation.Condition;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The confirmed event types whose rules no longer write the vendor's code as Data: seen in eval cases 67 and 70 on
/// Haiku, mappings saved whose rules had lost it.
class TestVendorCodeKept {

    private static final List<Object> RECORDS = List.of(
            // As the sample check reads JSON records: nested keys dotted.
            Map.of("event.code", "4624", "event.kind", "logon", "user", "alice"),
            Map.of("event.code", "4634", "event.kind", "logoff", "user", "alice"),
            Map.of("event.code", "9999", "event.kind", "other", "user", "bob"));

    private static final String KEPT = """
            [{"name": "Authenticate-Logon", "when": [{"field": "event.code", "equals": "4624"}],
              "fields": [{"path": "EventDetail/TypeId", "value": "Authenticate-Logon"},
                         {"path": "EventDetail/Authenticate/Action", "value": "Logon"},
                         {"path": "EventDetail/Authenticate/Data", "data_name": "code", "field": "event.code"}]},
             {"name": "uncatalogued", "catch_all": true,
              "fields": [{"path": "EventDetail/TypeId", "value": "Unknown"},
                         {"path": "EventDetail/Unknown/Data", "data_name": "user", "field": "user"}]}]""";

    @Test
    void aRuleThatKeepsTheVendorsCodeIsNotWarnedOf() {
        assertThat(VendorCodeKept.missing(mapping(KEPT), RECORDS, catalogue(logon()), null)).isEmpty();
    }

    @Test
    void aRuleThatLostTheVendorsCodeIsWarnedOfNamingTheEventTypeAndField() {
        final TranslationMapping lost = mapping("""
                [{"name": "Authenticate-Logon", "when": [{"field": "event.code", "equals": "4624"}],
                  "fields": [{"path": "EventDetail/TypeId", "value": "Authenticate-Logon"},
                             {"path": "EventDetail/Authenticate/Action", "value": "Logon"},
                             {"path": "EventDetail/Authenticate/Data", "data_name": "user", "field": "user"}]}]""");

        assertThat(VendorCodeKept.missing(lost, RECORDS, catalogue(logon()), null)).singleElement()
                .satisfies(warning -> assertThat(warning).contains("Authenticate-Logon", "rule 'Authenticate-Logon'",
                        "event.code", "Data named 'code'"));
    }

    @Test
    void theCodeCarriedInTheRulesDataListOrInCommonIsKept() {
        final String bare = """
                [{"name": "Authenticate-Logon", "when": [{"field": "event.code", "equals": "4624"}],
                  "fields": [{"path": "EventDetail/TypeId", "value": "Authenticate-Logon"},
                             {"path": "EventDetail/Authenticate/Action", "value": "Logon"}]%s}]""";

        assertThat(VendorCodeKept.missing(mapping(bare.formatted(", \"data\": [\"event.code\"]")), RECORDS,
                catalogue(logon()), null)).isEmpty();
        assertThat(VendorCodeKept.missing(TranslationMapping.fromJson(JsonUtil.readValue("""
                {"input": "json", "common": [{"path": "EventDetail/Authenticate/Data", "data_name": "code",
                 "field": "event.code"}], "events": %s}""".formatted(bare.formatted("")), Map.class)), RECORDS,
                catalogue(logon()), null)).isEmpty();
    }

    @Test
    void theSourceTypeFieldAndNotConditionsAreNotTheVendorsCode() {
        final EventType logon = new EventType("Authenticate-Logon", "Logon", null, "Authenticate", "Logon", null,
                null, null, null, List.of(new Way(List.of(
                        Condition.fromJson(Map.of("field", "event.kind", "equals", "logon")),
                        Condition.fromJson(Map.of("field", "user", "equals", "bob", "not", true))),
                List.of())), false, EventType.USER_CONFIRMED, "high", null, EventType.CONFIRMED);
        final TranslationMapping noData = mapping("""
                [{"name": "Authenticate-Logon", "when": [{"field": "event.kind", "equals": "logon"}],
                  "fields": [{"path": "EventDetail/TypeId", "value": "Authenticate-Logon"},
                             {"path": "EventDetail/Authenticate/Action", "value": "Logon"}]}]""");

        assertThat(VendorCodeKept.missing(noData, RECORDS, new Catalogue(List.of(logon), null,
                new Catalogue.Scope("event.kind", List.of())), null)).isEmpty();
        assertThat(VendorCodeKept.missing(noData, RECORDS, catalogue(logon), null)).singleElement()
                .satisfies(warning -> assertThat(warning).contains("event.kind").doesNotContain("named 'user'"));
    }

    @Test
    void anEventTypeNotYetConfirmedOrTheCatchAllIsNotWarnedOf() {
        final EventType proposed = new EventType("Authenticate-Logon", "Logon", null, "Authenticate", "Logon",
                null, null, null, null, List.of(way("4624")), false, EventType.AGENT_KNOWLEDGE, "high", null,
                EventType.PROPOSED);
        final TranslationMapping lost = mapping("""
                [{"name": "Authenticate-Logon", "when": [{"field": "event.code", "equals": "4624"}],
                  "fields": [{"path": "EventDetail/TypeId", "value": "Authenticate-Logon"},
                             {"path": "EventDetail/Authenticate/Action", "value": "Logon"}]},
                 {"name": "uncatalogued", "catch_all": true,
                  "fields": [{"path": "EventDetail/TypeId", "value": "Unknown"}]}]""");

        assertThat(VendorCodeKept.missing(lost, RECORDS, catalogue(proposed), null)).isEmpty();
        // The logoffs go to the catch-all, which writes no vendor's code: that is not this warning's.
        assertThat(VendorCodeKept.missing(lost, RECORDS, catalogue(logon(), logoff()), null)).singleElement()
                .satisfies(warning -> assertThat(warning).contains("Authenticate-Logon")
                        .doesNotContain("Authenticate-Logoff"));
    }

    @Test
    void withoutACatalogueNothingIsWarnedOf() {
        assertThat(VendorCodeKept.missing(mapping(KEPT), RECORDS, null, null)).isEmpty();
    }

    private static EventType logon() {
        return confirmed("Authenticate-Logon", "4624", "Logon");
    }

    private static EventType logoff() {
        return confirmed("Authenticate-Logoff", "4634", "Logoff");
    }

    private static EventType confirmed(final String typeId, final String code, final String action) {
        return new EventType(typeId, action, null, "Authenticate", action, null, null, null, null,
                List.of(way(code)), false, EventType.USER_CONFIRMED, "high", null, EventType.CONFIRMED);
    }

    private static Way way(final String code) {
        return new Way(List.of(Condition.fromJson(Map.of("field", "event.code", "equals", code))), List.of());
    }

    private static Catalogue catalogue(final EventType... types) {
        return new Catalogue(List.of(types), null, null);
    }

    private static TranslationMapping mapping(final String events) {
        return TranslationMapping.fromJson(JsonUtil.readValue("{\"input\": \"json\", \"events\": " + events + "}",
                Map.class));
    }
}
