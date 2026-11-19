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
import java.util.function.Consumer;

/// A document tab: its icon, its label (cut short where it is drawn, the whole as its tooltip), its
/// busy spinner and, if it can be closed, its close cross; one that can't is the
/// `tab-bar__tab--fixed` modifier. Its markup is `CurveTab.ui.xml`.
///
/// Its bar is told when its spinner starts and stops showing ([#setOnBusyChange]), and says so to
/// screen readers.
public class CurveTab extends AbstractTab {

    private static final Binder BINDER = GWT.create(Binder.class);

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

    private Consumer<Boolean> onBusyChange;

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

        spinner.setOnBusyChange(busy -> {
            if (onBusyChange != null) {
                onBusyChange.accept(busy);
            }
        });
    }

    public String getText() {
        return label.getText();
    }

    /// @return The label's element, which its bar measures to shrink the tabs to fit.
    Element getLabelElement() {
        return label.getElement();
    }

    /// Shows the tab's name. A long one is cut short with an ellipsis where it is drawn
    /// (`tab-bar.css`), not in its text, so that its name for screen readers is all of it.
    ///
    /// @param text The name.
    @Override
    public void setText(final String text) {
        label.setText(text);
    }

    @Override
    protected Element getCloseElement() {
        return close.getElement();
    }

    @Override
    public TaskMonitor createTaskMonitor() {
        return spinner.createTaskMonitor();
    }

    @Override
    public boolean isBusy() {
        return spinner.isBusy();
    }

    @Override
    public void setOnBusyChange(final Consumer<Boolean> onBusyChange) {
        this.onBusyChange = onBusyChange;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, CurveTab> {

    }
}
