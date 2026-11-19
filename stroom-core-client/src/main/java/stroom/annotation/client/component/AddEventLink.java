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

package stroom.annotation.client.component;

import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The Link An Event dialog's content: the event's id, as stream id and event id.
public class AddEventLink extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextBox event;

    private Runnable onEnter = () -> {
    };

    /// Makes empty content.
    public AddEventLink() {
        initWidget(BINDER.createAndBindUi(this));
        event.addKeyDownHandler(event -> {
            if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
                onEnter.run();
            }
        });
    }

    /// Puts the focus in the event id.
    public void focus() {
        event.setFocus(true);
    }

    /// @return The event id entered.
    public String getText() {
        return event.getText();
    }

    /// @param onEnter What Enter in the event id does, e.g. press the dialog's OK.
    public void setOnEnter(final Runnable onEnter) {
        this.onEnter = onEnter;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, AddEventLink> {

    }
}
