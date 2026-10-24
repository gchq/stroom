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

import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.tools.validation.EventSchema.Child;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// Every action EventDetail may hold, generated and validated: asked for by the user, as the Stroom MCP server's
/// tests cover only the actions their samples happen to use (none used Search, Import, Uninstall or Approval).
///
/// The cases are made from the schema itself, so a new schema version is covered as it is. For each action, one
/// case per member of each choice directly in it (View's Document, Object and Resource, say), else one case; each
/// case maps the least the schema requires below the action (a value for each leaf it must hold, the first member
/// of each deeper choice), the mapping is generated, and its Event validated against the XSD. Deeper choices'
/// other members reuse the types the cases already reach, so they are not tried one by one.
class TestXsltGeneratorActions extends XsltRuns {

    // EventDetail's children that aren't actions.
    private static final Set<String> NOT_ACTIONS = Set.of("TypeId", "Description", "Classification", "Purpose");
    // Each a value one of a leaf's patterns may take.
    private static final List<String> CANDIDATES = List.of("x", "1", "10.0.0.1", "00:11:22:33:44:55",
            "2026-01-01T00:00:00.000Z", "user@example.com", "PT1S", "http://example.com", "fe80::1", "A1");
    // How deep below the action a case maps, so a type that holds itself ends.
    private static final int DEEPEST = 10;

    @TestFactory
    Stream<DynamicTest> everyActionOfTheSchemaGeneratesAValidEvent() {
        return Stream.of(new Version("4.1.0", SCHEMA, XSD), new Version("3.5.2", SCHEMA_352, XSD_352))
                .flatMap(version -> {
                    final List<ActionCase> cases = cases(version.schema());
                    // Every action, each case named after the choice it takes.
                    assertThat(cases.stream().map(ActionCase::action).distinct().count())
                            .isEqualTo(actions(version.schema()).size());
                    return cases.stream().map(c -> DynamicTest.dynamicTest(version.name() + " " + c.name(),
                            () -> check(version, c)));
                });
    }

    private static void check(final Version version, final ActionCase actionCase) {
        final List<Map<String, Object>> fields = new ArrayList<>();
        fields.add(Map.of("path", "EventDetail/TypeId", "value", actionCase.action()));
        fields.addAll(actionCase.fields());
        final Map<String, Object> json = baseJson();
        json.put("events", List.of(rule(actionCase.action(), List.of(), fields)));
        final XsltGenerator.Generated generated = XsltGenerator.generate(TranslationMapping.fromJson(json),
                version.schema(), version.name());
        assertThat(generated.ok()).as("generated from %s: %s", fields, generated.problems()).isTrue();
        final XmlElement events = transform(generated.xslt(), RECORDS);
        assertThat(children(events, "Event")).as("an Event per record").hasSize(2);
        // Holding the action, and the member of its choice the case takes.
        assertThat(children(events, "Event")).allSatisfy(event -> assertThat(find(event,
                "EventDetail/" + actionCase.name())).as("%s in %s", actionCase.name(), XmlSerialiser.toXml(event))
                .isNotNull());
        assertThat(invalid(events, version.xsd())).as("valid, from %s:\n%s", fields,
                XmlSerialiser.toXml(events)).isEmpty();
    }

    // --- cases ------------------------------------------------------------------------------------------------------

    private static List<Child> actions(final EventSchema schema) {
        final Child detail = schema.resolve("EventDetail").getLast();
        return schema.children(detail.decl()).stream()
                .filter(child -> !NOT_ACTIONS.contains(child.name()))
                .toList();
    }

    private static List<ActionCase> cases(final EventSchema schema) {
        final List<ActionCase> cases = new ArrayList<>();
        for (final Child action : actions(schema)) {
            final String path = "EventDetail/" + action.name();
            final List<Child> members = schema.isLeaf(action.decl())
                    ? List.of()
                    : schema.children(action.decl()).stream()
                            .filter(child -> child.choice() != null && !"Data".equals(child.name()))
                            .toList();
            if (members.isEmpty()) {
                cases.add(new ActionCase(action.name(), action.name(), fill(schema, action.decl(), path, null)));
            }
            for (final Child member : members) {
                cases.add(new ActionCase(action.name(), action.name() + "/" + member.name(),
                        fill(schema, action.decl(), path, member)));
            }
        }
        return cases;
    }

    // The least the schema requires below an element, as mapping entries: a value for each leaf it must hold, the
    // given member of a choice in it (else each required choice's first), and when it requires nothing, one child.
    private static List<Map<String, Object>> fill(final EventSchema schema,
                                                  final Element decl,
                                                  final String path,
                                                  final Child forced) {
        final List<Map<String, Object>> fields = new ArrayList<>();
        fill(schema, decl, path, forced, 0, fields);
        return fields;
    }

    private static void fill(final EventSchema schema,
                             final Element decl,
                             final String path,
                             final Child forced,
                             final int depth,
                             final List<Map<String, Object>> fields) {
        if (schema.isLeaf(decl)) {
            fields.add(Map.of("path", path, "value", value(schema, decl, path)));
            return;
        }
        if (depth > DEEPEST) {
            return;
        }
        // A type that holds itself (Objects holding an Association holding Objects) is left by another member.
        final Set<String> walked = Set.of(path.split("/"));
        final List<Child> children = schema.children(decl).stream()
                .filter(child -> !"Data".equals(child.name()))
                .filter(child -> child.required() || child == forced || !walked.contains(child.name()))
                .toList();
        final List<Child> chosen = new ArrayList<>(children.stream().filter(Child::required).toList());
        if (forced != null) {
            chosen.add(forced);
        }
        for (final Map.Entry<Integer, List<String>> choice : schema.requiredChoices().entrySet()) {
            final boolean here = children.stream().anyMatch(child -> choice.getKey().equals(child.choice()));
            final boolean taken = forced != null && choice.getKey().equals(forced.choice());
            if (here && !taken) {
                children.stream()
                        .filter(child -> choice.getKey().equals(child.choice()))
                        .findFirst()
                        .ifPresent(chosen::add);
            }
        }
        if (chosen.isEmpty() && children.isEmpty()) {
            // It holds nothing but Data: an element is only written with something in it.
            fields.add(Map.of("path", path + "/Data", "data_name", "x", "value", "x"));
            return;
        }
        if (chosen.isEmpty()) {
            // Nothing required: an element is only written with something in it, a leaf if it may hold one.
            children.stream()
                    .min((a, b) -> Boolean.compare(!schema.isLeaf(a.decl()), !schema.isLeaf(b.decl())))
                    .ifPresent(chosen::add);
        }
        for (final Child child : chosen) {
            fill(schema, child.decl(), path + "/" + child.name(), null, depth + 1, fields);
        }
    }

    // A value the leaf takes.
    private static String value(final EventSchema schema, final Element decl, final String path) {
        final List<String> allowed = schema.enumeration(decl);
        if (allowed != null && !allowed.isEmpty()) {
            return allowed.getFirst();
        }
        final List<String> patterns = schema.patterns(decl);
        if (!patterns.isEmpty()) {
            return CANDIDATES.stream()
                    .filter(candidate -> patterns.stream().anyMatch(p -> Pattern.matches(p, candidate)))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("no value for " + path + " matches " + patterns));
        }
        final String type = schema.baseType(decl);
        return switch (type == null
                ? "string"
                : type) {
            case "boolean" -> "true";
            case "dateTime" -> "2026-01-01T00:00:00.000Z";
            case "date" -> "2026-01-01";
            case "time" -> "00:00:00";
            case "duration" -> "PT1S";
            case "anyURI" -> "http://example.com";
            case "integer", "int", "long", "short", "byte", "decimal", "double", "float", "nonNegativeInteger",
                 "positiveInteger", "unsignedInt", "unsignedLong", "unsignedShort", "unsignedByte" -> "1";
            default -> "x";
        };
    }


    // --------------------------------------------------------------------------------


    private record Version(String name, EventSchema schema, String xsd) {

    }

    private record ActionCase(String action, String name, List<Map<String, Object>> fields) {

    }
}
