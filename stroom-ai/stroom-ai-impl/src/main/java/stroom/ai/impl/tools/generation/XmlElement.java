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

import stroom.ai.impl.tools.validation.XmlTrees;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// An XML element of the generator's tree (see [XmlNode]), with these rules where the serialised text depends on it:
///
/// - an element made on its own ([#create(String, String)]) starts a document of its own, and declares its
///   namespace itself, with the usual prefix for it (`xsl` for XSLT, `xs`, `xsi`) or the next `ns0`, `ns1`, ... of
///   that document;
/// - an element added below another ([#addElement(String, String)]) uses a declaration in scope there, or declares
///   one on itself;
/// - an element moved into another ([#append(XmlNode)], [#insert(int, XmlNode)]) drops the declarations its new
///   place already has, and takes their prefixes;
/// - attributes keep the order they were set in, and setting one again keeps its place.
public final class XmlElement extends XmlNode {

    private static final Map<String, String> DEFAULT_PREFIXES = Map.of(
            "http://www.w3.org/XML/1998/namespace", "xml",
            "http://www.w3.org/1999/xhtml", "html",
            XmlTrees.XSL, "xsl",
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#", "rdf",
            "http://schemas.xmlsoap.org/wsdl/", "wsdl",
            "http://www.w3.org/2001/XMLSchema", "xs",
            "http://www.w3.org/2001/XMLSchema-instance", "xsi",
            "http://purl.org/dc/elements/1.1/", "dc",
            "http://codespeak.net/lxml/objectify/pytype", "py");

    private final String namespace;
    private final String localName;
    private final List<XmlNamespace> declarations = new ArrayList<>();
    private final List<Attribute> attributes = new ArrayList<>();
    private final List<XmlNode> children = new ArrayList<>();
    private Owner owner;
    private XmlNamespace binding;
    private String text;

    private XmlElement(final Owner owner, final String namespace, final String localName) {
        this.owner = owner;
        this.namespace = namespace == null || namespace.isEmpty()
                ? null
                : namespace;
        this.localName = Objects.requireNonNull(localName, "localName");
    }

    /// An element on its own, as `etree.Element('{namespace}localName')` makes one.
    ///
    /// @param namespace Its namespace URI, or null for none.
    /// @param localName Its local name.
    /// @return The element, in a document of its own.
    public static XmlElement create(final String namespace, final String localName) {
        final XmlElement element = new XmlElement(new Owner(), namespace, localName);
        if (element.namespace != null) {
            element.binding = element.findOrBuild(element.namespace, null, false);
        }
        return element;
    }

    /// An element on its own declaring namespaces, as `etree.Element(tag, nsmap=...)` makes one.
    ///
    /// @param namespace  Its namespace URI, or null for none.
    /// @param localName  Its local name.
    /// @param namespaces Prefix (null for the default namespace) to URI, declared in this order.
    /// @return The element, in a document of its own.
    public static XmlElement create(final String namespace,
                                    final String localName,
                                    final Map<String, String> namespaces) {
        final XmlElement element = new XmlElement(new Owner(), namespace, localName);
        for (final Map.Entry<String, String> entry : namespaces.entrySet()) {
            XmlNamespace declared = element.searchByPrefix(entry.getKey());
            if (declared == null || !declared.uri().equals(entry.getValue())) {
                declared = new XmlNamespace(entry.getKey(), entry.getValue());
                element.declarations.add(declared);
            }
            if (element.binding == null && entry.getValue().equals(element.namespace)) {
                element.binding = declared;
            }
        }
        if (element.binding == null && element.namespace != null) {
            element.binding = element.findOrBuild(element.namespace, null, false);
        }
        return element;
    }

    /// @param owner     The document being parsed.
    /// @param namespace The element's namespace URI, or null.
    /// @param localName Its local name.
    /// @return An element as read, its namespace bound once its declarations are read.
    static XmlElement parsed(final Owner owner, final String namespace, final String localName) {
        return new XmlElement(owner, namespace, localName);
    }

    /// Adds an element as this one's last child, as `etree.SubElement(this, '{namespace}localName')` does.
    ///
    /// @param namespace Its namespace URI, or null for none.
    /// @param localName Its local name.
    /// @return The new element.
    public XmlElement addElement(final String namespace, final String localName) {
        final XmlElement child = new XmlElement(owner, namespace, localName);
        child.setParent(this);
        children.add(child);
        if (child.namespace != null) {
            child.binding = child.findOrBuild(child.namespace, null, false);
        }
        return child;
    }

    /// Adds an element as this one's last child, with attributes.
    ///
    /// @param namespace  Its namespace URI, or null for none.
    /// @param localName  Its local name.
    /// @param attributes Attribute names and values, in turn (no namespace).
    /// @return The new element.
    public XmlElement addElement(final String namespace, final String localName, final String... attributes) {
        final XmlElement child = addElement(namespace, localName);
        for (int i = 0; i + 1 < attributes.length; i += 2) {
            child.set(attributes[i], attributes[i + 1]);
        }
        return child;
    }

    /// @param namespace  Its namespace URI, or null for none.
    /// @param localName  Its local name.
    /// @param attributes Attribute names and values, in turn (no namespace).
    /// @return A new element on its own, with the attributes.
    public static XmlElement create(final String namespace, final String localName, final String... attributes) {
        final XmlElement element = create(namespace, localName);
        for (int i = 0; i + 1 < attributes.length; i += 2) {
            element.set(attributes[i], attributes[i + 1]);
        }
        return element;
    }

    /// @return The element's namespace URI, or null.
    public String namespace() {
        return namespace;
    }

    /// @return The element's local name.
    public String localName() {
        return localName;
    }

    /// @return Its tag: `{namespace}localName`, or the local name alone.
    public String tag() {
        return namespace == null
                ? localName
                : "{" + namespace + "}" + localName;
    }

    /// @param namespace A namespace URI, or null.
    /// @param localName A local name.
    /// @return True if this element has that name.
    public boolean is(final String namespace, final String localName) {
        return Objects.equals(this.namespace, namespace) && this.localName.equals(localName);
    }

    /// @return The declaration the element's name is written with, or null for no namespace.
    public XmlNamespace binding() {
        return binding;
    }

    /// @return The namespaces declared on this element, in order.
    public List<XmlNamespace> declarations() {
        return Collections.unmodifiableList(declarations);
    }

    /// @return Each namespace in scope here, by prefix (null for the default): the outermost declarations first, a
    /// nearer one of the same prefix replacing its URI.
    public Map<String, String> namespaces() {
        final Map<String, String> found = new LinkedHashMap<>();
        final List<XmlElement> chain = new ArrayList<>();
        for (XmlElement at = this; at != null; at = at.parent()) {
            chain.addFirst(at);
        }
        for (final XmlElement at : chain) {
            for (final XmlNamespace declared : at.declarations) {
                found.put(declared.prefix(), declared.uri());
            }
        }
        return found;
    }

    /// @return The element's text before its first child, or null.
    public String text() {
        return text;
    }

    /// @param text The element's text before its first child, or null for none.
    public void setText(final String text) {
        this.text = text;
    }

    /// @param name An attribute's name (no namespace).
    /// @return Its value, or null if it has none.
    public String get(final String name) {
        return get(null, name);
    }

    /// @param namespace An attribute's namespace URI, or null.
    /// @param name      Its local name.
    /// @return Its value, or null if it has none.
    public String get(final String namespace, final String name) {
        final Attribute attribute = attribute(namespace, name);
        return attribute == null
                ? null
                : attribute.value();
    }

    /// Sets an attribute, keeping its place if it is set already.
    ///
    /// @param name  The attribute's name (no namespace).
    /// @param value Its value.
    /// @return This element.
    public XmlElement set(final String name, final String value) {
        return set(null, name, value);
    }

    /// Sets an attribute, keeping its place if it is set already.
    ///
    /// @param namespace The attribute's namespace URI, or null for none.
    /// @param name      Its local name.
    /// @param value     Its value.
    /// @return This element.
    public XmlElement set(final String namespace, final String name, final String value) {
        Objects.requireNonNull(value, "value");
        final Attribute existing = attribute(namespace, name);
        if (existing != null) {
            existing.value = value;
            return this;
        }
        final XmlNamespace declared = namespace == null || namespace.isEmpty()
                ? null
                : findOrBuild(namespace, null, true);
        attributes.add(new Attribute(declared, declared == null
                ? null
                : namespace, name, value));
        return this;
    }

    private Attribute attribute(final String namespace, final String name) {
        final String wanted = namespace == null || namespace.isEmpty()
                ? null
                : namespace;
        for (final Attribute attribute : attributes) {
            if (Objects.equals(attribute.namespace(), wanted) && attribute.name().equals(name)) {
                return attribute;
            }
        }
        return null;
    }

    /// @param declared The declaration the attribute is written with, or null.
    /// @param name     Its local name.
    /// @param value    Its value.
    void addParsedAttribute(final XmlNamespace declared, final String name, final String value) {
        attributes.add(new Attribute(declared, declared == null
                ? null
                : declared.uri(), name, value));
    }

    /// @param declared A namespace declared on the element as read.
    void addParsedDeclaration(final XmlNamespace declared) {
        declarations.add(declared);
    }

    /// @param declared The declaration the element's name is written with.
    void bind(final XmlNamespace declared) {
        binding = declared;
    }

    /// @param node A child, as read (its namespaces already as written).
    void appendParsed(final XmlNode node) {
        children.add(node);
        node.setParent(this);
    }

    /// @return The attributes, in order.
    public List<Attribute> attributes() {
        return Collections.unmodifiableList(attributes);
    }

    /// @return The child nodes (elements, comments and instructions), in order.
    public List<XmlNode> children() {
        return Collections.unmodifiableList(children);
    }

    /// @return The child elements, in order.
    public List<XmlElement> childElements() {
        final List<XmlElement> out = new ArrayList<>();
        for (final XmlNode child : children) {
            if (child instanceof final XmlElement element) {
                out.add(element);
            }
        }
        return out;
    }

    /// @param namespace A namespace URI, or null.
    /// @param localName A local name.
    /// @return The child elements of that name, in order.
    public List<XmlElement> childElements(final String namespace, final String localName) {
        return childElements().stream()
                .filter(child -> child.is(namespace, localName))
                .toList();
    }

    /// @return This element and every node below it, in document order.
    public List<XmlNode> iter() {
        final List<XmlNode> out = new ArrayList<>();
        collect(this, out);
        return out;
    }

    /// @return This element and every element below it, in document order.
    public List<XmlElement> iterElements() {
        final List<XmlElement> out = new ArrayList<>();
        for (final XmlNode node : iter()) {
            if (node instanceof final XmlElement element) {
                out.add(element);
            }
        }
        return out;
    }

    /// @param namespace A namespace URI, or null.
    /// @param localName A local name.
    /// @return This element and every element below it of that name, in document order.
    public List<XmlElement> iterElements(final String namespace, final String localName) {
        return iterElements().stream()
                .filter(element -> element.is(namespace, localName))
                .toList();
    }

    /// @return The elements above this one, nearest first.
    public List<XmlElement> ancestors() {
        final List<XmlElement> out = new ArrayList<>();
        for (XmlElement at = parent(); at != null; at = at.parent()) {
            out.add(at);
        }
        return out;
    }

    /// @param node A child of this element.
    /// @return Its position among the children, or -1.
    public int indexOf(final XmlNode node) {
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i) == node) {
                return i;
            }
        }
        return -1;
    }

    /// Adds a node as the last child, moving it (and its tail) from wherever it was.
    ///
    /// @param node The node.
    public void append(final XmlNode node) {
        detach(node);
        children.add(node);
        node.setParent(this);
        adopt(node);
    }

    /// Puts a node before the child at a position (or last, if there is none there), moving it (and its tail) from
    /// wherever it was.
    ///
    /// @param index The position.
    /// @param node  The node.
    public void insert(final int index, final XmlNode node) {
        if (index >= children.size()) {
            append(node);
            return;
        }
        final XmlNode before = children.get(Math.max(index, 0));
        if (before == node) {
            return;
        }
        detach(node);
        children.add(indexOf(before), node);
        node.setParent(this);
        adopt(node);
    }

    /// Removes a child (with its tail).
    ///
    /// @param node The child.
    public void remove(final XmlNode node) {
        final int at = indexOf(node);
        if (at >= 0) {
            children.remove(at);
            node.setParent(null);
        }
    }

    /// @return The document the element belongs to.
    Owner owner() {
        return owner;
    }

    private static void collect(final XmlNode node, final List<XmlNode> out) {
        out.add(node);
        if (node instanceof final XmlElement element) {
            for (final XmlNode child : element.children) {
                collect(child, out);
            }
        }
    }

    private static void detach(final XmlNode node) {
        final XmlElement old = node.parent();
        if (old != null) {
            old.remove(node);
        }
    }

    // Each element moved drops the namespace declarations its new ancestors make already, using theirs, and any
    // namespace not declared in its new place is declared on the moved element.
    private void adopt(final XmlNode node) {
        if (!(node instanceof final XmlElement start)) {
            return;
        }
        final Map<XmlNamespace, XmlNamespace> cache = new IdentityHashMap<>();
        for (final XmlElement element : start.iterElements()) {
            for (final XmlNamespace declared : List.copyOf(element.declarations)) {
                final XmlNamespace known = element.parent() == null
                        ? null
                        : element.parent().searchByUri(declared.uri(), false, false, element.parent());
                if (known == null) {
                    cache.put(declared, declared);
                } else {
                    cache.put(declared, known);
                    element.declarations.remove(declared);
                }
            }
            if (element.binding != null) {
                element.binding = fixed(start, element.binding, false, cache);
            }
            for (final Attribute attribute : element.attributes) {
                if (attribute.declared != null) {
                    attribute.declared = fixed(start, attribute.declared, true, cache);
                }
            }
            element.owner = owner;
        }
    }

    private XmlNamespace fixed(final XmlElement start,
                               final XmlNamespace old,
                               final boolean isAttribute,
                               final Map<XmlNamespace, XmlNamespace> cache) {
        final XmlNamespace known = cache.get(old);
        if (known != null && !(isAttribute && old.prefix() != null && known.prefix() == null)) {
            return known;
        }
        final XmlNamespace found = start.findOrBuild(old.uri(), old.prefix(), isAttribute);
        cache.put(old, found);
        return found;
    }

    // A declaration in scope of this element, or a new one on it.
    private XmlNamespace findOrBuild(final String uri, final String wantedPrefix, final boolean isAttribute) {
        final XmlNamespace found = searchByUri(uri, isAttribute, true, this);
        if (found != null && !(isAttribute && found.prefix() == null)) {
            return found;
        }
        String prefix = wantedPrefix;
        if (prefix == null) {
            prefix = DEFAULT_PREFIXES.get(uri);
            if (prefix == null) {
                prefix = owner.newPrefix();
            }
        }
        while (searchByPrefix(prefix) != null) {
            prefix = owner.newPrefix();
        }
        final XmlNamespace declared = new XmlNamespace(prefix, uri);
        declarations.add(declared);
        return declared;
    }

    // A declaration of the URI in scope at origin, searched from this element up, whose prefix isn't redeclared
    // between. The search for a declaration to use (lxmlSearch) keeps looking past a default namespace for an
    // attribute, falling back to it; the other skips it.
    private XmlNamespace searchByUri(final String uri,
                                     final boolean isAttribute,
                                     final boolean lxmlSearch,
                                     final XmlElement origin) {
        XmlNamespace fallback = null;
        for (XmlElement at = this; at != null; at = at.parent()) {
            final List<XmlNamespace> candidates = new ArrayList<>(at.declarations);
            if (at != origin && at.binding != null) {
                candidates.add(at.binding);
            }
            for (final XmlNamespace declared : candidates) {
                if (!declared.uri().equals(uri)) {
                    continue;
                }
                if (declared.prefix() == null && isAttribute) {
                    if (lxmlSearch && fallback == null) {
                        fallback = declared;
                    }
                } else if (origin.searchByPrefix(declared.prefix()) == declared) {
                    return declared;
                }
            }
        }
        if (fallback != null && origin.searchByPrefix(null) == fallback) {
            return fallback;
        }
        return null;
    }

    /// @param prefix A prefix, or null for the default namespace.
    /// @return Its declaration in scope here (libxml2's `xmlSearchNs`), or null.
    XmlNamespace searchByPrefix(final String prefix) {
        for (XmlElement at = this; at != null; at = at.parent()) {
            for (final XmlNamespace declared : at.declarations) {
                if (Objects.equals(declared.prefix(), prefix)) {
                    return declared;
                }
            }
            if (at != this && at.binding != null && Objects.equals(at.binding.prefix(), prefix)) {
                return at.binding;
            }
        }
        return null;
    }


    // --------------------------------------------------------------------------------


    /// An attribute of an element.
    public static final class Attribute {

        private final String namespace;
        private final String name;
        private XmlNamespace declared;
        private String value;

        private Attribute(final XmlNamespace declared, final String namespace, final String name, final String value) {
            this.declared = declared;
            this.namespace = namespace;
            this.name = name;
            this.value = value;
        }

        /// @return The attribute's namespace URI, or null.
        public String namespace() {
            return namespace;
        }

        /// @return Its local name.
        public String name() {
            return name;
        }

        /// @return Its key: `{namespace}name`, or the name alone.
        public String key() {
            return namespace == null
                    ? name
                    : "{" + namespace + "}" + name;
        }

        /// @return Its value.
        public String value() {
            return value;
        }

        /// @return The declaration it is written with, or null.
        public XmlNamespace declared() {
            return declared;
        }
    }


    // --------------------------------------------------------------------------------


    /// The document an element belongs to: the prefixes made up (`ns0`, `ns1`, ...) are numbered per document.
    static final class Owner {

        private int counter;

        /// @return The document's next made-up prefix.
        String newPrefix() {
            return "ns" + counter++;
        }
    }
}
