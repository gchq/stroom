/*
 * Copyright 2018 Crown Copyright
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
import stroom.widget.form.client.AccessibleName;
import stroom.widget.form.client.TextBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// A dashboard's Text Input pane: one text box, whose value is a query parameter.
public class TextInput extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextBox value;

    private Consumer<String> onChange = text -> {
    };

    /// Makes the pane, empty.
    public TextInput() {
        initWidget(BINDER.createAndBindUi(this));
        // On each key, as the old pane did, so the dashboard is changed as the user types
        value.addKeyUpHandler(event -> onChange.accept(value.getText()));
    }

    /// @param value The text to show.
    public void setValue(final String value) {
        this.value.setValue(value);
    }

    /// @return The text typed.
    public String getValue() {
        return value.getValue();
    }

    /// Names the box for screen readers.
    ///
    /// @param name The box's name: the pane's name.
    public void setAccessibleName(final String name) {
        AccessibleName.set(value, name);
    }

    /// @param onChange Given the text when the user types.
    public void setOnChange(final Consumer<String> onChange) {
        this.onChange = onChange;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, TextInput> {

    }
}
