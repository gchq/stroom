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

package stroom.ai.impl.tools.draft;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// Action elements the sample's own values show, as mapping rules (the Stroom MCP server's `utils/actions.py`,
/// which its tests reach through the draft and the sample check: see [TestDraftMapping] and [TestLocalCheck]).
class TestActionRules extends DraftFixtures {

    private static final List<Map<String, Object>> TRAFFIC = json("""
            [{"kind": "TRAFFIC", "action": "ALLOW", "src": "10.0.0.1", "dst": "10.0.0.2", "proto": "tcp"},
             {"kind": "TRAFFIC", "action": "DENY", "src": "10.0.0.3", "dst": "10.0.0.4", "proto": "icmp6"},
             {"kind": "TRAFFIC", "action": "TIMEOUT", "src": "10.0.0.3", "dst": "10.0.0.4", "proto": "udp"}]""");
    private static final List<String> NAMES = List.of("kind", "action", "src", "dst", "proto");

    @Test
    void connectionsAllowedAndDeniedAreNetworkPermitAndDeny() {
        final ActionRules.Found found = ActionRules.actionRules(TRAFFIC, NAMES, List.of(map("field", "kind",
                "equals", "TRAFFIC")), "traffic", null, null, Set.of("kind"), null);
        assertThat(found.split()).isEqualTo("action");
        assertThat(found.left()).containsExactly("TIMEOUT");
        assertThat(found.rules().stream().map(r -> r.get("name")).toList()).containsExactly("traffic_permitted",
                "traffic_denied");
        final Map<String, Object> permitted = found.rules().getFirst();
        assertThat(permitted.get("when")).isEqualTo(json("""
                [{"field": "kind", "equals": "TRAFFIC"}, {"field": "action", "equals": "ALLOW"}]"""));
        // The protocols TransportProtocol takes, from every record of the kind; the rest are Other.
        assertThat(PyValues.maps(permitted.get("fields"))).contains(map("path",
                "EventDetail/Network/Permit/Source/TransportProtocol", "field", "proto", "map",
                map("tcp", "TCP", "udp", "UDP"), "default", "Other"));
        assertThat(ActionRules.described(found.rules()))
                .isEqualTo("Network/Permit for action ALLOW; Network/Deny for action DENY");
    }

    @Test
    void recordsThatArentMapsOrHaveNoActionFieldGiveNoRules() {
        assertThat(ActionRules.actionRules(List.of("text"), NAMES, List.of(), "", null, null, Set.of(), null)
                .rules()).isEmpty();
        assertThat(ActionRules.actionRules(TRAFFIC, List.of("kind"), List.of(), "", null, null, Set.of(), null)
                .split()).isNull();
    }

    @Test
    void logonsTakeTheirOutcomeFromTheirValuesWhenEveryOneSaysIt() {
        final List<Map<String, Object>> records = json("""
                [{"result": "LOGIN_OK", "user": "a"}, {"result": "LOGIN_FAILED", "user": "b"},
                 {"result": "LOGOUT", "user": "a"}]""");
        final ActionRules.Found found = ActionRules.actionRules(records, List.of("result", "user"), List.of(), "",
                List.of("user", "account"), null, null, null);
        assertThat(found.rules().stream().map(r -> r.get("name")).toList()).containsExactly("logon", "logoff");
        final Map<String, Map<String, Object>> logon = fields(found.rules().getFirst());
        assertThat(logon.get("EventDetail/Authenticate/User/Id")).isEqualTo(map("path",
                "EventDetail/Authenticate/User/Id", "any_of", List.of("user", "account")));
        assertThat(logon.get("EventDetail/Authenticate/Outcome/Success").get("map"))
                .isEqualTo(map("LOGIN_OK", "true", "LOGIN_FAILED", "false"));
    }

    @Test
    void aSeverityTheSchemaHasNoWordForGivesNoSeverity() {
        final List<Map<String, Object>> records = json("[{\"level\": \"INFO\"}, {\"level\": \"WEIRD\"}]");
        assertThat(ActionRules.severityFields(records, List.of("level"))).isEmpty();
        final List<Map<String, Object>> mixed = json("[{\"level\": \"Info\"}, {\"level\": \"error\"}]");
        // Neither all upper nor all lower case: only the values seen.
        assertThat(ActionRules.severityFields(mixed, List.of("level")).getFirst().get("map"))
                .isEqualTo(map("Info", "Info", "error", "Major"));
    }

    @Test
    void aFieldOrSeveralReadInTurn() {
        assertThat(ActionRules.read("user")).isEqualTo(map("field", "user"));
        assertThat(ActionRules.read(List.of("user", "account"))).isEqualTo(map("any_of", List.of("user",
                "account")));
    }
}
