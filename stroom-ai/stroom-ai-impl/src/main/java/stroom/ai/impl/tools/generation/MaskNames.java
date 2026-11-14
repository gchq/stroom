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

import stroom.ai.impl.tools.ToolText;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/// `mask_names`: the entry's value is a bit mask (an unsigned number of up to 64 bits, decimal or `0x` hex), written as
/// the names of the masks it holds, from a table of mask -> name. A mask is usually one bit; one of several bits is
/// named when the value holds all of them. The names come in the table's order, each once; with `repeat` one element
/// each, else joined by the entry's `separator` (default `, `).
///
/// The table is static (`names`) or read for the record from reference data (`lookup`, its `path` giving the table's
/// entries, each with a `mask` and a `name`: the paths, relative to the entry, of its mask and its name). Set bits no
/// named mask covers, and a value that isn't such a number, are reported by stepping (a warning naming the record).
///
/// @param names  Mask -> name, e.g. `{"0x4": "Write"}`, or null.
/// @param lookup Or the table from reference data, e.g. `{map, key_fields, path: "flags/Flag"}`, or null.
/// @param mask   With lookup: the mask's path in a table entry, e.g. `@Mask`.
/// @param name   With lookup: the name's path in a table entry, e.g. `@Name`.
public record MaskNames(Map<String, String> names,
                        Lookup lookup,
                        String mask,
                        String name) {

    /// The largest value a mask may hold: 64 bits, unsigned.
    public static final BigInteger MAX = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);

    private static final Pattern HEX = Pattern.compile("0[xX][0-9a-fA-F]{1,16}");
    private static final Pattern DECIMAL = Pattern.compile("\\d{1,20}");

    /// Reads one from JSON-like values: lax types and the model's own checks, every error collected.
    ///
    /// @param reader The reader, collecting the errors.
    /// @param input  The value given.
    /// @param loc    Where it is.
    /// @return It, or null (with the errors noted) if it doesn't validate.
    static MaskNames read(final ModelReader reader, final Object input, final List<Object> loc) {
        final ModelReader.Fields fields = reader.fields(input, loc, "MaskNames");
        if (fields == null) {
            return null;
        }
        final MaskNames names = new MaskNames(fields.optionalStringMap("names"),
                fields.optionalModel("lookup", (value, at) -> Lookup.read(reader, value, at)),
                fields.optionalString("mask"), fields.optionalString("name"));
        if (!fields.ok()) {
            return null;
        }
        final String problem = names.problem();
        if (problem != null) {
            fields.valueError(problem);
            return null;
        }
        return names;
    }

    // The model's own checks: one table, its masks numbers of up to 64 bits, a lookup's entry paths given.
    private String problem() {
        if ((names == null) == (lookup == null)) {
            return "mask_names takes its table from names (mask -> name) or from a lookup (with mask and name, the "
                   + "paths of an entry's mask and name), one of them";
        }
        if (names != null) {
            if (names.isEmpty()) {
                return "mask_names names is empty: give each mask and its name, e.g. {\"0x1\": \"Read\"}";
            }
            final List<String> bad = names.keySet().stream()
                    .filter(key -> parse(key) == null || parse(key).signum() == 0)
                    .toList();
            if (!bad.isEmpty()) {
                return "mask_names names: " + ToolText.list(bad) + " isn't a mask: a non-zero number of up to 64 "
                       + "bits, decimal or 0x hex";
            }
            if (mask != null || name != null) {
                return "mask_names mask and name are the paths in a looked-up table's entries: not with names";
            }
            return null;
        }
        if (mask == null || mask.isBlank() || name == null || name.isBlank()) {
            return "mask_names with lookup needs mask and name: the paths, in each of the table's entries, of its "
                   + "mask and its name, e.g. {\"mask\": \"@Mask\", \"name\": \"@Name\"}";
        }
        if (lookup.path() == null || lookup.path().isBlank()) {
            return "mask_names lookup needs path: where the table's entries are in the map's value, e.g. "
                   + "'flags/Flag'";
        }
        return null;
    }

    /// @param mode How to write it.
    /// @return It as JSON, as the mode says, keys in the model's field order.
    public Map<String, Object> toJson(final DumpMode mode) {
        return mode.builder()
                .put("names", names == null
                        ? null
                        : new LinkedHashMap<>(names), null)
                .model("lookup", lookup, null, found -> found.toJson(mode))
                .put("mask", mask, null)
                .put("name", name, null)
                .build();
    }

    /// @param text A mask as a source or table writes it: decimal, or hex with `0x`; white space around it ignored.
    /// @return Its value, or null if it isn't an unsigned number of up to 64 bits.
    public static BigInteger parse(final String text) {
        if (text == null) {
            return null;
        }
        final String stripped = text.strip();
        final BigInteger value;
        if (HEX.matcher(stripped).matches()) {
            value = new BigInteger(stripped.substring(2), 16);
        } else if (DECIMAL.matcher(stripped).matches()) {
            value = new BigInteger(stripped);
        } else {
            return null;
        }
        return value.compareTo(MAX) > 0
                ? null
                : value;
    }

    /// The names a value holds, as the generated XSLT gives them (`mcp:mask-names`), for a preview outside Stroom (the
    /// agent's proposal shown to the user).
    ///
    /// @param value The value, as the source writes it.
    /// @param table The table: each mask and its name, in order.
    /// @return The names (each once, in the table's order) and the set bits no named mask covers.
    public static Named named(final String value, final List<Map.Entry<String, String>> table) {
        final BigInteger number = parse(value);
        if (number == null) {
            return new Named(List.of(), List.of(), value != null && !value.isBlank());
        }
        final Set<String> out = new LinkedHashSet<>();
        BigInteger covered = BigInteger.ZERO;
        for (final Map.Entry<String, String> entry : table) {
            final BigInteger mask = parse(entry.getKey());
            final String name = Objects.requireNonNullElse(entry.getValue(), "").strip();
            if (mask == null || mask.signum() == 0 || name.isEmpty()) {
                continue;
            }
            if (number.and(mask).equals(mask)) {
                out.add(name);
                covered = covered.or(mask);
            }
        }
        final List<Integer> unnamed = new ArrayList<>();
        final BigInteger left = number.andNot(covered);
        for (int bit = 0; bit < 64; bit++) {
            if (left.testBit(bit)) {
                unnamed.add(bit);
            }
        }
        return new Named(List.copyOf(out), List.copyOf(unnamed), false);
    }

    /// @param bit A bit, 0 to 63.
    /// @return The mask holding only it, in hex, e.g. `0x8000000000000000` for bit 63.
    public static String hex(final int bit) {
        return "0x" + BigInteger.ONE.shiftLeft(bit).toString(16).toUpperCase(Locale.ROOT);
    }


    // --------------------------------------------------------------------------------


    /// What a value's names are.
    ///
    /// @param names      The names, in the table's order.
    /// @param unnamed    The set bits no named mask covers, lowest first.
    /// @param notANumber True if the value has text but isn't an unsigned number of up to 64 bits.
    public record Named(List<String> names, List<Integer> unnamed, boolean notANumber) {

    }
}
