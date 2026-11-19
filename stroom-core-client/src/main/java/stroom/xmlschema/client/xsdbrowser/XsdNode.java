/*
 * Copyright 2016 Crown Copyright
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

package stroom.xmlschema.client.xsdbrowser;

import stroom.util.shared.CompareBuilder;

import com.google.gwt.xml.client.Node;
import com.google.gwt.xml.client.NodeList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class XsdNode implements Comparable<XsdNode> {

    private static final String XSD_NS = "http://www.w3.org/2001/XMLSchema";
    private static final Map<String, XsdType> xsdElementMap = new HashMap<>();
    private static final Map<String, XsdAttribute> xsdAttributeMap = new HashMap<>();

    static {
        for (final XsdType xsdElement : XsdType.values()) {
            xsdElementMap.put(xsdElement.toString(), xsdElement);
        }
        for (final XsdAttribute xsdAttribute : XsdAttribute.values()) {
            xsdAttributeMap.put(xsdAttribute.toString(), xsdAttribute);
        }
    }

    private final XsdModel model;
    private final Node node;
    private final String name;
    private final XsdType type;
    private final String valueType;
    private final String ref;
    private final String base;
    private final String occurance;
    private XsdNode parent;
    private String documentation;
    private boolean firstChild;
    private boolean lastChild;

    public XsdNode(final XsdModel model, final Node node) {
        this.model = model;
        this.node = node;

        // Get the type.
        type = getType(node);
        name = XmlUtil.getAttributeValue(node, XsdAttribute.NAME);
        ref = XmlUtil.getAttributeValue(node, XsdAttribute.REF);
        base = XmlUtil.getAttributeValue(node, XsdAttribute.BASE);
        valueType = XmlUtil.getAttributeValue(node, XsdAttribute.TYPE);

        String minOccurs = XmlUtil.getAttributeValue(node, XsdAttribute.MIN_OCCURS);
        String maxOccurs = XmlUtil.getAttributeValue(node, XsdAttribute.MAX_OCCURS);

        if (minOccurs != null || maxOccurs != null) {
            if (minOccurs == null) {
                minOccurs = "1";
            } else if (minOccurs.equalsIgnoreCase("unbounded")) {
                minOccurs = "*";
            }
            if (maxOccurs == null) {
                maxOccurs = "1";
            } else if (maxOccurs.equalsIgnoreCase("unbounded")) {
                maxOccurs = "*";
            }

            occurance = minOccurs + ".." + maxOccurs;
        } else {
            occurance = null;
        }
    }

    private static XsdType getType(final Node node) {
        if (node.getNamespaceURI() == null || !node.getNamespaceURI().equalsIgnoreCase(XSD_NS)) {
            return null;
        }

        final String localName = XmlUtil.removePrefix(node.getNodeName());
        return xsdElementMap.get(localName);
    }

    public Node getNode() {
        return node;
    }

    public String getName() {
        return name;
    }

    public XsdType getType() {
        return type;
    }

    public String getValueType() {
        return valueType;
    }

    public XsdNode getParent() {
        return parent;
    }

    public String getRef() {
        return ref;
    }

    private String getBase() {
        return base;
    }

    public String getOccurance() {
        return occurance;
    }

    public List<XsdNode> getChildNodes() {
        return getChildNodes(null, false);
    }

    public List<XsdNode> getChildNodes(final XsdTypeFilter filter, final boolean resolveGroups) {
        final List<XsdNode> nodeList = new ArrayList<>();

        if (node.hasChildNodes()) {
            // Add nodes to the list.
            addNodes(filter, node, nodeList, resolveGroups);

            if (getType() != XsdType.SEQUENCE) {
                Collections.sort(nodeList);
            }

            if (nodeList.size() > 0) {
                nodeList.get(0).firstChild = true;
                nodeList.get(nodeList.size() - 1).lastChild = true;
            }
        }

        return nodeList;
    }

    private void addNodes(final XsdTypeFilter filter, final Node node, final List<XsdNode> nodeList,
                          final boolean resolveGroups) {
        if (node.hasChildNodes()) {
            final NodeList childNodes = node.getChildNodes();
            final int childNodeCount = childNodes.getLength();

            for (int index = 0; index < childNodeCount; index++) {
                final Node child = childNodes.item(index);
                final XsdNode xsdNode = new XsdNode(model, child);
                final XsdType type = xsdNode.getType();

                if (type != null) {
                    // Recursively add referenced groups.
                    if (resolveGroups && XsdType.GROUP.equals(type)) {
                        if (xsdNode.getName() == null) {
                            final XsdNode refGroup = xsdNode.getRefGroup();
                            if (refGroup != null) {
                                addNodes(filter, refGroup.getNode(), nodeList, resolveGroups);
                            }
                        }
                    } else if (filter == null || filter.contains(type)) {
                        xsdNode.parent = this;
                        nodeList.add(xsdNode);
                    }
                }
            }
        }
    }

    public boolean isFirstChild() {
        return firstChild;
    }

    public boolean isLastChild() {
        return lastChild;
    }

    public List<XsdNode> getChildTypes() {
        return getChildTypes(this);
    }

    private List<XsdNode> getChildTypes(final XsdNode node) {
        final List<XsdNode> typeNodes = new ArrayList<>();

        // Get child nodes for complex context.
        for (final XsdNode child : node.getChildNodes(XsdTypeFilter.COMPLEX_CONTENT_FILTER, true)) {
            typeNodes.addAll(getChildTypes(child));
        }

        // Add child nodes for extensions.
        for (final XsdNode child : node.getChildNodes(XsdTypeFilter.EXTENSION_FILTER, true)) {
            typeNodes.addAll(getChildTypes(child));
        }

        // Get child nodes for structure elements, e.g. All, Sequence and
        // Choice.
        for (final XsdNode child : node.getChildNodes(XsdTypeFilter.STRUCTURE_FILTER, true)) {
            final XsdType type = child.getType();

            if (type == XsdType.ELEMENT) {
                final XsdNode typeNode = getTypeNode(child);
                if (typeNode != null && !typeNodes.contains(typeNode)) {
                    typeNodes.add(typeNode);
                }
            } else if (type.isStructural()) {
                final List<XsdNode> childTypeNodes = getChildTypes(child);
                for (final XsdNode childTypeNode : childTypeNodes) {
                    if (!typeNodes.contains(childTypeNode)) {
                        typeNodes.add(childTypeNode);
                    }
                }
            }
        }

        return typeNodes;
    }

    public XsdNode getTypeNode() {
        return getTypeNode(this);
    }

    private XsdNode getTypeNode(final XsdNode node) {
        XsdNode typeNode = null;

        if (node != null) {
            final String valueType = node.getValueType();
            if (valueType != null) {
                typeNode = model.getGlobalTypeMap().get(valueType);
            }

            if (typeNode == null) {
                final XsdNode refNode = node.getRefNode();
                if (refNode != null) {
                    typeNode = getTypeNode(refNode);
                }
            }

            if (typeNode == null) {
                for (final XsdNode child : node.getChildNodes(XsdTypeFilter.TYPE_FILTER, true)) {
                    typeNode = child;
                }
            }
        }

        return typeNode;
    }

    public XsdNode getRefGroup() {
        final String ref = getRef();
        if (ref == null) {
            return null;
        }
        return model.getGlobalGroupMap().get(ref);
    }

    public XsdNode getRefNode() {
        return model.getGlobalElementMap().get(getRef());
    }

    public XsdNode getBaseNode() {
        return model.getGlobalTypeMap().get(getBase());
    }

    public String getDocumentation() {
        if (documentation == null) {
            final StringBuilder sb = new StringBuilder();

            for (final XsdNode xsdNode : getChildNodes(XsdTypeFilter.DOCUMENTATION_FILTER, true)) {
                if (xsdNode.getType() == XsdType.ANNOTATION) {
                    sb.append(xsdNode.getDocumentation());
                } else if (xsdNode.getType() == XsdType.DOCUMENTATION) {
                    sb.append(XmlUtil.getContent(xsdNode.getNode()));
                }
            }

            documentation = sb.toString();
        }

        return documentation;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final XsdNode xsdNode = (XsdNode) o;
        final Node node2 = xsdNode.getNode();

        final String o1Name = XmlUtil.getAttributeValue(node, XsdAttribute.NAME);
        final String o2Name = XmlUtil.getAttributeValue(node2, XsdAttribute.NAME);

        return Objects.equals(o1Name, o2Name);
    }

    @Override
    public int hashCode() {
        final String name = XmlUtil.getAttributeValue(node, XsdAttribute.NAME);
        return Objects.hashCode(name);
    }

    @Override
    public int compareTo(final XsdNode o) {
        final Node node1 = node;
        final Node node2 = o.getNode();

        final String o1Name = XmlUtil.getAttributeValue(node1, XsdAttribute.NAME);
        final String o2Name = XmlUtil.getAttributeValue(node2, XsdAttribute.NAME);

        final CompareBuilder builder = new CompareBuilder();
        builder.append(o1Name, o2Name);
        return builder.toComparison();
    }

    public enum XsdType {
        SCHEMA("schema"),
        ATTRIBUTE("attribute"),
        COMPLEX_TYPE("complexType"),
        SIMPLE_TYPE("simpleType"),
        ELEMENT(
                "element"),
        ELEMENT_REF("ref"),
        GROUP("group"),
        SEQUENCE("sequence"),
        CHOICE("choice"),
        ALL("all"),
        ANY(
                "any"),
        ANNOTATION("annotation"),
        DOCUMENTATION("documentation"),
        RESTRICTION(
                "restriction"),
        PATTERN("pattern"),
        ENUMERATION("enumeration"),
        COMPLEX_CONTENT(
                "complexContent"),
        EXTENSION("extension");

        private final String string;

        XsdType(final String string) {
            this.string = string;
        }

        @Override
        public String toString() {
            return string;
        }

        public boolean isStructural() {
            return this == XsdType.SEQUENCE || this == XsdType.CHOICE || this == XsdType.ALL;
        }

        public boolean isStructuralOrElement() {
            return isStructural() || this == XsdType.ELEMENT;
        }
    }

    public enum XsdAttribute {
        MIN_OCCURS("minOccurs"),
        MAX_OCCURS("maxOccurs"),
        TYPE("type"),
        REF("ref"),
        NAME("name"),
        TARGET_NAMESPACE(
                "targetNamespace"),
        VALUE("value"),
        BASE("base");

        private final String string;

        XsdAttribute(final String string) {
            this.string = string;
        }

        @Override
        public String toString() {
            return string;
        }
    }
}
