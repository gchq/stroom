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

import stroom.ai.impl.tools.catalogue.CatalogueCheckResult.Several;
import stroom.ai.impl.tools.catalogue.CatalogueValidation.Problem;
import stroom.ai.impl.tools.draft.LocalCheck;
import stroom.ai.impl.tools.generation.Condition;
import stroom.ai.impl.tools.generation.DumpMode;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.sample.ToolJson;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The catalogue's check against the scope's records, read as processing reads them. The records are shaped as the
/// Pega Platform loggers' (seen in user testing, 2026-11-19): JSON lines whose `event` is a JSON string, the logger
/// in `event.logger_name`; SecurityEventLogger writes several event types told apart by `eventType`, SOAPService
/// requests, responses and faults told apart only by their message.
class TestCatalogueCheck {

    private static final String SECURITY = "com.pega.pegarules.web.security.SecurityEventLogger";
    private static final String SOAP = "com.pega.pegarules.integration.engine.internal.services.soap.SOAPService";
    private static final String REST = "com.pega.pegarules.integration.engine.internal.services.rest."
                                       + "RESTServiceRequestProcessorImpl";
    private static final String EXECUTABLE = "com.pega.pegarules.session.internal.mgmt.Executable";
    private static final String SOURCE_TYPE_FIELD = "event>log_type";
    private static final TranslationMapping READING = CatalogueSample.reading("json", null, null, List.of("event"),
            null, null, null);
    private static final List<String> SECURITY_TYPES = List.of("Access group change", "Operator record change",
            "Password change", "Ruleset unlock");
    private static final List<Integer> SECURITY_COUNTS = List.of(7, 5, 4, 9);

    private int serial;

    @Test
    void eachEventTypeAndEachWayIsCounted() {
        final Catalogue catalogue = catalogue(securityTypes(EventType.AGENT_KNOWLEDGE).toArray(EventType[]::new));

        final CatalogueCheckResult check = check(catalogue, pegaLines());

        assertThat(check.counts()).containsOnlyKeys("Update-AccessGroup", "Update-Operator", "Update-Password",
                "Unlock-Ruleset");
        assertThat(check.counts().get("Update-AccessGroup").records()).isEqualTo(7);
        assertThat(check.counts().get("Update-Password").byWay()).containsExactly(4);
        assertThat(check.counts().get("Unlock-Ruleset").records()).isEqualTo(9);
        assertThat(check.overlaps()).isEmpty();
        assertThat(check.several()).isEmpty();
        assertThat(check.problems()).isEmpty();
        // The SOAP, REST and Executable records: none recognises them.
        assertThat(check.uncatalogued().records()).isEqualTo(21 + 6 + 1);
    }

    @Test
    void aWayOfRecognisingIsCountedOnItsOwnAndTwoWaysOfOneTypeMayOverlap() {
        final EventType soap = type("Process-SOAPService", "SOAP message", EventType.AGENT_KNOWLEDGE,
                way(equalsTo("event.logger_name", SOAP)),
                way(startsWith("event.message", "Request:")));

        final CatalogueCheckResult check = check(catalogue(soap), pegaLines());

        assertThat(check.counts().get("Process-SOAPService").records()).isEqualTo(21);
        assertThat(check.counts().get("Process-SOAPService").byWay()).containsExactly(21, 10);
        assertThat(check.overlaps()).isEmpty();
    }

    @Test
    void oneEventTypeForTheSecurityLoggerMayBeSeveralByEventType() {
        final EventType seeded = type("Process-SecurityEventLogger", "Security event", EventType.DRAFT,
                way(equalsTo("event.logger_name", SECURITY)));

        final CatalogueCheckResult check = check(catalogue(withSoap(seeded)), pegaLines());

        assertThat(check.several()).hasSize(1);
        final Several several = check.several().getFirst();
        assertThat(several.typeId()).isEqualTo("Process-SecurityEventLogger");
        assertThat(several.byField().field()).isEqualTo("event.eventType");
        assertThat(several.byField().values()).containsExactly(Map.entry("Ruleset unlock", 9),
                Map.entry("Access group change", 7), Map.entry("Operator record change", 5),
                Map.entry("Password change", 4));
        // The draft's seed can't be confirmed until it is split or said to be one.
        assertThat(several.blocking()).isTrue();
        assertThat(check.refused()).isTrue();
        assertThat(check.toJson().get("several")).isEqualTo(List.of(Map.of("type_id", "Process-SecurityEventLogger",
                "by_field", Map.of("field", "event.eventType", "values", Map.of("Ruleset unlock", 9,
                        "Access group change", 7, "Operator record change", 5, "Password change", 4)),
                "shapes", List.of(), "blocking", true)));
    }

    @Test
    void anEventTypeOfTheAgentsMayBeSeveralWithoutBlockingAndOneSaidToBeOneIsNot() {
        final EventType agents = type("Process-SecurityEventLogger", "Security event", EventType.AGENT_KNOWLEDGE,
                way(equalsTo("event.logger_name", SECURITY)));
        final EventType saidOne = new EventType("Process-SecurityEventLogger", "Security event", null, "Process",
                null, null, null, null, null, List.of(way(equalsTo("event.logger_name", SECURITY))), false,
                EventType.DRAFT, "high", "every security event is translated alike", EventType.PROPOSED);

        assertThat(check(catalogue(agents), pegaLines()).several()).singleElement()
                .satisfies(several -> assertThat(several.blocking()).isFalse());
        assertThat(check(catalogue(saidOne), pegaLines()).several()).isEmpty();
    }

    @Test
    void theSoapLoggersRequestsResponsesAndFaultsAreToldByTheirMessage() {
        final CatalogueCheckResult check = check(catalogue(soapTypes()), pegaLines());

        assertThat(check.counts().get("Process-SOAPRequest").records()).isEqualTo(10);
        assertThat(check.counts().get("Process-SOAPResponse").records()).isEqualTo(8);
        assertThat(check.counts().get("Alert-SOAPFault").records()).isEqualTo(3);
        assertThat(check.overlaps()).isEmpty();
        assertThat(check.problems()).isEmpty();
    }

    @Test
    void anOverlapIsRefusedWithTheFieldsThatTellTheTypesOtherRecordsApart() {
        // Responses recognised by their start only: a fault is a response too.
        final EventType response = type("Process-SOAPResponse", "SOAP response", EventType.AGENT_KNOWLEDGE,
                way(equalsTo("event.logger_name", SOAP), startsWith("event.message", "Response:")));
        final EventType fault = type("Alert-SOAPFault", "SOAP fault", EventType.AGENT_KNOWLEDGE,
                way(equalsTo("event.logger_name", SOAP), contains("event.message", "Fault>")));
        final List<String> lines = pegaLines();

        final CatalogueCheckResult check = check(catalogue(response, fault), lines);

        assertThat(check.refused()).isTrue();
        assertThat(check.overlaps()).singleElement().satisfies(overlap -> {
            assertThat(overlap.typeIds()).containsExactly("Process-SOAPResponse", "Alert-SOAPFault");
            assertThat(lines.get(overlap.record())).contains("Fault>");
            assertThat(overlap.text()).contains("Fault>");
            assertThat(overlap.records()).isEqualTo(3);
            assertThat(overlap.tellingFields()).isNotEmpty();
            assertThat(overlap.tellingFields().getFirst().field()).isEqualTo("event.status");
            assertThat(overlap.tellingFields().getFirst().values()).containsExactly(
                    Map.entry("Process-SOAPResponse", List.of("Success")),
                    Map.entry("Alert-SOAPFault", List.of("Failure")));
        });
        @SuppressWarnings("unchecked") final Map<String, Object> first =
                ((List<Map<String, Object>>) check.toJson().get("overlaps")).getFirst();
        assertThat(first).containsOnlyKeys("record", "text", "type_ids", "telling_fields");
        assertThat(first.get("telling_fields")).asList().first().isEqualTo(Map.of("field", "event.status",
                "Process-SOAPResponse", List.of("Success"), "Alert-SOAPFault", List.of("Failure")));
    }

    @Test
    void uncataloguedRecordsAreGroupedByShapeWithTheirExamplesAndTheRareOnesAmongThem() {
        final CatalogueCheckResult check = check(catalogue(securityTypes(EventType.AGENT_KNOWLEDGE)
                .toArray(EventType[]::new)), pegaLines());

        assertThat(check.uncatalogued().shapes()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(check.uncatalogued().shapes().stream().mapToInt(CatalogueCheckResult.Shape::records).sum())
                .isEqualTo(28);
        // Commonest first, the rare one (seen once) among them.
        assertThat(check.uncatalogued().shapes().getFirst().records()).isGreaterThanOrEqualTo(
                check.uncatalogued().shapes().getLast().records());
        assertThat(check.uncatalogued().shapes()).anySatisfy(shape -> {
            assertThat(shape.records()).isEqualTo(1);
            assertThat(shape.examples()).singleElement().asString().contains(EXECUTABLE);
        });
        assertThat(check.uncatalogued().shapes()).allSatisfy(shape -> assertThat(shape.signature())
                .startsWith("fields:"));
        // The examples of a shape differ: the first, then the one that differs most from it.
        final CatalogueCheckResult.Shape rest = check.uncatalogued().shapes().stream()
                .filter(shape -> shape.examples().getFirst().contains("RESTService")).findFirst().orElseThrow();
        assertThat(rest.examples()).hasSize(5).doesNotHaveDuplicates();
        @SuppressWarnings("unchecked") final Map<String, Object> compact =
                (Map<String, Object>) check.toJson().get("uncatalogued");
        assertThat(compact.get("records")).isEqualTo(28);
        assertThat((List<?>) compact.get("shapes")).allSatisfy(shape -> assertThat((List<?>) ((Map<?, ?>) shape)
                .get("examples")).hasSizeLessThanOrEqualTo(CatalogueCheckResult.EXAMPLES_SHOWN));
    }

    @Test
    void theCompactFormShowsTheCommonestShapesAndTheRarest() {
        final List<String> lines = new ArrayList<>();
        // Twelve shapes, each one field of its own: the commonest has 12 records, the rarest 1.
        for (int s = 0; s < 12; s++) {
            for (int r = 0; r <= s; r++) {
                lines.add(line(event("other.Logger", "f" + s, "v" + r)));
            }
        }
        final CatalogueCheckResult check = check(catalogue(type("Process-Nothing", "Nothing",
                EventType.AGENT_KNOWLEDGE, way(equalsTo("event.logger_name", "none")))), lines);

        assertThat(check.uncatalogued().shapes()).hasSize(12);
        @SuppressWarnings("unchecked") final List<Map<String, Object>> shown = (List<Map<String, Object>>)
                ((Map<String, Object>) check.toJson().get("uncatalogued")).get("shapes");
        assertThat(shown).hasSize(CatalogueCheckResult.SHAPES_SHOWN);
        assertThat(shown.stream().map(shape -> shape.get("records")).toList())
                .containsExactly(12, 11, 10, 9, 8, 7, 6, 3, 2, 1);
    }

    @Test
    void recordsWhoseHeldValuesDontParseAreCountedWithWhyRatherThanUncatalogued() {
        final List<String> lines = new ArrayList<>(pegaLines());
        final Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("time", 1795050001660L);
        outer.put("host", "pega-web-1");
        // A bad escape in the held JSON.
        outer.put("event", "{\"logger_name\": \"" + SECURITY + "\", \"message\": \"bad \\u12zz\"}");
        lines.add(ToolJson.dumps(outer));

        final CatalogueCheckResult check = check(catalogue(securityTypes(EventType.AGENT_KNOWLEDGE)
                .toArray(EventType[]::new)), lines);

        assertThat(check.unparsed().records()).isEqualTo(1);
        assertThat(check.unparsed().why()).singleElement().asString().startsWith("event: ");
        assertThat(check.uncatalogued().records()).isEqualTo(28);
        assertThat(check.toJson().get("unparsed")).isEqualTo(Map.of("records", 1, "why", check.unparsed().why()));
    }

    @Test
    void aConditionOnAFieldNoRecordHasIsRefusedNamingTheFieldTheRecordsHave() {
        final EventType typo = type("Update-Password", "Password change", EventType.AGENT_KNOWLEDGE,
                way(equalsTo("event.logger_name", SECURITY), equalsTo("event.EventType", "Password change")));

        final CatalogueCheckResult check = check(catalogue(typo), pegaLines());

        assertThat(said(check)).containsExactly("Update-Password#0: no record has the field event.EventType (the "
                                                + "records have event.eventType): name a field as the records "
                                                + "spell it");
    }

    @Test
    void aConditionTheCheckCantEvaluateIsListedAsNotCheckedWithoutRefusing() {
        // Settled with the check and the translation lanes (2026-11-19): an xpath, a dictionary, a record-scope test
        // or a field an extraction gives is said, not blocking, never silently skipped.
        final EventType byXpath = type("Update-Password", "Password change", EventType.AGENT_KNOWLEDGE,
                way(equalsTo("event.logger_name", SECURITY), Condition.fromJson(Map.of("xpath",
                        "string(event)", "contains", "Password"))));

        final CatalogueCheckResult check = check(catalogue(byXpath), pegaLines());

        assertThat(check.problems()).isEmpty();
        assertThat(check.refused()).isFalse();
        assertThat(check.notChecked()).singleElement().satisfies(problem -> {
            assertThat(problem.typeId()).isEqualTo("Update-Password");
            assertThat(problem.way()).isZero();
            assertThat(problem.problem()).contains("can't be checked against the records here");
        });
        assertThat(check.toJson().get("not_checked")).isEqualTo(List.of(check.notChecked().getFirst().toJson()));
    }

    @Test
    void aRegexThatRunsPastItsBudgetIsRefusedAndNotRun() {
        final List<String> lines = new ArrayList<>();
        for (int n = 0; n < 5; n++) {
            lines.add(line(event(SECURITY, "message", "a".repeat(27) + "!")));
        }
        final EventType backtracks = type("Update-Thing", "Thing", EventType.AGENT_KNOWLEDGE,
                way(matches("event.message", "^(\\w+\\s?)*$")));
        final CatalogueSample sample = CatalogueSample.read(READING, String.join("\n", lines), null,
                LocalCheck.JSON_PARSER_CUT, null).withRegexBudget(Duration.ofMillis(50));
        final long started = System.nanoTime();

        final CatalogueCheckResult check = CatalogueCheck.check(catalogue(backtracks), sample, null);

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
        assertThat(said(check)).singleElement().asString().startsWith("Update-Thing#0: the regex of event.message "
                                                                      + "ran past 50 ms over the sample's records "
                                                                      + "and was stopped");
        assertThat(check.counts().get("Update-Thing").records()).isZero();
    }

    @Test
    void aRegexOrContainsOverLongValuesWhereStartsWithOrContainsWouldDoIsRefusedWithThatSuggestion() {
        final List<String> lines = new ArrayList<>();
        for (int n = 0; n < 3; n++) {
            lines.add(line(event(SOAP, "message", "Request: <soapenv:Envelope>" + "x".repeat(12_000))));
            lines.add(line(event(SOAP, "message", "Response: <soapenv:Envelope>" + "y".repeat(12_000))));
        }
        final String text = String.join("\n", lines);

        assertThat(saidOf(text, matches("event.message", "^Request:"))).singleElement().asString()
                .isEqualTo("Process-SOAPRequest#0: the regex of event.message reads values longer than 10,000 "
                           + "characters (6 record(s)), and starts_with 'Request:' recognises the same records: "
                           + "use starts_with");
        assertThat(saidOf(text, matches("event.message", "Request: <soap"))).singleElement().asString()
                .endsWith("contains 'Request: <soap' recognises the same records: use contains");
        assertThat(saidOf(text, contains("event.message", "Request:"))).singleElement().asString()
                .startsWith("Process-SOAPRequest#0: contains over event.message reads values longer than 10,000 "
                            + "characters")
                .endsWith("starts_with 'Request:' recognises the same records: use starts_with");
        // A regex no literal test can stand in for is left to the time budget.
        assertThat(saidOf(text, matches("event.message", "^Re(q|x)uest:"))).isEmpty();
    }

    @Test
    void aRegexOverAValueProcessingCutsIsStillOverALongValue() {
        // Seen in the e2e regex guard: a top-level body past 10,000 characters, cut by processing's JSONParser to
        // exactly 10,000, was taken for a short value and its regex let through.
        final TranslationMapping reading = CatalogueSample.reading("json", null, null, null, null, null, null);
        final List<String> lines = new ArrayList<>();
        for (int n = 0; n < 4; n++) {
            lines.add("{\"action\": \"login\", \"body\": \"" + (n % 2 == 0
                    ? "L".repeat(10_050)
                    : "short") + "\"}");
        }
        final EventType bulk = type("Import-Bulk", "Bulk import", EventType.AGENT_KNOWLEDGE,
                way(matches("body", "^L+$")));

        final CatalogueCheckResult check = CatalogueCheck.check(catalogue(bulk), CatalogueSample.read(reading,
                String.join("\n", lines), null, LocalCheck.JSON_PARSER_CUT, null), null);

        assertThat(said(check)).singleElement().asString().contains("reads values longer than 10,000",
                "use starts_with");
    }

    @Test
    void aConditionThatDependsOnProcessingsStringCutIsRefusedNamingTheSetting() {
        final Map<String, Object> held = new LinkedHashMap<>();
        held.put("padding", "p".repeat(10_050));
        held.put("logger_name", SECURITY);
        final List<String> lines = new ArrayList<>(pegaLines());
        lines.add(line(held));
        final EventType security = type("Process-SecurityEventLogger", "Security event", EventType.AGENT_KNOWLEDGE,
                way(equalsTo("event.logger_name", SECURITY)));

        final CatalogueCheckResult check = check(catalogue(security), lines);

        assertThat(said(check)).singleElement().asString().isEqualTo(
                "Process-SecurityEventLogger#0: whether the condition on event.logger_name holds depends on "
                + "processing's string cut: on 1 record(s) it holds read whole but not as processing reads them, "
                + "its strings cut at stringTruncateLength (10,000 characters): test what comes before the cut, or "
                + "raise the pipeline's stringTruncateLength");
        // Cut short, its held JSON doesn't parse: counted as such.
        assertThat(check.unparsed().records()).isEqualTo(1);
        assertThat(check.counts().get("Process-SecurityEventLogger").records()).isEqualTo(25);
    }

    @Test
    void anEventTypeRecognisedByEveryRecordOfItsLineFormatMustBeItsOnlyOne() {
        final List<String> lines = new ArrayList<>();
        for (int n = 0; n < 4; n++) {
            lines.add(variantLine("syslog", event(SECURITY, "eventType", "Password change")));
            lines.add(variantLine("kv", event(SOAP, "message", "Request: " + n)));
        }
        final EventType syslog = type("Process-Syslog", "Syslog line", EventType.AGENT_KNOWLEDGE,
                way(equalsTo("variant", "syslog")));
        final EventType password = type("Update-Password", "Password change", EventType.AGENT_KNOWLEDGE,
                way(equalsTo("event.eventType", "Password change")));
        final EventType kv = type("Process-SOAPRequest", "SOAP request", EventType.AGENT_KNOWLEDGE,
                way(equalsTo("variant", "kv")));
        final CatalogueSample sample = CatalogueSample.read(READING, String.join("\n", lines), null,
                LocalCheck.JSON_PARSER_CUT, "variant");

        final CatalogueCheckResult shared = CatalogueCheck.check(catalogue(syslog, password, kv), sample, null);
        final CatalogueCheckResult alone = CatalogueCheck.check(catalogue(syslog, kv), sample, null);

        assertThat(said(shared)).containsExactly("Process-Syslog#0: it is recognised by every record of its line "
                                                 + "format (variant is syslog), which only the line format's only "
                                                 + "event type is: Update-Password recognises 4 of its records; "
                                                 + "tell them apart");
        assertThat(shared.overlaps()).hasSize(1);
        assertThat(said(alone)).isEmpty();
        assertThat(alone.counts().get("Process-Syslog").records()).isEqualTo(4);
    }

    @Test
    void theCataloguesOwnProblemsAndItsOrderAreGiven() {
        final EventType loose = type("Process-SOAPService", "SOAP message", EventType.AGENT_KNOWLEDGE,
                way(equalsTo("event.logger_name", SOAP)));
        final EventType onSourceType = type("Process-Integration", "Integration", EventType.AGENT_KNOWLEDGE,
                way(equalsTo("event.log_type", "Integration"), equalsTo("event.logger_name", REST)));

        final CatalogueCheckResult check = check(catalogue(loose, onSourceType), pegaLines());

        assertThat(check.order()).containsExactly("Process-Integration", "Process-SOAPService");
        assertThat(said(check)).singleElement().asString().startsWith("Process-Integration#0: condition on "
                                                                      + "event.log_type, the build's source type "
                                                                      + "field");
        assertThat(check.toJson()).containsOnlyKeys("counts", "overlaps", "uncatalogued", "unparsed", "several",
                "problems", "order");
        assertThat(check.toJson().get("problems")).isEqualTo(List.of(Map.of("type_id", "Process-Integration", "way",
                0, "problem", check.problems().getFirst().problem())));
    }

    @Test
    void aSoleEventTypeIsRecognisedByEveryRecord() {
        final CatalogueCheckResult check = check(catalogue(type("Process-PegaEvent", "Pega event",
                EventType.AGENT_KNOWLEDGE)), pegaLines());

        assertThat(check.counts().get("Process-PegaEvent").records()).isEqualTo(25 + 21 + 6 + 1);
        assertThat(check.counts().get("Process-PegaEvent").byWay()).isEmpty();
        assertThat(check.uncatalogued().records()).isZero();
        assertThat(check.problems()).isEmpty();
    }

    @Test
    void conditionsAreCheckedOverXmlRecordsByLocalName() {
        final String xml = "<Events xmlns=\"urn:x\"><Event><Op>Logon</Op><User>a</User></Event>"
                           + "<Event><Op>Logoff</Op><User>b</User></Event><Event><Op>Logon</Op><User>c</User></Event>"
                           + "</Events>";
        final TranslationMapping reading = CatalogueSample.reading("xml", "Event", "urn:x", null, null, null, null);
        final EventType logon = type("Authenticate-Logon", "Logon", EventType.AGENT_KNOWLEDGE,
                way(equalsTo("Op", "Logon")));

        final CatalogueCheckResult check = CatalogueCheck.check(catalogue(logon),
                CatalogueSample.read(reading, xml, null, 0, null), null);

        assertThat(check.counts().get("Authenticate-Logon").records()).isEqualTo(2);
        assertThat(check.uncatalogued().records()).isEqualTo(1);
        assertThat(check.uncatalogued().shapes().getFirst().examples().getFirst()).contains("Logoff");
    }

    @Test
    void aConditionOnAnArrayFieldHoldsWhenAnyItemDoes() {
        final List<String> lines = List.of("{\"category\": [\"authentication\", \"iam\"]}",
                "{\"category\": [\"network\"]}");
        final TranslationMapping reading = CatalogueSample.reading("json", null, null, null, null, null, null);
        final EventType auth = type("Authenticate-User", "Authentication", EventType.AGENT_KNOWLEDGE,
                way(equalsTo("category", "authentication")));

        final CatalogueCheckResult check = CatalogueCheck.check(catalogue(auth),
                CatalogueSample.read(reading, String.join("\n", lines), null, LocalCheck.JSON_PARSER_CUT, null),
                null);

        assertThat(check.counts().get("Authenticate-User").records()).isEqualTo(1);
    }

    @Test
    void threeHundredEventTypesOverThousandsOfRecordsAreCheckedInSeconds() {
        final int types = 300;
        final int records = 6_000;
        final List<String> lines = new ArrayList<>(records);
        for (int n = 0; n < records; n++) {
            final int type = n % types;
            lines.add(line(event("com.example.Logger" + (type / 2), "eventType", "Op" + (type % 2), "userid",
                    "user" + (n % 37), "message", "Did thing " + type + " for user " + n + " " + "z".repeat(200))));
        }
        final List<EventType> catalogued = new ArrayList<>();
        for (int type = 0; type < types; type++) {
            catalogued.add(type("Process-Thing" + type, "Thing " + type, EventType.AGENT_KNOWLEDGE,
                    way(equalsTo("event.logger_name", "com.example.Logger" + (type / 2)),
                            equalsTo("event.eventType", "Op" + (type % 2)),
                            matches("event.message", "^Did thing " + type + " ")),
                    way(equalsTo("event.logger_name", "com.example.Logger" + (type / 2)),
                            contains("event.message", "thing " + type + " for"))));
        }
        final long started = System.nanoTime();

        final CatalogueCheckResult check = check(catalogue(catalogued.toArray(EventType[]::new)), lines);

        final Duration took = Duration.ofNanos(System.nanoTime() - started);
        assertThat(took).isLessThan(Duration.ofSeconds(10));
        assertThat(check.counts()).hasSize(types);
        assertThat(check.counts().values()).allSatisfy(count -> assertThat(count.records()).isEqualTo(20));
        assertThat(check.overlaps()).isEmpty();
        assertThat(check.uncatalogued().records()).isZero();
        assertThat(check.problems()).isEmpty();
    }

    // The scope's records: the security logger's four event types, the SOAP logger's requests, responses and faults,
    // REST requests, and one rare record.
    private List<String> pegaLines() {
        final List<String> lines = new ArrayList<>();
        for (int t = 0; t < SECURITY_TYPES.size(); t++) {
            for (int n = 0; n < SECURITY_COUNTS.get(t); n++) {
                final String user = "operator" + (serial++);
                final String target = "target" + (serial++);
                lines.add(line(event(SECURITY, "log_type", "Security", "level", "INFO", "userid", user,
                        "eventCategory", "Security administration event", "eventType", SECURITY_TYPES.get(t),
                        "target", target, "message", SECURITY_TYPES.get(t) + " by " + user + " for " + target)));
            }
        }
        for (int n = 0; n < 10; n++) {
            lines.add(line(soap("Success", "Request: <soapenv:Envelope><soapenv:Body><clm:SubmitClaim><clm:Id>" + n
                                           + "</clm:Id></clm:SubmitClaim></soapenv:Body></soapenv:Envelope>")));
        }
        for (int n = 0; n < 8; n++) {
            lines.add(line(soap("Success", "Response: <soapenv:Envelope><soapenv:Body><clm:SubmitClaimResponse>"
                                           + "<clm:CaseId>C-" + n + "</clm:CaseId></clm:SubmitClaimResponse>"
                                           + "</soapenv:Body></soapenv:Envelope>")));
        }
        for (int n = 0; n < 3; n++) {
            lines.add(line(soap("Failure", "Response: <soapenv:Envelope><soapenv:Body><soapenv:Fault><faultcode>"
                                           + "Server." + n + "</faultcode></soapenv:Fault></soapenv:Body>"
                                           + "</soapenv:Envelope>")));
        }
        for (int n = 0; n < 6; n++) {
            lines.add(line(event(REST, "log_type", "Integration", "level", "INFO", "httpMethod", n % 2 == 0
                    ? "GET"
                    : "POST", "path", "/prweb/api/v1/cases/" + n, "statusCode", 200 + n, "message",
                    "GET /prweb/api/v1/cases/" + n + " returned 200 in " + (n * 100) + " ms")));
        }
        lines.add(line(event(EXECUTABLE, "log_type", "Integration", "level", "WARN", "activity", "Pega-Run",
                "message", "Activity ran long")));
        return lines;
    }

    private Map<String, Object> soap(final String status, final String message) {
        return event(SOAP, "log_type", "Integration", "level", "INFO", "userid", "user" + (serial++ % 3),
                "CorrelationId", "c-" + (serial++), "serviceName", "ClaimsService", "serviceMethod", "SubmitClaim",
                "status", status, "message", message);
    }

    private static Map<String, Object> event(final String logger, final Object... keysAndValues) {
        final Map<String, Object> event = new LinkedHashMap<>();
        event.put("logger_name", logger);
        for (int i = 0; i < keysAndValues.length; i += 2) {
            event.put(String.valueOf(keysAndValues[i]), keysAndValues[i + 1]);
        }
        return event;
    }

    // A record as the shipper writes it: its own fields, and the source's as a JSON string.
    private static String line(final Map<String, Object> event) {
        final Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("time", 1795050001660L);
        outer.put("host", "pega-web-1");
        outer.put("event", ToolJson.dumps(event));
        return ToolJson.dumps(outer);
    }

    private static String variantLine(final String variant, final Map<String, Object> event) {
        final Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("variant", variant);
        outer.put("event", ToolJson.dumps(event));
        return ToolJson.dumps(outer);
    }

    private static CatalogueCheckResult check(final Catalogue catalogue, final List<String> lines) {
        return CatalogueCheck.check(catalogue, CatalogueSample.read(READING, String.join("\n", lines), null,
                LocalCheck.JSON_PARSER_CUT, null), null);
    }

    private static List<String> saidOf(final String text, final Condition condition) {
        final EventType request = type("Process-SOAPRequest", "SOAP request", EventType.AGENT_KNOWLEDGE,
                way(condition));
        return said(CatalogueCheck.check(catalogue(request), CatalogueSample.read(READING, text, null, 0, null),
                null));
    }

    private static List<String> said(final CatalogueCheckResult check) {
        final List<String> out = new ArrayList<>();
        for (final Problem problem : check.problems()) {
            out.add(problem.typeId() + (problem.way() >= 0
                    ? "#" + problem.way()
                    : "") + ": " + problem.problem());
        }
        return out;
    }

    private static List<EventType> securityTypes(final String basis) {
        final List<String> typeIds = List.of("Update-AccessGroup", "Update-Operator", "Update-Password",
                "Unlock-Ruleset");
        final List<EventType> out = new ArrayList<>();
        for (int t = 0; t < SECURITY_TYPES.size(); t++) {
            out.add(type(typeIds.get(t), SECURITY_TYPES.get(t), basis, way(equalsTo("event.logger_name", SECURITY),
                    equalsTo("event.eventType", SECURITY_TYPES.get(t)))));
        }
        return out;
    }

    private static EventType[] soapTypes() {
        return new EventType[]{
                type("Process-SOAPRequest", "SOAP request", EventType.AGENT_KNOWLEDGE,
                        way(equalsTo("event.logger_name", SOAP), startsWith("event.message", "Request:"))),
                type("Process-SOAPResponse", "SOAP response", EventType.AGENT_KNOWLEDGE,
                        way(equalsTo("event.logger_name", SOAP), startsWith("event.message", "Response:"),
                                not(contains("event.message", "Fault>")))),
                type("Alert-SOAPFault", "SOAP fault", EventType.AGENT_KNOWLEDGE,
                        way(equalsTo("event.logger_name", SOAP), contains("event.message", "Fault>")))};
    }

    private static EventType[] withSoap(final EventType type) {
        final List<EventType> types = new ArrayList<>(List.of(type));
        types.addAll(List.of(soapTypes()));
        return types.toArray(EventType[]::new);
    }

    private static Catalogue catalogue(final EventType... types) {
        return new Catalogue(List.of(types), null, new Catalogue.Scope(SOURCE_TYPE_FIELD,
                List.of("Integration", "Security")));
    }

    private static EventType type(final String typeId, final String title, final String basis, final Way... ways) {
        return new EventType(typeId, title, null, "Process", null, null, null, null, null, List.of(ways), false,
                basis, "high", null, EventType.PROPOSED);
    }

    private static Way way(final Condition... conditions) {
        return new Way(List.of(conditions), List.of());
    }

    private static Condition equalsTo(final String field, final String value) {
        return Condition.fromJson(Map.of("field", field, "equals", value));
    }

    private static Condition startsWith(final String field, final String value) {
        return Condition.fromJson(Map.of("field", field, "starts_with", value));
    }

    private static Condition contains(final String field, final String value) {
        return Condition.fromJson(Map.of("field", field, "contains", value));
    }

    private static Condition matches(final String field, final String regex) {
        return Condition.fromJson(Map.of("field", field, "matches", regex));
    }

    private static Condition not(final Condition condition) {
        final Map<String, Object> json = new LinkedHashMap<>(condition.toJson(DumpMode.COMPACT));
        json.put("not", true);
        return Condition.fromJson(json);
    }
}
