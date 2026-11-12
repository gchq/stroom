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

package stroom.ai.impl.tools.generation;

import stroom.ai.impl.tools.sample.SourceTypeRows;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/// An aggregated feed's translation modules, generated and run together through Saxon as the dispatcher's one
/// stylesheet: two source type groups, the feed's common module and its helpers, and the dispatcher routing records
/// by the registry's rows (exact and pattern, in order), passing the rest over and counting them; the checks of a
/// group module, of imports, and of patterns the two regex dialects read differently.
class TestSourceTypeModules extends XsltRuns {

    private static final String COMMON_HREF = "uuid='c0000000-0000-0000-0000-000000000001', name='PEGA common'";
    private static final String HELPERS_HREF = "uuid='c0000000-0000-0000-0000-000000000002', name='PEGA helpers'";
    private static final String SECURITY_HREF = "uuid='c0000000-0000-0000-0000-000000000003', name='PEGA security'";
    private static final String INTEGRATION_HREF = "uuid='c0000000-0000-0000-0000-000000000004', "
                                                   + "name='PEGA integration'";
    private static final String SOAP = "Request: <soapenv:Envelope xmlns:soapenv=\"urn:soap\" xmlns:cus=\"urn:cus\">"
                                       + "<soapenv:Header><cus:CorrelationId>c-1</cus:CorrelationId>"
                                       + "</soapenv:Header></soapenv:Envelope>";
    private static final String TRUNCATED = "Request: <soapenv:Envelope xmlns:soapenv=\"urn:soap\"><soapenv:Header><c";

    @Test
    void groupsCommonHelpersAndTheDispatcherRunAsOneStylesheet() {
        final Modules modules = modules();
        // A group: templates in its own mode, its names prefixed, its imports the common and helpers modules', no
        // stylesheet-level declarations and no inline helpers.
        final String security = modules.security();
        assertThat(security).contains("<xsl:import href=\"" + COMMON_HREF + "\"/>",
                        "<xsl:import href=\"" + HELPERS_HREF + "\"/>",
                        "<xsl:template match=\"*\" mode=\"pega_security\">",
                        "<xsl:call-template name=\"pega_common_event_time\"/>",
                        "<xsl:with-param name=\"system_name\" select=\"'PEGA Security'\"/>")
                .doesNotContain("name=\"mcp:json-to-xml\"", "match=\"/\"", "<xsl:output");
        assertThat(ModuleChecks.groupProblems(security)).isEmpty();
        assertThat(ModuleChecks.marker(security)).hasValueSatisfying(marker -> {
            assertThat(marker.kind()).isEqualTo("group");
            assertThat(marker.mode()).isEqualTo("pega_security");
            assertThat(marker.sourceTypes()).containsExactly("PegaSecurityEvent");
        });
        // The common module: a named template a part, the System part taking the group's System Name.
        assertThat(modules.common()).contains("<xsl:template name=\"pega_common_event_time\">",
                "<xsl:template name=\"pega_common_system\">", "<xsl:param name=\"system_name\" select=\"'PEGA'\"/>",
                "<xsl:template name=\"pega_common_device\">");
        assertThat(modules.helpers()).contains("name=\"mcp:json-to-xml\"", "name=\"mcp:parse-xml\"",
                "name=\"mcp:zone\"");

        final List<String> logged = new ArrayList<>();
        final XmlElement events = transform(modules.dispatcher(), records(), modules.imports(), logged);
        assertThat(invalid(events, XSD)).isEmpty();
        final List<XmlElement> written = children(events, "Event");
        // In-scope records only: the security event, and both integration records (the truncated one's values left
        // out); PegaDB (parked) and a value no row matches passed over.
        assertThat(written).hasSize(4);
        final XmlElement login = written.getFirst();
        // The rule mapping its own time writes its own; the common module's System written with the group's name.
        assertThat(text(login, "EventTime/TimeCreated")).isEqualTo("2026-11-10T00:00:01.000Z");
        assertThat(text(login, "EventSource/System/Name")).isEqualTo("PEGA Security");
        assertThat(text(login, "EventSource/System/Environment")).isEqualTo("Prod");
        assertThat(text(login, "EventSource/Device/HostName")).isEqualTo("app01");
        assertThat(text(login, "EventDetail/Authenticate/User/Id")).isEqualTo("bob");
        final XmlElement logout = written.get(1);
        assertThat(text(logout, "EventTime/TimeCreated")).isEqualTo("2026-11-10T00:00:02.000Z");
        final XmlElement request = written.get(2);
        assertThat(text(request, "EventSource/System/Name")).isEqualTo("PEGA");
        assertThat(text(request, "EventDetail/TypeId")).isEqualTo("Request");
        assertThat(XmlSerialiser.toXml(request)).contains("Value=\"c-1\"");
        assertThat(XmlSerialiser.toXml(written.get(3))).doesNotContain("c-1");
        assertThat(logged).contains("INFO " + DispatcherXslt.PASSED_OVER + "1" + DispatcherXslt.PASSED_OVER_OF
                                    + "PegaDB",
                "INFO " + DispatcherXslt.PASSED_OVER + "1" + DispatcherXslt.PASSED_OVER_OF
                + DispatcherXslt.PASSED_OVER_NONE);
        assertThat(logged).anySatisfy(line -> assertThat(line).startsWith("WARN " + DispatcherXslt.NEW_SOURCE_TYPE)
                .contains("'Weird'"));
        assertThat(logged).anySatisfy(line -> assertThat(line).startsWith("WARN pega_integration: soap"
                                                                         + XsltGenerator.NOT_PARSED + "XML"));
        // The wrapper's fields the common module reads, and the source type field the dispatcher reads, are no
        // group's unread fields.
        assertThat(logged).noneSatisfy(line -> assertThat(line).contains(XsltGenerator.UNREAD_FIELD));
        // Rows tried in order: the narrower pattern took its record before the wider one after it.
        assertThat(logged).contains("INFO " + DispatcherXslt.PASSED_OVER + "1" + DispatcherXslt.PASSED_OVER_OF
                                    + "MailAudit", "INFO " + DispatcherXslt.PASSED_OVER + "1"
                                                   + DispatcherXslt.PASSED_OVER_OF + "Mail");
    }

    @Test
    void anUnsavedGroupIsSteppedInlinedInPlaceOfItsImport() {
        final Modules modules = modules();
        final Map<String, String> imports = new LinkedHashMap<>(modules.imports());
        imports.remove(SECURITY_HREF);
        final DispatcherSpec spec = spec();
        final DispatcherXslt.Generated temporary = DispatcherXslt.generate(spec, SCHEMA, "4.1.0",
                Map.of("pega_security", modules.security()));
        assertThat(temporary.problems()).isEmpty();
        assertThat(temporary.xslt()).doesNotContain(SECURITY_HREF)
                .contains("<xsl:import href=\"" + COMMON_HREF + "\"/>", "mode=\"pega_security\"");
        final XmlElement events = transform(temporary.xslt(), records(), imports, new ArrayList<>());
        assertThat(invalid(events, XSD)).isEmpty();
        assertThat(children(events, "Event")).hasSize(4);
        // A new group, not imported yet, routed to its mode as it will be.
        final DispatcherSpec without = new DispatcherSpec(spec.feed(), spec.mode(), spec.input(), spec.field(),
                spec.helpers(), spec.rows(), spec.groups().subList(1, 2), spec.events());
        final DispatcherSpec added = without.withGroup(new DispatcherSpec.Group("PEGA security", "pega_security",
                null, List.of("PegaSecurityEvent")));
        final DispatcherXslt.Generated withNew = DispatcherXslt.generate(added, SCHEMA, "4.1.0",
                Map.of("pega_security", modules.security()));
        assertThat(withNew.problems()).isEmpty();
        assertThat(children(transform(withNew.xslt(), records(), imports, new ArrayList<>()), "Event")).hasSize(4);
    }

    @Test
    void theGeneratorTargetsWhicheverUuidsItIsGiven() {
        final Modules modules = modules();
        final TranslationMapping security = securityMapping(commonMapping());
        final String copies = XsltGenerator.generate(security.withModule(security.module().withImports(
                "uuid='d0000000-0000-0000-0000-000000000001', name='PEGA common'", HELPERS_HREF)), SCHEMA,
                "4.1.0").xslt();
        assertThat(copies).contains("d0000000-0000-0000-0000-000000000001").doesNotContain(COMMON_HREF);
        assertThat(modules.security()).contains(COMMON_HREF);
    }

    @Test
    void aGroupHoldingWhatIsStylesheetLevelOrOutsideItsModeIsRefused() {
        final String security = modules().security();
        final String edited = security.replace("<xsl:template match=\"*\" mode=\"pega_security\">",
                "<xsl:output method=\"xml\"/><xsl:key name=\"k\" match=\"*\" use=\"@key\"/>"
                + "<xsl:template match=\"*\"><xsl:apply-templates select=\".\" mode=\"pega_security\"/>"
                + "</xsl:template><xsl:template name=\"helper\"/>"
                + "<xsl:template match=\"*\" mode=\"pega_security\">");
        final List<String> problems = ModuleChecks.groupProblems(edited);
        assertThat(problems).anySatisfy(problem -> assertThat(problem).contains("xsl:output", "stylesheet-level"))
                .anySatisfy(problem -> assertThat(problem).contains("xsl:key"))
                .anySatisfy(problem -> assertThat(problem).contains("outside its mode"))
                .anySatisfy(problem -> assertThat(problem).contains("named template helper", "not prefixed"));
        // Not a group: nothing to say.
        assertThat(ModuleChecks.groupProblems(modules().dispatcher())).isEmpty();
    }

    @Test
    void importsThatCycleReachTheDispatcherOrNestTooDeepAreRefused() {
        final Modules modules = modules();
        final Map<String, ModuleChecks.Imported> docs = new LinkedHashMap<>();
        docs.put(SECURITY_HREF, new ModuleChecks.Imported("u-sec", "PEGA security", modules.security()));
        docs.put(COMMON_HREF, new ModuleChecks.Imported("u-common", "PEGA common", modules.common()));
        docs.put(HELPERS_HREF, new ModuleChecks.Imported("u-helpers", "PEGA helpers", modules.helpers()));
        docs.put(INTEGRATION_HREF, new ModuleChecks.Imported("u-int", "PEGA integration", modules.integration()));
        assertThat(ModuleChecks.importProblems(modules.dispatcher(), "u-dispatch", href -> Optional.ofNullable(
                docs.get(href)))).isEmpty();
        // A hand-edited group importing the dispatcher.
        docs.put("PEGA dispatcher", new ModuleChecks.Imported("u-dispatch", "PEGA dispatcher",
                modules.dispatcher()));
        final String importing = modules.security().replace("<xsl:import href=\"" + COMMON_HREF + "\"/>",
                "<xsl:import href=\"" + COMMON_HREF + "\"/><xsl:import href=\"PEGA dispatcher\"/>");
        assertThat(ModuleChecks.importProblems(importing, "u-sec", href -> Optional.ofNullable(docs.get(href))))
                .singleElement().satisfies(problem -> assertThat(problem).contains("imports the feed's dispatcher"));
        // A cycle, and a chain deeper than the cap.
        final Map<String, ModuleChecks.Imported> cycle = Map.of(
                "A", new ModuleChecks.Imported("a", "A", "<x><xsl:import href=\"B\"/></x>"),
                "B", new ModuleChecks.Imported("b", "B", "<x><xsl:import href=\"A\"/></x>"));
        assertThat(ModuleChecks.importProblems("<x><xsl:import href=\"A\"/></x>", null,
                href -> Optional.ofNullable(cycle.get(href)))).singleElement()
                .satisfies(problem -> assertThat(problem).contains("Imports cycle: this XSLT -> A -> B -> A"));
        final Map<String, ModuleChecks.Imported> chain = new LinkedHashMap<>();
        for (int n = 0; n < 12; n++) {
            chain.put("L" + n, new ModuleChecks.Imported("l" + n, "L" + n, "<x><xsl:import href=\"L" + (n + 1)
                                                                          + "\"/></x>"));
        }
        assertThat(ModuleChecks.importProblems("<x><xsl:import href=\"L0\"/></x>", null,
                href -> Optional.ofNullable(chain.get(href)))).singleElement()
                .satisfies(problem -> assertThat(problem).contains("nest more than " + ModuleChecks.MAX_IMPORT_DEPTH));
    }

    @Test
    void aPatternTheTwoDialectsReadDifferentlyIsRefused() {
        // Java's \w is ASCII; XPath's takes any letter: 'café' would be counted by the survey as one source type
        // and dispatched as another.
        assertThat(SourceTypeRows.problems(List.of(new SourceTypeRows.Row("words", SourceTypeRows.PATTERN,
                "^\\w+$")), List.of("cafe", "café"))).singleElement()
                .satisfies(problem -> assertThat(problem).contains("XPath's", "'café'"));
        // One only Java compiles (a lookahead) is refused when confirmed, and at generation.
        assertThat(SourceTypeRows.problems(List.of(new SourceTypeRows.Row("ahead", SourceTypeRows.PATTERN,
                "^/var/(?!tmp)")), List.of())).singleElement()
                .satisfies(problem -> assertThat(problem).contains("XPath"));
        final DispatcherSpec spec = spec();
        final List<DispatcherSpec.Row> rows = new ArrayList<>(spec.rows());
        rows.addFirst(new DispatcherSpec.Row("ahead", SourceTypeRows.PATTERN, "^Pega(?!DB)", "parked"));
        final DispatcherXslt.Generated refused = DispatcherXslt.generate(new DispatcherSpec(spec.feed(),
                spec.mode(), spec.input(), spec.field(), spec.helpers(), rows, spec.groups(), spec.events()), SCHEMA,
                "4.1.0");
        assertThat(refused.ok()).isFalse();
        assertThat(refused.problems()).anySatisfy(problem -> assertThat(problem).contains("'ahead'", "XPath"));
        // Plain characters, classes and anchors read alike.
        assertThat(SourceTypeRows.problems(List.of(new SourceTypeRows.Row("mail", SourceTypeRows.PATTERN,
                "^/var/log/mail/[a-z]+")), List.of("/var/log/mail/in", "/var/log/other"))).isEmpty();
    }

    // --- the feed's modules ---

    private static Modules modules() {
        final TranslationMapping common = commonMapping();
        final XsltGenerator.Generated commonXslt = XsltGenerator.generate(common, SCHEMA, "4.1.0");
        assertThat(commonXslt.problems()).isEmpty();
        final XsltGenerator.Generated security = XsltGenerator.generate(securityMapping(common), SCHEMA, "4.1.0");
        assertThat(security.problems()).isEmpty();
        final XsltGenerator.Generated integration = XsltGenerator.generate(integrationMapping(common), SCHEMA,
                "4.1.0");
        assertThat(integration.problems()).isEmpty();
        final DispatcherXslt.Generated dispatcher = DispatcherXslt.generate(spec(), SCHEMA, "4.1.0");
        assertThat(dispatcher.problems()).isEmpty();
        final String helpers = HelpersXslt.generate();
        assertThat(HelpersXslt.isCurrent(helpers)).isTrue();
        final Map<String, String> imports = new LinkedHashMap<>();
        imports.put(COMMON_HREF, commonXslt.xslt());
        imports.put(HELPERS_HREF, helpers);
        imports.put(SECURITY_HREF, security.xslt());
        imports.put(INTEGRATION_HREF, integration.xslt());
        return new Modules(dispatcher.xslt(), security.xslt(), integration.xslt(), commonXslt.xslt(), helpers,
                imports);
    }

    private static TranslationMapping commonMapping() {
        return TranslationMapping.fromJson(json("""
                {"input": "json", "json_layout": "lines",
                 "common": [{"path": "EventTime/TimeCreated", "field": "timestamp"},
                            {"path": "EventSource/System/Name", "value": "PEGA"},
                            {"path": "EventSource/System/Environment", "value": "Prod"},
                            {"path": "EventSource/Device/HostName", "field": "hostname"}],
                 "events": [{"name": "common"}],
                 "module": {"kind": "common", "name": "PEGA", "mode": "pega_common",
                            "helpers": "%s"}}""".formatted(HELPERS_HREF)));
    }

    private static TranslationMapping securityMapping(final TranslationMapping common) {
        final TranslationMapping mapping = TranslationMapping.fromJson(json("""
                {"input": "json", "json_layout": "lines", "json_fields": ["message"],
                 "common": [{"path": "EventSource/Generator", "value": "Pega"}],
                 "ignored_fields": ["app"],
                 "events": [{"name": "login", "when": [{"field": "message.eventType", "equals": "login"}],
                             "fields": [{"path": "EventTime/TimeCreated", "field": "message.eventTime"},
                                        {"path": "EventDetail/TypeId", "value": "Login"},
                                        {"path": "EventDetail/Authenticate/Action", "value": "Logon"},
                                        {"path": "EventDetail/Authenticate/User/Id", "field": "message.operatorId"}]},
                            {"name": "other",
                             "fields": [{"path": "EventDetail/TypeId", "field": "message.eventType"},
                                        {"path": "EventDetail/Unknown/Data", "data_name": "operator",
                                         "field": "message.operatorId"}]}]}"""));
        return mapping.withModule(new SourceTypeModule(SourceTypeModule.GROUP, "PEGA security", "pega_security",
                List.of("PegaSecurityEvent"), COMMON_HREF, HELPERS_HREF, SourceTypeModule.commonParts(common),
                "PEGA Security", "sourcetype"));
    }

    private static TranslationMapping integrationMapping(final TranslationMapping common) {
        final TranslationMapping mapping = TranslationMapping.fromJson(json("""
                {"input": "json", "json_layout": "lines", "xml_fields": ["soap"],
                 "extract": [{"field": "message", "regex": "^(Request|Response): (.*)$", "names": ["direction", "soap"],
                              "flags": "s"}],
                 "common": [{"path": "EventSource/Generator", "value": "Pega"}],
                 "ignored_fields": ["app"],
                 "events": [{"name": "call",
                             "fields": [{"path": "EventDetail/TypeId", "field": "direction"},
                                        {"path": "EventDetail/Unknown/Data", "data_name": "correlation",
                                         "field": "soap/Envelope/Header/CorrelationId"}]}]}"""));
        return mapping.withModule(new SourceTypeModule(SourceTypeModule.GROUP, "PEGA integration",
                "pega_integration", List.of("PegaIntegration"), COMMON_HREF, HELPERS_HREF,
                SourceTypeModule.commonParts(common), null, "sourcetype"));
    }

    private static DispatcherSpec spec() {
        final TranslationMapping input = TranslationMapping.fromJson(json("""
                {"input": "json", "json_layout": "lines", "events": [{"name": "x"}]}"""));
        final List<DispatcherSpec.Row> rows = List.of(
                new DispatcherSpec.Row("PegaSecurityEvent", SourceTypeRows.VALUE, "PegaSecurityEvent", "translated"),
                new DispatcherSpec.Row("MailAudit", SourceTypeRows.PATTERN, "^Mail/audit/", "parked"),
                new DispatcherSpec.Row("Mail", SourceTypeRows.PATTERN, "^Mail/", "parked"),
                new DispatcherSpec.Row("PegaIntegration", SourceTypeRows.VALUE, "PegaIntegration", "translated"),
                new DispatcherSpec.Row("PegaDB", SourceTypeRows.VALUE, "PegaDB", "parked"));
        final List<DispatcherSpec.Group> groups = List.of(
                new DispatcherSpec.Group("PEGA security", "pega_security", SECURITY_HREF, List.of("PegaSecurityEvent")),
                new DispatcherSpec.Group("PEGA integration", "pega_integration", INTEGRATION_HREF,
                        List.of("PegaIntegration")));
        return new DispatcherSpec("PEGA", "pega_dispatch", DispatcherSpec.inputOf(input), "sourcetype",
                HELPERS_HREF, rows, groups, Map.of());
    }

    // JSON lines as Stroom's JSON parser gives them, its root map around the records.
    private static String records() {
        final StringBuilder out = new StringBuilder("<map xmlns=\"http://www.w3.org/2013/XSL/json\">");
        out.append(record("2026-11-10T00:00:00.000Z", "PegaSecurityEvent",
                "{\"eventType\": \"login\", \"operatorId\": \"bob\", \"eventTime\": \"2026-11-10T00:00:01.000Z\"}"));
        out.append(record("2026-11-10T00:00:02.000Z", "PegaSecurityEvent",
                "{\"eventType\": \"logout\", \"operatorId\": \"amy\"}"));
        out.append(record("2026-11-10T00:00:03.000Z", "PegaIntegration", SOAP));
        out.append(record("2026-11-10T00:00:04.000Z", "PegaIntegration", TRUNCATED));
        out.append(record("2026-11-10T00:00:05.000Z", "PegaDB", "select 1"));
        out.append(record("2026-11-10T00:00:06.000Z", "Mail/audit/a.log", "mail"));
        out.append(record("2026-11-10T00:00:06.500Z", "Mail/in.log", "mail"));
        out.append(record("2026-11-10T00:00:07.000Z", "Weird", "?"));
        return out.append("</map>").toString();
    }

    private static String record(final String time, final String sourceType, final String message) {
        return "<map><string key=\"timestamp\">" + time + "</string><string key=\"hostname\">app01</string>"
               + "<string key=\"sourcetype\">" + sourceType + "</string><string key=\"app\">pega</string>"
               + "<string key=\"message\">" + escape(message) + "</string></map>";
    }

    private static String escape(final String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }


    // --------------------------------------------------------------------------------


    private record Modules(String dispatcher,
                           String security,
                           String integration,
                           String common,
                           String helpers,
                           Map<String, String> imports) {

    }
}
