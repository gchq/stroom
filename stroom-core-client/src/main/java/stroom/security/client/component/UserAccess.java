/*
 * Copyright 2023 Crown Copyright
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
import com.google.gwt.user.client.ui.Widget;

/// The User Access screen: who has access, and the selected user's sessions.
public class UserAccess extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    UserAccessList list;
    @UiField
    UserSessionsList sessions;

    /// Makes the screen, with its lists empty.
    public UserAccess() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @return The list of who has access.
    public UserAccessList getList() {
        return list;
    }

    /// @return The selected user's sessions.
    public UserSessionsList getSessions() {
        return sessions;
    }

    // --------------------------------------------------------------------------------

    interface Binder extends UiBinder<Widget, UserAccess> {

    }
}
