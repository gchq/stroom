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

import stroom.pipeline.structure.client.component.Box;
import stroom.svg.shared.SvgImage;
import stroom.widget.htree.client.treelayout.TreeLayout;
import stroom.widget.htree.client.treelayout.util.DefaultTreeForTreeLayout;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// An item of an expression tree (a term or an operator): its label, or its editor while it is
/// being edited. It is an item of the tree for assistive technology too (see [#setTreeItem]).
///
/// Its looks (expression-item.css, named BEM style) are drawn on its row: its label, or the row of
/// its editor's fields. They come from its ARIA attributes where it has them (`aria-selected`, only
/// in a tree that can be changed, and the focus) and from modifiers: `--disabled` for an item left
/// out of the expression (which can still be selected and edited, so not `aria-disabled`; its name
/// says so), and those of moving items. While an item is moved (dragged, or detached with the
/// keyboard) it shows whether it would take it ([#showHotspot], [#showRefused]), and the item moved
/// shows that it is ([#setDetached]).
public class ExpressionItemBox extends Box<Item> {

    private static final String BLOCK = "expression-item";
    private static final String ROW_CLASS = BLOCK + "__row";
    private static final String LABEL_CLASS = BLOCK + "__label";
    private static final String REFUSED_ICON_CLASS = BLOCK + "__refused-icon";
    private static final String DISABLED_CLASS = BLOCK + "--disabled";
    private static final String TARGET_CLASS = BLOCK + "--target";
    private static final String REFUSED_CLASS = BLOCK + "--refused";
    private static final String DETACHED_CLASS = BLOCK + "--detached";

    // Holds its editor, if it has one
    private final SimplePanel background = new SimplePanel();
    private final TreeLayout<Item> treeLayout;
    private final Item expressionItem;
    private final boolean allowSelection;
    // Its label, or the row of its editor's fields
    private Element row;
    // The "not allowed" icon, at the start of the item's row while it refuses what is moved to it (so
    // that that isn't told by colour alone), which is only in the document then
    private Element refusedIcon;
    // Its name (e.g. "StreamId > 1000"), and what is said after it while an item is detached
    private String name;
    private boolean detached;
    private String detachedName;
    private boolean canTake;

    public ExpressionItemBox(final TreeLayout<Item> treeLayout, final Item expressionItem,
                             final boolean allowSelection) {
        this.treeLayout = treeLayout;
        this.expressionItem = expressionItem;
        this.allowSelection = allowSelection;

        background.setStyleName(BLOCK);
        initWidget(background);
        toggleClass(DISABLED_CLASS, !isEnabled());
    }

    /// Shows the item as its label, e.g. "StreamId > 1000" or "OR".
    ///
    /// @param text Its text.
    public void setLabel(final String text) {
        row = Document.get().createDivElement();
        row.setClassName(ROW_CLASS + " " + LABEL_CLASS);
        row.setInnerText(text);
        getElement().appendChild(row);
    }

    /// Shows the item as its editor.
    ///
    /// @param editor The editor.
    /// @param row    The row of its fields, on which the item's looks are drawn.
    public void setEditor(final Widget editor, final Element row) {
        background.setWidget(editor);
        this.row = row;
        row.addClassName(ROW_CLASS);
    }

    /// Selects (or not) the item: `aria-selected`, which its looks follow, in a tree whose items
    /// can be selected (one that can be changed).
    ///
    /// @param selected Whether it is selected.
    @Override
    public void setSelected(final boolean selected) {
        if (allowSelection) {
            getElement().setAttribute("aria-selected", String.valueOf(selected));
        }
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
        this.name = name;
        updateName();
    }

    /// Shows (or stops showing) that the item would take what is moved to it: a green border.
    ///
    /// @param show Whether it would.
    @Override
    public void showHotspot(final boolean show) {
        toggleClass(TARGET_CLASS, show);
    }

    /// Shows (or stops showing) that the item refuses what is moved to it (a term can't have
    /// children): a red border, and the "not allowed" icon at the start of its row.
    ///
    /// @param refused Whether it refuses it.
    @Override
    public void showRefused(final boolean refused) {
        toggleClass(REFUSED_CLASS, refused);
        if (refused && row != null) {
            if (refusedIcon == null) {
                refusedIcon = createRefusedIcon();
            }
            if (refusedIcon.getParentElement() != row) {
                row.insertFirst(refusedIcon);
            }
        } else if (refusedIcon != null) {
            refusedIcon.removeFromParent();
        }
    }

    /// Shows (or stops showing) that the item is detached, or in the operator detached, to be
    /// attached to another operator: faded, with a dashed border, and said so in its name.
    ///
    /// @param detached Whether it is.
    @Override
    public void setDetached(final boolean detached) {
        this.detached = detached;
        toggleClass(DETACHED_CLASS, detached);
        updateName();
    }

    /// Says in the item's name whether it can take an item detached to be attached to another
    /// operator, e.g. "OR, 3 items, can take StreamId > 1000", so that it is known before trying.
    ///
    /// @param detachedName The detached item's name, or null when none is detached (or this is
    ///                     it, or in it).
    /// @param canTake      Whether this item can take it.
    @Override
    public void setDetachedName(final String detachedName, final boolean canTake) {
        this.detachedName = detachedName;
        this.canTake = canTake;
        updateName();
    }

    // e.g. "OR, 3 items", "StreamId > 1000, detached" or "OR, 3 items, can take StreamId > 1000"
    private void updateName() {
        if (name == null) {
            return;
        }
        final StringBuilder sb = new StringBuilder(name);
        if (detached) {
            sb.append(", detached");
        }
        if (detachedName != null) {
            sb.append(canTake
                            ? ", can take "
                            : ", can't take ")
                    .append(detachedName);
        }
        getElement().setAttribute("aria-label", sb.toString());
    }

    // Its meaning is in the box's name (see setDetachedName), so it is hidden from assistive
    // technology
    private static Element createRefusedIcon() {
        final Element holder = Document.get().createDivElement();
        holder.setInnerSafeHtml(SvgImageUtil.toSafeHtml(SvgImage.NOT_ALLOWED,
                "icon-wrap", REFUSED_ICON_CLASS));
        final Element icon = holder.getFirstChildElement();
        icon.removeFromParent();
        icon.setAttribute("aria-hidden", "true");
        return icon;
    }

    private void toggleClass(final String className, final boolean isSet) {
        if (isSet) {
            getElement().addClassName(className);
        } else {
            getElement().removeClassName(className);
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
