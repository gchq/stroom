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

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The Stroom MCP server's generator tests (`tests/test_xsltgen.py`), case by case: the XSLT the ported generator
/// writes, run with Saxon over the tests' records and validated against the event-logging schema.
class TestXsltGenerator extends XsltRuns {

    private static XsltGenerator.Generated generate(final TranslationMapping mapping) {
        return XsltGenerator.generate(mapping, SCHEMA, "4.1.0");
    }

    private static Map<String, Object> logonRule(final List<Map<String, Object>> fields) {
        return rule("logon", List.of(equalsCondition("action", "login")), fields);
    }

    private static List<String> localNames(final XmlElement element) {
        return element.childElements().stream().map(XmlElement::localName).toList();
    }

    @Test
    void theGeneratedXsltWritesValidEventsInSchemaOrder() {
        final XsltGenerator.Generated result = generate(mapping());
        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();
        final XmlElement events = transform(result.xslt(), RECORDS);
        assertThat(invalid(events, XSD)).isEmpty();
        final List<XmlElement> written = children(events, "Event");
        final XmlElement logon = written.get(0);
        final XmlElement other = written.get(1);
        // map, Data attribute, and a quote in the input survive
        final XmlElement authenticate = descendant(logon, "Authenticate");
        assertThat(text(authenticate, "Outcome/Success")).isEqualTo("false");
        final XmlElement data = find(authenticate, "Data");
        assertThat(data.get("Name")).isEqualTo("session");
        assertThat(data.get("Value")).isEqualTo("s1");
        assertThat(data.attributes()).hasSize(2);
        assertThat(text(authenticate, "User/Id")).isEqualTo("o'neil");
        // an empty input with a default gets the default; without one, the element is left out
        assertThat(text(logon, "EventSource/Device/HostName")).isEqualTo("unknown");
        assertThat(find(other, "EventSource/User")).isNull();
        assertThat(text(other, "EventSource/Device/HostName")).isEqualTo("ws01");
        assertThat(localNames(authenticate)).containsExactly("Action", "User", "Outcome", "Data");
    }

    @Test
    void elementsRepeatedAcrossRulesAreWrittenOnceAsNamedTemplates() {
        final String xslt = generate(mapping(with("style", Map.of("layout", "inline")))).xslt();
        final XmlElement sheet = sheet(xslt);
        final Set<String> named = sheet.childElements(XSL, "template").stream()
                .map(template -> template.get("name"))
                .filter(name -> name != null)
                .collect(Collectors.toSet());
        assertThat(named).containsExactlyInAnyOrder("event_time", "event_source");
        assertThat(sheet.iterElements(EVT, "EventSource")).hasSize(1);
        assertThat(having(sheet, XSL, "call-template", "name", "event_source")).hasSize(2);
        assertThat(xslt).contains("<!-- event_source: shared by the rules 'logon', 'other' -->");
        // One rule: nothing repeats, so everything stays inline.
        final String single = generate(mapping(with("events", List.of(rule("logon", List.of(), LOGON)),
                "style", Map.of("layout", "inline")))).xslt();
        assertThat(single).doesNotContain("call-template");
    }

    @Test
    void onlyTheSameElementAtTheSamePathIsShared() {
        // EventSource/User and Authenticate/User come out alike (both read user), but they mean different things.
        final List<Map<String, Object>> logoff = LOGON.stream()
                .map(f -> "EventDetail/Authenticate/Action".equals(f.get("path"))
                        ? Map.<String, Object>of("path", f.get("path"), "value", "Logoff")
                        : f)
                .toList();
        final List<Object> rules = List.of(logonRule(LOGON), rule("logoff",
                List.of(equalsCondition("action", "logout")), logoff));
        final XsltGenerator.Generated result = generate(mapping(with("events", rules, "style",
                Map.of("layout", "inline"))));
        final XmlElement sheet = sheet(result.xslt());
        final Map<String, XmlElement> named = new LinkedHashMap<>();
        sheet.childElements(XSL, "template").stream().filter(t -> t.get("name") != null)
                .forEach(t -> named.put(t.get("name"), t));
        // Authenticate/User, called from both rules
        assertThat(descendant(named.get("user"), "User")).isNotNull();
        assertThat(having(named.get("event_source"), XSL, "call-template", "name", "user")).isEmpty();
        assertThat(descendant(named.get("event_source"), "User")).isNotNull();
        final XmlElement events = transform(result.xslt(), RECORDS);
        assertThat(invalid(events, XSD)).isEmpty();
        assertThat(text(descendant(events, "Authenticate"), "User/Id")).isEqualTo("o'neil");
    }

    // (name, reads in its scope) for every variable declared in a template or a rule.
    private static List<Map.Entry<String, Integer>> variableReads(final String xslt) {
        final List<Map.Entry<String, Integer>> found = new ArrayList<>();
        for (final XmlElement scope : sheet(xslt).iterElements()) {
            if (scope.is(XSL, "template") || scope.is(XSL, "when") || scope.is(XSL, "otherwise")) {
                final String text = XmlSerialiser.toXml(scope);
                for (final XmlElement variable : scope.childElements(XSL, "variable")) {
                    final Matcher reads = Pattern.compile("\\$" + Pattern.quote(variable.get("name")) + "(?![\\w.-])")
                            .matcher(text);
                    int n = 0;
                    while (reads.find()) {
                        n++;
                    }
                    found.add(Map.entry(variable.get("name"), n));
                }
            }
        }
        return found;
    }

    @Test
    void variablesOnlyForFieldsReadOftenAndDeclaredWhereUsed() {
        // user is read by Authenticate/User and by the session Data: four reads, all in the logon rule.
        final List<Map<String, Object>> logon = plus(LOGON, json("""
                {"path": "EventDetail/Authenticate/Data", "data_name": "account", "field": "user"}"""));
        final List<Object> rules = List.of(logonRule(logon), otherRule());
        final String xslt = generate(mapping(with("events", rules, "style", Map.of("layout", "inline")))).xslt();
        assertThat(variableReads(xslt)).allSatisfy(read -> assertThat(read.getValue()).isGreaterThanOrEqualTo(3));
        final XmlElement record = having(sheet(xslt), XSL, "template", "mode", "event").getFirst();
        // action is read by the rules' tests and the other rule: the template's; user only by the logon rule,
        // declared just before its first use there (asked for by the user: not hoisted to the start).
        assertThat(record.childElements(XSL, "variable")).extracting(v -> v.get("name")).containsExactly("action");
        final XmlElement when = record.childElements(XSL, "choose").getFirst().childElements(XSL, "when")
                .getFirst();
        final List<XmlElement> users = when.iterElements(XSL, "variable");
        assertThat(users).hasSize(1);
        final XmlElement user = users.getFirst();
        assertThat(user.get("name")).isEqualTo("user");
        assertThat(user.parent().is(XSL, "when")).isFalse();
        // the next element is the first to read it
        final XmlNode following = user.parent().children().get(user.parent().indexOf(user) + 1);
        assertThat(XmlSerialiser.toXml(following)).contains("$user");
        // A field read only by its own element stays where it is used, with a short guard, its value
        // interpolated. One line a Data entry: the function writes it only when the value is present (the guard,
        // once).
        assertThat(xslt).contains("<xsl:sequence select=\"mcp:data('session', data[@name='sid']/@value)\"/>");
        // Declared at the start of the rule instead, for a style guide that says so.
        final String top = generate(mapping(with("events", rules, "style", Map.of("layout", "inline", "variables",
                "top")))).xslt();
        final XmlElement rule = having(sheet(top), XSL, "template", "mode", "event").getFirst()
                .childElements(XSL, "choose").getFirst().childElements(XSL, "when").getFirst();
        assertThat(rule.childElements(XSL, "variable")).extracting(v -> v.get("name")).containsExactly("user");
        final XmlElement events = transform(xslt, RECORDS);
        assertThat(invalid(events, XSD)).isEmpty();
        assertThat(having(events, EVT, "Data", "Name", "account").getFirst().get("Value")).isEqualTo("o'neil");
    }

    @Test
    void styleSetsNamesAndWhenToUseVariables() {
        final Map<String, Object> style = json("""
                {"naming": "camelCase", "variable_min_reads": 1, "inline_map_max_keys": 0, "layout": "inline"}""");
        final String xslt = generate(mapping(with("style", style))).xslt();
        final XmlElement sheet = sheet(xslt);
        assertThat(sheet.childElements(XSL, "template").stream().map(t -> t.get("name")).filter(n -> n != null))
                .containsExactlyInAnyOrder("eventTime", "eventSource");
        // always xsl:map
        assertThat(sheet.childElements(XSL, "variable")).extracting(v -> v.get("name"))
                .containsExactly("resultToSuccess");
        final String body = XmlSerialiser.toXml(having(sheet, XSL, "template", "mode", "event").getFirst());
        // one read is enough
        assertThat(body).contains("$sid").contains("data[@name='sid']/@value[normalize-space(.)]");
        assertThat(invalid(transform(xslt, RECORDS), XSD)).isEmpty();
        assertThatThrownBy(() -> mapping(with("style", Map.of("naming", "SCREAMING"))))
                .isInstanceOf(MappingValidationException.class);
    }

    @Test
    void sharedOrLongValueMapsAreDeclaredOnceAsXslMaps() {
        final List<Object> rules = List.of(logonRule(plus(LOGON, json("""
                        {"path": "EventDetail/Authenticate/Data", "data_name": "outcome", "field": "result",
                         "map": {"ok": "true", "fail": "false"}}"""))),
                rule("other", List.of(), json("""
                        [{"path": "EventDetail/TypeId", "field": "action"},
                         {"path": "EventDetail/Unknown/Data", "data_name": "kind", "field": "action",
                          "map": {"login": "Logon"}, "default": "Other"}]""")));
        final XsltGenerator.Generated result = generate(mapping(with("events", rules)));
        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();
        final String xslt = result.xslt();
        final Map<String, Map<String, String>> maps = new LinkedHashMap<>();
        for (final XmlElement variable : sheet(xslt).childElements(XSL, "variable")) {
            final Map<String, String> entries = new LinkedHashMap<>();
            variable.childElements(XSL, "map").forEach(map -> map.childElements(XSL, "map-entry")
                    .forEach(entry -> entries.put(entry.get("key"), entry.get("select"))));
            maps.put(variable.get("name"), entries);
        }
        // Success and the outcome Data use the same map, so it is declared once; the one-key map used once reads
        // better inline.
        assertThat(maps).isEqualTo(Map.of("result_to_success", Map.of("'ok'", "'true'", "'fail'", "'false'")));
        // guard and value of Outcome/Success and of the Data
        assertThat(count(xslt, "$result_to_success?($result)")).isEqualTo(4);
        assertThat(xslt).contains("if ($action = 'login') then 'Logon' else 'Other'");
        final XmlElement events = transform(xslt, RECORDS);
        assertThat(invalid(events, XSD)).isEmpty();
        final XmlElement logon = children(events, "Event").get(0);
        final XmlElement other = children(events, "Event").get(1);
        assertThat(text(descendant(logon, "Authenticate"), "Outcome/Success")).isEqualTo("false");
        assertThat(having(logon, EVT, "Data", "Name", "outcome").getFirst().get("Value")).isEqualTo("false");
        // keepalive isn't a key: the default
        assertThat(having(other, EVT, "Data", "Name", "kind").getFirst().get("Value")).isEqualTo("Other");
    }

    @Test
    void jsonKeysAndXmlPathsAddressTheRecord() {
        final List<Map<String, Object>> common = plus(BASE.subList(0, 4), json("""
                {"path": "EventSource/User/Id", "field": "user.name"}"""), json("""
                {"path": "EventSource/Device/HostName", "field": "host"}"""));
        final XsltGenerator.Generated result = generate(mapping(with("input", "json", "common", common)));
        assertThat(result.xslt()).contains("*[@key='user']/*[@key='name']")
                .contains("xpath-default-namespace=\"http://www.w3.org/2013/XSL/json\"");
        final XmlElement events = transform(result.xslt(), """
                <array xmlns="http://www.w3.org/2013/XSL/json"><map>
                    <string key="time">2026-09-28T10:00:00.000Z</string><string key="action">login</string>\
                <string key="host">h</string>
                    <map key="user"><string key="name">dave</string></map><string key="result">ok</string>\
                </map></array>""");
        assertThat(invalid(events, XSD)).isEmpty();
        assertThat(text(descendant(events, "EventSource"), "User/Id")).isEqualTo("dave");
        final XsltGenerator.Generated xml = generate(mapping(with("input", "xml")));
        assertThat(xml.ok()).isFalse();
        assertThat(xml.problems().getFirst()).contains("xml input needs root and record");
    }

    @Test
    void timeFormatsBecomeStroomFormatDate() {
        final List<Map<String, Object>> common = new ArrayList<>();
        common.add(json("""
                {"path": "EventTime/TimeCreated", "field": "time", "time_format": "yyyy-MM-dd'T'HH:mm:ss",
                 "timezone": "UTC"}"""));
        common.addAll(BASE.subList(1, BASE.size()));
        assertThat(generate(mapping(with("common", common))).xslt())
                .contains("stroom:format-date(data[@name='time']/@value[1], 'yyyy-MM-dd''T''HH:mm:ss', 'UTC')");
        final List<Map<String, Object>> epoch = new ArrayList<>();
        epoch.add(json("{\"path\": \"EventTime/TimeCreated\", \"field\": \"time\", \"time_format\": \"epoch_s\"}"));
        epoch.addAll(BASE.subList(1, BASE.size()));
        assertThat(generate(mapping(with("common", epoch))).xslt()).contains("xs:integer(floor(xs:decimal(");
    }

    @Test
    void timesFinerThanAMillisecondAreCutNotRounded() throws SaxonApiException {
        // Seen in eval case 31: an epoch_ns time 0.555 ms into its millisecond was written a millisecond late, as
        // the Stroom MCP server's generator rounded half up (23:59:59.9996 became the next day).
        assertThat(epochMs("epoch_ns", "1790899198000555000")).isEqualTo("1790899198000");
        assertThat(epochMs("epoch_us", "1790899198000999")).isEqualTo("1790899198000");
        assertThat(epochMs("epoch_s", "1790899198.9999")).isEqualTo("1790899198999");
        // A pattern with digits below the millisecond is read by Stroom's format-date alone, which cuts them.
        final List<Map<String, Object>> common = new ArrayList<>();
        common.add(json("""
                {"path": "EventTime/TimeCreated", "field": "time", "time_format": "yyyy-MM-dd HH:mm:ss.SSSSSS",
                 "timezone": "UTC"}"""));
        common.addAll(BASE.subList(1, BASE.size()));
        final String xslt = generate(mapping(with("common", common))).xslt();
        assertThat(xslt).contains("stroom:format-date(data[@name='time']/@value[1], 'yyyy-MM-dd HH:mm:ss.SSSSSS', "
                                  + "'UTC')")
                .doesNotContain("mcp:time-ms");
    }

    @Test
    void dataEntriesAreWrittenInTheDataTheirDataParentNames() {
        // Asked for by the user: a tweet's mentions as <Data Name="mentions"><Data Name="mention" Value="..."/>...
        // </Data>, which the schema allows (Data holds Data, each with a Name) and the mapping couldn't express.
        final List<Object> fields = new ArrayList<>(LOGON);
        fields.add(json("""
                {"path": "EventDetail/Authenticate/Data", "data_parent": "mentions", "data_name": "mention",
                 "repeat": true,
                 "xpath": "for $w in tokenize(*[@key='text'], '\\\\s+')[starts-with(., '@')] \
                return substring($w, 2)"}"""));
        fields.add(json("""
                {"path": "EventDetail/Authenticate/Data", "data_parent": "mentions/by", "data_name": "client",
                 "field": "client"}"""));
        final List<Map<String, Object>> common = plus(BASE.subList(0, 4), json("""
                {"path": "EventSource/Device/HostName", "field": "host"}"""));
        final XsltGenerator.Generated result = generate(mapping(with("input", "json", "common", common, "events",
                List.of(rule("logon", List.of(), fields)))));
        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();

        final XmlElement events = transform(result.xslt(), """
                <array xmlns="http://www.w3.org/2013/XSL/json"><map>
                    <string key="time">2026-09-28T10:00:00.000Z</string><string key="host">h</string>\
                <string key="user">dave</string><string key="text">@pat @lee hello</string>\
                <string key="client">web</string>
                </map><map>
                    <string key="time">2026-09-28T10:01:00.000Z</string><string key="host">h</string>\
                <string key="user">dave</string><string key="text">no one</string>
                </map></array>""");

        assertThat(invalid(events, XSD)).isEmpty();
        final XmlElement first = children(events, "Event").get(0);
        final List<XmlElement> mentions = having(first, EVT, "Data", "Name", "mentions");
        assertThat(mentions).singleElement().satisfies(container -> {
            assertThat(container.get("Value")).isNull();
            assertThat(children(container, "Data")).extracting(d -> d.get("Name") + "=" + d.get("Value"))
                    .containsExactly("mention=pat", "mention=lee", "by=null");
            assertThat(having(container, EVT, "Data", "Name", "client").getFirst().get("Value")).isEqualTo("web");
        });
        // No mentions and no client: no container either.
        assertThat(having(children(events, "Event").get(1), EVT, "Data", "Name", "mentions")).isEmpty();
    }

    @Test
    void aDataParentGoesWithAPathEndingInDataAndItsDataName() {
        assertThatThrownBy(() -> FieldMapping.fromJson(json("""
                {"path": "EventSource/User/Id", "data_parent": "x", "field": "user"}""")))
                .hasMessageContaining("data_parent names the Data element(s) a Data entry is written in");
        assertThatThrownBy(() -> FieldMapping.fromJson(json("""
                {"path": "EventDetail/Authenticate/Data", "data_parent": "a//b", "data_name": "c", "field": "f"}""")))
                .hasMessageContaining("data_parent");
        // Written only when set, so a mapping without it dumps as the Python server's does.
        assertThat(FieldMapping.fromJson(json("""
                {"path": "EventDetail/Authenticate/Data", "data_name": "c", "field": "f"}""")).toJson(DumpMode.ALL))
                .doesNotContainKey("data_parent");
    }

    @Test
    void aTypeIdJoinsTheFieldsThatTellTheKindsApart() {
        // Seen: TypeId the SecretServer category alone ('User' for logons, logoffs and lockouts); the kind is the
        // category and the action together, both extracted from one message, which no xpath could join.
        final Map<String, Object> json = baseJson();
        json.put("extract", List.of(Map.of("field", "msg",
                "regex", "Event: \\[([^\\]]+)\\](?: Action: \\[([^\\]]+)\\])?",
                "names", List.of("event", "action"))));
        json.put("events", List.of(rule("any", List.of(), List.of(
                Map.of("path", "EventDetail/TypeId", "join", List.of("event", "action")),
                Map.of("path", "EventDetail/Authenticate/Action", "value", "Logon"),
                Map.of("path", "EventDetail/Authenticate/User/Id", "field", "user")))));
        final String records = "<records xmlns=\"records:2\"><record><data name=\"time\" value=\"t\"/>"
                               + "<data name=\"user\" value=\"pat\"/><data name=\"msg\" value=\"x Event: [User] "
                               + "Action: [Login Failed] y\"/></record><record><data name=\"time\" value=\"t\"/>"
                               + "<data name=\"user\" value=\"sam\"/><data name=\"msg\" value=\"Event: [Secret]\"/>"
                               + "</record></records>";

        final XsltGenerator.Generated generated = generate(TranslationMapping.fromJson(json));
        final List<XmlElement> events = children(transform(generated.xslt(), records), "Event");

        assertThat(events).extracting(event -> find(event, "EventDetail/TypeId").text())
                // Only the values there are: no separator left dangling.
                .containsExactly("User-Login Failed", "Secret");
        json.put("events", List.of(rule("any", List.of(), List.of(
                Map.of("path", "EventDetail/TypeId", "join", List.of("event", "action"), "separator", "."),
                Map.of("path", "EventDetail/Authenticate/Action", "value", "Logon"),
                Map.of("path", "EventDetail/Authenticate/User/Id", "field", "user")))));
        assertThat(children(transform(generate(TranslationMapping.fromJson(json)).xslt(), records), "Event"))
                .extracting(event -> find(event, "EventDetail/TypeId").text())
                .containsExactly("User.Login Failed", "Secret");
    }

    @Test
    void aFieldWrittenToAnElementAndAsDataIsWarnedOf() {
        // Seen: 'item_id' as View/Object/Id and as Data in 13 SecretServer rules, the drafted Data kept beside the
        // elements the agent mapped. A warning: a Data copy may be kept on purpose.
        final Map<String, Object> json = baseJson();
        json.put("events", List.of(rule("view", List.of(), List.of(
                Map.of("path", "EventDetail/TypeId", "join", List.of("action", "sid")),
                Map.of("path", "EventDetail/View/Document/Id", "field", "sid"),
                Map.of("path", "EventDetail/View/Document/Name", "field", "result", "transform", "upper"),
                Map.of("path", "EventDetail/View/Data", "data_name", "session", "field", "sid"),
                Map.of("path", "EventDetail/View/Data", "data_name", "result", "field", "result"),
                Map.of("path", "EventDetail/View/Data", "data_name", "action", "field", "action")))));

        assertThat(generate(TranslationMapping.fromJson(json)).warnings())
                .contains("[view] 'sid' is written as EventDetail/View/Document/Id and as Data 'session' "
                          + "(EventDetail/View/Data): the element holds it, so the Data entry says it again. Remove "
                          + "the Data entry, unless it is kept on purpose (the same Data across event types that "
                          + "don't all have the element).")
                // Changed on the way to its element (upper), or part of the TypeId: not the same value twice.
                .noneMatch(warning -> warning.contains("'result' is written as"))
                .noneMatch(warning -> warning.contains("'action' is written as"));
    }

    @Test
    void aJoinIsOfTwoOrMoreFieldsAndOnlyTheirText() {
        for (final String[] bad : new String[][]{
                {"{\"path\": \"EventDetail/TypeId\", \"join\": [\"event\"]}", "two or more"},
                {"{\"path\": \"EventDetail/TypeId\", \"field\": \"event\", \"separator\": \".\"}",
                 "separator goes with join"},
                {"{\"path\": \"EventDetail/TypeId\", \"join\": [\"a\", \"b\"], \"field\": \"a\"}",
                 "give exactly one of field, any_of, join"},
                {"{\"path\": \"EventTime/TimeCreated\", \"join\": [\"d\", \"t\"], \"time_format\": \"x\"}",
                 "no repeat, time_format or dictionary"}}) {
            assertThatThrownBy(() -> FieldMapping.fromJson(json(bad[0])))
                    .as(bad[0]).hasMessageContaining(bad[1]);
        }
        assertThat(FieldMapping.fromJson(json("{\"path\": \"EventDetail/TypeId\", \"join\": [\"a\", \"b\"], "
                                              + "\"separator\": \".\"}")).toJson(DumpMode.COMPACT))
                .containsEntry("join", List.of("a", "b")).containsEntry("separator", ".");
    }

    @Test
    void eachFieldNeedsExactlyOneSource() {
        assertThatThrownBy(() -> mapping(with("common", json("""
                [{"path": "EventSource/User/Id", "field": "user", "value": "x"}]"""))))
                .isInstanceOf(MappingValidationException.class)
                .hasMessageContaining("exactly one of field, any_of, join, value, xpath or lookup");
    }

    @Test
    void helpers() {
        assertThat(XPathText.literal("o'neil")).isEqualTo("'o''neil'");
        assertThat(MappingFacts.patternProblem("yyyy-MM-dd'T'HH:mm:ss.SSSX")).isNull();
        assertThat(MappingFacts.patternProblem("dd/MMM/yyyy:HH:mm:ss Z")).isNull();
    }

    @Test
    void dropRulesLeaveRecordsUntranslatedWithoutAWarning() {
        final List<Object> rules = List.of(json("""
                {"name": "keepalive", "drop": true, "when": [{"field": "action", "equals": "keepalive"}]}"""),
                logonRule(LOGON));
        // With warnings on, keepalive records are caught by their own rule before the warning.
        final String warned = generate(mapping(with("events", rules, "unmatched", "warn"))).xslt();
        assertThat(warned.indexOf("left untranslated on purpose")).isLessThan(warned.indexOf("stroom:log('WARN'"));
        final XsltGenerator.Generated result = generate(mapping(with("events", rules)));
        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();
        assertThat(result.events().getFirst()).isEqualTo(json("""
                {"event": "keepalive", "when": ["data[@name='action']/@value = 'keepalive'"], "dropped": true}"""));
        final XmlElement events = transform(result.xslt(), RECORDS);
        assertThat(invalid(events, XSD)).isEmpty();
        // the keepalive record wrote nothing
        assertThat(children(events, "Event")).hasSize(1);
        assertThat(result.xslt()).contains("keepalive: left untranslated on purpose");
    }

    @Test
    void v352ObjectsFromARepeatingChoiceAreAlternativesNotAllRequired() {
        // In 3.5.2, View's objects come from an extended base type whose choice repeats (maxOccurs unbounded).
        final List<Map<String, Object>> view = json("""
                [{"path": "EventDetail/TypeId", "value": "FileRead"},
                 {"path": "EventDetail/View/File/Path", "field": "action"}]""");
        final XsltGenerator.Generated result = XsltGenerator.generate(mapping(with("events",
                List.of(rule("view", List.of(), view)))), SCHEMA_352, "3.5.2");
        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();
        assertThat(invalid(transform(result.xslt(), RECORDS), XSD_352)).isEmpty();
        final List<Map<String, Object>> empty = json("""
                [{"path": "EventDetail/TypeId", "value": "FileRead"},
                 {"path": "EventDetail/View/Outcome/Success", "value": "true"}]""");
        final List<String> problems = XsltGenerator.generate(mapping(with("events", List.of(rule("view", List.of(),
                empty)))), SCHEMA_352, "3.5.2").problems();
        assertThat(problems).anySatisfy(p -> assertThat(p).contains("EventDetail/View needs one of").contains("File"));
    }

    @Test
    void theRuleMarkIsOnlyInTheDocumentationRun() {
        // From test_sampled_field_mapping_matches_events_to_rules_by_the_marker: the marker the documentation run
        // puts in each Event (its tables are ported with the documentation).
        final TranslationMapping m = mapping();
        assertThat(XsltGenerator.generate(m, SCHEMA, "4.1.0", true).xslt())
                .contains("<xsl:processing-instruction name=\"stroom-mcp-rule\">logon</xsl:processing-instruction>");
        assertThat(generate(m).xslt()).doesNotContain("stroom-mcp-rule");
    }

    @Test
    void anActionUserWithoutTheActingUserAndUnreadExtractionsAreWarnings() {
        // Haiku mapped the user to Authenticate/User only (case 13), and extracted src without reading it.
        final List<Map<String, Object>> fields = plus(LOGON.subList(0, 2), json("""
                {"path": "EventDetail/Authenticate/User/Id", "field": "who"}"""));
        final TranslationMapping m = mapping(with("common", without(BASE, "EventSource/User/Id"), "extract", json("""
                [{"field": "msg", "regex": "^(\\\\S+) src=(\\\\S+)$", "names": ["who", "src"]}]"""), "events",
                List.of(rule("logon", List.of(), fields))));
        final List<String> warnings = generate(m).warnings();
        assertThat(warnings).anySatisfy(w -> assertThat(w).startsWith("[logon] maps EventDetail/Authenticate/User but "
                                                                       + "not EventSource/User"));
        assertThat(String.join(" ", warnings)).contains("extract names ['src'], which nothing reads");
        // With both users mapped and every extracted name read, neither is said.
        final TranslationMapping fine = mapping(with("extract", json("""
                [{"field": "msg", "regex": "^(\\\\S+)$", "names": ["who"]}]"""), "events",
                List.of(rule("logon", List.of(), fields))));
        assertThat(generate(fine).warnings()).noneSatisfy(w -> assertThat(w.contains("EventSource/User")
                                                                          || w.contains("nothing reads")).isTrue());
    }

    @Test
    void sharedTemplatesAreImportedAndCalledInTheirElementsPlace() {
        final List<Map<String, Object>> base = BASE.stream()
                .filter(e -> !((String) e.get("path")).startsWith("EventSource/Device"))
                .toList();
        final XsltGenerator.Generated result = generate(mapping(with("common", base, "shared", json("""
                [{"href": "Common-Event-V1", "template": "eventSourceDevice", "at": "EventSource/Device"}]"""))));
        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();
        final XmlElement sheet = sheet(result.xslt());
        final XmlElement first = sheet.childElements().getFirst();
        assertThat(first.localName()).isEqualTo("import");
        assertThat(first.get("href")).isEqualTo("Common-Event-V1");
        assertThat(having(sheet, XSL, "call-template", "name", "eventSourceDevice")).hasSize(1);
        assertThat(sheet.iterElements(EVT, "Device")).isEmpty();
        // Run with the shared XSLT beside it, as Stroom resolves the import by name: one Device, in its place.
        final XmlElement events = transform(result.xslt(), RECORDS, Map.of("Common-Event-V1", """
                <xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="3.0">
                  <xsl:template name="eventSourceDevice">
                    <Device><HostName>from-meta</HostName></Device>
                  </xsl:template>
                  <xsl:template name="eventMeta">
                    <Meta><Source><Type>stream</Type><Id>guid-1</Id></Source></Meta>
                  </xsl:template>
                </xsl:stylesheet>"""));
        assertThat(invalid(events, XSD)).isEmpty();
        for (final XmlElement event : children(events, "Event")) {
            final XmlElement source = find(event, "EventSource");
            final List<String> expected = List.of("System", "Generator", "Device", "User");
            assertThat(localNames(source).subList(0, Math.min(4, localNames(source).size())))
                    .isEqualTo(expected.subList(0, Math.min(4, source.childElements().size())));
            assertThat(children(source, "Device")).hasSize(1);
            assertThat(text(event, "EventSource/Device/HostName")).isEqualTo("from-meta");
        }
    }

    @Test
    void aSharedTemplateAtEventLevelAndGeneratedNamesAvoidSharedOnes() {
        final XsltGenerator.Generated result = generate(mapping(with("shared", json("""
                [{"href": "Common-Event-V1", "template": "event_source", "at": "Meta"}]"""))));
        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();
        final XmlElement sheet = sheet(result.xslt());
        final List<String> named = sheet.childElements(XSL, "template").stream().map(t -> t.get("name"))
                .filter(n -> n != null).toList();
        // The generator's own EventSource template is not named event_source: that would override the shared one.
        assertThat(named).doesNotContain("event_source");
        assertThat(having(sheet, XSL, "call-template", "name", "event_source")).hasSize(2);
    }

    @Test
    void byDefaultEachEventKindAndSharedPartIsATemplateRuleWithItsOwnMode() {
        // The house style seen in the live translations: match="node()" mode="eventTypeLogon", applied with
        // select=".".
        final XsltGenerator.Generated result = generate(mapping(with("style", Map.of("naming", "camelCase"))));
        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();
        final XmlElement sheet = sheet(result.xslt());
        final Map<String, XmlElement> modes = new LinkedHashMap<>();
        having(sheet, XSL, "template", "match", "node()").forEach(t -> modes.put(t.get("mode"), t));
        assertThat(modes.keySet()).containsExactlyInAnyOrder("eventTypeLogon", "eventTypeOther", "eventTime",
                "eventSource");
        assertThat(sheet.childElements(XSL, "template").stream().filter(t -> t.get("name") != null)).isEmpty();
        assertThat(result.xslt()).doesNotContain("call-template");
        final XmlElement record = having(sheet, XSL, "template", "mode", "event").getFirst();
        final List<XmlElement> applied = record.iterElements(XSL, "apply-templates");
        assertThat(applied).extracting(a -> a.get("mode")).containsExactly("eventTypeLogon", "eventTypeOther");
        assertThat(applied).allSatisfy(a -> assertThat(a.get("select")).isEqualTo("."));
        final XmlElement event = children(modes.get("eventTypeLogon"), "Event").getFirst();
        assertThat(having(event, XSL, "apply-templates", "mode", "eventSource")).isNotEmpty();
        // the events are in their kinds' templates
        assertThat(record.iterElements(EVT, "Event")).isEmpty();
        final XmlElement events = transform(result.xslt(), RECORDS);
        assertThat(invalid(events, XSD)).isEmpty();
        assertThat(text(descendant(events, "Authenticate"), "User/Id")).isEqualTo("o'neil");
    }

    @Test
    void namedLayoutCallsATemplatePerEventKind() {
        final XsltGenerator.Generated result = generate(mapping(with("style", Map.of("layout", "named"))));
        final XmlElement sheet = sheet(result.xslt());
        assertThat(sheet.childElements(XSL, "template").stream().map(t -> t.get("name")).filter(n -> n != null))
                .containsExactlyInAnyOrder("event_type_logon", "event_type_other", "event_time", "event_source");
        final XmlElement record = having(sheet, XSL, "template", "mode", "event").getFirst();
        assertThat(record.iterElements(XSL, "call-template")).extracting(c -> c.get("name"))
                .containsExactly("event_type_logon", "event_type_other");
        assertThat(invalid(transform(result.xslt(), RECORDS), XSD)).isEmpty();
        assertThatThrownBy(() -> mapping(with("style", Map.of("layout", "one-big-template"))))
                .isInstanceOf(MappingValidationException.class);
    }

    @Test
    void sharedFunctionsAreImportedAndCalledInXpaths() {
        final List<Object> rules = List.of(logonRule(plus(LOGON, json("""
                {"path": "EventDetail/Authenticate/Data", "data_name": "loud",
                 "xpath": "gs:shout(data[@name='user']/@value)"}"""))), otherRule());
        final Map<String, Object> shared = json("""
                {"href": "Common Functions", "prefix": "gs", "namespace": "urn:gs"}""");
        final XsltGenerator.Generated result = generate(mapping(with("events", rules, "functions", List.of(shared))));
        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();
        final XmlElement sheet = sheet(result.xslt());
        assertThat(sheet.childElements(XSL, "import")).extracting(i -> i.get("href"))
                .containsExactly("Common Functions");
        assertThat(sheet.namespaces()).containsEntry("gs", "urn:gs");
        assertThat(sheet.get("exclude-result-prefixes").split(" ")).contains("gs");
        // Run as Stroom would, with the import resolved to the shared document.
        final XmlElement events = transform(result.xslt(), RECORDS, Map.of("Common Functions", """
                <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:gs="urn:gs"
                    xmlns:xs="http://www.w3.org/2001/XMLSchema" version="3.0">
                  <xsl:function name="gs:shout" as="xs:string"><xsl:param name="text" as="xs:string"/>
                    <xsl:value-of select="upper-case($text)"/></xsl:function>
                </xsl:stylesheet>"""));
        assertThat(having(events, EVT, "Data", "Name", "loud").getFirst().get("Value")).isEqualTo("O'NEIL");
        // A prefix called but not bound, and one taken by the XSLT itself, are problems; an unused entry a warning.
        assertThat(generate(mapping(with("events", rules))).problems())
                .anySatisfy(p -> assertThat(p).contains("calls gs:... functions, but no functions entry binds 'gs'"));
        final Map<String, Object> taken = new LinkedHashMap<>(shared);
        taken.put("prefix", "xs");
        assertThat(generate(mapping(with("events", rules, "functions", List.of(taken)))).problems())
                .anySatisfy(p -> assertThat(p).contains("prefix 'xs' is the XSLT's own"));
        final XsltGenerator.Generated unused = generate(mapping(with("functions", List.of(shared))));
        assertThat(unused.ok()).isTrue();
        assertThat(unused.warnings()).anySatisfy(w -> assertThat(w).contains("nothing calls gs:..."));
    }

    @Test
    void aConversionSeveralElementsUseIsOneFunctionOfTheXsltsOwn() {
        // The environment's translations keep repeated conversions in xsl:functions (gs:parseTimestamp): so does
        // this.
        final List<Map<String, Object>> logon = withTransform(LOGON, "EventDetail/Authenticate/User/Id");
        final List<Map<String, Object>> base = withTransform(BASE, "EventSource/User/Id");
        final List<Object> rules = List.of(logonRule(logon));
        final XsltGenerator.Generated result = generate(mapping(with("common", base, "events", rules)));
        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();
        final String xslt = result.xslt();
        assertThat(sheet(xslt).childElements(XSL, "function")).extracting(f -> f.get("name"))
                .filteredOn(name -> !"mcp:data".equals(name))
                .containsExactly("mcp:strip_domain");
        // defined once, called twice
        assertThat(count(xslt, "replace(replace(")).isEqualTo(1);
        assertThat(count(xslt, "mcp:strip_domain(")).isEqualTo(2);
        final XmlElement events = transform(xslt, """
                <records xmlns="records:2">
                <record><data name="time" value="2026-09-28T10:00:00.000Z"/><data name="action" value="login"/>\
                <data name="user" value="CORP\\bob"/>
                <data name="result" value="ok"/><data name="sid" value="s1"/></record>
                </records>""");
        assertThat(invalid(events, XSD)).isEmpty();
        assertThat(events.iterElements(EVT, "Id")).extracting(XmlElement::text).containsExactly("bob", "bob");
        // 0: always inline; one use: inline too.
        final String inline = generate(mapping(with("common", base, "events", rules, "style",
                Map.of("function_min_uses", 0)))).xslt();
        assertThat(inline).doesNotContain("mcp:strip_domain");
        assertThat(count(inline, "replace(replace(")).isEqualTo(2);
        assertThat(generate(mapping(with("events", rules))).xslt()).doesNotContain("mcp:strip_domain");
    }

    private static List<Map<String, Object>> withTransform(final List<Map<String, Object>> entries,
                                                           final String path) {
        return entries.stream().map(f -> {
            if (!path.equals(f.get("path"))) {
                return f;
            }
            final Map<String, Object> changed = new LinkedHashMap<>(f);
            changed.put("transform", "strip_domain");
            return changed;
        }).toList();
    }

    @Test
    void aTimeFormatSeveralElementsUseIsOneFunction() {
        final List<Map<String, Object>> common = new ArrayList<>();
        common.add(json("""
                {"path": "EventTime/TimeCreated", "field": "time", "time_format": "dd/MM/yyyy HH:mm:ss",
                 "timezone": "+10:00"}"""));
        common.addAll(BASE.subList(1, BASE.size()));
        final List<Object> rules = List.of(rule("logon", List.of(), plus(LOGON, json("""
                {"path": "EventDetail/Authenticate/Data", "data_name": "seen", "field": "time",
                 "time_format": "dd/MM/yyyy HH:mm:ss", "timezone": "+10:00"}"""), json("""
                {"path": "EventDetail/Authenticate/Data", "data_name": "stamp", "field": "sid",
                 "time_format": "epoch_ms"}"""))));
        final XsltGenerator.Generated result = generate(mapping(with("common", common, "events", rules, "style",
                Map.of("naming", "camelCase"))));
        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();
        final XmlElement sheet = sheet(result.xslt());
        final List<XmlElement> functions = sheet.childElements(XSL, "function").stream()
                .filter(f -> !"mcp:data".equals(f.get("name"))).toList();
        assertThat(functions).hasSize(1);
        assertThat(functions.getFirst().get("name")).isEqualTo("mcp:parseTime");
        assertThat(functions.getFirst().childElements(XSL, "sequence").getFirst().get("select"))
                .isEqualTo("stroom:format-date($value, 'dd/MM/yyyy HH:mm:ss', '+10:00')");
        assertThat(count(result.xslt(), "mcp:parseTime(")).isEqualTo(2);
        assertThat(sheet.get("exclude-result-prefixes").split(" ")).contains("mcp");
        // Used once, epoch_ms stays inline.
        assertThat(result.xslt()).contains("stroom:format-date(string(");
    }

    @Test
    void anEmptyConstantIsRefused() {
        // Seen: IPAddress given value '' for want of the address, and every event invalid for its empty element.
        assertThatThrownBy(() -> mapping(with("common", plus(BASE, json("""
                {"path": "EventSource/Client/IPAddress", "value": ""}""")))))
                .isInstanceOf(MappingValidationException.class)
                .hasMessageContaining("value '' writes an empty element");
    }

    @Test
    void dataValuesInterpolatedAndDataNamesInAStyle() {
        // An AGENTS style guide could say Value="{...}" and PascalCase Names, and the mapping had no way to follow it.
        final List<Map<String, Object>> logon = plus(LOGON, json("""
                {"path": "EventDetail/Authenticate/Data", "data_name": "server_node", "field": "host"}"""), json("""
                {"path": "EventDetail/Authenticate/Data", "data_name": "IPAddress", "value": "a{b}"}"""), json("""
                {"path": "EventDetail/Authenticate/Data", "data_name": "first_two",
                 "xpath": "replace(data[@name='user']/@value, '^(.{2}).*', '$1')"}"""));
        final TranslationMapping styled = mapping(with("events", List.of(logonRule(logon), otherRule()), "style",
                json("""
                        {"data_values": "interpolated", "data_names": "PascalCase", "data_entries": "guarded"}""")));
        // The names are the mapping's own from now on: the documentation and the checks see what the events hold.
        assertThat(styled.events().getFirst().fields().stream().map(FieldMapping::dataName).filter(n -> n != null))
                .containsExactly("Session", "ServerNode", "IPAddress", "FirstTwo");
        final XsltGenerator.Generated result = generate(styled);
        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();
        final String xslt = result.xslt();
        assertThat(xslt).contains("<Data Name=\"ServerNode\" Value=\"{").contains("<Data Name=\"IPAddress\" "
                                                                                 + "Value=\"a{{b}}\"/>");
        // read into a variable, its braces kept out of the value
        assertThat(xslt).contains("<Data Name=\"FirstTwo\" Value=\"{$");
        final XmlElement events = transform(xslt, RECORDS.replace("<data name=\"sid\" value=\"s1\"/>",
                "<data name=\"sid\" value=\"s1\"/><data name=\"host\" value=\"ws09\"/>"));
        assertThat(invalid(events, XSD)).isEmpty();
        final Map<String, String> data = new LinkedHashMap<>();
        events.iterElements(EVT, "Data").forEach(d -> data.put(d.get("Name"), d.get("Value")));
        assertThat(data).containsEntry("ServerNode", "ws09").containsEntry("IPAddress", "a{b}")
                .containsEntry("FirstTwo", "o'").containsEntry("Session", "s1");
        // Interpolated is the default now (asked for by the user); attribute is there for a style guide that wants
        // it.
        final String plain = generate(mapping(with("style", Map.of("data_entries", "guarded")))).xslt();
        assertThat(plain).contains("<Data Name=\"session\" Value=\"{").doesNotContain("<xsl:attribute name=\"Value\"");
        final String old = generate(mapping(with("style", Map.of("data_values", "attribute", "data_entries",
                "guarded")))).xslt();
        assertThat(old).contains("<xsl:attribute name=\"Value\"").contains("Name=\"session\"");
    }

    private static TranslationMapping firewallMapping(final Map<String, Object> style) {
        // A key=value message read through one extraction per key, and two Network rules writing the same Source,
        // Destination and Data: the shape of the FortiGate translation that came to 93 KB.
        final List<Object> extract = List.of(kv("srcip", true), kv("dstip", true), kv("dstintfrole", true),
                kv("proto", false), kv("user", true));
        final List<Object> rules = new ArrayList<>();
        for (final String[] kind : new String[][]{{"deny", "deny", "Deny"}, {"permit", "accept", "Permit"}}) {
            final String at = "EventDetail/Network/" + kind[2] + "/";
            rules.add(rule(kind[0], List.of(equalsCondition("action", kind[1])), json("""
                    [{"path": "EventDetail/TypeId", "value": "%s"},
                     {"path": "%sSource/Device/IPAddress", "field": "srcip"},
                     {"path": "%sDestination/Device/IPAddress", "field": "dstip"},
                     {"path": "%sData", "data_name": "dstintfrole", "field": "dstintfrole"},
                     {"path": "%sData", "data_name": "proto", "field": "proto"}]"""
                    .formatted(kind[0], at, at, at, at))));
        }
        final Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("input", "data_splitter");
        mapping.put("unmatched", "skip");
        mapping.put("common", plus(BASE.subList(0, 5), json("{\"path\": \"EventSource/User/Id\", \"field\": "
                                                            + "\"user\"}")));
        mapping.put("extract", extract);
        mapping.put("events", rules);
        if (!style.isEmpty()) {
            mapping.put("style", style);
        }
        return TranslationMapping.fromJson(mapping);
    }

    private static Map<String, Object> kv(final String key, final boolean quoted) {
        return Map.of("field", "msg", "regex", quoted
                ? "(?:^|\\s)" + key + "=\"([^\"]*)\""
                : "(?:^|\\s)" + key + "=(\\S+)", "names", List.of(key));
    }

    private static final String FIREWALL = """
            <records xmlns="records:2">
            <record><data name="time" value="2026-09-28T10:00:00.000Z"/><data name="action" value="deny"/>
            <data name="msg" value='srcip="10.0.0.1" dstip="10.0.0.2" dstintfrole="wan" proto=6 user="bob"'/></record>
            <record><data name="time" value="2026-09-28T10:01:00.000Z"/><data name="action" value="accept"/>
            <data name="msg" value='srcip="10.0.0.3" dstip="10.0.0.4" dstintfrole="lan" proto=17'/></record>
            </records>""";

    @Test
    void keyValueExtractionsAreOneFunctionAShapeAndBlocksAreSharedAcrossActions() {
        // Asked for by the user: the FortiGate translation declared ~30 analyze-string variables in each of four
        // rules, and wrote the same Source, Destination and Data four times.
        final XsltGenerator.Generated result = generate(firewallMapping(Map.of()));
        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();
        final String xslt = result.xslt();
        // Four quoted keys: one function, called with the key; one bare key (proto) keeps its own extraction.
        assertThat(count(xslt, "<xsl:function name=\"mcp:quoted_value\"")).isEqualTo(1);
        assertThat(xslt).contains("mcp:quoted_value($msg, 'dstintfrole')").doesNotContain("dstintfrole_parts")
                .contains("proto_parts");
        // Deny's and Permit's Source and Destination are written once, applied from each.
        final List<String> modes = sheet(xslt).childElements(XSL, "template").stream().map(t -> t.get("mode"))
                .toList();
        assertThat(modes).contains("source", "destination");
        // EventSource's User stays its own: the action-free key leaves paths outside an action alone.
        final XmlElement events = transform(xslt, FIREWALL);
        assertThat(invalid(events, XSD)).isEmpty();
        final XmlElement deny = children(events, "Event").get(0);
        final XmlElement permit = children(events, "Event").get(1);
        assertThat(text(descendant(deny, "Deny"), "Source/Device/IPAddress")).isEqualTo("10.0.0.1");
        assertThat(text(descendant(permit, "Permit"), "Destination/Device/IPAddress")).isEqualTo("10.0.0.4");
        final Map<String, String> data = new LinkedHashMap<>();
        permit.iterElements(EVT, "Data").forEach(d -> data.put(d.get("Name"), d.get("Value")));
        assertThat(data).isEqualTo(Map.of("dstintfrole", "lan", "proto", "17"));
        assertThat(text(deny, "EventSource/User/Id")).isEqualTo("bob");
        assertThat(find(permit, "EventSource/User")).isNull();
    }

    @Test
    void theActionFreePathKeepsEverythingButTheAction() {
        assertThat(MappingFacts.actionFree("EventDetail/Network/Deny/Source/Device"))
                .isEqualTo("EventDetail/Network/*/Source/Device");
        assertThat(MappingFacts.actionFree("EventDetail/Authenticate/Data")).isEqualTo("EventDetail/*/Data");
        assertThat(MappingFacts.actionFree("EventDetail/TypeId")).isEqualTo("EventDetail/TypeId");
        assertThat(MappingFacts.actionFree("EventSource/Client/IPAddress")).isEqualTo("EventSource/Client/IPAddress");
    }

    @Test
    @SuppressWarnings("unchecked")
    void dataEntriesAreOneLineEachAndRunsSeveralRulesWriteAreSharedWithTheSameEvents() {
        // Asked for by the user: a production translation's Data lists (135 guarded Data, 31 KB of 50 KB) repeated
        // the same thirty entries in four rules. The same Events come out of the compact form as out of the guarded
        // one.
        final java.util.function.Function<Map<String, Object>, TranslationMapping> withData = style -> {
            final Map<String, Object> m = firewallMapping(style).toJson(DumpMode.WITHOUT_NONE);
            final List<Object> extract = new ArrayList<>((List<Object>) m.get("extract"));
            for (final String k : List.of("srcintfrole", "logid", "service")) {
                extract.add(kv(k, true));
            }
            extract.add(Map.of("field", "msg", "regex", "(?:^|\\s)policyid=(\\S+)", "names", List.of("policyid")));
            m.put("extract", extract);
            final List<Object> rules = new ArrayList<>();
            for (final Object given : (List<Object>) m.get("events")) {
                final Map<String, Object> rule = new LinkedHashMap<>((Map<String, Object>) given);
                final List<Object> fields = new ArrayList<>((List<Object>) rule.get("fields"));
                final String element = ((String) ((Map<String, Object>) fields.get(1)).get("path")).split("/")[2];
                for (final String k : List.of("logid", "policyid", "service", "srcintfrole")) {
                    fields.add(Map.of("path", "EventDetail/Network/" + element + "/Data", "data_name", k, "field", k));
                }
                rule.put("fields", fields);
                rules.add(rule);
            }
            m.put("events", rules);
            return TranslationMapping.fromJson(m);
        };
        final XsltGenerator.Generated compact = generate(withData.apply(Map.of()));
        final XsltGenerator.Generated old = generate(withData.apply(Map.of("data_entries", "guarded",
                "data_run_min", 99)));
        assertThat(compact.ok()).as(String.valueOf(compact.problems())).isTrue();
        assertThat(old.ok()).as(String.valueOf(old.problems())).isTrue();
        final String xslt = compact.xslt();
        assertThat(xslt).contains("<xsl:sequence select=\"mcp:data('logid', mcp:quoted_value($msg, 'logid'))\"/>")
                .contains("<xsl:function name=\"mcp:data\" as=\"element()?\">")
                .doesNotContain("<xsl:if test=\"mcp:quoted_value");
        // The Data both Network rules write, a run, written once and applied in each.
        final List<String> runs = sheet(xslt).childElements(XSL, "template").stream()
                .map(t -> t.get("mode"))
                .filter(mode -> mode != null && mode.startsWith("data_"))
                .toList();
        assertThat(runs).hasSize(1);
        assertThat(count(xslt, "mode=\"" + runs.getFirst() + "\"/>")).isEqualTo(2);
        assertThat(old.xslt()).contains("<xsl:if test=").doesNotContain("mcp:data");
        // One record with the keys (one of them blank), one without them.
        final String records = FIREWALL.replace("proto=6 user=\"bob\"", "proto=6 user=\"bob\" logid=\"0001\" "
                                                                         + "service=\"\" srcintfrole=\"lan\" "
                                                                         + "policyid=7");
        final XmlElement newEvents = transform(xslt, records);
        final XmlElement oldEvents = transform(old.xslt(), records);
        assertThat(invalid(newEvents, XSD)).isEmpty();
        assertThat(XmlSerialiser.toXml(newEvents)).isEqualTo(XmlSerialiser.toXml(oldEvents));
        final List<String> written = newEvents.iterElements(EVT, "Data").stream().map(d -> d.get("Name")).toList();
        // a blank value: none
        assertThat(written).contains("logid", "srcintfrole", "policyid").doesNotContain("service");
    }

    @Test
    void aTypeIdReadFromAFieldTheRestMayLackFallsBackToTheRulesName() {
        // Seen in production: Unknown events from the rule for the rest had no TypeId (their records lacked the
        // field the kinds were named by), and failed schema validation.
        final TranslationMapping m = TranslationMapping.fromJson(json("""
                {"input": "json", "common": [
                  {"path": "EventTime/TimeCreated", "field": "time"},
                  {"path": "EventSource/System/Name", "value": "Acme"},
                  {"path": "EventSource/System/Environment", "value": "Prod"},
                  {"path": "EventSource/Generator", "value": "Acme"},
                  {"path": "EventSource/Device/HostName", "field": "host"},
                  {"path": "EventDetail/TypeId", "field": "kind"}], "events": [
                  {"name": "logon", "when": [{"field": "kind", "equals": "LOGON"}], "fields": [
                    {"path": "EventDetail/Authenticate/Action", "value": "Logon"},
                    {"path": "EventDetail/Authenticate/User/Id", "field": "user"}]},
                  {"name": "other", "allow_unknown": "no action element describes them",
                   "fields": [{"path": "EventDetail/Unknown/Data", "data_name": "msg", "field": "msg"}]}]}"""));
        final XsltGenerator.Generated result = XsltGenerator.generate(m, SCHEMA_352, "3.5.2");
        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();
        // The rule for the rest falls back to its name; the logon rule, whose condition says kind is there, doesn't.
        assertThat(result.xslt()).contains("then *[@key='kind'] else 'other'").doesNotContain("else 'logon'");
        assertThat(result.warnings()).anySatisfy(w -> assertThat(w).startsWith("[other] TypeId is read from kind"));
        assertThat(result.warnings()).noneSatisfy(w -> assertThat(w).startsWith("[logon] TypeId"));
    }

    @Test
    void theExampleMappingGenerates() {
        // test_generation.py: the shape of a mapping, whole, shown in a reply that refuses one, generates.
        final TranslationMapping example = TranslationMapping.fromJson(json("""
                {"input": "xml_fragments", "record": "Event", "xml_namespace": "records:2",
                 "common": [{"path": "EventTime/TimeCreated", "xpath": "System/TimeCreated/@SystemTime",
                             "time_format": "yyyy-MM-dd'T'HH:mm:ss.SSSX"},
                            {"path": "EventSource/System/Name", "value": "AppAudit"},
                            {"path": "EventSource/System/Environment", "value": "Prod"},
                            {"path": "EventSource/Generator", "value": "AppAudit"},
                            {"path": "EventSource/Device/HostName", "field": "Computer"}],
                 "events": [{"name": "view", "when": [{"field": "EventID", "equals": "4663"}],
                             "fields": [{"path": "EventDetail/TypeId", "field": "EventID"},
                                        {"path": "EventDetail/View/Document/Id",
                                         "xpath": "EventData/Data[@Name=\\"ObjectName\\"]"}]}]}"""));
        final XsltGenerator.Generated result = generate(example);
        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();
    }

    @Test
    void theResultIsThePythonGeneratorsDict() {
        final Map<String, Object> map = generate(mapping(with("for_each", "items"))).toMap();
        assertThat(map.keySet()).containsExactly("ok", "problems", "warnings", "events", "xslt", "reference_maps",
                "dictionaries", "items");
        assertThat(map.get("items")).isEqualTo("items");
        assertThat(generate(mapping()).toMap()).doesNotContainKey("items");
    }

    // The epoch time's milliseconds as the generated XSLT works them out (Stroom's format-date, absent here, left out).
    private static String epochMs(final String format, final String value) throws SaxonApiException {
        final String expression = XPathText.timeExpression(format, null, XPathText.literal(value))
                .replace("stroom:format-date(", "(");
        final XPathCompiler compiler = new Processor(false).newXPathCompiler();
        compiler.declareNamespace("xs", "http://www.w3.org/2001/XMLSchema");
        return compiler.evaluateSingle(expression, null).getStringValue();
    }
}
