/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.security.identity.client.view;

import stroom.security.identity.client.presenter.AuthenticationErrorPresenter.AuthenticationErrorView;
import stroom.svg.shared.SvgImage;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class AuthenticationErrorViewImpl extends ViewImpl implements AuthenticationErrorView {

    private final Widget widget;

    @UiField
    SimplePanel image;
    @UiField
    Label errorText;
    @UiField
    HTML genericMessage;

    @Inject
    public AuthenticationErrorViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        image.getElement().setInnerHTML(SvgImage.ERROR.getSvg());
        image.addStyleName(SvgImage.ERROR.getClassName());
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
    }

    @Override
    public void setErrorText(final String errorText) {
        this.errorText.setText(errorText);
    }

    @Override
    public void setGenericMessage(final SafeHtml genericMessage) {
        this.genericMessage.setHTML(genericMessage);
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, AuthenticationErrorViewImpl> {

    }
}
