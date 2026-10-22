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

import stroom.ai.impl.tools.ToolRefusal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestShapes {

    private static final String SSH = """
            <38>1 2026-09-28T13:00:00.000Z bastion sshd 812 - - Accepted publickey for frank from 192.0.2.44 port \
            50122 ssh2
            <38>1 2026-09-28T13:02:10.000Z bastion sshd 815 - - Failed password for invalid user admin from \
            192.0.2.99 port 40022 ssh2
            <38>1 2026-09-28T13:04:55.000Z bastion sshd 820 - - Accepted password for grace from 192.0.2.45 port \
            50410 ssh2
            <38>1 2026-09-28T13:05:00.000Z bastion sshd 821 - - Accepted password for henry from 192.0.2.46 port \
            50411 ssh2""";

    private static Shapes shapesOf(final RecordSplitter.Chunk chunk, final List<String> known) {
        final Shapes shapes = new Shapes(known, 2);
        for (int i = 0; i < chunk.records().size(); i++) {
            shapes.add(chunk, i, 1);
        }
        return shapes;
    }

    private static Shapes shapesOf(final String text) {
        return shapesOf(RecordSplitter.split(text), List.of());
    }

    private static Map<String, Integer> counts(final Shapes shapes) {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        shapes.shapes().forEach((signature, shape) -> counts.put(signature, (Integer) shape.get("count")));
        return counts;
    }

    private static String json(final Object... records) {
        return PyJson.dumps(List.of(records));
    }

    @Test
    void syslogMessagesGroupByTemplateAcrossUsers() {
        final RecordSplitter.Chunk chunk = RecordSplitter.split(SSH);

        final Shapes shapes = shapesOf(chunk, List.of());

        assertThat(chunk.format()).isEqualTo("syslog rfc5424");
        final Map<String, Integer> templates = new LinkedHashMap<>();
        counts(shapes).forEach((signature, count) -> templates.put(signature.split("\\|", 2)[1], count));
        assertThat(templates).isEqualTo(Map.of("Accepted <*> for <*> from <ip> port <n> <n>", 3,
                "Failed password for invalid user admin from <ip> port <n> <n>", 1));
    }

    @Test
    void maskingHidesVariableParts() {
        assertThat(Shapes.mask("user \"Bob Smith\" from 10.1.2.3:443 took 250ms id 0x1f2e3d4c5b6a7980"))
                .containsExactly("user", "<s>", "from", "<ip>", "took", "<n>", "id", "<hex>");
    }

    @Test
    void jsonGroupsByFieldsAndNamingValues() {
        final String text = json(SampleProfiler.map("ts", "t", "user", "a", "action", "login"),
                SampleProfiler.map("ts", "t", "user", "b", "action", "login"),
                SampleProfiler.map("ts", "t", "user", "a", "action", "logout"),
                SampleProfiler.map("ts", "t", "path", "/x", "action", "read"));

        final Shapes shapes = shapesOf(text);

        assertThat(shapes.shapes()).hasSize(3);
        assertThat(shapes.shapes().get("fields:action,ts,user | action=login")).containsEntry("count", 2);
    }

    // Syslog shipped as JSON, whose body is an app prefix, a byte order mark and a JSON log record.
    private static Map<String, Object> wrapped(final String logger, final String message, final String host) {
        final Map<String, Object> record = SampleProfiler.map("timestamp", "t", "loggerName", logger, "level",
                "INFO", "message", message);
        return SampleProfiler.map("timestamp", "t", "hostname", host, "facility", 1L, "severity", 6L, "priority",
                14L, "version", 1L, "body", "keycloak 1234 " + logger + " - ﻿" + PyJson.dumps(record));
    }

    @Test
    void wrappedMessagesAreUnwrappedToTheKindOfEvent() {
        final String text = json(
                wrapped("org.keycloak.events", "type=\"LOGIN\", realmId=r1, userId=u1, ipAddress=192.0.2.1", "kc-1"),
                wrapped("org.keycloak.events", "type=\"LOGIN\", realmId=r1, userId=u2, ipAddress=192.0.2.2", "kc-2"),
                wrapped("org.keycloak.events", "type=\"LOGIN_ERROR\", realmId=r1, error=\"invalid_user_credentials\"",
                        "kc-1"),
                wrapped("org.infinispan.CLUSTER", "ISPN000094: Received new cluster view 42", "kc-1"),
                wrapped("org.infinispan.CLUSTER", "ISPN000094: Received new cluster view 43", "kc-1"));

        final Map<String, Integer> kinds = new LinkedHashMap<>();
        counts(shapesOf(text)).forEach((signature, count) -> kinds.put(signature.split(" > body: ", 2)[1], count));

        final String prefix = "json:level,loggerName,message,timestamp | ";
        assertThat(kinds).isEqualTo(Map.of(
                prefix + "loggerName=org.keycloak.events > message: kv:type=LOGIN", 2,
                prefix + "loggerName=org.keycloak.events > message: kv:type=LOGIN_ERROR", 1,
                // A logger names these, so their free text isn't split further.
                prefix + "loggerName=org.infinispan.CLUSTER", 2));
    }

    @Test
    void freeTextBodiesAreMaskedWhenNothingElseNamesThem() {
        final String text = json(SampleProfiler.map("host", "a", "message", "Accepted password for frank from "
                                                                            + "192.0.2.44"),
                SampleProfiler.map("host", "b", "message", "Accepted password for grace from 192.0.2.45"),
                SampleProfiler.map("host", "a", "message", "Disk /dev/sda1 is 91% full"));

        final Shapes shapes = shapesOf(text);

        assertThat(counts(shapes).values()).containsExactlyInAnyOrder(1, 2);
        assertThat(shapes.shapes()).containsKey("fields:host,message > message: text:|Accepted password for <*> "
                                                + "from <ip>");
    }

    @Test
    void delimitedGroupsByNamingOrLowVarietyColumns() {
        final String named = "time,user,action\n" + IntStream.range(0, 8)
                .mapToObj(i -> "2026-09-28 08:0" + i + ":00,u" + i + "," + "abababab".charAt(i))
                .collect(Collectors.joining("\n"));
        final RecordSplitter.Chunk chunk = RecordSplitter.split(named);

        final Shapes shapes = shapesOf(chunk, List.of());

        assertThat(chunk.namingColumns()).containsExactly("action");
        assertThat(shapes.shapes().keySet()).containsExactlyInAnyOrder("row:action=a", "row:action=b");
        final List<String> rows = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            rows.add("2026-09-28T16:0" + i + ":00Z,user" + i + "," + (i % 2 == 0
                    ? "usb"
                    : "net") + ",/src,/dst");
        }
        final RecordSplitter.Chunk unnamed = RecordSplitter.split(String.join("\n", rows));
        assertThat(unnamed.header()).isNull();
        assertThat(unnamed.namingColumns()).isNotEmpty();
        assertThat(shapesOf(unnamed, List.of()).shapes()).hasSize(2);
    }

    @Test
    void xmlAndKeyValueRecords() {
        final RecordSplitter.Chunk xml = RecordSplitter.split("<audit xmlns=\"urn:x\"><entry><op>read</op><path>/a"
                                                              + "</path></entry><entry><op>delete</op><path>/b"
                                                              + "</path></entry><entry><op>read</op><path>/c</path>"
                                                              + "</entry></audit>");

        assertThat(xml.format()).isEqualTo("xml");
        assertThat(shapesOf(xml, List.of()).shapes()).hasSize(2);
        final String kv = IntStream.range(0, 3).mapToObj(i -> "2026-09-28T15:00:0" + i + "Z fw01 action="
                                                              + List.of("allow", "deny", "allow").get(i)
                                                              + " src=10.0.0." + i + " dst=8.8.8.8 dport=53")
                .collect(Collectors.joining("\n"));
        final RecordSplitter.Chunk pairs = RecordSplitter.split(kv);
        assertThat(pairs.format()).isEqualTo("key=value");
        assertThat(shapesOf(pairs, List.of()).shapes()).hasSize(2);
    }

    @Test
    void knownShapesAreRecognised() {
        final RecordSplitter.Chunk chunk = RecordSplitter.split(SSH);
        final List<String> known = new ArrayList<>(shapesOf(chunk, List.of()).shapes().keySet());

        final Shapes again = shapesOf(chunk, known);

        assertThat(again.shapes().values()).allMatch(shape -> (boolean) shape.get("known"));
    }

    @Test
    void theKindOfEventInsideFreeTextIsReadByItsNamingPairs() {
        // Seen in production: Secret Server's audit events, every one an identical Windows event but for the
        // message in Data ("Event: [User] Action: [Login]"); the survey found one kind of event where there were
        // 32.
        final String line = "<Event xmlns=\"http://schemas.microsoft.com/win/2004/08/events/event\"><System><EventID>"
                            + "1000</EventID></System><EventData><Data>Sep 27 2026 23:48:10 - User: domain.com\\"
                            + "Patel, Priya - [[SecretServer]] Event: [%s] Action: [%s] By User: domain.com\\"
                            + "priya.patel (Item Id: 219); Source IP: 192.0.2.45</Data></EventData></Event>";
        final List<String> lines = new ArrayList<>();
        for (final String[] kind : List.of(new String[]{"User", "Login"}, new String[]{"User", "Login Failed"},
                new String[]{"Secret", "View"}, new String[]{"User", "Login"})) {
            lines.add(String.format(line, kind[0], kind[1]));
        }

        final Map<String, Integer> found = counts(shapesOf(String.join("\n", lines)));

        assertThat(found).hasSize(3);
        assertThat(found.keySet()).anyMatch(k -> k.endsWith("Data: kv:Action=Login Failed, Event=User"));
        // Free text that names nothing isn't masked into a shape of its own (only a field named as a message is).
        final String plain = IntStream.range(0, 3).mapToObj(n -> "<Event><Id>1</Id><Data>The quick brown fox "
                                                                 + "number " + n + " jumped over the lazy dog</Data>"
                                                                 + "</Event>")
                .collect(Collectors.joining("\n"));
        assertThat(shapesOf(plain).shapes().keySet()).containsExactly("fields:Data,Id");
    }

    @Test
    void cefRecordsAreGroupedByTheirHeader() {
        // Masked as text, CEF lines of 5 kinds came out as 3 shapes, one per count of extension keys, the signature
        // ids masked as numbers.
        final List<String[]> kinds = List.of(new String[]{"1001", "User authenticated"},
                new String[]{"1002", "Authentication failed"}, new String[]{"2001", "Web request allowed \\| proxy"});
        final List<String> lines = new ArrayList<>();
        for (int n = 0; n < 12; n++) {
            final String[] kind = kinds.get(n % 3);
            lines.add(String.format("<134>Oct  2 09:00:%02d swg01 CEF:0|Acme|SecureGateway|5.2|%s|%s|3|suser=u%d ",
                    n, kind[0], kind[1], n) + IntStream.range(0, n % 4).mapToObj(i -> "k" + i + "=v")
                              .collect(Collectors.joining(" ")));
        }
        final RecordSplitter.Chunk chunk = RecordSplitter.split(String.join("\n", lines));

        assertThat(chunk.format()).isEqualTo("cef");
        assertThat(shapesOf(chunk, List.of()).shapes().keySet()).containsExactlyInAnyOrder(
                "cef:Acme|SecureGateway|1001|User authenticated", "cef:Acme|SecureGateway|1002|Authentication failed",
                "cef:Acme|SecureGateway|2001|Web request allowed | proxy");
        assertThat(Shapes.cefHeader("no header here")).isNull();
        assertThat(Shapes.cefHeader("CEF:0|Acme|only|three")).isNull();
    }

    static List<String> pythonSurveyed() {
        return List.copyOf(SampleFixtures.pythonSurvey().keySet());
    }

    @ParameterizedTest
    @MethodSource("pythonSurveyed")
    void everySampleIsProfiledSplitAndSurveyedAsThePythonServerDidIt(final String name) {
        // The Python server's own code, run over the formats sources send, the evaluation cases' samples and the
        // heads of each a survey of a big stream reads: the same formats, records and kinds of event.
        final Map<String, Object> python = SampleFixtures.pythonSurvey().get(name);
        final String text = SampleFixtures.corpusText(name);
        final boolean truncated = name.endsWith(":head");

        if (python.containsKey("profile_format")) {
            final Map<String, Object> profile = SampleProfiler.profile(text);
            assertThat(profile.get("format")).as(name).isEqualTo(python.get("profile_format"));
            assertThat(String.valueOf(profile.get("records"))).as(name)
                    .isEqualTo(String.valueOf(python.get("profile_records")));
        } else {
            // Text that starts as JSON and isn't: refused, as the Python server refused it.
            assertThatThrownBy(() -> SampleProfiler.profile(text)).as(name).isInstanceOf(ToolRefusal.class);
        }
        if (!python.containsKey("split_format")) {
            // A JSON line among JSON lines that doesn't parse: the Python splitter raised, and the survey said it
            // couldn't split the part.
            assertThatThrownBy(() -> RecordSplitter.split(text, RecordSplitter.MAX_RECORDS, truncated)).as(name)
                    .isInstanceOf(RuntimeException.class);
            return;
        }
        final RecordSplitter.Chunk chunk = RecordSplitter.split(text, RecordSplitter.MAX_RECORDS, truncated);
        assertThat(chunk.format()).as(name).isEqualTo(python.get("split_format"));
        assertThat((long) chunk.records().size()).as(name).isEqualTo(python.get("split_records"));
        if (python.containsKey("shapes")) {
            final Map<String, Object> shapes = new LinkedHashMap<>();
            shapesOf(chunk, List.of()).shapes().forEach((signature, shape) -> shapes.put(signature,
                    ((Integer) shape.get("count")).longValue()));
            assertThat(shapes).as(name).isEqualTo(python.get("shapes"));
        }
    }

    @Test
    void examplesSayWhereTheyAreAndShareIsAPercentage() {
        final RecordSplitter.Chunk chunk = RecordSplitter.split("{\"a\": 1}\n{\"a\": 2}\n{\"a\": 3}\n");
        final Shapes shapes = new Shapes(List.of(), 2);
        assertThat(shapes.add(chunk, 0, 7, 1)).isTrue();
        assertThat(shapes.add(chunk, 1, 7, 1)).isFalse();
        shapes.add(chunk, 2, 8, 1);

        final Map<String, Object> shape = shapes.shapes().get("fields:a");

        assertThat(shape).containsEntry("count", 3).containsEntry("streams", List.of(7L, 8L));
        assertThat((List<?>) shape.get("examples")).hasSize(2);
        assertThat(((Map<?, ?>) ((List<?>) shape.get("examples")).get(1)).get("location"))
                .isEqualTo(SampleProfiler.map("stream", 7L, "part", 1, "record", 1));
        assertThat(Shapes.share(Map.of("a", 2, "b", 1), "a")).isEqualTo(66.7);
        assertThat(Shapes.share(Map.of(), "a")).isEqualTo(0.0);
    }
}
