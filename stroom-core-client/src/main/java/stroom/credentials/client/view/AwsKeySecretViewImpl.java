/*
 * Copyright 2026 Crown Copyright
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

package stroom.credentials.client.view;

import stroom.credentials.client.presenter.AwsKeySecretPresenter.AwsKeySecretView;
import stroom.document.client.event.DirtyUiHandlers;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.InlineSvgButton;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import javax.inject.Inject;

/// The fields of an AWS access key credential.
public class AwsKeySecretViewImpl
        extends ViewWithUiHandlers<DirtyUiHandlers>
        implements AwsKeySecretView {

    private final Widget widget;

    @UiField
    TextBox accessKeyId;
    @UiField
    PasswordTextBox secretAccessKey;
    @UiField
    InlineSvgButton showSecretAccessKey;

    /// @param binder Binds the UiBinder template.
    @Inject
    @SuppressWarnings("unused")
    public AwsKeySecretViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        showSecretAccessKey.setSvg(SvgImage.EYE);
        showSecretAccessKey.setTitle("Show Secret Access Key");
        showSecretAccessKey.setEnabled(true);
        secretAccessKey.getElement().setAttribute("placeholder", "Enter Secret Access Key");
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public String getAccessKeyId() {
        return accessKeyId.getValue();
    }

    @Override
    public void setAccessKeyId(final String accessKeyId) {
        this.accessKeyId.setValue(accessKeyId);
    }

    @Override
    public String getSecretAccessKey() {
        return secretAccessKey.getValue();
    }

    @Override
    public void setSecretAccessKey(final String secretAccessKey) {
        this.secretAccessKey.setValue(secretAccessKey);
    }

    @Override
    public void focus() {
        accessKeyId.setFocus(true);
    }

    /// Shows or hides the secret access key.
    ///
    /// @param e The click.
    @UiHandler("showSecretAccessKey")
    public void onShowSecretAccessKey(final ClickEvent e) {
        final String type = secretAccessKey.getElement().getAttribute("type");
        if (type == null || "password".equals(type)) {
            secretAccessKey.getElement().setAttribute("type", "text");
            showSecretAccessKey.setSvg(SvgImage.EYE_OFF);
            showSecretAccessKey.setTitle("Hide Secret Access Key");
        } else {
            secretAccessKey.getElement().setAttribute("type", "password");
            showSecretAccessKey.setSvg(SvgImage.EYE);
            showSecretAccessKey.setTitle("Show Secret Access Key");
        }
    }


    // --------------------------------------------------------------------------------


    /// Binds the UiBinder template.
    public interface Binder extends UiBinder<Widget, AwsKeySecretViewImpl> {

    }
}
