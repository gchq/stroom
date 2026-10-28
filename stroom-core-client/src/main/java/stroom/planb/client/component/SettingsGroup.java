/*
 * Copyright 2021 Crown Copyright
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

package stroom.planb.client.component;

import stroom.widget.form.client.FormLabel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HasWidgets;
import com.google.gwt.user.client.ui.Widget;

import java.util.Collections;
import java.util.Iterator;

/// A titled group of settings, e.g. Plan B's or the floor map's Retention: its label, then the
/// settings. Its markup is `SettingsGroup.ui.xml`.
public class SettingsGroup extends Composite implements HasWidgets {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    FlowPanel settingsGroupPanel;
    @UiField
    FormLabel formLabel;

    private Widget childWidget = null;

    public SettingsGroup() {
        initWidget(BINDER.createAndBindUi(this));
    }

    public void setLabel(final String label) {
        formLabel.setLabel(label);
    }

    public String getLabel() {
        return formLabel.getLabel();
    }

    @Override
    public void add(final Widget widget) {
        clear();
        this.childWidget = widget;
        widget.addStyleName("allow-focus");
        // After the label
        settingsGroupPanel.add(widget);
    }

    @Override
    public void clear() {
        if (childWidget != null) {
            settingsGroupPanel.remove(childWidget);
            childWidget = null;
        }
    }

    @Override
    public Iterator<Widget> iterator() {
        return Collections.singleton(childWidget).iterator();
    }

    @Override
    public boolean remove(final Widget w) {
        if (childWidget == w) {
            clear();
            return true;
        } else {
            return false;
        }
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<FlowPanel, SettingsGroup> {

    }
}
