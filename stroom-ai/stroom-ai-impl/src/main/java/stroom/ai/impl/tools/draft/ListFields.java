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

package stroom.ai.impl.tools.draft;

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.sample.NameValueLists;
import stroom.ai.impl.tools.sample.NameValueLists.ChangeList;
import stroom.ai.impl.tools.sample.NameValueLists.NameValueList;
import stroom.ai.impl.tools.sample.SampleProfiler;
import stroom.ai.impl.tools.sample.ToolJson;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/// Fields whose values are lists, drafted as repeated groups (one level deep): a field whose text is often a list of
/// values of one kind (addresses, ids, hosts...) parted by `,` or `;` is split, each piece a Data of its own; a JSON
/// field holding an array of objects gives a Data per object, holding a Data per key of the objects. Asked for by the
/// user: a mail gateway's recipients were drafted as one Data holding the whole comma list.
///
/// An old/new property-change list (2026-11-23, [NameValueLists#changes(java.util.Collection)]) under an Update is
/// drafted as its Before and After snapshots: the object changed (the one the rule's other entries write in a snapshot,
/// else `Object`) in each, every property a Data of the side's value; an array whose items each hold one (targets, each
/// with the properties changed) as a group over the items in each snapshot, an object per item. Elsewhere such a list
/// is an array of objects as any other.
///
/// Format-level knowledge only: what a list looks like, never a source's field names. A value is never run through a
/// regex whole: it is split by plain string search, and only its first [#MAX_PIECES] pieces, each at most
/// [#MAX_PIECE_LENGTH] long, are tested, each with short anchored patterns, so a list of thousands of items in a line
/// of a few hundred KB costs no more than a short one.
final class ListFields {

    /// How the draft's note on old/new lists written per property in snapshots of entries it didn't draft starts.
    static final String SNAPSHOTS_WRITTEN = "Old/new lists written as a Data per property in each Update snapshot "
                                            + "(the change form's, whatever placed them): ";

    /// The most pieces of a value examined.
    static final int MAX_PIECES = 50;
    /// The most values of a field examined.
    static final int MAX_VALUES = 200;
    /// The longest piece that can be a value of a kind: anything longer is text, not a list item.
    static final int MAX_PIECE_LENGTH = 255;
    /// The most keys of an array's objects drafted as Data.
    static final int MAX_KEYS = 50;

    // The separators a list's items are parted by, in the order tried.
    private static final List<String> SEPARATORS = List.of(",", ";");
    // The share of a field's values that must be a list, or one value, of the field's kind.
    private static final double CONSISTENT_SHARE = 0.9;
    // The share of a field's values that must be lists of two or more.
    private static final double LIST_SHARE = 0.25;
    // A weak kind's lists (names, numbers: what prose and numbers with thousands separators look like too) must show
    // this many items in one value at least.
    private static final int WEAK_KIND_PIECES = 3;
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+'-]{1,64}@[A-Za-z0-9-]{1,63}"
                                                         + "(?:\\.[A-Za-z0-9-]{1,63})+");
    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(?:\\.\\d{1,3}){3}");
    private static final Pattern NUMBER = Pattern.compile("-?\\d{1,20}");
    // A host name, an account, an id: one token holding a letter.
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.:/\\\\$#-]{1,128}");
    private static final Pattern LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern THOUSANDS_GROUP = Pattern.compile("\\d{3}");
    private static final Pattern LEADING_GROUP = Pattern.compile("-?\\d{1,3}");
    private static final Pattern ITEM_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_-]*");
    private static final Pattern NAME_PARTS = Pattern.compile("[./]");
    private static final String PATH = "path";
    private static final String FIELD = "field";
    private static final String DATA_NAME = "data_name";
    private static final String FIELDS = "fields";
    private static final String DATA = "Data";
    private static final String DATA_SUFFIX = "/Data";
    private static final String CHANGE = "change";
    // An Update's Data, whose old/new lists are drafted as its snapshots.
    private static final String UPDATE = "EventDetail/Update/";
    // The object a snapshot holds where nothing better is known.
    private static final String OBJECT = "Object";
    // The most distinct values a key saying what each target is may have.
    private static final int MAX_KINDS = 5;
    private static final Pattern WORD = Pattern.compile("\\p{L}+");
    private static final Set<String> PLAIN_DATA_KEYS = Set.of(PATH, DATA_NAME, FIELD);

    private ListFields() {
        // Static only.
    }

    /// Turns each plain Data entry (path ending Data, data_name, field and nothing else) that reads a list field into
    /// a Data group over its items.
    ///
    /// @param mapping The draft's mapping, changed in place.
    /// @param values  Each field's non-empty values in the sample.
    /// @param json    True if the input is JSON, whose fields may hold arrays of objects.
    /// @return The notes to add to the draft: one for the split fields, one for the arrays.
    static List<String> propose(final Map<String, Object> mapping,
                                final Map<String, List<String>> values,
                                final boolean json) {
        return propose(mapping, values, json, Map.of(), new ArrayList<>());
    }

    /// As [#propose(Map, Map, boolean)], with the user's answers of which key of each old/new list holds the value
    /// before the change (2026-11-23), and the old/new lists found said for the draft's tool to ask of.
    ///
    /// @param mapping The draft's mapping, changed in place.
    /// @param values  Each field's non-empty values in the sample.
    /// @param json    True if the input is JSON, whose fields may hold arrays of objects.
    /// @param sides   The user's answers, by the list ([NameValueLists#nestedName] for a list inside an array's
    ///                items): the key holding the value before; a list with none taken in its items' key order.
    /// @param found   Filled with each old/new list found ([#changeListFound]).
    /// @return The notes to add to the draft.
    static List<String> propose(final Map<String, Object> mapping,
                                final Map<String, List<String>> values,
                                final boolean json,
                                final Map<String, String> sides,
                                final List<Map<String, Object>> found) {
        final Map<String, Split> splits = new LinkedHashMap<>();
        final Map<String, List<String>> arrays = new LinkedHashMap<>();
        final Map<String, NameValueList> pairLists = new LinkedHashMap<>();
        final Map<String, ChangeList> changeLists = new LinkedHashMap<>();
        // The arrays of objects whose items each hold an old/new list: the key holding it, and the list.
        final Map<String, Map.Entry<String, ChangeList>> targetLists = new LinkedHashMap<>();
        // The entries drafted as snapshots: each Before entry with the After entry that follows it in its rule.
        final Map<Map<String, Object>, Map<String, Object>> afters = new IdentityHashMap<>();
        final Map<Map<String, Object>, String> objects = objectsOfEntries(mapping);
        // The arrays drafted as Data groups (not as snapshots).
        final Set<String> grouped = new LinkedHashSet<>();
        final Set<String> repeated = new LinkedHashSet<>();
        // Each field examined once, though many rules carry its Data.
        final Set<String> examined = new HashSet<>();
        for (final Map<String, Object> entry : entries(mapping)) {
            if (!plainData(entry)) {
                continue;
            }
            final String field = (String) entry.get(FIELD);
            final List<String> held = values.getOrDefault(field, List.of());
            if (examined.add(field)) {
                final Optional<NameValueList> pairs = json
                        ? NameValueLists.detect(held)
                        : Optional.empty();
                final List<String> keys = json && pairs.isEmpty()
                        ? arrayKeys(held)
                        : List.of();
                final Optional<ChangeList> changes = json && pairs.isEmpty()
                        ? NameValueLists.changes(held).map(list -> list.oriented(sides.get(field)))
                        : Optional.empty();
                if (changes.isPresent()) {
                    changeLists.put(field, changes.get());
                    found.add(changeListFound(field, changes.get(), held, sides.containsKey(field)));
                }
                if (pairs.isPresent()) {
                    pairLists.put(field, pairs.get());
                } else if (!keys.isEmpty()) {
                    arrays.put(field, keys);
                    nestedChanges(held).ifPresent(nested -> {
                        final String name = NameValueLists.nestedName(field, nested.getKey());
                        final ChangeList sided = nested.getValue().oriented(sides.get(name));
                        targetLists.put(field, Map.entry(nested.getKey(), sided));
                        found.add(changeListFound(name, sided, NameValueLists.heldIn(held, nested.getKey()),
                                sides.containsKey(name)));
                    });
                } else if (json && valueArrays(held)) {
                    repeated.add(field);
                } else {
                    final Split split = splitOf(held);
                    if (split != null) {
                        splits.put(field, split);
                    }
                }
            }
            final String path = (String) entry.get(PATH);
            final boolean update = path.startsWith(UPDATE) && objects.containsKey(entry);
            if (update && changeLists.containsKey(field)) {
                final List<Map<String, Object>> both = snapshots(field, changeLists.get(field), objects.get(entry));
                replace(entry, both.get(0));
                afters.put(entry, both.get(1));
            } else if (update && targetLists.containsKey(field)) {
                final List<Map<String, Object>> both = targetSnapshots(field, arrays.get(field),
                        targetLists.get(field), objects.get(entry));
                replace(entry, both.get(0));
                afters.put(entry, both.get(1));
            } else if (pairLists.containsKey(field)) {
                replace(entry, dataPerPair((String) entry.get(PATH), field, pairLists.get(field)));
            } else if (arrays.containsKey(field)) {
                replace(entry, arrayGroup((String) entry.get(PATH), field, arrays.get(field)));
                grouped.add(field);
            } else if (repeated.contains(field)) {
                // Stroom's JSON as XML runs an array's values together as its text: a Data per value.
                entry.put("repeat", true);
            } else if (splits.containsKey(field)) {
                replace(entry, splitGroup((String) entry.get(PATH), field, splits.get(field).separator()));
            }
        }
        insertAfters(mapping, afters);
        // The old/new lists of the groups and entries the draft didn't write itself (a catalogue's placements: seen
        // in eval case 76, targets placed as a group in After alone, no property written): a Data per property in
        // each snapshot whatever placed them (2026-11-23).
        final List<String> written = json
                ? SnapshotChanges.apply(mapping, field -> values.getOrDefault(field, List.of()), sides)
                : List.of();
        final List<String> notes = new ArrayList<>();
        if (!written.isEmpty()) {
            notes.add(SNAPSHOTS_WRITTEN + String.join("; ", written) + ".");
        }
        if (!changeLists.isEmpty() || !targetLists.isEmpty()) {
            notes.add(changeNote(changeLists, targetLists, values));
        }
        if (!splits.isEmpty()) {
            notes.add(splitNote(splits));
        }
        if (!repeated.isEmpty()) {
            notes.add(String.join(", ", repeated.stream().map(ToolText::repr).toList()) + (repeated.size() == 1
                    ? " holds a list"
                    : " hold lists") + " of values (a JSON array): a Data per value (repeat), not one Data of the "
                      + "values run together.");
        }
        if (!pairLists.isEmpty()) {
            notes.add(pairNote(pairLists));
        }
        arrays.keySet().retainAll(grouped);
        if (!arrays.isEmpty()) {
            notes.add(arrayNote(arrays));
            // Lists inside the objects are left out of the groups: said, so they aren't lost unsaid.
            arrays.keySet().forEach(field -> {
                // An old/new list inside the items is said with the old/new lists (a group over it is refused).
                final List<String> inner = nestedListKeys(values.getOrDefault(field, List.of())).stream()
                        .filter(key -> !targetLists.containsKey(field) || !key.equals(targetLists.get(field).getKey()))
                        .toList();
                if (!inner.isEmpty()) {
                    notes.add("Not drafted: " + String.join(", ", inner.stream().map(key -> ToolText.repr(key))
                            .toList()) + " in each object of " + ToolText.repr(field) + " (a list inside each "
                              + "object, whose values would run together read whole). If wanted, map each as a "
                              + "group inside this one, e.g. {\"path\": \"Data\", \"data_name\": \"" + inner.getFirst()
                              + "\", \"for_each\": {\"array\": \"" + inner.getFirst() + "\"}, \"as\": \"...\", "
                              + "\"fields\": [...]}.");
                }
            });
        }
        return notes;
    }

    /// The keys of the objects in a field's JSON arrays whose values are lists (left out of the array's group).
    ///
    /// @param values The field's values, as JSON text.
    /// @return The keys holding lists, dotted for an object inside an item, in the order first seen, at most
    /// [#MAX_KEYS].
    static List<String> nestedListKeys(final List<String> values) {
        final Set<String> keys = new LinkedHashSet<>();
        int examined = 0;
        for (final String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if (examined++ >= MAX_VALUES) {
                break;
            }
            final Object parsed;
            try {
                parsed = ToolJson.loads(value.strip());
            } catch (final ToolJson.JsonDecodeException e) {
                continue;
            }
            if (parsed instanceof final List<?> items) {
                items.stream().limit(MAX_PIECES).forEach(item -> SampleProfiler.flatten(item).forEach((key, inner) -> {
                    if (inner instanceof List) {
                        keys.add(key);
                    }
                }));
            }
        }
        return keys.stream().limit(MAX_KEYS).toList();
    }

    /// Whether a JSON field's values are arrays of plain values (strings, numbers, booleans), not objects nor lists:
    /// every one examined an array, and at least one holding a value. Read whole, Stroom's JSON as XML runs such an
    /// array's values together as its text.
    ///
    /// @param values The field's non-empty values, as JSON text.
    /// @return True if they are.
    static boolean valueArrays(final List<String> values) {
        boolean anyItems = false;
        int examined = 0;
        for (final String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if (examined++ >= MAX_VALUES) {
                break;
            }
            final String stripped = value.strip();
            if (!stripped.startsWith("[") || !stripped.endsWith("]")) {
                return false;
            }
            final Object parsed;
            try {
                parsed = ToolJson.loads(stripped);
            } catch (final ToolJson.JsonDecodeException e) {
                return false;
            }
            if (!(parsed instanceof final List<?> items)) {
                return false;
            }
            for (final Object item : items) {
                if (item instanceof Map || item instanceof List) {
                    return false;
                }
                anyItems = true;
            }
        }
        return anyItems;
    }

    /// Whether a field's values are lists: most of them (nine in ten) a list or one value of one kind, a quarter or
    /// more lists of two or more, with one separator throughout.
    ///
    /// @param values The field's non-empty values.
    /// @return The separator and the items' kind, or null if the values aren't lists.
    static Split splitOf(final List<String> values) {
        final List<String> examined = values.stream()
                .filter(value -> value != null && !value.isBlank())
                .limit(MAX_VALUES)
                .toList();
        if (examined.isEmpty()) {
            return null;
        }
        Split best = null;
        int bestLists = 0;
        for (final String separator : SEPARATORS) {
            final Map<Kind, Integer> kinds = new LinkedHashMap<>();
            final Map<Kind, Integer> lists = new LinkedHashMap<>();
            final Map<Kind, Integer> mostPieces = new LinkedHashMap<>();
            for (final String value : examined) {
                final List<String> pieces = pieces(value, separator);
                Kind kind = valueKind(pieces, separator);
                if (kind == Kind.NAME && pieces.stream().anyMatch(ListFields::word) && spaced(value, separator)) {
                    // Words parted by ", " are as likely prose ("Hi, there, all").
                    kind = null;
                }
                if (kind == null) {
                    continue;
                }
                kinds.merge(kind, 1, Integer::sum);
                if (pieces.size() >= 2) {
                    lists.merge(kind, 1, Integer::sum);
                }
                mostPieces.merge(kind, pieces.size(), Math::max);
            }
            if (kinds.isEmpty()) {
                continue;
            }
            final Kind kind = JsonValues.mostCommon(kinds).getFirst();
            final int listed = lists.getOrDefault(kind, 0);
            final boolean weakEnough = !kind.weak() || mostPieces.get(kind) >= WEAK_KIND_PIECES;
            if (kinds.get(kind) >= CONSISTENT_SHARE * examined.size() && listed >= 1
                && listed >= LIST_SHARE * examined.size() && weakEnough && listed > bestLists) {
                best = new Split(separator, kind);
                bestLists = listed;
            }
        }
        return best;
    }

    /// A value's first [#MAX_PIECES] pieces, parted by the separator (plain string search), each trimmed, empty ones
    /// dropped.
    ///
    /// @param value     The value.
    /// @param separator The separator.
    /// @return Its pieces, or null if one is longer than [#MAX_PIECE_LENGTH] (text, not a list).
    static List<String> pieces(final String value, final String separator) {
        final List<String> out = new ArrayList<>();
        int from = 0;
        while (from <= value.length() && out.size() < MAX_PIECES) {
            final int at = value.indexOf(separator, from);
            final int end = at < 0
                    ? value.length()
                    : at;
            if (end - from > 2 * MAX_PIECE_LENGTH) {
                // Not copied: too long to be an item even with space round it.
                return null;
            }
            final String piece = value.substring(from, end).strip();
            if (piece.length() > MAX_PIECE_LENGTH) {
                return null;
            }
            if (!piece.isEmpty()) {
                out.add(piece);
            }
            if (at < 0) {
                break;
            }
            from = at + separator.length();
        }
        return out;
    }

    // The one kind of all of a value's pieces, or null (no pieces, a piece of no kind, pieces of two kinds, or a
    // number written with thousands separators).
    private static Kind valueKind(final List<String> pieces, final String separator) {
        if (pieces == null || pieces.isEmpty()) {
            return null;
        }
        Kind kind = null;
        for (final String piece : pieces) {
            final Kind own = kindOf(piece);
            if (own == null || (kind != null && own != kind)) {
                return null;
            }
            kind = own;
        }
        if (kind == Kind.NUMBER && ",".equals(separator) && pieces.size() >= 2 && thousands(pieces)) {
            return null;
        }
        return kind;
    }

    /// @param piece A trimmed piece, at most [#MAX_PIECE_LENGTH] long.
    /// @return Its kind, or null if it is none (prose, a pair, anything with spaces...).
    static Kind kindOf(final String piece) {
        if (EMAIL.matcher(piece).matches()) {
            return Kind.EMAIL;
        }
        if (IPV4.matcher(piece).matches()) {
            return Kind.IP_ADDRESS;
        }
        if (NUMBER.matcher(piece).matches()) {
            return Kind.NUMBER;
        }
        if (NAME.matcher(piece).matches() && LETTER.matcher(piece).find()) {
            return Kind.NAME;
        }
        return null;
    }

    // Whether a piece is a plain word, letters only.
    private static boolean word(final String piece) {
        return piece.chars().allMatch(Character::isLetter);
    }

    // Whether a space follows the separator anywhere in the value's first pieces: only the separators are looked
    // for, never the whole value.
    private static boolean spaced(final String value, final String separator) {
        int from = 0;
        for (int i = 0; i < MAX_PIECES; i++) {
            final int at = value.indexOf(separator, from);
            if (at < 0) {
                return false;
            }
            from = at + separator.length();
            if (from < value.length() && Character.isWhitespace(value.charAt(from))) {
                return true;
            }
        }
        return false;
    }

    // Whether the pieces are one number with thousands separators: 1,234,567.
    private static boolean thousands(final List<String> pieces) {
        if (!LEADING_GROUP.matcher(pieces.getFirst()).matches()) {
            return false;
        }
        return pieces.subList(1, pieces.size()).stream().allMatch(piece -> THOUSANDS_GROUP.matcher(piece).matches());
    }

    /// The keys of the objects a field's JSON arrays hold, if its values are arrays of objects: every value (empty
    /// arrays aside) an array whose items are all objects, and one at least not empty.
    ///
    /// @param values The field's non-empty values, as JSON text.
    /// @return The keys (dotted for an object inside an item), in the order first seen, at most [#MAX_KEYS]; empty
    /// if the values aren't arrays of objects.
    static List<String> arrayKeys(final List<String> values) {
        final Set<String> keys = new LinkedHashSet<>();
        boolean anyItems = false;
        int examined = 0;
        for (final String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if (examined++ >= MAX_VALUES) {
                break;
            }
            final String stripped = value.strip();
            if (!stripped.startsWith("[") || !stripped.endsWith("]")) {
                return List.of();
            }
            final Object parsed;
            try {
                parsed = ToolJson.loads(stripped);
            } catch (final ToolJson.JsonDecodeException e) {
                return List.of();
            }
            if (!(parsed instanceof final List<?> items)) {
                return List.of();
            }
            for (final Object item : items) {
                if (!(item instanceof Map)) {
                    return List.of();
                }
            }
            for (final Object item : items.subList(0, Math.min(items.size(), MAX_PIECES))) {
                anyItems = true;
                // A key holding an array (a list inside an item) is left out: read whole, its values would run
                // together as one text. It is a group of its own, inside this one, if wanted.
                SampleProfiler.flatten(item).forEach((key, inner) -> {
                    if (!(inner instanceof List)) {
                        keys.add(key);
                    }
                });
            }
        }
        if (!anyItems) {
            return List.of();
        }
        return keys.stream().limit(MAX_KEYS).toList();
    }

    /// The name of one item of a list field: the field's last part made singular (`recipients` -> `recipient`,
    /// `addresses` -> `address`, `entries` -> `entry`), or `item` when it can't be (`status`, `class`, `to`).
    ///
    /// @param field The list field's name.
    /// @return The item's name, a plain name (letters, digits, _ or -).
    static String itemName(final String field) {
        final String[] parts = NAME_PARTS.split(field, -1);
        final String last = parts[parts.length - 1];
        final String lower = last.toLowerCase(Locale.ROOT);
        final String singular;
        if (lower.endsWith("sses") || lower.endsWith("xes")) {
            singular = last.substring(0, last.length() - 2);
        } else if (lower.endsWith("ies") && last.length() > 3) {
            singular = last.substring(0, last.length() - 3) + "y";
        } else if (lower.endsWith("s") && !lower.endsWith("ss") && !lower.endsWith("us") && last.length() > 1) {
            singular = last.substring(0, last.length() - 1);
        } else {
            singular = null;
        }
        return singular != null && ITEM_NAME.matcher(singular).matches() && !"item".equals(singular)
                ? singular
                : "item";
    }

    /// A Data group splitting a field: a Data of the field's name per item, holding the item as a Data of the item's
    /// name.
    ///
    /// @param path      The Data entry's path (ending Data).
    /// @param field     The field.
    /// @param separator The separator.
    /// @return The group entry.
    static Map<String, Object> splitGroup(final String path, final String field, final String separator) {
        final String item = itemName(field);
        final Map<String, Object> forEach = new LinkedHashMap<>();
        forEach.put("split", field);
        forEach.put("on", separator);
        final Map<String, Object> itemData = new LinkedHashMap<>();
        itemData.put(PATH, DATA);
        itemData.put(DATA_NAME, item);
        itemData.put("item", true);
        return group(path, field, forEach, item, new ArrayList<>(List.of(itemData)));
    }

    /// A Data group over a JSON array of objects: a Data of the field's name per object, holding a Data per key.
    ///
    /// @param path  The Data entry's path (ending Data).
    /// @param field The field holding the array.
    /// @param keys  The objects' keys.
    /// @return The group entry.
    static Map<String, Object> arrayGroup(final String path, final String field, final List<String> keys) {
        final Map<String, Object> forEach = new LinkedHashMap<>();
        forEach.put("array", field);
        final List<Map<String, Object>> fields = new ArrayList<>();
        for (final String key : keys) {
            final Map<String, Object> data = new LinkedHashMap<>();
            data.put(PATH, DATA);
            data.put(DATA_NAME, key);
            data.put(FIELD, key);
            fields.add(data);
        }
        return group(path, field, forEach, itemName(field), fields);
    }

    /// A name/value list's Data per pair: each pair a Data named by its name, holding its value
    /// ([stroom.ai.impl.tools.generation.ListPair]).
    ///
    /// @param path  The Data entry's path (ending Data).
    /// @param field The list field.
    /// @param pairs The list's keys.
    /// @return The entry.
    static Map<String, Object> dataPerPair(final String path, final String field, final NameValueList pairs) {
        final Map<String, Object> pair = new LinkedHashMap<>();
        pair.put("list", field);
        pair.put("name_key", pairs.nameKey());
        pair.put("value_key", pairs.valueKey());
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put(PATH, path);
        entry.put("pair", pair);
        return entry;
    }

    /// An old/new list's Before and After snapshots: the object in each, holding a Data per property of the side's
    /// value ([stroom.ai.impl.tools.generation.ListChange]).
    ///
    /// @param field   The list field.
    /// @param changes The list's keys.
    /// @param object  The object changed (`User`, `Group`; `Object` where nothing better is known).
    /// @return The Before entry, then the After entry.
    static List<Map<String, Object>> snapshots(final String field, final ChangeList changes, final String object) {
        final List<Map<String, Object>> out = new ArrayList<>();
        for (final String side : List.of("Before", "After")) {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put(PATH, UPDATE + side + "/" + object + "/" + DATA);
            entry.put(CHANGE, changeKeys(field, changes));
            out.add(entry);
        }
        return out;
    }

    /// An array of targets' Before and After snapshots, each item holding an old/new list: a group over the items in
    /// each, an object per item, holding a Data per key of the item's and a Data per property of the side's value.
    ///
    /// @param field   The array field.
    /// @param keys    The items' keys (those holding lists left out).
    /// @param held    The items' key holding the old/new list, with the list's keys.
    /// @param object  The object each item is (`Object` where nothing better is known).
    /// @return The Before group, then the After group.
    static List<Map<String, Object>> targetSnapshots(final String field,
                                                     final List<String> keys,
                                                     final Map.Entry<String, ChangeList> held,
                                                     final String object) {
        final List<Map<String, Object>> out = new ArrayList<>();
        for (final String side : List.of("Before", "After")) {
            final List<Map<String, Object>> fields = new ArrayList<>();
            for (final String key : keys) {
                final Map<String, Object> data = new LinkedHashMap<>();
                data.put(PATH, DATA);
                data.put(DATA_NAME, key);
                data.put(FIELD, key);
                fields.add(data);
            }
            final Map<String, Object> perProperty = new LinkedHashMap<>();
            perProperty.put(PATH, DATA);
            perProperty.put(CHANGE, changeKeys(held.getKey(), held.getValue()));
            fields.add(perProperty);
            final Map<String, Object> forEach = new LinkedHashMap<>();
            forEach.put("array", field);
            final Map<String, Object> group = new LinkedHashMap<>();
            group.put(PATH, UPDATE + side + "/" + object);
            group.put("for_each", forEach);
            group.put("as", itemName(field));
            group.put(FIELDS, fields);
            out.add(group);
        }
        return out;
    }

    /// An old/new list found, for the draft's tool to ask the user which key holds the value before the change.
    ///
    /// @param list     The list's name (a field, or [NameValueLists#nestedName]).
    /// @param changes  Its keys, as drafted.
    /// @param values   Its values.
    /// @param answered True if the user's answer set its old key, false if the items' key order did.
    /// @return `list`, `name_key`, `old_key`, `new_key`, `by` (`user` or `order`) and `example` (an item holding
    /// both values, where one does).
    static Map<String, Object> changeListFound(final String list,
                                               final ChangeList changes,
                                               final List<?> values,
                                               final boolean answered) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("list", list);
        out.put("name_key", changes.nameKey());
        out.put("old_key", changes.oldKey());
        out.put("new_key", changes.newKey());
        out.put("by", answered
                ? "user"
                : "order");
        final Map<String, Object> example = NameValueLists.example(values, changes);
        if (!example.isEmpty()) {
            out.put("example", example);
        }
        return out;
    }

    private static Map<String, Object> changeKeys(final String list, final ChangeList changes) {
        final Map<String, Object> change = new LinkedHashMap<>();
        change.put("list", list);
        change.put("name_key", changes.nameKey());
        change.put("old_key", changes.oldKey());
        change.put("new_key", changes.newKey());
        return change;
    }

    // The first key of an array's items holding an old/new list (targets, each with its properties changed).
    private static Optional<Map.Entry<String, ChangeList>> nestedChanges(final List<String> values) {
        for (final String key : nestedListKeys(values)) {
            if (key.contains(".")) {
                continue;
            }
            final Optional<ChangeList> found = NameValueLists.changes(NameValueLists.heldIn(values, key));
            if (found.isPresent()) {
                return Optional.of(Map.entry(key, found.get()));
            }
        }
        return Optional.empty();
    }

    // The object each Update rule's snapshots hold, by each of its Data entries reading one field: the one another
    // of its entries writes in a snapshot (an event type's affected user, EventDetail/Update/After/User/Id), else
    // Object. Rules of other action elements have none.
    private static Map<Map<String, Object>, String> objectsOfEntries(final Map<String, Object> mapping) {
        final Map<Map<String, Object>, String> out = new IdentityHashMap<>();
        for (final Map<String, Object> rule : JsonValues.maps(mapping.get("events"))) {
            final List<Map<String, Object>> fields = JsonValues.maps(rule.get(FIELDS));
            String object = null;
            for (final Map<String, Object> entry : fields) {
                final List<String> steps = ToolText.split(ToolText.str(entry.get(PATH)), "/");
                if (object == null && steps.size() >= 4 && "EventDetail".equals(steps.get(0))
                    && "Update".equals(steps.get(1))
                    && (steps.get(2).equals("Before") || steps.get(2).equals("After")) && !DATA.equals(steps.get(3))) {
                    object = steps.get(3);
                }
            }
            for (final Map<String, Object> entry : fields) {
                if (ToolText.str(entry.get(PATH)).startsWith(UPDATE)) {
                    out.put(entry, object == null
                            ? OBJECT
                            : object);
                }
            }
        }
        return out;
    }

    // Each snapshot's After entry put in its rule's fields after its Before entry.
    private static void insertAfters(final Map<String, Object> mapping,
                                     final Map<Map<String, Object>, Map<String, Object>> afters) {
        if (afters.isEmpty()) {
            return;
        }
        for (final Map<String, Object> rule : JsonValues.maps(mapping.get("events"))) {
            final List<Map<String, Object>> fields = new ArrayList<>();
            for (final Map<String, Object> entry : JsonValues.maps(rule.get(FIELDS))) {
                fields.add(entry);
                if (afters.containsKey(entry)) {
                    fields.add(afters.get(entry));
                }
            }
            rule.put(FIELDS, fields);
        }
    }

    private static Map<String, Object> group(final String path,
                                             final String field,
                                             final Map<String, Object> forEach,
                                             final String as,
                                             final List<Map<String, Object>> fields) {
        final Map<String, Object> group = new LinkedHashMap<>();
        group.put(PATH, path);
        group.put(DATA_NAME, field);
        group.put("for_each", forEach);
        group.put("as", as);
        group.put(FIELDS, fields);
        return group;
    }

    // Every field entry of the mapping: common's and each rule's.
    private static List<Map<String, Object>> entries(final Map<String, Object> mapping) {
        final List<Map<String, Object>> entries = new ArrayList<>(JsonValues.maps(mapping.get("common")));
        for (final Map<String, Object> rule : JsonValues.maps(mapping.get("events"))) {
            entries.addAll(JsonValues.maps(rule.get(FIELDS)));
        }
        return entries;
    }

    // A Data entry reading one field and nothing more: what the draft writes for a field it has no home for.
    private static boolean plainData(final Map<String, Object> entry) {
        return PLAIN_DATA_KEYS.equals(entry.keySet())
               && entry.get(PATH) instanceof final String path && (path.endsWith(DATA_SUFFIX) || DATA.equals(path))
               && entry.get(FIELD) instanceof String
               && entry.get(DATA_NAME) instanceof String;
    }

    // The entry made the group, in place: the same map may be in the mapping's lists more than once.
    private static void replace(final Map<String, Object> entry, final Map<String, Object> group) {
        entry.clear();
        entry.putAll(group);
    }

    private static String splitNote(final Map<String, Split> splits) {
        final List<String> said = new ArrayList<>();
        splits.forEach((field, split) -> said.add(ToolText.repr(field) + " (" + split.kind().said() + ", split on '"
                                                  + split.separator() + "')"));
        final boolean one = splits.size() == 1;
        return String.join(", ", said) + " " + (one
                ? "holds a list"
                : "hold lists") + " in many records: drafted as a Data group (for_each split), a Data per item, "
               + "not one Data holding the whole list. If the items are each one thing the schema has an element for, "
               + "move the group there: e.g. addresses as EventDetail/Send/Destination/User, {\"path\": "
               + "\"EventDetail/Send/Destination/User\", \"for_each\": {\"split\": \"<field>\", \"on\": \",\"}, "
               + "\"as\": \"recipient\", \"fields\": [{\"path\": \"Id\", \"item\": true, \"transform\": "
               + "\"strip_domain\"}, {\"path\": \"Domain\", \"item\": true, \"transform\": \"domain\"}]}; "
               + "hosts as Device elements.";
    }

    // The name/value lists, each with its names (capped): the pairs whose names mean an element are the agent's to
    // place, by name.
    private static String pairNote(final Map<String, NameValueList> lists) {
        final List<String> said = new ArrayList<>();
        lists.forEach((field, pairs) -> said.add(ToolText.repr(field) + " (names " + NameValueLists.said(
                pairs.names(), NameValueLists.NAMES_SHOWN) + ")"));
        final boolean one = lists.size() == 1;
        final Map.Entry<String, NameValueList> example = lists.entrySet().iterator().next();
        return String.join(", ", said) + " " + (one
                ? "is a name/value list"
                : "are name/value lists") + ": drafted as a Data per pair, each named by its pair's name. A pair "
               + "whose name means an element is placed there by name (an event type's placement, or the rule's "
               + "entry, with name_key and value_key), e.g. {\"path\": \"EventDetail/Create/Configuration/Name\", "
               + "\"pair\": {\"list\": \"" + example.getKey() + "\", \"name\": \"<its name>\", \"name_key\": \""
               + example.getValue().nameKey() + "\", \"value_key\": \"" + example.getValue().valueKey() + "\"}}, "
               + "and is then not written as Data too. Never put a "
               + "value in an element that means something else (a setting's value in a Description).";
    }

    // The old/new lists, each with its keys and properties (capped): drafted as an Update's snapshots, every property
    // Data; the properties whose names mean an element are the agent's to place by name, the object its to choose.
    private static String changeNote(final Map<String, ChangeList> lists,
                                     final Map<String, Map.Entry<String, ChangeList>> targets,
                                     final Map<String, List<String>> values) {
        final List<String> said = new ArrayList<>();
        lists.forEach((field, changes) -> said.add(ToolText.repr(field) + " (" + changeKeysSaid(changes) + ")"));
        targets.forEach((field, held) -> said.add(ToolText.repr(held.getKey()) + " in each item of "
                                                  + ToolText.repr(field) + " (" + changeKeysSaid(held.getValue())
                                                  + kindsSaid(field, values.getOrDefault(field, List.of())) + ")"));
        final Map.Entry<String, ChangeList> example = lists.isEmpty()
                ? targets.values().iterator().next()
                : lists.entrySet().iterator().next();
        return String.join(", ", said) + (said.size() == 1
                ? " is an old/new list"
                : " are old/new lists") + " of property changes: under an Update drafted as its Before and After "
               + "snapshots, the object changed (the one the rule writes in a snapshot, else Object) holding a Data "
               + "per property of its old value in Before and its new value in After (one in each object per item of a "
               + "list of targets); elsewhere as any array of objects. The old values are taken from the first of "
               + "the two value keys in the items' order: if the keys say otherwise, swap old_key and new_key. Write "
               + "the snapshots' object as the element the changed thing is (User, Group...), and place a property "
               + "whose name means an element by name, typed in both snapshots, e.g. {\"path\": "
               + "\"EventDetail/Update/After/User/Name\", \"change\": {\"list\": \"" + example.getKey()
               + "\", \"name\": \"<its name>\", ...}} with the same in Before (an event type's placement "
               + "{\"path\": \"User/Name\", \"change\": {\"list\": ..., \"name\": ...}} writes both); "
               + "it is then not written as Data too.";
    }

    private static String changeKeysSaid(final ChangeList changes) {
        return "properties " + NameValueLists.said(changes.names(), NameValueLists.NAMES_SHOWN) + "; named by "
               + changes.nameKey() + ", old values in " + changes.oldKey() + ", new in " + changes.newKey();
    }

    // The items' key that may say what each target is (few distinct words), with its values; "" for none.
    private static String kindsSaid(final String field, final List<String> values) {
        for (final String key : arrayKeys(values)) {
            final Set<String> kinds = new LinkedHashSet<>();
            int held = 0;
            for (final Object value : NameValueLists.heldIn(values, key)) {
                held++;
                if (!(value instanceof final String kind) || !WORD.matcher(kind).matches()) {
                    kinds.clear();
                    break;
                }
                kinds.add(kind);
            }
            if (!kinds.isEmpty() && kinds.size() <= MAX_KINDS && held >= 2 * kinds.size()) {
                return "; each item's " + key + " is one of " + String.join(", ", kinds) + ": if that says what "
                       + "each item of " + field + " is, it names the snapshots' object";
            }
        }
        return "";
    }

    private static String arrayNote(final Map<String, List<String>> arrays) {
        final List<String> said = new ArrayList<>();
        arrays.forEach((field, keys) -> said.add(ToolText.repr(field) + " (keys " + String.join(", ",
                keys.stream().limit(6).toList()) + (keys.size() > 6
                ? "..."
                : "") + ")"));
        final boolean one = arrays.size() == 1;
        return String.join(", ", said) + " " + (one
                ? "holds an array"
                : "hold arrays") + " of objects: drafted as a Data group (for_each array), a Data per object holding "
               + "a Data per key. If each object is one thing the schema has an element for (a user, a file, a "
               + "host), move the group there, its fields the element's (paths relative to it, each reading a key "
               + "of the object with field).";
    }


    // --------------------------------------------------------------------------------


    /// What a list's items are.
    enum Kind {
        /// Mail addresses.
        EMAIL("mail addresses", false),
        /// IPv4 addresses.
        IP_ADDRESS("IP addresses", false),
        /// Whole numbers.
        NUMBER("numbers", true),
        /// Names, ids, hosts: one token holding a letter.
        NAME("names or ids", true);

        private final String said;
        private final boolean weak;

        Kind(final String said, final boolean weak) {
            this.said = said;
            this.weak = weak;
        }

        /// @return What a note calls the items.
        String said() {
            return said;
        }

        /// @return True if prose or a number may look like a list of it, so a list must show more items.
        boolean weak() {
            return weak;
        }
    }

    /// A list field's separator and the kind of its items.
    ///
    /// @param separator The separator.
    /// @param kind      The kind.
    record Split(String separator, Kind kind) {

    }
}
