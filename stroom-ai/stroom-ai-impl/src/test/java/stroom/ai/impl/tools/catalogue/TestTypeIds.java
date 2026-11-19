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
