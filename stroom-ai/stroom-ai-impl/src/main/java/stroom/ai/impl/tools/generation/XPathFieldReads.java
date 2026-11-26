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

import stroom.ai.impl.tools.sample.JsonFieldNames;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/// The fields of the records a mapping's xpath reads, named as the records spell them: a JSON record's keys
/// (`*[@key='event']/*[@key='app']` is `event.app`), a Data Splitter record's names (`data[@name='a']/@value` is
/// `a`), an XML record's element and attribute paths (`System/EventID`, `System/@Name`), and the keys of the JSON a
/// field holds, read with `json-to-xml()` (`json-to-xml(*[@key='msg'])/*/*[@key='user']` is `msg.user`, JSON held in
/// a key of held JSON read the same way again). Seen in testing: a field placed by an xpath into the JSON a field
/// held was kept as Data too, as nothing knew the xpath read it.
///
/// Only a path the records' own shape names is read; anything else (a variable, an absolute path, a step by any
/// other axis, a function whose result isn't the node it is given) reads nothing here, and an expression that
/// doesn't parse reads nothing: a field is never taken as read when it may not be. What a predicate tests is not
/// counted, as it isn't written.
public final class XPathFieldReads {

    // The operators whose right side is read from their left side's nodes.
    private static final Set<String> PATH_OPERATORS = Set.of("/", "//", "!");
    // The functions whose value is the text of the node they are given: a read of it, as it is.
    private static final Set<String> AS_IT_IS = Set.of("string", "data");
    // The functions whose value is the text of the node they are given, changed: a read of it ([#fields]), but not
    // the field as it is ([#plainField]); normalize-space() trims and folds its white space.
    private static final Set<String> CHANGED = Set.of("normalize-space");
    private static final Set<String> JSON_CALLS = Set.of("json-to-xml", "fn:json-to-xml", "mcp:json-to-xml",
            "stroom:json-to-xml");
    private static final Cursor RECORD = new Cursor(null, Mode.RECORD, Style.NONE, true);
    // Where a path is at a field's value: not a document, a held JSON's top, nor a field's items.
    private static final Set<Mode> VALUES = Set.of(Mode.RECORD, Mode.HELD, Mode.END);

    private XPathFieldReads() {
        // Static only.
    }

    /// @param xpath An xpath of a mapping's entry, read relative to the record (or null).
    /// @return The fields of the records it reads, each once, in the order it reads them; none when it doesn't
    /// parse, or reads none the records' shape names.
    public static Set<String> fields(final String xpath) {
        final XPathNode node = parsed(xpath);
        if (node == null) {
            return Set.of();
        }
        final Set<String> out = new LinkedHashSet<>();
        collect(node, out);
        return Collections.unmodifiableSet(out);
    }

    /// @param xpath An xpath of a mapping's entry (or null).
    /// @return The one field the whole xpath gives as it is (its text, its first value), or null: an xpath doing
    /// anything else with it (a substring, a join, normalize-space(), a condition, a filter or a position other than
    /// the first in a predicate beyond the step naming the field) gives a value of its own.
    public static String plainField(final String xpath) {
        final XPathNode node = parsed(xpath);
        if (node == null) {
            return null;
        }
        final Cursor cursor = resolve(node);
        return cursor != null && cursor.plain() && cursor.field() != null && VALUES.contains(cursor.mode())
                ? cursor.field()
                : null;
    }

    private static XPathNode parsed(final String xpath) {
        if (xpath == null || xpath.isBlank()) {
            return null;
        }
        try {
            return XPathTreeParser.parserFor(MappingFacts.KNOWN_PREFIXES).parse(xpath);
        } catch (final RuntimeException e) {
            // Not an expression read here (an unbound prefix, say): nothing known to be read.
            return null;
        }
    }

    // The fields a node reads: itself when it is a path to one, else those its parts read (not its predicates').
    private static void collect(final XPathNode node, final Set<String> out) {
        final Cursor cursor = resolve(node);
        if (cursor != null) {
            if (cursor.field() != null) {
                out.add(cursor.field());
            }
            return;
        }
        if ("$".equals(node.symbol())) {
            // A variable: what it holds isn't known here.
            return;
        }
        if (PATH_OPERATORS.contains(node.symbol()) || "[".equals(node.symbol())) {
            // A step's (or a predicate's) context is what is before it, not the record: only that is read here.
            if (node.size() == 2) {
                collect(node.get(0), out);
            }
            return;
        }
        node.children().forEach(child -> collect(child, out));
    }

    // Where an expression is: a field of the record (or the record itself), or null when it isn't a path the
    // records' shape names.
    private static Cursor resolve(final XPathNode node) {
        switch (node.symbol()) {
            case "." -> {
                return RECORD;
            }
            case "$" -> {
                // A group's field with scope record reads the record through $record.
                return "record".equals(node.valueText())
                        ? RECORD
                        : null;
            }
            case "(" -> {
                return node.size() == 1
                        ? resolve(node.get(0))
                        : null;
            }
            case "/" -> {
                if (node.size() != 2) {
                    // An absolute path: not the record's.
                    return null;
                }
                final Cursor left = resolve(node.get(0));
                return left == null
                        ? null
                        : changedIf(step(left, node.get(1)), !left.plain());
            }
            case "call" -> {
                return called(node);
            }
            default -> {
                return step(RECORD, node);
            }
        }
    }

    private static Cursor called(final XPathNode node) {
        final String name = node.valueText();
        if (JSON_CALLS.contains(name) && (node.size() == 1 || node.size() == 2)) {
            final Cursor held = resolve(node.get(0));
            return held == null || held.field() == null || !VALUES.contains(held.mode())
                    ? null
                    : new Cursor(held.field(), Mode.DOCUMENT, Style.JSON, held.plain());
        }
        final String local = name.startsWith("fn:")
                ? name.substring(3)
                : name;
        if ((AS_IT_IS.contains(local) || CHANGED.contains(local)) && node.size() == 1) {
            final Cursor read = resolve(node.get(0));
            return read == null || read.mode() == Mode.DOCUMENT
                    ? null
                    : changedIf(read, CHANGED.contains(local));
        }
        return null;
    }

    // One step from where a path is.
    private static Cursor step(final Cursor at, final XPathNode node) {
        switch (node.symbol()) {
            case "[" -> {
                if (node.size() != 2) {
                    return null;
                }
                final XPathNode base = node.get(0);
                final String key = tested(node.get(1), "key");
                if (key != null && ("*".equals(base.symbol()) || "(name)".equals(base.symbol()))) {
                    return keyStep(at, key);
                }
                final String name = tested(node.get(1), "name");
                if (name != null && "(name)".equals(base.symbol()) && "data".equals(base.valueText())) {
                    return dataStep(at, name);
                }
                final String named = tested(node.get(1), "Name");
                if (named != null && "(name)".equals(base.symbol())) {
                    // A Windows event's EventData/Data by its Name, as XML records name it.
                    return elementStep(at, base.valueText() + "[@Name='" + named + "']");
                }
                // A position or a filter: the same field read; as it is only for its first value ([1]), not for
                // a filter (`[. != '']`) or another position, which may give other values than the field's.
                return changedIf(at == RECORD
                        ? resolve(base)
                        : step(at, base), !isFirst(node.get(1)));
            }
            case "*" -> {
                if (at.mode() == Mode.DOCUMENT) {
                    // The held JSON's own map (or array).
                    return new Cursor(at.field(), Mode.HELD_TOP, Style.JSON, at.plain());
                }
                // A field's items: the field read, nothing below it named.
                return at.field() == null || at.mode() == Mode.END
                        ? null
                        : new Cursor(at.field(), Mode.ITEMS, at.style(), at.plain());
            }
            case "(name)" -> {
                return elementStep(at, node.valueText());
            }
            case "@" -> {
                if (node.size() != 1 || !"(name)".equals(node.get(0).symbol())) {
                    return null;
                }
                final String attribute = node.get(0).valueText();
                if (at.style() == Style.DATA && "value".equals(attribute) && at.mode() == Mode.RECORD) {
                    return new Cursor(at.field(), Mode.END, Style.DATA, at.plain());
                }
                if (at.mode() == Mode.RECORD && (at.style() == Style.NONE || at.style() == Style.XML)) {
                    return new Cursor(at.field() == null
                            ? "@" + attribute
                            : at.field() + "/@" + attribute, Mode.END, Style.XML, at.plain());
                }
                return null;
            }
            default -> {
                return null;
            }
        }
    }

    private static Cursor keyStep(final Cursor at, final String key) {
        if (at.mode() == Mode.HELD || at.mode() == Mode.HELD_TOP) {
            return new Cursor(JsonFieldNames.child(at.field(), key), Mode.HELD, Style.JSON, at.plain());
        }
        if (at.mode() == Mode.RECORD && (at.style() == Style.NONE || at.style() == Style.JSON)) {
            return new Cursor(JsonFieldNames.child(at.field() == null
                    ? ""
                    : at.field(), key), Mode.RECORD, Style.JSON, at.plain());
        }
        return null;
    }

    private static Cursor dataStep(final Cursor at, final String name) {
        if (at.mode() == Mode.RECORD && (at.style() == Style.NONE || at.style() == Style.DATA)) {
            return new Cursor(at.field() == null
                    ? name
                    : at.field() + "/" + name, Mode.RECORD, Style.DATA, at.plain());
        }
        return null;
    }

    private static Cursor elementStep(final Cursor at, final String name) {
        if (at.mode() == Mode.RECORD && (at.style() == Style.NONE || at.style() == Style.XML)) {
            return new Cursor(at.field() == null
                    ? name
                    : at.field() + "/" + name, Mode.RECORD, Style.XML, at.plain());
        }
        return null;
    }

    // Whether a predicate is the position 1: a field's first value, as a value-of of the field gives it.
    private static boolean isFirst(final XPathNode predicate) {
        return "(integer)".equals(predicate.symbol()) && "1".equals(predicate.valueText());
    }

    // The cursor, no longer the field as it is where changed (null for none).
    private static Cursor changedIf(final Cursor cursor, final boolean changed) {
        return cursor == null || !changed || !cursor.plain()
                ? cursor
                : new Cursor(cursor.field(), cursor.mode(), cursor.style(), false);
    }

    // The value a predicate tests an attribute for (`@key = 'x'`, either way round), or null.
    private static String tested(final XPathNode predicate, final String attribute) {
        if (!"=".equals(predicate.symbol()) && !"eq".equals(predicate.symbol())) {
            return null;
        }
        if (predicate.size() != 2) {
            return null;
        }
        for (int i = 0; i < 2; i++) {
            final XPathNode side = predicate.get(i);
            final XPathNode other = predicate.get(1 - i);
            if ("@".equals(side.symbol()) && side.size() == 1 && "(name)".equals(side.get(0).symbol())
                && attribute.equals(side.get(0).valueText()) && "(string)".equals(other.symbol())) {
                return other.valueText();
            }
        }
        return null;
    }


    // --------------------------------------------------------------------------------


    // Where a path is: in the record's own tree, the document json-to-xml() gives, the top of the JSON a field
    // holds, a key of it, at a value with nothing named below it, or at a field's items.
    private enum Mode {
        RECORD,
        DOCUMENT,
        HELD_TOP,
        HELD,
        END,
        ITEMS
    }

    // How the record's own steps name its fields, once one is taken.
    private enum Style {
        NONE,
        JSON,
        DATA,
        XML
    }

    // A path's field so far (null for the record itself), where it is, how it names its fields, and whether its
    // value is still the field's as it is (no filter, position or changing function on the way).
    private record Cursor(String field, Mode mode, Style style, boolean plain) {

    }
}
