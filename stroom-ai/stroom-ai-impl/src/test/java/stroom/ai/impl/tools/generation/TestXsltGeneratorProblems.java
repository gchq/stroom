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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The Stroom MCP server's generator tests of mistakes (`tests/test_xsltgen.py`), case by case: what the schema
/// and the generator catch come back as problems per mapping entry instead of as XSLT for the model to debug, and
/// what may be wrong as warnings, worded as the server words them.
class TestXsltGeneratorProblems extends XsltRuns {

    private static XsltGenerator.Generated generate(final TranslationMapping mapping) {
        return XsltGenerator.generate(mapping, SCHEMA, "4.1.0");
    }

    private static Map<String, Object> logonRule(final List<Map<String, Object>> fields) {
        return rule("logon", List.of(equalsCondition("action", "login")), fields);
    }

    static Stream<Arguments> mistakes() {
        return Stream.of(
                Arguments.of("{\"path\": \"EventSource/IPAddress\", \"field\": \"ip\"}",
                        "exists at: ['Event/EventSource/Device/IPAddress'"),
                Arguments.of("{\"path\": \"EventDetail/Logon\", \"value\": \"x\"}",
                        "'Logon' is a value of: ['Event/EventDetail/Authenticate/Action']"),
                Arguments.of("{\"path\": \"EventDetail/Authenticate/Action\", \"value\": \"Login\"}",
                        "['Login'] not allowed; Action takes one of ['Logon'"),
                Arguments.of("{\"path\": \"EventDetail/Authenticate/Outcome/Success\", \"value\": \"yes\"}",
                        "Success is true or false"),
                Arguments.of("{\"path\": \"EventDetail/Process/Action\", \"value\": \"Execute\"}",
                        "['Authenticate', 'Process'] are alternatives"),
                Arguments.of("{\"path\": \"EventSource/Device\", \"field\": \"host\"}",
                        "Device holds other elements; map one of"),
                Arguments.of("{\"path\": \"EventDetail/Authenticate/Data\", \"field\": \"sid\"}",
                        "a Data path needs data_name"),
                Arguments.of("{\"path\": \"EventTime/TimeCreated\", \"field\": \"time\", \"time_format\": "
                             + "\"yyyy-MM-ddTHH:mm:ss\"}",
                        "unquoted letters ['T']; quote them, e.g. \"yyyy-MM-dd'T'HH:mm:ss\""));
    }

    @ParameterizedTest
    @MethodSource("mistakes")
    void mappingMistakesComeBackAsProblemsNotXslt(final String change, final String expected) {
        final Map<String, Object> entry = json(change);
        final List<Map<String, Object>> fields = plus(without(LOGON, (String) entry.get("path")), entry);
        final XsltGenerator.Generated result = generate(mapping(with("events", List.of(rule("logon", List.of(),
                fields)))));
        assertThat(result.ok()).isFalse();
        assertThat(result.xslt()).isNull();
        assertThat(result.problems()).anySatisfy(p -> assertThat(p).contains(expected));
    }

    @Test
    void missingRequiredElementsAndChoicesAreProblems() {
        final List<String> problems = generate(mapping(with("events", List.of(rule("bare", List.of(), json("""
                [{"path": "EventDetail/Description", "value": "x"}]""")))))).problems();
        assertThat(problems).contains("[bare] Event/EventDetail/TypeId is required by the schema; map it");
        assertThat(problems).anySatisfy(p -> assertThat(p).startsWith("[bare] Event/EventDetail needs one of "
                                                                      + "['Authenticate'"));
        // Environment is the user's to say; when they don't know it, a placeholder keeps the build going (seen:
        // Haiku stopped for good on this problem).
        final List<String> noEnvironment = generate(mapping(with("common", without(BASE,
                "EventSource/System/Environment")))).problems();
        assertThat(noEnvironment).anySatisfy(p -> assertThat(p)
                .startsWith("[logon] Event/EventSource/System/Environment is required by the schema; map it (a "
                            + "value only the user knows")
                .contains("such as Unknown"));
    }

    @Test
    void aMissingChoicePointsToTheSameMemberMappedElsewhere() {
        // The user is mapped as the source's (BASE) but not as the one logging on: say so, and to keep both.
        final List<Map<String, Object>> noUser = without(LOGON, "EventDetail/Authenticate/User/Id");
        final List<String> problems = generate(mapping(with("events", List.of(rule("logon", List.of(), noUser)))))
                .problems();
        final String problem = problems.stream().filter(p -> p.contains("Authenticate needs one of")).findFirst()
                .orElseThrow();
        assertThat(problem).contains("EventSource/User/Id (field 'user')").contains("EventDetail/Authenticate/User/Id")
                .contains("keep the existing mapping");
        // Nothing comparable mapped elsewhere: the plain message.
        final List<Map<String, Object>> base = BASE.stream()
                .filter(f -> !((String) f.get("path")).startsWith("EventSource/User")
                             && !((String) f.get("path")).startsWith("EventSource/Device"))
                .toList();
        final List<String> plain = generate(mapping(with("common", base, "events", List.of(rule("logon", List.of(),
                noUser))))).problems();
        assertThat(plain).anySatisfy(p -> assertThat(p).endsWith("Authenticate needs one of ['User', 'Device', "
                                                                 + "'Group']"));
    }

    @Test
    void unknownInARuleWithConditionsIsAProblemUnlessAllowed() {
        // The default mapping's 'other' rule has no conditions: Unknown is its job, so nothing is said.
        final XsltGenerator.Generated plain = generate(mapping());
        assertThat(plain.ok()).isTrue();
        assertThat(plain.problems()).noneSatisfy(m -> assertThat(m).contains("Unknown"));
        assertThat(plain.warnings()).noneSatisfy(m -> assertThat(m).contains("Unknown"));
        final Map<String, Object> kind = rule("status", List.of(equalsCondition("action", "status")), json("""
                [{"path": "EventDetail/TypeId", "field": "action"},
                 {"path": "EventDetail/Unknown/Data", "data_name": "action", "field": "action"}]"""));
        // Haiku kept the draft's Unknown placeholder through a warning in 6 of 36 runs: now nothing is generated.
        final XsltGenerator.Generated result = generate(mapping(with("events", List.of(kind, otherRule()))));
        assertThat(result.ok()).isFalse();
        assertThat(result.problems().getFirst()).startsWith("[status] writes EventDetail/Unknown")
                .contains("allow_unknown");
        assertThat(result.xslt()).isNull();
        final Map<String, Object> allowedTrue = new LinkedHashMap<>(kind);
        allowedTrue.put("allow_unknown", true);
        assertThat(generate(mapping(with("events", List.of(allowedTrue, otherRule())))).problems())
                .anySatisfy(p -> assertThat(p).contains("takes the reason"));
        final Map<String, Object> allowed = new LinkedHashMap<>(kind);
        allowed.put("allow_unknown", "status lines carry no activity");
        final XsltGenerator.Generated kept = generate(mapping(with("events", List.of(allowed, otherRule()))));
        assertThat(kept.ok()).isTrue();
        assertThat(kept.warnings()).noneSatisfy(m -> assertThat(m).contains("Unknown"));
    }

    @Test
    void extractedFieldsReadThroughAnyOfAreDeclaredInSharedTemplates() {
        // The user is in two rules, so it is written once as a named template; it reads the extraction through
        // any_of, and the template has its own scope, so the extraction's variable must be declared there too.
        final List<Object> rules = new ArrayList<>();
        for (final String[] kind : new String[][]{{"login", "Logon"}, {"logout", "Logoff"}}) {
            rules.add(rule(kind[0], List.of(equalsCondition("action", kind[0])), json("""
                    [{"path": "EventSource/User/Id", "any_of": ["user_quoted", "user_plain"]},
                     {"path": "EventDetail/TypeId", "value": "%s"},
                     {"path": "EventDetail/Authenticate/Action", "value": "%s"},
                     {"path": "EventDetail/Authenticate/User/Id", "any_of": ["user_quoted", "user_plain"]}]"""
                    .formatted(kind[0], kind[1]))));
        }
        final TranslationMapping m = mapping(with("common", without(BASE, "EventSource/User/Id"), "events", rules,
                "extract", json("""
                        [{"field": "msg", "regex": " user=(?:\\"([^\\"]*)\\"|(\\\\S+))",
                          "names": ["user_quoted", "user_plain"]}]""")));
        final XsltGenerator.Generated result = generate(m);
        assertThat(result.problems()).isEmpty();
        final java.util.regex.Pattern read = java.util.regex.Pattern.compile("\\$([\\w.-]+)");
        for (final XmlElement template : sheet(result.xslt()).iterElements(MappingFacts.XSL, "template")) {
            final java.util.Set<String> declared = new java.util.HashSet<>();
            template.iterElements().stream()
                    .filter(el -> el.is(MappingFacts.XSL, "variable") || el.is(MappingFacts.XSL, "param"))
                    .forEach(el -> declared.add(el.get("name")));
            final java.util.Set<String> reads = new java.util.HashSet<>();
            for (final XmlElement el : template.iterElements()) {
                final java.util.regex.Matcher matcher = read.matcher(java.util.Objects.requireNonNullElse(el.get(
                        "select"), "") + " " + java.util.Objects.requireNonNullElse(el.get("test"), ""));
                while (matcher.find()) {
                    reads.add(matcher.group(1));
                }
            }
            assertThat(declared).as(template.get("name") + template.get("match")).containsAll(reads);
        }
    }

    @Test
    void aRuleWithoutConditionsMustBeLast() {
        final List<Object> rules = List.of(rule("any", List.of(), LOGON), logonRule(LOGON));
        assertThat(generate(mapping(with("events", rules))).problems())
                .anySatisfy(p -> assertThat(p).contains("Rules ['any'] have no conditions"));
    }

    @Test
    void unmatchedRecordsAreLoggedWhenEveryRuleHasConditions() {
        final List<Object> rules = List.of(rule("logon", json("""
                [{"field": "action", "one_of": ["login", "logon"]}]"""), LOGON));
        final String xslt = generate(mapping(with("events", rules, "unmatched", "warn"))).xslt();
        // With the value the rule tests, so a whole feed's misses are grouped from its Error streams alone.
        assertThat(xslt).contains("stroom:log('WARN', concat('No event mapping matched record ', "
                                  + "stroom:record-no(), ' (', string-join((concat('action=', string(("
                                  + "normalize-space(data[@name='action']/@value))[1]))), ' | '), ')'))");
        // one read: no variable
        assertThat(xslt).contains("data[@name='action']/@value = ('login', 'logon')");
    }

    @Test
    void aDropRuleTakesNoFields() {
        final List<Object> rules = List.of(json("""
                {"name": "noise", "drop": true, "when": [{"field": "action", "equals": "x"}],
                 "fields": [{"path": "EventDetail/TypeId", "value": "x"}]}"""), rule("logon", List.of(), LOGON));
        final XsltGenerator.Generated result = generate(mapping(with("events", rules)));
        assertThat(result.ok()).isFalse();
        assertThat(result.problems()).anySatisfy(p -> assertThat(p).contains("takes no fields"));
    }

    @Test
    void anXpathReadingAnExtractsNameAsAVariableIsAProblemNotAnUnreadName() {
        // Seen: extracts chained with xpath "$kv", saved ok:true with only "extract names ['kv'], which nothing
        // reads", then every record failed at stepping: "Variable kv has not been declared".
        final TranslationMapping chained = mapping(with("extract", json("""
                [{"field": "msg", "regex": "^(.*)$", "names": ["kv"]},
                 {"xpath": "$kv", "regex": "src=(\\\\S+)", "names": ["src"]},
                 {"xpath": "for $kv in tokenize(., \\" \\") return $kv", "regex": "(x)", "names": ["x"]}]"""),
                "events", List.of(rule("logon", List.of(), plus(LOGON.subList(0, 2), json("""
                        {"path": "EventDetail/Authenticate/Data", "data_name": "src", "field": "src"}"""), json("""
                        {"path": "EventDetail/Authenticate/Data", "data_name": "x", "field": "x"}"""))))));
        final XsltGenerator.Generated result = generate(chained);
        assertThat(result.ok()).isFalse();
        assertThat(result.problems().stream().filter(p -> p.contains("reads $")).toList()).containsExactly(
                "xpath '$kv' reads $kv: an extract's names are fields, not variables, and the XSLT declares none by "
                + "that name, so it wouldn't compile. Read one with field: 'kv' (an extraction takes an earlier "
                + "extraction's name as its field too); to change the text first, put the change in the xpath that "
                + "reads the source itself.");
        assertThat(result.warnings()).noneSatisfy(w -> assertThat(w).contains("nothing reads"));
        assertThat(MappingFacts.readsInput(chained, "kv")).isTrue();
        assertThat(MappingFacts.readsInput(chained, "k")).isFalse();
    }

    static Stream<Arguments> rootedPaths() {
        return Stream.of(
                Arguments.of("normalize-space(replace(//*:EventData/*:Data, ' \\| ', '; '))", "//*:EventData/*:Data"),
                Arguments.of("(//data)[1]/@value", "//data"),
                Arguments.of("data[@name = /records/@host]", "/records/@host"),
                Arguments.of("string(.) and //x", "//x"),
                Arguments.of(".//*:EventData/*:Data", null),
                Arguments.of("data[@name='a']//b", null),
                Arguments.of("$record//data", null),
                Arguments.of("concat('//', data, ' / ')", null),
                Arguments.of("count(data) div 2", null));
    }

    @ParameterizedTest
    @MethodSource("rootedPaths")
    void pathsFromTheDocumentRootAreToldFromPathsFromTheRecord(final String xpath, final String rooted) {
        assertThat(MappingFacts.rootedPath(xpath)).isEqualTo(rooted);
    }

    @Test
    void anXpathFromTheDocumentRootIsRefusedAsItReadsEveryRecordsValueWhenProcessed() {
        // Seen: //*:EventData/*:Data stepped clean, one record a document, then processing stopped on the whole
        // stream: "A sequence of more than one item is not allowed as the first argument of fn:replace()".
        final List<Map<String, Object>> common = plus(BASE.subList(0, 5), json("""
                {"path": "EventSource/User/Id", "xpath": "upper-case(//data[@name='user']/@value)"}"""));
        final TranslationMapping rooted = mapping(with("common", common));
        final XsltGenerator.Generated result = generate(rooted);
        assertThat(result.ok()).isFalse();
        final String said = result.problems().stream().filter(p -> p.contains("document root")).findFirst()
                .orElseThrow();
        assertThat(said).contains("(//data[@name='user']/@value)").contains("processing reads a stream")
                .contains("Start from the record: .//data[@name='user']/@value");
        final TranslationMapping relative = mapping(with("common", plus(BASE.subList(0, 5), json("""
                {"path": "EventSource/User/Id", "xpath": "upper-case(.//data[@name='user']/@value)"}"""))));
        assertThat(generate(relative).ok()).isTrue();
        // A source's own XML document may hold a value outside its records, read from the root: said, not refused.
        final Map<String, Object> header = new LinkedHashMap<>(rooted.toJson(DumpMode.WITHOUT_NONE));
        header.put("input", "xml");
        header.put("root", "logons");
        header.put("record", "logon");
        final XsltGenerator.Generated xml = generate(TranslationMapping.fromJson(header));
        assertThat(xml.ok()).isTrue();
        assertThat(xml.warnings()).anySatisfy(w -> assertThat(w).contains("document root")
                .contains("outside the records"));
    }

    @Test
    void anElementASharedTemplateWritesCannotBeMappedToo() {
        // BASE maps EventSource/Device/HostName
        final XsltGenerator.Generated result = generate(mapping(with("shared", json("""
                [{"href": "Common-Event-V1", "template": "eventSourceDevice", "at": "EventSource/Device"}]"""))));
        assertThat(result.ok()).isFalse();
        assertThat(result.problems()).anySatisfy(p -> assertThat(p)
                .contains("EventSource/Device is written by the shared template eventSourceDevice (Common-Event-V1), "
                          + "so it must not be mapped as well")
                .contains("Event/EventSource/Device/HostName"));
        final XsltGenerator.Generated nowhere = generate(mapping(with("shared", json("""
                [{"href": "C", "template": "t", "at": "EventSource/Nowhere"}]"""))));
        assertThat(nowhere.problems()).anySatisfy(p -> assertThat(p).startsWith("shared t (C): at "
                                                                                + "EventSource/Nowhere"));
    }

    @Test
    void aValueNoElementMeansIsOfferedAsDataOfTheActionElement() {
        // Seen in VS Code: the agent invented elements (Network/Protocol, Alert/AlertSeverity) for fields the schema
        // has none for, was refused, and in the end left the events Unknown.
        final List<Map<String, Object>> traffic = new ArrayList<>(json("""
                [{"path": "EventDetail/TypeId", "value": "Traffic"},
                 {"path": "EventDetail/Network/Permit/Source/Device/IPAddress", "field": "ip"},
                 {"path": "EventDetail/Network/Protocol", "field": "result"}]"""));
        final List<Map<String, Object>> alert = new ArrayList<>(json("""
                [{"path": "EventDetail/TypeId", "value": "Alert"},
                 {"path": "EventDetail/Alert/Type", "value": "Other"},
                 {"path": "EventDetail/Alert/AlertSeverity", "field": "result"}]"""));
        final List<String> problems = XsltGenerator.generate(mapping(with("events", List.of(rule("traffic",
                List.of(equalsCondition("action", "connect")), traffic), rule("alert", List.of(), alert)))),
                SCHEMA_352, "3.5.2").problems();
        final String network = problems.stream().filter(p -> p.contains("Network/Protocol")).findFirst()
                .orElseThrow();
        assertThat(network).contains("{\"path\": \"EventDetail/Network/Permit/Data\", \"data_name\": \"result\", "
                                     + "\"field\": \"result\"}");
        final String severity = problems.stream().filter(p -> p.contains("AlertSeverity")).findFirst().orElseThrow();
        assertThat(severity).contains("Did you mean ['Severity']").contains("\"path\": \"EventDetail/Alert/Data\"")
                .contains("no reason to leave the event Unknown");
        // Carried as Data, as offered, they generate.
        traffic.set(2, json("""
                {"path": "EventDetail/Network/Permit/Data", "data_name": "result", "field": "result"}"""));
        alert.set(2, json("{\"path\": \"EventDetail/Alert/Data\", \"data_name\": \"result\", \"field\": \"result\"}"));
        assertThat(XsltGenerator.generate(mapping(with("events", List.of(rule("traffic",
                List.of(equalsCondition("action", "connect")), traffic), rule("alert", List.of(), alert)))),
                SCHEMA_352, "3.5.2").ok()).isTrue();
    }

    @Test
    void anInventedChildIsOfferedAsDataOfTheNearestElementThatTakesIt() {
        // destination_key given a made-up Destination/Key: Data under Destination, not under the action element.
        final List<String> problems = XsltGenerator.generate(mapping(with("events", List.of(rule("c",
                List.of(equalsCondition("action", "connect")), json("""
                        [{"path": "EventDetail/TypeId", "value": "Connect"},
                         {"path": "EventDetail/Network/Connect/Source/Device/IPAddress", "field": "ip"},
                         {"path": "EventDetail/Network/Connect/Destination/Key", "field": "result"}]"""))))),
                SCHEMA_352, "3.5.2").problems();
        assertThat(problems.stream().filter(p -> p.contains("Destination/Key")).findFirst().orElseThrow())
                .contains("{\"path\": \"EventDetail/Network/Connect/Destination/Data\", \"data_name\": \"result\", "
                          + "\"field\": \"result\"}");
    }

    static Stream<Arguments> functionCalls() {
        return Stream.of(
                Arguments.of("{\"path\": \"EventDetail/Authenticate/Action\", \"field\": \"extract(EventData/Data, "
                             + "'Action: \\\\[([^\\\\]]+)\\\\]')\"}",
                        "is a function call, not an input field. To take values out of a text field with a regular "
                        + "expression, add an entry to the mapping's extract list"),
                Arguments.of("{\"path\": \"EventSource/User/Id\", \"field\": \"stroom:extract(EventData/Data, "
                             + "'User: (\\\\S+)')\"}", "mapping's extract list"),
                Arguments.of("{\"path\": \"EventSource/User/Id\", \"any_of\": [\"user\", \"lower-case(name)\"]}",
                        "is an expression, not an input field: give it as xpath"));
    }

    @ParameterizedTest
    @MethodSource("functionCalls")
    void aFunctionCallIsNotTakenAsAField(final String entry, final String message) {
        // Seen (Gemma 4 31B, VS Code): every field an extract() call, written into the XSLT as XPath, and each step
        // failed to compile until the agent gave up.
        final List<Object> fields = List.of((Object) json(entry));
        assertThatThrownBy(() -> mapping(with("events", List.of(rule("logon", List.of(), fields)))))
                .isInstanceOf(MappingValidationException.class)
                .hasMessageContaining(message);
        assertThatThrownBy(() -> mapping(with("events", List.of(rule("logon", json("""
                [{"field": "extract(msg, \\"a\\")", "present": true}]"""), json("""
                [{"path": "EventDetail/TypeId", "value": "x"}]"""))))))
                .isInstanceOf(MappingValidationException.class)
                .hasMessageContaining("is a function call");
        assertThat(mapping(with("events", List.of(rule("logon", List.of(), json("""
                [{"path": "EventDetail/TypeId", "field": "System/Provider/@Name"}]""")))))).isNotNull();
    }

    @Test
    void theMappingErrorsSayWhereAsTheServerSaysIt() {
        // The agent is shown where each error is (events[0].fields[1].path, as mapping_problems writes it) and
        // pydantic's message.
        final MappingValidationException e = org.assertj.core.api.Assertions.catchThrowableOfType(
                MappingValidationException.class, () -> TranslationMapping.parse(json("""
                        {"input": "json", "events": [{"name": "a", "fields": [{"path": "x", "field": "a"},
                         {"field": "b"}]}]}""")));
        assertThat(e.errors()).hasSize(1);
        assertThat(e.errors().getFirst().where()).isEqualTo("events[0].fields[1].path");
        assertThat(e.errors().getFirst().message()).isEqualTo("Field required");
        assertThat(e.errors().getFirst().type()).isEqualTo("missing");
        assertThat(e.getMessage()).startsWith("1 validation error for TranslationMapping\nevents.0.fields.1.path\n"
                                              + "  Field required");
    }
}
