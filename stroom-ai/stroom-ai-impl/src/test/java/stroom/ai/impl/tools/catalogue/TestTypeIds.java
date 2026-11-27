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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// TypeIds: Verb-Object, and the default suggested when a proposal gives none.
class TestTypeIds {

    @ParameterizedTest
    @ValueSource(strings = {"Authenticate-Logon", "Update-Password", "Process-SOAPRequest", "Create-User-Group",
            "Alert-IDS2"})
    void aVerbAndAnObjectIsATypeId(final String typeId) {
        assertThat(TypeIds.problem(typeId)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Logon", "logon-user", "Authenticate-4624", "Authenticate_Logon", "Update-Password-",
            "Update Password", "-Password", "Update--Password", "Process-SOAP.Request", " "})
    void anythingElseIsRefusedSayingWhatATypeIdIs(final String typeId) {
        assertThat(TypeIds.problem(typeId)).isNotNull();
        assertThat(TypeIds.problem(null)).contains("type_id is needed");
        assertThat(TypeIds.problem("A-" + "B".repeat(TypeIds.MAX_LENGTH))).contains("at most 100");
    }

    @Test
    void theDefaultIsTheEventDetailAndWhatItActsOn() {
        assertThat(TypeIds.defaultTypeId(type("Update", "User/Id", "Password change", "x"))).isEqualTo("Update-User");
        assertThat(TypeIds.defaultTypeId(type("Network/Permit", "Destination", null, null)))
                .isEqualTo("Network-Destination");
    }

    @Test
    void elseTheLastSegmentOfItsFirstEqualsValueAsItIs() {
        assertThat(TypeIds.defaultTypeId(type("Process", null, "SOAP service call",
                "com.pega.pegarules.integration.engine.internal.services.soap.SOAPService")))
                .isEqualTo("Process-SOAPService");
        assertThat(TypeIds.defaultTypeId(type("Authenticate", null, "Logon", "Security\\logon")))
                .isEqualTo("Authenticate-Logon");
    }

    @Test
    void elseItsTitlesFirstWord() {
        // A code that can't be an object (all digits) is passed over.
        assertThat(TypeIds.defaultTypeId(type("Authenticate", null, "Logon succeeded", "4624")))
                .isEqualTo("Authenticate-Logon");
        assertThat(TypeIds.defaultTypeId(type("Update", null, "password change", null)))
                .isEqualTo("Update-Password");
        assertThat(TypeIds.defaultTypeId(type("Update", null, null, null))).isEqualTo("Update-Event");
        assertThat(TypeIds.defaultTypeId(type(null, null, "Heartbeat", null))).isEqualTo("Unknown-Heartbeat");
    }

    @Test
    void theDefaultIsAlwaysATypeId() {
        for (final EventType type : List.of(type("Update", "User", "x", "y"), type(null, null, null, "4624"),
                type("Network/Deny", null, "!!", "a.b-c"))) {
            assertThat(TypeIds.problem(TypeIds.defaultTypeId(type))).as(TypeIds.defaultTypeId(type)).isNull();
        }
    }

    // Seen in testing: a drop rule given a made-up Verb-Object TypeId ('Drop-PEGAOperational') to pass the check.
    @ParameterizedTest
    @ValueSource(strings = {"Operational logging", "PEGA operational", "Heartbeats (load balancer)", "Drop-Rule1",
            "noise", "4624 events"})
    void aDropRulesTypeIdIsAPlainShortLabel(final String label) {
        assertThat(TypeIds.dropProblem(label)).isNull();
        assertThat(TypeIds.problemOf(drop(label))).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "-noise", "noise-", "two--dashes", "a <tag>", "line\nbreak", "trailing "})
    void aDropRulesLabelHoldsOnlyWhatAShortLabelDoes(final String label) {
        assertThat(TypeIds.dropProblem(label)).isNotNull();
        assertThat(TypeIds.dropProblem(null)).contains("type_id is needed");
        assertThat(TypeIds.dropProblem("a".repeat(TypeIds.MAX_DROP_LENGTH + 1))).contains("at most 60");
    }

    // Found in review (2026-11-27): a drop rule labelled Unknown had its count overwritten by the uncatalogued
    // records', which go by that label.
    @ParameterizedTest
    @ValueSource(strings = {"Unknown", "unknown", "UNKNOWN", "Uncatalogued records", "uncatalogued Records"})
    void aDropRuleMayNotTakeTheLabelTheUncataloguedRecordsGoBy(final String label) {
        assertThat(TypeIds.dropProblem(label)).contains("is what the uncatalogued records go by");
    }

    @Test
    void anEventTypeThatIsNoDropRuleIsStillVerbObject() {
        final EventType kept = type("Update", null, "Operational logging", null).withTypeId("Operational logging");

        assertThat(TypeIds.problemOf(kept)).contains("isn't Verb-Object");
    }

    @Test
    void aDropRulesDefaultLabelIsItsTitleElseItsDescriptionsFirstWordsNeverAMadeUpPrefix() {
        assertThat(TypeIds.defaultFor(dropOf("PEGA operational logging", null)))
                .isEqualTo("PEGA operational logging");
        assertThat(TypeIds.defaultFor(dropOf(null, "Records of loggers other than the security and SOAP ones. "
                                                   + "Not wanted."))).isEqualTo("Records of loggers other than the");
        assertThat(TypeIds.defaultFor(dropOf("<<<>>>", null))).isEqualTo(TypeIds.DEFAULT_DROP_LABEL);
        assertThat(TypeIds.defaultFor(dropOf("Heartbeats -- load balancer!", null)))
                .isEqualTo("Heartbeats - load balancer");
        for (final EventType drop : List.of(dropOf("x".repeat(80) + " y", null), dropOf(null, null),
                dropOf("  -- ", "!"))) {
            assertThat(TypeIds.dropProblem(TypeIds.defaultFor(drop))).as(TypeIds.defaultFor(drop)).isNull();
        }
        // An event type that is no drop rule keeps its Verb-Object default.
        assertThat(TypeIds.defaultFor(type("Update", null, "password change", null))).isEqualTo("Update-Password");
    }

    private static EventType drop(final String label) {
        return dropOf(label, null).withTypeId(label);
    }

    private static EventType dropOf(final String title, final String description) {
        return new EventType(null, title, description, null, null, null, null, null, null, List.of(), true,
                EventType.USER_CONFIRMED, "high", null, EventType.PROPOSED);
    }

    private static EventType type(final String detail, final String affectedAs, final String title,
                                  final String equals) {
        final List<Way> ways = equals == null
                ? List.of()
                : List.of(new Way(List.of(Condition.fromJson(Map.of("field", "level", "starts_with", "x")),
                        Condition.fromJson(Map.of("field", "code", "equals", equals))), List.of()));
        return new EventType(null, title, null, detail, null, null, null, null, affectedAs, ways, false,
                EventType.AGENT_KNOWLEDGE, "high", null, EventType.PROPOSED);
    }
}
