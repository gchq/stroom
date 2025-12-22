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

import stroom.widget.xsdbrowser.client.view.XSDNode.XSDAttribute;
import stroom.widget.xsdbrowser.client.view.XSDNode.XSDType;

import com.google.gwt.dom.client.Style.Position;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XSDDisplay extends Composite {

    private final FlowPanel contentPanel = new FlowPanel();
    private final Map<XSDNode, Integer> rowMap = new HashMap<>();
    XSDModel model;
    SelectionMap selectionMap;

    public XSDDisplay() {
        initWidget(contentPanel);
    }

    public void setModel(final XSDModel model) {
        this.model = model;
        setSelectedNode(model.getSelectedItem(), true);
    }

    public void setSelectedNode(final XSDNode node, final boolean change) {
        if (change) {
            showNode(node);
        }

        if (selectionMap != null) {
            selectionMap.setSelectedItem(node);
        }
    }

    void showNode(final XSDNode node) {
        if (node != null) {
            Widget display = null;

            final SelectionMap map = new SelectionMap();
            final XSDType type = node.getType();

            if (type == XSDType.SCHEMA) {
                display = getRootDisplay(map, node);
            } else if (type == XSDType.ELEMENT) {
                display = getElementDisplay(map, node);
            } else if (type == XSDType.COMPLEX_TYPE) {
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

    private XSDDisplayBox getRootDisplay(final SelectionMap map, final XSDNode node) {
        final HorizontalPanel midPanel = new HorizontalPanel();
        midPanel.addStyleName("midPanel");
        midPanel.setSpacing(5);

        midPanel.add(new XSDDisplayBox(
                "xsdBrowser-xsdTitleElements",
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
                null,
                "100%",
                "100%"));

        midPanel.add(new XSDDisplayBox(
                "xsdBrowser-xsdTitleTypes",
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
                null,
                "100%",
                "100%"));

        final String targetNamespace = XMLUtil.getAttributeValue(
                node.getNode(),
                XSDAttribute.TARGET_NAMESPACE,
                false);
        String title = "Schema: ";
        if (targetNamespace != null) {
            title = title + targetNamespace;
        }
        final XSDDisplayBox box = new XSDDisplayBox(
                "xsdBrowser-xsdTitleSchema", title, midPanel, map, model, node, null,
                null);

        return box;
    }

    private HorizontalPanel getElementDisplay(final SelectionMap map, final XSDNode node) {
        final XSDNode typeNode = node.getTypeNode();
        if (typeNode == null) {
            return null;
        }

        final VerticalPanel left = new VerticalPanel();
        final VerticalPanel right = new VerticalPanel();

        left.add(new XSDDisplayBox(
                "xsdBrowser-xsdTitleElement",
                node.getName(),
                null,
                map,
                model,
                node,
                null,
                null));

        right.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_CENTER);
        right.add(getTypeBox(right, map, typeNode));

        final Label padding = new Label();
        padding.setSize("40px", "1px");

        final HorizontalPanel layout = new HorizontalPanel();
        layout.add(left);
        layout.add(padding);
        layout.add(right);

        return layout;
    }

    private HorizontalPanel getTypeDisplay(final SelectionMap map, final XSDNode node) {
        final VerticalPanel left = new VerticalPanel();
        final VerticalPanel right = new VerticalPanel();

        left.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_CENTER);
        left.add(getTypeBox(left, map, node));

        for (final XSDNode child : node.getChildTypes()) {
            final XSDDisplayBox typeBox = getTypeBox(null, map, child);
            typeBox.addStyleName("displayTypeBox");
            right.add(typeBox);
        }

        final Label padding = new Label();
        padding.setSize("40px", "1px");

        final HorizontalPanel layout = new HorizontalPanel();
        layout.add(left);
        layout.add(padding);
        layout.add(right);

        return layout;
    }

    private XSDDisplayBox getTypeBox(final VerticalPanel layoutColumn,
                                     final SelectionMap map,
                                     final XSDNode node) {
        if (node != null) {
            String title = node.getName();
            if (title == null) {
                final XSDNode parent = node.getParent();
                if (parent != null) {
                    title = parent.getName();
                    title = "(" + title + "Type)";
                }
            }

            if (title != null) {
                if (node.getType() == XSDType.SIMPLE_TYPE) {
                    return new XSDDisplayBox(
                            "xsdBrowser-xsdTitleSimpleType",
                            title,
                            null,
                            map,
                            model,
                            node,
                            null,
                            null);
                }

                return new XSDDisplayBox(
                        "xsdBrowser-xsdTitleComplexType",
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
                        node,
                        null,
                        null);
            }
        }

        return null;
    }

    private Grid getNodeListDisplay(final VerticalPanel layoutColumn,
                                    final SelectionMap map,
                                    final XSDNode node,
                                    final boolean showAttributes,
                                    final boolean showElements,
                                    final boolean showComplexTypes,
                                    final boolean showSimpleTypes,
                                    final boolean showOccurance) {
        Grid layout = null;

        if (node != null) {
            // Clear the row map.
            rowMap.clear();

            // First find out what the maximum depth of nesting constructs is.
            int maxConstructDepth = getMaxConstructDepth(node);

            if (maxConstructDepth > 0) {
                // Each level has 2 images so double the construct depth.
                maxConstructDepth = maxConstructDepth * 2;
            }

            layout = new Grid(0, 6 + maxConstructDepth);
            layout.setCellPadding(0);
            layout.setCellSpacing(0);

            int row = 0;
            int col = 0;

            if (showAttributes) {
                row = addNode(layoutColumn, layout, map, node, showOccurance, true, row, col,
                        XSDTypeFilter.ATTRIBUTE_FILTER);
            }
            if (showElements) {
                row = addNode(layoutColumn, layout, map, node, showOccurance, false, row, col,
                        XSDTypeFilter.COMPLEX_CONTENT_FILTER);
                row = addNode(layoutColumn, layout, map, node, showOccurance, false, row, col,
                        XSDTypeFilter.STRUCTURE_FILTER);
            }
            if (showComplexTypes) {
                row = addNode(layoutColumn, layout, map, node, showOccurance, false, row, col,
                        XSDTypeFilter.COMPLEX_TYPE_FILTER);
            }
            if (showSimpleTypes) {
                row = addNode(layoutColumn, layout, map, node, showOccurance, false, row, col,
                        XSDTypeFilter.SIMPLE_TYPE_FILTER);
            }

            // Apply some overall styles to surround the content.
            for (row = 0; row < layout.getRowCount(); row++) {
                layout.getCellFormatter().getElement(row, 0).getStyle().setProperty("paddingLeft", "5px");
                layout.getCellFormatter().getElement(row, layout.getColumnCount() - 1).getStyle()
                        .setProperty("paddingRight", "5px");
            }

            // Align all cells to the middle.
            for (row = 0; row < layout.getRowCount(); row++) {
                for (col = 0; col < layout.getColumnCount(); col++) {
                    layout.getCellFormatter().setVerticalAlignment(row, col, HasVerticalAlignment.ALIGN_MIDDLE);
                }
            }

            if (layout.getRowCount() > 0) {
                setRowStyle(layout, 0, "paddingTop", "3px");
                setRowStyle(layout, layout.getRowCount() - 1, "paddingBottom", "3px");
            }

            // If we didn't add any rows then set the layout to null.
            if (row == 0) {
                layout = null;
            }
        }

        return layout;
    }

    private int getMaxConstructDepth(final XSDNode node) {
        boolean hasConstruct = false;
        int maxDepth = 0;

        // Add complex content structures.
        final List<XSDNode> complexContentNodes = node.getChildNodes(
                XSDTypeFilter.COMPLEX_CONTENT_FILTER,
                true);
        for (final XSDNode complexContentNode : complexContentNodes) {
            maxDepth += getMaxConstructDepth(complexContentNode);
        }

        // Add extension node structures.
        final List<XSDNode> extensionNodes = node.getChildNodes(
                XSDTypeFilter.EXTENSION_FILTER,
                true);
        for (final XSDNode extensionNode : extensionNodes) {
            maxDepth += getMaxConstructDepth(extensionNode);
        }

        // Add structure nodes.
        final List<XSDNode> structureNodes = node.getChildNodes(
                XSDTypeFilter.STRUCTURE_FILTER,
                true);
        for (final XSDNode child : structureNodes) {
            final XSDType type = child.getType();

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

    private int addNode(final VerticalPanel layoutColumn,
                        final Grid layout,
                        final SelectionMap map,
                        final XSDNode node,
                        final boolean showOccurrence,
                        final boolean addSeparator,
                        final int initialRow,
                        final int initialCol,
                        final XSDTypeFilter typeFilter) {
        int row = initialRow;
        final int col = initialCol;

        final List<XSDNode> childNodes = node.getChildNodes(typeFilter, true);
        if (childNodes.size() == 0 && node.getType().isStructural()) {
            row++;

        } else {
            if (typeFilter == XSDTypeFilter.COMPLEX_CONTENT_FILTER) {
                // See if we have complex content.
                for (final XSDNode child : childNodes) {
                    final List<XSDNode> extensionNodes = child.getChildNodes(
                            XSDTypeFilter.EXTENSION_FILTER, true);
                    for (final XSDNode extensionNode : extensionNodes) {
                        // Add a type box for the super type above the extension
                        // type.
                        if (layoutColumn != null) {
                            final XSDNode baseNode = extensionNode.getBaseNode();
                            final XSDDisplayBox typeBox = getTypeBox(layoutColumn, map, baseNode);

                            layoutColumn.add(typeBox);
                            final SimplePanel superArrow = new SimplePanel();
                            superArrow.getElement().setClassName("xsdBrowser-superArrowSelect");
                            layoutColumn.add(superArrow);
                            superArrow.getElement().getParentElement().setAttribute(
                                    "textAlign",
                                    "center");
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
                                XSDTypeFilter.ATTRIBUTE_FILTER);

                        row = addNode(
                                layoutColumn,
                                layout,
                                map,
                                extensionNode,
                                showOccurrence,
                                addSeparator,
                                row,
                                col,
                                XSDTypeFilter.STRUCTURE_FILTER);
                    }
                }

            } else {
                // Add child element nodes as normal.
                for (final XSDNode child : childNodes) {
                    final XSDType type = child.getType();

                    if (type == XSDType.SEQUENCE || type == XSDType.CHOICE || type == XSDType.ALL) {
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

    private void addChild(final Grid layout,
                          final SelectionMap map,
                          final XSDNode node,
                          final boolean showOccurance,
                          final int row, final int col) {
        // Ensure layout size.
        if (layout.getRowCount() < row + 1) {
            layout.resizeRows(row + 1);
        }

        final XSDType type = node.getType();
        rowMap.put(node, row);

        // Add the image for the structure element if this is one.
        if (type.isStructural()) {
            final String occurrence = node.getOccurance();

            if (occurrence != null) {
                final SafeHtmlBuilder builder = new SafeHtmlBuilder();
                builder.appendHtmlConstant("<div class=\"occuranceLabel\">");
                builder.appendEscaped(occurrence);
                builder.appendHtmlConstant("</div>");

                final HTML html = new HTML(builder.toSafeHtml());

                final FlowPanel fp = new FlowPanel();
                fp.add(getImage(type));
                fp.add(html);
                fp.getElement().getStyle().setPosition(Position.RELATIVE);

                layout.setWidget(row, col, fp);
            } else {
                layout.setWidget(row, col, getImage(type));
            }

            final SimplePanel panel = new SimplePanel();
            panel.getElement().setClassName("xsdBrowser-xsdTree03");
            layout.setWidget(row, col + 1, panel);

        } else {
            // Otherwise add the element.
            XSDNode refNode = null;

            Widget image = null;
            XSDNodeLabel lblName = null;
            Label lblOccurrence = null;
            Label lblType = null;

            String name = node.getName();
            String valueType = null;

            if (type == XSDType.ELEMENT || type == XSDType.ATTRIBUTE) {
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
                        lblOccurrence = new Label(node.getOccurance(), false);
                    }
                }
            }

            // Get the image to use.
            if (refNode != null) {
                image = getImage(XSDType.ELEMENT_REF);
            } else {
                image = getImage(type);
            }
            if (name != null) {
                lblName = new XSDNodeLabel(name, map, model, node, refNode);
            }
            if (valueType != null) {
                lblType = new Label(valueType, false);
            }

            final int colCount = layout.getColumnCount();

            // Add line images to get back to the structure level.
            if (node.getParent() != null && node.getParent().getType().isStructural()) {
                for (int i = col; i < colCount - 6; i++) {
                    layout.setWidget(row, i, image("xsdBrowser-xsdTree03"));
                }
            }

            // Add other images to create the tree lines.
            int pos = col - 1;
            XSDNode parent = node;
            while (pos >= 0 && parent != null && parent.getType().isStructuralOrElement()) {
                if (node == parent || rowMap.get(parent) == row) {
                    if (parent.isFirstChild() && parent.isLastChild()) {
                        layout.setWidget(row,
                                pos,
                                image("xsdBrowser-xsdTree03"));
                    } else if (parent.isFirstChild()) {
                        layout.setWidget(row,
                                pos,
                                image("xsdBrowser-xsdTree06"));
                    } else if (parent.isLastChild()) {
                        layout.setWidget(row,
                                pos,
                                image("xsdBrowser-xsdTree09"));
                    } else {
                        layout.setWidget(row,
                                pos,
                                image("xsdBrowser-xsdTree05"));
                    }
                } else if (!parent.isLastChild()) {
                    layout.setWidget(row, pos, image("xsdBrowser-xsdTree02"));
                }

                parent = parent.getParent();
                pos -= 2;
            }

            if (image != null) {
                layout.setWidget(row, colCount - 6, image);
                image.addStyleName("marginRight");
            }
            if (lblName != null) {
                layout.setWidget(row, colCount - 5, lblName);
            }
            if (lblOccurrence != null) {
                layout.setWidget(row, colCount - 4, new Label("[", false));
                layout.setWidget(row, colCount - 3, lblOccurrence);
                layout.setWidget(row, colCount - 2, new Label("]", false));
            }
            if (lblType != null) {
                layout.setWidget(row, colCount - 1, lblType);
                lblType.addStyleName("marginLeft");
            }
        }
    }

    private Widget image(final String className) {
        final SimplePanel panel = new SimplePanel();
        panel.getElement().setClassName(className);
        return panel;
    }

    private void addSeparator(final Grid layout) {
        // Add a row with a black border at the bottom.
        setRowStyle(layout, layout.getRowCount() - 1, "borderBottom", "1px solid black");
    }

    private void setRowStyle(final Grid layout, final int row, final String name, final String value) {
        if (row >= 0) {
            for (int col = 0; col < layout.getColumnCount(); col++) {
                layout.getCellFormatter().getElement(row, col).getStyle().setProperty(name, value);
            }
        }
    }

    private Widget getImage(final XSDType type) {
        String className = null;
        String title = null;

        switch (type) {
            case ALL:
                className = "xsdBrowser-xsdAll";
                title = "All";
                break;
            case ANY:
                className = "xsdBrowser-xsdAny";
                break;
            case ATTRIBUTE:
                className = "xsdBrowser-xsdAttribute";
                break;
            case CHOICE:
                className = "xsdBrowser-xsdChoice";
                title = "Choice";
                break;
            case COMPLEX_TYPE:
                className = "xsdBrowser-xsdComplexType";
                break;
            case ELEMENT:
                className = "xsdBrowser-xsdElement";
                break;
            case ELEMENT_REF:
                className = "xsdBrowser-xsdElementRef";
                break;
            case SCHEMA:
                className = "xsdBrowser-xsdTitleSchema";
                break;
            case SEQUENCE:
                className = "xsdBrowser-xsdSequence";
                title = "Sequence";
                break;
            case SIMPLE_TYPE:
                className = "xsdBrowser-xsdSimpleType";
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
}
