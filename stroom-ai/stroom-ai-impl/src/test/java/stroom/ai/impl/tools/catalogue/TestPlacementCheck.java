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

package stroom.ai.impl.tools.catalogue;

import stroom.ai.impl.tools.catalogue.CatalogueValidation.Problem;
import stroom.ai.impl.tools.draft.LocalCheck;
import stroom.ai.impl.tools.generation.ModelReader;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.tools.validation.HeldSchemasWorld;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// Typed placements checked in the catalogue's check (decided by the user 2026-11-21): the path the schema's below
/// the action element, the field one the records hold, repetition that fits, values the element takes once
/// converted; the elements the schema requires that nothing is placed at refuse confirmation; a high share of Data is
/// only worth a look. The records are shaped as Microsoft 365's audit log's (seen in chat 182): JSON whose `event`
/// holds the audit record.
class TestPlacementCheck {

    private static final EventSchema SCHEMA = EventSchema.parse(HeldSchemasWorld.XSD_4_1_0);
    private static final TranslationMapping READING = CatalogueSample.reading("json", null, null, null, null, null,
            null);

    @Test
    void placementsThatFitAreAcceptedWithTheirFillRates() {
        final EventType send = type("Send-Mail", "Send", "Send", List.of(
                placement("Source/User/EmailAddress", "event.UserId"),
                Map.of("path", "Destination/User", "for_each", Map.of("array", "event.Recipients"),
                        "fields", List.of(Map.of("path", "EmailAddress", "item", true))),
                placement("Payload/Email/Subject", "event.Subject")));

        final CatalogueCheckResult check = check(send);

        assertThat(check.problems()).isEmpty();
        assertThat(check.unplaced()).isEmpty();
        assertThat(check.refused()).isFalse();
        assertThat(check.typing().get("Send-Mail").fill()).containsEntry("event.UserId", "3 of 3")
                .containsEntry("event.Subject", "3 of 3");
        assertThat(check.typing().get("Send-Mail").typed()).isEqualTo(3);
    }

    @Test
    void aPathTheSchemaHasntBelowTheActionElementIsRefusedSayingWhatItAllows() {
        final EventType view = type("View-MailItems", "View", "MailItemsAccessed", List.of(
                placement("Email/Topic", "event.Subject")));

        assertThat(said(check(view).problems())).singleElement().asString()
                .contains("placement 'Email/Topic'", "has no child 'Topic'", "Subject");
    }

    @Test
    void aPathToAnElementHoldingOthersOrToDataIsRefused() {
        final EventType view = type("View-MailItems", "View", "MailItemsAccessed", List.of(
                placement("Email/From", "event.UserId"),
                placement("Data", "event.Subject")));

        assertThat(said(check(view).problems())).satisfiesExactlyInAnyOrder(
                problem -> assertThat(problem).contains("Email/From holds elements, not a value",
                        "Email/From/EmailAddress"),
                problem -> assertThat(problem).contains("Data is Data", "kept as Data anyway"));
    }

    @Test
    void aFieldTheRecordsHaventIsRefusedNamingTheNearestRecordField() {
        // Chat 182: affected given as MailboxOwnerUPN, the records' field event.MailboxOwnerUPN.
        final EventType view = new EventType("View-MailItems", "View mail", null, "View", null, null, null,
                "MailboxOwnerUPN", "User/Id", List.of(way("MailItemsAccessed")), false, EventType.AGENT_KNOWLEDGE,
                "high", null, EventType.PROPOSED);
        final EventType misspelt = type("View-Folder", "View", "FolderBind", List.of(
                placement("Email/Subject", "event.Subjet")));

        assertThat(said(check(view, misspelt).problems())).satisfiesExactlyInAnyOrder(
                problem -> assertThat(problem).contains("affected MailboxOwnerUPN",
                        "no record has the field MailboxOwnerUPN", "event.MailboxOwnerUPN"),
                problem -> assertThat(problem).contains("no record has the field event.Subjet", "event.Subject"));
    }

    @Test
    void anEventTypeWithNoSampledRecordHasItsFieldsLeftUncheckedButItsPathsChecked() {
        // One from the documentation: its fields may be ones this sample never holds.
        final EventType documented = type("Delete-Mail", "Delete", "HardDelete", List.of(
                placement("Email/Subject", "event.HardDeletedSubject")));
        final EventType badPath = type("Delete-Folder", "Delete", "FolderDelete", List.of(
                placement("Folder/Heading", "event.FolderName")));

        assertThat(said(check(documented, badPath).problems())).singleElement().asString()
                .contains("Delete-Folder", "has no child 'Heading'");
    }

    @Test
    void severalValuesWhereTheElementTakesOneAreRefusedWithForEachSuggested() {
        final EventType one = type("Send-Mail", "Send", "Send", List.of(
                placement("Source/User/EmailAddress", "event.UserId"),
                placement("Destination/User/EmailAddress", "event.Recipients"),
                placement("Payload/Email/From/EmailAddress", "event.Recipients")));

        assertThat(said(check(one).problems())).satisfiesExactlyInAnyOrder(
                // Destination's User repeats: the first only, without repeat.
                problem -> assertThat(problem).contains("Destination/User/EmailAddress", "holds several values",
                        "repeat: true"),
                problem -> assertThat(problem).contains("Payload/Email/From/EmailAddress", "takes one",
                        "for_each"));
    }

    @Test
    void aForEachOnAnElementTheSchemaHasOnceIsRefused() {
        final EventType send = type("Send-Mail", "Send", "Send", List.of(
                placement("Source/User/EmailAddress", "event.UserId"),
                placement("Destination/User/EmailAddress", "event.UserId"),
                Map.of("path", "Payload/Email/From", "for_each", Map.of("array", "event.Recipients"),
                        "fields", List.of(Map.of("path", "EmailAddress", "item", true)))));

        assertThat(said(check(send).problems())).singleElement().asString()
                .contains("for_each writes Payload/Email/From once per item", "an element that repeats");
    }

    @Test
    void valuesTheElementRefusesAreNamedWithTheFixAndPassOnceConverted() {
        final EventType bad = type("View-Folder", "View", "FolderBind", List.of(
                placement("Folder/Size", "event.Size"),
                placement("Folder/Created", "event.Created")));

        assertThat(said(check(bad).problems())).satisfiesExactlyInAnyOrder(
                problem -> assertThat(problem).contains("Folder/Size", "an xs:nonNegativeInteger", "'12 KB'",
                        "transform: digits", "or leave the field as Data"),
                problem -> assertThat(problem).contains("Folder/Created", "an xs:dateTime", "'01/11/2026 10:00'",
                        "time_format"));

        final EventType converted = type("View-Folder", "View", "FolderBind", List.of(
                withKeys(placement("Folder/Size", "event.Size"), "transform", "digits"),
                withKeys(placement("Folder/Created", "event.Created"), "time_format", "dd/MM/yyyy HH:mm")));
        assertThat(check(converted).problems()).isEmpty();
    }

    @Test
    void aValueOutsideAnEnumerationIsRefusedWithAMapSuggested() {
        final EventType logon = new EventType("Authenticate-Logon", "Logon", null, "Authenticate", "Logon", null,
                "event.UserId", null, null, List.of(way("UserLoggedIn")), false, EventType.AGENT_KNOWLEDGE, "high",
                null, EventType.PROPOSED, null, List.of(placement(placementMap("LogonType", "event.LogonType"))));

        assertThat(said(check(logon).problems())).singleElement().asString()
                .contains("LogonType", "one of", "'Web'", "give map:");
    }

    @Test
    void anElementTheSchemaRequiresThatNothingIsPlacedAtRefusesConfirmationOnly() {
        final EventType update = type("Update-InboxRule", "Update", "UpdateInboxRules", List.of());
        final EventType send = type("Send-Mail", "Send", "Send", List.of(
                placement("Source/User/EmailAddress", "event.UserId")));
        final EventType view = type("View-MailItems", "View", "MailItemsAccessed", List.of());

        final CatalogueCheckResult check = check(update, send, view);

        assertThat(check.problems()).isEmpty();
        // Only the sets these are in are refused: the catalogue's other sets can still be confirmed.
        assertThat(check.refused()).isFalse();
        assertThat(check.unplacedOf(List.of("send-mail"))).singleElement()
                .satisfies(problem -> assertThat(problem.typeId()).isEqualTo("Send-Mail"));
        assertThat(said(check.unplaced())).satisfiesExactlyInAnyOrder(
                problem -> assertThat(problem).contains("event type Update-InboxRule",
                        "EventDetail/Update/After is required by the schema", "give a placement"),
                problem -> assertThat(problem).contains("event type Send-Mail",
                        "EventDetail/Send/Destination is required by the schema", "Destination/User/Id"),
                problem -> assertThat(problem).contains("event type View-MailItems",
                        "EventDetail/View needs one of", "Email"));
        assertThat(check.toJson()).containsKey("unplaced");

        final EventType placedUpdate = type("Update-InboxRule", "Update", "UpdateInboxRules", List.of(
                placement("After/Object/Name", "event.Subject")));
        final EventType placedSend = type("Send-Mail", "Send", "Send", List.of(
                placement("Source/User/EmailAddress", "event.UserId"),
                withKeys(placement("Destination/User/EmailAddress", "event.Recipients"), "repeat", true)));
        final EventType placedView = type("View-MailItems", "View", "MailItemsAccessed", List.of(
                placement("Email/Subject", "event.Subject")));
        final CatalogueCheckResult placed = check(placedUpdate, placedSend, placedView);
        assertThat(placed.unplaced()).isEmpty();
        assertThat(placed.unplacedOf(List.of("Update-InboxRule", "Send-Mail", "View-MailItems"))).isEmpty();
    }

    @Test
    void anElementBelowOnePlacedIsRequiredTooAndTheActionAndActorCount() {
        // Authenticate's Action and its user, written from action and actor, need no placement.
        final EventType logon = new EventType("Authenticate-Logon", "Logon", null, "Authenticate", "Logon", null,
                "event.UserId", null, null, List.of(way("UserLoggedIn")), false, EventType.AGENT_KNOWLEDGE, "high",
                null, EventType.PROPOSED);
        assertThat(check(logon).unplaced()).isEmpty();

        final EventType noAction = new EventType("Authenticate-Logon", "Logon", null, "Authenticate", null, null,
                null, null, null, List.of(way("UserLoggedIn")), false, EventType.AGENT_KNOWLEDGE, "high", null,
                EventType.PROPOSED);
        assertThat(said(check(noAction).unplaced())).satisfiesExactlyInAnyOrder(
                problem -> assertThat(problem).contains("EventDetail/Authenticate/Action is required"),
                problem -> assertThat(problem).contains("EventDetail/Authenticate needs one of User, Device, Group"));
    }

    @Test
    void anElementTheDraftsCommonEntriesWriteBelowTheActionElementCounts() {
        // Eval case 01: the user authenticating written in common, for every Authenticate event.
        final EventType logon = new EventType("Authenticate-Logon", "Logon", null, "Authenticate", "Logon", null,
                null, null, null, List.of(way("UserLoggedIn")), false, EventType.AGENT_KNOWLEDGE, "high", null,
                EventType.PROPOSED);
        final CatalogueCheck.Drafted inCommon = CatalogueCheck.Drafted.of(Map.of("common", List.of(
                Map.of("path", "EventDetail/Authenticate/User/Id", "field", "event.UserId"))));

        assertThat(said(check(logon).unplaced())).singleElement().asString()
                .contains("EventDetail/Authenticate needs one of User, Device, Group");
        assertThat(inCommon.commonPaths()).containsExactly("EventDetail/Authenticate/User/Id");
        assertThat(CatalogueCheck.check(catalogue(logon), sample(), SCHEMA, inCommon).unplaced()).isEmpty();
    }

    @Test
    void mostFieldsKeptAsDataIsOnlyWorthALook() {
        final EventType view = type("View-MailItems", "View", "MailItemsAccessed", List.of(
                placement("Email/Subject", "event.Subject")));

        final CatalogueCheckResult check = check(view);

        assertThat(check.refused()).isFalse();
        final CatalogueCheckResult.Typing typing = check.typing().get("View-MailItems");
        // Who acted and the subject.
        assertThat(typing.typed()).isEqualTo(2);
        assertThat(typing.data()).isGreaterThanOrEqualTo(PlacementCheck.DATA_FIELDS_NOTED);
        assertThat(typing.note()).startsWith("most of its fields are kept as Data");
    }

    @Test
    void valuesAreTransformedAsTheXsltTransformsThem() {
        assertThat(PlacementCheck.transformed("strip_domain", "ACME\\jo")).isEqualTo("jo");
        assertThat(PlacementCheck.transformed("strip_domain", "jo@acme.com")).isEqualTo("jo");
        assertThat(PlacementCheck.transformed("domain", "jo@acme.com")).isEqualTo("acme.com");
        assertThat(PlacementCheck.transformed("domain", "ACME\\jo")).isEqualTo("ACME");
        assertThat(PlacementCheck.transformed("digits", "12 KB")).isEqualTo("12");
        assertThat(PlacementCheck.transformed("trim", "  a   b ")).isEqualTo("a b");
        assertThat(PlacementCheck.transformed(null, "As is")).isEqualTo("As is");
    }

    @Test
    void aPlacementIsReadInTheMappingsFormAndOtherKeysAreRefused() {
        final ModelReader reader = new ModelReader();
        assertThat(Placement.read(reader, Map.of("path", "Email/Subject", "field", "a", "data_name", "Subject"),
                List.of())).isNull();
        assertThat(reader.errors()).singleElement().satisfies(error -> assertThat(error.message())
                .contains("not data_name"));

        final ModelReader twoInputs = new ModelReader();
        assertThat(Placement.read(twoInputs, Map.of("path", "Email/Subject", "field", "a", "value", "b"),
                List.of())).isNull();

        final Placement read = Placement.read(new ModelReader(), Map.of("path", "EventDetail/Send/Payload/Email/To",
                "for_each", Map.of("split", "to", "on", ";"), "fields", List.of(Map.of("path", "EmailAddress",
                        "item", true)), "also_data", true), List.of());
        assertThat(read.pathBelow("Send")).isEqualTo("Payload/Email/To");
        assertThat(read.pathBelow("View")).isNull();
        assertThat(read.writes("Send")).containsExactly("Payload/Email/To/EmailAddress");
        assertThat(read.inputs()).containsExactly("to");
        assertThat(read.alsoData()).isTrue();
        assertThat(read.mappingEntry("Send")).containsEntry("path", "EventDetail/Send/Payload/Email/To")
                .doesNotContainKey("also_data");
    }

    @Test
    void aRequiredElementReadByAnXpathIsPlacedWithItsValuesSaidAsNotChecked() {
        // Eval case 56: the inbox rule's Name read by an xpath out of the Parameters array.
        final Map<String, Object> byXpath = new LinkedHashMap<>();
        byXpath.put("path", "After/Object/Name");
        byXpath.put("xpath", "*[@key='event']/*[@key='Subject']");
        final EventType update = type("Update-InboxRule", "Update", "UpdateInboxRules", List.of(byXpath));

        final CatalogueCheckResult check = check(update);

        assertThat(check.problems()).isEmpty();
        assertThat(check.unplaced()).isEmpty();
        assertThat(said(check.notChecked())).singleElement().asString()
                .contains("placement 'After/Object/Name'", "values aren't checked here", "xpath");
        assertThat(update.placements().getFirst().said()).startsWith("xpath *[@key='event']");
        assertThat(update.placements().getFirst().mappingEntry("Update"))
                .containsEntry("path", "EventDetail/Update/After/Object/Name")
                .containsEntry("xpath", "*[@key='event']/*[@key='Subject']");
    }

    @Test
    void anXpathPlacementsPathIsStillChecked() {
        final Map<String, Object> byXpath = new LinkedHashMap<>();
        byXpath.put("path", "After/Object/Heading");
        byXpath.put("xpath", "*[@key='event']/*[@key='Subject']");
        final Map<String, Object> toData = new LinkedHashMap<>();
        toData.put("path", "After/Object/Data");
        toData.put("xpath", "*[@key='event']/*[@key='Subject']");

        final CatalogueCheckResult check = check(type("Update-InboxRule", "Update", "UpdateInboxRules",
                List.of(byXpath, toData)));

        assertThat(said(check.problems())).satisfiesExactlyInAnyOrder(
                problem -> assertThat(problem).contains("After/Object/Heading", "has no child 'Heading'"),
                problem -> assertThat(problem).contains("After/Object/Data is Data"));
        assertThat(check.notChecked()).isEmpty();
    }

    @Test
    void aLookupPlacementHasItsKeyFieldCheckedAndItsValuesSaidAsNotChecked() {
        final EventType send = type("Send-Mail", "Send", "Send", List.of(
                placement("Source/User/EmailAddress", "event.UserId"),
                Map.of("path", "Destination/User/Id", "lookup", Map.of("map", "USER_TO_ID", "field",
                        "event.UserId"))));
        final EventType misspelt = type("Send-Mail", "Send", "Send", List.of(
                placement("Source/User/EmailAddress", "event.UserId"),
                Map.of("path", "Destination/User/Id", "lookup", Map.of("map", "USER_TO_ID", "field",
                        "event.UsrId"))));

        final CatalogueCheckResult check = check(send);
        assertThat(check.problems()).isEmpty();
        assertThat(check.unplaced()).isEmpty();
        assertThat(said(check.notChecked())).singleElement().asString()
                .contains("placement 'Destination/User/Id'", "values aren't checked here");
        assertThat(send.placements().get(1).inputs()).containsExactly("event.UserId");

        assertThat(said(check(misspelt).problems())).singleElement().asString()
                .contains("no record has the field event.UsrId", "event.UserId");
    }

    @Test
    void anXpathOrALookupIsOneInputAmongTheOthers() {
        final ModelReader both = new ModelReader();
        assertThat(Placement.read(both, Map.of("path", "Email/Subject", "field", "a", "xpath", "b"), List.of()))
                .isNull();
        assertThat(both.errors()).singleElement().satisfies(error -> assertThat(error.message())
                .contains("give one of", "xpath or lookup"));

        final ModelReader badLookup = new ModelReader();
        final Placement noMap = Placement.read(badLookup, Map.of("path", "Destination/User/Id", "lookup",
                Map.of("field", "event.UserId")), List.of());
        // Read as a placement; the mapping's own reading of it refuses the lookup without a map in the check.
        assertThat(noMap).isNotNull();
        assertThat(said(check(type("Send-Mail", "Send", "Send", List.of(
                placement("Source/User/EmailAddress", "event.UserId"),
                Map.of("path", "Destination/User/Id", "lookup", Map.of("field", "event.UserId"))))).problems()))
                .singleElement().asString().contains("placement 'Destination/User/Id'", "map");
    }

    @Test
    void anEventTypeWrittenBeforePlacementsReadsAndWritesAsItDid() {
        final Map<String, Object> old = new LinkedHashMap<>(new EventType("Delete-Records", "Delete records", null,
                "Delete", null, null, null, "record", "Document/Id", List.of(way("purge")), false,
                EventType.AGENT_KNOWLEDGE, "high", null, EventType.PROPOSED).toJson());

        assertThat(old).doesNotContainKey(EventType.PLACEMENTS);
        final EventType read = EventType.read(new ModelReader(), old, List.of());
        assertThat(read.placements()).isEmpty();
        assertThat(read.allPlacements()).singleElement().satisfies(placement -> {
            assertThat(placement.path()).isEqualTo("Document/Id");
            assertThat(placement.field()).isEqualTo("record");
        });
    }


    // --------------------------------------------------------------------------------


    // Three Send, a MailItemsAccessed, a FolderBind, an UpdateInboxRules and a UserLoggedIn, as M365's audit
    // log writes them (their fields nested under event).
    private static List<String> records() {
        final List<String> out = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            out.add(line("Send", "UserId", "jo@acme.com", "Subject", "Report " + i, "Recipients",
                    List.of("al@acme.com", "bo@acme.com"), "ClientIP", "10.0.0." + i, "Workload", "Exchange",
                    "ResultStatus", "Succeeded", "OrganizationId", "org", "RecordType", "2", "Version", "1"));
        }
        out.add(line("MailItemsAccessed", "UserId", "jo@acme.com", "MailboxOwnerUPN", "al@acme.com", "Subject",
                "Hello", "ClientIP", "10.0.0.9", "Workload", "Exchange", "ResultStatus", "Succeeded",
                "OrganizationId", "org", "RecordType", "50", "Version", "1", "LogonType", "Web"));
        out.add(line("FolderBind", "UserId", "jo@acme.com", "Size", "12 KB", "Created", "01/11/2026 10:00",
                "Subject", "x"));
        out.add(line("UpdateInboxRules", "UserId", "jo@acme.com", "Subject", "Rule 1"));
        out.add(line("UserLoggedIn", "UserId", "jo@acme.com", "LogonType", "Web"));
        return out;
    }

    private static String line(final String operation, final Object... keysAndValues) {
        final Map<String, Object> event = new LinkedHashMap<>();
        event.put("Operation", operation);
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            event.put(String.valueOf(keysAndValues[i]), keysAndValues[i + 1]);
        }
        return ToolJson.dumps(Map.of("event", event));
    }

    private static CatalogueCheckResult check(final EventType... types) {
        return CatalogueCheck.check(catalogue(types), sample(), SCHEMA);
    }

    private static Catalogue catalogue(final EventType... types) {
        return new Catalogue(List.of(types), Catalogue.CatchAll.PROPOSED, Catalogue.Scope.NONE);
    }

    private static CatalogueSample sample() {
        return CatalogueSample.read(READING, String.join("\n", records()), null, LocalCheck.JSON_PARSER_CUT, null);
    }

    private static List<String> said(final List<Problem> problems) {
        return problems.stream().map(Problem::problem).toList();
    }

    private static EventType type(final String typeId,
                                  final String detail,
                                  final String operation,
                                  final List<Map<String, Object>> placements) {
        return new EventType(typeId, typeId.replace('-', ' '), null, detail, null, null, "event.UserId", null, null,
                List.of(way(operation)), false, EventType.AGENT_KNOWLEDGE, "high", null, EventType.PROPOSED, null,
                placements.stream().map(TestPlacementCheck::placement).toList());
    }

    private static Placement placement(final Map<String, Object> entry) {
        final ModelReader reader = new ModelReader();
        final Placement read = Placement.read(reader, entry, List.of());
        assertThat(reader.errors()).isEmpty();
        return read;
    }

    private static Map<String, Object> placement(final String path, final String field) {
        return placementMap(path, field);
    }

    private static Map<String, Object> placementMap(final String path, final String field) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("path", path);
        out.put("field", field);
        return out;
    }

    private static Map<String, Object> withKeys(final Map<String, Object> entry, final Object... keysAndValues) {
        final Map<String, Object> out = new LinkedHashMap<>(entry);
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            out.put(String.valueOf(keysAndValues[i]), keysAndValues[i + 1]);
        }
        return out;
    }

    private static Way way(final String operation) {
        return Way.read(new ModelReader(), Map.of("when", List.of(Map.of("field", "event.Operation", "equals",
                operation))), List.of());
    }
}
