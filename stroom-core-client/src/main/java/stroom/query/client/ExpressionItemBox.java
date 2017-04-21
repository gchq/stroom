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

import com.google.gwt.core.shared.GWT;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import stroom.pipeline.structure.client.view.Box;
import stroom.query.shared.ExpressionItem;
import stroom.widget.htree.client.treelayout.TreeLayout;
import stroom.widget.htree.client.treelayout.util.DefaultTreeForTreeLayout;

public class ExpressionItemBox extends Box<ExpressionItem> {
    public interface Style extends CssResource {
        String DEFAULT = "ExpressionItemBox.css";

        String background();

        String selectable();

        String backgroundSelected();

        String backgroundSelectedDisabled();

        String hotspot();

        String image();

        String label();

        String labelDisabled();
    }

    public interface Resources extends ClientBundle {
        @Source(Style.DEFAULT)
        Style style();
    }

    private static Resources resources;

    private final SimplePanel background = new SimplePanel();
    private final TreeLayout<ExpressionItem> treeLayout;
    private final ExpressionItem expressionItem;
    private boolean selected;
    private Widget innerWidget;

    public ExpressionItemBox(final TreeLayout<ExpressionItem> treeLayout, final ExpressionItem expressionItem,
            final boolean allowSelection) {
        this.treeLayout = treeLayout;
        this.expressionItem = expressionItem;

        if (resources == null) {
            resources = GWT.create(Resources.class);
            resources.style().ensureInjected();
        }

        if (allowSelection) {
            background.setStyleName(resources.style().background() + " " + resources.style().selectable());
        } else {
            background.setStyleName(resources.style().background());
        }

        initWidget(background);
    }

    public void setInnerWidget(final Widget innerWidget) {
        this.innerWidget = innerWidget;
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
            getElement().addClassName(resources.style().hotspot());
        } else {
            getElement().removeClassName(resources.style().hotspot());
        }
    }

    private void updateStyle() {
        final boolean enabled = isEnabled();

        if (innerWidget != null && innerWidget instanceof Label) {
            innerWidget.addStyleName(resources.style().label());
            if (enabled) {
                innerWidget.removeStyleName(resources.style().labelDisabled());
            } else {
                innerWidget.addStyleName(resources.style().labelDisabled());
            }
        }

        if (selected) {
            if (enabled) {
                getElement().addClassName(resources.style().backgroundSelected());
            } else {
                getElement().addClassName(resources.style().backgroundSelectedDisabled());
            }
        } else {
            getElement().removeClassName(resources.style().backgroundSelected());
            getElement().removeClassName(resources.style().backgroundSelectedDisabled());
        }
    }

    private boolean isEnabled() {
        final DefaultTreeForTreeLayout<ExpressionItem> tree = (DefaultTreeForTreeLayout<ExpressionItem>) treeLayout
                .getTree();

        ExpressionItem item = expressionItem;
        while (item != null) {
            if (!item.isEnabled()) {
                return false;
            }
            item = tree.getParent(item);
        }

        return true;
    }

    @Override
    public ExpressionItem getItem() {
        return expressionItem;
    }
}
