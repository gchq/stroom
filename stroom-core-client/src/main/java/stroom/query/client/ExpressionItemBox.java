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

package stroom.query.client;

import stroom.pipeline.structure.client.view.Box;
import stroom.widget.htree.client.treelayout.TreeLayout;
import stroom.widget.htree.client.treelayout.util.DefaultTreeForTreeLayout;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// An item of an expression tree (a term or an operator): its label, or its editor while it is
/// being edited. It is an item of the tree for assistive technology too (see [#setTreeItem]).
public class ExpressionItemBox extends Box<Item> {

    private final SimplePanel background = new SimplePanel();
    private final TreeLayout<Item> treeLayout;
    private final Item expressionItem;
    private final boolean allowSelection;
    private boolean selected;

    public ExpressionItemBox(final TreeLayout<Item> treeLayout, final Item expressionItem,
                             final boolean allowSelection) {
        this.treeLayout = treeLayout;
        this.expressionItem = expressionItem;
        this.allowSelection = allowSelection;

        if (allowSelection) {
            background.setStyleName("expressionItemBox-background expressionItemBox-selectable");
        } else {
            background.setStyleName("expressionItemBox-background");
        }

        initWidget(background);
    }

    public void setInnerWidget(final Widget innerWidget) {
        background.setWidget(innerWidget);
    }

    @Override
    public void setSelected(final boolean selected) {
        this.selected = selected;
        if (allowSelection) {
            getElement().setAttribute("aria-selected", String.valueOf(selected));
        }
        updateStyle();
    }

    /// Makes the box an item of the tree for assistive technology (`role="treeitem"`), with a name,
    /// at its place in the tree, and whether it is the tree's one tab stop.
    ///
    /// @param level       Its depth in the tree, from 1 for the root operator.
    /// @param position    Its position among its siblings, from 1.
    /// @param count       How many siblings it has, itself included.
    /// @param hasChildren Whether it has children (which are always shown).
    /// @param tabStop     Whether it is the tree's tab stop.
    /// @param name        Its name, e.g. "StreamId > 1000" or "OR, 2 items".
    public void setTreeItem(final int level,
                            final int position,
                            final int count,
                            final boolean hasChildren,
                            final boolean tabStop,
                            final String name) {
        final Element element = getElement();
        element.setAttribute("role", "treeitem");
        element.setAttribute("aria-level", String.valueOf(level));
        element.setAttribute("aria-posinset", String.valueOf(position));
        element.setAttribute("aria-setsize", String.valueOf(count));
        // Always expanded, as the tree can't be collapsed: in a flat tree (items with aria-level)
        // it is what tells assistive technology that an item has children
        if (hasChildren) {
            element.setAttribute("aria-expanded", "true");
        } else {
            element.removeAttribute("aria-expanded");
        }
        element.setAttribute("tabindex", tabStop
                ? "0"
                : "-1");
        element.setAttribute("aria-label", name);
    }

    @Override
    public void showHotspot(final boolean show) {
        if (show) {
            getElement().addClassName("expressionItemBox-hotspot");
        } else {
            getElement().removeClassName("expressionItemBox-hotspot");
        }
    }

    private void updateStyle() {
        final boolean enabled = isEnabled();

        if (enabled) {
            getElement().removeClassName("expressionItemBox-disabled");
        } else {
            getElement().addClassName("expressionItemBox-disabled");
        }

        if (selected) {
            getElement().addClassName("expressionItemBox-selected");
        } else {
            getElement().removeClassName("expressionItemBox-selected");
        }
    }

    /// @return Whether the item, and every operator it is in, is enabled.
    public boolean isEnabled() {
        final DefaultTreeForTreeLayout<Item> tree = (DefaultTreeForTreeLayout<Item>) treeLayout
                .getTree();

        Item item = expressionItem;
        while (item != null) {
            if (!item.isEnabled()) {
                return false;
            }
            item = tree.getParent(item);
        }

        return true;
    }

    @Override
    public Item getItem() {
        return expressionItem;
    }
}
