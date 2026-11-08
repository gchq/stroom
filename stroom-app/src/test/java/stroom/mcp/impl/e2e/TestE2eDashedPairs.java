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

package stroom.mcp.impl.e2e;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// Bodies of `Key value` parts separated by ` - ` (NetScaler's SSLVPN messages; seen in chat 163, where the agent
/// wrote some 25 extract entries by hand and one took the next field with every logout's method): the draft reads
/// them as pairs, and the XSLT generated from them steps clean in a real pipeline, each value to its own dash, a
/// quoted value to its closing quote (a dash inside it kept). Driving the REAL tools on a real Stroom.
@SuppressWarnings("unchecked")
class TestE2eDashedPairs extends AbstractE2eTest {

    private static final String STAMP = String.valueOf(System.currentTimeMillis() % 100_000);
    private static final String HEADER = "Oct 13 09:%s:02 ns01 10/13/2026:09:%s:02 GMT ns01 0-PPE-0 : default SSLVPN "
                                         + "%s %s 0 : ";
    private static final String SAMPLE = String.join("\n",
            header("15", "LOGIN", "2639") + "Context grace@203.0.113.9 - SessionId: 2639 - User grace - Client_ip "
            + "203.0.113.9 - Nat_ip \"Mapped Ip\" - Vserver 192.0.2.10:443 - Browser_type \"Citrix Workspace App "
            + "2409\" - SSLVPN_client_type ICA - Group(s) \"Staff\"",
            header("16", "LOGIN", "2640") + "Context dave@203.0.113.12 - SessionId: 2640 - User dave - Client_ip "
            + "203.0.113.12 - Nat_ip \"Mapped Ip\" - Vserver 192.0.2.10:443 - Browser_type \"Citrix Workspace App "
            + "2409\" - SSLVPN_client_type ICA - Group(s) \"Staff - North\"",
            header("40", "LOGOUT", "2639") + "Context grace@203.0.113.9 - SessionId: 2639 - User grace - Client_ip "
            + "203.0.113.9 - Nat_ip \"Mapped Ip\" - Vserver 192.0.2.10:443 - Duration 00:25:00 - LogoutMethod "
            + "\"IdleTimeout\" - Group(s) \"Staff\"",
            header("41", "LOGOUT", "2640") + "Context dave@203.0.113.12 - SessionId: 2640 - User dave - Client_ip "
            + "203.0.113.12 - Nat_ip \"Mapped Ip\" - Vserver 192.0.2.10:443 - Duration 00:25:00 - LogoutMethod "
            + "\"Explicit\" - Group(s) \"Staff - North\"") + "\n";

    @Test
    void dashedPairsAreDraftedAndReadByStroomEachValueToItsOwnDash() {
        final String build = "dashed-pairs-" + STAMP;
        final String feed = "DASHED-PAIRS-" + STAMP;
        final String template = templateUuid("translation", "Event Data (Text)");
        createFeed(build, feed);
        final long raw = upload(feed, SAMPLE);
        final Map<String, Object> converter = map(agreed("build_data_splitter", args("stream_ids", List.of(raw),
                "save_as", feed + "-SYSLOG", "build", build)), "saved");
        final Map<String, Object> draft = json("draft_translation_mapping", args("stream_ids", List.of(raw),
                "build", build, "schema_version", VERSION, "system_name", "E2E", "environment", "Test"));
        final Map<String, Object> mapping = sessions((Map<String, Object>) draft.get("mapping"));

        final Map<String, Object> built = agreed("build_translation_xslt", args("mapping", mapping, "stream_ids",
                List.of(raw), "build", build, "name", feed + "-Events", "schema_version", VERSION));

        assertThat(built.get("ok")).as("accepted: %s", built.get("problems")).isEqualTo(true);
        final String xslt = (String) map(built, "saved").get("uuid");
        final String pipeline = (String) createPipeline(build, feed + "-Events", template,
                docProperty("dsParser", "textConverter", (String) converter.get("uuid"), "TextConverter"),
                docProperty("translationFilter", "xslt", xslt, "XSLT")).get("uuid");
        final Map<String, Object> stepped = stepSample(pipeline, List.of(raw));
        assertThat(stepped.get("verdict")).as("stepped: %s", stepped.get("groups")).isEqualTo("clean");
        final List<String> written = new ArrayList<>();
        for (int record = 0; record < 4; record++) {
            written.add(translationOutput(pipeline, raw, record));
        }
        assertThat(written).as("Stroom read each value to its own dash").satisfiesExactly(
                output -> assertThat(output).contains("<Id>grace</Id>", "Value=\"Staff\"")
                        .doesNotContain("Value=\"Staff - North\""),
                output -> assertThat(output).contains("<Id>dave</Id>", "Value=\"Staff - North\""),
                output -> assertThat(output).contains("<Id>grace</Id>", "Value=\"IdleTimeout\"",
                        "Value=\"00:25:00\"").doesNotContain("Group(s)"),
                output -> assertThat(output).contains("<Id>dave</Id>", "Value=\"Explicit\"",
                        "Value=\"Staff - North\""));
    }

    private static String header(final String minute, final String kind, final String session) {
        return HEADER.formatted(minute, minute, kind, session);
    }

    // The drafted mapping, its pairs as drafted, with one rule for every record: the session's user, group,
    // logout method and duration (the draft has only its catch-all rule for them).
    private static Map<String, Object> sessions(final Map<String, Object> drafted) {
        final Map<String, Object> mapping = new LinkedHashMap<>(drafted);
        mapping.put("events", List.of(Map.of("name", "session",
                "fields", List.of(
                        Map.of("path", "EventDetail/TypeId", "value", "SSLVPN"),
                        Map.of("path", "EventDetail/Authenticate/Action", "value", "Logon"),
                        Map.of("path", "EventDetail/Authenticate/User/Id", "field", "user"),
                        Map.of("path", "EventDetail/Authenticate/Data", "data_name", "group", "field", "group_s"),
                        Map.of("path", "EventDetail/Authenticate/Data", "data_name", "logout_method", "field",
                                "logoutmethod"),
                        Map.of("path", "EventDetail/Authenticate/Data", "data_name", "duration", "field",
                                "duration")))));
        return mapping;
    }
}
