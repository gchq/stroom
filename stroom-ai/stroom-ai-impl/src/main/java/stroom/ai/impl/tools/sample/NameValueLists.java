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
import java.util.Map;
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
/// (2026-11-23). An item of three keys (a name with its old and new values) is never one.
public final class NameValueLists {

    /// The value key of a list whose items each hold their value under a key of its own (`value`, `boolValue`,
    /// `multiValue`...): the item's one key besides its name.
    public static final String ANY_VALUE_KEY = "*";
    /// The most names a listing gives before "… N more".
    public static final int NAMES_SHOWN = 10;
    /// The fewest lists with items for a field to be told a name/value list.
    static final int MIN_LISTS = 2;
    /// The shortest a name is (most names): anything shorter is a code.
    static final int MIN_NAME_LENGTH = 2;

    // The most values read.
    private static final int VALUES_READ = 5_000;
    // The share of the distinct names that must be words or identifiers.
    private static final double WORDY_SHARE = 0.9;
    private static final Pattern LETTER = Pattern.compile("\\p{L}");

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
        final Optional<NameValueList> told = detect(values);
        if (told.isPresent()) {
            return told;
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
}
