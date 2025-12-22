/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.widget.xsdbrowser.client.view;

import stroom.util.shared.CompareBuilder;

import com.google.gwt.xml.client.Node;
import com.google.gwt.xml.client.NodeList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class XSDNode implements Comparable<XSDNode> {

    private static final String XSD_NS = "http://www.w3.org/2001/XMLSchema";
    private static final Map<String, XSDType> xsdElementMap = new HashMap<>();
    private static final Map<String, XSDAttribute> xsdAttributeMap = new HashMap<>();

    static {
        for (final XSDType xsdElement : XSDType.values()) {
            xsdElementMap.put(xsdElement.toString(), xsdElement);
        }
        for (final XSDAttribute xsdAttribute : XSDAttribute.values()) {
            xsdAttributeMap.put(xsdAttribute.toString(), xsdAttribute);
        }
    }

    private final XSDModel model;
    private final Node node;
    private final String name;
    private final XSDType type;
    private final String valueType;
    private final String ref;
    private final String base;
    private final String occurance;
    private XSDNode parent;
    private String documentation;
    private boolean firstChild;
    private boolean lastChild;

    public XSDNode(final XSDModel model, final Node node) {
        this.model = model;
        this.node = node;

        // Get the type.
        type = getType(node);
        name = XMLUtil.getAttributeValue(node, XSDAttribute.NAME);
        ref = XMLUtil.getAttributeValue(node, XSDAttribute.REF);
        base = XMLUtil.getAttributeValue(node, XSDAttribute.BASE);
        valueType = XMLUtil.getAttributeValue(node, XSDAttribute.TYPE);

        String minOccurs = XMLUtil.getAttributeValue(node, XSDAttribute.MIN_OCCURS);
        String maxOccurs = XMLUtil.getAttributeValue(node, XSDAttribute.MAX_OCCURS);

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

    private static XSDType getType(final Node node) {
        if (node.getNamespaceURI() == null || !node.getNamespaceURI().equalsIgnoreCase(XSD_NS)) {
            return null;
        }

        final String localName = XMLUtil.removePrefix(node.getNodeName());
        return xsdElementMap.get(localName);
    }

    public Node getNode() {
        return node;
    }

    public String getName() {
        return name;
    }

    public XSDType getType() {
        return type;
    }

    public String getValueType() {
        return valueType;
    }

    public XSDNode getParent() {
        return parent;
    }

    public String getRef() {
        return ref;
    }

    public String getBase() {
        return base;
    }

    public String getOccurance() {
        return occurance;
    }

    public List<XSDNode> getChildNodes() {
        return getChildNodes(null, false);
    }

    public List<XSDNode> getChildNodes(final XSDTypeFilter filter, final boolean resolveGroups) {
        final List<XSDNode> nodeList = new ArrayList<>();

        if (node.hasChildNodes()) {
            // Add nodes to the list.
            addNodes(filter, node, nodeList, resolveGroups);

            if (getType() != XSDType.SEQUENCE) {
                Collections.sort(nodeList);
            }

            if (nodeList.size() > 0) {
                nodeList.get(0).firstChild = true;
                nodeList.get(nodeList.size() - 1).lastChild = true;
            }
        }

        return nodeList;
    }

    private void addNodes(final XSDTypeFilter filter, final Node node, final List<XSDNode> nodeList,
                          final boolean resolveGroups) {
        if (node.hasChildNodes()) {
            final NodeList childNodes = node.getChildNodes();
            final int childNodeCount = childNodes.getLength();

            for (int index = 0; index < childNodeCount; index++) {
                final Node child = childNodes.item(index);
                final XSDNode xsdNode = new XSDNode(model, child);
                final XSDType type = xsdNode.getType();

                if (type != null) {
                    // Recursively add referenced groups.
                    if (resolveGroups && XSDType.GROUP.equals(type)) {
                        if (xsdNode.getName() == null) {
                            final XSDNode refGroup = xsdNode.getRefGroup();
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

    public List<XSDNode> getChildTypes() {
        return getChildTypes(this);
    }

    private List<XSDNode> getChildTypes(final XSDNode node) {
        final List<XSDNode> typeNodes = new ArrayList<>();

        // Get child nodes for complex context.
        for (final XSDNode child : node.getChildNodes(XSDTypeFilter.COMPLEX_CONTENT_FILTER, true)) {
            typeNodes.addAll(getChildTypes(child));
        }

        // Add child nodes for extensions.
        for (final XSDNode child : node.getChildNodes(XSDTypeFilter.EXTENSION_FILTER, true)) {
            typeNodes.addAll(getChildTypes(child));
        }

        // Get child nodes for structure elements, e.g. All, Sequence and
        // Choice.
        for (final XSDNode child : node.getChildNodes(XSDTypeFilter.STRUCTURE_FILTER, true)) {
            final XSDType type = child.getType();

            if (type == XSDType.ELEMENT) {
                final XSDNode typeNode = getTypeNode(child);
                if (typeNode != null && !typeNodes.contains(typeNode)) {
                    typeNodes.add(typeNode);
                }
            } else if (type.isStructural()) {
                final List<XSDNode> childTypeNodes = getChildTypes(child);
                for (final XSDNode childTypeNode : childTypeNodes) {
                    if (!typeNodes.contains(childTypeNode)) {
                        typeNodes.add(childTypeNode);
                    }
                }
            }
        }

        return typeNodes;
    }

    public XSDNode getTypeNode() {
        return getTypeNode(this);
    }

    private XSDNode getTypeNode(final XSDNode node) {
        XSDNode typeNode = null;

        if (node != null) {
            final String valueType = node.getValueType();
            if (valueType != null) {
                typeNode = model.getGlobalTypeMap().get(valueType);
            }

            if (typeNode == null) {
                final XSDNode refNode = node.getRefNode();
                if (refNode != null) {
                    typeNode = getTypeNode(refNode);
                }
            }

            if (typeNode == null) {
                for (final XSDNode child : node.getChildNodes(XSDTypeFilter.TYPE_FILTER, true)) {
                    typeNode = child;
                }
            }
        }

        return typeNode;
    }

    public XSDNode getRefGroup() {
        final String ref = getRef();
        if (ref == null) {
            return null;
        }
        return model.getGlobalGroupMap().get(ref);
    }

    public XSDNode getRefNode() {
        return model.getGlobalElementMap().get(getRef());
    }

    public XSDNode getBaseNode() {
        return model.getGlobalTypeMap().get(getBase());
    }

    public String getDocumentation() {
        if (documentation == null) {
            final StringBuilder sb = new StringBuilder();

            for (final XSDNode xsdNode : getChildNodes(XSDTypeFilter.DOCUMENTATION_FILTER, true)) {
                if (xsdNode.getType() == XSDType.ANNOTATION) {
                    sb.append(xsdNode.getDocumentation());
                } else if (xsdNode.getType() == XSDType.DOCUMENTATION) {
                    sb.append(XMLUtil.getContent(xsdNode.getNode()));
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
        final XSDNode xsdNode = (XSDNode) o;
        final Node node2 = xsdNode.getNode();

        final String o1Name = XMLUtil.getAttributeValue(node, XSDAttribute.NAME);
        final String o2Name = XMLUtil.getAttributeValue(node2, XSDAttribute.NAME);

        return Objects.equals(o1Name, o2Name);
    }

    @Override
    public int hashCode() {
        final String name = XMLUtil.getAttributeValue(node, XSDAttribute.NAME);
        return Objects.hashCode(name);
    }

    @Override
    public int compareTo(final XSDNode o) {
        final Node node1 = node;
        final Node node2 = o.getNode();

        final String o1Name = XMLUtil.getAttributeValue(node1, XSDAttribute.NAME);
        final String o2Name = XMLUtil.getAttributeValue(node2, XSDAttribute.NAME);

        final CompareBuilder builder = new CompareBuilder();
        builder.append(o1Name, o2Name);
        return builder.toComparison();
    }

    public enum XSDType {
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

        XSDType(final String string) {
            this.string = string;
        }

        @Override
        public String toString() {
            return string;
        }

        public boolean isStructural() {
            return this == XSDType.SEQUENCE || this == XSDType.CHOICE || this == XSDType.ALL;
        }

        public boolean isStructuralOrElement() {
            return isStructural() || this == XSDType.ELEMENT;
        }
    }

    public enum XSDAttribute {
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

        XSDAttribute(final String string) {
            this.string = string;
        }

        @Override
        public String toString() {
            return string;
        }
    }
}
