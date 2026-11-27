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

import stroom.ai.impl.tools.sample.ToolJson;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/// A held value that is a document (HTML, a mail's body, text whose elements differ from record to record) is kept
/// as one Data value, not read through xml_fields. Seen in chat 183: a Teams message body (`event.Body.Content`, its
/// sibling `event.Body.ContentType` `html`) read through xml_fields, each part of the HTML mapped as Data
/// (`.../div/p/at/@id`, `.../emoji/@title`), and the records whose HTML wasn't well-formed warned of.
class TestDraftHeldMarkup {

    private static final String BODY = "event.Body.Content";
    private static final String TEAMS_BODY = "<div><p>See the diff <at id=\"0\">Omar Haddad</at> <emoji id=\"like\" "
                                             + "alt=\"👍\" title=\"Like\"/></p><p><pre><code>kubectl ..."
                                             + "</code></pre></p></div>";

    // JSON lines, one a record, each made by the function from its number.
    private static String lines(final int count, final IntFunction<Map<String, Object>> record) {
        return IntStream.range(0, count)
                .mapToObj(n -> ToolJson.dumps(record.apply(n)))
                .collect(Collectors.joining("\n"));
    }

    // A record with a user, a time, and the body given (with its content type, when not null).
    private static Map<String, Object> message(final int n, final String body, final String contentType) {
        final Map<String, Object> inner = new LinkedHashMap<>();
        if (contentType != null) {
            inner.put("ContentType", contentType);
        }
        inner.put("Content", body);
        final Map<String, Object> record = new LinkedHashMap<>();
        record.put("time", "2026-11-20T10:0" + n + ":00Z");
        record.put("user", "user" + n);
        record.put("event", Map.of("Body", inner));
        return record;
    }

    private static Map<String, Object> draft(final String lines) {
        return DraftMapping.draft(List.of(lines), "Messages", null, null, null, null, null);
    }

    private static List<String> fieldsRead(final Map<String, Object> draft) {
        final List<String> out = new ArrayList<>();
        fieldsRead(DraftFixtures.mappingOf(draft), out);
        return out;
    }

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

    private static String noteOn(final Map<String, Object> draft, final String field) {
        return DraftFixtures.notes(draft).stream()
                .filter(note -> note.startsWith("'" + field + "'"))
                .findFirst()
                .orElse("");
    }

    // Kept whole: not in xml_fields, read once as itself, none of its parts read.
    private static void assertKeptWhole(final Map<String, Object> draft, final String field) {
        final Map<String, Object> mapping = DraftFixtures.mappingOf(draft);
        assertThat(String.valueOf(mapping.get("xml_fields"))).doesNotContain(field);
        final List<String> read = fieldsRead(draft);
        assertThat(read).contains(field).noneMatch(name -> name.startsWith(field + "/"));
    }

    @Test
    void aBodyWhoseSiblingSaysHtmlIsKeptWhole() {
        final Map<String, Object> draft = draft(lines(4, n -> message(n, TEAMS_BODY, "html")));

        assertKeptWhole(draft, BODY);
        assertThat(noteOn(draft, BODY)).contains("document", "'event.Body.ContentType'", "'html'",
                "xml_fields");
    }

    @Test
    void aBodyWhoseSiblingSaysTextOrRtfIsKeptWholeHoweverUniform() {
        for (final String type : List.of("text/plain", "rtf", "text/rtf; charset=utf-8", "message/rfc822")) {
            final Map<String, Object> draft = draft(lines(4, n -> message(n,
                    "<note><to>user" + n + "</to><body>hello</body></note>", type)));

            assertKeptWhole(draft, BODY);
            assertThat(noteOn(draft, BODY)).as(type).contains("document");
        }
    }

    @Test
    void aBodyWhoseSiblingSaysXmlOrJsonIsFieldMapped() {
        // A structured type: mapped as today, even where its structure alone can't tell (two records of two
        // shapes).
        for (final String type : List.of("application/soap+xml", "text/xml", "xml", "application/vnd.acme+xml",
                "application/json")) {
            final Map<String, Object> draft = draft(lines(2, n -> message(n, n == 0
                    ? "<Login><User>user0</User><Result>success</Result></Login>"
                    : "<Account><Id>a1</Id><Owner>user1</Owner></Account>", type)));

            assertThat(DraftFixtures.mappingOf(draft).get("xml_fields")).as(type).isEqualTo(List.of(BODY));
        }
    }

    @Test
    void htmlWithNoSiblingIsKeptWholeWithASample() {
        final Map<String, Object> draft = draft(lines(4, n -> message(n,
                TEAMS_BODY.replace("Omar Haddad", "user" + n), null)));

        assertKeptWhole(draft, BODY);
        assertThat(noteOn(draft, BODY)).contains("HTML", "<div><p>See the diff");
    }

    @Test
    void xhtmlIsKeptWhole() {
        final Map<String, Object> draft = draft(lines(4, n -> message(n,
                "<page xmlns=\"http://www.w3.org/1999/xhtml\"><widget>w" + n + "</widget></page>", null)));

        assertKeptWhole(draft, BODY);
    }

    @Test
    void anUnclearValueIsKeptWholeAndItsSamplesShown() {
        // Two records, half alike: neither clearly uniform nor clearly varied; the agent judges from what it knows
        // of the source.
        final Map<String, Object> draft = draft(lines(2, n -> message(n, n == 0
                ? "<audit>   <who>alice</who>\n  <what>login</what></audit>"
                : "<audit><who>bob</who><where>office</where></audit>", null)));

        assertKeptWhole(draft, BODY);
        assertThat(noteOn(draft, BODY)).contains("<audit> <who>alice</who> <what>login</what></audit>",
                "xml_fields", "ask the user");
    }

    @Test
    void xmlWhoseStructureVariesFromRecordToRecordIsKeptWhole() {
        final List<String> bodies = List.of(
                "<doc><title>a</title><para>x</para></doc>",
                "<doc><section><heading>b</heading><list><item>1</item></list></section></doc>",
                "<doc><figure><caption>c</caption><img src=\"x\"/></figure></doc>",
                "<doc><quote><cite>d</cite></quote><footnote>e</footnote></doc>",
                "<doc><table><row><cell>f</cell></row></table></doc>",
                "<doc><appendix><ref>g</ref></appendix></doc>");
        final Map<String, Object> draft = draft(lines(bodies.size(), n -> message(n, bodies.get(n), null)));

        assertKeptWhole(draft, BODY);
        assertThat(noteOn(draft, BODY)).contains("differ", "confirm with the user");
    }

    @Test
    void aUniformPayloadIsFieldMapped() {
        final Map<String, Object> draft = draft(lines(4, n -> message(n,
                "<Login><User>user" + n + "</User><Result>success</Result></Login>", null)));

        assertThat(DraftFixtures.mappingOf(draft).get("xml_fields")).isEqualTo(List.of(BODY));
    }

    @Test
    void aWindowsEventWhoseDataVariesByEventIdIsFieldMapped() {
        // Each event id has its own EventData and UserData: varied over the sample, uniform within an event type.
        final List<String> ids = List.of("4624", "4688", "1102", "4624", "4688", "1102");
        final Map<String, Object> draft = draft(lines(ids.size(), n -> message(n, windowsEvent(ids.get(n), n),
                null)));

        assertThat(DraftFixtures.mappingOf(draft).get("xml_fields")).isEqualTo(List.of(BODY));
    }

    @Test
    void aWindowsEventIsFieldMappedWhenMeasuredWithinCataloguedEventTypes() {
        final List<String> ids = List.of("4624", "4688", "1102", "4624", "4688", "1102");
        final String lines = lines(ids.size(), n -> {
            final Map<String, Object> record = message(n, windowsEvent(ids.get(n), n), null);
            record.put("id", ids.get(n));
            return record;
        });
        final List<Map<String, Object>> types = ids.stream().distinct()
                .map(id -> TestCatalogueTranslation.type("E" + id, "Event " + id, "Process", "Create", null,
                        TestCatalogueTranslation.way(TestCatalogueTranslation.eq("id", id))))
                .toList();
        final Map<String, Object> draft = DraftMapping.draft(List.of(lines), "Windows", "Windows", "Test",
                TestCatalogueTranslation.notes(types, true), null, null);

        assertThat(DraftFixtures.mappingOf(draft).get("xml_fields")).isEqualTo(List.of(BODY));
    }

    private static String windowsEvent(final String id, final int n) {
        final String data = switch (id) {
            case "4624" -> "<EventData><Data Name=\"TargetUserName\">u" + n + "</Data><Data Name=\"LogonType\">3"
                           + "</Data></EventData>";
            case "4688" -> "<EventData><Data Name=\"NewProcessName\">cmd.exe</Data></EventData>";
            default -> "<UserData><LogFileCleared><SubjectUserName>u" + n + "</SubjectUserName>"
                       + "</LogFileCleared></UserData>";
        };
        return "<Event><System><Provider Name=\"Microsoft-Windows-Security-Auditing\"/><EventID>" + id
               + "</EventID><TimeCreated SystemTime=\"2026-11-20T10:00:0" + n + "Z\"/><Computer>pc" + n
               + "</Computer><Channel>Security</Channel></System>" + data + "</Event>";
    }
}
