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

import stroom.widget.util.client.DoubleSelectTester;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.DoubleClickEvent;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Label;

public class XSDNodeLabel extends Composite implements SelectableItem {

    private final DoubleSelectTester doubleClickTest = new DoubleSelectTester();
    private boolean selected;

    public XSDNodeLabel(final String title, final SelectionMap selectionMap, final XSDModel model, final XSDNode node,
                        final XSDNode refNode) {
        final Label label = new Label(title);
        label.addStyleName("nodeLabel");
        label.getElement().setAttribute("unselectable", "on");

        selectionMap.addSelectionItem(node, this);
        selectionMap.addSelectionItem(refNode, this);

        initWidget(label);

        addDomHandler(event -> {
            XSDNode select = null;
            if (refNode != null) {
                select = refNode;
            } else {
                select = node;
            }

            final boolean doubleClick = doubleClickTest.test(select);
            model.setSelectedItem(select, doubleClick);
        }, ClickEvent.getType());
        addDomHandler(event -> model.setSelectedItem(node, true), DoubleClickEvent.getType());
    }

    @Override
    public boolean isSelected() {
        return selected;
    }

    @Override
    public void setSelected(final boolean selected) {
        this.selected = selected;

        final Element td = getElement().getParentElement();
        final Element tr = td.getParentElement();

        if (selected) {
            tr.addClassName("nodeLabelSelected");
        } else {
            tr.removeClassName("nodeLabelSelected");
        }
    }
}
