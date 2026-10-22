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

package stroom.ai.impl.tools.indexing;

import stroom.ai.impl.tools.indexing.ExampleNames.FieldGroup;
import stroom.ai.impl.tools.indexing.ExampleNames.Named;
import stroom.ai.impl.tools.indexing.ExampleTemplates.Built;
import stroom.ai.impl.tools.indexing.ExampleTemplates.ComponentsUsed;
import stroom.ai.impl.tools.indexing.ExampleTemplates.DataStream;
import stroom.ai.impl.tools.indexing.ExampleTemplates.LeftToEcs;
import stroom.ai.impl.tools.indexing.IndexTemplates.NamedTemplate;
import stroom.ai.impl.tools.indexing.IndexTemplates.StroomTemplate;
import stroom.ai.impl.tools.indexing.IndexingHelpers.TemplateTarget;
import stroom.ai.impl.tools.indexing.TemplateCheck.DocValue;
import stroom.ai.impl.tools.indexing.TemplateMappings.Composed;
import stroom.ai.impl.tools.indexing.TemplateMappings.Mapping;
import stroom.ai.impl.tools.indexing.TemplateMappings.NotIndexed;
import stroom.ai.impl.tools.sample.PyJson;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// The ported indexing core against the Stroom MCP server's own, call by call: `tools/indexing/recorded_calls.json`
/// holds calls made to `utils/ecs.py`, `utils/fieldplan.py`, `utils/templatecheck.py` and the pure helpers of
/// `tools/indexing.py` while the server's test suite ran (and from crafted inputs), each with its arguments and what
/// it returned or raised (at most 150 of each function, from every test that reached it). Each is made again on
/// the port, which must give the same result (or refuse with the same message).
///
/// Python's tuples and sets are written `{"__tuple__": [...]}` and `{"__set__": [sorted]}`; a document value as read
/// is a tuple of its kind and text.
class TestIndexingParity {

    private static final EcsSchema ECS = IndexingFixtures.ECS;

    @TestFactory
    Stream<DynamicTest> everyRecordedCallGivesWhatTheServerGave() {
        final List<Object> calls = calls();
        return calls.stream().map(item -> {
            final Map<String, Object> call = PyValues.map(item);
            return DynamicTest.dynamicTest(call.get("fn") + " " + call.get("test"), () -> check(call));
        });
    }

    @Test
    void everyFunctionIsReached() {
        final Set<String> functions = new TreeSet<>();
        calls().forEach(item -> functions.add((String) PyValues.map(item).get("fn")));
        assertThat(functions).contains("templatecheck.compare", "templatecheck.from_example",
                "templatecheck.names_from_example", "templatecheck.with_components", "templatecheck.compose",
                "templatecheck.composition", "templatecheck.json_xml_documents", "templatecheck.flatten_document",
                "templatecheck.dev_tools", "templatecheck.parse_template", "templatecheck.parse_component_templates",
                "ecs.check", "ecs.problems", "ecs.for_data_name", "ecs.source_namespace", "ecs.fields_in",
                "fieldplan.source_matches", "fieldplan.population_of", "fieldplan.any_action",
                "plan.elastic_template", "plan.required", "plan.convention_problems", "plan.events",
                "plan.lucene_fields", "indexing.components_wanted", "indexing.template_target");
    }

    private static List<Object> calls() {
        return PyValues.listOrEmpty(PyJson.loads(IndexingFixtures.resource(
                "/stroom/ai/impl/tools/indexing/recorded_calls.json")));
    }

    private static void check(final Map<String, Object> call) {
        final Args args = new Args(PyValues.listOrEmpty(call.get("args")), PyValues.mapOrEmpty(call.get("kwargs")));
        final String fn = (String) call.get("fn");
        if (call.containsKey("raises")) {
            final Throwable thrown = catchThrown(() -> run(fn, args, call));
            assertThat(thrown).as(fn + " raises").isNotNull();
            assertThat(thrown.getMessage()).isEqualTo(call.get("message"));
            return;
        }
        final Object result = run(fn, args, call);
        assertThat(PyJson.dumps(canonical(encode(result)))).isEqualTo(PyJson.dumps(canonical(call.get("result"))));
    }

    private static Throwable catchThrown(final Runnable runnable) {
        try {
            runnable.run();
            return null;
        } catch (final IllegalArgumentException | IndexingRefusal e) {
            return e;
        }
    }

    // The port's function for the server's, given the server's arguments.
    @SuppressWarnings("unchecked")
    private static Object run(final String fn, final Args args, final Map<String, Object> call) {
        return switch (fn) {
            case "ecs.check" -> ECS.check(args.string(0, "name"), args.string(1, "type_"));
            case "ecs.problems" -> ECS.problems(plannedFields(args.get(0, "fields")),
                    PyValues.strings(decode(args.get(1, "namespaces", List.of()))));
            case "ecs.source_namespace" -> ECS.sourceNamespace(args.string(0, "index_name"));
            case "ecs.for_data_name" -> ECS.forDataName(args.string(0, "data_name"));
            case "ecs.left_to_component" -> ECS.leftToComponent(args.string(0, "name"), args.string(1, "plan_type"));
            case "ecs.mapped_as_component" -> ECS.mappedAsComponent(args.string(0, "name"), args.map(1, "spec"));
            case "ecs.component_type" -> ECS.componentType(args.string(0, "name"));
            case "ecs.plan_type" -> ECS.planType(args.string(0, "name"));
            case "ecs.fields_in" -> ECS.fieldsIn(args.string(0, "prefix"),
                    ((Number) args.get(1, "limit", 200L)).intValue());
            case "ecs.top_level" -> ECS.topLevel();
            case "fieldplan.source_matches" -> FieldPlans.sourceMatches(args.string(0, "source"),
                    args.string(1, "path"));
            case "fieldplan.population_of" -> FieldPlans.populationOf(args.string(0, "source"),
                    (Map<String, Number>) (Map<?, ?>) args.map(1, "population"));
            case "fieldplan.any_action" -> FieldPlans.anyAction(args.string(0, "path"));
            case "indexing.components_wanted" -> IndexingHelpers.componentsWanted(args.map(0, "example"),
                    args.map(1, "components"), without(args.get(2, "without", List.of())));
            case "indexing.template_target" -> IndexingHelpers.templateTarget(args.string(0, "index"));
            case "indexing.missing_fields_said" -> IndexingHelpers.missingFieldsSaid(
                    (Map<String, List<String>>) (Map<?, ?>) args.map(0, "missing"), args.string(1, "index_name"));
            case "indexing.writes_to" -> writesTo(args);
            case "indexing._component_notes" -> IndexingHelpers.componentNotes(PyValues.strings(args.get(0,
                    "missing")));
            case "indexing._ecs_base" -> IndexingHelpers.ecsBase(args.map(0, "example"));
            case "plan.elastic_template" -> plan(call).elasticTemplate(args.string(0, "template_name"),
                    args.get(1, "priority", null) == null
                            ? null
                            : ((Number) args.get(1, "priority", null)).longValue(),
                    (Boolean) args.get(2, "leave_to_component", true), ECS);
            case "plan.lucene_fields" -> plan(call).luceneFields();
            case "plan.required" -> plan(call).required();
            case "plan.convention_problems" -> plan(call).conventionProblems(ECS);
            case "plan.events" -> plan(call).events();
            default -> runTemplateCheck(fn, args);
        };
    }

    @SuppressWarnings("unchecked")
    private static Object runTemplateCheck(final String fn, final Args args) {
        return switch (fn) {
            case "templatecheck.as_put" -> args.has(1, "fields")
                    ? IndexTemplates.asPut(args.get(0, "body"), PyValues.strings(decode(args.get(1, "fields"))))
                    : IndexTemplates.asPut(args.get(0, "body"));
            case "templatecheck.stroom_tool_templates" -> IndexTemplates.stroomToolTemplates(args.get(0, "text"));
            case "templatecheck.parse_template" -> IndexTemplates.parseTemplate(args.string(0, "text"));
            case "templatecheck.dev_tools" -> IndexTemplates.devTools(args.string(0, "name"), args.map(1, "body"));
            case "templatecheck.parse_component_templates" -> IndexTemplates.parseComponentTemplates(
                    PyValues.listOrEmpty(args.get(0, "texts")));
            case "templatecheck.with_components" -> ExampleTemplates.withComponents(args.map(0, "body"),
                    components(args.get(1, "components")), args.strings(2, "include"), args.strings(3, "exclude"),
                    ECS);
            case "templatecheck.leave_to_ecs_component" -> ExampleTemplates.leaveToEcsComponent(
                    args.map(0, "mappings"), ECS);
            case "templatecheck.not_indexed" -> TemplateMappings.notIndexed(args.map(0, "mappings"));
            case "templatecheck.unsearchable" -> TemplateMappings.unsearchable(args.map(0, "mappings"),
                    PyValues.strings(decode(args.get(1, "paths"))));
            case "templatecheck.ecs_duplicates" -> ExampleTemplates.ecsDuplicates(args.map(0, "body"), ECS);
            case "templatecheck.compose" -> TemplateMappings.compose(args.map(0, "body"),
                    components(args.get(1, "components")));
            case "templatecheck.read_mapping" -> TemplateMappings.readMapping(args.map(0, "mappings"));
            case "templatecheck.naming_style" -> ExampleNames.namingStyle(args.string(0, "name"));
            case "templatecheck.fits_style" -> ExampleNames.fitsStyle(args.string(0, "name"),
                    args.string(1, "style"));
            case "templatecheck.example_naming" -> ExampleNames.exampleNaming(args.strings(0, "fields"));
            case "templatecheck.example_dotted" -> ExampleNames.exampleDotted(args.strings(0, "fields"));
            case "templatecheck.field_group" -> ExampleNames.fieldGroup(args.string(0, "source"));
            case "templatecheck.nested_name" -> ExampleNames.nestedName(args.string(0, "source"),
                    args.string(1, "style"));
            case "templatecheck.names_from_example" -> ExampleNames.namesFromExample(
                    (List<Map<String, Object>>) (List<?>) PyValues.listOrEmpty(args.get(0, "fields")),
                    components(args.get(1, "example")), conventionNames(args.get(2, "convention_names")),
                    args.strings(3, "populated"));
            case "templatecheck.type_styles" -> TemplateMappings.typeStyles(components(args.get(0, "fields")));
            case "templatecheck.read_mapping_fields" -> TemplateMappings.readMappingFields(args.map(0, "body"));
            case "templatecheck.object_nodes" -> TemplateMappings.objectNodes(args.map(0, "mappings"));
            case "templatecheck.components_used" -> ExampleTemplates.componentsUsed(args.strings(0, "composed_of"),
                    components(args.get(1, "components")), args.map(2, "leaves"), args.strings(3, "to_ecs"),
                    (Boolean) args.get(4, "discovery", false), args.strings(5, "nominated"));
            case "templatecheck.composition" -> ExampleTemplates.composition(args.strings(0, "example_composed_of"),
                    args.strings(1, "include"), args.strings(2, "exclude"));
            case "templatecheck.from_example" -> ExampleTemplates.fromExample(args.map(0, "planned"),
                    args.map(1, "example"), components(args.get(2, "components")),
                    (Boolean) args.get(3, "discovery", false), args.strings(4, "include"),
                    args.strings(5, "exclude"), (Boolean) args.get(6, "ecs_base", false), ECS);
            case "templatecheck.json_xml_documents" -> TemplateCheck.jsonXmlDocuments(args.string(0, "xml"));
            case "templatecheck.flatten_document" -> TemplateCheck.flattenDocument(document(args.get(0, "doc")),
                    (String) args.get(1, "prefix", ""));
            case "templatecheck.compare" -> TemplateCheck.compare(args.map(0, "body"), documents(args.get(1, "docs")),
                    args.string(2, "index_name"), ECS);
            case "templatecheck.discovery_data_stream" -> ExampleTemplates.discoveryDataStream(args.map(0, "body"),
                    documents(args.get(1, "docs")), args.string(2, "field"));
            default -> throw new IllegalStateException("No port of " + fn);
        };
    }

    private static FieldPlan plan(final Map<String, Object> call) {
        return FieldPlan.fromJson(PyValues.map(call.get("plan")));
    }

    private static List<PlannedField> plannedFields(final Object value) {
        final List<PlannedField> fields = new ArrayList<>();
        for (final Object item : PyValues.listOrEmpty(value)) {
            fields.add(PlannedField.fromJson(PyValues.map(PyValues.map(item).get("data"))));
        }
        return fields;
    }

    private static List<String> without(final Object value) {
        final Object decoded = decode(value);
        return decoded instanceof final String one
                ? List.of(one)
                : PyValues.strings(decoded);
    }

    // The server's writes_to, the Elasticsearch side being the port's.
    private static boolean writesTo(final Args args) {
        final String docType = args.string(1, "doc_type");
        final String uuid = args.string(2, "uuid");
        final String indexName = args.string(3, "index_name");
        for (final Object pair : PyValues.listOrEmpty(PyValues.map(args.get(0, "properties")).get("__pairs__"))) {
            final List<Object> entry = PyValues.listOrEmpty(pair);
            // The key, (element, name), as decoded: a pair of strings.
            final String name = ((DocValue) entry.get(0)).text();
            final Object held = entry.get(1);
            final Object value = held instanceof final Map<?, ?> map && map.containsKey("value")
                    ? map.get("value")
                    : held;
            if ("Index".equals(docType) && "index".equals(name) && value instanceof final Map<?, ?> doc
                && uuid.equals(doc.get("uuid"))) {
                return true;
            }
            if ("ElasticIndex".equals(docType) && "indexName".equals(name) && value instanceof final String text
                && IndexingHelpers.writesToIndex(text, indexName)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> components(final Object value) {
        return (Map<String, Map<String, Object>>) (Map<?, ?>) PyValues.mapOrEmpty(value);
    }

    private static Map<String, Set<String>> conventionNames(final Object value) {
        final Map<String, Set<String>> out = new LinkedHashMap<>();
        PyValues.mapOrEmpty(value).forEach((path, names) -> {
            final Set<String> set = new LinkedHashSet<>();
            ((Collection<?>) decode(names)).forEach(name -> set.add((String) name));
            out.put(path, set);
        });
        return out;
    }

    private static List<Map<String, Object>> documents(final Object value) {
        final List<Map<String, Object>> docs = new ArrayList<>();
        for (final Object doc : PyValues.listOrEmpty(value)) {
            docs.add(document(doc));
        }
        return docs;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> document(final Object doc) {
        return (Map<String, Object>) decode(doc);
    }

    // A recorded value as the port takes it: a tuple of two (a document value) as a DocValue, a set as a set.
    private static Object decode(final Object value) {
        if (value instanceof final Map<?, ?> map) {
            if (map.size() == 1 && map.containsKey("__tuple__")) {
                final List<Object> items = PyValues.listOrEmpty(map.get("__tuple__"));
                if (items.size() == 2 && items.getFirst() instanceof final String kind
                    && (items.get(1) == null || items.get(1) instanceof String)) {
                    return new DocValue(kind, (String) items.get(1));
                }
                return items.stream().map(TestIndexingParity::decode).toList();
            }
            if (map.size() == 1 && map.containsKey("__set__")) {
                return new LinkedHashSet<>(PyValues.listOrEmpty(map.get("__set__")));
            }
            final Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((key, item) -> out.put((String) key, decode(item)));
            return out;
        }
        if (value instanceof final List<?> list) {
            return list.stream().map(TestIndexingParity::decode).toList();
        }
        return value;
    }

    // A port's result as the recorder wrote the server's.
    private static Object encode(final Object value) {
        return switch (value) {
            case null -> null;
            case final DocValue read -> tuple(read.kind(), read.text());
            case final Named named -> tuple(named.fields(), named.notes());
            case final Built built -> tuple(built.body(), built.notes());
            case final Composed composed -> tuple(composed.body(), composed.missing());
            case final NotIndexed hidden -> tuple(hidden.fields(), hidden.objects());
            case final LeftToEcs left -> tuple(left.mappings(), left.left());
            case final ComponentsUsed used -> tuple(used.used(), used.unused(), used.unsure());
            case final DataStream stream -> tuple(stream.body(), stream.note());
            case final NamedTemplate named -> tuple(named.name(), named.body());
            case final StroomTemplate template -> tuple(template.kind(), template.name(), template.body());
            case final FieldGroup group -> tuple(group.group(), group.rest());
            case final TemplateTarget target -> tuple(target.name(), target.indexName());
            case final Mapping mapping -> {
                final Map<String, Object> read = new LinkedHashMap<>();
                read.put("fields", encode(mapping.fields()));
                read.put("dynamic", encode(mapping.dynamic()));
                yield Map.of("__mapping__", read);
            }
            case final Set<?> set -> Map.of("__set__", new TreeSet<>(set.stream().map(String::valueOf).toList())
                    .stream().toList());
            case final Map<?, ?> map -> {
                final Map<String, Object> out = new LinkedHashMap<>();
                map.forEach((key, item) -> out.put(String.valueOf(key), encode(item)));
                yield out;
            }
            case final Collection<?> items -> items.stream().map(TestIndexingParity::encode).toList();
            default -> value;
        };
    }

    private static Map<String, Object> tuple(final Object... items) {
        final List<Object> encoded = new ArrayList<>();
        for (final Object item : items) {
            encoded.add(encode(item));
        }
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("__tuple__", encoded);
        return out;
    }

    // Numbers compared as numbers: Python's 70 and 70.0 are the same value.
    private static Object canonical(final Object value) {
        if (value instanceof final Map<?, ?> map) {
            final Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((key, item) -> out.put(String.valueOf(key), canonical(item)));
            return out;
        }
        if (value instanceof final List<?> list) {
            return list.stream().map(TestIndexingParity::canonical).toList();
        }
        if (value instanceof final Double number && number == Math.rint(number) && Math.abs(number) < 1e15) {
            return number.longValue();
        }
        if (value instanceof final Integer number) {
            return number.longValue();
        }
        return value;
    }


    // --------------------------------------------------------------------------------


    // A call's arguments, positional or by keyword.
    private record Args(List<Object> positional, Map<String, Object> keywords) {

        boolean has(final int at, final String name) {
            return at < positional.size() || keywords.containsKey(name);
        }

        Object get(final int at, final String name) {
            return get(at, name, null);
        }

        Object get(final int at, final String name, final Object defaultValue) {
            if (at < positional.size()) {
                return decode(positional.get(at));
            }
            return keywords.containsKey(name)
                    ? decode(keywords.get(name))
                    : defaultValue;
        }

        String string(final int at, final String name) {
            return (String) get(at, name);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> map(final int at, final String name) {
            return (Map<String, Object>) get(at, name);
        }

        List<String> strings(final int at, final String name) {
            return PyValues.strings(get(at, name, List.of()));
        }
    }
}
