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

import stroom.ai.impl.tools.JsonValues;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The indexes of an aggregated feed, as its source type registry keeps them in its machine block: one index per
/// output feed (decided 2026-11-12), shared by every source type group whose Events go to that feed, and the indexing
/// backend and field naming the feed's builds share.
///
/// The registry's `indexing` holds the feed's shared decisions: `backend`, `naming` (`convention` or `like_index`, as
/// get_field_conventions records them) and `decided` (`by`, `at`, `build`). Its `indexes` holds, by output feed (the
/// raw feed's own name for its own Events), `index` (the index's name), `backend`, `index_doc` (`type`, `uuid`,
/// `name`, once known), `fields` (each as a plan keeps it, [PlannedField#toJson()]), `added_by` (the build that added
/// each field, by name), `groups` (the source type groups whose Events it holds) and `decided`.
///
/// A field's meaning is its description where it has one, else what it reads (its source path in the Events): the
/// Events schema gives a path its meaning, so two groups writing the same path agree, and a field read from another
/// path, or described otherwise, may not.
public final class FeedIndexes {

    /// The registry's key for the feed's shared indexing decisions.
    public static final String INDEXING = "indexing";
    /// The registry's key for its indexes, by output feed.
    public static final String INDEXES = "indexes";
    /// A field another group already has with another type.
    public static final String TYPE_CLASH = "type";
    /// A field another group already has, of the same type, meaning something else.
    public static final String MEANING_CLASH = "meaning";
    /// The answer that renames this build's field.
    public static final String RENAME = "rename";
    /// The answer that makes a new version of the index.
    public static final String NEW_VERSION = "new_version";
    /// The answer that keeps one field, read from both.
    public static final String SAME_MEANING = "same_meaning";

    private static final String FIELDS = "fields";
    private static final String ADDED_BY = "added_by";
    private static final String GROUPS = "groups";
    private static final String INDEX = "index";
    private static final String BACKEND = "backend";
    private static final String DECIDED = "decided";
    private static final Pattern VERSIONED = Pattern.compile("^(.*?)([-_.]v)(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Set<String> ALWAYS_THE_SAME = Set.of("StreamId", "EventId", "@timestamp");

    private FeedIndexes() {
        // Static only.
    }

    /// @param registry A registry's machine block.
    /// @return Its output feeds: the raw feed's own first, then each one of its own a row's group writes to.
    public static List<String> outputFeeds(final Map<String, Object> registry) {
        final Set<String> out = new LinkedHashSet<>();
        out.add(JsonValues.text(registry.get("feed")));
        JsonValues.maps(registry.get("rows")).forEach(row -> out.add(outputFeedOf(registry, row)));
        return List.copyOf(out);
    }

    /// @param registry A registry's machine block.
    /// @param row      One of its rows.
    /// @return The feed the row's Events go to: its own output feed where it has one, else the raw feed's.
    public static String outputFeedOf(final Map<String, Object> registry, final Map<String, Object> row) {
        final String own = JsonValues.text(row.get("output_feed"));
        return own.isBlank()
                ? JsonValues.text(registry.get("feed"))
                : own;
    }

    /// @param registry A registry's machine block.
    /// @return The feed's shared indexing decisions (`backend`, `naming`, `decided`); empty until made.
    public static Map<String, Object> indexing(final Map<String, Object> registry) {
        return JsonValues.mapOrEmpty(registry.get(INDEXING));
    }

    /// Records a shared indexing decision: the backend, or how fields are named. A new backend clears the naming,
    /// which follows from it.
    ///
    /// @param registry A registry's machine block, changed in place.
    /// @param backend  The backend chosen, or null to keep the one recorded.
    /// @param naming   How fields are named (`convention` or `like_index`), or null to keep what is recorded.
    /// @param decided  Who decided, when, in which build.
    public static void settleIndexing(final Map<String, Object> registry,
                                      final String backend,
                                      final Map<String, Object> naming,
                                      final Decided decided) {
        final Map<String, Object> indexing = new LinkedHashMap<>(indexing(registry));
        if (backend != null) {
            if (!backend.equals(indexing.get(BACKEND))) {
                indexing.remove("naming");
            }
            indexing.put(BACKEND, backend);
        }
        if (naming != null && !naming.isEmpty()) {
            indexing.put("naming", new LinkedHashMap<>(naming));
        }
        indexing.put(DECIDED, decided.toJson());
        registry.put(INDEXING, indexing);
    }

    /// @param registry   A registry's machine block.
    /// @param outputFeed One of its output feeds.
    /// @return That feed's index as recorded; empty until a build promoting one recorded it.
    public static Map<String, Object> index(final Map<String, Object> registry, final String outputFeed) {
        return JsonValues.mapOrEmpty(JsonValues.mapOrEmpty(registry.get(INDEXES)).get(outputFeed));
    }

    /// @param registry   A registry's machine block.
    /// @param outputFeed One of its output feeds.
    /// @return The source type groups whose Events go to it, translated now, in the registry's order.
    public static List<String> groupsTo(final Map<String, Object> registry, final String outputFeed) {
        final Set<String> out = new LinkedHashSet<>();
        JsonValues.maps(registry.get("rows")).stream()
                .filter(row -> "translated".equals(row.get("status")))
                .filter(row -> outputFeed.equals(outputFeedOf(registry, row)))
                .map(row -> JsonValues.text(row.get("group")))
                .filter(group -> !group.isBlank())
                .forEach(out::add);
        return List.copyOf(out);
    }

    /// @param entry An index as recorded ([#index]).
    /// @return Its fields, as plans keep them; one that no longer reads as a field is left out.
    public static List<PlannedField> recordedFields(final Map<String, Object> entry) {
        final List<PlannedField> out = new ArrayList<>();
        for (final Map<String, Object> field : JsonValues.maps(entry.get(FIELDS))) {
            try {
                out.add(PlannedField.fromJson(field));
            } catch (final RuntimeException e) {
                // A field kept by an older form that no longer reads: not inherited, and not compared.
            }
        }
        return out;
    }

    /// @param plan  A plan for an output feed's index.
    /// @param entry That index as recorded, or empty.
    /// @return True when the plan is for the index recorded (its name), so it inherits and is checked against it.
    public static boolean sameIndex(final FieldPlan plan, final Map<String, Object> entry) {
        return !entry.isEmpty() && JsonValues.text(entry.get(INDEX)).equals(plan.indexName());
    }

    /// The plan with every field the index already has that it lacks (by name), as recorded: the union of the
    /// groups' fields, so a build of one group never drops another's.
    ///
    /// @param plan  A plan for the output feed's index, or a new version of it.
    /// @param entry The index as recorded, or empty.
    /// @return The plan, with the fields it inherits added after its own.
    public static FieldPlan withInherited(final FieldPlan plan, final Map<String, Object> entry) {
        final List<PlannedField> fields = new ArrayList<>(plan.fields());
        final Set<String> planned = new LinkedHashSet<>();
        fields.forEach(field -> planned.add(field.name()));
        boolean added = false;
        for (final PlannedField field : recordedFields(entry)) {
            if (planned.add(field.name())) {
                fields.add(field);
                added = true;
            }
        }
        return added
                ? plan.withFields(fields)
                : plan;
    }

    /// @param plan  A plan for the output feed's index.
    /// @param entry The index as recorded, or empty.
    /// @return The fields of the plan the index has already: of another type (a type clash), or of the same type
    /// meaning something else (a meaning clash); none when the plan is for another index (a new version).
    public static List<Clash> clashes(final FieldPlan plan, final Map<String, Object> entry) {
        if (!sameIndex(plan, entry)) {
            return List.of();
        }
        final Map<String, String> addedBy = new LinkedHashMap<>();
        JsonValues.mapOrEmpty(entry.get(ADDED_BY)).forEach((name, build) -> addedBy.put(name,
                JsonValues.text(build)));
        final Map<String, PlannedField> theirs = new LinkedHashMap<>();
        recordedFields(entry).forEach(field -> theirs.put(field.name(), field));
        final List<Clash> out = new ArrayList<>();
        for (final PlannedField ours : plan.fields()) {
            final PlannedField recorded = theirs.get(ours.name());
            if (recorded == null) {
                continue;
            }
            final Clash.Context where = new Clash.Context(JsonValues.text(entry.get(INDEX)),
                    JsonValues.text(entry.get(BACKEND)), addedBy.get(ours.name()));
            if (!Objects.equals(lower(ours.type()), lower(recorded.type()))) {
                out.add(new Clash(TYPE_CLASH, ours, recorded, where));
            } else if (!ALWAYS_THE_SAME.contains(ours.name()) && !ours.name().equals(plan.timeField())
                       && !sameMeaning(ours, recorded)) {
                out.add(new Clash(MEANING_CLASH, ours, recorded, where));
            }
        }
        return out;
    }

    /// @param ours   A field of a plan.
    /// @param theirs The field of that name the index has.
    /// @return True when they mean the same: their descriptions agree where both have one, else what the index's
    /// field reads is read by the plan's too (it may read more: a field read from both groups' paths).
    public static boolean sameMeaning(final PlannedField ours, final PlannedField theirs) {
        if (!ours.description().isBlank() && !theirs.description().isBlank()) {
            return normalised(ours.description()).equals(normalised(theirs.description()));
        }
        final Set<String> read = new LinkedHashSet<>(branches(ours.source()));
        return read.containsAll(branches(theirs.source()));
    }

    /// The plan with the user's answer to a clash applied: the field renamed, the index's next version, or one
    /// field read from both (the index's description kept).
    ///
    /// @param plan   The plan.
    /// @param entry  The index as recorded.
    /// @param clash  A clash of the plan's.
    /// @param choice [#RENAME], [#NEW_VERSION] or [#SAME_MEANING].
    /// @param suffix What a renamed field's name ends with ([#renamed]).
    /// @return The plan, the answer applied, with the fields it inherits added again.
    /// @throws IllegalArgumentException for an answer the clash doesn't take.
    public static FieldPlan resolved(final FieldPlan plan,
                                     final Map<String, Object> entry,
                                     final Clash clash,
                                     final String choice,
                                     final String suffix) {
        if (!clash.choices().contains(choice)) {
            throw new IllegalArgumentException("A " + clash.kind() + " clash isn't resolved by " + choice);
        }
        final List<PlannedField> fields = new ArrayList<>();
        for (final PlannedField field : plan.fields()) {
            if (!field.name().equals(clash.ours().name())) {
                fields.add(field);
            } else if (RENAME.equals(choice)) {
                fields.add(field.withName(free(plan, renamed(field.name(), suffix))));
            } else if (SAME_MEANING.equals(choice)) {
                final Set<String> read = new LinkedHashSet<>(branches(clash.theirs().source()));
                read.addAll(branches(field.source()));
                fields.add(new PlannedField(field.name(), field.type(), String.join(" | ", read),
                        clash.theirs().description().isBlank()
                                ? field.description()
                                : clash.theirs().description(), field.transform(), field.multiple(),
                        field.items(), field.distinctBy()));
            } else {
                fields.add(field);
            }
        }
        final FieldPlan changed = plan.withFields(fields);
        return withInherited(NEW_VERSION.equals(choice)
                ? changed.withIndexName(nextVersion(plan.indexName()))
                : changed, entry);
    }

    /// @param name   A field's name.
    /// @param suffix What it is to end with: a source type group's mode, say.
    /// @return The name of a field of its own for this build: `name_suffix`.
    public static String renamed(final String name, final String suffix) {
        return name + "_" + (suffix == null || suffix.isBlank()
                ? "2"
                : suffix);
    }

    /// @param index An index's name.
    /// @return Its next version's name: `-v2` added, or the version it ends with counted on (`acme-v2` to `acme-v3`).
    public static String nextVersion(final String index) {
        final Matcher versioned = VERSIONED.matcher(index);
        if (versioned.matches()) {
            return versioned.group(1) + versioned.group(2) + (Long.parseLong(versioned.group(3)) + 1);
        }
        return index + "-v2";
    }

    /// @param plan  A plan for the output feed's index, or a new version of it.
    /// @param entry The index as recorded, or empty.
    /// @return The plan's fields the index doesn't have yet: every one for an index not recorded (or a new version).
    public static List<PlannedField> newFields(final FieldPlan plan, final Map<String, Object> entry) {
        if (!sameIndex(plan, entry)) {
            return plan.fields();
        }
        final Set<String> have = new LinkedHashSet<>();
        recordedFields(entry).forEach(field -> have.add(field.name()));
        return plan.fields().stream().filter(field -> !have.contains(field.name())).toList();
    }

    /// Records a promoted build's plan as its output feed's index: its fields added to those recorded (forward only:
    /// a field already there is kept, gaining what the plan reads besides, as the user agreed for a field of the
    /// same meaning), or, for a new version or an index not recorded yet, the plan's fields in their place. The
    /// feed's shared backend and naming are recorded from the plan where none are yet.
    ///
    /// @param registry   A registry's machine block, changed in place.
    /// @param outputFeed The output feed the plan's index holds the Events of.
    /// @param plan       The plan, its clashes resolved.
    /// @param indexDoc   The index's document (`type`, `uuid`, `name`), or null to keep the one recorded.
    /// @param decided    Who promoted it, when, in which build.
    /// @return The names of the fields added.
    public static List<String> record(final Map<String, Object> registry,
                                      final String outputFeed,
                                      final FieldPlan plan,
                                      final Map<String, Object> indexDoc,
                                      final Decided decided) {
        final Map<String, Object> earlier = index(registry, outputFeed);
        final boolean same = sameIndex(plan, earlier);
        final Map<String, Object> entry = same
                ? new LinkedHashMap<>(earlier)
                : new LinkedHashMap<>();
        final List<PlannedField> fields = same
                ? new ArrayList<>(recordedFields(earlier))
                : new ArrayList<>();
        final Map<String, Object> addedBy = same
                ? new LinkedHashMap<>(JsonValues.mapOrEmpty(earlier.get(ADDED_BY)))
                : new LinkedHashMap<>();
        final List<String> added = new ArrayList<>();
        for (final PlannedField field : plan.fields()) {
            final int at = indexOf(fields, field.name());
            if (at < 0) {
                fields.add(field);
                addedBy.put(field.name(), decided.build());
                added.add(field.name());
            } else if (field.type().equalsIgnoreCase(fields.get(at).type())) {
                // Same field: what it reads besides is kept (a field the user said both groups mean the same by).
                fields.set(at, field);
            }
        }
        entry.put(INDEX, plan.indexName());
        entry.put(BACKEND, plan.backend());
        if (indexDoc != null) {
            entry.put("index_doc", new LinkedHashMap<>(indexDoc));
        } else if (!same) {
            entry.remove("index_doc");
        }
        entry.put(FIELDS, fields.stream().map(PlannedField::toJson).toList());
        entry.put(ADDED_BY, addedBy);
        final Set<String> groups = new LinkedHashSet<>(JsonValues.strings(entry.get(GROUPS)));
        groups.addAll(groupsTo(registry, outputFeed));
        entry.put(GROUPS, List.copyOf(groups));
        if (!same && !earlier.isEmpty()) {
            entry.put("replaces", JsonValues.text(earlier.get(INDEX)));
        }
        entry.put(DECIDED, decided.toJson());
        final Map<String, Object> indexes = new LinkedHashMap<>(JsonValues.mapOrEmpty(registry.get(INDEXES)));
        indexes.put(outputFeed, entry);
        registry.put(INDEXES, indexes);
        if (indexing(registry).get(BACKEND) == null) {
            settleIndexing(registry, plan.backend(), plan.convention() == null
                    ? null
                    : Map.of("convention", plan.convention()), decided);
        }
        return added;
    }

    /// @param registry A registry's machine block.
    /// @param index    An index's name.
    /// @return The output feed whose index it is, recorded, if any.
    public static Optional<String> outputFeedOfIndex(final Map<String, Object> registry, final String index) {
        return JsonValues.mapOrEmpty(registry.get(INDEXES)).entrySet().stream()
                .filter(each -> index.equals(JsonValues.text(JsonValues.mapOrEmpty(each.getValue()).get(INDEX))))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    // A name no field of the plan has: the one given, else with a number after it.
    private static String free(final FieldPlan plan, final String name) {
        String candidate = name;
        for (int n = 2; indexOf(plan.fields(), candidate) >= 0; n++) {
            candidate = name + n;
        }
        return candidate;
    }

    private static int indexOf(final List<PlannedField> fields, final String name) {
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).name().equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private static List<String> branches(final String source) {
        if (source == null || source.isBlank()) {
            return List.of();
        }
        return FieldPlans.branches(source).stream().map(String::strip).toList();
    }

    private static String lower(final String text) {
        return text == null
                ? null
                : text.toLowerCase(Locale.ROOT);
    }

    private static String normalised(final String text) {
        final String flat = WHITESPACE.matcher(text.strip().toLowerCase(Locale.ROOT)).replaceAll(" ");
        return flat.endsWith(".")
                ? flat.substring(0, flat.length() - 1)
                : flat;
    }


    // --------------------------------------------------------------------------------


    /// Who made a decision kept in the registry, and when.
    ///
    /// @param by    The user.
    /// @param at    When (ISO-8601).
    /// @param build The build it was made in.
    public record Decided(String by, String at, String build) {

        /// @return It as the registry keeps it.
        public Map<String, Object> toJson() {
            final Map<String, Object> json = new LinkedHashMap<>();
            json.put("by", by);
            json.put("at", at);
            json.put("build", build);
            return json;
        }
    }

    /// A field of a build's plan the output feed's index already has, otherwise.
    ///
    /// @param kind   [#TYPE_CLASH] or [#MEANING_CLASH].
    /// @param ours   The plan's field.
    /// @param theirs The index's.
    /// @param where  The index, its backend, and the build that added the field.
    public record Clash(String kind, PlannedField ours, PlannedField theirs, Context where) {

        /// @return The answers it takes, the first recommended: for another type, a field of another name or a new
        /// version of the index; for another meaning, one field read from both or a field of another name.
        public List<String> choices() {
            return TYPE_CLASH.equals(kind)
                    ? List.of(RENAME, NEW_VERSION)
                    : List.of(SAME_MEANING, RENAME);
        }

        /// @param suffix What a renamed field's name ends with.
        /// @return Each answer as the user is offered it (plain text, short), by its label.
        public Map<String, String> options(final String suffix) {
            final Map<String, String> out = new LinkedHashMap<>();
            final String rename = "rename this build's field to " + renamed(ours.name(), suffix);
            if (TYPE_CLASH.equals(kind)) {
                out.put("Rename: " + rename, RENAME);
                out.put("New index version: " + nextVersion(where.index()) + ", from now on", NEW_VERSION);
            } else {
                out.put("Same meaning: one field, read from both", SAME_MEANING);
                out.put("Different: " + rename, RENAME);
            }
            return out;
        }

        /// @return The question, in one line of plain text.
        public String question() {
            return TYPE_CLASH.equals(kind)
                    ? "Field " + ours.name() + " is " + ours.type() + " here but " + theirs.type() + " in index "
                      + where.index() + ". Which?"
                    : "Field " + ours.name() + " here: " + meaning(ours) + "; in index " + where.index() + ": "
                      + meaning(theirs) + ". Same meaning?";
        }

        /// @return What the clash is, said in full for the agent and the form's message.
        public String said() {
            final String by = where.addedBy() == null || where.addedBy().isBlank()
                    ? ""
                    : " (added by build " + where.addedBy() + ")";
            if (TYPE_CLASH.equals(kind)) {
                return "Field " + ours.name() + ": " + ours.type() + " in this build's plan, " + theirs.type()
                       + " in index " + where.index() + by + ". " + (FieldPlan.ELASTICSEARCH.equals(where.backend())
                        ? "Elasticsearch can't change a field's type in an index that has it."
                        : "Documents already indexed keep the old type, so searches over both would disagree.");
            }
            return "Field " + ours.name() + " (" + ours.type() + "): this build's plan " + meaning(ours) + ", index "
                   + where.index() + by + " " + meaning(theirs) + ".";
        }

        private static String meaning(final PlannedField field) {
            return field.description().isBlank()
                    ? "reads " + field.source()
                    : "is described as '" + field.description() + "'";
        }

        /// Where a clash is.
        ///
        /// @param index   The index's name.
        /// @param backend Its backend.
        /// @param addedBy The build that added the field, or null.
        public record Context(String index, String backend, String addedBy) {

        }
    }
}
