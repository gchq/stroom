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

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// Every kind of entry and condition fed no value, one, and several: from a field the record holds twice, an xpath
/// selecting both, and an xpath splitting a delimited text (seen in testing: a `tokenize()` group list with
/// `repeat` failed every record holding two groups, as its guard had no effective boolean value). An entry without
/// `repeat` writes the first value with text; one with `repeat` writes each; a condition holds when any value
/// passes. Each XSLT is run with Saxon and its Events validated.
class TestXsltGeneratorMultiValues extends XsltRuns {

    private static final List<String> NONE = List.of();
    private static final List<String> ONE = List.of("a");
    private static final List<String> TWO = List.of("a", "b");
    private static final String T1 = "2026-09-28T10:00:00.000Z";
    private static final String T2 = "2026-09-28T11:00:00.000Z";

    @TestFactory
    Stream<DynamicTest> aPlainEntryWritesTheFirstValue() {
        return eachSource(source -> {
            final Map<String, Object> entry = entry(source, "v", "EventSource/Device/Name");
            assertThat(text(common(entry, source, "v", NONE), "EventSource/Device/Name")).isNull();
            assertThat(text(common(entry, source, "v", ONE), "EventSource/Device/Name")).isEqualTo("a");
            assertThat(text(common(entry, source, "v", TWO), "EventSource/Device/Name")).isEqualTo("a");
        });
    }

    @TestFactory
    Stream<DynamicTest> aTransformedEntryWritesTheFirstValueTransformed() {
        return eachSource(source -> {
            final Map<String, Object> entry = with(entry(source, "v", "EventSource/Device/Name"),
                    "transform", "upper");
            assertThat(text(common(entry, source, "v", NONE), "EventSource/Device/Name")).isNull();
            assertThat(text(common(entry, source, "v", TWO), "EventSource/Device/Name")).isEqualTo("A");
        });
    }

    @TestFactory
    Stream<DynamicTest> anEntryWithADefaultWritesTheFirstValueElseTheDefault() {
        return eachSource(source -> {
            final Map<String, Object> entry = with(entry(source, "v", "EventSource/Device/Name"), "default", "x");
            assertThat(text(common(entry, source, "v", NONE), "EventSource/Device/Name")).isEqualTo("x");
            assertThat(text(common(entry, source, "v", TWO), "EventSource/Device/Name")).isEqualTo("a");
        });
    }

    @TestFactory
    Stream<DynamicTest> aMapWrittenInlineMapsTheFirstValue() {
        return eachSource(source -> checkMap(source, Map.of()));
    }

    @TestFactory
    Stream<DynamicTest> aMapWrittenAsAnXslMapMapsTheFirstValue() {
        return eachSource(source -> checkMap(source, Map.of("inline_map_max_keys", 0)));
    }

    @TestFactory
    Stream<DynamicTest> aDataEntryWritesTheFirstValue() {
        return eachSource(source -> {
            final Map<String, Object> entry = with(entry(source, "v", "EventDetail/Authenticate/Data"),
                    "data_name", "m");
            assertThat(data(logon(List.of(entry), source, "v", NONE), "m")).isEmpty();
            assertThat(data(logon(List.of(entry), source, "v", ONE), "m")).containsExactly("a");
            assertThat(data(logon(List.of(entry), source, "v", TWO), "m")).containsExactly("a");
        });
    }

    @TestFactory
    Stream<DynamicTest> aDataEntryInADataParentWritesTheFirstValueAndNoEmptyParent() {
        return eachSource(source -> {
            final Map<String, Object> entry = with(with(entry(source, "v", "EventDetail/Authenticate/Data"),
                    "data_name", "m"), "data_parent", "p");
            assertThat(having(logon(List.of(entry), source, "v", NONE), EVT, "Data", "Name", "p")).isEmpty();
            final List<XmlElement> parents = having(logon(List.of(entry), source, "v", TWO), EVT, "Data", "Name",
                    "p");
            assertThat(parents).singleElement().satisfies(parent -> assertThat(children(parent, "Data"))
                    .extracting(d -> d.get("Name") + "=" + d.get("Value")).containsExactly("m=a"));
        });
    }

    @TestFactory
    Stream<DynamicTest> aRepeatedEntryWritesEachValue() {
        return eachSource(source -> {
            final Map<String, Object> entry = with(entry(source, "v", "EventSource/User/Groups/Group/Name"),
                    "repeat", true);
            assertThat(find(common(entry, source, "v", NONE), "EventSource/User/Groups")).isNull();
            assertThat(groupNames(common(entry, source, "v", ONE))).containsExactly("a");
            assertThat(groupNames(common(entry, source, "v", TWO))).containsExactly("a", "b");
        });
    }

    @TestFactory
    Stream<DynamicTest> aRepeatedDataEntryWritesEachValue() {
        return eachSource(source -> {
            final Map<String, Object> entry = with(with(entry(source, "v", "EventDetail/Authenticate/Data"),
                    "data_name", "m"), "repeat", true);
            assertThat(data(logon(List.of(entry), source, "v", NONE), "m")).isEmpty();
            assertThat(data(logon(List.of(entry), source, "v", TWO), "m")).containsExactly("a", "b");
        });
    }

    @TestFactory
    Stream<DynamicTest> aTimeWrittenAsItIsTakesTheFirstValue() {
        return eachSource(source -> {
            final Map<String, Object> entry = entry(source, "t", "EventTime/TimeCreated");
            assertThat(text(time(entry, source, List.of(T1)), "EventTime/TimeCreated")).isEqualTo(T1);
            assertThat(text(time(entry, source, List.of(T1, T2)), "EventTime/TimeCreated")).isEqualTo(T1);
        });
    }

    @TestFactory
    Stream<DynamicTest> aTimeWithAFormatTakesTheFirstValue() {
        return eachSource(source -> {
            final Map<String, Object> entry = with(with(entry(source, "t", "EventTime/TimeCreated"),
                    "time_format", "yyyy-MM-dd HH:mm:ss"), "timezone", "UTC");
            assertThat(text(time(entry, source, List.of("2026-09-28 10:00:00", "2026-09-28 11:00:00")),
                    "EventTime/TimeCreated")).isEqualTo(T1);
        });
    }

    @TestFactory
    Stream<DynamicTest> aConditionHoldsWhenAnyValuePasses() {
        final Map<String, Map<String, Object>> tests = new LinkedHashMap<>();
        tests.put("equals", Map.of("equals", "b"));
        tests.put("one_of", Map.of("one_of", List.of("b", "c")));
        tests.put("matches", Map.of("matches", "^b$"));
        return Stream.of(Source.values()).flatMap(source -> tests.entrySet().stream().map(test ->
                DynamicTest.dynamicTest(source + " " + test.getKey(), () -> {
                    final Map<String, Object> condition = new LinkedHashMap<>(spec(source, "v"));
                    condition.putAll(test.getValue());
                    assertThat(typeId(when(condition, source, NONE))).isEqualTo("login");
                    assertThat(typeId(when(condition, source, ONE))).isEqualTo("login");
                    assertThat(typeId(when(condition, source, TWO))).isEqualTo("Logon");
                })));
    }

    @TestFactory
    Stream<DynamicTest> aPresenceConditionCountsAnyValue() {
        return eachSource(source -> {
            final Map<String, Object> present = with(spec(source, "v"), "present", true);
            assertThat(typeId(when(present, source, NONE))).isEqualTo("login");
            assertThat(typeId(when(present, source, TWO))).isEqualTo("Logon");
            final Map<String, Object> absent = with(spec(source, "v"), "present", false);
            assertThat(typeId(when(absent, source, NONE))).isEqualTo("Logon");
            assertThat(typeId(when(absent, source, TWO))).isEqualTo("login");
        });
    }

    @Test
    void anXpathEntryWithoutRepeatWarnsWhenItReadsSeveralValues() {
        // The warning goes where several values can come from the entry's own xpath; a field gives one value per
        // name, and a repeated entry writes each.
        final String warned = generated(entry(Source.XPATH_STRINGS, "v", "EventSource/Device/Name"));
        assertThat(warned).containsPattern("count\\(\\$[\\w-]+\\) gt 1")
                .contains("'EventSource/Device/Name, " + XsltGenerator.SEVERAL_VALUES);
        assertThat(generated(entry(Source.FIELD, "v", "EventSource/Device/Name")))
                .doesNotContain(XsltGenerator.SEVERAL_VALUES);
        assertThat(generated(with(entry(Source.XPATH_STRINGS, "v", "EventSource/User/Groups/Group/Name"),
                "repeat", true))).doesNotContain(XsltGenerator.SEVERAL_VALUES);
        // A Data entry warns too, its value written by the mcp:data function or in full.
        final List<Object> fields = new ArrayList<>(LOGON);
        fields.add(with(entry(Source.XPATH_STRINGS, "v", "EventDetail/Authenticate/Data"), "data_name", "m"));
        final XsltGenerator.Generated withData = XsltGenerator.generate(mapping(with("events",
                List.of(rule("logon", List.of(equalsCondition("action", "login")), fields), otherRule()))),
                SCHEMA, "4.1.0");
        assertThat(withData.xslt()).contains("'EventDetail/Authenticate/Data, " + XsltGenerator.SEVERAL_VALUES);
    }

    // The map's keys in this order tell the first value from any: with a then b, any value equal to b would map to
    // false, the first (a) maps to true; a first value that isn't a key writes nothing.
    private static void checkMap(final Source source, final Map<String, Object> style) {
        final Map<String, String> map = new LinkedHashMap<>();
        map.put("b", "false");
        map.put("a", "true");
        final Map<String, Object> entry = with(entry(source, "v", "EventDetail/Authenticate/Outcome/Success"),
                "map", map);
        final List<Object> fields = new ArrayList<>(LOGON.stream()
                .filter(field -> !entry.get("path").equals(((Map<?, ?>) field).get("path")))
                .toList());
        fields.add(entry);
        assertThat(text(run(BASE, fields, null, style, source, "v", NONE),
                "EventDetail/Authenticate/Outcome/Success")).isNull();
        assertThat(text(run(BASE, fields, null, style, source, "v", TWO),
                "EventDetail/Authenticate/Outcome/Success")).isEqualTo("true");
        assertThat(text(run(BASE, fields, null, style, source, "v", List.of("z", "b")),
                "EventDetail/Authenticate/Outcome/Success")).isNull();
    }

    private static String generated(final Map<String, Object> entry) {
        final XsltGenerator.Generated result = XsltGenerator.generate(mapping(with("common",
                GenerationFixtures.plus(BASE, entry))), SCHEMA, "4.1.0");
        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();
        return result.xslt();
    }

    private static Stream<DynamicTest> eachSource(final Consumer<Source> check) {
        return Stream.of(Source.values()).map(source -> DynamicTest.dynamicTest(source.toString(),
                () -> check.accept(source)));
    }

    // The field, or the xpath, an entry or condition reads its values from.
    private static Map<String, Object> spec(final Source source, final String name) {
        return switch (source) {
            case FIELD -> Map.of("field", name);
            case XPATH_NODES -> Map.of("xpath", "data[@name='" + name + "']/@value");
            case XPATH_STRINGS -> Map.of("xpath", "tokenize(data[@name='" + name + "s']/@value, ';')");
        };
    }

    private static Map<String, Object> entry(final Source source, final String name, final String path) {
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("path", path);
        entry.putAll(spec(source, name));
        return entry;
    }

    private static Map<String, Object> with(final Map<String, Object> map, final String key, final Object value) {
        final Map<String, Object> out = new LinkedHashMap<>(map);
        out.put(key, value);
        return out;
    }

    // The Event of a record with the values, the entry in common.
    private static XmlElement common(final Map<String, Object> entry,
                                     final Source source,
                                     final String name,
                                     final List<String> values) {
        return run(GenerationFixtures.plus(BASE, entry), LOGON, null, Map.of(), source, name, values);
    }

    // The Event of a record with the values, the entries added to the logon rule.
    private static XmlElement logon(final List<Map<String, Object>> entries,
                                    final Source source,
                                    final String name,
                                    final List<String> values) {
        final List<Object> fields = new ArrayList<>(LOGON);
        fields.addAll(entries);
        return run(BASE, fields, null, Map.of(), source, name, values);
    }

    // The Event of a record whose time is in the values, the entry writing TimeCreated.
    private static XmlElement time(final Map<String, Object> entry, final Source source, final List<String> values) {
        final List<Map<String, Object>> common = new ArrayList<>(BASE);
        common.set(0, entry);
        return run(common, LOGON, null, Map.of(), source, "t", values);
    }

    // The Event of a record with the values, the logon rule matching on the condition alone.
    private static XmlElement when(final Map<String, Object> condition,
                                   final Source source,
                                   final List<String> values) {
        return run(BASE, LOGON, condition, Map.of(), source, "v", values);
    }

    private static XmlElement run(final List<Map<String, Object>> common,
                                  final List<?> logonFields,
                                  final Map<String, Object> condition,
                                  final Map<String, Object> style,
                                  final Source source,
                                  final String name,
                                  final List<String> values) {
        final List<Object> when = condition == null
                ? List.of(equalsCondition("action", "login"))
                : List.of(condition);
        final Map<String, Object> overrides = new LinkedHashMap<>(with("common", common, "events",
                List.of(rule("logon", when, logonFields), otherRule())));
        if (!style.isEmpty()) {
            overrides.put("style", style);
        }
        final XsltGenerator.Generated result = XsltGenerator.generate(mapping(overrides), SCHEMA, "4.1.0");
        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();
        final XmlElement events = transform(result.xslt(), records(source, name, values));
        assertThat(invalid(events, XSD)).as(result.xslt()).isEmpty();
        return children(events, "Event").getFirst();
    }

    // One record: a login by dave, and the values, as the source gives them.
    private static String records(final Source source, final String name, final List<String> values) {
        final StringBuilder record = new StringBuilder("<record>");
        if (!"t".equals(name)) {
            record.append("<data name=\"time\" value=\"").append(T1).append("\"/>");
        }
        record.append("<data name=\"action\" value=\"login\"/><data name=\"user\" value=\"dave\"/>")
                .append("<data name=\"result\" value=\"ok\"/><data name=\"sid\" value=\"s1\"/>");
        if (source == Source.XPATH_STRINGS) {
            if (!values.isEmpty()) {
                record.append("<data name=\"").append(name).append("s\" value=\"").append(String.join(";", values))
                        .append("\"/>");
            }
        } else {
            values.forEach(value -> record.append("<data name=\"").append(name).append("\" value=\"").append(value)
                    .append("\"/>"));
        }
        return "<records xmlns=\"records:2\">" + record + "</record></records>";
    }

    private static List<String> data(final XmlElement event, final String name) {
        return having(event, EVT, "Data", "Name", name).stream().map(data -> data.get("Value")).toList();
    }

    private static List<String> groupNames(final XmlElement event) {
        final XmlElement groups = find(event, "EventSource/User/Groups");
        return groups == null
                ? List.of()
                : children(groups, "Group").stream().map(group -> text(group, "Name")).toList();
    }

    private static String typeId(final XmlElement event) {
        return text(event, "EventDetail/TypeId");
    }

    // Where an input's several values come from.
    private enum Source {
        // A field the record holds twice (a key given again).
        FIELD,
        // An xpath selecting every value of a field.
        XPATH_NODES,
        // An xpath splitting a delimited text into strings.
        XPATH_STRINGS;

        @Override
        public String toString() {
            return Arrays.stream(name().split("_")).map(String::toLowerCase).reduce((a, b) -> a + " " + b)
                    .orElse(name());
        }
    }
}
