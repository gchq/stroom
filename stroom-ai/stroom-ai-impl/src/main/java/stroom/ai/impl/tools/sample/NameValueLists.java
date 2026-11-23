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

package stroom.ai.impl.tools.sample;

import stroom.ai.impl.tools.ToolText;

import java.util.ArrayList;
import java.util.Collection;
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
import java.util.regex.Pattern;

/// Name/value lists (decided by the user 2026-11-23): a JSON array field whose items are settings, each a pair of a
/// name and its value (`[{"Name": "MailAccessType", "Value": "Bind"}, {"Name": "IsThrottled", "Value": "False"}]`),
/// told by shape alone, never by the keys' names. A pair's name says what its value is, so the list is not one
/// thing the schema has an element for (as a list of users is): its pairs are Data, each named by its pair's name,
/// and a pair whose name means an element is read by its name.
///
/// A field is a name/value list where, over the values given (each a list as read, or JSON text holding one; others
/// skipped):
///
/// * every item is an object of two keys, the same two holding text (JSON strings: an entity with a numeric type
///   code, `{"ID": "...", "Type": 5}`, isn't one);
/// * one key, the name, is unique within each list (one pair per name), and its values are words or identifiers:
///   most of its distinct values hold a letter and are at least [#MIN_NAME_LENGTH] characters long (not numbers, nor
///   one-character codes);
/// * across the sample the names recur: there are at most half as many distinct names as items, and fewer distinct
///   names than values;
/// * the values vary by name: some name has two values, or some value two names. Two keys naming the same thing
///   (a user's address and display name, `{"MentionedUser": "...", "DisplayName": "..."}`) are one to one, so not a
///   name and its value;
/// * at least [#MIN_LISTS] lists hold items, so the recurrence can be seen.
///
/// Where both keys could be the name, the one with fewer distinct values is, else the first in the items' order.
///
/// A list whose items share only the name's key, each holding its value under a key of its own kind
/// (`{"name": "IS_ADMIN", "boolValue": true}`, `{"name": "GROUPS", "multiValue": ["a", "b"]}`), is one too where the
/// name passes the same tests and each value is a scalar or a list of scalars: its value key is [#ANY_VALUE_KEY]
/// (2026-11-23). An item of three keys (a name with its old and new values) is never one: such a list may be an
/// old/new property-change list ([#changes(Collection)]), told by the same name tests.
public final class NameValueLists {

    /// The value key of a list whose items each hold their value under a key of its own (`value`, `boolValue`,
    /// `multiValue`...): the item's one key besides its name.
    public static final String ANY_VALUE_KEY = "*";
    /// The most names a listing gives before "… N more".
    public static final int NAMES_SHOWN = 10;
    /// JSON text holding one value: a string, or an array of at most one scalar (an old or new value a source writes
    /// as JSON, unwrapped: [#changeText(Object)]). The translation tests a value with the same pattern.
    public static final String JSON_ONE_VALUE_REGEX = "^\\s*(\\[\\s*(\"([^\"\\\\]|\\\\.)*\"|-?[0-9][0-9.eE+-]*|true"
                                                      + "|false|null)?\\s*\\]|\"([^\"\\\\]|\\\\.)*\")\\s*$";
    /// The fewest lists with items for a field to be told a name/value list.
    static final int MIN_LISTS = 2;
    /// The shortest a name is (most names): anything shorter is a code.
    static final int MIN_NAME_LENGTH = 2;

    // The most values read.
    private static final int VALUES_READ = 5_000;
    // The share of the distinct names that must be words or identifiers.
    private static final double WORDY_SHARE = 0.9;
    // The words of a key's name meaning the value before a change, and after it (decided by the user 2026-11-23:
    // only which side is proposed first, the user still answering).
    private static final Set<String> BEFORE_WORDS = Set.of("old", "previous", "prev", "before", "original", "from");
    private static final Set<String> AFTER_WORDS = Set.of("new", "current", "after", "updated", "to");
    // Where a key's name splits into words: between a lower case letter or digit and an upper case one, before the
    // last upper case letter of a run followed by a lower case one, and at anything but a letter or digit.
    private static final Pattern WORD_BREAK = Pattern.compile(
            "(?<=[\\p{Ll}\\d])(?=\\p{Lu})|(?<=\\p{Lu})(?=\\p{Lu}\\p{Ll})|[^\\p{L}\\d]+");
    private static final Pattern LETTER = Pattern.compile("\\p{L}");
    // The keys of an old/new list's items: a name, its old value and its new value.
    private static final int CHANGE_KEYS = 3;
    private static final Pattern ALPHANUMERIC_RUN = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Pattern JSON_ONE_VALUE = Pattern.compile(JSON_ONE_VALUE_REGEX, Pattern.DOTALL);

    private NameValueLists() {
        // Static only.
    }

    /// @param values A field's values: each a list (as read) or JSON text holding one; any other value skipped.
    /// @return The field as a name/value list, or empty where its values aren't one.
    public static Optional<NameValueList> detect(final Collection<?> values) {
        final List<List<Map<?, ?>>> lists = new ArrayList<>();
        Set<String> common = null;
        int read = 0;
        for (final Object value : values) {
            if (read++ >= VALUES_READ) {
                break;
            }
            final List<?> list = items(value);
            if (list == null) {
                if (value != null && !(value instanceof final String text && text.isBlank())) {
                    return Optional.empty();
                }
                continue;
            }
            if (list.isEmpty()) {
                continue;
            }
            final List<Map<?, ?>> pairs = new ArrayList<>();
            for (final Object item : list) {
                // Two keys, never three: an item of an old and a new value with its name is something else.
                if (!(item instanceof final Map<?, ?> pair) || pair.size() != 2) {
                    return Optional.empty();
                }
                final Set<String> keys = new LinkedHashSet<>();
                pair.keySet().forEach(key -> keys.add(String.valueOf(key)));
                if (common == null) {
                    common = keys;
                } else {
                    common.retainAll(keys);
                }
                pairs.add(pair);
            }
            lists.add(pairs);
        }
        if (common == null || common.isEmpty() || lists.size() < MIN_LISTS) {
            return Optional.empty();
        }
        if (common.size() == 2) {
            final List<String> keys = List.copyOf(common);
            return fixed(values, lists, keys.get(0), keys.get(1));
        }
        return varying(values, lists, common.iterator().next());
    }

    // Both keys the same in every item: each could be the name, the one with fewer values is (else the first); both
    // hold text.
    private static Optional<NameValueList> fixed(final Collection<?> values,
                                                 final List<List<Map<?, ?>>> lists,
                                                 final String first,
                                                 final String second) {
        final Tally a = new Tally();
        final Tally b = new Tally();
        for (final List<Map<?, ?>> list : lists) {
            final Set<String> seenA = new HashSet<>();
            final Set<String> seenB = new HashSet<>();
            for (final Map<?, ?> pair : list) {
                if (!(pair.get(first) instanceof final String one) || !(pair.get(second) instanceof final String two)) {
                    return Optional.empty();
                }
                a.add(one, two, seenA);
                b.add(two, one, seenB);
            }
        }
        final boolean secondIsName = b.values.size() < a.values.size();
        final Tally name = secondIsName
                ? b
                : a;
        final Tally value = secondIsName
                ? a
                : b;
        if (!name.isName() || name.values.size() >= value.values.size() || oneToOne(name.with, value.with)) {
            return Optional.empty();
        }
        final String nameKey = secondIsName
                ? second
                : first;
        return Optional.of(new NameValueList(nameKey, secondIsName
                ? first
                : second, namesIn(values, nameKey)));
    }

    // One key in every item, the name; each item's other key holding its value, named by its kind (a text, a number,
    // a list...): its value a scalar, or a list of scalars.
    private static Optional<NameValueList> varying(final Collection<?> values,
                                                   final List<List<Map<?, ?>>> lists,
                                                   final String nameKey) {
        final Tally name = new Tally();
        final Map<String, Set<String>> byValue = new HashMap<>();
        for (final List<Map<?, ?>> list : lists) {
            final Set<String> seen = new HashSet<>();
            for (final Map<?, ?> pair : list) {
                if (!(pair.get(nameKey) instanceof final String named)) {
                    return Optional.empty();
                }
                final Object held = pair.entrySet().stream()
                        .filter(entry -> !nameKey.equals(String.valueOf(entry.getKey())))
                        .findFirst()
                        .map(Map.Entry::getValue)
                        .orElse(null);
                final String text = scalarText(held);
                if (text == null) {
                    return Optional.empty();
                }
                name.add(named, text, seen);
                byValue.computeIfAbsent(text, ignored -> new HashSet<>()).add(named);
            }
        }
        if (!name.isName() || oneToOne(name.with, byValue)) {
            return Optional.empty();
        }
        return Optional.of(new NameValueList(nameKey, ANY_VALUE_KEY, namesIn(values, nameKey)));
    }

    /// @param item     A list's item.
    /// @param nameKey  The key holding its name.
    /// @param valueKey The key holding its value, or [#ANY_VALUE_KEY] for its one other key.
    /// @return Its value's text (a list of scalars' joined with `, `), or null for none.
    public static String valueOf(final Map<?, ?> item, final String nameKey, final String valueKey) {
        if (!ANY_VALUE_KEY.equals(valueKey)) {
            return scalarText(item.get(valueKey));
        }
        return item.entrySet().stream()
                .filter(entry -> !nameKey.equals(String.valueOf(entry.getKey())))
                .findFirst()
                .map(entry -> scalarText(entry.getValue()))
                .orElse(null);
    }

    // A scalar's text, or a list of scalars' joined; null for anything else.
    private static String scalarText(final Object value) {
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof final List<?> list && list.stream().allMatch(item -> item instanceof String
                                                                          || item instanceof Number
                                                                          || item instanceof Boolean)) {
            return String.join(", ", list.stream().map(String::valueOf).toList());
        }
        return null;
    }

    /// The list a pair placed by its name reads: told by shape ([#detect(Collection)]), else, where the sample is too
    /// small to tell (a list or two), read as the pair's name says: every item an object of two keys, one of the keys
    /// every item has holding that name in an item (its name key), the other its value (or [#ANY_VALUE_KEY] where
    /// the items share only the name's key).
    ///
    /// @param values The field's values.
    /// @param name   The pair's name.
    /// @return The list, or empty where it isn't one, or no pair has the name (where it can't be told by shape).
    public static Optional<NameValueList> forPair(final Collection<?> values, final String name) {
        return forPair(values, name, null);
    }

    /// The list a pair placed by its name reads, as [#forPair(Collection, String)] tells it, or an old/new list
    /// ([#changes(Collection)]) read on one side (2026-11-23: an event type with no Before and After, an Authorise
    /// adding a role, reads a property's new value as a pair): its value key the one given where it is the list's old
    /// or new key, else its new key.
    ///
    /// @param values The field's values.
    /// @param name   The pair's name.
    /// @param given  The value key given, or null.
    /// @return The list, or empty where it isn't one, or no pair has the name (where it can't be told by shape).
    public static Optional<NameValueList> forPair(final Collection<?> values, final String name,
                                                  final String given) {
        final Optional<NameValueList> told = detect(values);
        if (told.isPresent()) {
            return told;
        }
        final Optional<ChangeList> changes = changes(values);
        if (changes.isPresent()) {
            final ChangeList found = changes.get();
            return Optional.of(new NameValueList(found.nameKey(), found.oldKey().equals(given)
                    ? found.oldKey()
                    : found.newKey(), found.names()));
        }
        Set<String> common = null;
        String nameKey = null;
        int read = 0;
        for (final Object value : values) {
            if (read++ >= VALUES_READ) {
                break;
            }
            final List<?> list = items(value);
            if (list == null) {
                continue;
            }
            for (final Object item : list) {
                if (!(item instanceof final Map<?, ?> pair) || pair.size() != 2) {
                    return Optional.empty();
                }
                final Set<String> keys = new LinkedHashSet<>();
                pair.forEach((key, held) -> {
                    if (held instanceof String) {
                        keys.add(String.valueOf(key));
                    }
                });
                if (common == null) {
                    common = keys;
                } else {
                    common.retainAll(keys);
                }
                for (final Map.Entry<?, ?> entry : pair.entrySet()) {
                    if (name.equals(entry.getValue()) && nameKey == null) {
                        nameKey = String.valueOf(entry.getKey());
                    }
                }
            }
        }
        if (common == null || nameKey == null || !common.contains(nameKey)) {
            return Optional.empty();
        }
        final String key = nameKey;
        final String valueKey = common.stream().filter(other -> !other.equals(key)).findFirst()
                .orElse(ANY_VALUE_KEY);
        return Optional.of(new NameValueList(key, valueKey, namesIn(values, key)));
    }

    /// Old/new property-change lists (decided by the user 2026-11-23): a JSON array field whose items each give a
    /// property's name, its old value and its new value (`[{"Name": "DisplayName", "OldValue": "Sam T", "NewValue":
    /// "Sam Taylor"}]`), told by shape alone, never by the keys' names. A field is one where, over the values given
    /// (each a list as read, or JSON text holding one):
    ///
    /// * every item is an object of the same three keys, each holding text, a number, a boolean, null or a list of
    ///   scalars;
    /// * one key, the name, passes the name's tests of a name/value list ([#detect(Collection)]): text in every
    ///   item, unique within each list, words or identifiers, recurring across the sample; where two keys pass, the
    ///   one with fewer distinct values is the name, else the first;
    /// * the other two are one property's values before and after: a value held under one key in an item is held
    ///   under the other in some item (a setting turned on and off, a title changed twice); else some item holds
    ///   one without the other (a property set or cleared) and, where both hold a value in an item, most such
    ///   items' two values are of one shape ([#shape(String)]: `Sam T` and `Sam Taylor` are, an id and a display
    ///   name aren't). A list of things of three keys ({UPN, DisplayName, Role}, {Type, ID, Name}) shows neither;
    /// * at least [#MIN_LISTS] lists hold items.
    ///
    /// Which of the two is old, as proposed (the user is asked in a build with forms; a placement or entry names
    /// them, `old_key` and `new_key`): the one the two keys' names say ([#oldKeyByName]), else the first in the
    /// items' key order (decided by the user 2026-11-23: Microsoft 365 writes `NewValue` before `OldValue`, so the
    /// order alone proposed the wrong one).
    ///
    /// @param values A field's values: each a list (as read) or JSON text holding one; any other value skipped.
    /// @return The field as an old/new list, or empty where its values aren't one.
    public static Optional<ChangeList> changes(final Collection<?> values) {
        final List<List<Map<?, ?>>> lists = new ArrayList<>();
        List<String> keys = null;
        int read = 0;
        for (final Object value : values) {
            if (read++ >= VALUES_READ) {
                break;
            }
            final List<?> list = items(value);
            if (list == null) {
                if (value != null && !(value instanceof final String text && text.isBlank())) {
                    return Optional.empty();
                }
                continue;
            }
            if (list.isEmpty()) {
                continue;
            }
            final List<Map<?, ?>> changes = new ArrayList<>();
            for (final Object item : list) {
                if (!(item instanceof final Map<?, ?> change) || change.size() != CHANGE_KEYS) {
                    return Optional.empty();
                }
                final List<String> own = change.keySet().stream().map(String::valueOf).toList();
                if (keys == null) {
                    keys = own;
                } else if (!Set.copyOf(keys).equals(Set.copyOf(own))) {
                    return Optional.empty();
                }
                for (final Object held : change.values()) {
                    if (held != null && scalarText(held) == null) {
                        return Optional.empty();
                    }
                }
                changes.add(change);
            }
            lists.add(changes);
        }
        if (keys == null || lists.size() < MIN_LISTS) {
            return Optional.empty();
        }
        final String nameKey = changeName(lists, keys);
        if (nameKey == null) {
            return Optional.empty();
        }
        final List<String> sides = keys.stream().filter(key -> !key.equals(nameKey)).toList();
        if (!beforeAndAfter(lists, sides.get(0), sides.get(1))) {
            return Optional.empty();
        }
        final String old = Objects.requireNonNullElse(oldKeyByName(sides.get(0), sides.get(1)), sides.get(0));
        final String added = old.equals(sides.get(0))
                ? sides.get(1)
                : sides.get(0);
        return Optional.of(new ChangeList(nameKey, old, added, namesIn(values, nameKey)));
    }

    /// The key of an old/new list's two value keys whose name says it holds the value before the change: a word of
    /// its name (a whole word, or a camelCase or snake_case part, in any case) meaning before (old, previous, prev,
    /// before, original, from) where the other's doesn't, or the other's meaning after (new, current, after,
    /// updated, to) where its own doesn't. Proposed first to the user (decided by the user 2026-11-23); the items'
    /// key order where the names say nothing, or say both of one key.
    ///
    /// @param first  The first value key, in the items' order.
    /// @param second The second.
    /// @return The one holding the value before, by their names; null where the names don't tell.
    public static String oldKeyByName(final String first, final String second) {
        final boolean firstBefore = says(first, BEFORE_WORDS) && !says(first, AFTER_WORDS);
        final boolean firstAfter = says(first, AFTER_WORDS) && !says(first, BEFORE_WORDS);
        final boolean secondBefore = says(second, BEFORE_WORDS) && !says(second, AFTER_WORDS);
        final boolean secondAfter = says(second, AFTER_WORDS) && !says(second, BEFORE_WORDS);
        final boolean firstIsOld = firstBefore || secondAfter;
        final boolean secondIsOld = secondBefore || firstAfter;
        if (firstIsOld == secondIsOld) {
            // Neither says, or they say both ways.
            return null;
        }
        return firstIsOld
                ? first
                : second;
    }

    // Whether a key's name holds one of the words, as a whole word or a camelCase or snake_case part.
    private static boolean says(final String key, final Set<String> words) {
        if (key == null) {
            return false;
        }
        for (final String part : WORD_BREAK.split(key)) {
            if (words.contains(part.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    // The key of the items' three that names each property: text in every item, passing a name's tests; of two that
    // pass, the one with fewer distinct values, else the first. Null where none does.
    private static String changeName(final List<List<Map<?, ?>>> lists, final List<String> keys) {
        String best = null;
        int fewest = Integer.MAX_VALUE;
        for (final String key : keys) {
            final Tally tally = new Tally();
            boolean text = true;
            for (final List<Map<?, ?>> list : lists) {
                final Set<String> seen = new HashSet<>();
                for (final Map<?, ?> item : list) {
                    if (!(item.get(key) instanceof final String name) || name.isBlank()) {
                        text = false;
                        break;
                    }
                    tally.add(name, "", seen);
                }
            }
            if (text && tally.isName() && tally.values.size() < fewest) {
                best = key;
                fewest = tally.values.size();
            }
        }
        return best;
    }

    // Whether two keys hold one property's values before and after: a value under both (a setting turned on and
    // off); else one held without the other somewhere, and of one shape where both hold a value.
    private static boolean beforeAndAfter(final List<List<Map<?, ?>>> lists, final String first, final String second) {
        final Set<String> firsts = new HashSet<>();
        final Set<String> seconds = new HashSet<>();
        int both = 0;
        int alike = 0;
        boolean oneSided = false;
        for (final List<Map<?, ?>> list : lists) {
            for (final Map<?, ?> item : list) {
                final String one = changeText(item.get(first));
                final String two = changeText(item.get(second));
                if (!one.isBlank()) {
                    firsts.add(one);
                }
                if (!two.isBlank()) {
                    seconds.add(two);
                }
                if (!one.isBlank() && !two.isBlank()) {
                    both++;
                    if (shape(one).equals(shape(two))) {
                        alike++;
                    }
                } else if (!one.isBlank() || !two.isBlank()) {
                    oneSided = true;
                }
            }
        }
        if (firsts.stream().anyMatch(seconds::contains)) {
            return true;
        }
        return oneSided && (both == 0 || alike * 2 >= both);
    }

    /// A value's shape: each run of letters and digits one `x`, everything else as it is (`Sam Taylor` and `Sam T`
    /// are `x x`, a GUID `x-x-x-x-x`, `[false]` `[x]`): two values of one property are mostly of one shape.
    ///
    /// @param value A value.
    /// @return Its shape.
    static String shape(final String value) {
        return ALPHANUMERIC_RUN.matcher(value.strip()).replaceAll("x");
    }

    /// An old or new value as a translation writes it (decided by the user 2026-11-23): JSON text holding a string
    /// or an array of one scalar is unwrapped to its value (`"[\"true\"]"` is `true`, `"\"Sam T\""` is `Sam T`, `[]`
    /// nothing); a longer array or an object stays as its JSON text; a list of scalars (as read) is joined with
    /// `, `, as a name/value list's is.
    ///
    /// @param value An item's value (text, a scalar, a list of scalars), or null.
    /// @return Its text, empty for none.
    public static String changeText(final Object value) {
        final String text = value == null
                ? null
                : scalarText(value);
        if (text == null) {
            return "";
        }
        if (!(value instanceof String) || !JSON_ONE_VALUE.matcher(text).matches()) {
            return text;
        }
        try {
            final Object parsed = ToolJson.loads(text.strip());
            final Object one = parsed instanceof final List<?> list
                    ? (list.isEmpty()
                            ? null
                            : list.getFirst())
                    : parsed;
            return one == null
                    ? ""
                    : String.valueOf(one);
        } catch (final ToolJson.JsonDecodeException e) {
            return text;
        }
    }

    /// @param array The array field whose items hold the list.
    /// @param key   The items' key holding it.
    /// @return The name an old/new list inside an array's items goes by, where the user's answer of which key holds
    /// the old value is kept (`targetResources[].modifiedProperties`); a list of the records goes by its field.
    public static String nestedName(final String array, final String key) {
        return array + "[]." + key;
    }

    /// @param values  The list field's values, as [#changes(Collection)] reads them.
    /// @param changes The list's keys.
    /// @return The first item holding both an old and a new value (as the user is shown it to say which is which),
    /// or empty where none does.
    public static Map<String, Object> example(final Collection<?> values, final ChangeList changes) {
        int read = 0;
        for (final Object value : values) {
            if (read++ >= VALUES_READ) {
                break;
            }
            final List<?> list = items(value);
            if (list == null) {
                continue;
            }
            for (final Object item : list) {
                if (item instanceof final Map<?, ?> map && !changeText(map.get(changes.oldKey())).isBlank()
                    && !changeText(map.get(changes.newKey())).isBlank()) {
                    final Map<String, Object> out = new LinkedHashMap<>();
                    map.forEach((key, held) -> out.put(String.valueOf(key), held));
                    return out;
                }
            }
        }
        return Map.of();
    }

    /// The values of a key of an array field's items, each item's its own value: the lists inside the items
    /// (`targetResources[].modifiedProperties`), to tell by shape as a field's are.
    ///
    /// @param values The array field's values: each a list (as read) or JSON text holding one.
    /// @param key    The items' key.
    /// @return Each item's value of the key, in order.
    public static List<Object> heldIn(final Collection<?> values, final String key) {
        final List<Object> out = new ArrayList<>();
        int read = 0;
        for (final Object value : values) {
            if (read++ >= VALUES_READ) {
                break;
            }
            final List<?> list = items(value);
            if (list == null) {
                continue;
            }
            for (final Object item : list) {
                if (item instanceof final Map<?, ?> map && map.get(key) != null) {
                    out.add(map.get(key));
                }
            }
        }
        return out;
    }

    /// The first key of an array field's items holding an old/new list ([#changes(Collection)]): targets, each with
    /// the properties changed (Graph's `targetResources[].modifiedProperties`), told by shape.
    ///
    /// @param values The array field's values: each a list (as read) or JSON text holding one.
    /// @return The key, with the list's keys; empty where no key of the items holds one.
    public static Optional<Map.Entry<String, ChangeList>> heldChanges(final Collection<?> values) {
        final Set<String> keys = new LinkedHashSet<>();
        int read = 0;
        for (final Object value : values) {
            if (read++ >= VALUES_READ) {
                break;
            }
            final List<?> list = items(value);
            if (list == null) {
                continue;
            }
            for (final Object item : list) {
                if (item instanceof final Map<?, ?> map) {
                    map.forEach((key, held) -> {
                        if (held instanceof List) {
                            keys.add(String.valueOf(key));
                        }
                    });
                }
            }
        }
        for (final String key : keys) {
            final Optional<ChangeList> found = changes(heldIn(values, key));
            if (found.isPresent()) {
                return Optional.of(Map.entry(key, found.get()));
            }
        }
        return Optional.empty();
    }

    /// @param values The field's values (as [#detect(Collection)] reads them).
    /// @param nameKey The key holding each pair's name.
    /// @return How many items hold each name, most first (the first seen first among equals).
    public static Map<String, Integer> namesIn(final Collection<?> values, final String nameKey) {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        int read = 0;
        for (final Object value : values) {
            if (read++ >= VALUES_READ) {
                break;
            }
            final List<?> list = items(value);
            if (list == null) {
                continue;
            }
            for (final Object item : list) {
                if (item instanceof final Map<?, ?> pair && pair.get(nameKey) instanceof final String name) {
                    counts.merge(name, 1, Integer::sum);
                }
            }
        }
        final List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        final Map<String, Integer> out = new LinkedHashMap<>();
        sorted.forEach(entry -> out.put(entry.getKey(), entry.getValue()));
        return out;
    }

    /// @param names Names with their counts, most first.
    /// @param most  The most named.
    /// @return Them in words: `Name (198), From (40), … 3 more`.
    public static String said(final Map<String, Integer> names, final int most) {
        final List<String> out = new ArrayList<>();
        for (final Map.Entry<String, Integer> entry : names.entrySet()) {
            if (out.size() == most) {
                break;
            }
            out.add(entry.getKey() + " (" + entry.getValue() + ")");
        }
        return String.join(", ", out) + (names.size() > most
                ? ", … " + (names.size() - most) + " more"
                : "");
    }

    // A value's items: a list as read, or JSON text holding one; null for anything else.
    private static List<?> items(final Object value) {
        if (value instanceof final List<?> list) {
            return list;
        }
        if (value instanceof final String string) {
            final String text = ToolText.strip(string);
            if (!text.startsWith("[")) {
                return null;
            }
            try {
                return SampleProfiler.loadsDecoded(text) instanceof final List<?> list
                        ? list
                        : null;
            } catch (final ToolJson.JsonDecodeException e) {
                return null;
            }
        }
        return null;
    }

    // Most of the names are words or identifiers: a letter, and longer than a code.
    private static boolean wordy(final Set<String> names) {
        final long words = names.stream()
                .filter(name -> name.strip().length() >= MIN_NAME_LENGTH && LETTER.matcher(name).find())
                .count();
        return words >= WORDY_SHARE * names.size();
    }

    // Each name with one value and each value with one name: two names for the same thing.
    private static boolean oneToOne(final Map<String, Set<String>> byName, final Map<String, Set<String>> byValue) {
        return byName.values().stream().allMatch(set -> set.size() == 1)
               && byValue.values().stream().allMatch(set -> set.size() == 1);
    }


    // --------------------------------------------------------------------------------


    // A key's values over the items: each value's partners (the other key's), and whether a list held one twice.
    private static final class Tally {

        private final Set<String> values = new HashSet<>();
        private final Map<String, Set<String>> with = new HashMap<>();
        private boolean repeats;
        private int items;

        private void add(final String value, final String partner, final Set<String> seenInList) {
            items++;
            values.add(value);
            with.computeIfAbsent(value, ignored -> new HashSet<>()).add(partner);
            if (!seenInList.add(value)) {
                repeats = true;
            }
        }

        // The tests of a name's key: one pair per name in a list, names that recur, words or identifiers.
        private boolean isName() {
            return !repeats && values.size() * 2 <= items && wordy(values);
        }
    }


    /// A field told a name/value list.
    ///
    /// @param nameKey  The items' key holding each pair's name.
    /// @param valueKey The items' key holding its value; [#ANY_VALUE_KEY] where each item holds it under a key of its
    ///                 own (a scalar, or a list of scalars).
    /// @param names    How many items hold each name over the values it was told from, most first.
    public record NameValueList(String nameKey, String valueKey, Map<String, Integer> names) {

    }


    /// A field told an old/new property-change list ([#changes(Collection)]).
    ///
    /// @param nameKey The items' key holding each property's name.
    /// @param oldKey  The key holding its value before, as proposed: by the keys' names where they say, else the
    ///                first of the other two in the items' order.
    /// @param newKey  The key holding its value after.
    /// @param names   How many items hold each property's name over the values it was told from, most first.
    public record ChangeList(String nameKey, String oldKey, String newKey, Map<String, Integer> names) {

        /// @param before The key the user said holds the value before the change (2026-11-23), or null.
        /// @return It with that key as its old one (the other its new); itself where none is given or the key isn't
        /// one of its two.
        public ChangeList oriented(final String before) {
            return newKey.equals(before)
                    ? new ChangeList(nameKey, newKey, oldKey, names)
                    : this;
        }
    }
}
