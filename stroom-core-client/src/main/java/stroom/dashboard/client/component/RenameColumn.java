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
import stroom.widget.form.client.TextBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The Rename Field dialog's content: the column's new name.
public class RenameColumn extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextBox name;

    private Runnable onEnter = () -> {
    };

    /// Makes an empty name.
    public RenameColumn() {
        initWidget(BINDER.createAndBindUi(this));
        name.addKeyDownHandler(event -> {
            if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
                onEnter.run();
            }
        });
    }

    /// Puts the focus in the name.
    public void focus() {
        name.setFocus(true);
    }

    /// @return The name typed.
    public String getName() {
        return name.getText();
    }

    /// @param name The name to start with.
    public void setName(final String name) {
        this.name.setText(name);
    }

    /// @param onEnter What Enter in the name does, e.g. press the dialog's OK.
    public void setOnEnter(final Runnable onEnter) {
        this.onEnter = onEnter;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, RenameColumn> {

    }
}
