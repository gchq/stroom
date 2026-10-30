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

package stroom.widget.xsdbrowser.client;

import stroom.widget.text.client.Html;
import stroom.widget.text.client.Label;
import stroom.widget.xsdbrowser.client.XsdNode.XsdAttribute;
import stroom.widget.xsdbrowser.client.XsdNode.XsdType;

import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XsdDiagram extends Composite {

    private final FlowPanel contentPanel = new FlowPanel();
    private final Map<XsdNode, Integer> rowMap = new HashMap<>();
    XsdModel model;
    SelectionMap selectionMap;

    public XsdDiagram() {
        initWidget(contentPanel);
    }

    public void setModel(final XsdModel model) {
        this.model = model;
        setSelectedNode(model.getSelectedItem(), true);
    }

    public void setSelectedNode(final XsdNode node, final boolean change) {
        if (change) {
            showNode(node);
        }

        if (selectionMap != null) {
            selectionMap.setSelectedItem(node);
        }
    }

    void showNode(final XsdNode node) {
        if (node != null) {
            Widget display = null;

            final SelectionMap map = new SelectionMap();
            final XsdType type = node.getType();

            if (type == XsdType.SCHEMA) {
                display = getRootDisplay(map, node);
            } else if (type == XsdType.ELEMENT) {
                display = getElementDisplay(map, node);
            } else if (type == XsdType.COMPLEX_TYPE) {
                display = getTypeDisplay(map, node);
            }

            if (display != null) {
                contentPanel.clear();
                contentPanel.add(display);
                selectionMap = map;
            }
        } else {
            contentPanel.clear();
            if (model.getParseException() != null) {
                contentPanel.add(new Label(model.getParseException().getMessage()));
            }
        }
    }

    private XsdDiagramBox getRootDisplay(final SelectionMap map, final XsdNode node) {
        final FlowPanel midPanel = new FlowPanel();
        midPanel.addStyleName("xsd-browser__mid-panel");

        midPanel.add(new XsdDiagramBox(
                "xsd-browser__title-elements",
                "Elements",
                getNodeListDisplay(
                        null,
                        map,
                        node,
                        false,
                        true,
                        false,
                        false,
                        false),
                map,
                model,
                null));

        midPanel.add(new XsdDiagramBox(
                "xsd-browser__title-types",
                "Types",
                getNodeListDisplay(
                        null,
                        map,
                        node,
                        false,
                        false,
                        true,
                        true,
                        false),
                map,
                model,
                null));

        final String targetNamespace = XmlUtil.getAttributeValue(
                node.getNode(),
                XsdAttribute.TARGET_NAMESPACE,
                false);
        String title = "Schema: ";
        if (targetNamespace != null) {
            title = title + targetNamespace;
        }
        final XsdDiagramBox box = new XsdDiagramBox(
                "xsd-browser__title-schema", title, midPanel, map, model, node);

        return box;
    }

    private FlowPanel getElementDisplay(final SelectionMap map, final XsdNode node) {
        final XsdNode typeNode = node.getTypeNode();
        if (typeNode == null) {
            return null;
        }

        final FlowPanel left = column(false);
        final FlowPanel right = column(true);

        left.add(new XsdDiagramBox(
                "xsd-browser__title-element",
                node.getName(),
                null,
                map,
                model,
                node));

        right.add(getTypeBox(right, map, typeNode));

        return columns(left, right);
    }

    private FlowPanel getTypeDisplay(final SelectionMap map, final XsdNode node) {
        final FlowPanel left = column(true);
        final FlowPanel right = column(false);

        left.add(getTypeBox(left, map, node));

        for (final XsdNode child : node.getChildTypes()) {
            final XsdDiagramBox typeBox = getTypeBox(null, map, child);
            typeBox.addStyleName("xsd-browser__type-box");
            right.add(typeBox);
        }

        return columns(left, right);
    }

    // A column of boxes, one above another, either centred or to its left
    private FlowPanel column(final boolean centred) {
        final FlowPanel column = new FlowPanel();
        column.addStyleName("xsd-browser__column");
        if (centred) {
            column.addStyleName("xsd-browser__column--centred");
        }
        return column;
    }

    // Two columns side by side, with a gap between them
    private FlowPanel columns(final FlowPanel left, final FlowPanel right) {
        final FlowPanel layout = new FlowPanel();
        layout.addStyleName("xsd-browser__columns");
        layout.add(left);
        layout.add(right);
        return layout;
    }

    private XsdDiagramBox getTypeBox(final FlowPanel layoutColumn,
                                     final SelectionMap map,
                                     final XsdNode node) {
        if (node != null) {
            String title = node.getName();
            if (title == null) {
                final XsdNode parent = node.getParent();
                if (parent != null) {
                    title = parent.getName();
                    title = "(" + title + "Type)";
                }
            }

            if (title != null) {
                if (node.getType() == XsdType.SIMPLE_TYPE) {
                    return new XsdDiagramBox(
                            "xsd-browser__title-simple-type",
                            title,
                            null,
                            map,
                            model,
                            node);
                }

                return new XsdDiagramBox(
                        "xsd-browser__title-complex-type",
                        title,
                        getNodeListDisplay(
                                layoutColumn,
                                map,
                                node,
                                true,
                                true,
                                false,
                                false,
                                true),
                        map,
                        model,
                        node);
            }
        }

        return null;
    }

    private XsdNodeGrid getNodeListDisplay(final FlowPanel layoutColumn,
                                    final SelectionMap map,
                                    final XsdNode node,
                                    final boolean showAttributes,
                                    final boolean showElements,
                                    final boolean showComplexTypes,
                                    final boolean showSimpleTypes,
                                    final boolean showOccurance) {
        XsdNodeGrid layout = null;

        if (node != null) {
            // Clear the row map.
            rowMap.clear();

            // First find out what the maximum depth of nesting constructs is.
            int maxConstructDepth = getMaxConstructDepth(node);

            if (maxConstructDepth > 0) {
                // Each level has 2 images so double the construct depth.
                maxConstructDepth = maxConstructDepth * 2;
            }

            layout = new XsdNodeGrid(6 + maxConstructDepth);

            int row = 0;
            final int col = 0;

            if (showAttributes) {
                row = addNode(layoutColumn, layout, map, node, showOccurance, true, row, col,
                        XsdTypeFilter.ATTRIBUTE_FILTER);
            }
            if (showElements) {
                row = addNode(layoutColumn, layout, map, node, showOccurance, false, row, col,
                        XsdTypeFilter.COMPLEX_CONTENT_FILTER);
                row = addNode(layoutColumn, layout, map, node, showOccurance, false, row, col,
                        XsdTypeFilter.STRUCTURE_FILTER);
            }
            if (showComplexTypes) {
                row = addNode(layoutColumn, layout, map, node, showOccurance, false, row, col,
                        XsdTypeFilter.COMPLEX_TYPE_FILTER);
            }
            if (showSimpleTypes) {
                row = addNode(layoutColumn, layout, map, node, showOccurance, false, row, col,
                        XsdTypeFilter.SIMPLE_TYPE_FILTER);
            }

            // Apply some overall styles to surround the content (the cells are aligned to the
            // middle by xsd-browser.css).
            for (row = 0; row < layout.getRowCount(); row++) {
                layout.addCellStyleName(row, 0, "xsd-browser__node-grid-cell--start");
                layout.addCellStyleName(row, layout.getColumnCount() - 1, "xsd-browser__node-grid-cell--end");
            }

            if (layout.getRowCount() > 0) {
                addRowStyleName(layout, 0, "xsd-browser__node-grid-cell--top");
                addRowStyleName(layout, layout.getRowCount() - 1, "xsd-browser__node-grid-cell--bottom");
            }

            // If we didn't add any rows then set the layout to null.
            if (row == 0) {
                layout = null;
            }
        }

        return layout;
    }

    private int getMaxConstructDepth(final XsdNode node) {
        boolean hasConstruct = false;
        int maxDepth = 0;

        // Add complex content structures.
        final List<XsdNode> complexContentNodes = node.getChildNodes(
                XsdTypeFilter.COMPLEX_CONTENT_FILTER,
                true);
        for (final XsdNode complexContentNode : complexContentNodes) {
            maxDepth += getMaxConstructDepth(complexContentNode);
        }

        // Add extension node structures.
        final List<XsdNode> extensionNodes = node.getChildNodes(
                XsdTypeFilter.EXTENSION_FILTER,
                true);
        for (final XsdNode extensionNode : extensionNodes) {
            maxDepth += getMaxConstructDepth(extensionNode);
        }

        // Add structure nodes.
        final List<XsdNode> structureNodes = node.getChildNodes(
                XsdTypeFilter.STRUCTURE_FILTER,
                true);
        for (final XsdNode child : structureNodes) {
            final XsdType type = child.getType();

            if (type.isStructural()) {
                hasConstruct = true;
                maxDepth += getMaxConstructDepth(child);
            }
        }

        if (hasConstruct) {
            maxDepth++;
        }

        return maxDepth;
    }

    private int addNode(final FlowPanel layoutColumn,
                        final XsdNodeGrid layout,
                        final SelectionMap map,
                        final XsdNode node,
                        final boolean showOccurrence,
                        final boolean addSeparator,
                        final int initialRow,
                        final int initialCol,
                        final XsdTypeFilter typeFilter) {
        int row = initialRow;
        final int col = initialCol;

        final List<XsdNode> childNodes = node.getChildNodes(typeFilter, true);
        if (childNodes.size() == 0 && node.getType().isStructural()) {
            row++;

        } else {
            if (typeFilter == XsdTypeFilter.COMPLEX_CONTENT_FILTER) {
                // See if we have complex content.
                for (final XsdNode child : childNodes) {
                    final List<XsdNode> extensionNodes = child.getChildNodes(
                            XsdTypeFilter.EXTENSION_FILTER, true);
                    for (final XsdNode extensionNode : extensionNodes) {
                        // Add a type box for the super type above the extension
                        // type.
                        if (layoutColumn != null) {
                            final XsdNode baseNode = extensionNode.getBaseNode();
                            final XsdDiagramBox typeBox = getTypeBox(layoutColumn, map, baseNode);

                            layoutColumn.add(typeBox);
                            final SimplePanel superArrow = new SimplePanel();
                            superArrow.getElement().setClassName("xsd-browser__super-arrow-select");
                            layoutColumn.add(superArrow);
                        }

                        row = addNode(
                                layoutColumn,
                                layout,
                                map,
                                extensionNode,
                                showOccurrence,
                                true,
                                row,
                                col,
                                XsdTypeFilter.ATTRIBUTE_FILTER);

                        row = addNode(
                                layoutColumn,
                                layout,
                                map,
                                extensionNode,
                                showOccurrence,
                                addSeparator,
                                row,
                                col,
                                XsdTypeFilter.STRUCTURE_FILTER);
                    }
                }

            } else {
                // Add child element nodes as normal.
                for (final XsdNode child : childNodes) {
                    final XsdType type = child.getType();

                    if (type == XsdType.SEQUENCE || type == XsdType.CHOICE || type == XsdType.ALL) {
                        addChild(layout, map, child, showOccurrence, row, col);
                        row = addNode(
                                layoutColumn,
                                layout,
                                map,
                                child,
                                true,
                                false,
                                row,
                                col + 2,
                                typeFilter);
                    } else {
                        addChild(layout, map, child, showOccurrence, row, col);

                        row++;
                    }
                }
            }
        }

        if (addSeparator) {
            addSeparator(layout);
        }

        return row;
    }

    private void addChild(final XsdNodeGrid layout,
                          final SelectionMap map,
                          final XsdNode node,
                          final boolean showOccurance,
                          final int row, final int col) {
        // Ensure layout size.
        layout.ensureRows(row + 1);

        final XsdType type = node.getType();
        rowMap.put(node, row);

        // Add the image for the structure element if this is one.
        if (type.isStructural()) {
            final String occurrence = node.getOccurance();

            if (occurrence != null) {
                final SafeHtmlBuilder builder = new SafeHtmlBuilder();
                builder.appendHtmlConstant("<div class=\"xsd-browser__occurrence-label\">");
                builder.appendEscaped(occurrence);
                builder.appendHtmlConstant("</div>");

                final Html html = new Html(builder.toSafeHtml());

                final FlowPanel fp = new FlowPanel();
                fp.add(getImage(type));
                fp.add(html);
                fp.addStyleName("xsd-browser__occurrence");

                layout.setWidget(row, col, fp);
            } else {
                layout.setWidget(row, col, getImage(type));
            }

            final SimplePanel panel = new SimplePanel();
            panel.getElement().setClassName("xsd-browser__tree-03");
            layout.setWidget(row, col + 1, panel);

        } else {
            // Otherwise add the element.
            XsdNode refNode = null;

            Widget image = null;
            XsdNodeLabel lblName = null;
            Label lblOccurrence = null;

            String name = node.getName();
            String valueType = null;

            if (type == XsdType.ELEMENT || type == XsdType.ATTRIBUTE) {
                if (name == null) {
                    refNode = node.getRefNode();
                    if (refNode != null) {
                        name = refNode.getName();
                    }
                }

                if (refNode != null) {
                    valueType = refNode.getValueType();
                    if (valueType == null) {
                        valueType = "(" + name + "Type)";
                    }

                } else {
                    valueType = node.getValueType();
                    if (valueType == null) {
                        valueType = "(" + name + "Type)";
                    }
                }

                if (showOccurance) {
                    final String occurance = node.getOccurance();
                    if (occurance != null) {
                        lblOccurrence = oneLineLabel(node.getOccurance());
                    }
                }
            }

            // Get the image to use.
            if (refNode != null) {
                image = getImage(XsdType.ELEMENT_REF);
            } else {
                image = getImage(type);
            }
            if (name != null) {
                lblName = new XsdNodeLabel(name, map, model, node, refNode);
            }

            final Label lblType = valueType != null
                    ? oneLineLabel(valueType)
                    : null;

            final int colCount = layout.getColumnCount();

            // Add line images to get back to the structure level.
            if (node.getParent() != null && node.getParent().getType().isStructural()) {
                for (int i = col; i < colCount - 6; i++) {
                    layout.setWidget(row, i, image("xsd-browser__tree-03"));
                }
            }

            // Add other images to create the tree lines.
            int pos = col - 1;
            XsdNode parent = node;
            while (pos >= 0 && parent != null && parent.getType().isStructuralOrElement()) {
                if (node == parent || rowMap.get(parent) == row) {
                    if (parent.isFirstChild() && parent.isLastChild()) {
                        layout.setWidget(row,
                                pos,
                                image("xsd-browser__tree-03"));
                    } else if (parent.isFirstChild()) {
                        layout.setWidget(row,
                                pos,
                                image("xsd-browser__tree-06"));
                    } else if (parent.isLastChild()) {
                        layout.setWidget(row,
                                pos,
                                image("xsd-browser__tree-09"));
                    } else {
                        layout.setWidget(row,
                                pos,
                                image("xsd-browser__tree-05"));
                    }
                } else if (!parent.isLastChild()) {
                    layout.setWidget(row, pos, image("xsd-browser__tree-02"));
                }

                parent = parent.getParent();
                pos -= 2;
            }

            if (image != null) {
                layout.setWidget(row, colCount - 6, image);
                image.addStyleName("xsd-browser__icon");
            }
            if (lblName != null) {
                layout.setWidget(row, colCount - 5, lblName);
            }
            if (lblOccurrence != null) {
                layout.setWidget(row, colCount - 4, oneLineLabel("["));
                layout.setWidget(row, colCount - 3, lblOccurrence);
                layout.setWidget(row, colCount - 2, oneLineLabel("]"));
            }
            if (lblType != null) {
                layout.setWidget(row, colCount - 1, lblType);
                lblType.addStyleName("xsd-browser__type-name");
            }
        }
    }

    private Widget image(final String className) {
        final SimplePanel panel = new SimplePanel();
        panel.getElement().setClassName(className);
        return panel;
    }

    private void addSeparator(final XsdNodeGrid layout) {
        // Add a row with a black border at the bottom.
        addRowStyleName(layout, layout.getRowCount() - 1, "xsd-browser__node-grid-cell--separator");
    }

    private void addRowStyleName(final XsdNodeGrid layout, final int row, final String styleName) {
        if (row >= 0) {
            layout.addRowStyleName(row, styleName);
        }
    }

    private Widget getImage(final XsdType type) {
        String className = null;
        String title = null;

        switch (type) {
            case ALL:
                className = "xsd-browser__node-all";
                title = "All";
                break;
            case ANY:
                className = "xsd-browser__node-any";
                break;
            case ATTRIBUTE:
                className = "xsd-browser__node-attribute";
                break;
            case CHOICE:
                className = "xsd-browser__node-choice";
                title = "Choice";
                break;
            case COMPLEX_TYPE:
                className = "xsd-browser__node-complex-type";
                break;
            case ELEMENT:
                className = "xsd-browser__node-element";
                break;
            case ELEMENT_REF:
                className = "xsd-browser__node-element-ref";
                break;
            case SCHEMA:
                className = "xsd-browser__title-schema";
                break;
            case SEQUENCE:
                className = "xsd-browser__node-sequence";
                title = "Sequence";
                break;
            case SIMPLE_TYPE:
                className = "xsd-browser__node-simple-type";
                break;
            default:
                break;
        }

        if (className != null) {
            final Widget image = image(className);
            if (title != null) {
                image.setTitle(title);
            }

            return image;
        }

        return null;
    }

    // A label kept on one line (xsd-browser.css)
    private static Label oneLineLabel(final String text) {
        final Label label = new Label(text);
        label.addStyleName("xsd-browser__one-line");
        return label;
    }
}
