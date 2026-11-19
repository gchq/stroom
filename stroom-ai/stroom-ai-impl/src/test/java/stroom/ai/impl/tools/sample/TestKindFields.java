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

/// The fields that tell structured records' kinds of event apart, chosen by their values. Seen in testing: a CSV mail
/// log whose kind column was `msg_act`, a name nothing knew, so the survey's kinds shifted from part to part and the
/// draft made one 'other' rule.
class TestKindFields {

    private static final List<String> ACTS = List.of("allow", "quarantine", "reject");
    // Pega's loggers: Java class names, some well past 80 characters.
    private static final List<String> PEGA_LOGGERS = List.of(
            "com.pega.pegarules.integration.engine.internal.services.soap.SOAPService",
            "com.pega.pegarules.integration.engine.internal.services.rest.RESTServiceRequestProcessor",
            "com.pega.platform.executor.jobscheduler.scheduler.internal.JobSchedulerImplementationService",
            "com.pega.pegarules.session.internal.authorization.context.AuthorizationContextManagerImpl",
            "com.pega.pegarules.data.internal.access.DatabaseImpl",
            "com.pega.pegarules.session.internal.mgmt.Executable");

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

    @Test
    void epochTimesAndTimeNamedFieldsAreNeverOfferedThoughTheyRepeatWithinABatch() {
        // Seen in testing (2026-11-18): an aggregated feed's `time` (epoch milliseconds) and `fields.orig_rt` (epoch
        // nanoseconds) put to the user as the kinds of event; records read at once share their times.
        final List<Map<String, Object>> records = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            final Map<String, Object> one = new LinkedHashMap<>();
            one.put("time", 1_763_452_800_000L + i % 3);
            one.put("received", 1_763_452_800.0e3 + i % 2);
            one.put("fields.orig_rt", "1763452800" + "00000000" + (i % 3));
            one.put("seq", String.valueOf(1_763_452_800L + i % 4));
            one.put("log_ts", List.of("a", "b").get(i % 2));
            one.put("eventDate", List.of("x", "y").get(i % 2));
            one.put("tier", List.of("gold", "silver").get(i % 2));
            records.add(one);
        }

        final KindFields.Choice choice = KindFields.choose(records);

        assertThat(choice.candidates()).extracting(KindFields.Candidate::field).containsExactly("tier");
        assertThat(KindFields.candidates(records).candidates()).extracting(KindFields.Candidate::field)
                .containsExactly("tier");
        assertThat(KindFields.whyNot("fields.orig_rt", records)).hasValueSatisfying(why -> assertThat(why)
                .contains("epoch times"));
        assertThat(KindFields.whyNot("received", records)).hasValueSatisfying(why -> assertThat(why)
                .contains("epoch times"));
        assertThat(KindFields.whyNot("tier", records)).isEmpty();
    }

    @Test
    void timesAreToldByNameAndEpochTimesByTheirRange() {
        assertThat(List.of("time", "@timestamp", "ts", "date", "event_time", "orig_rt", "fields.orig_rt",
                "event>log_ts", "eventTime", "created_date", "time_generated")).allMatch(KindFields::namedAsTime);
        assertThat(List.of("type", "action", "runtime_kind", "msg_act", "status", "logon_type", "category"))
                .noneMatch(KindFields::namedAsTime);
        assertThat(KindFields.epochTimes(List.of("1763452800", "1763452800123", "1763452800123456",
                "1763452800123456789", "1.763452800123E12", "-"))).isTrue();
        // A FortiGate logid, Windows event ids, a count: not times.
        assertThat(KindFields.epochTimes(List.of("0101039424"))).isFalse();
        assertThat(KindFields.epochTimes(List.of("4624", "4625"))).isFalse();
        assertThat(KindFields.epochTimes(List.of("1763452800", "login"))).isFalse();
        assertThat(KindFields.epochTimes(List.of("-"))).isFalse();
    }

    @Test
    void aHeldValuesNameIsSpeltAsTheDraftReadsIt() {
        assertThat(KindFields.asRecordsSpellThem(List.of("event>logger_name"), Set.of("event", "event.logger_name")))
                .containsExactly("event.logger_name");
        assertThat(KindFields.asRecordsSpellThem(List.of("event>Header.Op"), Set.of("event", "event/Header/Op")))
                .containsExactly("event/Header/Op");
    }

    @Test
    void aLoggersQualifiedClassNameIsAKindHoweverLong() {
        // Seen in testing (2026-11-19, Pega via filebeat): event.logger_name, the Java class that logged each event,
        // refused as "whole messages" for a name over 80 characters, though it is one word and repeats.
        final List<Map<String, Object>> records = pegaLog(60);

        assertThat(KindFields.whyNot("event.logger_name", records)).isEmpty();
        assertThat(KindFields.messagesNotKinds("event.logger_name", PEGA_LOGGERS)).isEmpty();
    }

    @Test
    void aLoggersQualifiedClassNameIsOfferedAndHostsNodesAndLevelsAreNot() {
        // Seen in testing (2026-11-19): with event.app held as the source type field, the kind fields offered were
        // event.level, event.alertType, event.source_host and event.nodeId, and never event.logger_name, so the
        // user could only say "all one kind" and the mapping had one real event.
        final KindFields.Choice choice = KindFields.candidates(pegaLog(60));

        assertThat(choice.candidates()).extracting(KindFields.Candidate::field)
                .contains("event.logger_name")
                .doesNotContain("event.source_host", "event.nodeId", "event.level");
    }

    @Test
    void onlyQualifiedNamesAreCodesHoweverLongNotAddressesVersionsTimesOrSentences() {
        assertThat(List.of("com.example.App", "std::io::Error", "org/apache/Thing", "Outer$Inner$Deep",
                "a.b.c", "_x.$y.z1")).allMatch(KindFields::qualifiedName);
        // Two parts, digit-led parts (an address, a version), whitespace, a time, too long.
        assertThat(List.of("com.App", "10.0.0.5", "1.2.3", "v1.2.3", "app.v2.3", "com.example App.Thing",
                "01:00:01", "a." + "b".repeat(200) + ".c", "", "com..App")).noneMatch(KindFields::qualifiedName);

        // Over 80 characters: an address, a version, a sentence, a time are still no kinds.
        final String longVersion = "1.2.3." + "4".repeat(80);
        final String longAddress = "10.0.0.5:" + "8".repeat(80);
        final String sentence = "The operator signed on from the branch office terminal and was then given access";
        final String withTime = "com.example.batch.Job.ran.at.01:00:01.and.finished." + "x".repeat(40);
        assertThat(KindFields.messagesNotKinds("v", List.of(longVersion))).hasValueSatisfying(why ->
                assertThat(why).contains("holds whole messages"));
        assertThat(KindFields.messagesNotKinds("v", List.of(longAddress))).isPresent();
        assertThat(KindFields.messagesNotKinds("v", List.of(sentence))).isPresent();
        assertThat(KindFields.messagesNotKinds("v", List.of(withTime))).isPresent();
        assertThat(KindFields.messagesNotKinds("v", List.of("10.0.0.5"))).hasValueSatisfying(why ->
                assertThat(why).contains("addresses or times"));
    }

    @Test
    void aQualifiedNameIsAKindInAFieldOfAnyNameAndItsLabelShowsItsLastPart() {
        // `component`, a name no kind field has, of class names past 40 characters.
        final List<Map<String, Object>> records = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            records.add(record("component", PEGA_LOGGERS.get(i % 3), i));
        }

        final KindFields.Choice choice = KindFields.choose(records);

        assertThat(choice.certain()).isFalse();
        assertThat(choice.candidates()).first().satisfies(candidate -> {
            assertThat(candidate.field()).isEqualTo("component");
            assertThat(candidate.values()).containsExactlyInAnyOrderElementsOf(PEGA_LOGGERS.subList(0, 3));
            assertThat(candidate.label()).isEqualTo("component (SOAPService, RESTServiceRequestProcessor, "
                                                    + "JobSchedulerImplementationService)");
            // The label, as the form shows it, is still an answer that chooses the field.
            assertThat(KindFields.fromAnswer(candidate.label(), choice)).containsExactly("component");
        });
        assertThat(KindFields.shortExample(PEGA_LOGGERS.getFirst())).isEqualTo("com.pega…SOAPService");
        assertThat(KindFields.shortExample("a.b.c")).isEqualTo("a.b.c");
    }

    @Test
    void fieldsNamingWhereARecordWasHandledAreNeverOffered() {
        final List<String> handled = List.of("server", "pod", "log_level", "collector", "fields.collector",
                "host.name", "agent.id", "beat", "kubernetes.pod_name", "event.nodeId", "event.source_host",
                "sev", "severity", "priority", "forwarder", "shipper", "instance_id", "cluster_node", "dvc_host");
        final List<Map<String, Object>> records = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            final Map<String, Object> one = record("outcome", List.of("ok", "fail").get(i % 2), i);
            final int n = i;
            handled.forEach(field -> one.put(field, List.of("one", "two", "three").get(n % 3)));
            records.add(one);
        }

        assertThat(handled).allMatch(KindFields::namedAsHandledWhere);
        assertThat(List.of("action", "event.logger_name", "msg_act", "nodeType", "operation", "status",
                "event.alertType")).noneMatch(KindFields::namedAsHandledWhere);
        assertThat(KindFields.choose(records).candidates()).extracting(KindFields.Candidate::field)
                .containsExactly("outcome");
        assertThat(KindFields.candidates(records).candidates()).extracting(KindFields.Candidate::field)
                .containsExactly("outcome");
        // The user may still name one: only its values are judged.
        assertThat(KindFields.whyNot("server", records)).isEmpty();
    }

    @Test
    void aKindFieldOnAThirdOfTheRecordsIsACandidateNotTheChoice() {
        // Seen in testing (2026-11-19): Pega's SOAP records' `operation`, on a third of the Integration log's
        // records, settled the choice, no form was shown, and the draft ended in one kind.
        final List<Map<String, Object>> withoutLogger = integrationLog(60);
        withoutLogger.forEach(record -> record.remove("event.logger_name"));

        final KindFields.Choice choice = KindFields.choose(withoutLogger);

        assertThat(choice.certain()).isFalse();
        assertThat(choice.candidates()).extracting(KindFields.Candidate::field).contains("event.operation")
                .doesNotContain("event.level", "event.source_host", "event.nodeId");
        // Beside the logger, on every record: the logger settles it.
        final KindFields.Choice withLogger = KindFields.choose(integrationLog(60));
        assertThat(withLogger.certain()).isTrue();
        assertThat(withLogger.fields()).containsExactly("event.logger_name");
    }

    @Test
    void aFieldsValuesShapeIsAConcernTheUserMayOverruleAndAHardReasonIsNot() {
        final List<Map<String, Object>> records = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            final Map<String, Object> one = record("note", List.of(
                    "The request was received from the web channel and queued",
                    "The request was rejected by the validation rules of the case").get(i % 2), i);
            one.put("seen_at", "server 10.0.0." + i % 3);
            one.put("empty", "-");
            one.put("serial", "s" + i);
            one.put("kind", List.of("queued", "rejected").get(i % 2));
            records.add(one);
        }

        assertThat(KindFields.concern("note", records)).hasValueSatisfying(concern -> {
            assertThat(concern.shape()).isTrue();
            assertThat(concern.said()).contains("holds whole messages");
            assertThat(concern.asked("note")).startsWith("note: its values look long (e.g. The request was")
                    .endsWith("). Tell the kinds apart by it anyway?");
        });
        assertThat(KindFields.concern("seen_at", records)).hasValueSatisfying(concern -> assertThat(concern.shape())
                .isTrue());
        assertThat(KindFields.concern("nothing", records)).hasValueSatisfying(concern -> assertThat(concern.shape())
                .isFalse());
        assertThat(KindFields.concern("empty", records)).hasValueSatisfying(concern -> assertThat(concern.shape())
                .isFalse());
        assertThat(KindFields.concern("serial", records)).hasValueSatisfying(concern -> assertThat(concern.shape())
                .isFalse());
        assertThat(KindFields.concern("kind", records)).isEmpty();
    }

    // Pega's Integration log, flattened as the draft reads it: four loggers, the SOAP service's (a third of the
    // records) with the operation it served, as the generator's serviceMethod; a level, host and node of a few each.
    private static List<Map<String, Object>> integrationLog(final int count) {
        final String soap = "com.pega.pegarules.integration.engine.internal.services.soap.SOAPService";
        final List<String> loggers = List.of(soap,
                "com.pega.pegarules.integration.engine.internal.services.rest.RESTServiceRequestProcessorImpl",
                "com.pega.pegarules.integration.engine.internal.client.http.HTTPClientConnectorRequestProcessor");
        final List<String> nodes = List.of("pega-web-7d9f8c6b5-x2lqk", "pega-web-7d9f8c6b5-m8hrt",
                "pega-batch-5c7b9d8f4-q9wzp");
        final List<Map<String, Object>> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final Map<String, Object> one = new LinkedHashMap<>();
            final String logger = loggers.get(i % loggers.size());
            one.put("event.logger_name", logger);
            one.put("event.level", List.of("INFO", "ERROR").get(i % 2));
            one.put("event.source_host", nodes.get(i % nodes.size()));
            one.put("event.nodeId", nodes.get(i % nodes.size()));
            one.put("event.pegathread", List.of("STANDARD", "BATCH").get(i % 2));
            if (logger.equals(soap)) {
                one.put("event.operation", List.of("CreateCase", "GetCaseStatus", "SubmitClaim").get(i / 3 % 3));
            }
            one.put("event.message", "Request " + i + " handled in " + i * 37 % 900 + " ms");
            records.add(one);
        }
        return records;
    }

    // A Pega log as filebeat ships it, flattened: the logger's class, a level, a host and node of a few each, and an
    // alert type only on PEGA alerts.
    private static List<Map<String, Object>> pegaLog(final int count) {
        final List<String> levels = List.of("INFO", "WARN", "ERROR", "DEBUG");
        final List<String> hosts = List.of("pegaweb01", "pegaweb02", "pegabatch01");
        final List<String> nodes = List.of("pega-web-7d9f8c6b5-x2lqk", "pega-web-7d9f8c6b5-m8hrt",
                "pega-batch-5c7b9d8f4-q9wzp");
        final List<Map<String, Object>> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final Map<String, Object> one = new LinkedHashMap<>();
            one.put("@timestamp", "2026-11-19T01:" + String.format("%02d", i % 60) + ":12.345Z");
            one.put("event.app", List.of("App", "Security", "Integration").get(i % 3));
            one.put("event.logger_name", PEGA_LOGGERS.get(i % PEGA_LOGGERS.size()));
            one.put("event.level", levels.get(i % levels.size()));
            one.put("event.source_host", hosts.get(i % hosts.size()));
            one.put("event.nodeId", nodes.get(i % nodes.size()));
            one.put("event.alertType", i % 5 == 0
                    ? List.of("PEGA0005", "PEGA0001").get(i % 2)
                    : "");
            one.put("event.message", "Request " + i + " completed in " + (i * 37 % 900) + " ms for operator op"
                                     + i % 7);
            records.add(one);
        }
        return records;
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
