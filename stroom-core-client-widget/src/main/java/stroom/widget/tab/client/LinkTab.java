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

package stroom.widget.tab.client;

import stroom.util.shared.NullSafe;
import stroom.widget.component.client.AriaPanel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// A link tab: its label, over an invisible bold copy that keeps its width the same when it is
/// selected, and a hotspot that a dashboard pane being dragged highlights. Its markup is
/// `LinkTab.ui.xml`.
public class LinkTab extends AbstractTab {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    AriaPanel sizer;
    @UiField
    Label label;
    @UiField
    SimplePanel hotspot;

    public LinkTab(final String text, final String tooltip) {
        initWidget(BINDER.createAndBindUi(this));
        NullSafe.consumeNonBlankString(tooltip, this::setTitle);
        setText(text);
    }

    public Element getHotspot() {
        return hotspot.getElement();
    }

    public void setHighlight(final boolean highlight) {
        hotspot.setStyleName("tab-bar__hotspot--visible", highlight);
    }

    public String getText() {
        return label.getText();
    }

    @Override
    public void setText(final String text) {
        sizer.getElement().setInnerText(text);
        label.setText(text);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, LinkTab> {

    }
}
