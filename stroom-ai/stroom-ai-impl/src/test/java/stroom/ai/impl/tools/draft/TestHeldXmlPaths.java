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

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.sample.ToolJson;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// The draft maps each node of held XML once. Seen in chat 182: a Teams message body (`event.Body.Content`, read
/// through xml_fields) drafted as `//p` and `/div/p`, `//at` and `/div/p/at`, `//@alt`, `//emoji/@alt` and
/// `/div/p/emoji/@alt`, ...: each value written two or three times as Data.
class TestHeldXmlPaths {

    private static final String FIELD = "event.Body.Content";
    private static final String TEAMS_BODY = "<div><p>See the diff <at id=\"0\">Omar Haddad</at> <emoji id=\"like\" "
                                             + "alt=\"👍\" title=\"Like\"/></p><p><pre><code>kubectl ..."
                                             + "</code></pre></p></div>";

    // The names LocalCheck adds for the XML held in the field of each record given.
    private static List<String> names(final String... bodies) {
        final List<Object> records = new ArrayList<>();
        for (final String body : bodies) {
            final Map<String, Object> record = new LinkedHashMap<>();
            record.put(FIELD, body);
            records.add(record);
        }
        LocalCheck.withXmlFields(records, List.of(FIELD));
        final Set<String> names = new LinkedHashSet<>();
        records.forEach(record -> ((Map<?, ?>) record).keySet().forEach(key -> names.add((String) key)));
        return List.copyOf(names);
    }

    private static List<String> drafted(final List<String> names) {
        final Set<String> leftOut = HeldXmlPaths.leftOut(names, List.of(FIELD));
        return names.stream().filter(name -> name.startsWith(FIELD + "/") && !leftOut.contains(name)).toList();
    }

    @Test
    void eachNodeOfATeamsBodyIsDraftedOnceByItsFullPath() {
        final List<String> names = names(TEAMS_BODY);
        // Every form stays readable (LocalCheck finds them all); the draft maps one of each.
        assertThat(names).contains(FIELD + "//p", FIELD + "//at", FIELD + "//@alt", FIELD + "//emoji/@alt",
                FIELD + "/div/p/emoji/@alt");

        assertThat(drafted(names)).containsExactlyInAnyOrder(
                FIELD + "/div/p",
                FIELD + "/div/p/at",
                FIELD + "/div/p/at/@id",
                FIELD + "/div/p/emoji",
                FIELD + "/div/p/emoji/@id",
                FIELD + "/div/p/emoji/@alt",
                FIELD + "/div/p/emoji/@title",
                FIELD + "/div/p/pre/code");
    }

    @Test
    void aNameFoundAtSeveralDepthsIsDraftedAnywhereAndItsFullPathsLeftOut() {
        final List<String> names = names("<div><p>x</p><blockquote><p id=\"q\">quoted</p></blockquote></div>",
                "<div><p>y</p></div>");

        assertThat(drafted(names)).containsExactlyInAnyOrder(FIELD + "//p", FIELD + "//p/@id");
    }

    @Test
    void oneNameAtTwoPathsOfTheSameDepthKeepsBothFullPaths() {
        // //name reads the first only: the two full paths keep both values.
        final List<String> names = names("<msg><from><name>a</name></from><to><name>b</name></to></msg>");

        assertThat(drafted(names)).containsExactlyInAnyOrder(FIELD + "/msg/from/name", FIELD + "/msg/to/name");
    }

    @Test
    void otherFieldsAreLeftAlone() {
        assertThat(HeldXmlPaths.leftOut(List.of("user", "event.Body.ContentType", FIELD), List.of(FIELD)))
                .isEmpty();
        assertThat(HeldXmlPaths.leftOut(List.of(FIELD + "//p"), List.of())).isEmpty();
    }

    @Test
    void theDraftMapsEachNodeOfAHeldBodyOnce() {
        final String lines = Stream.of("alice", "bob", "carol")
                .map(user -> ToolJson.dumps(Map.of("time", "2026-11-20T10:00:00Z", "user", user, "event",
                        Map.of("Body", Map.of("Content", TEAMS_BODY.replace("Omar Haddad", user))))))
                .collect(Collectors.joining("\n"));

        final Map<String, Object> draft = DraftMapping.draft(List.of(lines), "Teams", null, null, null, null, null);
        final Map<String, Object> mapping = DraftFixtures.mappingOf(draft);

        assertThat(mapping.get("xml_fields")).isEqualTo(List.of(FIELD));
        final List<String> read = new ArrayList<>();
        fieldsRead(mapping, read);
        final List<String> held = read.stream().filter(name -> name.startsWith(FIELD + "/")).toList();
        assertThat(held).doesNotHaveDuplicates()
                .noneMatch(name -> name.contains("//"))
                .contains(FIELD + "/div/p", FIELD + "/div/p/at", FIELD + "/div/p/emoji/@alt",
                        FIELD + "/div/p/pre/code");
        // The note still offers the anywhere form.
        assertThat(DraftFixtures.notes(draft)).anySatisfy(note -> assertThat(note)
                .contains("'<field>//<name>' for one anywhere below"));
    }

    @Test
    void eachTimeHeldInXmlIsATimeCandidateOnce() {
        // Two times in held XML, each as likely the event's: put to the agent by the path each is drafted by, not
        // also by `<field>//<name>` (the same node, read another way).
        final String lines = Stream.of(0, 1, 2)
                .map(n -> ToolJson.dumps(Map.of("user", "u" + n, "event", Map.of("Body", Map.of("Content",
                        "<audit><ts>2026-11-20T10:0" + n + ":00Z</ts><logged_at>2026-11-20T11:0" + n
                        + ":30Z</logged_at></audit>")))))
                .collect(Collectors.joining("\n"));

        final Map<String, Object> draft = DraftMapping.draft(List.of(lines), "Audit", null, null, null, null, null);

        final List<Map<String, Object>> candidates = JsonValues.maps(draft.get(DraftMapping.TIME_CANDIDATES));
        assertThat(candidates).singleElement().satisfies(candidate -> assertThat(JsonValues.maps(
                candidate.get("fields")).stream().map(field -> field.get("field")).toList())
                .containsExactlyInAnyOrder(FIELD + "/audit/ts", FIELD + "/audit/logged_at"));
        // The timestamp fields the notes list, each once too.
        assertThat(DraftFixtures.notes(draft)).filteredOn(note -> note.startsWith("Timestamp fields: "))
                .allSatisfy(note -> assertThat(note).doesNotContain(FIELD + "//"));
    }

    // Every field the mapping's entries (common, the rules', the groups') read, conditions left out.
    private static void fieldsRead(final Object value, final List<String> out) {
        if (value instanceof final Map<?, ?> map) {
            if (map.get("field") instanceof final String field && map.containsKey("path")) {
                out.add(field);
            }
            for (final String key : List.of("common", "events", "fields")) {
                fieldsRead(map.get(key), out);
            }
        } else if (value instanceof final List<?> list) {
            list.forEach(inner -> fieldsRead(inner, out));
        }
    }
}
