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

package stroom.ai.impl.tools.validation;

import stroom.ai.impl.tools.ToolText;

import org.w3c.dom.Element;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/// What the event-logging XSD allows below Event: element order, required elements, choices and values.
///
/// The checks use it to say which elements an XSLT writes where the schema has no place for them, and what an
/// element takes; the translation generator uses it to put elements in schema order, to reject paths the schema
/// does not have (with suggestions), and to check constants against enumerations.
///
/// It is read from the XSD's own text once, and everything it gives is worked out then, for every element below
/// Event (its children, whether it is a leaf, its type, patterns, values and description, and the paths below
/// Event), into maps that are never changed after: one is shared between threads ([HeldSchemas] keeps one per
/// version), and a DOM, which Xerces reads lazily (deferred node expansion), is not safe to read from several
/// threads at once. The declarations it gives ([#event()], [Child#decl()]) are handles to pass back to it; it
/// never reads them as DOM nodes again, except for one it didn't give (read under a lock).
public final class EventSchema {

    /// The XML Schema namespace.
    public static final String XS = "http://www.w3.org/2001/XMLSchema";

    private static final Pattern SENTENCE_END = Pattern.compile("(?<!e\\.g)(?<!i\\.e)\\.\\s+(?=[A-Z])");
    private static final Pattern WILD_STEP = Pattern.compile("[\\w.-]+|\\*|@[\\w.-]+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern PREDICATE = Pattern.compile("\\[[^\\]]*]");

    private final String version;
    private final Element event;
    // Read only under its own lock, for a declaration that isn't one of the schema's own.
    private final Reader reader;
    private final Map<Element, Decl> decls;
    private final Map<Integer, List<String>> requiredChoices;
    private final Map<String, List<String>> paths;
    private final Map<String, List<String>> values;

    private EventSchema(final Element root) {
        reader = new Reader(root);
        version = reader.version;
        event = reader.event;
        decls = Collections.unmodifiableMap(reader.everyDecl(event));
        requiredChoices = copied(reader.requiredChoices);
        final Map<String, List<String>> foundPaths = new LinkedHashMap<>();
        final Map<String, List<String>> foundValues = new LinkedHashMap<>();
        index(foundPaths, foundValues);
        paths = copied(foundPaths);
        values = copied(foundValues);
    }

    /// @param xsd The event-logging XSD's text.
    /// @return What it allows.
    /// @throws XmlTrees.NotWellFormedException if it is not well-formed.
    /// @throws IllegalArgumentException        if it declares no Events holding Event.
    public static EventSchema parse(final String xsd) {
        return new EventSchema(XmlTrees.parse(xsd));
    }

    /// @return The schema's version attribute, e.g. `4.1.0`, or null.
    public String version() {
        return version;
    }

    /// @return The declaration of Event.
    public Element event() {
        return event;
    }

    /// @return Each required choice's id, with the names of its members: one of them must be given.
    public Map<Integer, List<String>> requiredChoices() {
        return requiredChoices;
    }

    /// @param decl An element's declaration (local, or a reference to a global one).
    /// @return The elements it may hold, in its content model's order, each once.
    public List<Child> children(final Element decl) {
        return decl(decl).children();
    }

    /// The schema's description of the last element of a path (as [#resolve(String)] gives it): the first
    /// sentence of its own documentation, else its parent's (User for User/Id), else its type's.
    ///
    /// @param chain A path's chain of children.
    /// @return The description, or "" if there is none.
    public String describe(final List<Child> chain) {
        final Decl last = decl(chain.getLast().decl());
        final List<String> candidates = new ArrayList<>();
        candidates.add(last.sentence());
        if (chain.size() > 1) {
            candidates.add(decl(chain.get(chain.size() - 2).decl()).sentence());
        }
        candidates.add(last.complexSentence());
        candidates.add(last.simpleSentence());
        return candidates.stream()
                .filter(sentence -> !sentence.isEmpty())
                .findFirst()
                .orElse("");
    }

    /// @param decl An element's declaration.
    /// @return True if it holds text rather than elements.
    public boolean isLeaf(final Element decl) {
        return decl(decl).leaf();
    }

    /// @param decl A leaf's declaration.
    /// @return The built-in XSD type it ends up as, e.g. `dateTime`, `boolean`, `string`, or null.
    public String baseType(final Element decl) {
        return decl(decl).baseType();
    }

    /// @param decl A leaf's declaration.
    /// @return The XSD patterns its value must match (any one of them), from its simple type or the nearest type
    /// it restricts that has some; empty when it has none.
    public List<String> patterns(final Element decl) {
        return decl(decl).patterns();
    }

    /// @param decl A leaf's declaration.
    /// @return The values it allows, or null if it is not an enumeration.
    public List<String> enumeration(final Element decl) {
        return decl(decl).enumeration();
    }

    /// @param decl An element's declaration.
    /// @return The name of the complex type it is declared with, e.g. `UserComplexType`, or null for an inline
    /// type or a leaf.
    public String typeName(final Element decl) {
        return decl(decl).typeName();
    }

    /// @param decl An element's declaration.
    /// @return True if it is one of the objects a collection of them holds (the members of MultiObjectComplexType:
    /// Object, Folder, File, Email...).
    public boolean isObject(final Element decl) {
        return decl(decl).object();
    }

    /// A step that says which side of its parent what is below it is on: one of the parent's children that holds
    /// elements, where another of them holds the same type (a Move's Source and Destination, an Update's Before and
    /// After, an Authorise's AddGroups and RemoveGroups, a Network's Connect and Close).
    ///
    /// @param parent The parent's declaration.
    /// @param child  One of its children.
    /// @return True if the child is such a side.
    public boolean isSide(final Element parent, final Child child) {
        final Decl self = decl(child.decl());
        if (self.leaf() || self.children().isEmpty()) {
            return false;
        }
        return children(parent).stream()
                .filter(other -> !other.name().equals(child.name()))
                .map(other -> decl(other.decl()))
                .anyMatch(other -> !other.leaf() && !other.children().isEmpty()
                                   && other.typeKey().equals(self.typeKey()));
    }

    /// @param path A path below Event, e.g. `EventSource/User/Id` (a leading `Event/` is allowed).
    /// @return The chain of children it walks.
    /// @throws IllegalArgumentException if the schema has no such path, saying what is allowed where it stops,
    /// what it may have meant, where else the name exists, and which leaf takes it as a value.
    public List<Child> resolve(final String path) {
        final List<Child> chain = new ArrayList<>();
        Element decl = event;
        String walked = "Event";
        for (final String segment : segments(path)) {
            if ("Event".equals(segment) && chain.isEmpty()) {
                continue;
            }
            final List<Child> options = children(decl);
            final Child child = options.stream()
                    .filter(option -> option.name().equals(segment))
                    .findFirst()
                    .orElse(null);
            if (child == null) {
                throw new IllegalArgumentException(noChild(walked, segment, options));
            }
            chain.add(child);
            decl = child.decl();
            walked = walked + "/" + segment;
        }
        return chain;
    }

    /// Whether a path below Event can select several values in one event: an element on it the schema lets
    /// repeat (Data, say), under a wildcard step any of the elements it stands for. A path the schema doesn't
    /// know, or an expression rather than a path, counts as one value.
    ///
    /// @param path A path below Event.
    /// @return True if it can select several values.
    public boolean canRepeat(final String path) {
        final String first = path.split(" ! ", -1)[0];
        final List<String> steps = segments(PREDICATE.matcher(first).replaceAll(""));
        if (steps.isEmpty() || steps.stream().anyMatch(step -> !WILD_STEP.matcher(step).matches())) {
            return false;
        }
        List<Element> current = List.of(event);
        for (final String step : steps) {
            if ("Event".equals(step) && current.size() == 1 && current.getFirst() == event) {
                continue;
            }
            if (step.startsWith("@")) {
                break;
            }
            final List<Child> children = current.stream()
                    .flatMap(decl -> children(decl).stream())
                    .filter(child -> "*".equals(step) || child.name().equals(step))
                    .toList();
            if (children.isEmpty()) {
                return false;
            }
            if (children.stream().anyMatch(Child::repeatable)) {
                return true;
            }
            current = children.stream().map(Child::decl).toList();
        }
        return false;
    }

    /// @param name An element's name.
    /// @return The paths below Event whose last element is that name, shortest first.
    public List<String> pathsNamed(final String name) {
        return paths.getOrDefault(name, List.of());
    }

    /// @param value A value.
    /// @return The leaf paths whose enumeration allows it, e.g. `Logon` gives
    /// `Event/EventDetail/Authenticate/Action`.
    public List<String> pathsWithValue(final String value) {
        return values.getOrDefault(value, List.of());
    }

    // What the schema worked out for a declaration it gave; one it didn't is read from the DOM, under the lock.
    private Decl decl(final Element decl) {
        final Decl known = decls.get(decl);
        if (known != null) {
            return known;
        }
        synchronized (reader) {
            return reader.decl(decl);
        }
    }

    private String noChild(final String walked, final String segment, final List<Child> options) {
        final List<String> names = options.stream().map(Child::name).toList();
        final List<String> close = CloseMatches.closeMatches(segment, names, CloseMatches.DEFAULT_COUNT,
                CloseMatches.DEFAULT_CUTOFF);
        final List<String> named = pathsNamed(segment);
        final List<String> elsewhere = Stream.concat(
                        named.stream().filter(p -> p.startsWith(walked + "/")),
                        named.stream().filter(p -> !p.startsWith(walked + "/")))
                .limit(4)
                .toList();
        final List<String> valueOf = pathsWithValue(segment).stream().limit(3).toList();
        return "'" + walked + "' has no child '" + segment + "'."
               + (close.isEmpty()
                ? ""
                : " Did you mean " + ToolText.list(close) + "?")
               + (elsewhere.isEmpty()
                ? ""
                : " '" + segment + "' exists at: " + ToolText.list(elsewhere) + ".")
               + (valueOf.isEmpty()
                ? ""
                : " '" + segment + "' is a value of: " + ToolText.list(valueOf) + " (map a constant to it).")
               + " Allowed here: " + ToolText.list(names);
    }

    // The paths below Event (six levels down) by their last element's name, and the leaf paths by the values
    // their enumerations allow.
    private void index(final Map<String, List<String>> foundPaths, final Map<String, List<String>> foundValues) {
        final Deque<Queued> queue = new ArrayDeque<>();
        queue.add(new Queued(event, "Event", 0));
        while (!queue.isEmpty()) {
            final Queued next = queue.removeFirst();
            for (final Child child : children(next.decl())) {
                final String here = next.path() + "/" + child.name();
                foundPaths.computeIfAbsent(child.name(), k -> new ArrayList<>()).add(here);
                if (isLeaf(child.decl())) {
                    for (final String value : Objects.requireNonNullElse(enumeration(child.decl()),
                            List.<String>of())) {
                        foundValues.computeIfAbsent(value, k -> new ArrayList<>()).add(here);
                    }
                }
                if (next.level() < 6) {
                    queue.add(new Queued(child.decl(), here, next.level() + 1));
                }
            }
        }
    }

    // A map that can't be changed, of lists that can't be changed, in the same order.
    private static <K, V> Map<K, List<V>> copied(final Map<K, List<V>> map) {
        final Map<K, List<V>> copy = new LinkedHashMap<>();
        map.forEach((key, list) -> copy.put(key, List.copyOf(list)));
        return Collections.unmodifiableMap(copy);
    }

    private static List<String> segments(final String path) {
        final List<String> segments = new ArrayList<>();
        for (final String segment : path.split("/", -1)) {
            if (!segment.isEmpty()) {
                segments.add(segment);
            }
        }
        return segments;
    }


    // --------------------------------------------------------------------------------


    /// An element a content model allows.
    ///
    /// @param name       Its name.
    /// @param decl       Its declaration (local, or the global one it refers to).
    /// @param index      Its position in the parent's content model.
    /// @param required   True if it must appear (not optional, not one of a choice).
    /// @param repeatable True if it may appear more than once.
    /// @param choice     The id of the one-of choice it belongs to, or null.
    public record Child(String name, Element decl, int index, boolean required, boolean repeatable,
                        Integer choice) {

    }

    // What the schema gives for one declaration, worked out once.
    private record Decl(List<Child> children,
                        boolean leaf,
                        String typeName,
                        String typeKey,
                        boolean object,
                        String baseType,
                        List<String> patterns,
                        List<String> enumeration,
                        String sentence,
                        String complexSentence,
                        String simpleSentence) {

    }

    private record Found(String name, Element decl, boolean required, boolean repeatable, Integer choice) {

    }

    private record Queued(Element decl, String path, int level) {

    }

    // Reads the XSD's DOM: only while the schema is made, and then only under its lock.
    private static final class Reader {

        private final String version;
        private final Map<String, Element> types = new LinkedHashMap<>();
        private final Map<String, Element> simple = new LinkedHashMap<>();
        private final Map<String, Element> groups = new LinkedHashMap<>();
        private final Map<String, Element> elements = new LinkedHashMap<>();
        private final Map<Element, List<Child>> childrenOf = new IdentityHashMap<>();
        private final Map<Element, Integer> choiceIds = new IdentityHashMap<>();
        private final Map<Integer, List<String>> requiredChoices = new LinkedHashMap<>();
        // The objects a collection of them holds: MultiObjectComplexType's members.
        private final Set<Element> objects = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Element event;

        private Reader(final Element root) {
            version = XmlTrees.attribute(root, "version");
            for (final Element child : XmlTrees.children(root)) {
                if (!XS.equals(child.getNamespaceURI())) {
                    continue;
                }
                final String name = child.getAttribute("name");
                switch (child.getLocalName()) {
                    case "complexType" -> types.putIfAbsent(name, child);
                    case "simpleType" -> simple.putIfAbsent(name, child);
                    case "group" -> groups.putIfAbsent(name, child);
                    case "element" -> elements.putIfAbsent(name, child);
                    default -> {
                        // Nothing else is read.
                    }
                }
            }
            final Element events = elements.get("Events");
            if (events == null) {
                throw new IllegalArgumentException("It is not an event-logging schema: it declares no Events "
                                                   + "element");
            }
            event = children(events).stream()
                    .filter(child -> "Event".equals(child.name()))
                    .findFirst()
                    .map(Child::decl)
                    .orElseThrow(() -> new IllegalArgumentException("Its Events element holds no Event"));
            final Element multiObject = types.get("MultiObjectComplexType");
            if (multiObject != null) {
                final List<Found> members = new ArrayList<>();
                walkType(multiObject, members, null, false);
                members.forEach(member -> objects.add(resolve(member.decl())));
            }
        }

        // Every declaration below Event (Event's own included), with what the schema gives for it.
        private Map<Element, Decl> everyDecl(final Element from) {
            final Map<Element, Decl> found = new IdentityHashMap<>();
            final Deque<Element> queue = new ArrayDeque<>();
            queue.add(from);
            while (!queue.isEmpty()) {
                final Element next = queue.removeFirst();
                if (found.containsKey(next)) {
                    continue;
                }
                final Decl decl = decl(next);
                found.put(next, decl);
                decl.children().forEach(child -> queue.add(child.decl()));
            }
            return found;
        }

        private Decl decl(final Element decl) {
            final Element resolved = resolve(decl);
            final Element complexType = complex(resolved);
            final String typeName = complexType != null && xs(resolved, "complexType") == null
                    ? local(XmlTrees.attribute(resolved, "type"))
                    : null;
            return new Decl(children(resolved),
                    complexType == null || xs(complexType, "simpleContent") != null,
                    typeName,
                    typeName != null
                            ? typeName
                            : inlineKey(resolved),
                    objects.contains(resolved),
                    baseType(resolved),
                    patterns(resolved),
                    enumeration(resolved),
                    firstSentence(resolved),
                    firstSentence(complexType),
                    firstSentence(simpleType(resolved)));
        }

        private List<Child> children(final Element decl) {
            final Element resolved = resolve(decl);
            final List<Child> known = childrenOf.get(resolved);
            if (known != null) {
                return known;
            }
            final List<Found> found = new ArrayList<>();
            final Element complexType = complex(resolved);
            if (complexType != null) {
                walkType(complexType, found, null, false);
            }
            final Set<String> seen = new HashSet<>();
            final List<Child> out = new ArrayList<>();
            for (final Found entry : found) {
                if (seen.add(entry.name())) {
                    out.add(new Child(entry.name(), resolve(entry.decl()), out.size(), entry.required(),
                            entry.repeatable(), entry.choice()));
                }
            }
            final List<Child> children = List.copyOf(out);
            childrenOf.put(resolved, children);
            return children;
        }

        // An inline type by what it holds: each child's name and declared type, so two declared alike (an
        // Authorise's AddGroups and RemoveGroups, each with its own documentation) are the same.
        private String inlineKey(final Element resolved) {
            final StringBuilder key = new StringBuilder("inline:");
            for (final Child child : children(resolved)) {
                key.append(child.name()).append('=')
                        .append(Objects.requireNonNullElse(XmlTrees.attribute(child.decl(), "type"), ""))
                        .append(child.required()
                                ? "!"
                                : "?")
                        .append(child.repeatable()
                                ? "*"
                                : "")
                        .append(';');
            }
            return key.toString();
        }

        private String baseType(final Element resolved) {
            Element simpleType = simpleType(resolved);
            String name = local(XmlTrees.attribute(resolved, "type"));
            for (int i = 0; i < 10; i++) {
                if (simpleType == null) {
                    return name;
                }
                final Element restriction = xs(simpleType, "restriction");
                if (restriction == null) {
                    return "string";
                }
                name = local(XmlTrees.attribute(restriction, "base"));
                simpleType = simple.get(name);
            }
            return name;
        }

        private List<String> patterns(final Element decl) {
            Element simpleType = simpleType(decl);
            for (int i = 0; i < 10; i++) {
                if (simpleType == null) {
                    return List.of();
                }
                final Element restriction = xs(simpleType, "restriction");
                if (restriction == null) {
                    return List.of();
                }
                final List<String> found = xsAll(restriction, "pattern").stream()
                        .map(pattern -> pattern.getAttribute("value"))
                        .filter(value -> !value.isEmpty())
                        .toList();
                if (!found.isEmpty()) {
                    return found;
                }
                simpleType = simple.get(local(XmlTrees.attribute(restriction, "base")));
            }
            return List.of();
        }

        private List<String> enumeration(final Element decl) {
            Element simpleType = simpleType(decl);
            for (int i = 0; i < 10; i++) {
                if (simpleType == null) {
                    return null;
                }
                final Element restriction = xs(simpleType, "restriction");
                if (restriction == null) {
                    return null;
                }
                final List<String> found = xsAll(restriction, "enumeration").stream()
                        .map(value -> value.getAttribute("value"))
                        .toList();
                if (!found.isEmpty()) {
                    return found;
                }
                simpleType = simple.get(local(XmlTrees.attribute(restriction, "base")));
            }
            return null;
        }

        private void walkType(final Element complexType,
                              final List<Found> out,
                              final Integer choice,
                              final boolean optional) {
            for (final Element node : XmlTrees.children(complexType)) {
                final String tag = xsName(node);
                if ("complexContent".equals(tag) || "simpleContent".equals(tag)) {
                    for (final Element derivation : XmlTrees.children(node)) {
                        final Element base = types.get(local(XmlTrees.attribute(derivation, "base")));
                        if ("extension".equals(xsName(derivation)) && base != null) {
                            walkType(base, out, choice, optional);
                        }
                        walkType(derivation, out, choice, optional);
                    }
                } else if (List.of("sequence", "choice", "all", "group", "element").contains(tag)) {
                    particle(node, out, choice, optional);
                }
            }
        }

        private void particle(final Element node,
                              final List<Found> out,
                              final Integer choice,
                              final boolean optional) {
            final boolean isOptional = optional || Integer.valueOf(0).equals(occurs(node, "minOccurs"));
            final String tag = xsName(node);
            if ("element".equals(tag)) {
                final String name = node.hasAttribute("name")
                        ? node.getAttribute("name")
                        : local(node.getAttribute("ref"));
                final Integer maximum = occurs(node, "maxOccurs");
                out.add(new Found(name, node, !isOptional && choice == null, maximum == null || maximum > 1,
                        choice));
            } else if ("sequence".equals(tag) || "all".equals(tag)) {
                for (final Element child : XmlTrees.children(node)) {
                    particle(child, out, choice, isOptional);
                }
            } else if ("choice".equals(tag)) {
                // One of (maxOccurs 1), or, when the choice repeats, any number of its members; either way a
                // required choice needs at least one member, and no member is required on its own.
                final boolean exclusive = Integer.valueOf(1).equals(occurs(node, "maxOccurs"));
                final Integer id = exclusive || choice == null
                        ? choiceIds.computeIfAbsent(node, k -> choiceIds.size())
                        : choice;
                final int before = out.size();
                for (final Element child : XmlTrees.children(node)) {
                    particle(child, out, id, isOptional);
                }
                if (!exclusive) {
                    // Members of a repeating choice can each appear many times.
                    for (int i = before; i < out.size(); i++) {
                        final Found member = out.get(i);
                        out.set(i, new Found(member.name(), member.decl(), false, true, member.choice()));
                    }
                }
                if (!isOptional && choice == null) {
                    requiredChoices.put(id, out.subList(before, out.size()).stream().map(Found::name).toList());
                }
            } else if ("group".equals(tag)) {
                final Element group = node.hasAttribute("ref")
                        ? groups.get(local(node.getAttribute("ref")))
                        : node;
                if (group != null) {
                    for (final Element child : XmlTrees.children(group)) {
                        final String childTag = xsName(child);
                        if ("sequence".equals(childTag) || "choice".equals(childTag) || "all".equals(childTag)) {
                            particle(child, out, choice, isOptional);
                        }
                    }
                }
            }
        }

        private Element resolve(final Element decl) {
            if (decl.hasAttribute("ref")) {
                return elements.getOrDefault(local(decl.getAttribute("ref")), decl);
            }
            return decl;
        }

        private Element complex(final Element decl) {
            final Element resolved = resolve(decl);
            final Element inline = xs(resolved, "complexType");
            return inline != null
                    ? inline
                    : types.get(local(XmlTrees.attribute(resolved, "type")));
        }

        private Element simpleType(final Element decl) {
            final Element resolved = resolve(decl);
            final Element inline = xs(resolved, "simpleType");
            return inline != null
                    ? inline
                    : simple.get(local(XmlTrees.attribute(resolved, "type")));
        }

        private static String firstSentence(final Element node) {
            if (node == null) {
                return "";
            }
            final Element annotation = xs(node, "annotation");
            final Element documentation = annotation == null
                    ? null
                    : xs(annotation, "documentation");
            final String text = documentation == null
                    ? ""
                    : String.join(" ", XmlTrees.text(documentation).split("\\s+")).strip();
            if (text.isEmpty()) {
                return "";
            }
            // A sentence ends at a full stop before a capital, not at 'e.g.' or 'i.e.'.
            final String first = SENTENCE_END.split(text, 2)[0];
            return stripTrailingFullStops(first) + ".";
        }

        private static String stripTrailingFullStops(final String text) {
            int end = text.length();
            while (end > 0 && text.charAt(end - 1) == '.') {
                end--;
            }
            return text.substring(0, end);
        }

        private static Integer occurs(final Element node, final String attribute) {
            final String value = node.hasAttribute(attribute)
                    ? node.getAttribute(attribute)
                    : "1";
            return "unbounded".equals(value)
                    ? null
                    : Integer.valueOf(value);
        }

        private static String local(final String qName) {
            if (qName == null || qName.isEmpty()) {
                return null;
            }
            final int colon = qName.lastIndexOf(':');
            return colon >= 0
                    ? qName.substring(colon + 1)
                    : qName;
        }

        private static String xsName(final Element element) {
            return XS.equals(element.getNamespaceURI())
                    ? element.getLocalName()
                    : "";
        }

        private static Element xs(final Element parent, final String localName) {
            return XmlTrees.children(parent).stream()
                    .filter(child -> localName.equals(xsName(child)))
                    .findFirst()
                    .orElse(null);
        }

        private static List<Element> xsAll(final Element parent, final String localName) {
            return XmlTrees.children(parent).stream()
                    .filter(child -> localName.equals(xsName(child)))
                    .toList();
        }
    }
}
