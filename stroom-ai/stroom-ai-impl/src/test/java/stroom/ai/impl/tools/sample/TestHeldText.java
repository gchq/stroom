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

package stroom.ai.impl.tools.sample;

import stroom.ai.impl.tools.generation.XPathRegex;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// Structured data held in a string field, its format told per record (JSON, encoded once or twice; XML; a syslog
/// header; key=value pairs; else text), stepped into, and the names a discovery offers inside it.
class TestHeldText {

    @Test
    void eachFormatIsToldInOrderAndPlainTextIsNeverRead() {
        assertThat(HeldText.parse("{\"a\": 1}").format()).isEqualTo(HeldText.JSON);
        assertThat(HeldText.parse("\"{\\\"a\\\": 1}\"").format()).isEqualTo(HeldText.JSON);
        assertThat(HeldText.parse("﻿  <a x=\"1\"/>\n").format()).isEqualTo(HeldText.XML);
        assertThat(HeldText.parse("<?xml version=\"1.0\"?><a/>").format()).isEqualTo(HeldText.XML);
        assertThat(HeldText.parse("<34>Oct 11 22:14:15 host1 sshd[1]: hello").format()).isEqualTo(HeldText.SYSLOG);
        assertThat(HeldText.parse("<165>1 2026-11-18T10:00:00Z h app 1 ID - x").format()).isEqualTo(HeldText.SYSLOG);
        assertThat(HeldText.parse("a=1 b=2").format()).isEqualTo(HeldText.KEY_VALUE);
        assertThat(HeldText.parse("a=1,b=2;c=3").format()).isEqualTo(HeldText.KEY_VALUE);
        // One pair is text: a sentence may hold one.
        assertThat(HeldText.parse("failed with code=7").format()).isEqualTo(HeldText.TEXT);
        assertThat(HeldText.parse("User bob logged in").format()).isEqualTo(HeldText.TEXT);
        assertThat(HeldText.parse("").format()).isEqualTo(HeldText.TEXT);
        assertThat(HeldText.parse(null).format()).isEqualTo(HeldText.TEXT);
        // A JSON array, or a string holding no object, is no object to read.
        assertThat(HeldText.parse("[1, 2]").format()).isEqualTo(HeldText.TEXT);
        assertThat(HeldText.parse("\"just words\"").format()).isEqualTo(HeldText.TEXT);
    }

    @Test
    void malformedJsonOrXmlIsReadOnDownTheList() {
        // Cut off: no longer JSON, but pairs in it make it key=value; none make it text.
        assertThat(HeldText.parse("{\"cut\": x=1 y=2").format()).isEqualTo(HeldText.KEY_VALUE);
        assertThat(HeldText.parse("{\"logger_name\": \"com.x\"").format()).isEqualTo(HeldText.TEXT);
        assertThat(HeldText.parse("<a><b></a>").format()).isEqualTo(HeldText.TEXT);
        assertThat(HeldText.parse("<a/> trailing").format()).isEqualTo(HeldText.TEXT);
    }

    @Test
    void stepsReadEachFormatsNames() {
        assertThat(read("{\"p\": {\"q\": \"v\"}, \"n\": 3, \"b\": true, \"z\": null}", "p.q")).isEqualTo("v");
        assertThat(read("{\"n\": 3}", "n")).isEqualTo("3");
        assertThat(read("{\"b\": true}", "b")).isEqualTo("true");
        assertThat(read("{\"z\": null}", "z")).isNull();
        assertThat(read("{\"o\": {\"x\": 1}}", "o")).isNull();
        assertThat(read("{\"l\": [1]}", "l")).isNull();
        assertThat(read("<r a=\"1\"><x:b xmlns:x=\"u\" x:c=\"2\">t<i>u</i></x:b><b>second</b></r>", "b"))
                .isEqualTo("tu");
        assertThat(read("<r a=\"1\"><b c=\"2\"/></r>", "@a")).isEqualTo("1");
        assertThat(read("<r a=\"1\"><b c=\"2\"/></r>", "b.@c")).isEqualTo("2");
        assertThat(read("<r a=\"1\"><b c=\"2\"/></r>", "x:b.@c")).isEqualTo("2");
        assertThat(read("<ns:Root xmlns:ns=\"u\"/>", "#root")).isEqualTo("Root");
        assertThat(read("k=1 logger.name=\"a b\" k=2", "logger.name")).isEqualTo("a b");
        assertThat(read("k=1 j=2 k=3", "k")).isEqualTo("1");
        assertThat(read("<34>Oct 11 22:14:15 host1 sshd[123]: hello there", "host")).isEqualTo("host1");
        assertThat(read("<34>Oct 11 22:14:15 host1 sshd[123]: hello there", "app")).isEqualTo("sshd");
        assertThat(read("<34>Oct 11 22:14:15 host1 sshd[123]: hello there", "message")).isEqualTo("hello there");
        assertThat(read("Oct 11 22:14:15 host1 no program here", "app")).isEmpty();
        assertThat(read("<165>1 2026-11-18T10:00:00Z h2 auditd 7 ID - a=b", "app")).isEqualTo("auditd");
        assertThat(read("{\"a\": 1}", "#format")).isEqualTo(HeldText.JSON);
        assertThat(read("plain words", "#format")).isEqualTo(HeldText.TEXT);
    }

    @Test
    void aStepOfOneFormatReadsNothingInAnother() {
        assertThat(read("{\"k\": \"v\"}", "kv:k")).isNull();
        assertThat(read("k=v j=w", "kv:k")).isEqualTo("v");
        assertThat(read("k=v j=w", "json:k")).isNull();
        assertThat(read("<r k=\"v\"/>", "xml:@k")).isEqualTo("v");
        // A name a format can't hold reads nothing in it.
        assertThat(read("{\"k\": \"v\"}", "@k")).isNull();
        assertThat(read("{\"k\": \"v\"}", "#root")).isNull();
        assertThat(read("k=v j=w", "@k")).isNull();
        assertThat(read("k=v j=w", "not a key")).isNull();
        assertThat(read("<34>Oct 11 22:14:15 host1 sshd: x", "pid")).isNull();
    }

    @Test
    void valuesAreStrippedAndNilValuesAreNone() {
        assertThat(HeldText.normalised(" \tv\n")).isEqualTo("v");
        assertThat(HeldText.normalised("N/A")).isNull();
        assertThat(HeldText.normalised("-")).isNull();
        assertThat(HeldText.normalised("  ")).isNull();
        assertThat(HeldText.normalised(null)).isNull();
    }

    @Test
    void theRegularExpressionsAreOnesXPathReadsToo() {
        for (final String regex : List.of(HeldText.AROUND, HeldText.SPACE_AROUND, HeldText.PAIR,
                HeldText.SYSLOG_5424, HeldText.SYSLOG_3164, HeldText.keyRegex("logger_name"))) {
            assertThat(XPathRegex.problem(regex)).as(regex).isEmpty();
        }
        assertThat(HeldText.keyRegex("not a key")).isNull();
        assertThat(HeldText.keyRegex("")).isNull();
        assertThat(HeldText.keyRegex(null)).isNull();
    }

    @Test
    void aDiscoveryOffersTheNamesInsideHeldValuesThreeDeepAtMost() {
        final Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", "1");
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("a", "1");
        payload.put("inner", ToolJson.dumps(Map.of("d", "<r k='v'><x>y</x></r>")));
        final Map<String, Object> event = new LinkedHashMap<>();
        event.put("logger_name", "com.x");
        event.put("payload", ToolJson.dumps(payload));
        record.put("event", ToolJson.dumps(event));
        record.put("text", "plain words here");

        final HeldText.Expansion expansion = HeldText.expand(record);

        assertThat(expansion.values()).containsEntry("event>logger_name", "com.x")
                .containsEntry("event>payload>a", "1")
                .containsKey("event>payload>inner>d");
        // A fourth held value inside is never stepped into.
        assertThat(expansion.values().keySet()).noneMatch(path -> path.startsWith("event>payload>inner>d>"));
        assertThat(expansion.formats()).containsEntry("event", HeldText.JSON)
                .containsEntry("event>payload", HeldText.JSON)
                .containsEntry("event>payload>inner", HeldText.JSON)
                .containsEntry("text", HeldText.TEXT)
                .containsEntry("id", HeldText.TEXT);
        // Every name offered reads, through a reading, the value the discovery found.
        expansion.values().forEach((path, value) -> assertThat(SourceTypeFields.valueOf(record, path)).as(path)
                .isEqualTo(value));
    }

    @Test
    void aDiscoveryOffersXmlElementsAttributesTheRootAndSyslogFields() {
        final Map<String, Object> record = new LinkedHashMap<>();
        record.put("xml", "<Log logger=\"com.x\"><Head><Id>7</Id></Head><Body>b</Body></Log>");
        record.put("line", "<34>Oct 11 22:14:15 host1 sshd[123]: k=1 j=2");

        final HeldText.Expansion expansion = HeldText.expand(record);

        assertThat(expansion.values()).containsEntry("xml>@logger", "com.x").containsEntry("xml>Head.Id", "7")
                .containsEntry("xml>Body", "b").containsEntry("xml>#root", "Log")
                .containsEntry("line>host", "host1").containsEntry("line>app", "sshd")
                .containsEntry("line>message>k", "1")
                .doesNotContainKey("line>message");
        expansion.values().forEach((path, value) -> assertThat(SourceTypeFields.valueOf(record, path)).as(path)
                .isEqualTo(value));
    }

    private static String read(final String text, final String step) {
        return HeldText.read(HeldText.parse(text), SourceTypeReading.Step.of(step));
    }
}
