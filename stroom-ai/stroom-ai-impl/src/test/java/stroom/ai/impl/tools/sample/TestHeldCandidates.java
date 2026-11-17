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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// Source type field candidates inside held values: judged as the records' own fields are, the formats each reads,
/// the records it gives no value, readings joined across the formats that name the source differently, and never a
/// field named for kinds of event or the held format alone.
class TestHeldCandidates {

    @Test
    void aHeldReadingSaysTheFormatsItReadsAndTheRecordsWithNoValue() {
        final List<SourceTypeFields.Candidate> found = SourceTypeFields.candidates(HeldFixtures.wrapped(72), false);
        final SourceTypeFields.Candidate joined = found.getFirst();

        assertThat(joined.held()).isTrue();
        assertThat(joined.formats()).containsOnlyKeys(HeldText.JSON, HeldText.KEY_VALUE, HeldText.XML)
                .containsEntry(HeldText.JSON, 36).containsEntry(HeldText.KEY_VALUE, 12)
                .containsEntry(HeldText.XML, 12);
        assertThat(joined.noValue()).isEqualTo(12);
        assertThat(joined.holdsKinds()).isTrue();
        assertThat(joined.separation()).isGreaterThanOrEqualTo(SourceTypeFields.MIN_SEPARATION);
        // Its first part, JSON and key=value joined by name, is offered after it.
        assertThat(found).extracting(SourceTypeFields.Candidate::field).contains("event>logger_name");
        final SourceTypeFields.Candidate byName = found.stream()
                .filter(candidate -> "event>logger_name".equals(candidate.field())).findFirst().orElseThrow();
        assertThat(byName.formats()).containsOnlyKeys(HeldText.JSON, HeldText.KEY_VALUE);
        assertThat(byName.noValue()).isEqualTo(24);
    }

    @Test
    void neitherAKindNamedFieldNorTheFormatAloneNorAWrappersFieldIsOffered() {
        final List<String> fields = SourceTypeFields.candidates(HeldFixtures.wrapped(72), true).stream()
                .map(SourceTypeFields.Candidate::field)
                .toList();

        assertThat(fields).noneMatch(field -> field.contains("action") || field.contains(SourceTypeReading.FORMAT_STEP)
                                              || field.equals("host") || field.equals("system"));
    }

    @Test
    void aFieldNamedForKindsIsNeverASourceTypeEvenWhereItSeparates() {
        // An operation that decides the record's shape, as a source would: still classify_kinds' to tell apart.
        final List<Map<String, Object>> records = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            final Map<String, Object> record = new LinkedHashMap<>();
            final int which = i % 3;
            record.put("event", "{\"operation\": \"op" + which + "\", \"f" + which + "\": \"" + i % 5 + "\", \"g"
                                + which + i % 2 + "\": 1}");
            records.add(record);
        }

        assertThat(SourceTypeFields.candidates(records, true)).extracting(SourceTypeFields.Candidate::field)
                .doesNotContain("event>operation");
        assertThat(SourceTypeFields.mayName("event>operation", Set.of("op0"))).isFalse();
        assertThat(SourceTypeFields.mayName("Operation", Set.of("op0"))).isFalse();
        assertThat(SourceTypeFields.mayName("event>logger_name", Set.of("com.x"))).isTrue();
    }

    @Test
    void heldValuesOfPlainTextOfferNothing() {
        final List<Map<String, Object>> records = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            records.add(Map.of("system", "Teams", "event", "User u" + i % 4 + " did thing " + i % 3 + " today"));
        }

        assertThat(SourceTypeFields.candidates(records, true)).isEmpty();
    }

    @Test
    void aHeldNameMostRecordsOfItsFormatLackIsNotOffered() {
        // `src` is in a fifth of the JSON records only: not what tells their source.
        final List<Map<String, Object>> records = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            final String src = i % 5 == 0
                    ? "\"src\": \"s" + i % 2 + "\", "
                    : "";
            records.add(Map.of("event", "{" + src + "\"a" + i % 3 + "\": 1, \"k" + i % 2 + "\": 2}"));
        }

        assertThat(SourceTypeFields.candidates(records, true)).extracting(SourceTypeFields.Candidate::field)
                .doesNotContain("event>src");
    }

    @Test
    void aHeldFieldMissingFromSomeRecordsLeavesThemWithNoValue() {
        final List<Map<String, Object>> records = new ArrayList<>(HeldFixtures.wrapped(72));
        for (int i = 0; i < 12; i++) {
            records.add(Map.of("system", "Teams", "host", "h1"));
        }

        final SourceTypeFields.Candidate first = SourceTypeFields.candidates(records, false).getFirst();

        assertThat(first.field()).isEqualTo("event>logger_name | event>xml:@logger");
        assertThat(first.noValue()).isEqualTo(24);
    }

    @Test
    void aRecordsOwnFieldComesBeforeAHeldReadingAndUnlikeNamesAreNotJoined() {
        // Seen in an e2e walk: a collector's LogFilePath says the source, and its message holds syslog lines whose
        // key=value bodies name a mailbox; the syslog `app` joined by `mailbox` (user names) was offered first.
        final List<Map<String, Object>> records = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            final Map<String, Object> record = new LinkedHashMap<>();
            final boolean mail = i % 3 != 0;
            record.put("LogFilePath", mail
                    ? "/var/log/mail/maillog"
                    : "/var/log/secure");
            record.put("message", mail
                    ? (i % 2 == 0
                            ? "mailbox=user" + i % 5 + " size=" + i + " status=sent"
                            : "<22>Oct 11 22:14:" + String.format("%02d", i) + " mx1 postfix/smtpd[" + i
                              + "]: connect from host" + i)
                    : "<86>Oct 11 22:14:" + String.format("%02d", i) + " bastion sshd[" + i + "]: Accepted "
                      + (i % 2 == 0
                            ? "password"
                            : "publickey") + " for u" + i % 4);
            records.add(record);
        }

        final List<SourceTypeFields.Candidate> found = SourceTypeFields.candidates(records, false);

        assertThat(found).extracting(SourceTypeFields.Candidate::field).first().isEqualTo("LogFilePath");
        assertThat(found).extracting(SourceTypeFields.Candidate::field)
                .noneMatch(field -> field.contains("mailbox") && field.contains("|"));
    }
}
