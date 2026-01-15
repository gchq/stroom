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

import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;

public class XSDDisplayBox extends Composite implements SelectableItem {

    private final XSDDisplayBoxTitle boxTitle;
    private final VerticalPanel layout;
    private final VerticalPanel outerBox;
    private boolean selected;

    public XSDDisplayBox(final String imageClassName, final String title, final Widget content,
                         final SelectionMap selectionMap, final XSDModel model, final XSDNode node, final String width,
                         final String height) {
        selectionMap.addSelectionItem(node, this);
        boxTitle = new XSDDisplayBoxTitle(imageClassName, title, model, node);
        boxTitle.setStylePrimaryName("boxTitle");

        layout = new VerticalPanel();
        layout.setStyleName("box");

        layout.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_CENTER);
        layout.add(boxTitle);
        layout.setCellHeight(boxTitle, "1px");

        if (content != null) {
            final ScrollPanel scrollPanel = new ScrollPanel();
            scrollPanel.add(content);
            layout.add(scrollPanel);
            layout.setCellHeight(scrollPanel, "100%");
            boxTitle.addStyleDependentName("noContent");
        }

        outerBox = new VerticalPanel();
        outerBox.setStyleName("outerBox");
        outerBox.add(layout);

        if (width != null) {
            outerBox.setWidth(width);
        }
        if (height != null) {
            outerBox.setHeight(height);
        }

        initWidget(outerBox);

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
            boxTitle.addStyleDependentName("selected");
        } else {
            boxTitle.removeStyleDependentName("selected");
        }
    }
}
