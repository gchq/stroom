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

package stroom.security.identity.client.component;

import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.user.client.ui.Widget;

/// The password reset page: only the sign in page's background, which the new password dialog is
/// shown over.
public class ResetPassword extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    /// Makes the page.
    public ResetPassword() {
        initWidget(BINDER.createAndBindUi(this));
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ResetPassword> {

    }
}
