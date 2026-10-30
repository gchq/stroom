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

import stroom.svg.client.Icon;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
import stroom.widget.component.client.AriaPanel;
import stroom.widget.spinner.client.SmallBusySpinner;
import stroom.widget.task.client.TaskMonitor;
import stroom.widget.text.client.Label;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.Objects;

/// A document tab: its icon, its label (cut to 50 characters, the whole as its tooltip), its busy
/// spinner and, if it can be closed, its close cross; one that can't is the `tab-bar__tab--fixed`
/// modifier. Its markup is `CurveTab.ui.xml`.
public class CurveTab extends AbstractTab {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final int MAX_TEXT_LENGTH = 50;

    @UiField
    AriaPanel iconPanel;
    @UiField
    Icon icon;
    @UiField
    Label label;
    @UiField
    SmallBusySpinner spinner;
    @UiField
    AriaPanel close;

    public CurveTab(final SvgImage icon,
                    final IconColour iconColour,
                    final String text,
                    final String tooltip,
                    final boolean allowClose) {
        initWidget(BINDER.createAndBindUi(this));
        final String title = Objects.requireNonNullElse(tooltip, text);

        if (icon != null) {
            iconPanel.addStyleName(iconColour.getClassName());
            iconPanel.setTitle(title);
            this.icon.setImage(icon);
        } else {
            // A tab with no icon has room before its label instead (its label is its first child)
            iconPanel.removeFromParent();
        }

        setText(text);
        label.setTitle(title);

        if (!allowClose) {
            addStyleName("tab-bar__tab--fixed");
        }
    }

    public String getText() {
        return label.getText();
    }

    @Override
    public void setText(final String text) {
        if (text.length() > MAX_TEXT_LENGTH) {
            label.setText(text.substring(0, MAX_TEXT_LENGTH) + "...");
        } else {
            label.setText(text);
        }
    }

    @Override
    protected Element getCloseElement() {
        return close.getElement();
    }

    @Override
    public TaskMonitor createTaskMonitor() {
        return spinner.createTaskMonitor();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, CurveTab> {

    }
}
