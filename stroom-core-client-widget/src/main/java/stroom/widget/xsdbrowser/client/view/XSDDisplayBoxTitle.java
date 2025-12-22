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

import com.google.gwt.dom.client.Style.Cursor;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;

public class XSDDisplayBoxTitle extends Composite {

    private final SimplePanel imgImage;
    private final Label lblTitle;
    private final ClickPanel layout;
    private final DoubleSelectTester doubleClickTest = new DoubleSelectTester();

    public XSDDisplayBoxTitle(final String imageClassName,
                              final String title,
                              final XSDModel model,
                              final XSDNode node) {
        imgImage = new SimplePanel();
        imgImage.getElement().setClassName(imageClassName);
        lblTitle = new Label(title, false);
        lblTitle.getElement().setAttribute("unselectable", "on");
        lblTitle.getElement().getStyle().setColor("white");

        final HorizontalPanel hp = new HorizontalPanel();
        hp.setVerticalAlignment(HasVerticalAlignment.ALIGN_MIDDLE);
        hp.add(imgImage);
        hp.add(lblTitle);

        imgImage.addStyleName("marginRight");

        layout = new ClickPanel();
        layout.add(hp);

        if (node != null) {
            setSelectable(true);
            layout.addClickHandler(event -> {
                final boolean doubleClick = doubleClickTest.test(node);
                model.setSelectedItem(node, doubleClick);
            });
            layout.addDoubleClickHandler(event -> model.setSelectedItem(node, true));
        }

        initWidget(layout);
    }

    private void setSelectable(final boolean selectable) {
        if (selectable) {
            layout.getElement().getStyle().setCursor(Cursor.POINTER);
            imgImage.getElement().getStyle().setCursor(Cursor.POINTER);
            lblTitle.getElement().getStyle().setCursor(Cursor.POINTER);
        } else {
            layout.getElement().getStyle().setCursor(Cursor.DEFAULT);
            imgImage.getElement().getStyle().setCursor(Cursor.DEFAULT);
            lblTitle.getElement().getStyle().setCursor(Cursor.DEFAULT);
        }
    }
}
