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

import stroom.ai.impl.tools.sample.SourceTypeFields;
import stroom.ai.impl.tools.sample.SourceTypeRows;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/// An aggregated feed's translation modules, generated and run through Saxon: each source type group a whole
/// pipeline XSLT (its Events, its own source types' records told by the registry's rows in order, exact and pattern,
/// the rest passed over and counted), importing the feed's common module, which imports the helpers: a chain; the
/// checks of a group module, of imports (cycles, depth, a module imported twice), and of patterns the two regex
/// dialects read differently.
class TestSourceTypeModules extends XsltRuns {

    private static final String COMMON_HREF = "uuid='c0000000-0000-0000-0000-000000000001', name='PEGA common'";
    private static final String HELPERS_HREF = "uuid='c0000000-0000-0000-0000-000000000002', name='PEGA helpers'";
    private static final String SOAP = "Request: <soapenv:Envelope xmlns:soapenv=\"urn:soap\" xmlns:cus=\"urn:cus\">"
                                       + "<soapenv:Header><cus:CorrelationId>c-1</cus:CorrelationId>"
                                       + "</soapenv:Header></soapenv:Envelope>";
    private static final String TRUNCATED = "Request: <soapenv:Envelope xmlns:soapenv=\"urn:soap\"><soapenv:Header><c";
    private static final List<SourceTypeModule.Row> ROWS = List.of(
            new SourceTypeModule.Row("PegaSecurityEvent", SourceTypeRows.VALUE, "PegaSecurityEvent"),
            new SourceTypeModule.Row("MailAudit", SourceTypeRows.PATTERN, "^Mail/audit/"),
            new SourceTypeModule.Row("Mail", SourceTypeRows.PATTERN, "^Mail/"),
            new SourceTypeModule.Row("PegaIntegration", SourceTypeRows.VALUE, "PegaIntegration"),
            new SourceTypeModule.Row("PegaDB", SourceTypeRows.VALUE, "PegaDB"));

    @Test
    void aGroupIsItsPipelinesWholeXsltTranslatingItsOwnSourceTypesAlone() {
        final Modules modules = modules();
        // The group: the Events written, its records in its own mode, its names prefixed, only the common module
        // imported (which imports the helpers), no inline helpers.
        final String security = modules.security();
        assertThat(security).contains("<xsl:import href=\"" + COMMON_HREF + "\"/>",
                        "<Events Version=\"4.1.0\"",
                        "<xsl:apply-templates select=\"/map/map\" mode=\"pega_security\"/>",
                        "<xsl:template match=\"*\" mode=\"pega_security\">",
                        "<xsl:call-template name=\"pega_common_event_time\"/>",
                        "<xsl:with-param name=\"system_name\" select=\"'PEGA Security'\"/>",
                        "name=\"mcp:pega_security_in_group\"")
                .doesNotContain(HELPERS_HREF, "name=\"mcp:json-to-xml\"");
        assertThat(ModuleChecks.hrefs(security)).containsExactly(COMMON_HREF);
        assertThat(ModuleChecks.hrefs(modules.common())).containsExactly(HELPERS_HREF);
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
        final XmlElement events = transform(security, records(), modules.imports(), logged);
        assertThat(invalid(events, XSD)).isEmpty();
        final List<XmlElement> written = children(events, "Event");
        // Its own source type's records only; the other six (another group's, parked, a value no row matches)
        // passed over, counted once in one line, never warned of.
        assertThat(written).hasSize(2);
        final XmlElement login = written.getFirst();
        // The rule mapping its own time writes its own; the common module's System written with the group's name.
        assertThat(text(login, "EventTime/TimeCreated")).isEqualTo("2026-11-10T00:00:01.000Z");
        assertThat(text(login, "EventSource/System/Name")).isEqualTo("PEGA Security");
        assertThat(text(login, "EventSource/System/Environment")).isEqualTo("Prod");
        assertThat(text(login, "EventSource/Device/HostName")).isEqualTo("app01");
        assertThat(text(login, "EventDetail/Authenticate/User/Id")).isEqualTo("bob");
        assertThat(text(written.get(1), "EventTime/TimeCreated")).isEqualTo("2026-11-10T00:00:02.000Z");
        assertThat(logged).filteredOn(line -> line.contains(XsltGenerator.PASSED_OVER))
                .containsExactly(passedOver(6));
        assertThat(logged).noneSatisfy(line -> assertThat(line).startsWith("WARN"));
        // The wrapper's fields the common module reads, and the source type field the group reads, are no unread
        // fields.
        assertThat(logged).noneSatisfy(line -> assertThat(line).contains(XsltGenerator.UNREAD_FIELD));
    }

    @Test
    void anotherGroupsHeldMessagesAreParsedAndThoseThatDoNotParseLogged() {
        final Modules modules = modules();
        final List<String> logged = new ArrayList<>();
        final XmlElement events = transform(modules.integration(), records(), modules.imports(), logged);
        assertThat(invalid(events, XSD)).isEmpty();
        final List<XmlElement> written = children(events, "Event");
        assertThat(written).hasSize(2);
        final XmlElement request = written.getFirst();
        assertThat(text(request, "EventSource/System/Name")).isEqualTo("PEGA");
        assertThat(text(request, "EventDetail/TypeId")).isEqualTo("Request");
        assertThat(XmlSerialiser.toXml(request)).contains("Value=\"c-1\"");
        assertThat(XmlSerialiser.toXml(written.get(1))).doesNotContain("c-1");
        assertThat(logged).anySatisfy(line -> assertThat(line).startsWith("WARN pega_integration: soap"
                                                                         + XsltGenerator.NOT_PARSED + "XML"));
        assertThat(logged).contains(passedOver(6));
    }

    @Test
    void patternRowsAreTriedInOrderAsTheToolsTryThem() {
        // MailAudit's narrower pattern comes before Mail's: a Mail/audit/ record is MailAudit's, not Mail's, so a
        // group of Mail alone translates only the other mail record.
        final Modules modules = modules();
        final TranslationMapping mail = otherMapping("PEGA mail", "pega_mail", List.of("Mail"), ROWS,
                modules.commonMapping());
        final XsltGenerator.Generated generated = XsltGenerator.generate(mail, SCHEMA, "4.1.0");
        assertThat(generated.problems()).isEmpty();
        assertThat(generated.xslt()).contains("matches($value, '^Mail/audit/')", "$value = 'PegaDB'");
        final List<String> logged = new ArrayList<>();
        final XmlElement events = transform(generated.xslt(), records(), modules.imports(), logged);
        assertThat(invalid(events, XSD)).isEmpty();
        assertThat(children(events, "Event")).singleElement()
                .satisfies(event -> assertThat(text(event, "EventTime/TimeCreated"))
                        .isEqualTo("2026-11-10T00:00:06.500Z"));
        assertThat(logged).contains(passedOver(7));
        assertThat(SourceTypeRows.first(ROWS.stream().map(row -> new SourceTypeRows.Row(row.name(), row.match(),
                row.value())).toList(), "Mail/audit/a.log")).hasValueSatisfying(row -> assertThat(row.name())
                .isEqualTo("MailAudit"));

        // Both mail source types in one group: both records its own.
        final TranslationMapping both = otherMapping("PEGA mail", "pega_mail", List.of("MailAudit", "Mail"), ROWS,
                modules.commonMapping());
        final XmlElement all = transform(XsltGenerator.generate(both, SCHEMA, "4.1.0").xslt(), records(),
                modules.imports(), new ArrayList<>());
        assertThat(children(all, "Event")).hasSize(2);
    }

    @Test
    void aGroupReadsASourceTypesValueAsTheToolsDo() {
        // The tools strip a value and take -, null, none and n/a for no value: a group reads it the same, so a
        // padded value is its source type, and a nil value is no source type's.
        final List<String> values = List.of(" PegaDB\t", "PegaDB", "N/A", "-", "none", "Null", "", "\nPegaDB ");
        assertThat(values.stream().map(value -> SourceTypeFields.valueOf(Map.of("sourcetype", value),
                "sourcetype")).toList()).containsExactly("PegaDB", "PegaDB", null, null, null, null, null, "PegaDB");

        final Modules modules = modules();
        final TranslationMapping db = otherMapping("PEGA db", "pega_db", List.of("PegaDB"), ROWS,
                modules.commonMapping());
        final StringBuilder xml = new StringBuilder("<map xmlns=\"http://www.w3.org/2013/XSL/json\">");
        values.forEach(value -> xml.append(record("2026-11-10T00:00:05.000Z", value, "select 1")));
        final List<String> logged = new ArrayList<>();
        final XmlElement events = transform(XsltGenerator.generate(db, SCHEMA, "4.1.0").xslt(),
                xml.append("</map>").toString(), modules.imports(), logged);

        assertThat(children(events, "Event")).hasSize(3);
        assertThat(logged).contains(passedOver(5));
    }

    @Test
    void aRecordPassedOverHasItsNumberReadAsTheGroupsOwnAreRead() {
        // Stroom moves to a record's location only when one is read: a record passed over without reading it left
        // later log lines naming earlier records (seen with the dispatcher in the aggregated feed walks). Each record
        // is read once: by the group's rules, or by the template passing it over.
        final Modules modules = modules();
        final XmlElement sheet = XmlParser.parse(modules.security());
        final XmlElement passOver = sheet.childElements(XSL, "template").stream()
                .filter(template -> "*[not(mcp:pega_security_in_group(.))]".equals(template.get("match")))
                .findFirst()
                .orElseThrow();

        assertThat(passOver.get("mode")).isEqualTo("pega_security");
        assertThat(passOver.childElements(XSL, "variable")).singleElement()
                .satisfies(variable -> assertThat(variable.get("select")).isEqualTo("stroom:record-no()"));
        assertThat(modules.security().split("stroom:record-no\\(\\)", -1)).hasSize(3);
    }

    @Test
    void theGeneratorTargetsWhicheverUuidsItIsGiven() {
        final Modules modules = modules();
        final TranslationMapping security = securityMapping(modules.commonMapping());
        final String copies = XsltGenerator.generate(security.withModule(security.module().withImports(
                "uuid='d0000000-0000-0000-0000-000000000001', name='PEGA common'", null)), SCHEMA,
                "4.1.0").xslt();
        assertThat(copies).contains("d0000000-0000-0000-0000-000000000001").doesNotContain(COMMON_HREF);
        assertThat(modules.security()).contains(COMMON_HREF);
        // A group with no common module (its whole Event its own) imports the helpers itself.
        final TranslationMapping common = modules.commonMapping();
        final List<FieldMapping> entries = new ArrayList<>(common.common());
        entries.addAll(security.common());
        final XsltGenerator.Generated alone = XsltGenerator.generate(security.withCommonAndShared(entries, List.of())
                .withModule(security.module().withImports(null, HELPERS_HREF).withParts(List.of())), SCHEMA,
                "4.1.0");
        assertThat(alone.problems()).isEmpty();
        assertThat(ModuleChecks.hrefs(alone.xslt())).containsExactly(HELPERS_HREF);
    }

    @Test
    void aGroupWithNothingToTellItsRecordsByOrAnImportOfItsOwnIsRefused() {
        final Modules modules = modules();
        final TranslationMapping security = securityMapping(modules.commonMapping());
        final SourceTypeModule module = security.module();
        final XsltGenerator.Generated noRows = XsltGenerator.generate(security.withModule(new SourceTypeModule(
                module.kind(), module.name(), module.mode(), module.sourceTypes(), module.common(), null,
                module.parts(), module.systemName(), null, List.of())), SCHEMA, "4.1.0");
        assertThat(noRows.ok()).isFalse();
        assertThat(noRows.problems()).anySatisfy(problem -> assertThat(problem).contains("no source type field"))
                .anySatisfy(problem -> assertThat(problem).contains("'PegaSecurityEvent'", "no row"));
        // A shared call of the group's own: the common module's to import, never the group's beside it.
        final TranslationMapping calling = security.withCommonAndShared(security.common(), List.of(
                new SharedTemplate("Event Meta", "event-meta", "Meta", Map.of())));
        final XsltGenerator.Generated refused = XsltGenerator.generate(calling, SCHEMA, "4.1.0");
        assertThat(refused.ok()).isFalse();
        assertThat(refused.problems()).anySatisfy(problem -> assertThat(problem).contains("event-meta",
                "imports only the feed's common module"));
    }

    @Test
    void aGroupsNamesNotPrefixedWithItsModeAreRefused() {
        final String security = modules().security();
        // A group is a whole pipeline XSLT: stylesheet-level declarations and its root template are its own.
        final String declaring = security.replace("<xsl:template match=\"*\" mode=\"pega_security\">",
                "<xsl:output method=\"xml\"/><xsl:template match=\"*\" mode=\"pega_security\">");
        assertThat(ModuleChecks.groupProblems(declaring)).isEmpty();
        // Mode pega_security can't claim pega_securitymail's names, nor its mode, nor an unprefixed one.
        final String edited = security.replace("<xsl:template match=\"*\" mode=\"pega_security\">",
                "<xsl:template name=\"pega_securitymail_x\"/><xsl:variable name=\"pega_securitymail_v\"/>"
                + "<xsl:template match=\"*\" mode=\"pega_securitymail\"/><xsl:template name=\"helper\"/>"
                + "<xsl:template match=\"*\" mode=\"pega_security\">");

        final List<String> problems = ModuleChecks.groupProblems(edited);

        assertThat(problems).anySatisfy(problem -> assertThat(problem).contains("pega_securitymail_x"))
                .anySatisfy(problem -> assertThat(problem).contains("pega_securitymail_v"))
                .anySatisfy(problem -> assertThat(problem).contains("mode pega_securitymail"))
                .anySatisfy(problem -> assertThat(problem).contains("named template helper", "not prefixed"));
        // Not a group: nothing to say.
        assertThat(ModuleChecks.groupProblems(modules().common())).isEmpty();
        assertThat(ModuleChecks.ownName("pega_security_x", "pega_security")).isTrue();
        assertThat(ModuleChecks.ownName("pega_securityX", "pega_security")).isTrue();
        assertThat(ModuleChecks.ownName("pega_securitymail", "pega_security")).isFalse();
    }

    @Test
    void importsThatCycleNestTooDeepOrImportAModuleTwiceAreRefused() {
        final Modules modules = modules();
        final Map<String, ModuleChecks.Imported> docs = new LinkedHashMap<>();
        docs.put(COMMON_HREF, new ModuleChecks.Imported("u-common", "PEGA common", modules.common()));
        docs.put(HELPERS_HREF, new ModuleChecks.Imported("u-helpers", "PEGA helpers", modules.helpers()));
        docs.put("Event Meta", new ModuleChecks.Imported("u-meta", "Event Meta", "<x/>"));
        // The chain: group -> common -> helpers, each once.
        assertThat(ModuleChecks.importProblems(modules.security(), "u-sec", href -> Optional.ofNullable(
                docs.get(href)))).isEmpty();
        // A hand-edited group importing the helpers beside the common module: a diamond.
        final String helpersToo = modules.security().replace("<xsl:import href=\"" + COMMON_HREF + "\"/>",
                "<xsl:import href=\"" + COMMON_HREF + "\"/><xsl:import href=\"" + HELPERS_HREF + "\"/>");
        assertThat(ModuleChecks.importProblems(helpersToo, "u-sec", href -> Optional.ofNullable(docs.get(href))))
                .singleElement().satisfies(problem -> assertThat(problem).contains("'PEGA helpers' is imported "
                                                                                   + "twice",
                        "PEGA security -> PEGA common -> PEGA helpers", "PEGA security -> PEGA helpers",
                        "imports only the feed's common module"));
        // A site library the common module imports, imported by the group too.
        final String common = modules.common().replace("<xsl:import href=\"" + HELPERS_HREF + "\"/>",
                "<xsl:import href=\"" + HELPERS_HREF + "\"/><xsl:import href=\"Event Meta\"/>");
        docs.put(COMMON_HREF, new ModuleChecks.Imported("u-common", "PEGA common", common));
        final String libraryToo = modules.security().replace("<xsl:import href=\"" + COMMON_HREF + "\"/>",
                "<xsl:import href=\"" + COMMON_HREF + "\"/><xsl:import href=\"Event Meta\"/>");
        assertThat(ModuleChecks.importProblems(libraryToo, "u-sec", href -> Optional.ofNullable(docs.get(href))))
                .singleElement().satisfies(problem -> assertThat(problem).contains("'Event Meta' is imported twice"));
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
        // and translated as another.
        assertThat(SourceTypeRows.problems(List.of(new SourceTypeRows.Row("words", SourceTypeRows.PATTERN,
                "^\\w+$")), List.of("cafe", "café"))).singleElement()
                .satisfies(problem -> assertThat(problem).contains("XPath's", "'café'"));
        // One only Java compiles (a lookahead) is refused when confirmed, and at generation.
        assertThat(SourceTypeRows.problems(List.of(new SourceTypeRows.Row("ahead", SourceTypeRows.PATTERN,
                "^/var/(?!tmp)")), List.of())).singleElement()
                .satisfies(problem -> assertThat(problem).contains("XPath"));
        final List<SourceTypeModule.Row> rows = new ArrayList<>(ROWS);
        rows.addFirst(new SourceTypeModule.Row("ahead", SourceTypeRows.PATTERN, "^Pega(?!DB)"));
        final Modules modules = modules();
        final XsltGenerator.Generated refused = XsltGenerator.generate(otherMapping("PEGA db", "pega_db",
                List.of("PegaDB"), rows, modules.commonMapping()), SCHEMA, "4.1.0");
        assertThat(refused.ok()).isFalse();
        assertThat(refused.problems()).anySatisfy(problem -> assertThat(problem).contains("'ahead'", "XPath"));
        // Plain characters, classes and anchors read alike.
        assertThat(SourceTypeRows.problems(List.of(new SourceTypeRows.Row("mail", SourceTypeRows.PATTERN,
                "^/var/log/mail/[a-z]+")), List.of("/var/log/mail/in", "/var/log/other"))).isEmpty();
    }

    @Test
    void aGroupsCommentsAreWellFormedWhateverDashesItsNameHolds() {
        // A comment can't hold "--": replacing each "--" with "-" once left one in "---".
        final Modules modules = modules();
        final TranslationMapping dashed = otherMapping("PEGA---db", "pega_db", List.of("PegaDB"), ROWS,
                modules.commonMapping());
        final XsltGenerator.Generated generated = XsltGenerator.generate(dashed, SCHEMA, "4.1.0");

        assertThat(generated.problems()).isEmpty();
        final Matcher comments = Pattern.compile("<!--(.*?)-->", Pattern.DOTALL).matcher(generated.xslt());
        final List<String> found = new ArrayList<>();
        while (comments.find()) {
            found.add(comments.group(1));
        }
        assertThat(found).anySatisfy(comment -> assertThat(comment).contains("PEGA-db"))
                .allSatisfy(comment -> assertThat(comment).doesNotContain("--").doesNotEndWith("-"));
    }

    @Test
    void aGroupsModuleIsKeptWithItsMappingRowsAndAll() {
        final TranslationMapping security = securityMapping(commonMapping());
        final TranslationMapping read = TranslationMapping.fromJson(security.toJson(DumpMode.COMPACT));
        assertThat(read.module()).isEqualTo(security.module());
        assertThat(read.module().rows()).isEqualTo(ROWS);
    }

    // --- the feed's modules ---

    private static String passedOver(final int count) {
        return "INFO " + XsltGenerator.PASSED_OVER + count + XsltGenerator.PASSED_OVER_END;
    }

    private static Modules modules() {
        final TranslationMapping common = commonMapping();
        final XsltGenerator.Generated commonXslt = XsltGenerator.generate(common, SCHEMA, "4.1.0");
        assertThat(commonXslt.problems()).isEmpty();
        final XsltGenerator.Generated security = XsltGenerator.generate(securityMapping(common), SCHEMA, "4.1.0");
        assertThat(security.problems()).isEmpty();
        final XsltGenerator.Generated integration = XsltGenerator.generate(integrationMapping(common), SCHEMA,
                "4.1.0");
        assertThat(integration.problems()).isEmpty();
        final String helpers = HelpersXslt.generate();
        assertThat(HelpersXslt.isCurrent(helpers)).isTrue();
        final Map<String, String> imports = new LinkedHashMap<>();
        imports.put(COMMON_HREF, commonXslt.xslt());
        imports.put(HELPERS_HREF, helpers);
        return new Modules(security.xslt(), integration.xslt(), commonXslt.xslt(), helpers, common, imports);
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
                List.of("PegaSecurityEvent"), COMMON_HREF, null, SourceTypeModule.commonParts(common),
                "PEGA Security", "sourcetype", ROWS));
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
                "pega_integration", List.of("PegaIntegration"), COMMON_HREF, null,
                SourceTypeModule.commonParts(common), null, "sourcetype", ROWS));
    }

    // A group whose events say only what its records are.
    private static TranslationMapping otherMapping(final String name,
                                                   final String mode,
                                                   final List<String> sourceTypes,
                                                   final List<SourceTypeModule.Row> rows,
                                                   final TranslationMapping common) {
        final TranslationMapping mapping = TranslationMapping.fromJson(json("""
                {"input": "json", "json_layout": "lines",
                 "common": [{"path": "EventSource/Generator", "value": "Pega"}],
                 "ignored_fields": ["app"],
                 "events": [{"name": "line", "allow_unknown": "a test",
                             "fields": [{"path": "EventDetail/TypeId", "field": "sourcetype"},
                                        {"path": "EventDetail/Unknown/Data", "data_name": "message",
                                         "field": "message"}]}]}"""));
        return mapping.withModule(new SourceTypeModule(SourceTypeModule.GROUP, name, mode, sourceTypes, COMMON_HREF,
                null, SourceTypeModule.commonParts(common), null, "sourcetype", rows));
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


    private record Modules(String security,
                           String integration,
                           String common,
                           String helpers,
                           TranslationMapping commonMapping,
                           Map<String, String> imports) {

    }
}
