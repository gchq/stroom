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

package stroom.ai.impl.tools.fielddoc;

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.generation.SharedTemplate;
import stroom.ai.impl.tools.generation.XmlElement;
import stroom.ai.impl.tools.generation.XmlParser;
import stroom.ai.impl.tools.indexing.Discovery;
import stroom.ai.impl.tools.indexing.EcsSchema;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.indexing.FieldPlans;
import stroom.ai.impl.tools.indexing.PlannedField;
import stroom.ai.impl.tools.indexing.TemplateCheck;
import stroom.ai.impl.tools.indexing.TemplateCheck.DocValue;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.util.shared.NullSafe;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/// The Field mapping section of an indexing pipeline's documentation (from its index plan, or the documents it
/// wrote), of an existing index (from its survey), and of a discovery pipeline: each index field with a
/// description, its type, the event-logging path it comes from, and from the sample how often it was populated and
/// its values; by event type (the fields any kind of event has, then a table per action element) or compact.
public final class IndexFieldDocs {

    // Where the bundled conventions are on the class path.
    private static final String CONVENTIONS = "/stroom/mcp/impl/knowledge/conventions/";

    private static final String RECORDS = "records:2";
    private static final String IN_SAMPLE = "In sample";
    // The most TypeIds named under a section.
    private static final int MAX_TYPE_IDS = 15;
    // What FieldDocs.actionOf gives a path that skips the action element (EventDetail//...).
    private static final String ANY_ACTION = "";
    // An index Stroom did not build may hold raw records, not events.
    private static final Map<String, String> DOCUMENT_IDS = Map.of(
            "StreamId", "The id of the stream the document came from.",
            "EventId", "The record's number in that stream; with StreamId, it identifies the document.");

    private IndexFieldDocs() {
        // Static only.
    }

    /// `index_documents`: the documents an indexing XSLT wrote, one per output record, as field to values:
    /// `records:2` `<data name value>` (Lucene), or the xpath-functions JSON XML the Elasticsearch filter reads
    /// (nested maps as dotted names).
    ///
    /// @param outputs The XSLT's outputs; one that doesn't parse is passed over.
    /// @return The documents.
    public static List<Map<String, List<String>>> indexDocuments(final List<String> outputs) {
        final List<Map<String, List<String>>> documents = new ArrayList<>();
        for (final String xml : outputs) {
            final XmlElement root;
            try {
                root = XmlParser.parse(xml);
            } catch (final XmlParser.XmlParseException e) {
                continue;
            }
            if (RECORDS.equals(root.namespace())) {
                for (final XmlElement record : root.iterElements(RECORDS, "record")) {
                    final Map<String, List<String>> document = new LinkedHashMap<>();
                    for (final XmlElement data : record.iterElements(RECORDS, "data")) {
                        if (JsonValues.truthy(data.get("name"))) {
                            document.computeIfAbsent(data.get("name"), k -> new ArrayList<>())
                                    .add(Objects.requireNonNullElse(data.get("value"), ""));
                        }
                    }
                    documents.add(document);
                }
            } else {
                for (final Map<String, Object> item : TemplateCheck.jsonXmlDocuments(xml)) {
                    // Values come as (JSON type, text); a nested map as ('object', None).
                    final Map<String, List<String>> document = new LinkedHashMap<>();
                    TemplateCheck.flattenDocument(item, "").forEach((key, values) -> document.put(key, values.stream()
                            .filter(v -> v instanceof final DocValue scalar && !"object".equals(scalar.kind())
                                         && scalar.text() != null)
                            .map(v -> ((DocValue) v).text())
                            .toList()));
                    documents.add(document);
                }
            }
        }
        return documents;
    }

    /// `object_arrays`: fields the documents hold as arrays of objects (dotted paths). Elasticsearch flattens
    /// these: `items.sku` and `items.qty` are searchable, but not which sku went with which qty.
    ///
    /// @param outputs The indexing XSLT's outputs.
    /// @return The fields, sorted.
    public static List<String> objectArrays(final List<String> outputs) {
        final Set<String> found = new TreeSet<>();
        for (final String xml : outputs) {
            for (final Map<String, Object> document : TemplateCheck.jsonXmlDocuments(xml)) {
                walkArrays(document, "", found);
            }
        }
        return new ArrayList<>(found);
    }

    /// `written_fields_markdown`: the Field mapping section of an indexing pipeline whose XSLT keeps no plan
    /// (written by hand): the fields the documents it wrote from the sample hold, how often, and their values. No
    /// source paths: only a plan records those.
    ///
    /// @param documents The documents the pipeline wrote from the sample.
    /// @return The section's text, ending with a new line.
    public static String writtenFieldsMarkdown(final List<Map<String, List<String>>> documents) {
        final Map<String, Integer> seen = populated(documents, Set.of());
        final List<String> lines = new ArrayList<>(List.of("The fields in the " + documents.size() + " documents "
                                                           + "the pipeline wrote from the sample. Its XSLT keeps no "
                                                           + "index plan, so where each comes from is not recorded "
                                                           + "here (save the XSLT with its plan to add it).", "",
                "| Index field | Description | In sample | Sample values |", "| --- | --- | --- | --- |"));
        for (final String key : new TreeSet<>(seen.keySet()).stream().limit(120).toList()) {
            final String what = "StreamId".equals(key) || "EventId".equals(key)
                    ? FieldDocs.IDS.getOrDefault("@" + key, "")
                    : "";
            lines.add(FieldDocs.row("`" + key + "`", FieldDocs.fieldDescription(what, perDocument(documents, key)),
                    percent(seen.get(key), documents.size()) + " of documents",
                    FieldDocs.values(allValues(documents, key))));
        }
        return String.join("\n", lines) + "\n";
    }

    /// `shown_type`: a field's type as its backend has it: Elasticsearch maps ids as long; Lucene keeps Stroom's
    /// own types.
    ///
    /// @param plan The index plan.
    /// @param kind A logical type, e.g. `id`.
    /// @return The type shown.
    public static String shownType(final FieldPlan plan, final String kind) {
        return "elasticsearch".equals(plan.backend())
                ? FieldPlans.ELASTIC.getOrDefault(kind, kind)
                : kind;
    }

    /// `ecs_says`: what ECS says of a field of that name.
    ///
    /// @param name A field name, e.g. `source.ip`.
    /// @return ECS's words, or "" when it is not ECS's.
    public static String ecsSays(final String name) {
        final EcsSchema ecs = Ecs.SCHEMA;
        final Map<String, Object> field = ecs == null
                ? null
                : ecs.field(name);
        return field != null && field.get("short") instanceof final String text
                ? text
                : "";
    }

    // The ECS reference bundled with Stroom for agents (stroom-mcp-impl's resources), read once; null where it
    // isn't on the class path.
    private static EcsSchema readEcs() {
        try (final InputStream fields = IndexFieldDocs.class.getResourceAsStream(CONVENTIONS + EcsSchema.FIELDS_FILE);
                final InputStream component = IndexFieldDocs.class.getResourceAsStream(
                        CONVENTIONS + EcsSchema.COMPONENT_FILE)) {
            if (fields == null || component == null) {
                return null;
            }
            return EcsSchema.parse(new String(fields.readAllBytes(), StandardCharsets.UTF_8),
                    new String(component.readAllBytes(), StandardCharsets.UTF_8));
        } catch (final IOException | RuntimeException e) {
            return null;
        }
    }

    /// `index_tables`: an index's field rows (each with the event-logging path it comes from), by event type (the
    /// default, asked for by the user): the fields any kind of event has in one table, then a section per action
    /// element with a table of the fields from it. Compact, or with no field from an action element: one table.
    ///
    /// @param rows       The rows, each with its event-logging path.
    /// @param head       The table's head.
    /// @param layout     [FieldDocs#BY_EVENT_TYPE] or [FieldDocs#COMPACT].
    /// @param population The share of events (%) each event path is populated in, or null.
    /// @return The tables' lines.
    public static List<String> indexTables(final List<IndexRow> rows,
                                           final List<String> head,
                                           final String layout,
                                           final Map<String, Double> population) {
        return indexTables(rows, head, layout, population, null);
    }

    /// [#indexTables(List, List, String, Map)], with each field only some events have under each event type whose
    /// events have it. Seen in testing, with every field not of an action element and every one read from
    /// whichever action element the event has (EventDetail/*/User/Id, EventDetail//Data[@Name='x']) taken as
    /// common, though only some kinds of event have them: source.ip, in 40% of the events, user.target.*, the
    /// object's and folder's fields. Common fields are then only those every event of
    /// the sample has, without the In sample column; one no event of the sample has is under "Not in the
    /// sample".
    ///
    /// @param rows       The rows, each with its event-logging path.
    /// @param head       The table's head.
    /// @param layout     [FieldDocs#BY_EVENT_TYPE] or [FieldDocs#COMPACT].
    /// @param population The share of events (%) each event path is populated in, or null.
    /// @param byAction   What the sample's events hold by their action element, or null: then only a field read
    ///                   from whichever action element is placed (by its paths' own action elements), and those not
    ///                   of an action element stay common.
    /// @return The tables' lines.
    public static List<String> indexTables(final List<IndexRow> rows,
                                           final List<String> head,
                                           final String layout,
                                           final Map<String, Double> population,
                                           final ByAction byAction) {
        // The action elements' own fields, then those placed under them.
        final Map<String, List<List<String>>> own = new LinkedHashMap<>();
        final Map<String, Set<String>> sources = new LinkedHashMap<>();
        final Map<String, List<List<String>>> placed = new LinkedHashMap<>();
        final List<List<String>> common = new ArrayList<>();
        final List<List<String>> notInSample = new ArrayList<>();
        for (final IndexRow row : rows) {
            final String action = FieldDocs.actionOf(row.source());
            final boolean anyAction = ANY_ACTION.equals(action) || isAnyAction(row.source());
            if (action != null && !anyAction) {
                own.computeIfAbsent(action, k -> new ArrayList<>()).add(row.cells());
                sources.computeIfAbsent(action, k -> new LinkedHashSet<>()).add(FieldDocs.anyActionPath(
                        row.source()));
                continue;
            }
            if (!anyAction && (population == null || everyEvent(row.cells()) || byAction == null)) {
                common.add(row.cells());
                continue;
            }
            final Map<String, Double> shares = byAction != null
                    ? sharesIn(row.source(), byAction.shares())
                    : sharesByAction(row.source(), population);
            if (shares.isEmpty()) {
                notInSample.add(row.cells());
            }
            shares.forEach((each, share) -> {
                placed.computeIfAbsent(each, k -> new ArrayList<>()).add(withShare(row.cells(), share));
                // An action element's grouping counts what is read from whichever action element, not what is
                // outside them (EventSource's, which nearly every kind of event has, would make them all alike).
                if (anyAction) {
                    sources.computeIfAbsent(each, k -> new LinkedHashSet<>()).add(FieldDocs.anyActionPath(
                            row.source()));
                }
            });
        }
        final Set<String> actions = new LinkedHashSet<>(own.keySet());
        actions.addAll(placed.keySet());
        if (FieldDocs.COMPACT.equals(layout) || (actions.isEmpty() && notInSample.isEmpty())) {
            return table(head, rows.stream().map(IndexRow::cells).toList());
        }
        final List<String> lines = new ArrayList<>(List.of("### Common fields", "", "Any kind of event can have "
                                                                                    + "these: from EventTime, "
                                                                                    + "EventSource, the event type, "
                                                                                    + "and the ids.", ""));
        // With a sample, a table of what every event has: its In sample column would say nothing.
        final int inSample = head.indexOf(IN_SAMPLE);
        if (population != null && inSample >= 0 && common.stream().allMatch(IndexFieldDocs::everyEvent)) {
            lines.addAll(table(without(head, inSample), common.stream().map(cells -> without(cells, inSample))
                    .toList()));
        } else {
            lines.addAll(table(head, common));
        }
        if (!actions.isEmpty()) {
            lines.addAll(List.of("", "### Event types", "", "Each from the action element that kind of event has: "
                                                            + "only those events have them.", ""));
            final Map<String, Set<String>> signatures = new LinkedHashMap<>();
            actions.forEach(action -> signatures.put(action, sources.getOrDefault(action, Set.of())));
            for (final List<String> group : FieldDocs.similarGroups(signatures)) {
                final List<String> shares = new ArrayList<>();
                for (final String action : group) {
                    final Double share = Objects.requireNonNullElse(population, Map.<String, Double>of())
                            .entrySet()
                            .stream()
                            .filter(e -> e.getKey().startsWith("EventDetail/" + action + "/"))
                            .map(Map.Entry::getValue)
                            .max(Double::compare)
                            .orElse(null);
                    if (share != null) {
                        shares.add(ToolText.formatG(share) + "%" + (group.size() > 1
                                ? " (" + action + ")"
                                : ""));
                    }
                }
                lines.add("#### " + String.join(", ", group));
                lines.add("");
                if (!shares.isEmpty()) {
                    lines.add("In " + String.join(", ", shares) + " of the sample's events.");
                    lines.add("");
                }
                final String typeIds = typeIdsOf(group, byAction);
                if (!typeIds.isEmpty()) {
                    lines.add(typeIds);
                    lines.add("");
                }
                final List<List<String>> cells = new ArrayList<>();
                group.forEach(action -> cells.addAll(own.getOrDefault(action, List.of())));
                group.forEach(action -> cells.addAll(placed.getOrDefault(action, List.of())));
                lines.addAll(table(head, once(cells)));
                lines.add("");
            }
            lines.removeLast();
        }
        if (!notInSample.isEmpty()) {
            lines.addAll(List.of("", "### Not in the sample", "", "No event of the sample has these, so it doesn't "
                                                                  + "show which kinds of event carry them.", ""));
            lines.addAll(table(head, notInSample));
        }
        return lines;
    }

    // The TypeIds of a section's events, most first, each with its share of all events (seen in testing: nothing
    // said which kinds of event, Secret-View say, a section's fields come from).
    private static String typeIdsOf(final List<String> group, final ByAction byAction) {
        if (byAction == null) {
            return "";
        }
        final Map<String, Double> shares = new LinkedHashMap<>();
        group.forEach(action -> byAction.typeIds().getOrDefault(action, Map.of()).forEach((typeId, share) ->
                shares.merge(typeId, share, (a, b) -> Math.min(100.0, a + b))));
        if (shares.isEmpty()) {
            return "";
        }
        final List<Map.Entry<String, Double>> sorted = shares.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .toList();
        final String listed = sorted.stream()
                .limit(MAX_TYPE_IDS)
                .map(entry -> "`" + entry.getKey() + "` (" + ToolText.formatG(entry.getValue()) + "%)")
                .collect(Collectors.joining(", "));
        return "TypeIds: " + listed + (sorted.size() > MAX_TYPE_IDS
                ? " and " + (sorted.size() - MAX_TYPE_IDS) + " more"
                : "") + ".";
    }

    // Each action element whose events have the source, with that share of all events.
    private static Map<String, Double> sharesIn(final String source,
                                                final Map<String, Map<String, Double>> byAction) {
        final Map<String, Double> shares = new LinkedHashMap<>();
        byAction.forEach((action, paths) -> {
            final Double share = FieldPlans.populationOf(source, paths);
            if (share != null && share > 0) {
                shares.put(action, share);
            }
        });
        return shares;
    }

    // A source read from whichever action element the event has: EventDetail/*/... or EventDetail//...
    private static boolean isAnyAction(final String source) {
        final String path = ToolText.strip(NullSafe.string(source), "/");
        return path.startsWith("EventDetail/*/") || path.startsWith("EventDetail//");
    }

    // Each action element the sample's events have the source under, with its share of events: the paths it
    // matches under that action summed (an event has one action element), at most 100.
    private static Map<String, Double> sharesByAction(final String source, final Map<String, Double> population) {
        final Map<String, Double> shares = new LinkedHashMap<>();
        if (population == null) {
            return shares;
        }
        population.forEach((path, share) -> {
            final String action = FieldDocs.actionOf(path);
            if (action != null && !ANY_ACTION.equals(action) && share != null && share > 0
                && FieldPlans.sourceMatches(source, path)) {
                shares.merge(action, share, (a, b) -> Math.min(100.0, a + b));
            }
        });
        return shares;
    }

    // Each field once: one read from whichever action element the event has, under several of a section's, with
    // their shares added (an event has one action element).
    private static List<List<String>> once(final List<List<String>> rows) {
        final Map<String, List<String>> byField = new LinkedHashMap<>();
        for (final List<String> row : rows) {
            final List<String> seen = byField.get(row.getFirst());
            if (seen == null) {
                byField.put(row.getFirst(), row);
            } else if (seen.size() > 4 && seen.get(4).endsWith("% of events") && row.get(4).endsWith("% of events")) {
                final double sum = Math.min(100.0, share(seen.get(4)) + share(row.get(4)));
                byField.put(row.getFirst(), withShare(seen, sum));
            }
        }
        return List.copyOf(byField.values());
    }

    private static double share(final String cell) {
        return Double.parseDouble(cell.substring(0, cell.indexOf('%')));
    }

    // True if the row's In sample cell (the fifth, given a sample) says every event has it.
    private static boolean everyEvent(final List<String> cells) {
        return cells.size() > 4 && ("always".equals(cells.get(4)) || "100% of events".equals(cells.get(4)));
    }

    private static List<String> without(final List<String> cells, final int index) {
        final List<String> out = new ArrayList<>(cells);
        if (index < out.size()) {
            out.remove(index);
        }
        return out;
    }

    // The row with its In sample cell (the fifth, given a sample) as the action's share.
    private static List<String> withShare(final List<String> cells, final double share) {
        final List<String> out = new ArrayList<>(cells);
        if (out.size() > 4 && out.get(4).endsWith("% of events")) {
            out.set(4, ToolText.formatG(share) + "% of events");
        }
        return out;
    }

    /// `grouped_fields`: the plan's fields for the table: the document's ids and time field first, then grouped by
    /// their top-level object (`host.*`, `user.*`), each group where its first field comes in the plan.
    ///
    /// @param plan The index plan.
    /// @return Its fields in that order.
    public static List<PlannedField> groupedFields(final FieldPlan plan) {
        final List<String> head = new ArrayList<>(List.of("StreamId", "EventId"));
        head.add(plan.timeField());
        head.add("@timestamp");
        final List<PlannedField> out = new ArrayList<>(plan.leafFields().stream()
                .filter(f -> head.contains(f.name()))
                .toList());
        final Map<String, List<PlannedField>> groups = new LinkedHashMap<>();
        for (final PlannedField field : plan.leafFields()) {
            if (!head.contains(field.name())) {
                groups.computeIfAbsent(ToolText.split(field.name(), ".").getFirst(), k -> new ArrayList<>())
                        .add(field);
            }
        }
        groups.values().forEach(out::addAll);
        return out;
    }

    /// `index_field_mapping_markdown`: the Field mapping section of an indexing pipeline's documentation: index
    /// field, a description (the plan's, else the event-logging schema's for its source), type, the event-logging
    /// path it comes from, and from the sample: how often that path is populated in the Events, and the values the
    /// index documents the pipeline wrote got for the field.
    ///
    /// @param plan       The index plan.
    /// @param population The share of the sample's events (%) each event path is populated in, or null for no
    ///                   sample.
    /// @param documents  The documents the pipeline wrote from the sample, or null.
    /// @param schema     The event-logging schema, or null.
    /// @param layout     [FieldDocs#BY_EVENT_TYPE] or [FieldDocs#COMPACT].
    /// @param described  The field descriptions, or null for none.
    /// @return The section's text, ending with a new line.
    public static String indexFieldMappingMarkdown(final FieldPlan plan,
                                                   final Map<String, Double> population,
                                                   final List<Map<String, List<String>>> documents,
                                                   final EventSchema schema,
                                                   final String layout,
                                                   final Descriptions described) {
        return indexFieldMappingMarkdown(plan, population, null, documents, schema, layout, described);
    }

    /// [#indexFieldMappingMarkdown(FieldPlan, Map, List, EventSchema, String, Descriptions)], with each field only
    /// some events have under each event type whose events have it
    /// ([#indexTables(List, List, String, Map, Map)]).
    ///
    /// @param plan       The index plan.
    /// @param population The share of the sample's events (%) each event path is populated in, or null for no
    ///                   sample.
    /// @param byAction   What the sample's events hold by their action element, or null.
    /// @param documents  The documents the pipeline wrote from the sample, or null.
    /// @param schema     The event-logging schema, or null.
    /// @param layout     [FieldDocs#BY_EVENT_TYPE] or [FieldDocs#COMPACT].
    /// @param described  The field descriptions, or null for none.
    /// @return The section's text, ending with a new line.
    public static String indexFieldMappingMarkdown(final FieldPlan plan,
                                                   final Map<String, Double> population,
                                                   final ByAction byAction,
                                                   final List<Map<String, List<String>>> documents,
                                                   final EventSchema schema,
                                                   final String layout,
                                                   final Descriptions described) {
        return indexFieldMappingMarkdown(plan, population, byAction, documents, null, schema, layout, described);
    }

    /// [#indexFieldMappingMarkdown(FieldPlan, Map, Map, List, EventSchema, String, Descriptions)], with example
    /// values the index holds for fields the sample's documents have none of, so a reader sees what kind of data
    /// each holds (asked for by the user): shown as from the index, and given to the
    /// field's description.
    ///
    /// @param plan        The index plan.
    /// @param population  The share of the sample's events (%) each event path is populated in, or null for no
    ///                    sample.
    /// @param byAction    What the sample's events hold by their action element, or null.
    /// @param documents   The documents the pipeline wrote from the sample, or null.
    /// @param indexValues Example values the index holds, by index field, or null.
    /// @param schema      The event-logging schema, or null.
    /// @param layout      [FieldDocs#BY_EVENT_TYPE] or [FieldDocs#COMPACT].
    /// @param described   The field descriptions, or null for none.
    /// @return The section's text, ending with a new line.
    public static String indexFieldMappingMarkdown(final FieldPlan plan,
                                                   final Map<String, Double> population,
                                                   final ByAction byAction,
                                                   final List<Map<String, List<String>>> documents,
                                                   final Map<String, List<String>> indexValues,
                                                   final EventSchema schema,
                                                   final String layout,
                                                   final Descriptions described) {
        final Descriptions descriptions = described == null
                ? new Descriptions()
                : described;
        final List<String> lines = new ArrayList<>(List.of("Documents for `" + plan.indexName() + "` ("
                                                           + plan.backend() + "); the time field is `"
                                                           + plan.timeField() + "`.", ""));
        if (!plan.dropWhen().isEmpty()) {
            lines.addAll(List.of("Events left out of the index:", ""));
            plan.dropWhen().forEach(t -> lines.add("- `" + t + "`"));
            lines.add("");
        }
        final boolean sampled = population != null;
        if (documents != null) {
            lines.addAll(List.of("Sample values are what the " + documents.size() + " documents written from the "
                                 + "sample got.", ""));
        }
        final List<String> head = new ArrayList<>(List.of("Index field", "Description", "Type",
                "From (event-logging path)"));
        if (sampled) {
            head.add(IN_SAMPLE);
        }
        if (documents != null) {
            head.add("Sample values");
        }
        final List<IndexRow> rows = new ArrayList<>();
        for (final PlannedField f : groupedFields(plan)) {
            final SharedTemplate shared = plan.writtenBy(f.name());
            final List<List<String>> perDocument = documents == null
                    ? null
                    : perDocument(documents, f.name());
            final List<String> fromSample = perDocument == null
                    ? List.of()
                    : distinctNonEmpty(perDocument.stream().flatMap(List::stream).toList());
            final List<String> fromIndex = fromSample.isEmpty()
                    ? NullSafe.list(NullSafe.get(indexValues, values -> values.get(f.name())))
                    : List.of();
            final List<String> sampledValues = fromSample.isEmpty()
                    ? fromIndex
                    : fromSample;
            final Map<String, String> says = new LinkedHashMap<>();
            if (shared != null) {
                says.put("shared template", "Written by the shared template `" + shared.template() + "`.");
            } else {
                says.put(Descriptions.EVENT_LOGGING, FieldDocs.schemaDescription(schema, f.source()));
            }
            says.put("ECS", ecsSays(f.name()));
            final String what = JsonValues.truthy(f.description())
                    ? f.description()
                    : descriptions.of(f.name(), shared == null
                            ? f.source()
                            : "shared template " + shared.template(), sampledValues, says, f.source());
            final List<String> cells = new ArrayList<>(List.of("`" + f.name() + "`",
                    FieldDocs.fieldDescription(what, perDocument), shownType(plan, f.type()), shared != null
                            ? "shared template `" + shared.template() + "` of `" + shared.href() + "`"
                            : "`" + f.source() + "`"));
            if (sampled) {
                final Double pct = FieldPlans.populationOf(f.source(), population);
                cells.add(f.source().startsWith("@") || shared != null
                        ? "always"
                        : pct != null
                                ? ToolText.formatG(pct) + "% of events"
                                : "not in the sample");
            }
            if (documents != null) {
                final List<String> values = distinctNonEmpty(allValues(documents, f.name()));
                cells.add(!values.isEmpty()
                        ? values.subList(0, Math.min(3, values.size())).stream()
                                  .map(v -> "`" + ToolText.head(v, 40) + "`")
                                  .collect(Collectors.joining(", ")) + (values.size() > 3
                                ? " (+" + (values.size() - 3) + ")"
                                : "")
                        : !fromIndex.isEmpty()
                                ? fromIndex.stream()
                                          .map(v -> "`" + ToolText.head(v, 40) + "`")
                                          .collect(Collectors.joining(", ")) + " (from the index)"
                                : "(none in the sample)");
            }
            rows.add(new IndexRow(shared != null
                    ? ""
                    : f.source(), cells));
        }
        lines.addAll(indexTables(rows, head, layout, population, byAction));
        return String.join("\n", lines) + "\n";
    }

    /// `existing_index_summary`: 'Data surveyed', for an existing index's Purpose and data: what the index holds as
    /// a whole, where its documents come from (feed, stream type, the pipeline that produced the streams), what
    /// writes to it, and what the field table was drawn from.
    ///
    /// @param survey The index's survey (`survey_index`'s result, as JSON-like values).
    /// @return The summary's text, ending with a new line.
    public static String existingIndexSummary(final Map<String, Object> survey) {
        final Map<String, Object> index = JsonValues.mapOrEmpty(survey.get("index"));
        final Map<String, Object> profile = JsonValues.mapOrEmpty(survey.get("profile"));
        final List<String> lines = new ArrayList<>(List.of("### Data surveyed", ""));
        if (JsonValues.truthy(profile.get("documents"))) {
            final String span = JsonValues.truthy(profile.get("earliest"))
                    ? ", from " + ToolText.str(profile.get("earliest")) + " to " + ToolText.str(profile.get("latest"))
                    : "";
            final String streams = JsonValues.truthy(profile.get("streams"))
                    ? ", in " + ToolText.str(profile.get("streams")) + " stream" + plural(profile.get("streams"))
                    : "";
            final String atLeast = JsonValues.truthy(profile.get("partial"))
                    ? "at least "
                    : "";
            lines.add("- `" + ToolText.str(index.get("name")) + "` holds " + atLeast
                      + ToolText.str(profile.get("documents"))
                      + " document" + plural(profile.get("documents")) + span + streams + "."
                      + (JsonValues.truthy(profile.get("partial"))
                    ? " (" + ToolText.str(profile.get("partial")) + ".)"
                    : ""));
            final List<Object> sources = JsonValues.listOrEmpty(profile.get("sources"));
            final double examined = number(profile.get("streams_examined"));
            for (final Object item : sources.subList(0, Math.min(10, sources.size()))) {
                final Map<String, Object> s = JsonValues.mapOrEmpty(item);
                final String made = JsonValues.truthy(s.get("produced_by"))
                        ? ", produced by pipeline `" + ToolText.str(s.get("produced_by")) + "`"
                        : "";
                final String said = JsonValues.truthy(s.get("description"))
                        ? " The feed's description: " + ToolText.str(s.get("description"))
                        : "";
                lines.add("- From feed `" + ToolText.str(s.get("feed")) + "` (" + ToolText.str(s.get("type"))
                          + " streams"
                          + made
                          + "): " + ToolText.str(s.get("streams")) + " stream" + plural(s.get("streams")) + ", "
                          + ToolText.str(s.get("documents")) + " documents." + said);
            }
            if (!sources.isEmpty() && JsonValues.truthy(profile.get("streams"))
                && examined < number(profile.get("streams"))) {
                lines.add("- (Sources from the "
                          + ToolText.str(Objects.requireNonNullElse(profile.get("streams_examined"), 0))
                          + " streams with the most documents.)");
            }
        } else if (survey.get("documents_sampled") instanceof final Number sampled && sampled.doubleValue() == 0) {
            lines.add("- " + (JsonValues.truthy(survey.get("note"))
                    ? ToolText.str(survey.get("note"))
                    : "No documents came back through Stroom."));
        }
        final List<Object> fed = JsonValues.listOrEmpty(survey.get("fed_by"));
        for (final Object item : fed) {
            final Map<String, Object> p = JsonValues.mapOrEmpty(item);
            lines.add("- Indexed by pipeline `" + ToolText.str(p.get("name")) + "`" + (JsonValues.truthy(p.get("plan"))
                    ? ", from the index plan kept with its XSLT."
                    : "."));
        }
        if (fed.isEmpty()) {
            lines.add("- No pipeline was found that writes to it (pipelines are looked for by the index name they "
                      + "write; one that builds the name from values, or writes through an alias, is not found "
                      + "this way).");
        }
        if (JsonValues.truthy(survey.get("documents_sampled"))) {
            final String sampledFrom = survey.get("sampled_from") instanceof final String text
                    ? text
                    : "";
            final String recent = sampledFrom.startsWith("the last")
                    ? " of " + sampledFrom
                    : "";
            lines.add("- The field table is drawn from " + ToolText.str(survey.get("documents_sampled")) + " documents"
                      + recent + (JsonValues.truthy(survey.get("earliest"))
                    ? " (" + ToolText.str(survey.get("earliest")) + " to " + ToolText.str(survey.get("latest")) + ")"
                    : "") + ": In sample and Sample values are what they held.");
        }
        return String.join("\n", lines) + "\n";
    }

    /// `existing_index_markdown`: the Field mapping section for an existing index, from its survey: every field
    /// Stroom has for it, with a description (the feeding pipeline's plan, else the event-logging schema for the
    /// field's source), its type, where it comes from when a feeding pipeline's plan records it, how often the
    /// surveyed documents held it, and their values.
    ///
    /// @param survey    The index's survey (`survey_index`'s result, as JSON-like values).
    /// @param planned   The fields the feeding pipelines' plans write, by name.
    /// @param schema    The event-logging schema, or null.
    /// @param layout    [FieldDocs#BY_EVENT_TYPE] or [FieldDocs#COMPACT].
    /// @param described The field descriptions, or null for none.
    /// @return The section's text, ending with a new line.
    public static String existingIndexMarkdown(final Map<String, Object> survey,
                                               final Map<String, PlannedField> planned,
                                               final EventSchema schema,
                                               final String layout,
                                               final Descriptions described) {
        final Map<String, Object> index = JsonValues.mapOrEmpty(survey.get("index"));
        final List<Map<String, List<String>>> documents = documents(survey.get("documents"));
        final String target = "elasticsearch".equals(survey.get("backend"))
                ? "Elasticsearch index `" + ToolText.str(survey.get("index_name")) + "`"
                : "a Lucene index";
        // The overall facts (what feeds it, what was surveyed) are in Data surveyed: only what explains the columns
        // here.
        final List<Object> fed = JsonValues.listOrEmpty(survey.get("fed_by"));
        String intro = "`" + ToolText.str(index.get("name")) + "` is " + target + "; the time field is `"
                       + ToolText.str(survey.get("time_field")) + "`.";
        if (fed.stream().noneMatch(p -> JsonValues.truthy(JsonValues.mapOrEmpty(p).get("plan")))) {
            intro += " Where each field comes from is not recorded: no pipeline that writes to it keeps an index "
                     + "plan.";
        }
        if (documents.isEmpty()) {
            intro += " No documents came back through Stroom, so no field below was read (see Data surveyed).";
        }
        final List<String> lines = new ArrayList<>(List.of(intro, ""));
        final List<String> head = List.of("Index field", "Description", "Type", "From (event-logging path)",
                "In sample", "Sample values");
        final List<Map<String, Object>> fields = JsonValues.listOrEmpty(survey.get("fields")).stream()
                .map(JsonValues::mapOrEmpty)
                .toList();
        final List<Object> first = new ArrayList<>(List.of("StreamId", "EventId"));
        first.add(survey.get("time_field"));
        first.add("@timestamp");
        final Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (final Map<String, Object> f : fields) {
            if (!first.contains(f.get("name"))) {
                groups.computeIfAbsent(ToolText.split(ToolText.str(f.get("name")), ".").getFirst(),
                        k -> new ArrayList<>()).add(f);
            }
        }
        final List<Map<String, Object>> ordered = new ArrayList<>();
        for (final Object name : first) {
            fields.stream().filter(f -> Objects.equals(f.get("name"), name)).forEach(ordered::add);
        }
        groups.values().forEach(ordered::addAll);
        final Set<String> surveyed = new LinkedHashSet<>(JsonValues.truthy(survey.get("surveyed_fields"))
                ? JsonValues.listOrEmpty(survey.get("surveyed_fields")).stream().map(ToolText::str).toList()
                : fields.stream().map(f -> ToolText.str(f.get("name"))).toList());
        final List<String> unsurveyed = fields.stream()
                .map(f -> ToolText.str(f.get("name")))
                .filter(n -> !surveyed.contains(n))
                .toList();
        if (!documents.isEmpty() && !unsurveyed.isEmpty()) {
            lines.add("The survey read " + surveyed.size() + " of the " + fields.size() + " fields; the other "
                      + unsurveyed.size() + " are listed as not surveyed.");
            lines.add("");
        }
        final Descriptions descriptions = described == null
                ? new Descriptions()
                : described;
        final List<IndexRow> rows = new ArrayList<>();
        for (final String f : new LinkedHashSet<>(ordered.stream().map(x -> ToolText.str(x.get("name"))).toList())) {
            final Map<String, Object> field = fields.stream()
                    .filter(x -> f.equals(ToolText.str(x.get("name"))))
                    .findFirst()
                    .orElseThrow();
            final PlannedField plan = planned.get(f);
            final String source = plan != null
                    ? plan.source()
                    : "";
            final List<List<String>> perDocument = perDocument(documents, f);
            final String description;
            final String held;
            final String values;
            if (Boolean.FALSE.equals(field.get("stored"))) {
                description = "Indexed but not stored: searchable, its values cannot be shown.";
                held = "not stored";
                values = "-";
            } else if (!documents.isEmpty() && !surveyed.contains(f)) {
                description = orElse(FieldDocs.fieldDescription(planned(plan, schema, source), null),
                        "Not surveyed: past the fields the survey reads.");
                held = "not surveyed";
                values = "-";
            } else if (documents.isEmpty()) {
                description = orElse(FieldDocs.fieldDescription(planned(plan, schema, source), null),
                        "Not read: no documents came back through Stroom.");
                held = "not read";
                values = "-";
            } else {
                String hint = !source.isEmpty()
                        ? FieldDocs.schemaDescription(schema, source)
                        : DOCUMENT_IDS.getOrDefault(f, "");
                if (hint.isEmpty() && f.equals(survey.get("time_field"))) {
                    hint = "The index's time field: searches and dashboards filter on it.";
                }
                final Map<String, String> says = new LinkedHashMap<>();
                says.put(Descriptions.EVENT_LOGGING, hint);
                says.put("ECS", ecsSays(f));
                final String own = plan != null
                        ? plan.description()
                        : "";
                final String what = !own.isEmpty()
                        ? own
                        : descriptions.of(f, source, distinctNonEmpty(perDocument.stream()
                                .flatMap(List::stream)
                                .toList()), says, source);
                description = FieldDocs.fieldDescription(what, perDocument);
                final long count = perDocument.stream().filter(vs -> vs.stream().anyMatch(v -> !v.isEmpty())).count();
                held = percent((int) count, documents.size()) + " of documents";
                values = FieldDocs.values(perDocument.stream().flatMap(List::stream).toList());
            }
            rows.add(new IndexRow(source, List.of("`" + f + "`", description, ToolText.str(field.get("type")),
                    !source.isEmpty()
                            ? "`" + source + "`"
                            : "(not recorded)", held, values)));
        }
        // Written by a feeding pipeline's plan, but not in the fields Stroom lists for the index: only documented
        // when the user chose to (write_documentation refuses otherwise), and said as such rather than left out.
        for (final String name : notListed(survey, planned)) {
            final PlannedField plan = planned.get(name);
            final String what = JsonValues.truthy(plan.description())
                    ? plan.description()
                    : JsonValues.truthy(plan.source())
                            ? FieldDocs.schemaDescription(schema, plan.source())
                            : "";
            rows.add(new IndexRow(plan.source(), List.of("`" + name + "`",
                    orElse(FieldDocs.fieldDescription(what, null), "Written by the pipeline that feeds the index."),
                    Objects.requireNonNullElse(plan.type(), ""), JsonValues.truthy(plan.source())
                            ? "`" + plan.source() + "`"
                            : "(not recorded)", "not in the index", "-")));
        }
        lines.addAll(indexTables(rows, head, layout, null));
        return String.join("\n", lines) + "\n";
    }

    /// `not_listed`: fields a feeding pipeline's plan writes that Stroom doesn't list for the index.
    ///
    /// @param survey  The index's survey.
    /// @param planned The fields the feeding pipelines' plans write, by name.
    /// @return Their names, in the plans' order.
    public static List<String> notListed(final Map<String, Object> survey, final Map<String, PlannedField> planned) {
        final Set<String> listed = JsonValues.listOrEmpty(survey.get("fields")).stream()
                .map(f -> ToolText.str(JsonValues.mapOrEmpty(f).get("name")))
                .collect(Collectors.toSet());
        return planned.keySet().stream().filter(n -> !listed.contains(n)).toList();
    }

    /// `discovery_field_markdown`: the Field mapping section of a discovery pipeline: how records become documents,
    /// the fields mapped explicitly, and the fields the sample's documents held (Elasticsearch maps those
    /// dynamically).
    ///
    /// @param plan      The index plan, with its discovery set.
    /// @param documents The documents written from the sample, or null.
    /// @param arrays    The fields held as arrays of objects ([#objectArrays(List)]), or null.
    /// @return The section's text, ending with a new line.
    public static String discoveryFieldMarkdown(final FieldPlan plan,
                                                final List<Map<String, List<String>>> documents,
                                                final List<String> arrays) {
        final Discovery d = plan.discovery();
        final List<String> lines = new ArrayList<>(List.of("Documents for `" + plan.indexName() + "` "
                                                           + "(Elasticsearch, discovery): each raw record indexed as "
                                                           + "it is, with the source's field names. Elasticsearch "
                                                           + "maps fields dynamically as documents arrive: strings as "
                                                           + "keywords (up to " + d.ignoreAbove() + " characters), at "
                                                           + "most " + d.totalFieldsLimit() + " fields, malformed "
                                                           + "values ignored.", ""));
        if (d.unpackJson()) {
            lines.addAll(List.of("A string holding a JSON object is also indexed parsed, as `<field>_json`; the "
                                 + "string is kept.", ""));
        }
        if (!d.drop().isEmpty()) {
            lines.addAll(List.of("Left out: " + d.drop().stream().map(k -> "`" + k + "`")
                    .collect(Collectors.joining(", ")) + ".", ""));
        }
        lines.addAll(List.of("Fields starting with `_` (Elasticsearch reserves `_id` and others; Stroom drops the "
                             + "rest) or top-level ones named like a field written here are kept as "
                             + "`<field>_original`, without the leading `_` (`_id` is `id_original`); keys "
                             + "Elasticsearch cannot take (empty, or with an empty dotted part) are repaired.", ""));
        if (arrays != null && !arrays.isEmpty()) {
            lines.addAll(List.of("Arrays of objects are indexed flattened: each field inside is searchable, but not "
                                 + "which values went together in one object: " + arrays.stream()
                                         .map(a -> "`" + a + "`")
                                         .collect(Collectors.joining(", ")) + ".", ""));
        }
        final Map<String, String> explicit = new LinkedHashMap<>();
        explicit.put("StreamId", "the stream id");
        explicit.put("EventId", "the record number in the stream");
        explicit.put("@timestamp", "`" + d.timestampField() + "`" + (JsonValues.truthy(d.timestampFormat())
                ? " (format `" + d.timestampFormat() + "`)"
                : ""));
        d.meta().forEach((name, attr) -> explicit.put(name, "stream meta `" + attr + "`"));
        lines.add("| Index field | Description | Mapping | From |" + (documents != null
                ? " Sample values |"
                : ""));
        lines.add("| --- | --- | --- | --- |" + (documents != null
                ? " --- |"
                : ""));
        final Map<String, String> types = new LinkedHashMap<>();
        plan.leafFields().forEach(f -> types.put(f.name(), f.type()));
        final Map<String, String> meaning = Map.of(
                "StreamId", "The id of the raw stream the record came from.",
                "EventId", "The record's number in its stream; with StreamId, it identifies the record.",
                "@timestamp", "The record's time, from its `" + d.timestampField() + "` field.");
        for (final Map.Entry<String, String> entry : explicit.entrySet()) {
            final String name = entry.getKey();
            final String what = meaning.containsKey(name)
                    ? meaning.get(name)
                    : "The stream's `" + ToolText.str(d.meta().get(name)) + "` meta attribute.";
            final List<List<String>> perDocument = documents == null
                    ? null
                    : perDocument(documents, name);
            final String type = types.get(name);
            final List<String> cells = new ArrayList<>(List.of("`" + name + "`",
                    FieldDocs.fieldDescription(what, perDocument), "id".equals(type)
                            ? "long"
                            : "date".equals(type)
                                    ? "date"
                                    : "dynamic", entry.getValue()));
            if (documents != null) {
                cells.add(FieldDocs.values(allValues(documents, name)));
            }
            lines.add(FieldDocs.row(cells));
        }
        if (documents != null && !documents.isEmpty()) {
            final Map<String, Integer> seen = populated(documents, explicit.keySet());
            lines.addAll(List.of("", "Fields in the " + documents.size() + " documents written from the sample, "
                                     + "mapped dynamically:", "",
                    "| Field | Description | In sample | Sample values |", "| --- | --- | --- | --- |"));
            for (final String key : new TreeSet<>(seen.keySet()).stream().limit(80).toList()) {
                final String top = ToolText.split(key, ".").getFirst();
                final String what = top.endsWith("_json") && d.unpackJson()
                        ? "Parsed from the JSON held in the source's `" + top.substring(0, top.length() - 5)
                          + "` field."
                        : "The source's `" + key + "` field.";
                lines.add(FieldDocs.row("`" + key + "`", FieldDocs.fieldDescription(what, perDocument(documents,
                                key)), percent(seen.get(key), documents.size()) + " of documents",
                        FieldDocs.values(allValues(documents, key))));
            }
            if (seen.size() > 80) {
                lines.add("\n(+" + (seen.size() - 80) + " more fields)");
            }
        }
        return String.join("\n", lines) + "\n";
    }

    // How many documents hold a value of each field (those of the explicit ones aside).
    private static Map<String, Integer> populated(final List<Map<String, List<String>>> documents,
                                                  final Set<String> explicit) {
        final Map<String, Integer> seen = new TreeMap<>();
        for (final Map<String, List<String>> document : documents) {
            document.forEach((key, values) -> {
                if (!explicit.contains(key) && values.stream().anyMatch(v -> !v.isEmpty())) {
                    seen.merge(key, 1, Integer::sum);
                }
            });
        }
        return seen;
    }

    // The table of the rows, with its head.
    private static List<String> table(final List<String> head, final List<List<String>> cells) {
        final List<String> lines = new ArrayList<>(FieldDocs.head(head));
        cells.forEach(c -> lines.add(FieldDocs.row(c)));
        return lines;
    }

    // What a planned field is (its description, else the schema's words for its source), with no sample.
    private static String planned(final PlannedField plan, final EventSchema schema, final String source) {
        final String own = plan != null
                ? plan.description()
                : "";
        if (!own.isEmpty()) {
            return own;
        }
        return !source.isEmpty()
                ? FieldDocs.schemaDescription(schema, source)
                : "";
    }

    private static void walkArrays(final Object value, final String path, final Set<String> found) {
        if (value instanceof final Map<?, ?> map) {
            map.forEach((key, sub) -> walkArrays(sub, path.isEmpty()
                    ? String.valueOf(key)
                    : path + "." + key, found));
        } else if (value instanceof final List<?> list) {
            if (list.stream().anyMatch(v -> v instanceof Map)) {
                found.add(path);
            }
            list.forEach(sub -> walkArrays(sub, path, found));
        }
    }

    private static List<List<String>> perDocument(final List<Map<String, List<String>>> documents,
                                                  final String name) {
        return documents.stream().map(d -> d.getOrDefault(name, List.of())).toList();
    }

    private static List<String> allValues(final List<Map<String, List<String>>> documents, final String name) {
        return documents.stream().flatMap(d -> d.getOrDefault(name, List.of()).stream()).toList();
    }

    private static List<String> distinctNonEmpty(final List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values.stream().filter(v -> !v.isEmpty()).toList()));
    }

    // A share as a whole percentage, a half rounded to the even number: the documentation's established form.
    private static String percent(final int count, final int total) {
        return ToolText.formatFixed0(100.0 * count / total) + "%";
    }

    private static String orElse(final String text, final String fallback) {
        return text.isEmpty()
                ? fallback
                : text;
    }

    private static String plural(final Object count) {
        return number(count) != 1
                ? "s"
                : "";
    }

    private static double number(final Object value) {
        return value instanceof final Number n
                ? n.doubleValue()
                : 0;
    }

    // The survey's documents: field to values.
    private static List<Map<String, List<String>>> documents(final Object value) {
        final List<Map<String, List<String>>> documents = new ArrayList<>();
        for (final Object item : JsonValues.listOrEmpty(value)) {
            final Map<String, List<String>> document = new LinkedHashMap<>();
            JsonValues.mapOrEmpty(item).forEach((key, values) -> document.put(key, JsonValues.listOrEmpty(values)
                    .stream()
                    .map(ToolText::str)
                    .toList()));
            documents.add(document);
        }
        return documents;
    }


    // --------------------------------------------------------------------------------


    /// A row of an index's field table, with the event-logging path the field comes from (which places it by
    /// event type).
    ///
    /// @param source The event-logging path, or "" (a shared template's field, or none recorded).
    /// @param cells  The row's cells.
    public record IndexRow(String source, List<String> cells) {

    }

    /// What the sample's events hold, by the action element of each.
    ///
    /// @param shares  The share of all events (%) each path (and named Data entry) is populated in, by action.
    /// @param typeIds Each TypeId's share of all events (%), by action, most first.
    public record ByAction(Map<String, Map<String, Double>> shares, Map<String, Map<String, Double>> typeIds) {

    }

    // The ECS reference, read the first time it is needed.
    private static final class Ecs {

        private static final EcsSchema SCHEMA = readEcs();
    }
}
