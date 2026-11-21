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
import stroom.ai.impl.tools.catalogue.CatalogueCheckResult.Typing;
import stroom.ai.impl.tools.draft.LocalCheck;
import stroom.ai.impl.tools.draft.VendorCode;
import stroom.ai.impl.tools.generation.Condition;
import stroom.ai.impl.tools.generation.FieldMapping;
import stroom.ai.impl.tools.generation.MappingError;
import stroom.ai.impl.tools.generation.MappingValidationException;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.sample.TimePatterns;
import stroom.ai.impl.tools.validation.CloseMatches;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.tools.validation.EventSchema.Child;
import stroom.util.shared.NullSafe;

import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
///   element's type, values or pattern refuse, named with the fix.
/// * **Unplaced** (refuses confirmation, never proposals): an element the schema requires of the action element (or
///   of an element placed below it) that nothing the catalogue gives writes, e.g. Update/After, Send/Destination,
///   the object View needs: derived from the schema, element by element.
/// * **Typing**: how many fields its records' events type and keep as Data; a high share kept as Data is only worth
///   a look.
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
    private static final String UNKNOWN = "Unknown";
    // The most distinct values a placement's check reads, and names when they fail.
    private static final int VALUES_READ = 2_000;
    private static final int SHOWN = 3;
    private static final int LEAVES_SHOWN = 8;
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
    private final Set<String> nil;
    private final Catalogue catalogue;
    private final List<CatalogueCheck.Drafted.Rule> rules;
    private final Function<List<Condition>, BitSet> recognises;
    private List<BitSet> byRule;

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
        this.recognises = recognises;
        final TranslationMapping reading = index.sample().reading();
        this.common = new HashSet<>(drafted.commonFields());
        String user = drafted.commonUser();
        for (final FieldMapping entry : NullSafe.list(reading.common())) {
            if (entry.field() != null) {
                this.common.add(entry.field());
                if (user == null && "EventSource/User/Id".equals(ToolText.strip(entry.path(), "/"))) {
                    user = entry.field();
                }
            }
            this.common.addAll(NullSafe.list(entry.anyOf()));
        }
        this.commonUser = user;
        this.nil = new HashSet<>(NullSafe.list(reading.nilValues()));
    }

    /// @param type       An event type.
    /// @param recognised The records it recognises.
    /// @return Its placements' problems, the schema's required elements nothing writes, and its typing.
    Checked check(final EventType type, final BitSet recognised) {
        final List<String> problems = new ArrayList<>();
        final List<String> unplaced = new ArrayList<>();
        final String detail = detail(type);
        if (detail == null || type.drop()) {
            return new Checked(problems, unplaced, null);
        }
        final String label = "event type " + type.typeId();
        final Map<String, String> fill = new LinkedHashMap<>();
        final List<String> written = new ArrayList<>();
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
            if (problems.size() == before || implied) {
                written.addAll(placement.writes(detail));
            }
            if (placement.field() != null && recognised.cardinality() > 0) {
                fill.put(placement.field(), filled(placement.field(), recognised) + " of "
                                            + recognised.cardinality());
            }
        }
        written.addAll(givenElsewhere(type, detail));
        written.addAll(drafted(detail, recognised));
        unplaced.addAll(required(label, detail, written));
        return new Checked(problems, unplaced, typing(type, recognised, fill));
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
    // user authenticating (who acted, else the common user), and the Data left over.
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
        return out;
    }

    // What the draft's own rule taking most of the type's records (each record the first rule's that takes it)
    // writes below the action element: the translation's rule starts from its fields.
    private List<String> drafted(final String detail, final BitSet recognised) {
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
        if (best < 0) {
            return List.of();
        }
        final String prefix = EVENT_DETAIL + detail + "/";
        return rules.get(best).paths().stream()
                .filter(path -> path.startsWith(prefix))
                .map(path -> path.substring(prefix.length()))
                .toList();
    }

    // --------------------------------------------------------------------------------
    // One placement


    private void placementProblems(final String said,
                                   final String detail,
                                   final Placement placement,
                                   final BitSet recognised,
                                   final List<String> problems) {
        final String below = placement.pathBelow(detail);
        if (below == null || below.isEmpty()) {
            problems.add(said + ": its path is below its action element, " + detail + " (e.g. 'Email/Subject', "
                         + "or 'EventDetail/" + detail + "/Email/Subject')");
            return;
        }
        final String form = formProblem(placement, detail);
        if (form != null) {
            problems.add(said + ": " + form);
            return;
        }
        final Map<String, Child> leaves = new LinkedHashMap<>();
        for (final String path : placement.writes(detail)) {
            final String problem = pathProblem(detail, path, leaves);
            if (problem != null) {
                problems.add(said + ": " + problem);
                return;
            }
        }
        if (placement.group() && !schema.canRepeat(EVENT_DETAIL + detail + "/" + below)) {
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
        if (!placement.group()) {
            final String repeated = repetitionProblem(detail, below, placement, recognised);
            if (repeated != null) {
                problems.add(said + ": " + repeated);
                return;
            }
        }
        valueProblems(said, detail, below, placement, recognised, leaves).ifPresent(problems::add);
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

    // A path below the action element the schema has, to a leaf, not under Data; null when it is one.
    private String pathProblem(final String detail, final String path, final Map<String, Child> leaves) {
        final String full = EVENT_DETAIL + detail + "/" + path;
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

    // Several values in a record placed where the schema has one, or without repeat: why, or null.
    private String repetitionProblem(final String detail,
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
        return schema.canRepeat(EVENT_DETAIL + detail + "/" + below)
                ? head + ", and only the first is written: give repeat: true (one " + below + " a value), or leave "
                  + "it as Data"
                : head + ", but " + below + " takes one: place them with for_each on an element that repeats (e.g. "
                  + "{path: 'Email/To', for_each: {array: '" + field + "'}, fields: [{path: 'EmailAddress', item: "
                  + "true}]}), or leave it as Data";
    }

    // --------------------------------------------------------------------------------
    // Values


    private Optional<String> valueProblems(final String said,
                                                     final String detail,
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
                } else if ("record".equals(field.get("scope"))
                           && field.get(Placement.FIELD) instanceof final String read) {
                    values = values(read, recognised, true);
                } else {
                    // A key of an item: not read here.
                    continue;
                }
                final String problem = invalid(EVENT_DETAIL + detail + "/" + path, path, leaves.get(path), field,
                        values);
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
        } else {
            // A join's parts each a value of its own: not read here.
            return Optional.empty();
        }
        final String problem = invalid(EVENT_DETAIL + detail + "/" + below, below, leaves.get(below), entry,
                values);
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
        walk(label, EVENT_DETAIL + detail, decl, root, out);
        return out;
    }

    private void walk(final String label,
                      final String at,
                      final Element decl,
                      final Node node,
                      final List<String> out) {
        if (schema.isLeaf(decl)) {
            return;
        }
        final List<Child> allowed = schema.children(decl);
        for (final Child child : allowed) {
            if (child.required() && !node.kids.containsKey(child.name())) {
                out.add(label + ": " + at + "/" + child.name() + " is required by the schema, and nothing the "
                        + "catalogue gives is written there: give a placement (e.g. {path: '"
                        + below(at + "/" + child.name()) + example(child.decl()) + "', field: <the field that "
                        + "holds it>}) from what the records hold");
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
                        .orElse(null)) + "', field: <the field that holds it>})");
            }
        }
        for (final Child child : allowed) {
            final Node kid = node.kids.get(child.name());
            if (kid != null && !DATA.equals(child.name())) {
                walk(label, at + "/" + child.name(), child.decl(), kid, out);
            }
        }
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
        final Child first = children.stream()
                .filter(child -> !DATA.equals(child.name()) && !"Meta".equals(child.name()))
                .findFirst()
                .orElse(null);
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
    private Typing typing(final EventType type, final BitSet recognised, final Map<String, String> fill) {
        if (recognised.cardinality() == 0) {
            return null;
        }
        final Set<String> typed = new LinkedHashSet<>();
        type.allPlacements().forEach(placement -> typed.addAll(placement.inputs()));
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
                  + typedCount + "): placements would type those the schema has homes for"
                : null;
        return new Typing(typedCount, data.size(), fill, note);
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
    /// @param problems What is refused where the proposals bring it.
    /// @param unplaced The schema's required elements nothing writes: refuse confirmation.
    /// @param typing   How many fields are typed and kept as Data, or null when its records aren't in the sample.
    record Checked(List<String> problems, List<String> unplaced, Typing typing) {

    }


    // --------------------------------------------------------------------------------


    // An element written, by its children's names.
    private static final class Node {

        private final Map<String, Node> kids = new LinkedHashMap<>();
    }
}
