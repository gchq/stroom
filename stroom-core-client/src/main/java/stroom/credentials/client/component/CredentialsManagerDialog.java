/*
 * Copyright 2025 Crown Copyright
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

package stroom.credentials.client.component;

import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// The content of the credentials picker: the credentials, under an optional note on where to get
/// them.
public class CredentialsManagerDialog extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    /// Introduces the credentials and tells users where to get the credentials from
    @UiField
    HTML lblHtml;
    @UiField
    SimplePanel pnlCredentialsList;

    /// Makes empty content, with no note.
    public CredentialsManagerDialog() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @param list The credentials.
    public void setCredentialsList(final IsWidget list) {
        pnlCredentialsList.setWidget(list.asWidget());
    }

    /// @param html The note on where to get credentials, or `null` for none, which hides it.
    public void setLabelHtml(final SafeHtml html) {
        if (html != null) {
            lblHtml.setHTML(html);
            lblHtml.setVisible(true);
        } else {
            lblHtml.setText("");
            lblHtml.setVisible(false);
        }
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, CredentialsManagerDialog> {

    }
}
