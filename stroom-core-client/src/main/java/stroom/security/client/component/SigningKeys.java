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

package stroom.security.client.component;

import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// The Signing Keys screen: the keys that sign Stroom's tokens.
public class SigningKeys extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SimplePanel listContainer;

    /// Makes the screen, with no content until it is given it.
    public SigningKeys() {
        initWidget(BINDER.createAndBindUi(this));
    }


    /// Shows the list of signing keys.
    ///
    /// @param view Its view.
    public void setList(final IsWidget view) {
        listContainer.setWidget(view.asWidget());
    }

    // --------------------------------------------------------------------------------

    interface Binder extends UiBinder<Widget, SigningKeys> {

    }
}
