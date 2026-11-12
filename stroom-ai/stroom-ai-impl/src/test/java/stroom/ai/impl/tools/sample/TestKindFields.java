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

/// The fields that tell structured records' kinds of event apart, chosen by their values. Seen in testing: a CSV mail
/// log whose kind column was `msg_act`, a name nothing knew, so the survey's kinds shifted from part to part and the
/// draft made one 'other' rule.
class TestKindFields {

    private static final List<String> ACTS = List.of("allow", "quarantine", "reject");

    @Test
    void aFieldNamedLikeKindsIsChosenWithoutAsking() {
        final KindFields.Choice choice = KindFields.choose(mailLog("action", 30));

        assertThat(choice.certain()).isTrue();
        assertThat(choice.fields()).containsExactly("action");
    }

    @Test
    void aFieldOfAnotherNameIsPutToTheUserWithItsValues() {
        final KindFields.Choice choice = KindFields.choose(mailLog("msg_act", 30));

        assertThat(choice.certain()).isFalse();
        assertThat(choice.fields()).containsExactly("msg_act");
        assertThat(choice.candidates()).first().satisfies(candidate -> assertThat(candidate.label())
                .isEqualTo("msg_act (allow, quarantine, reject)"));
        assertThat(choice.options()).contains("msg_act (allow, quarantine, reject)", KindFields.ONE_KIND_ANSWER);
        // Addresses, times, subjects all different and the sender's address are never candidates.
        assertThat(choice.candidates()).extracting(KindFields.Candidate::field)
                .doesNotContain("timestamp", "sender", "recipient", "subject");
    }

    @Test
    void aFieldWithAValueARecordNamesNoKindWhateverItsName() {
        // Seen: a mail log's message id, one a message, taken as a kind each.
        final List<Map<String, Object>> records = mailLog("msg_act", 30);
        for (int i = 0; i < records.size(); i++) {
            records.get(i).put("message_id", "id" + i);
        }

        final KindFields.Choice choice = KindFields.choose(records);

        assertThat(choice.fields()).doesNotContain("message_id");
        assertThat(choice.candidates()).extracting(KindFields.Candidate::field).doesNotContain("message_id");
    }

    @Test
    void codesWithDigitsAreKindsWhenTheyRepeat() {
        final List<Map<String, Object>> records = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            records.add(record("smtp_code", List.of("250", "451", "550").get(i % 3), i));
        }

        assertThat(KindFields.choose(records).fields()).containsExactly("smtp_code");
    }

    @Test
    void recordsWithNoFieldToTellThemApartAreOneKindWithoutAsking() {
        final List<Map<String, Object>> records = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final Map<String, Object> one = new LinkedHashMap<>();
            one.put("timestamp", "2026-11-11T08:00:" + String.format("%02d", i) + "Z");
            one.put("sender", "u" + i + "@example.com");
            records.add(one);
        }

        final KindFields.Choice choice = KindFields.choose(records);

        assertThat(choice.certain()).isTrue();
        assertThat(choice.fields()).isEmpty();
    }

    @Test
    void theUsersAnswerIsAnOptionOneKindOrAFieldTheyName() {
        final KindFields.Choice choice = KindFields.choose(mailLog("msg_act", 30));

        assertThat(KindFields.fromAnswer("msg_act (allow, quarantine, reject)", choice)).containsExactly("msg_act");
        assertThat(KindFields.fromAnswer(KindFields.ONE_KIND_ANSWER, choice)).containsExactly(KindFields.ONE_KIND);
        assertThat(KindFields.fromAnswer("it's 'subject'", choice)).isEmpty();
        assertThat(KindFields.fromAnswer("subject", choice)).containsExactly("subject");
        assertThat(KindFields.fromAnswer("msg_act and subject", choice)).containsExactly("msg_act", "subject");
    }

    @Test
    void aFieldOfMessagesOrAddressesTellsNoKindsApartWhyIsSaid() {
        // Seen in testing (Synology DSM): kind_field "body", whole syslog messages, and an `event` field of sentences
        // naming the user, taken as kinds.
        final List<Map<String, Object>> records = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            final Map<String, Object> one = new LinkedHashMap<>();
            one.put("event", "User [user" + i % 4 + "] logged in from [10.0.0." + i % 4 + "] via [SSH]");
            one.put("body", "<14>1 2026-11-11T20:51:0" + i % 10 + "Z nas01 - - - [synolog@6574 event_id=\"0x0004\"] "
                            + "User [user" + i + "] logged in");
            one.put("event_id", List.of("0x0004", "0x0005", "0x0006").get(i % 3));
            one.put("logid", List.of("0101039424", "0101039425").get(i % 2));
            records.add(one);
        }

        assertThat(KindFields.whyNot("body", records)).hasValueSatisfying(why ->
                assertThat(why).contains("holds whole messages"));
        assertThat(KindFields.whyNot("event", records)).hasValueSatisfying(why ->
                assertThat(why).contains("holds whole messages"));
        assertThat(KindFields.whyNot("nothing", records)).hasValue("the records have no field 'nothing'");
        // Codes are kinds, whatever they look like: a hex id, a FortiGate logid of ten digits.
        assertThat(KindFields.whyNot("event_id", records)).isEmpty();
        assertThat(KindFields.whyNot("logid", records)).isEmpty();
        // A field of addresses names no kinds.
        assertThat(KindFields.messagesNotKinds("src", List.of("from 10.0.0.1", "from 10.0.0.2")))
                .hasValueSatisfying(why -> assertThat(why).contains("addresses or times"));
    }

    @Test
    void aKindFieldArgumentIsReadAsTheUserSaidIt() {
        assertThat(KindFields.parse(null)).isNull();
        assertThat(KindFields.parse(" ")).isNull();
        assertThat(KindFields.parse("'msg_act'")).containsExactly("msg_act");
        assertThat(KindFields.parse("a, b")).containsExactly("a", "b");
        assertThat(KindFields.parse("All one kind of event")).containsExactly(KindFields.ONE_KIND);
        assertThat(KindFields.oneKind(KindFields.parse("all one kind"))).isTrue();
    }

    // A mail log: time, addresses, a subject each its own, and the kind in the field named.
    private static List<Map<String, Object>> mailLog(final String kindField, final int count) {
        final List<Map<String, Object>> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            records.add(record(kindField, ACTS.get(i % 3), i));
        }
        return records;
    }

    private static Map<String, Object> record(final String kindField, final String kind, final int i) {
        final Map<String, Object> one = new LinkedHashMap<>();
        one.put("timestamp", "2026-11-11T08:" + String.format("%02d", i % 60) + ":04Z");
        one.put("sender", "u" + i + "@example.com");
        one.put("recipient", "bob@example.com");
        one.put("subject", "Subject number " + Integer.toString(i * 7919, 36).replaceAll("[0-9]", "x"));
        one.put(kindField, kind);
        return one;
    }
}
