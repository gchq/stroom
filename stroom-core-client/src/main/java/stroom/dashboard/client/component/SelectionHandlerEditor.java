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

package stroom.dashboard.client.component;

import stroom.widget.component.client.Component;
import stroom.widget.tickbox.client.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// A dashboard query's selection handler: whether it is enabled, the expression it adds when other
/// components have a selection, and the selection now, to insert from.
public class SelectionHandlerEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SimplePanel layout;
    @UiField
    TickBox enabled;
    @UiField
    SimplePanel currentSelection;

    /// Makes the form, empty.
    public SelectionHandlerEditor() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// Puts the keyboard focus on Enabled.
    public void focus() {
        enabled.setFocus(true);
    }

    /// @param expressionEditor The expression editor, a controller of its own.
    public void setExpressionEditor(final IsWidget expressionEditor) {
        layout.add(expressionEditor.asWidget());
    }

    /// @param currentSelection The selection now, a controller of its own.
    public void setCurrentSelection(final IsWidget currentSelection) {
        this.currentSelection.setWidget(currentSelection.asWidget());
    }

    /// @return Whether the handler is enabled.
    public boolean isEnabled() {
        return enabled.getValue();
    }

    /// @param enabled Whether the handler is enabled.
    public void setEnabled(final boolean enabled) {
        this.enabled.setValue(enabled);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, SelectionHandlerEditor> {

    }
}
