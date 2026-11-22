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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Whether a field holding XML holds structured data, whose elements the draft maps (xml_fields), or a document (a
/// message's HTML body, a mail, text whose elements differ from record to record), kept as one Data value. Seen in
/// chat 183: a Teams message body (`event.Body.Content`, its sibling `event.Body.ContentType` `html`) read through
/// xml_fields, each part of the HTML mapped as Data, and the records whose HTML wasn't well-formed warned of.
///
/// Read in turn (decided by the user, 2026-11-22):
/// 1. a sibling field (the same parent in the record) whose values name a format: an XML or JSON type
///    ([#STRUCTURED_FORMATS], any `+xml` or `+json` type) is structured data, any other format a document;
/// 2. else HTML: the root in the XHTML namespace, or the root and most of the element names HTML's
///    ([#HTML_ELEMENTS]): a document;
/// 3. else the element structure, measured within each event type (the catalogue's, where there is one; else over
///    the sample): records alike (most of them sharing nearly all their paths with another record) are structured
///    data; records each unlike the others a document, for the agent to confirm with the user; anything between
///    unclear, kept whole too, with samples for the agent to judge from what it knows of the source.
///
/// Only format-level knowledge: format names and HTML's element vocabulary, never a source's field names.
public final class HeldMarkup {

    /// The format names a sibling's value may give that mean structured data: XML and JSON, read through
    /// xml_fields (and json_fields). Any other format named is a document; a new or unknown type falls to a
    /// document too, kept whole, nothing lost.
    public static final Set<String> STRUCTURED_FORMATS = Set.of("xml", "json");
    /// The words alone (not a MIME type) a sibling's value may give naming a format.
    public static final Set<String> FORMAT_WORDS = Set.of("xml", "json", "html", "htm", "xhtml", "text", "plain",
            "plaintext", "rtf", "markdown", "md", "enriched", "richtext");
    /// HTML's element names (HTML5's and the common older ones), lower case.
    public static final Set<String> HTML_ELEMENTS = Set.of("a", "abbr", "acronym", "address", "area", "article",
            "aside", "audio", "b", "base", "bdi", "bdo", "big", "blockquote", "body", "br", "button", "canvas",
            "caption", "center", "cite", "code", "col", "colgroup", "data", "datalist", "dd", "del", "details",
            "dfn", "dialog", "div", "dl", "dt", "em", "embed", "fieldset", "figcaption", "figure", "font", "footer",
            "form", "h1", "h2", "h3", "h4", "h5", "h6", "head", "header", "hgroup", "hr", "html", "i", "iframe",
            "img", "input", "ins", "kbd", "label", "legend", "li", "link", "main", "map", "mark", "menu", "meta",
            "meter", "nav", "noscript", "object", "ol", "optgroup", "option", "output", "p", "param", "picture",
            "pre", "progress", "q", "rp", "rt", "ruby", "s", "samp", "script", "section", "select", "small",
            "source", "span", "strike", "strong", "style", "sub", "summary", "sup", "table", "tbody", "td",
            "template", "textarea", "tfoot", "th", "thead", "time", "title", "tr", "track", "tt", "u", "ul", "var",
            "video", "wbr");
    /// The XHTML namespace: a root in it is HTML.
    public static final String XHTML_NAMESPACE = "http://www.w3.org/1999/xhtml";

    // A MIME type: its top-level type, subtype and any parameters.
    private static final Pattern MIME_TYPE = Pattern.compile(
            "^(application|audio|font|image|message|model|multipart|text|video)/([A-Za-z0-9][\\w.+-]*)\\s*(;.*)?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern WHITE_SPACE = Pattern.compile("\\s+");
    // A sibling names the format when it does so in this share of the records holding the field's XML.
    private static final double MOSTLY = 0.8;
    // Two records are alike when they share this share of their paths (Jaccard); the records are alike when this
    // share of them has another alike, and unlike when no more than VARIED_SHARE has.
    private static final double ALIKE = 0.8;
    private static final double UNIFORM_SHARE = 0.8;
    private static final double VARIED_SHARE = 0.3;
    // The fewest records compared within an event type to call them unlike.
    private static final int FEWEST_VARIED = 3;
    // The most distinct shapes a record is compared with.
    private static final int MOST_SHAPES = 200;
    private static final int SAMPLE_LENGTH = 200;
    private static final int MOST_SAMPLES = 3;

    private HeldMarkup() {
        // Static only.
    }

    /// @param field   A field whose values hold XML.
    /// @param records The records, as the draft reads them.
    /// @param typeOf  The event type each record is of (any value telling them apart; null for none), for its
    ///                structure to be measured within its event type; null to measure it over the records.
    /// @param <R>     The records' type.
    /// @return What the field holds.
    public static <R extends Map<String, ?>> Verdict judge(final String field,
                                                           final List<R> records,
                                                           final Function<? super R, Object> typeOf) {
        final List<R> holding = new ArrayList<>();
        final List<XmlTree.Element> roots = new ArrayList<>();
        for (final R record : records) {
            if (record.get(field) instanceof final String text && ToolText.strip(text).startsWith("<")) {
                holding.add(record);
                roots.add(parsed(text));
            }
        }
        final List<String> samples = samples(holding, field);
        final Verdict byType = byFormatSibling(field, holding);
        if (byType != null) {
            return byType;
        }
        final List<String> html = htmlElements(roots);
        if (html != null) {
            return new Verdict(Kind.HTML, null, null, html, samples.subList(0, Math.min(1, samples.size())));
        }
        final Map<Object, List<Set<String>>> shapes = new LinkedHashMap<>();
        for (int n = 0; n < holding.size(); n++) {
            if (roots.get(n) != null) {
                final Object type = typeOf == null
                        ? null
                        : typeOf.apply(holding.get(n));
                shapes.computeIfAbsent(type == null
                        ? List.of()
                        : type, ignored -> new ArrayList<>()).add(paths(roots.get(n)));
            }
        }
        return byStructure(shapes, samples, typeOf != null);
    }

    /// @param value A value.
    /// @return Whether it names a format (a MIME type, or one of [#FORMAT_WORDS]); when it does, whether it is
    /// structured data's (XML, JSON): true or false, else null.
    public static Boolean structuredFormat(final String value) {
        if (value == null) {
            return null;
        }
        final String text = ToolText.strip(value).toLowerCase(Locale.ROOT);
        final Matcher mime = MIME_TYPE.matcher(text);
        if (mime.matches()) {
            final String subtype = mime.group(2);
            if ("xhtml+xml".equals(subtype)) {
                return false;
            }
            return STRUCTURED_FORMATS.contains(subtype)
                   || STRUCTURED_FORMATS.stream().anyMatch(format -> subtype.endsWith("+" + format));
        }
        if (FORMAT_WORDS.contains(text)) {
            return STRUCTURED_FORMATS.contains(text);
        }
        return null;
    }

    // The verdict a sibling naming the field's format gives, or null when none does.
    private static Verdict byFormatSibling(final String field, final List<? extends Map<String, ?>> holding) {
        if (holding.isEmpty()) {
            return null;
        }
        final List<String> keys = JsonFieldNames.keys(field);
        final List<String> parent = keys.subList(0, keys.size() - 1);
        final Set<String> siblings = new LinkedHashSet<>();
        holding.forEach(record -> record.keySet().stream()
                .filter(name -> !name.equals(field) && sibling(name, parent))
                .forEach(siblings::add));
        for (final String sibling : siblings) {
            int named = 0;
            int structured = 0;
            String example = null;
            for (final Map<String, ?> record : holding) {
                final Boolean format = record.get(sibling) instanceof final String value
                        ? structuredFormat(value)
                        : null;
                if (format != null) {
                    named++;
                    if (format) {
                        structured++;
                    }
                    if (example == null) {
                        example = ToolText.strip((String) record.get(sibling));
                    }
                }
            }
            if (named >= MOSTLY * holding.size()) {
                return structured * 2 > named
                        ? new Verdict(Kind.STRUCTURED, sibling, example, List.of(), List.of())
                        : new Verdict(Kind.DOCUMENT, sibling, example, List.of(), List.of());
            }
        }
        return null;
    }

    // Whether a field's name is one key below the parent's keys (and not a path into held XML).
    private static boolean sibling(final String name, final List<String> parent) {
        final List<String> keys = JsonFieldNames.keys(name);
        return keys.size() == parent.size() + 1
               && keys.subList(0, parent.size()).equals(parent)
               && JsonFieldNames.of(keys).equals(name);
    }

    // The HTML element names most of the parsed values are made of (root and most names HTML's, or the root in
    // XHTML's namespace), or null when they aren't HTML.
    private static List<String> htmlElements(final List<XmlTree.Element> roots) {
        int parsed = 0;
        int html = 0;
        final Set<String> found = new LinkedHashSet<>();
        for (final XmlTree.Element root : roots) {
            if (root == null) {
                continue;
            }
            parsed++;
            final Set<String> names = new LinkedHashSet<>();
            root.iter().forEach(element -> names.add(element.localName()));
            final List<String> htmlNames = names.stream().filter(HeldMarkup::htmlName).toList();
            if (XHTML_NAMESPACE.equals(root.namespace())
                || htmlName(root.localName()) && htmlNames.size() * 2 >= names.size()) {
                html++;
                found.addAll(htmlNames);
            }
        }
        return parsed > 0 && html * 2 > parsed
                ? found.stream().limit(MOST_SAMPLES + 2).toList()
                : null;
    }

    // An HTML element's name, written in lower or upper case (HTML is case-blind; a capitalised name is XML's).
    private static boolean htmlName(final String name) {
        final String lower = name.toLowerCase(Locale.ROOT);
        return HTML_ELEMENTS.contains(lower)
               && (name.equals(lower) || name.equals(name.toUpperCase(Locale.ROOT)));
    }

    // Alike, unlike, or unclear: each record's paths compared with the other records' of its event type.
    private static Verdict byStructure(final Map<Object, List<Set<String>>> shapes,
                                       final List<String> samples,
                                       final boolean byType) {
        int compared = 0;
        int alike = 0;
        for (final List<Set<String>> ofType : shapes.values()) {
            if (ofType.size() < 2) {
                continue;
            }
            final Map<Set<String>, Integer> counted = new LinkedHashMap<>();
            ofType.forEach(shape -> counted.merge(shape, 1, Integer::sum));
            final List<Set<String>> distinct = counted.keySet().stream().limit(MOST_SHAPES).toList();
            for (final Set<String> shape : ofType) {
                compared++;
                if (counted.get(shape) > 1 || distinct.stream()
                        .anyMatch(other -> other != shape && !other.equals(shape)
                                           && jaccard(shape, other) >= ALIKE)) {
                    alike++;
                }
            }
        }
        if (compared >= 2 && alike >= UNIFORM_SHARE * compared) {
            return new Verdict(Kind.STRUCTURED, null, null, List.of(), List.of());
        }
        if (compared >= FEWEST_VARIED && alike <= VARIED_SHARE * compared) {
            return new Verdict(byType
                    ? Kind.VARIED_IN_TYPE
                    : Kind.VARIED, null, null, List.of(), samples);
        }
        return new Verdict(Kind.UNCLEAR, null, null, List.of(), samples);
    }

    private static double jaccard(final Set<String> one, final Set<String> other) {
        final Set<String> both = new HashSet<>(one);
        both.retainAll(other);
        final int either = one.size() + other.size() - both.size();
        return either == 0
                ? 1
                : (double) both.size() / either;
    }

    // A record's element paths from the root, and its attributes' (`path/@name`), by local name.
    private static Set<String> paths(final XmlTree.Element root) {
        final Set<String> out = new HashSet<>();
        final Map<XmlTree.Element, String> at = new HashMap<>();
        for (final XmlTree.Element element : root.iter()) {
            final String path = element.parent() == null
                    ? element.localName()
                    : at.get(element.parent()) + "/" + element.localName();
            at.put(element, path);
            out.add(path);
            element.attrib().keySet().forEach(name -> out.add(path + "/@" + (name.startsWith("{")
                    ? name.substring(name.indexOf('}') + 1)
                    : name)));
        }
        return out;
    }

    private static XmlTree.Element parsed(final String text) {
        try {
            return XmlTree.parse(ToolText.strip(text));
        } catch (final XmlTree.XmlException e) {
            return null;
        }
    }

    // The field's first distinct values, white space made single spaces, each cut.
    private static List<String> samples(final List<? extends Map<String, ?>> holding, final String field) {
        final Set<String> out = new LinkedHashSet<>();
        for (final Map<String, ?> record : holding) {
            final String text = WHITE_SPACE.matcher(ToolText.strip((String) record.get(field))).replaceAll(" ");
            out.add(ToolText.len(text) > SAMPLE_LENGTH
                    ? ToolText.head(text, SAMPLE_LENGTH) + "..."
                    : text);
            if (out.size() == MOST_SAMPLES) {
                break;
            }
        }
        return List.copyOf(out);
    }


    // --------------------------------------------------------------------------------


    /// What a held field's XML is.
    public enum Kind {
        /// Structured data: its elements are mapped (xml_fields).
        STRUCTURED,
        /// A document, as a sibling naming its format says: kept whole.
        DOCUMENT,
        /// HTML: kept whole.
        HTML,
        /// Elements differing from record to record: kept whole, for the user to confirm.
        VARIED,
        /// Elements differing from record to record within the catalogue's event types: as [#VARIED].
        VARIED_IN_TYPE,
        /// Neither clearly: kept whole, for the agent to judge from samples.
        UNCLEAR
    }

    /// What a held field's XML is, and why.
    ///
    /// @param kind      What it is.
    /// @param sibling   The sibling naming its format, or null.
    /// @param format    The format the sibling names (its first value), or null.
    /// @param elements  The HTML element names found ([Kind#HTML]), else empty.
    /// @param samples   Its values to show the agent, white space collapsed and each cut; empty when none are.
    public record Verdict(Kind kind, String sibling, String format, List<String> elements, List<String> samples) {

        /// @return Whether its elements are mapped (xml_fields).
        public boolean mapped() {
            return kind == Kind.STRUCTURED;
        }

        /// @return Why it is kept whole, in a few words, or null for one mapped.
        public String reason() {
            return switch (kind) {
                case STRUCTURED -> null;
                case DOCUMENT -> ToolText.repr(sibling) + " names its format: " + ToolText.repr(format);
                case HTML -> "HTML";
                case VARIED, VARIED_IN_TYPE -> "its elements differ from record to record";
                case UNCLEAR -> "the sample can't tell a document from structured data";
            };
        }

        /// @param field The field.
        /// @return The draft's note on a field kept whole, or null for one mapped.
        public String note(final String field) {
            final String name = ToolText.repr(field);
            final String ask = " Its parts are mapped only if the user asks: then give " + name
                               + " in xml_fields (changes=) with entries reading its parts ('" + field
                               + "/<path>').";
            final String shown = samples.isEmpty()
                    ? ""
                    : " " + (samples.size() == 1
                            ? "Sample"
                            : "Samples") + ": " + String.join(" | ", samples.stream().map(ToolText::repr).toList())
                      + ".";
            return switch (kind) {
                case STRUCTURED -> null;
                case DOCUMENT -> name + " holds a document, not structured data (" + ToolText.repr(sibling)
                                 + " names its format: " + ToolText.repr(format) + "): kept as one Data value."
                                 + ask;
                case HTML -> name + " holds HTML (" + String.join(", ", elements) + "), a document, not "
                             + "structured data: kept as one Data value." + ask + shown
                             + " Should it be structured data after all, give it in xml_fields the same way.";
                case VARIED, VARIED_IN_TYPE -> name + " holds XML whose elements differ from record to record"
                               + (kind == Kind.VARIED_IN_TYPE
                        ? " of one event type"
                        : "") + ": likely a document, so kept as one Data value; confirm with the user "
                               + "whether it is a document or structured data. If structured data, give it in "
                               + "xml_fields (changes=) with entries reading its parts ('" + field + "/<path>')."
                               + shown;
                case UNCLEAR -> name + " holds XML the sample can't tell as a document or structured data: kept "
                                + "as one Data value." + shown + " Judge from what you know of the source: "
                                + "structured data (a SOAP envelope, an XML event) is given in xml_fields "
                                + "(changes=) with entries reading its parts ('" + field + "/<path>'); if you "
                                + "can't tell, ask the user.";
            };
        }
    }
}
