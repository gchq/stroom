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

import stroom.ai.impl.tools.KeptMapping.Kept;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The mapping kept with an XSLT, read as the Stroom MCP server wrote it.
class TestKeptMapping {

    /// The mapping of the MCP server's tests (`tests/test_xsltgen.py`'s `mapping()`), as it keeps it.
    static final String MAPPING = """
            {"input":"data_splitter","unmatched":"skip",
             "common":[{"path":"EventTime/TimeCreated","field":"time"},
                       {"path":"EventSource/System/Name","value":"Acme VPN"},
                       {"path":"EventSource/System/Environment","value":"Dev"},
                       {"path":"EventSource/Generator","value":"vpnd"},
                       {"path":"EventSource/Device/HostName","field":"host","default":"unknown"},
                       {"path":"EventSource/User/Id","field":"user"}],
             "events":[{"name":"logon","when":[{"field":"action","equals":"login"}],
                        "fields":[{"path":"EventDetail/TypeId","value":"Logon"},
                                  {"path":"EventDetail/Authenticate/Action","value":"Logon"},
                                  {"path":"EventDetail/Authenticate/User/Id","field":"user"},
                                  {"path":"EventDetail/Authenticate/Data","data_name":"session","field":"sid"}]},
                       {"name":"other","fields":[{"path":"EventDetail/TypeId","field":"action"},
                          {"path":"EventDetail/Unknown/Data","data_name":"action","field":"action"}]}]}""";

    @Test
    void anXsltsKeptMappingIsSummarisedUnlessAskedForWhole() {
        // Seen in production: describe_document returned a 1,400-line kept mapping; the client spilled it to a
        // file, and the agent read it in parts and sent it back whole to regenerate the XSLT.
        final String description = described("Acme VPN events");

        assertThat(KeptMapping.withoutBlock(description, null)).isEqualTo("Acme VPN events");
        assertThat(KeptMapping.pendingOf(description).map(KeptMapping::pendingChanges))
                .hasValue(List.of("Created"));
        final Kept kept = KeptMapping.readMapping(description).orElseThrow();
        assertThat(kept.kind()).isEqualTo("translation");

        final Map<String, Object> summary = KeptMapping.summary(kept, "x-1", false);
        assertThat(summary)
                .containsEntry("kind", "translation")
                .containsEntry("schema_version", "4.1.0")
                .containsEntry("input", "data_splitter")
                .containsEntry("common_entries", 6)
                .containsEntry("extractions", 0)
                .containsEntry("style", "the generator's current defaults");
        assertThat(summary.get("rules")).isEqualTo(List.of(
                "logon: Authenticate (`action` = login)", "other: Unknown (any other record)"));
        assertThat((String) summary.get("how"))
                .contains("uuid='x-1' alone regenerates it")
                .contains("never send it back whole");

        final Map<String, Object> whole = KeptMapping.summary(kept, "x-1", true);
        assertThat(whole.get("mapping")).isEqualTo(JsonUtil.readValue(MAPPING, Object.class));
    }

    @Test
    void anotherKindOfMappingIsSummarisedByItsFields() {
        final String description = "Index\n\n" + KeptMapping.NOTE + "\n\n"
                                   + block("index mapping", "{\"backend\":\"elasticsearch\",\"index_name\":\"ecs-v1\","
                                                            + "\"fields\":[{\"name\":\"a\"},{\"name\":\"b\"}],"
                                                            + "\"settings\":{\"x\":1}}");

        assertThat(KeptMapping.summary(KeptMapping.readMapping(description).orElseThrow(), "i", false))
                .containsExactly(Map.entry("kind", "index"), Map.entry("backend", "elasticsearch"),
                        Map.entry("index_name", "ecs-v1"), Map.entry("fields", 2));
    }

    @Test
    void blocksWrittenBeforeAreStillRead() {
        // Shown between --- markers, as earlier versions of the server wrote them.
        final String description = "Old\n\n--- stroom-mcp translation mapping (kept by the server) ---\n"
                                   + "{\"schema_version\":\"3.5.2\",\"mapping\":{\"events\":[]}}\n"
                                   + "--- end of stroom-mcp translation mapping ---\n";

        assertThat(KeptMapping.readMapping(description)).hasValueSatisfying(kept ->
                assertThat(kept.payload()).containsEntry("schema_version", "3.5.2"));
        assertThat(KeptMapping.withoutBlock(description, null)).isEqualTo("Old");
        // A hand-edited block that isn't an object is no block.
        assertThat(KeptMapping.readMapping("x\n" + block("translation mapping", "[1, 2]"))).isEmpty();
        assertThat(KeptMapping.readMapping("Just a description")).isEmpty();
    }

    @Test
    void onlyTheNamedBlockIsTakenOut() {
        final String description = described("Acme VPN events");

        final String withoutPending = KeptMapping.withoutBlock(description, KeptMapping.PENDING);
        assertThat(KeptMapping.pendingOf(withoutPending)).isEmpty();
        assertThat(KeptMapping.readMapping(withoutPending)).isPresent();
        // The note stays while a block it speaks of does.
        assertThat(withoutPending).contains(KeptMapping.NOTE);
    }

    @Test
    void conditionsAndXpathsAreWrittenForAReader() {
        assertThat(KeptMapping.readable("normalize-space(data[@name='username']/@value)"))
                .isEqualTo("normalize-space(username)");
        assertThat(KeptMapping.readable("*[@key='user']/*[@key='name']")).isEqualTo("user.name");
        assertThat(KeptMapping.conditionText(Map.of("xpath", "data[@name='a']/@value", "one_of", List.of("x", "y"))))
                .isEqualTo("`a` in x, y");
        assertThat(KeptMapping.conditionText(Map.of("field", "b", "present", true, "scope", "record")))
                .isEqualTo("`b` (of the record) present");
        assertThat(KeptMapping.conditionText(Map.of("field", "c", "matches", "^x")))
                .isEqualTo("`c` matches `^x`");
        assertThat(KeptMapping.conditionText(Map.of("field", "d", "in_dictionary", "Admins")))
                .isEqualTo("`d` in dictionary `Admins`");
        assertThat(KeptMapping.eventKinds(Map.of("events", List.of(Map.of("name", "noise", "drop", true)))))
                .containsExactly("noise: left untranslated (any other record)");
    }

    /// @return A description as the MCP server wrote it for an XSLT it generated and then changed in a build: its
    /// own text, the note, the kept mapping and the build's pending changes.
    static String described(final String text) {
        final String mapping = "{\"schema_version\":\"4.1.0\",\"mapping\":" + MAPPING.replaceAll("\\s*\\n\\s*", "")
                               + "}";
        final String pending = "{\"entries\":[{\"date\":\"2026-10-01\",\"author\":\"pk\",\"change\":\"Created\","
                               + "\"by\":\"x\",\"digest\":\"0123456789abcdef\"}],\"base\":null}";
        return text + "\n\n" + KeptMapping.NOTE + "\n\n" + block("translation mapping", mapping) + "\n\n"
               + block(KeptMapping.PENDING, pending);
    }

    private static String block(final String name, final String json) {
        return "<!-- stroom-mcp " + name + ": kept by the server, do not edit or delete\n" + json + "\n-->";
    }
}
