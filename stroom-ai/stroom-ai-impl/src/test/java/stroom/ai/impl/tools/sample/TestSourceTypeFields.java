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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The source type field of an aggregated feed, chosen by values and shapes: a wrapper's field whose values decide
/// the message's format, or a record's own field deciding its nested fields; never a kind field (each value one
/// shape), a host or an identifier.
class TestSourceTypeFields {

    @Test
    void aWrappersFieldDecidingTheMessagesFormatIsFound() {
        final List<SourceTypeFields.Candidate> found = SourceTypeFields.candidates(wrapped(60), false);

        assertThat(found).extracting(SourceTypeFields.Candidate::field).containsExactly("source");
        assertThat(found.getFirst().separation()).isGreaterThan(0.9);
        assertThat(found.getFirst().holdsKinds()).isTrue();
        assertThat(found.getFirst().label()).startsWith("source (");
    }

    @Test
    void aRecordsOwnFieldDecidingItsNestedFieldsIsFoundAndNotTheKindFieldWithinIt() {
        final List<Map<String, Object>> records = new ArrayList<>();
        final List<List<String>> workloads = List.of(List.of("Files", "FileRead", "FileWritten", "FileShared"),
                List.of("Mail", "MailSent", "MailRead"), List.of("Directory", "LoggedIn", "PasswordReset"));
        for (int i = 0; i < 90; i++) {
            final List<String> workload = workloads.get(i % 3);
            final Map<String, Object> record = new LinkedHashMap<>();
            record.put("Id", "id-" + i);
            record.put("Workload", workload.getFirst());
            record.put("Operation", workload.get(1 + (i / 3) % (workload.size() - 1)));
            record.put("UserId", "user" + (i % 7));
            record.put("event." + workload.getFirst() + "Path", "x" + i);
            record.put("event.Common", "c");
            records.add(record);
        }

        final List<SourceTypeFields.Candidate> found = SourceTypeFields.candidates(records, false);

        assertThat(found).extracting(SourceTypeFields.Candidate::field).first().isEqualTo("Workload");
        assertThat(found).extracting(SourceTypeFields.Candidate::field).doesNotContain("Id", "UserId");
    }

    @Test
    void aKindFieldWhoseValuesAreEachOneShapeIsNotTaken() {
        final List<Map<String, Object>> records = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            final Map<String, Object> record = new LinkedHashMap<>();
            record.put("type", i % 2 == 0
                    ? "login"
                    : "file");
            record.put(i % 2 == 0
                    ? "user"
                    : "path", "v" + i);
            records.add(record);
        }

        assertThat(SourceTypeFields.candidates(records, false)).isEmpty();
        // Said by the user to aggregate sources, it is offered.
        assertThat(SourceTypeFields.candidates(records, true)).extracting(SourceTypeFields.Candidate::field)
                .contains("type");
    }

    @Test
    void aHostFieldIsNeverTakenWhateverItsValues() {
        final List<Map<String, Object>> records = wrapped(60);
        records.forEach(record -> record.put("hostname", record.get("source") + ".example"));

        assertThat(SourceTypeFields.candidates(records, false)).extracting(SourceTypeFields.Candidate::field)
                .doesNotContain("hostname");
    }

    @Test
    void tooFewRecordsChooseNothing() {
        assertThat(SourceTypeFields.candidates(wrapped(6), false)).isEmpty();
    }

    @Test
    void messagesAreReadForTheirFormat() {
        assertThat(SourceTypeFields.format("{\"a\": 1, \"b\": {\"c\": 2}}").format()).isEqualTo("json");
        assertThat(SourceTypeFields.format("{\"a\": 1, \"b\": {\"c\": 2}}").fields()).containsKeys("a", "b.c");
        assertThat(SourceTypeFields.format("{\"a\": 1, ").failed()).isTrue();
        final SourceTypeFields.Held xml = SourceTypeFields.format("Request: <s:Envelope xmlns:s=\"u\"><s:Body/>"
                                                                  + "</s:Envelope>");
        assertThat(xml.named()).isEqualTo("xml s:Envelope");
        assertThat(xml.failed()).isFalse();
        assertThat(SourceTypeFields.format("<s:Envelope xmlns:s=\"u\"><s:Body>").failed()).isTrue();
        assertThat(SourceTypeFields.format("Nov 10 00:00:01 mx01 postfix/smtpd[1866]: connect from x").format())
                .isEqualTo("syslog");
        assertThat(SourceTypeFields.format("Nov 10 00:00:01 mx01 postfix/smtpd[1866]: connect from x").template())
                .isEqualTo("<n> connect from x");
        assertThat(SourceTypeFields.format("a=1 b=2 c=3").format()).isEqualTo("key=value");
        assertThat(SourceTypeFields.format("Data page refreshed").format()).isEqualTo("text");
        assertThat(SourceTypeFields.held(Map.of("a", 1)).format()).isEqualTo(SourceTypeFields.NO_MESSAGE);
    }

    @Test
    void aValueIsReadAsTextWithNilValuesAside() {
        assertThat(SourceTypeFields.valueOf(Map.of("a", 5), "a")).isEqualTo("5");
        assertThat(SourceTypeFields.valueOf(Map.of("a", "-"), "a")).isNull();
        assertThat(SourceTypeFields.valueOf(Map.of("a", List.of()), "a")).isNull();
        assertThat(SourceTypeFields.valueOf(null, "a")).isNull();
    }

    // Records wrapped with a source field, each source's messages in a format (and templates) of its own.
    static List<Map<String, Object>> wrapped(final int count) {
        final List<Map<String, Object>> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final Map<String, Object> record = new LinkedHashMap<>();
            record.put("timestamp", "2026-11-10T00:00:0" + (i % 10) + "Z");
            record.put("app", "acme");
            switch (i % 3) {
                case 0 -> {
                    record.put("source", "Security");
                    record.put("message", "{\"eventType\": \"" + (i % 2 == 0
                            ? "login"
                            : "logout") + "\", \"user\": \"u" + i + "\", \"outcome\": \"failure\"}");
                }
                case 1 -> {
                    record.put("source", "Database");
                    record.put("message", i % 2 == 0
                            ? "Executing query SELECT a FROM b took " + i + "ms"
                            : "Connection pool grew to " + i);
                }
                default -> {
                    record.put("source", "Integration");
                    record.put("message", "Request: <Envelope><Body>" + i + "</Body></Envelope>");
                }
            }
            records.add(record);
        }
        return records;
    }
}
