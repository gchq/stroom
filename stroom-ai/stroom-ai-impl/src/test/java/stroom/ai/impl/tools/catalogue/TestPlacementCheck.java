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

import stroom.ai.impl.tools.JsonValues;
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
    void anElementRequiredBelowAChoiceTheCatalogueTookNamesTheOtherChoicesThere() {
        // Seen in chat 185 (Haiku): a Move's folder placed through Association, whose required Objects was said to
        // be empty one level deeper each round, for 4 rounds, when Move/Source offers Folder itself.
        final EventType move = type("Move-Folder", "Move", "FolderBind", List.of(
                placement("Source/Association/Objects/Association/Name", "event.Subject"),
                placement("Destination/Folder/Path", "event.Subject")));

        final List<String> unplaced = said(check(move).unplaced());

        assertThat(unplaced).singleElement().asString()
                .contains("EventDetail/Move/Source/Association/Objects/Association/Objects is required by the "
                          + "schema")
                .contains("Source takes one of: Association, Banner, Chat, Configuration, Criteria, Document, "
                          + "Email, File, Folder,")
                .contains("Resource; a placement there may go straight to the one the value is (e.g. "
                          + "Source/<that element>/Id), not through Association")
                .doesNotContain("Source/Banner");
        // The message stays short: the choices capped.
        assertThat(unplaced.getFirst()).hasSizeLessThan(1_200);

        // A placement one level up: the same choices named, those of the shallowest choice the placements took.
        final EventType deeper = type("Move-Folder", "Move", "FolderBind", List.of(
                placement("Source/Association/Name", "event.Subject"),
                placement("Destination/Folder/Path", "event.Subject")));
        assertThat(said(check(deeper).unplaced())).singleElement().asString()
                .contains("Source/Association/Objects", "Source takes one of: Association,");

        // A placement straight to the object: nothing required is left.
        final EventType direct = type("Move-Folder", "Move", "FolderBind", List.of(
                placement("Source/Folder/Path", "event.Subject"),
                placement("Destination/Folder/Path", "event.Subject")));
        assertThat(check(direct).unplaced()).isEmpty();
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
        assertThat(typing.note()).startsWith("most of its fields are kept as Data")
                .endsWith("place each whose meaning an element carries at the element that means it");
    }

    @Test
    void anEventTypeKeepingMostOfItsFieldsAsDataIsToldWhatCouldBeTyped() {
        // Eval case 56: only what the schema requires placed, the rest kept as Data, and nothing told the agent what
        // more it could type.
        final EventType view = type("View-MailItems", "View", "MailItemsAccessed", List.of(
                placement("Email/Subject", "event.Subject")));

        final Map<String, Object> couldType = couldType(check(view), "View-MailItems");
        // Small: it goes to the model with every check (EventSource's elements among them since 2026-11-22).
        assertThat(ToolJson.dumps(couldType).length()).as(ToolJson.dumps(couldType)).isLessThan(1_200);

        assertThat(strings(couldType.get("data")))
                .contains("event.MailboxOwnerUPN (1/1, e.g. 'al@acme.com')", "event.ClientIP (1/1, e.g. '10.0.0.9')")
                // The vendor code recognising it, the field placed and who acted aren't listed.
                .noneMatch(field -> field.startsWith("event.Operation ") || field.startsWith("event.Subject ")
                                    || field.startsWith("event.UserId "));
        // The event type's own fields first, those most records hold alike last (seen in eval case 56: the list began
        // with the fields every audit record carries, and the ones the schema has homes for were cut off).
        final List<String> named = strings(couldType.get("data")).stream()
                .map(field -> field.substring(0, field.indexOf(' ')))
                .toList();
        assertThat(named.getFirst()).isEqualTo("event.MailboxOwnerUPN");
        assertThat(named.indexOf("event.LogonType")).isLessThan(named.indexOf("event.ClientIP"))
                .isLessThan(named.indexOf("event.OrganizationId"));
        assertThat(String.valueOf(couldType.get("schema")))
                // The object it writes, then the action element's other children.
                .startsWith("Email: ")
                .contains("Subject", "Outcome: ", "Success", "other objects: ")
                .doesNotContain("Data", "Meta");
    }

    @Test
    void whatCouldBeTypedIsCapped() {
        final Map<String, Object> event = new LinkedHashMap<>();
        event.put("Operation", "FolderBind");
        event.put("UserId", "jo@acme.com");
        for (int i = 0; i < 30; i++) {
            event.put("Field" + i, "a value long enough to be shortened in the example " + i);
        }
        final EventType view = type("View-Folder", "View", "FolderBind", List.of());
        final CatalogueSample sample = CatalogueSample.read(READING, ToolJson.dumps(Map.of("event", event)), null,
                LocalCheck.JSON_PARSER_CUT, null);

        final Map<String, Object> couldType = couldType(CatalogueCheck.check(catalogue(view), sample, SCHEMA),
                "View-Folder");

        final List<String> data = strings(couldType.get("data"));
        assertThat(data).hasSize(13);
        assertThat(data.getLast()).isEqualTo("… 18 more");
        assertThat(data.getFirst()).endsWith("…')");
        assertThat(ToolJson.dumps(couldType).length()).isLessThan(1_500);
    }

    @Test
    void anEventTypeMostlyTypedIsNotToldWhatCouldBeTyped() {
        final EventType send = type("Send-Mail", "Send", "Send", List.of(
                placement("Source/User/EmailAddress", "event.UserId"),
                Map.of("path", "Destination/User", "for_each", Map.of("array", "event.Recipients"),
                        "fields", List.of(Map.of("path", "EmailAddress", "item", true))),
                placement("Payload/Email/Subject", "event.Subject")));

        final Map<String, Object> typing = typingJson(check(send), "Send-Mail");

        assertThat(typing).doesNotContainKeys("worth_a_look", "could_type");
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
                .contains("give one of", "xpath, lookup, pair (one pair of a name/value list, by its name) or "
                          + "change"));

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


    @Test
    void aPlacementInEventSourceIsCheckedAsTheOthersAre() {
        // Chat 183: event.ClientInfoString ("Client=OWA") names the client application, kept as Data while the
        // user expected EventSource/Client.
        final EventType logon = type("Authenticate-Logon", "Authenticate", "UserLoggedIn", List.of(
                placement("User/Id", "event.UserId"),
                placement("EventSource/Client/Name", "event.ClientInfoString")));
        final EventType misspelt = type("View-Folder", "View", "FolderBind", List.of(
                placement("EventSource/Client/Nmae", "event.ClientInfoString")));

        final CatalogueCheckResult check = CatalogueCheck.check(catalogue(logon, misspelt), clientSample(), SCHEMA);

        assertThat(said(check.problems())).singleElement().asString()
                .contains("View-Folder", "placement 'EventSource/Client/Nmae'", "has no child 'Nmae'");
        assertThat(check.typing().get("Authenticate-Logon").fill()).containsEntry("event.ClientInfoString",
                "1 of 1");
    }

    @Test
    void anEventSourcePlacementOfWhatTheCommonEntriesWriteIsRefused() {
        final EventType logon = type("Authenticate-Logon", "Authenticate", "UserLoggedIn", List.of(
                placement("User/Id", "event.UserId"),
                placement("EventSource/Client/IPAddress", "event.ClientIP")));
        final CatalogueCheck.Drafted inCommon = CatalogueCheck.Drafted.of(Map.of("common", List.of(
                Map.of("path", "EventSource/Client/IPAddress", "field", "event.ClientIP"))));

        assertThat(said(CatalogueCheck.check(catalogue(logon), clientSample(), SCHEMA, inCommon).problems()))
                .singleElement().asString()
                .contains("placement 'EventSource/Client/IPAddress'", "common entries", "every event");
    }

    @Test
    void anEventSourcePlacementOutsideEventSourceOrAtItsRootIsRefused() {
        final EventType logon = type("Authenticate-Logon", "Authenticate", "UserLoggedIn", List.of(
                placement("User/Id", "event.UserId"),
                placement("EventSource/Client", "event.ClientInfoString")));

        assertThat(said(CatalogueCheck.check(catalogue(logon), clientSample(), SCHEMA).problems()))
                .singleElement().asString().contains("EventSource/Client holds elements, not a value",
                        "EventSource/Client/Name");
    }

    @Test
    void whatCouldBeTypedListsEventSourcesElementsNothingWritesYet() {
        final EventType view = type("View-MailItems", "View", "MailItemsAccessed", List.of(
                placement("Email/Subject", "event.Subject")));
        final CatalogueCheck.Drafted inCommon = CatalogueCheck.Drafted.of(Map.of("common", List.of(
                Map.of("path", "EventSource/Client/IPAddress", "field", "event.ClientIP"),
                Map.of("path", "EventSource/System/Name", "value", "M365"))));

        final Map<String, Object> couldType = couldType(CatalogueCheck.check(catalogue(view), sample(), SCHEMA,
                inCommon), "View-MailItems");

        final List<String> schema = List.of(String.valueOf(couldType.get("schema")).split("; "));
        assertThat(schema).anySatisfy(line -> assertThat(line).startsWith("EventSource/Client: ")
                .contains("Name").doesNotContain("IPAddress"));
        assertThat(schema).anySatisfy(line -> assertThat(line).startsWith("EventSource/"));
        assertThat(String.join("\n", schema)).as(String.valueOf(couldType.get("schema")))
                .doesNotContain("System: Name", "System: Environment, Name");
        assertThat(ToolJson.dumps(couldType).length()).isLessThan(1_500);
    }

    @Test
    void anArrayOfObjectsPlacedInALeafIsRefusedWithAGroupOrAnXpathSuggested() {
        // Chat 183: event.Parameters, an array of {Name, Value}, placed at Configuration/Name with repeat: each event
        // got <Name>NameFW to personal</Name>, an item's key and value run together.
        final EventType rule = type("Update-InboxRule", "Update", "UpdateInboxRules", List.of(
                withKeys(placement("After/Configuration/Name", "event.Parameters"), "repeat", true)));

        assertThat(said(CatalogueCheck.check(catalogue(rule), clientSample(), SCHEMA).problems()))
                .singleElement().asString()
                .contains("event.Parameters", "fields of their own (Name, Value)", "for_each",
                        "{array: 'event.Parameters'}", "xpath",
                        "*[@key='event']/*[@key='Parameters']/*[*[@key='Name'] = 'Name']/*[@key='Value']");
    }

    @Test
    void aGroupWritingItsItemsWholeWhereTheyHaveFieldsOfTheirOwnIsRefusedWithAKeySuggested() {
        // Chat 184: after the leaf placement was refused, the agent read each {Name, Value} item whole with
        // item: true, and Configuration/Name got "NameFW to personal" again.
        final EventType rule = type("Update-InboxRule", "Update", "UpdateInboxRules", List.of(
                Map.of("path", "After/Configuration", "for_each", Map.of("array", "event.Parameters"),
                        "fields", List.of(Map.of("path", "Name", "item", true)))));

        assertThat(said(CatalogueCheck.check(catalogue(rule), clientSample(), SCHEMA).problems()))
                .singleElement().asString()
                .contains("event.Parameters's items have fields of their own (Name, Value)",
                        "{path: 'Name', item: true} writes each item whole at After/Configuration/Name",
                        "{path: 'Name', field: 'Name'}");
    }

    @Test
    void aGroupWritingItsItemsWholeIsAcceptedWhereTheItemsAreValues() {
        final EventType rule = type("Update-InboxRule", "Update", "UpdateInboxRules", List.of(
                Map.of("path", "After/Configuration", "for_each", Map.of("array", "event.Names"),
                        "fields", List.of(Map.of("path", "Name", "item", true)))));
        final CatalogueSample names = CatalogueSample.read(READING, line("UpdateInboxRules", "UserId", "jo@acme.com",
                "Names", List.of("FW to personal", "Archive")), null, LocalCheck.JSON_PARSER_CUT, null);

        assertThat(said(CatalogueCheck.check(catalogue(rule), names, SCHEMA).problems()))
                .noneMatch(problem -> problem.contains("fields of their own"));
    }

    @Test
    void whatCouldBeTypedListsTheArraysOfObjectsKeptAsDataWithTheirKeys() {
        // Chat 184: TeamMemberAdded's members, each with UPN and DisplayName, stayed Data: nothing said a for_each
        // group could type them.
        final EventType added = type("Update-TeamMember", "Update", "MemberAdded", List.of());
        final List<String> lines = new ArrayList<>();
        for (int n = 0; n < 2; n++) {
            lines.add(line("MemberAdded", "UserId", "jo@acme.com", "TeamName", "Sales " + n, "ClientIP", "10.0.0.9",
                    "Workload", "MicrosoftTeams", "OrganizationId", "o1", "CommunicationType", "Team", "Members",
                    List.of(member("al@acme.com", "Al"), member("bo@acme.com", "Bo")), "Tags", List.of("a", "b")));
        }
        final CatalogueSample members = CatalogueSample.read(READING, String.join("\n", lines), null,
                LocalCheck.JSON_PARSER_CUT, null);

        final Map<String, Object> couldType = couldType(CatalogueCheck.check(catalogue(added), members, SCHEMA),
                "Update-TeamMember");

        // A list of plain values (event.Tags) isn't one.
        assertThat(strings(couldType.get("arrays"))).as(ToolJson.dumps(couldType)).containsExactly(
                "event.Members: items with UPN, DisplayName, Role — a for_each group on an element the schema "
                + "repeats types each");
    }

    @Test
    void anArrayOfObjectsReadByAGroupIsAccepted() {
        final EventType ok = type("Update-InboxRule", "Update", "UpdateInboxRules", List.of(
                Map.of("path", "After/Configuration", "for_each", Map.of("array", "event.Parameters"),
                        "fields", List.of(Map.of("path", "Name", "field", "Name"),
                                Map.of("path", "Description", "field", "Value")))));

        assertThat(said(CatalogueCheck.check(catalogue(ok), clientSample(), SCHEMA).problems()))
                .noneMatch(problem -> problem.contains("fields of their own"));
    }

    @Test
    void whatCouldBeTypedListsANameValueListApartWithItsPairsNames() {
        // Chat 187: each {Name, Value} pair of event.Parameters typed as an object, its value in a Description, as
        // the group suggestion said each item could be typed.
        final EventType rule = type("Create-InboxRule", "Create", "New-InboxRule", List.of());

        final Map<String, Object> couldType = couldType(CatalogueCheck.check(catalogue(rule), ruleSample(), SCHEMA),
                "Create-InboxRule");

        assertThat(strings(couldType.get("arrays"))).as(ToolJson.dumps(couldType)).containsExactly(
                "event.Members: items with UPN, DisplayName, Role — a for_each group on an element the schema "
                + "repeats types each",
                "event.Parameters: a name/value list; names: Name (4), From (4), MoveToFolder (2) — its pairs are "
                + "Data, each named by its pair's name; a pair whose name means an element goes there by name, "
                + "e.g. {path: 'Configuration/Name', pair: {list: '<list>', name: '<name>'}}");
    }

    @Test
    void aPairReadByItsNameIsAcceptedItsValuesCheckedAndItsNameNoLongerListed() {
        final EventType rule = type("Create-InboxRule", "Create", "New-InboxRule", List.of(Map.of(
                "path", "Configuration/Name", "pair", Map.of("list", "event.Parameters", "name", "Name"))));

        final CatalogueCheckResult check = CatalogueCheck.check(catalogue(rule), ruleSample(), SCHEMA);

        assertThat(said(check.problems())).isEmpty();
        assertThat(strings(couldType(check, "Create-InboxRule").get("arrays")))
                .anyMatch(line -> line.startsWith("event.Parameters: a name/value list; names: From (4), "
                                                  + "MoveToFolder (2) —"));
    }

    @Test
    void aPairIsReadByItsNameWhereTheSampleIsTooSmallToTellTheListByShape() {
        // One inbox rule in the sample (as eval case 61's shown lines): its list read as the pair's name says.
        final EventType rule = type("Update-InboxRule", "Update", "UpdateInboxRules", List.of(Map.of(
                "path", "After/Configuration/Name", "pair", Map.of("list", "event.Parameters", "name", "Name"))));
        final EventType missing = type("Update-InboxRule", "Update", "UpdateInboxRules", List.of(Map.of(
                "path", "After/Configuration/Name", "pair", Map.of("list", "event.Parameters", "name", "Title"))));

        assertThat(said(CatalogueCheck.check(catalogue(rule), clientSample(), SCHEMA).problems())).isEmpty();
        assertThat(said(CatalogueCheck.check(catalogue(missing), clientSample(), SCHEMA).problems()))
                .singleElement().asString().contains("isn't a name/value list holding a pair named 'Title'");
    }

    @Test
    void aPairOfAnEventTypeWithNoSampledRecordHasOnlyItsFormChecked() {
        // Eval case 60: a later build's scope holding no inbox rule, its catalogue's pair placement kept from case 56.
        final EventType rule = type("Create-InboxRule", "Create", "New-InboxRule", List.of(Map.of(
                "path", "Configuration/Name", "pair", Map.of("list", "event.Parameters", "name", "Name"))));
        final EventType badPath = type("Create-InboxRule", "Create", "New-InboxRule", List.of(Map.of(
                "path", "Configuration/Nom", "pair", Map.of("list", "event.Parameters", "name", "Name"))));

        assertThat(said(check(rule).problems())).isEmpty();
        assertThat(said(check(badPath).problems())).singleElement().asString().contains("Nom");
    }

    @Test
    void aPairTheListHasntOrAListOfThingsIsRefusedNamingWhatItHas() {
        final EventType unknownName = type("Create-InboxRule", "Create", "New-InboxRule", List.of(Map.of(
                "path", "Configuration/Name", "pair", Map.of("list", "event.Parameters", "name", "RuleName"))));
        final EventType things = type("Create-InboxRule", "Create", "New-InboxRule", List.of(Map.of(
                "path", "Configuration/Name", "pair", Map.of("list", "event.Members", "name", "Al"))));
        final EventType wrongKeys = type("Create-InboxRule", "Create", "New-InboxRule", List.of(Map.of(
                "path", "Configuration/Name", "pair", Map.of("list", "event.Parameters", "name", "Name",
                        "name_key", "Value", "value_key", "Name"))));

        assertThat(said(CatalogueCheck.check(catalogue(unknownName), ruleSample(), SCHEMA).problems()))
                .singleElement().asString()
                .contains("event.Parameters has no pair named 'RuleName'", "Name (4), From (4), MoveToFolder (2)");
        assertThat(said(CatalogueCheck.check(catalogue(things), ruleSample(), SCHEMA).problems()))
                .singleElement().asString().contains("event.Members isn't a name/value list");
        assertThat(said(CatalogueCheck.check(catalogue(wrongKeys), ruleSample(), SCHEMA).problems()))
                .singleElement().asString().contains("named by Name and hold their values in Value");
    }

    @Test
    void aGroupOverANameValueListIsRefusedWithThePairFormSuggested() {
        // Chat 187: {path: 'Object', for_each: {array: OperationProperties}, fields: [Name <- Name, Description <-
        // Value]}: a setting's value written as an object's description.
        final EventType rule = type("Create-InboxRule", "Create", "New-InboxRule", List.of(
                Map.of("path", "Object", "for_each", Map.of("array", "event.Parameters"),
                        "fields", List.of(Map.of("path", "Name", "field", "Name"),
                                Map.of("path", "Description", "field", "Value")))));

        assertThat(said(CatalogueCheck.check(catalogue(rule), ruleSample(), SCHEMA).problems()))
                .singleElement().asString()
                .contains("event.Parameters is a name/value list", "element meaning something else",
                        "pair: {list: 'event.Parameters', name: 'Name'}");
    }

    @Test
    void aNameValueListPlacedAtALeafIsRefusedWithThePairFormSuggested() {
        final EventType rule = type("Create-InboxRule", "Create", "New-InboxRule", List.of(
                placement("Configuration/Name", "event.Parameters")));

        assertThat(said(CatalogueCheck.check(catalogue(rule), ruleSample(), SCHEMA).problems()))
                .singleElement().asString()
                .contains("event.Parameters is a name/value list",
                        "{path: 'Configuration/Name', pair: {list: 'event.Parameters', name: 'Name'}}");
    }

    @Test
    void whatCouldBeTypedListsAnOldNewListApartWithItsPropertiesAndTheChangeForm() {
        // Decided by the user 2026-11-23: an Update's old/new list is written as Before and After snapshots, each
        // property Data of the object, a property whose name means an element placed by name for both.
        final EventType update = type("Update-User", "Update", "Update user.", List.of());

        final Map<String, Object> couldType = couldType(CatalogueCheck.check(catalogue(update), userSample(), SCHEMA),
                "Update-User");

        assertThat(strings(couldType.get("arrays"))).as(ToolJson.dumps(couldType)).contains(
                "event.ModifiedProperties: an old/new list (old values in OldValue, new in NewValue, by the items' "
                + "order: give old_key and new_key where the keys say otherwise); properties: DisplayName (4), "
                + "JobTitle (2), Department (2) — written as Before and After snapshots, each property Data of the "
                + "object; a property whose name means an element goes there in both by name, e.g. {path: "
                + "'User/Name', change: {list: 'event.ModifiedProperties', name: 'DisplayName'}}");
    }

    @Test
    void theUsersAnswerOfWhichKeyHoldsTheOldValueIsFollowedOverTheItemsOrder() {
        // Decided by the user 2026-11-23: the build's answer (kept by the draft's tool) wins over the key order.
        final EventType update = type("Update-User", "Update", "Update user.", List.of());
        final CatalogueCheck.Drafted answered = CatalogueCheck.Drafted.NONE.withChangeSides(Map.of(
                "event.ModifiedProperties", "NewValue"));

        final Map<String, Object> couldType = couldType(CatalogueCheck.check(catalogue(update), userSample(), SCHEMA,
                answered), "Update-User");

        assertThat(strings(couldType.get("arrays"))).anyMatch(line -> line.startsWith(
                "event.ModifiedProperties: an old/new list (old values in NewValue, new in OldValue, as the user "
                + "said); properties:"));
    }

    @Test
    void aChangeReadByItsNameIsAcceptedInBothSnapshotsAndItsNameNoLongerListed() {
        final EventType update = type("Update-User", "Update", "Update user.", List.of(Map.of(
                "path", "User/Name", "change", Map.of("list", "event.ModifiedProperties", "name", "DisplayName"))));

        final CatalogueCheckResult check = CatalogueCheck.check(catalogue(update), userSample(), SCHEMA);

        assertThat(said(check.problems())).isEmpty();
        assertThat(strings(couldType(check, "Update-User").get("arrays")))
                .anyMatch(line -> line.contains("properties: JobTitle (2), Department (2) —"));
        // The keys swapped where the order says otherwise: kept as given.
        final EventType swapped = type("Update-User", "Update", "Update user.", List.of(Map.of(
                "path", "User/Name", "change", Map.of("list", "event.ModifiedProperties", "name", "DisplayName",
                        "old_key", "NewValue", "new_key", "OldValue"))));
        assertThat(said(CatalogueCheck.check(catalogue(swapped), userSample(), SCHEMA).problems())).isEmpty();
    }

    @Test
    void aChangeWhereTheActionElementHasNoSnapshotsIsRefusedWithAPairOfOneSideSuggested() {
        final EventType authorise = type("Add-RoleMember", "Authorise", "Update user.", List.of(Map.of(
                "path", "AddGroups/Group/Name", "change", Map.of("list", "event.ModifiedProperties", "name",
                        "DisplayName"))));
        final EventType pair = type("Add-RoleMember", "Authorise", "Update user.", List.of(Map.of(
                "path", "AddGroups/Group/Name", "pair", Map.of("list", "event.ModifiedProperties", "name",
                        "DisplayName", "value_key", "NewValue"))));

        assertThat(said(CatalogueCheck.check(catalogue(authorise), userSample(), SCHEMA).problems()))
                .singleElement().asString()
                .contains("change writes the Before and After snapshots of an Update, which Authorise hasn't",
                        "value_key");
        assertThat(said(CatalogueCheck.check(catalogue(pair), userSample(), SCHEMA).problems())).isEmpty();
    }

    @Test
    void aChangeOfAListThatIsntOneOrOfANameItHasntIsRefusedNamingWhatItHas() {
        final EventType things = type("Update-User", "Update", "Update user.", List.of(Map.of(
                "path", "User/Name", "change", Map.of("list", "event.Members", "name", "Al"))));
        final EventType unknownName = type("Update-User", "Update", "Update user.", List.of(Map.of(
                "path", "User/Name", "change", Map.of("list", "event.ModifiedProperties", "name", "DisplayNme"))));
        final EventType wrongKeys = type("Update-User", "Update", "Update user.", List.of(Map.of(
                "path", "User/Name", "change", Map.of("list", "event.ModifiedProperties", "name", "DisplayName",
                        "old_key", "Before"))));
        final EventType leaf = type("Update-User", "Update", "Update user.", List.of(Map.of(
                "path", "User", "change", Map.of("list", "event.ModifiedProperties", "name", "DisplayName"))));

        assertThat(said(CatalogueCheck.check(catalogue(things), userSample(), SCHEMA).problems()))
                .singleElement().asString().contains("event.Members isn't an old/new list");
        assertThat(said(CatalogueCheck.check(catalogue(unknownName), userSample(), SCHEMA).problems()))
                .singleElement().asString()
                .contains("has no property named 'DisplayNme'", "did you mean DisplayName?");
        assertThat(said(CatalogueCheck.check(catalogue(wrongKeys), userSample(), SCHEMA).problems()))
                .singleElement().asString().contains("give those as name_key, old_key and new_key");
        assertThat(said(CatalogueCheck.check(catalogue(leaf), userSample(), SCHEMA).problems()))
                .singleElement().asString().contains("Before/User holds elements, not a value");
    }

    @Test
    void aGroupOrALeafOverAnOldNewListIsRefusedWithTheChangeFormSuggested() {
        final EventType group = type("Update-User", "Update", "Update user.", List.of(
                Map.of("path", "After/Object", "for_each", Map.of("array", "event.ModifiedProperties"),
                        "fields", List.of(Map.of("path", "Name", "field", "Name")))));
        final EventType leaf = type("Update-User", "Update", "Update user.", List.of(
                placement("After/User/Name", "event.ModifiedProperties")));

        assertThat(said(CatalogueCheck.check(catalogue(group), userSample(), SCHEMA).problems()))
                .singleElement().asString()
                .contains("event.ModifiedProperties is an old/new list",
                        "change: {list: 'event.ModifiedProperties', name: 'DisplayName'}");
        assertThat(said(CatalogueCheck.check(catalogue(leaf), userSample(), SCHEMA).problems()))
                .singleElement().asString()
                .contains("event.ModifiedProperties is an old/new list",
                        "change: {list: 'event.ModifiedProperties', name: 'DisplayName'}");
    }

    @Test
    void aGroupOverTargetsReadsEachTargetsOldNewListByNameAndIsSaidOfTheTargets() {
        final EventType none = type("Update-User", "Update", "Update user.", List.of());
        final EventType group = type("Update-User", "Update", "Update user.", List.of(
                Map.of("path", "User", "for_each", Map.of("array", "event.Targets"),
                        "fields", List.of(Map.of("path", "Id", "field", "id"),
                                Map.of("path", "Name", "change", Map.of("list", "modifiedProperties", "name",
                                        "DisplayName"))))));
        final EventType missing = type("Update-User", "Update", "Update user.", List.of(
                Map.of("path", "User", "for_each", Map.of("array", "event.Targets"),
                        "fields", List.of(Map.of("path", "Name", "change", Map.of("list", "modifiedProperties",
                                "name", "Title"))))));

        assertThat(strings(couldType(CatalogueCheck.check(catalogue(none), targetSample(), SCHEMA), "Update-User")
                .get("arrays"))).anyMatch(line -> line.startsWith("event.Targets: items with id, type, "
                                                                  + "modifiedProperties")
                                                  && line.contains("each item's modifiedProperties is an old/new "
                                                                   + "list"));
        assertThat(said(CatalogueCheck.check(catalogue(group), targetSample(), SCHEMA).problems())).isEmpty();
        assertThat(said(CatalogueCheck.check(catalogue(missing), targetSample(), SCHEMA).problems()))
                .singleElement().asString().contains("modifiedProperties has no property named 'Title'");
    }

    @Test
    void aGroupsFieldWritingEachTargetsOldNewListWholeAtATypedElementIsRefusedWithTheChangeForm() {
        // Eval case 76: each target's list is written as a Data per property of its object in both snapshots
        // (2026-11-23); written whole at a typed element its properties' names and values would run together.
        final EventType leaf = type("Update-User", "Update", "Update user.", List.of(
                Map.of("path", "After/User", "for_each", Map.of("array", "event.Targets"),
                        "fields", List.of(Map.of("path", "Id", "field", "id"),
                                Map.of("path", "Name", "field", "modifiedProperties")))));

        assertThat(said(CatalogueCheck.check(catalogue(leaf), targetSample(), SCHEMA).problems()))
                .singleElement().asString()
                .contains("each item's modifiedProperties is an old/new list",
                        "After/User/Name takes one value",
                        "written as Data of each item's object in Before and After",
                        "{path: 'Name', change: {list: 'modifiedProperties', name: '");
    }

    // Four users updated, each with the properties changed (an old/new list, a name's old and new values) and the
    // members told (a list of things), and fields enough kept as Data for what could be typed to be said.
    private static CatalogueSample userSample() {
        final List<String> lines = new ArrayList<>();
        for (int n = 0; n < 4; n++) {
            final List<Object> changes = new ArrayList<>(List.of(change("DisplayName", "User " + n,
                    "User Name " + n)));
            if (n % 2 == 0) {
                changes.add(change("JobTitle", n == 0
                        ? ""
                        : "Analyst", n == 0
                        ? "Analyst"
                        : "Lead"));
            } else {
                changes.add(change("Department", "Ops", "Finance"));
            }
            lines.add(line("Update user.", "UserId", "jo@acme.com", "ClientIP", "10.0.0." + n, "Workload",
                    "Directory", "OrganizationId", "o1", "ResultStatus", "Success", "RecordType", "8",
                    "ModifiedProperties", changes, "Members", List.of(member("al" + n + "@acme.com", "Al " + n))));
        }
        return CatalogueSample.read(READING, String.join("\n", lines), null, LocalCheck.JSON_PARSER_CUT, null);
    }

    // Three users updated as a directory's API writes them: targets, each with its properties changed.
    private static CatalogueSample targetSample() {
        final List<String> lines = new ArrayList<>();
        for (int n = 0; n < 3; n++) {
            final Map<String, Object> target = new LinkedHashMap<>();
            target.put("id", "u-" + n);
            target.put("type", "User");
            target.put("modifiedProperties", List.of(change("DisplayName", "[\"U" + n + "\"]", "[\"User " + n
                                                                                             + "\"]"),
                    change("AccountEnabled", n % 2 == 0
                            ? "[true]"
                            : "[false]", n % 2 == 0
                            ? "[false]"
                            : "[true]")));
            lines.add(line("Update user.", "UserId", "jo@acme.com", "ClientIP", "10.0.0." + n, "Workload",
                    "Directory", "OrganizationId", "o1", "ResultStatus", "Success", "RecordType", "8",
                    "Targets", List.of(target)));
        }
        return CatalogueSample.read(READING, String.join("\n", lines), null, LocalCheck.JSON_PARSER_CUT, null);
    }

    // A property changed, its keys in that order (its name, then its old and new values).
    private static Map<String, Object> change(final String name, final String old, final String now) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("Name", name);
        out.put("OldValue", old);
        out.put("NewValue", now);
        return out;
    }

    // Four inbox rules created, each with its parameters (a name/value list) and the members told (a list of things),
    // and fields enough kept as Data for what could be typed to be said.
    private static CatalogueSample ruleSample() {
        final List<String> lines = new ArrayList<>();
        for (int n = 0; n < 4; n++) {
            final List<Object> parameters = new ArrayList<>(List.of(parameter("Name", "Rule " + n),
                    parameter("From", "boss" + n + "@acme.com")));
            if (n % 2 == 0) {
                parameters.add(parameter("MoveToFolder", "Folder " + n));
            }
            lines.add(line("New-InboxRule", "UserId", "jo@acme.com", "ClientIP", "10.0.0." + n, "Workload",
                    "Exchange", "OrganizationId", "o1", "ResultStatus", "True", "RecordType", "1", "Parameters",
                    parameters, "Members", List.of(member("al" + n + "@acme.com", "Al " + n))));
        }
        return CatalogueSample.read(READING, String.join("\n", lines), null, LocalCheck.JSON_PARSER_CUT, null);
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

    private static Map<String, Object> typingJson(final CatalogueCheckResult check, final String typeId) {
        final Map<String, Object> typing = JsonValues.mapOrEmpty(check.toJson().get("typing"));
        return JsonValues.mapOrEmpty(typing.get(typeId));
    }

    private static Map<String, Object> couldType(final CatalogueCheckResult check, final String typeId) {
        final Map<String, Object> couldType = JsonValues.mapOrEmpty(typingJson(check, typeId).get("could_type"));
        assertThat(couldType).as("could_type of " + typeId).isNotEmpty();
        return couldType;
    }

    private static List<String> strings(final Object value) {
        return JsonValues.strings(value);
    }

    @Test
    void aTypeIdWhoseVerbIsAnotherActionElementIsWorthALookNeverRefused() {
        // Chat 182: Move-MailToDeletedItems written as Update. Update-InboxRule for a rule created is the source's
        // word, written as its verb says, so it is left alone.
        final EventType moved = type("Move-MailToDeletedItems", "Update", "MoveToDeletedItems", List.of());
        final EventType rule = type("Update-InboxRule", "Update", "New-InboxRule", List.of());
        final EventType moveAsMove = type("Move-Mail", "Move", "Move", List.of());
        final EventType notAnElement = type("Unlock-Mailbox", "Update", "Unlock", List.of());
        final EventType network = type("Network-Blocked", "Alert", "Blocked", List.of());

        final CatalogueCheckResult check = check(moved, rule, moveAsMove, notAnElement, network);

        assertThat(check.worthALookOf("move-maildeleteditems")).isEmpty();
        assertThat(check.worthALookOf("Move-MailToDeletedItems")).containsExactly("its TypeId's verb Move is an "
                                                                                + "action element of its own, but "
                                                                                + "its records are written as Update");
        // Read from the schema, not a list of verbs: Network is one of EventDetail's action elements too.
        assertThat(check.worthALookOf("Network-Blocked")).singleElement().asString().contains("verb Network",
                "written as Alert");
        assertThat(check.worthALookOf("Update-InboxRule")).isEmpty();
        assertThat(check.worthALookOf("Move-Mail")).isEmpty();
        assertThat(check.worthALookOf("Unlock-Mailbox")).isEmpty();
        assertThat(check.problems()).noneMatch(problem -> problem.problem().contains("verb"));
        assertThat(check.toJson()).containsKey("worth_a_look");
        // An action element written with a path below it (Network/Permit) is compared by its first step.
        assertThat(CatalogueCheck.verbsOtherThanWritten(List.of(type("Network-Allowed", "Network/Permit", "Allowed",
                List.of())), SCHEMA)).isEmpty();
    }

    // A logon naming its client application, and an inbox rule updated with its parameters (name/value objects).
    private static CatalogueSample clientSample() {
        final List<String> lines = List.of(
                line("UserLoggedIn", "UserId", "jo@acme.com", "ClientIP", "10.0.0.9", "ClientInfoString",
                        "Client=OWA"),
                line("FolderBind", "UserId", "jo@acme.com", "ClientInfoString", "Client=REST;Outlook"),
                line("UpdateInboxRules", "UserId", "jo@acme.com", "Parameters", List.of(
                        parameter("Name", "FW to personal"), parameter("Enabled", "True"))));
        return CatalogueSample.read(READING, String.join("\n", lines), null, LocalCheck.JSON_PARSER_CUT, null);
    }

    // A team member, its keys in that order.
    private static Map<String, Object> member(final String upn, final String name) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("UPN", upn);
        out.put("DisplayName", name);
        out.put("Role", "Member");
        return out;
    }

    // A name/value item, its keys in that order.
    private static Map<String, Object> parameter(final String name, final String value) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("Name", name);
        out.put("Value", value);
        return out;
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
