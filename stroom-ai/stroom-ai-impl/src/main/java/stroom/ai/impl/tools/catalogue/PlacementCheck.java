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

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.catalogue.CatalogueCheckResult.CouldType;
import stroom.ai.impl.tools.catalogue.CatalogueCheckResult.Typing;
import stroom.ai.impl.tools.draft.LocalCheck;
import stroom.ai.impl.tools.draft.VendorCode;
import stroom.ai.impl.tools.generation.Condition;
import stroom.ai.impl.tools.generation.FieldMapping;
import stroom.ai.impl.tools.generation.MappingError;
import stroom.ai.impl.tools.generation.MappingValidationException;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.sample.HeldText;
import stroom.ai.impl.tools.sample.JsonFieldNames;
import stroom.ai.impl.tools.sample.NameValueLists;
import stroom.ai.impl.tools.sample.NameValueLists.ChangeList;
import stroom.ai.impl.tools.sample.NameValueLists.NameValueList;
import stroom.ai.impl.tools.sample.SampleProfiler;
import stroom.ai.impl.tools.sample.TimePatterns;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.ai.impl.tools.validation.CloseMatches;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.tools.validation.EventSchema.Child;
import stroom.util.shared.NullSafe;

import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/// The checks of an event type's typed placements ([Placement]) against the schema and the records, as the
/// catalogue reads them (decided by the user 2026-11-21), and what the schema requires of its action element:
///
/// * **Problems** (refused where the proposals bring them): a path the schema hasn't below the action element (with
///   what it allows there), one under Data, one that holds elements rather than a value; a field no record has, with
///   the record fields it may have meant (by its last name, then by spelling); a field holding several values in a
///   record placed where one goes, without `repeat` or `for_each`, or a `for_each` writing an element the schema
///   has once; values that, converted as the translation converts them (transform, map, default, time_format), the
///   element's type, values or pattern refuse, named with the fix; a pair (2026-11-23) of a list that isn't a
///   name/value list ([NameValueLists]) or has no pair of its name (naming those it has), and a for_each group over a
///   name/value list (each setting would be one element, its value in an element meaning something else); a change
///   (2026-11-23) on an action element with no Before and After, of a list that isn't an old/new list
///   ([NameValueLists#changes(java.util.Collection)]), with keys that aren't its items', or no property of its name,
///   and a for_each group over an old/new list (its path checked in both snapshots, its values both sides').
/// * **Not checked** (said, never refused): the values of a placement read by an xpath or a lookup (2026-11-22),
///   which only the translation reads; its path, and a lookup's key field, are checked as the others are.
/// * **Unplaced** (refuses confirmation, never proposals): an element the schema requires of the action element (or
///   of an element placed below it) that nothing the catalogue gives writes, e.g. Update/After, Send/Destination,
///   the object View needs: derived from the schema, element by element.
/// * **Typing**: how many fields its records' events type and keep as Data; a high share kept as Data is only worth
///   a look, and the agent is told what it could type ([CatalogueCheckResult.CouldType], 2026-11-22).
///
/// The values are converted here as [stroom.ai.impl.tools.generation.XPathText#transformExpression] writes each
/// transform in the XSLT.
final class PlacementCheck {

    /// The fewest Data fields for a high share of them to be worth a look.
    static final int DATA_FIELDS_NOTED = 5;
    /// The share of an event type's fields kept as Data, at least, worth a look.
    static final double DATA_SHARE_NOTED = 0.8;

    private static final String EVENT_DETAIL = "EventDetail/";
    private static final String DATA = "Data";
    private static final String SYSTEM = "System";
    private static final String UNKNOWN = "Unknown";
    // The most distinct values a placement's check reads, and names when they fail.
    private static final int VALUES_READ = 2_000;
    private static final int SHOWN = 3;
    private static final int LEAVES_SHOWN = 8;
    // The most of a choice's members named where an element required below one of them is left empty.
    private static final int CHOICES_SHOWN = 20;
    // What could be typed: the most Data fields listed, the longest example, the most groups of the schema's
    // elements, and the most elements in one.
    private static final int COULD_TYPE_FIELDS = 12;
    private static final int EXAMPLE_LENGTH = 30;
    private static final int HOME_GROUPS = 6;
    private static final int HOMES_SHOWN = 14;
    // EventSource's objects listed (those partly written first), and the most leaves of each.
    private static final int SOURCE_GROUPS = 4;
    private static final int SOURCE_LEAVES_SHOWN = 8;
    // The most of an item's keys named, and the most arrays of objects kept as Data listed.
    private static final int ITEM_KEYS_SHOWN = 6;
    private static final int ARRAYS_SHOWN = 4;
    private static final List<String> PREFERRED_LEAVES = List.of("Id", "Name", "EmailAddress", "Path", "Title",
            "HostName", "IPAddress");
    private static final Pattern DATE_TIME = Pattern.compile(
            "-?\\d{4,}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})?");
    private static final Pattern INTEGER = Pattern.compile("[+-]?\\d+");
    private static final Pattern DECIMAL = Pattern.compile("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)");
    private static final Set<String> INTEGER_TYPES = Set.of("integer", "int", "long", "short", "byte",
            "nonNegativeInteger", "positiveInteger", "nonPositiveInteger", "negativeInteger", "unsignedInt",
            "unsignedLong", "unsignedShort", "unsignedByte");
    private static final Set<String> NON_NEGATIVE = Set.of("nonNegativeInteger", "positiveInteger", "unsignedInt",
            "unsignedLong", "unsignedShort", "unsignedByte");
    private static final Set<String> DECIMAL_TYPES = Set.of("decimal", "double", "float");
    private static final Pattern STRIP_DOMAIN_BACKSLASH = Pattern.compile("^[^\\\\]*\\\\");
    private static final Pattern STRIP_DOMAIN_AT = Pattern.compile("@.*$");
    private static final Pattern DOMAIN = Pattern.compile("^(?:([^\\\\]*)\\\\.*|[^@]*@(.*))$");
    private static final Pattern NOT_DIGIT = Pattern.compile("[^0-9]");
    private static final Pattern CONTROL = Pattern.compile("\\p{Cc}\\[[0-9;]*[A-Za-z]|[\\p{Cc}&&[^\\t\\n\\r]]");
    private static final Pattern ESCAPED = Pattern.compile("\\\\([\"\\\\])");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private final EventSchema schema;
    private final RecordIndex index;
    private final UnaryOperator<String> missing;
    private final Set<String> common;
    private final String commonUser;
    private final List<String> commonPaths;
    private final Set<String> nil;
    private final Catalogue catalogue;
    private final List<CatalogueCheck.Drafted.Rule> rules;
    private final Function<List<Condition>, BitSet> recognises;
    private List<BitSet> byRule;
    // Each list field told a name/value list (or not), over every record holding it.
    private final Map<String, Optional<NameValueList>> nameValueLists = new HashMap<>();
    // Each list field told an old/new list (or not), over every record holding it; a list inside an array's items by
    // the array and its key.
    private final Map<List<String>, Optional<ChangeList>> changeLists = new HashMap<>();
    // The user's answers of which key of each old/new list holds the value before the change, by the list.
    private final Map<String, String> changeSides;

    /// @param schema    The event-logging schema.
    /// @param index     The scope's records.
    /// @param missing   Why a field no record has is refused, with the fields it may have meant.
    /// @param catalogue The catalogue the event types are of.
    /// @param drafted    What the draft writes: in common, and its own rules.
    /// @param recognises The records conditions recognise, as the check evaluates them.
    PlacementCheck(final EventSchema schema,
                   final RecordIndex index,
                   final UnaryOperator<String> missing,
                   final Catalogue catalogue,
                   final CatalogueCheck.Drafted drafted,
                   final Function<List<Condition>, BitSet> recognises) {
        this.schema = schema;
        this.index = index;
        this.missing = missing;
        this.catalogue = catalogue;
        this.rules = drafted.rules();
        this.changeSides = drafted.changeSides();
        this.recognises = recognises;
        final TranslationMapping reading = index.sample().reading();
        this.common = new HashSet<>(drafted.commonFields());
        final List<String> paths = new ArrayList<>(drafted.commonPaths());
        String user = drafted.commonUser();
        for (final FieldMapping entry : NullSafe.list(reading.common())) {
            paths.add(ToolText.strip(entry.path(), "/"));
            if (entry.field() != null) {
                this.common.add(entry.field());
                if (user == null && "EventSource/User/Id".equals(ToolText.strip(entry.path(), "/"))) {
                    user = entry.field();
                }
            }
            this.common.addAll(NullSafe.list(entry.anyOf()));
        }
        this.commonUser = user;
        this.commonPaths = List.copyOf(paths);
        this.nil = new HashSet<>(NullSafe.list(reading.nilValues()));
    }

    /// @param type       An event type.
    /// @param recognised The records it recognises.
    /// @return Its placements' problems, the schema's required elements nothing writes, what isn't checked, and its
    /// typing.
    Checked check(final EventType type, final BitSet recognised) {
        final List<String> problems = new ArrayList<>();
        final List<String> unplaced = new ArrayList<>();
        final List<String> notChecked = new ArrayList<>();
        final String detail = detail(type);
        if (detail == null || type.drop()) {
            return new Checked(problems, unplaced, notChecked, null);
        }
        final String label = "event type " + type.typeId();
        final Map<String, String> fill = new LinkedHashMap<>();
        final List<String> written = new ArrayList<>();
        // What is written in EventSource, from the root: the common entries', the placements' there, the draft's.
        final Set<String> sourceWritten = new LinkedHashSet<>();
        for (final Placement given : type.allPlacements()) {
            final boolean implied = !type.placements().contains(given);
            final Placement placement = implied
                    ? impliedLeaf(type, given)
                    : given;
            if (placement == null) {
                // affected_as isn't an object under the element: the meaning's check says so.
                continue;
            }
            final String said = label + ": " + (implied
                    ? "affected " + given.field() + " (" + given.path() + ")"
                    : "placement '" + placement.path() + "'");
            final int before = problems.size();
            placementProblems(said, detail, placement, recognised, problems);
            if (problems.size() == before && placement.readElsewhere()) {
                notChecked.add(said + ": its values aren't checked here (read by an xpath or a lookup, which only "
                               + "the translation reads); its path is checked");
            }
            if (problems.size() == before || implied) {
                written.addAll(placement.writes(detail));
                if (placement.inEventSource()) {
                    sourceWritten.addAll(placement.writesFromRoot(detail));
                }
            }
            if (placement.field() != null && recognised.cardinality() > 0) {
                fill.put(placement.field(), filled(placement.field(), recognised) + " of "
                                            + recognised.cardinality());
            }
        }
        written.addAll(givenElsewhere(type, detail));
        written.addAll(drafted(detail, recognised));
        commonPaths.stream().filter(path -> path.startsWith(Placement.EVENT_SOURCE)).forEach(sourceWritten::add);
        draftedFromRoot(recognised).stream()
                .filter(path -> path.startsWith(Placement.EVENT_SOURCE))
                .forEach(sourceWritten::add);
        unplaced.addAll(required(label, detail, written));
        return new Checked(problems, unplaced, notChecked, typing(type, detail, recognised, fill, written,
                sourceWritten));
    }

    // The action element, as the translation writes it; null for none, Unknown, or one the schema hasn't.
    private String detail(final EventType type) {
        if (NullSafe.isBlankString(type.eventDetail())) {
            return null;
        }
        final String detail = ToolText.strip(type.eventDetail().strip(), "/");
        if (detail.isEmpty() || UNKNOWN.equals(detail)) {
            return null;
        }
        try {
            schema.resolve(EVENT_DETAIL + detail);
        } catch (final IllegalArgumentException e) {
            return null;
        }
        return detail;
    }

    // affected and affected_as as a placement at the leaf the translation writes; null where it isn't one.
    private Placement impliedLeaf(final EventType type, final Placement implied) {
        final String leaf = EventMeaning.affected(schema, "", type.eventDetail(), type.affectedAs()).path();
        return leaf == null
                ? null
                : Placement.affected(implied.field(), leaf);
    }

    // What the translation writes below the action element besides the placements: the Action, the outcome, the
    // user authenticating (who acted, else the common user), the common entries written below it, and the Data left
    // over.
    private List<String> givenElsewhere(final EventType type, final String detail) {
        final List<String> out = new ArrayList<>();
        out.add(DATA);
        if (NullSafe.isNonBlankString(type.action())) {
            out.add("Action");
        }
        if (NullSafe.isNonBlankString(type.success())) {
            out.add("Outcome/Success");
        }
        final String user = NullSafe.isNonBlankString(type.actor())
                ? type.actor()
                : commonUser;
        if ("Authenticate".equals(detail) && user != null) {
            out.add("User/Id");
        }
        final String prefix = EVENT_DETAIL + detail + "/";
        commonPaths.stream()
                .filter(path -> path.startsWith(prefix))
                .map(path -> path.substring(prefix.length()))
                .forEach(out::add);
        return out;
    }

    // What the draft's own rule taking most of the type's records (each record the first rule's that takes it)
    // writes below the action element: the translation's rule starts from its fields.
    private List<String> drafted(final String detail, final BitSet recognised) {
        final String prefix = EVENT_DETAIL + detail + "/";
        return draftedFromRoot(recognised).stream()
                .filter(path -> path.startsWith(prefix))
                .map(path -> path.substring(prefix.length()))
                .toList();
    }

    // The paths, from the root, the draft's own rule taking most of the records writes.
    private List<String> draftedFromRoot(final BitSet recognised) {
        if (rules.isEmpty() || recognised.isEmpty()) {
            return List.of();
        }
        if (byRule == null) {
            byRule = rules.stream().map(rule -> recognises.apply(rule.when())).toList();
        }
        final int[] taken = new int[rules.size()];
        for (int n = recognised.nextSetBit(0); n >= 0; n = recognised.nextSetBit(n + 1)) {
            for (int r = 0; r < rules.size(); r++) {
                if (byRule.get(r).get(n)) {
                    taken[r]++;
                    break;
                }
            }
        }
        int best = -1;
        for (int r = 0; r < rules.size(); r++) {
            if (taken[r] > 0 && (best < 0 || taken[r] > taken[best])) {
                best = r;
            }
        }
        return best < 0
                ? List.of()
                : rules.get(best).paths();
    }

    // --------------------------------------------------------------------------------
    // One placement


    private void placementProblems(final String said,
                                   final String detail,
                                   final Placement placement,
                                   final BitSet recognised,
                                   final List<String> problems) {
        // A path in EventSource (2026-11-22) is read from the event's root; the others below the action element.
        final boolean source = placement.inEventSource();
        final String base = source
                ? ""
                : EVENT_DETAIL + detail + "/";
        final String given = source
                ? placement.path()
                : placement.pathBelow(detail);
        if (given == null || given.isEmpty()) {
            problems.add(said + ": its path is below its action element, " + detail + " (e.g. 'Email/Subject', "
                         + "or 'EventDetail/" + detail + "/Email/Subject'), or in EventSource (e.g. "
                         + "'EventSource/Client/Name')");
            return;
        }
        if (placement.snapshots()) {
            if (source || !schema.hasPath(base + "After")) {
                problems.add(said + ": change writes the Before and After snapshots of an Update, which " + detail
                             + " hasn't: read a property's new value by its name with pair, e.g. {path: "
                             + "'<element>', pair: {list: '<list>', name: '<name>', value_key: '<the key of the new "
                             + "value>'}}");
                return;
            }
            if (!recognised.isEmpty()) {
                final String changeProblem = changeProblem(placement);
                if (changeProblem != null) {
                    problems.add(said + ": " + changeProblem);
                    return;
                }
            }
        }
        // A change's values are checked where they go in After (Before has the same elements).
        final String below = placement.snapshots()
                ? "After/" + placement.snapshotPath(detail)
                : given;
        if (placement.pair() != null && !recognised.isEmpty()) {
            // No sampled record of it: its pair's list can't be looked for here (its form is checked).
            final String pairProblem = pairProblem(placement);
            if (pairProblem != null) {
                problems.add(said + ": " + pairProblem);
                return;
            }
        }
        final String form = formProblem(completed(placement), detail);
        if (form != null) {
            problems.add(said + ": " + form);
            return;
        }
        final Map<String, Child> leaves = new LinkedHashMap<>();
        for (final String path : source
                ? placement.writesFromRoot(detail)
                : placement.writes(detail)) {
            final String problem = pathProblem(base, path, leaves);
            if (problem != null) {
                problems.add(said + ": " + problem);
                return;
            }
            if (source && commonPaths.contains(path)) {
                // The rule's entry would take the common entry's place for this event type's records only: refused,
                // so what every event gives there is changed in one place, the common entry.
                problems.add(said + ": the common entries already write " + path + " for every event: leave it to "
                             + "them (or change the common entry), or place the field at another element of "
                             + "EventSource");
                return;
            }
        }
        // The group's own element must repeat in its parent: one higher up that repeats (Payload/Email for
        // Payload/Email/From) would make each item another of it, not another From.
        if (placement.group() && !schema.repeatsInPlace(base + below)) {
            problems.add(said + ": for_each writes " + below + " once per item, but the schema has one " + below
                         + " in an event: put the group on an element that repeats (e.g. Email/To), or place one "
                         + "value, or leave the field as Data");
            return;
        }
        if (recognised.isEmpty()) {
            // No sampled record of it (one from documentation): its fields can't be looked for here.
            return;
        }
        boolean inputsOk = true;
        for (final String input : placement.inputs()) {
            if (!index.untold(input) && !index.anyHas(input)) {
                problems.add(said + ": " + nearest(input));
                inputsOk = false;
            }
        }
        if (!inputsOk) {
            return;
        }
        if (placement.group()) {
            final String pairs = groupOfPairsProblem(below, placement);
            if (pairs != null) {
                problems.add(said + ": " + pairs);
                return;
            }
            final String items = groupItemsProblem(below, placement, recognised);
            if (items != null) {
                problems.add(said + ": " + items);
                return;
            }
            final String changes = groupChangeListProblem(below, placement);
            if (changes != null) {
                problems.add(said + ": " + changes);
                return;
            }
        } else {
            final String items = itemsProblem(below, placement, recognised);
            if (items != null) {
                problems.add(said + ": " + items);
                return;
            }
            final String repeated = repetitionProblem(base, below, placement, recognised);
            if (repeated != null) {
                problems.add(said + ": " + repeated);
                return;
            }
        }
        valueProblems(said, base, below, completed(placement), recognised, leaves).ifPresent(problems::add);
    }

    // The mapping's own reading of the entry (its for_each form, its conversions); null when it reads.
    private static String formProblem(final Placement placement, final String detail) {
        try {
            FieldMapping.fromJson(placement.mappingEntry(detail));
            return null;
        } catch (final MappingValidationException e) {
            final List<String> said = new ArrayList<>();
            for (final MappingError error : e.errors()) {
                said.add((error.dotted().isEmpty()
                        ? ""
                        : error.dotted() + ": ") + error.message().replaceFirst("^Value error, ", ""));
            }
            return String.join("; ", said);
        } catch (final IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    // A path below the base (the action element's, or the root for EventSource's) the schema has, to a leaf, not
    // under Data; null when it is one.
    private String pathProblem(final String base, final String path, final Map<String, Child> leaves) {
        final String full = base + path;
        final List<Child> chain;
        try {
            chain = schema.resolve(full);
        } catch (final IllegalArgumentException e) {
            return e.getMessage();
        }
        if (chain.stream().anyMatch(child -> DATA.equals(child.name()))) {
            return path + " is Data: a placement types a field, and the fields no placement takes are kept as Data "
                   + "anyway; place it at a typed element, or leave it out";
        }
        final Element decl = chain.getLast().decl();
        if (!schema.isLeaf(decl)) {
            final List<String> names = new ArrayList<>();
            leafNames(decl, path, 2, names);
            // The leaves a value most often goes in first.
            names.sort(Comparator.comparingInt(name -> PREFERRED_LEAVES.contains(name.substring(
                    name.lastIndexOf('/') + 1))
                    ? 0
                    : 1));
            return path + " holds elements, not a value: name its leaf, one of " + String.join(", ",
                    names.subList(0, Math.min(LEAVES_SHOWN, names.size())));
        }
        leaves.put(path, chain.getLast());
        return null;
    }

    private void leafNames(final Element decl, final String at, final int depth, final List<String> out) {
        for (final Child child : schema.children(decl)) {
            if (DATA.equals(child.name()) || "Meta".equals(child.name())) {
                continue;
            }
            if (schema.isLeaf(child.decl())) {
                out.add(at + "/" + child.name());
            } else if (depth > 1) {
                leafNames(child.decl(), at + "/" + child.name(), depth - 1, out);
            }
        }
    }

    // A field no record has, with the record fields it may have meant: by its last name, else by spelling.
    private String nearest(final String field) {
        final String said = missing.apply(field);
        if (said.contains("(the records have")) {
            return said;
        }
        final List<String> close = CloseMatches.closeMatches(field, index.fieldNames(), SHOWN,
                CloseMatches.DEFAULT_CUTOFF);
        return close.isEmpty()
                ? said
                : said.replace(": name a field", " (the records have " + String.join(", ", close) + "): name a "
                                                 + "field");
    }

    // A field whose values are items with fields of their own (objects, as the records read them) placed at a
    // leaf: why it is refused, or null. Chat 183: an array of {Name, Value} placed at Configuration/Name, each item
    // written as its key and value run together ("NameFW to personal").
    private String itemsProblem(final String below, final Placement placement, final BitSet recognised) {
        final String field = placement.field();
        if (field == null) {
            return null;
        }
        final ItemKeys found = itemKeys(field, recognised, false);
        if (found == null) {
            return null;
        }
        final Optional<ChangeList> changes = changeList(field);
        if (changes.isPresent()) {
            return field + " is an old/new list (" + namesSaid(changes.get().names()) + "): " + below + " takes one "
                   + "value, so each property's name and values would be written run together: read the property "
                   + "whose name means " + below + " by its name, " + changeExample(field, changes.get(), below);
        }
        final Optional<NameValueList> pairs = nameValueList(field);
        if (pairs.isPresent()) {
            return field + " is a name/value list (" + namesSaid(pairs.get().names()) + "): " + below + " takes one "
                   + "value, so each pair would be written as its name and value run together: read the pair whose "
                   + "name means " + below + " by its name, e.g. {path: '" + below + "', pair: {list: '" + field
                   + "', name: '" + pairs.get().names().keySet().iterator().next() + "'}}, or leave it as Data (a "
                   + "Data per pair, each named by its pair's name)";
        }
        final Map<?, ?> first = found.first();
        final List<String> named = found.keys();
        final int slash = below.lastIndexOf('/');
        final String parent = slash < 0
                ? below
                : below.substring(0, slash);
        final String leaf = below.substring(slash + 1);
        final String key = String.valueOf(first.keySet().iterator().next());
        final String other = named.stream().filter(name -> !name.equals(key)).findFirst().orElse(key);
        final String group = "{path: '" + parent + "', for_each: {array: '" + field + "'}, fields: [{path: '" + leaf
                             + "', field: '" + key + "'}, ...]}";
        final String pick = jsonXpath(field, key, ToolText.str(first.get(key)), other);
        return field + "'s items have fields of their own (" + cappedTo(named, ITEM_KEYS_SHOWN) + "): " + below
               + " takes one value, so each item would be written as its fields' text run together: give a for_each "
               + "group with fields, e.g. " + group + ", or an xpath picking one item" + (pick == null
                ? " (e.g. the item whose " + key + " is '" + ToolText.str(first.get(key)) + "')"
                : ", e.g. {path: '" + below + "', xpath: \"" + pick + "\"}") + ", or leave it as Data";
    }

    // A group's field reading its item whole (item: true) where the group's items are objects with fields of their
    // own: why it is refused, or null. Chat 184: after the leaf placement was refused, the agent wrote
    // {path: 'Configuration', for_each: {array: 'event.Parameters'}, fields: [{path: 'Name', item: true}]}, and
    // each {Name, Value} item was written to Configuration/Name as its fields' text run together again.
    private String groupItemsProblem(final String below, final Placement placement, final BitSet recognised) {
        final Map<String, Object> entry = placement.entry();
        final Object array = JsonValues.mapOrEmpty(entry.get(Placement.FOR_EACH)).get("array");
        if (!(array instanceof final String field) || field.isBlank()) {
            return null;
        }
        final List<String> whole = JsonValues.maps(entry.get(Placement.FIELDS)).stream()
                .filter(PlacementCheck::readsItemWhole)
                .map(inner -> ToolText.strip(ToolText.str(inner.get(Placement.PATH)).strip(), "/"))
                .toList();
        if (whole.isEmpty()) {
            return null;
        }
        final ItemKeys found = itemKeys(field, recognised, true);
        if (found == null) {
            return null;
        }
        final String leaf = whole.getFirst();
        final String at = leaf.isEmpty()
                ? below
                : below + "/" + leaf;
        final String key = keyFor(leaf, found.keys());
        return field + "'s items have fields of their own (" + cappedTo(found.keys(), ITEM_KEYS_SHOWN) + "): "
               + "{path: '" + leaf + "', item: true} writes each item whole at " + at + ", its fields' text run "
               + "together: read a key of the item instead, e.g. {path: '" + leaf + "', field: '" + key + "'}, a "
               + "field of the group for each key the schema has a home for, or leave the rest as Data";
    }

    // A group's field writing its item's old/new list whole at a typed element (a key read with field, or a group
    // over it): why it is refused, or null. The list's properties go to Data per property of each snapshot's object
    // whatever the placement says (2026-11-23, eval case 76), so only one read by its name is typed; at Data the
    // list is written per property, as the draft writes a list read whole as Data.
    private String groupChangeListProblem(final String below, final Placement placement) {
        final Object array = JsonValues.mapOrEmpty(placement.entry().get(Placement.FOR_EACH)).get("array");
        if (!(array instanceof final String field) || field.isBlank()) {
            return null;
        }
        for (final Map<String, Object> inner : JsonValues.maps(placement.entry().get(Placement.FIELDS))) {
            final String leaf = ToolText.strip(ToolText.str(inner.get(Placement.PATH)).strip(), "/");
            final Object forEach = JsonValues.mapOrEmpty(inner.get(Placement.FOR_EACH)).get("array");
            final Object key = forEach instanceof String
                    ? forEach
                    : inner.containsKey(Placement.CHANGE) || inner.containsKey(Placement.PAIR)
                            ? null
                            : inner.get(Placement.FIELD);
            if (!(key instanceof final String list) || DATA.equals(leaf) || leaf.endsWith("/" + DATA)) {
                continue;
            }
            final Optional<ChangeList> changes = nestedChangeList(field, list);
            if (changes.isPresent()) {
                return "each item's " + list + " is an old/new list (" + namesSaid(changes.get().names()) + "): "
                       + below + "/" + leaf + " takes one value, so its properties' names and values would be "
                       + "written run together. Its properties are written as Data of each item's object in Before "
                       + "and After; read one whose name means " + below + "/" + leaf + " by its name, e.g. {path: '"
                       + leaf + "', change: {list: '" + list + "', name: '" + changes.get().names().keySet()
                               .iterator().next() + "'}}";
            }
        }
        return null;
    }

    // A group's field that reads its own item whole: item: true, no key of it, no group of its own, not the record.
    private static boolean readsItemWhole(final Map<String, Object> field) {
        return Boolean.TRUE.equals(field.get("item")) && !(field.get(Placement.FIELD) instanceof String)
               && !field.containsKey(Placement.FOR_EACH) && !"record".equals(field.get("scope"));
    }

    // The item's key to read at a leaf, for an example: the one of the leaf's name (any case), else the first.
    private static String keyFor(final String leaf, final List<String> keys) {
        final String name = leaf.substring(leaf.lastIndexOf('/') + 1);
        return keys.stream()
                .filter(key -> key.equalsIgnoreCase(name))
                .findFirst()
                .orElse(keys.getFirst());
    }

    // The keys of a field's items where most of the recognised records holding it hold objects (arrays of them only,
    // where arrays is true): read from the records as they are, as the index's values are each item's text (its
    // fields' run together); null where they don't.
    private ItemKeys itemKeys(final String field, final BitSet recognised, final boolean arrays) {
        final List<Object> records = index.sample().records();
        final Set<String> keys = new LinkedHashSet<>();
        Map<?, ?> first = null;
        int objects = 0;
        int held = 0;
        for (int n = recognised.nextSetBit(0); n >= 0 && n < records.size() && held < VALUES_READ;
             n = recognised.nextSetBit(n + 1)) {
            if (!(records.get(n) instanceof final Map<?, ?> record) || record.get(field) == null) {
                continue;
            }
            held++;
            final Object value = record.get(field);
            if (arrays && !isArray(value)) {
                continue;
            }
            final List<Map<?, ?>> items = objects(value);
            if (!items.isEmpty()) {
                objects++;
                for (final Map<?, ?> item : items) {
                    item.keySet().forEach(key -> keys.add(String.valueOf(key)));
                    if (first == null && !item.isEmpty()) {
                        first = item;
                    }
                }
            }
        }
        if (first == null || objects * 2 <= held) {
            return null;
        }
        return new ItemKeys(new ArrayList<>(keys), first);
    }

    // A list as read, or JSON text holding one.
    private static boolean isArray(final Object value) {
        return value instanceof List<?>
               || value instanceof final String text && ToolText.strip(text).startsWith("[");
    }

    // The objects a value holds: itself, or an array's items that are objects (as read, or JSON text holding
    // them); none for any other value.
    private static List<Map<?, ?>> objects(final Object value) {
        Object read = value;
        if (value instanceof final String string) {
            final String text = ToolText.strip(string);
            if (!text.startsWith("{") && !text.startsWith("[")) {
                return List.of();
            }
            try {
                read = SampleProfiler.loadsDecoded(text);
            } catch (final ToolJson.JsonDecodeException e) {
                return List.of();
            }
        }
        if (read instanceof final Map<?, ?> map) {
            return List.of(map);
        }
        final List<Map<?, ?>> out = new ArrayList<>();
        if (read instanceof final List<?> list) {
            list.stream().filter(Map.class::isInstance).forEach(item -> out.add((Map<?, ?>) item));
        }
        return out;
    }

    // An xpath reading one item of a JSON input's array of objects (the item whose key is the value) and the other
    // key of it, as the JSONParser writes the record; null for a field of another input, or held JSON.
    private String jsonXpath(final String field, final String key, final String value, final String other) {
        final TranslationMapping reading = index.sample().reading();
        if (!"json".equals(reading.input()) || NullSafe.list(reading.jsonFields()).stream()
                .anyMatch(held -> field.equals(held) || field.startsWith(held + "."))
            || value.indexOf('\'') >= 0) {
            return null;
        }
        final StringBuilder out = new StringBuilder();
        for (final String step : JsonFieldNames.keys(field)) {
            if (step.indexOf('\'') >= 0) {
                return null;
            }
            out.append(out.isEmpty()
                    ? ""
                    : "/").append("*[@key='").append(step).append("']");
        }
        return out + "/*[*[@key='" + key + "'] = '" + value + "']/*[@key='" + other + "']";
    }

    // Several values in a record placed where the schema has one, or without repeat: why, or null.
    private String repetitionProblem(final String base,
                                     final String below,
                                     final Placement placement,
                                     final BitSet recognised) {
        final String field = placement.field();
        if (field == null || JsonValues.truthy(placement.entry().get("repeat"))) {
            return null;
        }
        final List<List<String>> values = index.values(field);
        int several = 0;
        List<String> example = null;
        for (int n = recognised.nextSetBit(0); n >= 0; n = recognised.nextSetBit(n + 1)) {
            final List<String> held = values.get(n).stream().filter(value -> !value.isBlank()).toList();
            if (held.size() > 1) {
                several++;
                if (example == null) {
                    example = held.subList(0, Math.min(SHOWN, held.size()));
                }
            }
        }
        if (several == 0) {
            return null;
        }
        final String head = field + " holds several values in " + several + " of its records (e.g. "
                            + ToolText.repr(example) + ")";
        return schema.takesSeveralInPlace(base + below)
                ? head + ", and only the first is written: give repeat: true (one " + below + " a value), or leave "
                  + "it as Data"
                : head + ", but " + below + " takes one: place them with for_each on an element that repeats (e.g. "
                  + "{path: 'Email/To', for_each: {array: '" + field + "'}, fields: [{path: 'EmailAddress', item: "
                  + "true}]}), or leave it as Data";
    }

    // --------------------------------------------------------------------------------
    // Values


    private Optional<String> valueProblems(final String said,
                                                     final String base,
                                                     final String below,
                                                     final Placement placement,
                                                     final BitSet recognised,
                                                     final Map<String, Child> leaves) {
        if (placement.group()) {
            final List<String> items = items(placement, recognised);
            for (final Map<String, Object> field : JsonValues.maps(placement.entry().get(Placement.FIELDS))) {
                final String own = ToolText.strip(ToolText.str(field.get(Placement.PATH)).strip(), "/");
                final String path = own.isEmpty()
                        ? below
                        : below + "/" + own;
                final List<String> values;
                if (field.get(Placement.VALUE) != null) {
                    values = List.of(ToolText.str(field.get(Placement.VALUE)));
                } else if (Boolean.TRUE.equals(field.get("item")) && field.get(Placement.FIELD) == null) {
                    values = items;
                } else if (field.get(Placement.XPATH) != null || field.get(Placement.LOOKUP) != null) {
                    // Read by the translation only: said as not checked.
                    continue;
                } else if ("record".equals(field.get("scope"))
                           && field.get(Placement.FIELD) instanceof final String read) {
                    values = values(read, recognised, true);
                } else {
                    // A key of an item: not read here.
                    continue;
                }
                final String problem = invalid(base + path, path, leaves.get(path), field, values);
                if (problem != null) {
                    return Optional.of(said + ": " + problem);
                }
            }
            return Optional.empty();
        }
        final Map<String, Object> entry = placement.entry();
        final List<String> values;
        if (entry.get(Placement.VALUE) != null) {
            values = List.of(ToolText.str(entry.get(Placement.VALUE)));
        } else if (placement.field() != null) {
            values = values(placement.field(), recognised, JsonValues.truthy(entry.get("repeat")));
        } else if (entry.get("any_of") != null) {
            values = anyOf(JsonValues.strings(entry.get("any_of")), recognised);
        } else if (placement.pair() != null) {
            values = pairValues(placement, recognised);
        } else if (placement.change() != null) {
            values = changeValues(placement, recognised);
        } else {
            // A join's parts each a value of its own, an xpath's and a lookup's values the translation's: not read
            // here.
            return Optional.empty();
        }
        final String problem = invalid(base + below, below, leaves.get(below), entry, values);
        return problem == null
                ? Optional.empty()
                : Optional.of(said + ": " + problem);
    }

    // A field's values in the records, the first of each (every one with repeat), blank and nil values aside.
    private List<String> values(final String field, final BitSet recognised, final boolean every) {
        final Set<String> out = new LinkedHashSet<>();
        final List<List<String>> held = index.values(field);
        for (int n = recognised.nextSetBit(0); n >= 0 && out.size() < VALUES_READ; n = recognised.nextSetBit(n + 1)) {
            for (final String value : held.get(n)) {
                if (value.isBlank() || nil.contains(ToolText.strip(value))) {
                    continue;
                }
                out.add(value);
                if (!every) {
                    break;
                }
            }
        }
        return List.copyOf(out);
    }

    private List<String> anyOf(final List<String> fields, final BitSet recognised) {
        final Set<String> out = new LinkedHashSet<>();
        for (int n = recognised.nextSetBit(0); n >= 0 && out.size() < VALUES_READ; n = recognised.nextSetBit(n + 1)) {
            for (final String field : fields) {
                final String first = index.values(field).get(n).stream()
                        .filter(value -> !value.isBlank() && !nil.contains(ToolText.strip(value)))
                        .findFirst()
                        .orElse(null);
                if (first != null) {
                    out.add(first);
                    break;
                }
            }
        }
        return List.copyOf(out);
    }

    // A group's items: split's pieces, an array's values (zip's not read here).
    private List<String> items(final Placement placement, final BitSet recognised) {
        final Map<String, Object> source = JsonValues.mapOrEmpty(placement.entry().get(Placement.FOR_EACH));
        if (source.get("split") instanceof final String field) {
            final String on = ToolText.str(source.get("on"));
            final Set<String> out = new LinkedHashSet<>();
            for (final String value : values(field, recognised, true)) {
                for (final String piece : value.split(Pattern.quote(on), -1)) {
                    if (!piece.isBlank()) {
                        out.add(piece.strip());
                    }
                }
            }
            return List.copyOf(out);
        }
        if (source.get("array") instanceof final String field) {
            return values(field, recognised, true).stream()
                    .filter(value -> !value.startsWith("{"))
                    .toList();
        }
        return List.of();
    }

    // Why the values, converted as the translation converts them, the element refuses; null when it takes them.
    private String invalid(final String full,
                           final String path,
                           final Child leaf,
                           final Map<String, Object> entry,
                           final List<String> given) {
        if (leaf == null || given.isEmpty()) {
            return null;
        }
        final String transform = entry.get("transform") instanceof final String t
                ? t
                : null;
        final Map<String, String> map = new LinkedHashMap<>();
        JsonValues.mapOrEmpty(entry.get("map")).forEach((key, value) -> map.put(key, ToolText.str(value)));
        final String fallback = entry.get("default") == null
                ? null
                : ToolText.str(entry.get("default"));
        final List<String> values = new ArrayList<>();
        for (final String value : given) {
            final String converted = transformed(transform, value);
            if (!map.isEmpty()) {
                final String mapped = map.containsKey(converted)
                        ? map.get(converted)
                        : fallback;
                if (mapped != null) {
                    values.add(mapped);
                }
            } else {
                values.add(converted);
            }
        }
        if (entry.get("time_format") instanceof final String format) {
            final String problem = TimePatterns.checkTimeFormat(format, values);
            return problem == null
                    ? null
                    : problem + ": give the time_format its values are written in, or leave the field as Data";
        }
        final Element decl = leaf.decl();
        final List<String> allowed = schema.enumeration(decl);
        final List<Pattern> patterns = LocalCheck.schemaPatterns(schema, full);
        final String type = NullSafe.nonBlankStringElse(schema.baseType(decl), "string");
        final List<String> bad = new ArrayList<>();
        for (final String value : values) {
            if (!valid(value, allowed, patterns, type)) {
                bad.add(value);
            }
        }
        if (bad.isEmpty()) {
            return null;
        }
        final String head = bad.size() + " of its " + values.size() + " distinct value(s) aren't valid for " + path
                            + " (" + takes(allowed, decl, type) + "): " + ToolText.repr(bad.subList(0,
                Math.min(SHOWN, bad.size())));
        if (entry.get(Placement.VALUE) != null) {
            return head + ": give a value the element takes";
        }
        return head + ": " + fix(bad, allowed, type) + ", or leave the field as Data (no placement)";
    }

    private boolean valid(final String value,
                          final List<String> allowed,
                          final List<Pattern> patterns,
                          final String type) {
        if (allowed != null) {
            return allowed.contains(value);
        }
        if (!patterns.isEmpty() && patterns.stream().noneMatch(p -> p.matcher(ToolText.strip(value)).matches())) {
            return false;
        }
        final String stripped = ToolText.strip(value);
        if ("dateTime".equals(type)) {
            return DATE_TIME.matcher(stripped).matches();
        }
        if ("boolean".equals(type)) {
            return List.of("true", "false", "1", "0").contains(stripped);
        }
        if (INTEGER_TYPES.contains(type)) {
            if (!INTEGER.matcher(stripped).matches()) {
                return false;
            }
            return !NON_NEGATIVE.contains(type) || !stripped.startsWith("-")
                                                   && !("positiveInteger".equals(type)
                                                        && stripped.replace("+", "").matches("0+"));
        }
        if (DECIMAL_TYPES.contains(type)) {
            return DECIMAL.matcher(stripped).matches();
        }
        return true;
    }

    private String takes(final List<String> allowed, final Element decl, final String type) {
        if (allowed != null) {
            return "one of " + String.join(", ", allowed.subList(0, Math.min(LEAVES_SHOWN * 2, allowed.size())));
        }
        if (!"string".equals(type)) {
            return "an xs:" + type;
        }
        if (!schema.patterns(decl).isEmpty()) {
            return "a value of the schema's pattern";
        }
        return "an xs:" + type;
    }

    private static String fix(final List<String> bad, final List<String> allowed, final String type) {
        if (allowed != null) {
            return "give map: {" + bad.stream().limit(SHOWN).map(value -> "'" + value + "': <one of those>")
                    .reduce((a, b) -> a + ", " + b).orElse("") + "} (and default for the rest)";
        }
        if ("dateTime".equals(type)) {
            final String inferred = TimePatterns.inferTimePattern(ToolText.strip(bad.getFirst()));
            return "give time_format" + (inferred == null
                    ? " (the Java pattern its values are written in)"
                    : ": '" + inferred + "'");
        }
        if ("boolean".equals(type)) {
            return "give map: {" + bad.stream().limit(SHOWN).map(value -> "'" + value + "': true|false")
                    .reduce((a, b) -> a + ", " + b).orElse("") + "}";
        }
        if (INTEGER_TYPES.contains(type) || DECIMAL_TYPES.contains(type)) {
            return "give transform: digits, or map, or split the number out of the text (for_each split)";
        }
        return "give a transform (lower, upper, trim, strip_domain, domain, digits) or map";
    }

    /// @param transform A transform, or null.
    /// @param value     A value.
    /// @return The value transformed as the XSLT transforms it.
    static String transformed(final String transform, final String value) {
        if (transform == null) {
            return value;
        }
        return switch (transform) {
            case "lower" -> value.toLowerCase(Locale.ROOT);
            case "upper" -> value.toUpperCase(Locale.ROOT);
            case "trim" -> SPACES.matcher(value.strip()).replaceAll(" ");
            case "strip_domain" -> STRIP_DOMAIN_AT.matcher(STRIP_DOMAIN_BACKSLASH.matcher(value).replaceFirst(""))
                    .replaceFirst("");
            case "domain" -> DOMAIN.matcher(value).matches()
                    ? DOMAIN.matcher(value).replaceFirst("$1$2")
                    : value;
            case "digits" -> NOT_DIGIT.matcher(value).replaceAll("");
            case "strip_control" -> CONTROL.matcher(value).replaceAll("");
            case "clean" -> HeldText.clean(value);
            case "unescape_quotes" -> value.replace("\"\"", "\"");
            case "unescape_backslashes" -> ESCAPED.matcher(value).replaceAll("$1");
            default -> value;
        };
    }

    // --------------------------------------------------------------------------------
    // What the schema requires


    // The elements the schema requires below the action element (and below each element written there) that
    // nothing written gives: each said with what it needs.
    private List<String> required(final String label, final String detail, final List<String> written) {
        final Node root = new Node();
        for (final String path : written) {
            Node node = root;
            for (final String step : path.split("/")) {
                if (!step.isEmpty()) {
                    node = node.kids.computeIfAbsent(step, ignored -> new Node());
                }
            }
        }
        final List<String> out = new ArrayList<>();
        final Element decl = schema.resolve(EVENT_DETAIL + detail).getLast().decl();
        walk(label, EVENT_DETAIL + detail, decl, root, out, new HashSet<>());
        return out;
    }

    // hinted: the choices whose other members were named already in this check, so each is named once.
    private void walk(final String label,
                      final String at,
                      final Element decl,
                      final Node node,
                      final List<String> out,
                      final Set<String> hinted) {
        if (schema.isLeaf(decl)) {
            return;
        }
        final List<Child> allowed = schema.children(decl);
        for (final Child child : allowed) {
            if (child.required() && !node.kids.containsKey(child.name())) {
                out.add(label + ": " + at + "/" + child.name() + " is required by the schema, and nothing the "
                        + "catalogue gives is written there: give a placement (e.g. {path: '"
                        + below(at + "/" + child.name()) + example(child.decl()) + "', field: <the field that "
                        + "holds it>}) from what the records hold" + otherChoices(at, hinted));
            }
        }
        for (final Map.Entry<Integer, List<String>> choice : schema.requiredChoices().entrySet()) {
            if (allowed.stream().anyMatch(child -> choice.getKey().equals(child.choice()))
                && choice.getValue().stream().noneMatch(node.kids::containsKey)) {
                out.add(label + ": " + at + " needs one of " + String.join(", ", choice.getValue().subList(0,
                        Math.min(LEAVES_SHOWN * 2, choice.getValue().size()))) + " (what the event is of), and "
                        + "nothing the catalogue gives is written there: give a placement (e.g. {path: '"
                        + below(at + "/" + choice.getValue().getFirst()) + example(allowed.stream()
                        .filter(child -> child.name().equals(choice.getValue().getFirst()))
                        .findFirst()
                        .map(Child::decl)
                        .orElse(null)) + "', field: <the field that holds it>})" + otherChoices(at, hinted));
            }
        }
        for (final Child child : allowed) {
            final Node kid = node.kids.get(child.name());
            if (kid != null && !DATA.equals(child.name())) {
                walk(label, at + "/" + child.name(), child.decl(), kid, out, hinted);
            }
        }
    }

    // Where an element required at or below `at` is left empty below a member of a choice the catalogue's
    // placements took: the choice's other members, at the shallowest such choice below the action element, so a
    // placement can go straight to one (seen in chat 185: a Move's folder placed through Source/Association, whose
    // required Objects was said to be empty one level deeper each round, when Move/Source offers Folder itself).
    // "" where no choice the placements took is on the path, or its members were named already.
    private String otherChoices(final String at, final Set<String> hinted) {
        final String rest = below(at);
        if (rest.isEmpty()) {
            return "";
        }
        final List<Child> chain = schema.resolve(at);
        final String[] steps = rest.split("/");
        final int first = chain.size() - steps.length;
        for (int i = Math.max(first, 1); i < chain.size(); i++) {
            final Child taken = chain.get(i);
            if (taken.choice() == null) {
                continue;
            }
            final List<Child> members = schema.children(chain.get(i - 1).decl()).stream()
                    .filter(child -> taken.choice().equals(child.choice()))
                    .toList();
            final String parent = String.join("/", Arrays.copyOfRange(steps, 0, i - first));
            if (members.size() < 2 || parent.isEmpty() || !hinted.add(parent)) {
                return "";
            }
            final List<String> names = members.stream().map(Child::name).toList();
            // No example member: the first that needs nothing more (Banner, for a folder) only misled.
            final String other = members.stream()
                    .anyMatch(child -> !child.name().equals(taken.name()) && needsNothingMore(child.decl()))
                    ? "; a placement there may go straight to the one the value is (e.g. " + parent
                      + "/<that element>/Id), not through " + taken.name()
                    : "";
            return ". " + parent + " takes one of: " + String.join(", ", names.subList(0, Math.min(CHOICES_SHOWN,
                    names.size()))) + (names.size() > CHOICES_SHOWN
                    ? " and " + (names.size() - CHOICES_SHOWN) + " more"
                    : "") + other;
        }
        return "";
    }

    // True for an element one leaf below it fills: it is a leaf, or it requires no element and no choice of its own.
    private boolean needsNothingMore(final Element decl) {
        if (decl == null || schema.isLeaf(decl)) {
            return true;
        }
        final List<Child> children = schema.children(decl);
        return children.stream().noneMatch(Child::required)
               && schema.requiredChoices().keySet().stream()
                       .noneMatch(id -> children.stream().anyMatch(child -> id.equals(child.choice())));
    }

    // A path below the action element, for an example: what follows `EventDetail/<element>/`.
    private static String below(final String path) {
        final String[] steps = path.split("/");
        // EventDetail, the element (one step, or Network/Permit's two), then the rest.
        final int skip = steps.length > 2 && "Network".equals(steps[1])
                ? 3
                : 2;
        return String.join("/", Arrays.copyOfRange(steps, Math.min(skip, steps.length), steps.length));
    }

    // The rest of a path to a leaf under an element, for an example: `/Id`, `/User/Id`.
    private String example(final Element decl) {
        if (decl == null || schema.isLeaf(decl)) {
            return "";
        }
        final List<Child> children = schema.children(decl);
        for (final String leaf : List.of("Id", "Name", "Path", "EmailAddress", "Title")) {
            if (children.stream().anyMatch(child -> child.name().equals(leaf) && schema.isLeaf(child.decl()))) {
                return "/" + leaf;
            }
        }
        // One that needs nothing more first: the first of a multi-object choice is Association, which needs objects
        // of its own, so an example through it led one level deeper each round (seen in chat 185).
        final List<Child> candidates = children.stream()
                .filter(child -> !DATA.equals(child.name()) && !"Meta".equals(child.name()))
                .toList();
        final Child first = candidates.stream()
                .filter(child -> needsNothingMore(child.decl()))
                .findFirst()
                .orElse(candidates.isEmpty()
                        ? null
                        : candidates.getFirst());
        return first == null
                ? ""
                : "/" + first.name() + example(first.decl());
    }

    // --------------------------------------------------------------------------------
    // Typing


    private int filled(final String field, final BitSet recognised) {
        final List<List<String>> values = index.values(field);
        int out = 0;
        for (int n = recognised.nextSetBit(0); n >= 0; n = recognised.nextSetBit(n + 1)) {
            if (values.get(n).stream().anyMatch(value -> !value.isBlank())) {
                out++;
            }
        }
        return out;
    }

    // How many of the fields its records hold are typed and kept as Data, as the translation will write them: those
    // its placements, who acted and its outcome read are typed; those common to every event aren't counted.
    private Typing typing(final EventType type,
                          final String detail,
                          final BitSet recognised,
                          final Map<String, String> fill,
                          final List<String> written,
                          final Set<String> sourceWritten) {
        if (recognised.cardinality() == 0) {
            return null;
        }
        final Set<String> typed = new LinkedHashSet<>();
        type.allPlacements().forEach(placement -> {
            typed.addAll(placement.inputs());
            if (placement.pair() != null) {
                // One pair of a list typed: counted as a field typed, the list's other pairs still Data.
                typed.add(placement.pair().get("list") + " (" + placement.pair().get("name") + ")");
            }
            if (placement.change() != null) {
                // One property of an old/new list typed in both snapshots: as a pair's.
                typed.add(placement.change().get("list") + " (" + placement.change().get("name") + ")");
            }
        });
        if (NullSafe.isNonBlankString(type.actor())) {
            typed.add(type.actor());
        }
        if (NullSafe.isNonBlankString(type.success()) && !"true".equalsIgnoreCase(type.success())
            && !"false".equalsIgnoreCase(type.success())) {
            typed.add(type.success());
        }
        final Set<String> codes = new HashSet<>();
        type.recognisedBy().forEach(way -> codes.addAll(VendorCode.fields(way, catalogue)));
        final Set<String> names = index.fieldNames();
        final Set<String> data = new LinkedHashSet<>();
        for (final String name : names) {
            if (typed.contains(name) && !codes.contains(name) || common.contains(name) || parent(name, names)) {
                continue;
            }
            if (filled(name, recognised) > 0) {
                data.add(name);
            }
        }
        final int typedCount = (int) typed.stream().filter(field -> !common.contains(field)).count();
        final int total = typedCount + data.size();
        final String note = data.size() >= DATA_FIELDS_NOTED && total > 0
                            && (double) data.size() / total >= DATA_SHARE_NOTED
                ? "most of its fields are kept as Data (" + data.size() + " of " + total + "; typed: "
                  + typedCount + "): place each whose meaning an element carries at the element that means it"
                : null;
        final CouldType couldType = note == null
                ? null
                : new CouldType(dataFields(data, codes, recognised), homes(detail, written, sourceWritten),
                        arraysOfObjects(type, detail, data, codes, recognised));
        return new Typing(typedCount, data.size(), fill, note, couldType);
    }

    // The fields kept as Data, but the vendor codes recognising the type (Data keeps those by design): those most
    // the event type's own first (filled in its records far more than in the source's records at large), so the
    // fields every record carries alike come last (seen in eval case 56: the list began with the twelve fields every
    // audit record holds, Id, OrganizationId, RecordType..., and the subject, folders and members the schema has
    // homes for were cut off as "... more"); each with its fill rate and a short example value.
    private List<String> dataFields(final Set<String> data, final Set<String> codes, final BitSet recognised) {
        final int records = recognised.cardinality();
        final Map<String, Integer> fills = new LinkedHashMap<>();
        final Map<String, Double> ownness = new LinkedHashMap<>();
        data.stream()
                .filter(field -> !codes.contains(field))
                .forEach(field -> {
                    final int filledHere = filled(field, recognised);
                    fills.put(field, filledHere);
                    ownness.put(field, (double) filledHere / records - everywhere(field));
                });
        final List<String> out = new ArrayList<>();
        fills.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<String, Integer> entry) -> ownness.get(entry.getKey()))
                        .reversed()
                        .thenComparing(Map.Entry.<String, Integer>comparingByValue().reversed()))
                .limit(COULD_TYPE_FIELDS)
                .forEach(entry -> {
                    final List<String> example = values(entry.getKey(), recognised, false);
                    out.add(entry.getKey() + " (" + entry.getValue() + "/" + records + (example.isEmpty()
                            ? ""
                            : ", e.g. " + ToolText.repr(shortened(example.getFirst()))) + ")");
                });
        if (fills.size() > COULD_TYPE_FIELDS) {
            out.add("… " + (fills.size() - COULD_TYPE_FIELDS) + " more");
        }
        return out;
    }

    // The fields kept as Data whose values are arrays of objects, each with its items' keys (capped): a for_each
    // group types each item where the schema repeats an element for it. Chat 184: TeamMemberAdded's members, each
    // with UPN and DisplayName, stayed Data, as nothing said a group could type them. A name/value list is listed
    // apart, with its pairs' names (2026-11-23, chat 187: each {Name, Value} pair written as a View/Object, its value
    // in Description, as the group suggestion said each item could be typed): its pairs are Data, each named by its
    // pair's name, but those whose names mean an element, read by the pair form.
    //
    // An old/new list (2026-11-23) is listed apart too, with its properties' names: written as Before and After
    // snapshots under an Update, each property Data of the object, but those whose names mean an element, read by the
    // change form for both; elsewhere one side's value read as a pair. An array of objects each holding an old/new
    // list (targets, each with its changed properties) says so, with the group writing each target in both.
    private List<String> arraysOfObjects(final EventType type,
                                         final String detail,
                                         final Set<String> data,
                                         final Set<String> codes,
                                         final BitSet recognised) {
        final boolean snapshots = schema.hasPath(EVENT_DETAIL + detail + "/After");
        final List<String> out = new ArrayList<>();
        final List<String> pairLists = new ArrayList<>();
        int found = 0;
        boolean groupSaid = false;
        for (final String field : data) {
            if (codes.contains(field)) {
                continue;
            }
            final ItemKeys items = itemKeys(field, recognised, true);
            if (items == null) {
                continue;
            }
            found++;
            if (out.size() + pairLists.size() >= ARRAYS_SHOWN) {
                continue;
            }
            final Optional<ChangeList> changes = changeList(field);
            if (changes.isPresent()) {
                final Map<String, Integer> names = new LinkedHashMap<>(NameValueLists.namesIn(heldBy(field,
                        recognised), changes.get().nameKey()));
                placedChanges(type, field).forEach(names::remove);
                if (!names.isEmpty()) {
                    out.add(changeSaid(field, changes.get(), names, snapshots, changeSides.containsKey(field)));
                }
                continue;
            }
            final Optional<NameValueList> pairs = nameValueList(field);
            if (pairs.isPresent()) {
                final Map<String, Integer> names = new LinkedHashMap<>(NameValueLists.namesIn(heldBy(field,
                        recognised), pairs.get().nameKey()));
                placedPairs(type, field).forEach(names::remove);
                if (!names.isEmpty()) {
                    pairLists.add(field + ": a name/value list; names: " + namesSaid(names));
                }
                continue;
            }
            out.add(field + ": items with " + cappedTo(items.keys(), ITEM_KEYS_SHOWN) + (groupSaid
                    ? ""
                    : " — a for_each group on an element the schema repeats types each")
                    + nestedChangesSaid(field, items.keys(), snapshots));
            groupSaid = true;
        }
        if (!pairLists.isEmpty()) {
            pairLists.set(0, pairLists.getFirst() + " — its pairs are Data, each named by its pair's name; a pair "
                             + "whose name means an element goes there by name, e.g. {path: 'Configuration/Name', "
                             + "pair: {list: '<list>', name: '<name>'}}");
            out.addAll(pairLists);
        }
        if (found > ARRAYS_SHOWN) {
            out.add("… " + (found - ARRAYS_SHOWN) + " more");
        }
        return out;
    }

    // An old/new list in words: its keys, its properties' names (capped), and how its properties are typed.
    private static String changeSaid(final String field,
                                     final ChangeList changes,
                                     final Map<String, Integer> names,
                                     final boolean snapshots,
                                     final boolean answered) {
        final String head = field + ": an old/new list (old values in " + changes.oldKey() + ", new in "
                            + changes.newKey() + (answered
                ? ", as the user said"
                : ", by the keys' names, else the items' order: give old_key and new_key where the values say "
                  + "otherwise") + "); properties: "
                            + namesSaid(names);
        final String name = names.keySet().iterator().next();
        return head + (snapshots
                ? " — written as Before and After snapshots, each property Data of the object; a property whose name "
                  + "means an element goes there in both by name, e.g. {path: 'User/Name', change: {list: '" + field
                  + "', name: '" + name + "'}}"
                : " — a property whose new value means an element is read by name, e.g. {path: '<element>', pair: "
                  + "{list: '" + field + "', name: '" + name + "', value_key: '" + changes.newKey() + "'}}");
    }

    // The old/new lists inside an array's items (targets, each with the properties changed), in words; "" for none.
    private String nestedChangesSaid(final String field, final List<String> keys, final boolean snapshots) {
        for (final String key : keys) {
            final Optional<ChangeList> changes = nestedChangeList(field, key);
            if (changes.isPresent()) {
                final String name = changes.get().names().keySet().iterator().next();
                return "; each item's " + key + " is an old/new list (properties: " + namesSaid(
                        changes.get().names()) + ")" + (snapshots
                        ? ": a for_each group over " + field + " at the object of both snapshots writes each item "
                          + "in Before and After, e.g. {path: 'User', for_each: {array: '" + field + "'}, fields: "
                          + "[{path: 'Id', field: '<its id>'}, {path: 'Name', change: {list: '" + key + "', name: '"
                          + name + "'}}]} (its other properties Data of each)"
                        : "");
            }
        }
        return "";
    }

    // The names of a list's properties the event type's placements read by the change form.
    private static List<String> placedChanges(final EventType type, final String field) {
        return type.allPlacements().stream()
                .map(Placement::change)
                .filter(change -> change != null && field.equals(change.get("list")))
                .map(change -> ToolText.str(change.get("name")))
                .toList();
    }

    // The names of a list's pairs the event type's placements read.
    private static List<String> placedPairs(final EventType type, final String field) {
        return type.allPlacements().stream()
                .map(Placement::pair)
                .filter(pair -> pair != null && field.equals(pair.get("list")))
                .map(pair -> ToolText.str(pair.get("name")))
                .toList();
    }

    private static String namesSaid(final Map<String, Integer> names) {
        return NameValueLists.said(names, NameValueLists.NAMES_SHOWN);
    }

    // --------------------------------------------------------------------------------
    // Pairs of name/value lists

    // A field told a name/value list over every record holding it, or empty.
    private Optional<NameValueList> nameValueList(final String field) {
        return nameValueLists.computeIfAbsent(field, key -> {
            final BitSet all = new BitSet();
            all.set(0, index.sample().records().size());
            return NameValueLists.detect(heldBy(key, all));
        });
    }

    // The field's values in the records given (those holding it).
    private List<Object> heldBy(final String field, final BitSet recognised) {
        final List<Object> records = index.sample().records();
        final List<Object> out = new ArrayList<>();
        for (int n = recognised.nextSetBit(0); n >= 0 && n < records.size(); n = recognised.nextSetBit(n + 1)) {
            if (records.get(n) instanceof final Map<?, ?> record && record.get(field) != null) {
                out.add(record.get(field));
            }
        }
        return out;
    }

    // Why a placement's pair can't be read: the input isn't JSON's own, the list no record has, isn't a name/value
    // list, its keys aren't the items', or no pair has the name. Null when it reads.
    private String pairProblem(final Placement placement) {
        final Map<String, Object> pair = placement.pair();
        final String list = ToolText.str(pair.get("list"));
        final String name = ToolText.str(pair.get("name"));
        final TranslationMapping reading = index.sample().reading();
        if (!"json".equals(reading.input()) || NullSafe.list(reading.jsonFields()).stream()
                .anyMatch(held -> list.equals(held) || list.startsWith(held + "."))) {
            return "pair reads a name/value list of a JSON input's records; " + list + " isn't one (read a value "
                   + "of it with an xpath)";
        }
        if (!index.untold(list) && !index.anyHas(list)) {
            return nearest(list);
        }
        final Optional<NameValueList> found = pairList(list, name, pair.get("value_key"));
        if (found.isEmpty()) {
            return list + " isn't a name/value list holding a pair named '" + name + "' (its items aren't each a "
                   + "name and its value): place its items with a for_each group, or read one with an xpath";
        }
        final NameValueList pairs = found.get();
        final Object nameKey = pair.get("name_key");
        final Object valueKey = pair.get("value_key");
        if (nameKey != null && !pairs.nameKey().equals(nameKey) || valueKey != null && !pairs.valueKey().equals(
                valueKey)) {
            return list + "'s pairs are named by " + pairs.nameKey() + " and hold their values in "
                   + pairs.valueKey() + ": give those as name_key and value_key, or leave them out";
        }
        if (!pairs.names().containsKey(name)) {
            final List<String> close = CloseMatches.closeMatches(name, pairs.names().keySet(), SHOWN,
                    CloseMatches.DEFAULT_CUTOFF);
            return list + " has no pair named '" + name + "' in the sample" + (close.isEmpty()
                    ? ""
                    : " (did you mean " + String.join(", ", close) + "?)") + "; its names: "
                   + namesSaid(pairs.names());
        }
        return null;
    }

    // The list a pair by its name reads: told by shape (an old/new list read on the side its value key names, else
    // its new values), else (a sample too small to tell) by its name.
    private Optional<NameValueList> pairList(final String list, final String name, final Object valueKey) {
        final Optional<NameValueList> told = nameValueList(list);
        if (told.isPresent()) {
            return told;
        }
        return NameValueLists.forPair(heldBy(list, everyRecord()), name, valueKey instanceof final String key
                ? key
                : null);
    }

    private BitSet everyRecord() {
        final BitSet all = new BitSet();
        all.set(0, index.sample().records().size());
        return all;
    }

    // The placement with its pair's keys, as the list's items have them (as given where they can't be read here: no
    // record holds the list, the form alone checked, with stand-in keys where none are given).
    private Placement completed(final Placement placement) {
        if (placement.snapshots()) {
            return withChangeKeys(placement);
        }
        final Map<String, Object> pair = placement.pair();
        if (pair == null) {
            return placement;
        }
        return pairList(ToolText.str(pair.get("list")), ToolText.str(pair.get("name")), pair.get("value_key"))
                .map(pairs -> placement.withPairKeys(pairs.nameKey(), pairs.valueKey()))
                .orElseGet(() -> placement.withPairKeys(Objects.toString(pair.get("name_key"), "name"),
                        Objects.toString(pair.get("value_key"), "value")));
    }

    // A for_each group over a name/value list: why it is refused, or null. Chat 187: each {Name, Value} pair written
    // as a View/Object, Name the pair's name and Description its value, a setting's value in an element meaning
    // something else.
    private String groupOfPairsProblem(final String below, final Placement placement) {
        final Object array = JsonValues.mapOrEmpty(placement.entry().get(Placement.FOR_EACH)).get("array");
        if (!(array instanceof final String field) || field.isBlank()) {
            return null;
        }
        final Optional<ChangeList> changes = changeList(field);
        if (changes.isPresent()) {
            return field + " is an old/new list (" + namesSaid(changes.get().names()) + "): each item is a "
                   + "property's change, named by its " + changes.get().nameKey() + ", not one thing " + below
                   + " means. Read the property whose name means an element by its name, "
                   + changeExample(field, changes.get(), "User/Name") + "; the other properties are Data of the "
                   + "snapshots' object, each named by its property";
        }
        return nameValueList(field)
                .map(pairs -> field + " is a name/value list (" + namesSaid(pairs.names()) + "): each item is a "
                              + "setting, named by its " + pairs.nameKey() + ", not one thing " + below + " means, "
                              + "so a group would put each setting's value in an element meaning something else. "
                              + "Read the pair whose name means an element by its name, e.g. {path: '<element>', "
                              + "pair: {list: '" + field + "', name: '" + pairs.names().keySet().iterator().next()
                              + "'}}; the other pairs are Data, each named by its pair's name")
                .orElse(null);
    }

    // The values of a placement's pair in the records given.
    private List<String> pairValues(final Placement placement, final BitSet recognised) {
        final String list = ToolText.str(placement.pair().get("list"));
        final String name = ToolText.str(placement.pair().get("name"));
        final Optional<NameValueList> found = pairList(list, name, placement.pair().get("value_key"));
        if (found.isEmpty()) {
            return List.of();
        }
        final Set<String> out = new LinkedHashSet<>();
        for (final Object value : heldBy(list, recognised)) {
            for (final Map<?, ?> item : objects(value)) {
                final String held = NameValueLists.valueOf(item, found.get().nameKey(), found.get().valueKey());
                if (name.equals(item.get(found.get().nameKey())) && held != null && !held.isBlank()
                    && !nil.contains(ToolText.strip(held)) && out.size() < VALUES_READ) {
                    out.add(held);
                }
            }
        }
        return List.copyOf(out);
    }

    // --------------------------------------------------------------------------------
    // Properties of old/new lists

    // A field told an old/new list over every record holding it, or empty.
    private Optional<ChangeList> changeList(final String field) {
        return changeLists.computeIfAbsent(List.of(field), key -> NameValueLists.changes(heldBy(field,
                everyRecord())).map(list -> list.oriented(changeSides.get(field))));
    }

    // A key of an array's items told an old/new list over every item holding it, or empty.
    private Optional<ChangeList> nestedChangeList(final String array, final String key) {
        return changeLists.computeIfAbsent(List.of(array, key), ignored -> NameValueLists.changes(
                NameValueLists.heldIn(heldBy(array, everyRecord()), key)).map(list -> list.oriented(
                changeSides.get(NameValueLists.nestedName(array, key)))));
    }

    // A change placement's example, for a message.
    private static String changeExample(final String field, final ChangeList changes, final String at) {
        return "e.g. {path: '" + at + "', change: {list: '" + field + "', name: '" + changes.names().keySet()
                .iterator().next() + "'}} (an Update's Before and After: old and new value)";
    }

    // Why a placement's changes can't be read: the input isn't JSON's own, a list no record has, isn't an old/new
    // list, its keys aren't the items', or no property has the name. Null when they read.
    private String changeProblem(final Placement placement) {
        final TranslationMapping reading = index.sample().reading();
        final String array = placement.group()
                ? ToolText.str(JsonValues.mapOrEmpty(placement.entry().get(Placement.FOR_EACH)).get("array"))
                : null;
        final List<Map<String, Object>> changes = new ArrayList<>();
        if (placement.change() != null) {
            changes.add(placement.change());
        }
        JsonValues.maps(placement.entry().get(Placement.FIELDS)).stream()
                .filter(field -> field.get(Placement.CHANGE) instanceof Map)
                .forEach(field -> changes.add(JsonValues.mapOrEmpty(field.get(Placement.CHANGE))));
        for (final Map<String, Object> change : changes) {
            final String list = ToolText.str(change.get("list"));
            final String read = array == null
                    ? list
                    : array;
            if (!"json".equals(reading.input()) || NullSafe.list(reading.jsonFields()).stream()
                    .anyMatch(held -> read.equals(held) || read.startsWith(held + "."))) {
                return "change reads an old/new list of a JSON input's records; " + list + " isn't one (read a "
                       + "value of it with an xpath)";
            }
            if (array == null && !index.untold(list) && !index.anyHas(list)) {
                return nearest(list);
            }
            final Optional<ChangeList> found = array == null
                    ? changeList(list)
                    : nestedChangeList(array, list);
            if (found.isEmpty()) {
                return (array == null
                        ? list
                        : array + "'s items' " + list) + " isn't an old/new list (its items aren't each a "
                       + "property's name with its old and new values): place its items with a for_each group, or "
                       + "read one with an xpath";
            }
            final ChangeList keys = found.get();
            final Set<String> three = Set.of(keys.nameKey(), keys.oldKey(), keys.newKey());
            for (final String key : List.of("name_key", "old_key", "new_key")) {
                if (change.get(key) != null && !three.contains(ToolText.str(change.get(key)))) {
                    return list + "'s items name each property by " + keys.nameKey() + " and hold its values in "
                           + keys.oldKey() + " and " + keys.newKey() + " (old first, by the keys' names, else the "
                           + "items' order): give those as name_key, old_key and new_key, or leave them out";
                }
            }
            final String name = change.get("name") instanceof final String named
                    ? named
                    : null;
            if (name != null && !keys.names().containsKey(name)) {
                final List<String> close = CloseMatches.closeMatches(name, keys.names().keySet(), SHOWN,
                        CloseMatches.DEFAULT_CUTOFF);
                return list + " has no property named '" + name + "' in the sample" + (close.isEmpty()
                        ? ""
                        : " (did you mean " + String.join(", ", close) + "?)") + "; its properties: "
                       + namesSaid(keys.names());
            }
        }
        return null;
    }

    // The placement with its changes' keys, as the lists' items have them: a key given kept, the rest the sample's
    // (stand-ins where the list can't be read here, the form alone checked).
    private Placement withChangeKeys(final Placement placement) {
        final String array = placement.group()
                ? ToolText.str(JsonValues.mapOrEmpty(placement.entry().get(Placement.FOR_EACH)).get("array"))
                : null;
        final Map<String, List<String>> keys = new LinkedHashMap<>();
        for (final String list : placement.changeLists()) {
            final Optional<ChangeList> found = array == null
                    ? changeList(list)
                    : nestedChangeList(array, list);
            keys.put(list, found.map(changes -> List.of(changes.nameKey(), changes.oldKey(), changes.newKey()))
                    .orElse(List.of("name", "old_value", "new_value")));
        }
        return placement.withChangeKeys(keys);
    }

    // The values of a placement's change in the records given: the property's old and new values, unwrapped.
    private List<String> changeValues(final Placement placement, final BitSet recognised) {
        final Map<String, Object> change = placement.change();
        final String list = ToolText.str(change.get("list"));
        final String name = ToolText.str(change.get("name"));
        final String nameKey = ToolText.str(change.get("name_key"));
        final Set<String> out = new LinkedHashSet<>();
        for (final Object value : heldBy(list, recognised)) {
            for (final Map<?, ?> item : objects(value)) {
                if (!name.equals(item.get(nameKey))) {
                    continue;
                }
                for (final String side : List.of("old_key", "new_key")) {
                    final String held = NameValueLists.changeText(item.get(ToolText.str(change.get(side))));
                    if (!held.isBlank() && !nil.contains(ToolText.strip(held)) && out.size() < VALUES_READ) {
                        out.add(held);
                    }
                }
            }
        }
        return List.copyOf(out);
    }

    // The share of all the source's records (every event type's) that hold a value of the field.
    private double everywhere(final String field) {
        final List<List<String>> values = index.values(field);
        if (values.isEmpty()) {
            return 0;
        }
        final long filled = values.stream()
                .filter(record -> record.stream().anyMatch(value -> !value.isBlank()))
                .count();
        return (double) filled / values.size();
    }

    private static String shortened(final String value) {
        final String one = SPACES.matcher(value.strip()).replaceAll(" ");
        return one.length() <= EXAMPLE_LENGTH
                ? one
                : one.substring(0, EXAMPLE_LENGTH) + "…";
    }

    // Where the schema has homes below the action element: the elements under each object the event type writes
    // (the element holding a leaf written, e.g. Source/Folder for Source/Folder/Id), then the action element's other
    // children (an element holding others with its own, the objects of a collection by name only).
    private List<String> homes(final String detail, final List<String> written, final Set<String> sourceWritten) {
        final String at = EVENT_DETAIL + detail;
        final Element action = schema.resolve(at).getLast().decl();
        final Set<String> objects = new LinkedHashSet<>();
        final Set<String> tops = new HashSet<>();
        for (final String path : written) {
            final String[] steps = path.split("/");
            if (path.isBlank() || DATA.equals(steps[0])) {
                continue;
            }
            tops.add(steps[0]);
            final List<Child> chain;
            try {
                chain = schema.resolve(at + "/" + path);
            } catch (final IllegalArgumentException e) {
                continue;
            }
            final int holding = schema.isLeaf(chain.getLast().decl())
                    ? steps.length - 1
                    : steps.length;
            if (holding > 0) {
                objects.add(String.join("/", Arrays.copyOfRange(steps, 0, holding)));
            }
        }
        final List<String> out = new ArrayList<>();
        for (final String object : objects) {
            if (out.size() < HOME_GROUPS) {
                out.add(object + ": " + homesIn(schema.resolve(at + "/" + object).getLast().decl()));
            }
        }
        final List<String> leaves = new ArrayList<>();
        final List<String> alternatives = new ArrayList<>();
        for (final Child child : schema.children(action)) {
            if (DATA.equals(child.name()) || "Meta".equals(child.name()) || tops.contains(child.name())) {
                continue;
            }
            if (schema.isLeaf(child.decl())) {
                leaves.add(child.name());
            } else if (schema.isObject(child.decl())) {
                alternatives.add(child.name());
            } else if (out.size() < HOME_GROUPS) {
                out.add(child.name() + ": " + homesIn(child.decl()));
            }
        }
        if (!leaves.isEmpty()) {
            out.add(detail + ": " + capped(leaves));
        }
        if (!alternatives.isEmpty()) {
            out.add("other objects: " + capped(alternatives));
        }
        out.addAll(sourceHomes(sourceWritten));
        return out;
    }

    // EventSource's elements nothing writes yet for the event type (2026-11-22; chat 183: the client application's
    // name kept as Data beside the Client/IPAddress written): each object's leaves not written, those partly
    // written first, objects whose open leaves are alike listed together (`EventSource/{Device,Server}: ...`), then
    // EventSource's own leaves. Not System: the draft's own (system_name, environment).
    private List<String> sourceHomes(final Set<String> sourceWritten) {
        final String at = ToolText.strip(Placement.EVENT_SOURCE, "/");
        final Element source;
        try {
            source = schema.resolve(at).getLast().decl();
        } catch (final IllegalArgumentException e) {
            return List.of();
        }
        // The open leaves -> the objects that have them, partly written ones first.
        final Map<List<String>, List<String>> partly = new LinkedHashMap<>();
        final Map<List<String>, List<String>> unwritten = new LinkedHashMap<>();
        final List<String> leaves = new ArrayList<>();
        for (final Child child : schema.children(source)) {
            final String path = at + "/" + child.name();
            if (DATA.equals(child.name()) || "Meta".equals(child.name()) || SYSTEM.equals(child.name())) {
                continue;
            }
            if (schema.isLeaf(child.decl())) {
                if (!sourceWritten.contains(path)) {
                    leaves.add(child.name());
                }
                continue;
            }
            final List<String> open = new ArrayList<>();
            for (final Child inner : schema.children(child.decl())) {
                if (!DATA.equals(inner.name()) && !"Meta".equals(inner.name()) && schema.isLeaf(inner.decl())
                    && !sourceWritten.contains(path + "/" + inner.name())) {
                    open.add(inner.name());
                }
            }
            final boolean touched = sourceWritten.stream().anyMatch(written -> written.startsWith(path + "/"));
            if (!open.isEmpty()) {
                (touched
                        ? partly
                        : unwritten).computeIfAbsent(open, ignored -> new ArrayList<>()).add(child.name());
            }
        }
        final List<String> out = new ArrayList<>();
        for (final Map<List<String>, List<String>> objects : List.of(partly, unwritten)) {
            objects.forEach((open, names) -> {
                if (out.size() < SOURCE_GROUPS) {
                    out.add(at + "/" + (names.size() == 1
                            ? names.getFirst()
                            : "{" + String.join(",", names) + "}") + ": " + cappedTo(open, SOURCE_LEAVES_SHOWN));
                }
            });
        }
        if (!leaves.isEmpty()) {
            out.add(at + ": " + cappedTo(leaves, SOURCE_LEAVES_SHOWN));
        }
        return out;
    }

    // An element's children: its leaves by name first, then those holding others, each with the leaf a value most
    // often goes in.
    private String homesIn(final Element decl) {
        final List<String> leaves = new ArrayList<>();
        final List<String> holding = new ArrayList<>();
        for (final Child child : schema.children(decl)) {
            if (DATA.equals(child.name()) || "Meta".equals(child.name())) {
                continue;
            }
            if (schema.isLeaf(child.decl())) {
                leaves.add(child.name());
            } else {
                holding.add(child.name() + example(child.decl()));
            }
        }
        leaves.addAll(holding);
        return capped(leaves);
    }

    private static String capped(final List<String> names) {
        return cappedTo(names, HOMES_SHOWN);
    }

    private static String cappedTo(final List<String> names, final int most) {
        return String.join(", ", names.subList(0, Math.min(most, names.size())))
               + (names.size() > most
                ? ", … " + (names.size() - most) + " more"
                : "");
    }

    // A field whose own fields the records hold too (a held document's, an object's): counted by them.
    private static boolean parent(final String name, final Set<String> names) {
        for (final String other : names) {
            if (other.length() > name.length() + 1 && other.startsWith(name)
                && ".>/[".indexOf(other.charAt(name.length())) >= 0) {
                return true;
            }
        }
        return false;
    }


    // --------------------------------------------------------------------------------


    /// An event type's placements checked.
    ///
    /// @param problems   What is refused where the proposals bring it.
    /// @param unplaced   The schema's required elements nothing writes: refuse confirmation.
    /// @param notChecked The placements whose values aren't checked here (an xpath's, a lookup's): said only.
    /// @param typing     How many fields are typed and kept as Data, or null when its records aren't in the sample.
    record Checked(List<String> problems, List<String> unplaced, List<String> notChecked, Typing typing) {

    }


    // --------------------------------------------------------------------------------


    // The keys a field's items have, each once, in the order first seen, and the first item with any.
    private record ItemKeys(List<String> keys, Map<?, ?> first) {

    }


    // An element written, by its children's names.
    private static final class Node {

        private final Map<String, Node> kids = new LinkedHashMap<>();
    }
}
