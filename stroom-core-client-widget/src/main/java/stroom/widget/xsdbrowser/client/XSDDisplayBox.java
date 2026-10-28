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

import stroom.widget.text.client.Label;
import stroom.widget.util.client.DoubleSelectTester;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// A box of the schema diagram: its title, which selects its node when clicked (or opens it when
/// double clicked), then any content below it. Its markup is `XSDDisplayBox.ui.xml`.
public class XSDDisplayBox extends Composite implements SelectableItem {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    FlowPanel box;
    @UiField
    ClickPanel title;
    @UiField
    SimplePanel image;
    @UiField
    Label titleText;

    private final DoubleSelectTester doubleClickTest = new DoubleSelectTester();
    private boolean selected;

    /// @param imageClassName The class of the title's image, for the kind of node.
    /// @param titleText      The title.
    /// @param content        What the box shows below its title, or null for nothing.
    /// @param selectionMap   Where the box is recorded as showing its node.
    /// @param model          The schema, told when the box's node is selected.
    /// @param node           The node the box shows, or null for a box that can't be selected.
    public XSDDisplayBox(final String imageClassName,
                         final String titleText,
                         final Widget content,
                         final SelectionMap selectionMap,
                         final XSDModel model,
                         final XSDNode node) {
        initWidget(BINDER.createAndBindUi(this));
        selectionMap.addSelectionItem(node, this);
        image.addStyleName(imageClassName);
        this.titleText.setText(titleText);

        if (node != null) {
            title.addStyleName("xsd-browser__box-title--selectable");
            title.addClickHandler(event -> {
                final boolean doubleClick = doubleClickTest.test(node);
                model.setSelectedItem(node, doubleClick);
            });
            title.addDoubleClickHandler(event -> model.setSelectedItem(node, true));
        }

        // Any content below the title takes the rest of the height
        if (content != null) {
            final ScrollPanel scrollPanel = new ScrollPanel();
            scrollPanel.addStyleName("xsd-browser__box-content");
            scrollPanel.add(content);
            box.add(scrollPanel);
        }

        setSelected(false);
    }

    @Override
    public boolean isSelected() {
        return selected;
    }

    @Override
    public void setSelected(final boolean selected) {
        this.selected = selected;

        if (selected) {
            title.addStyleName("xsd-browser__box-title--selected");
        } else {
            title.removeStyleName("xsd-browser__box-title--selected");
        }
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, XSDDisplayBox> {

    }
}
