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

package stroom.query.client;

import stroom.pipeline.structure.client.view.Box;
import stroom.widget.htree.client.treelayout.TreeLayout;
import stroom.widget.htree.client.treelayout.util.DefaultTreeForTreeLayout;

import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

public class ExpressionItemBox extends Box<Item> {

    private final SimplePanel background = new SimplePanel();
    private final TreeLayout<Item> treeLayout;
    private final Item expressionItem;
    private boolean selected;

    public ExpressionItemBox(final TreeLayout<Item> treeLayout, final Item expressionItem,
                             final boolean allowSelection) {
        this.treeLayout = treeLayout;
        this.expressionItem = expressionItem;

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
        updateStyle();
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

    private boolean isEnabled() {
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
