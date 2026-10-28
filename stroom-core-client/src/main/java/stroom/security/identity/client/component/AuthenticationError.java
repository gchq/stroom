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

package stroom.security.identity.client.component;

import stroom.svg.shared.SvgImage;
import stroom.widget.component.client.Component;
import stroom.widget.text.client.Html;
import stroom.widget.text.client.Label;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// The page shown when signing in fails: the error, and a message from the UI config.
public class AuthenticationError extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SimplePanel image;
    @UiField
    Label errorText;
    @UiField
    Html genericMessage;

    /// Makes an empty page.
    public AuthenticationError() {
        initWidget(BINDER.createAndBindUi(this));

        image.getElement().setInnerHTML(SvgImage.ERROR.getSvg());
        image.addStyleName(SvgImage.ERROR.getClassName());
    }

    /// @param errorText What went wrong.
    public void setErrorText(final String errorText) {
        this.errorText.setText(errorText);
    }

    /// @param genericMessage The message every authentication error shows, e.g. who to contact.
    public void setGenericMessage(final SafeHtml genericMessage) {
        this.genericMessage.setHTML(genericMessage);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, AuthenticationError> {

    }
}
