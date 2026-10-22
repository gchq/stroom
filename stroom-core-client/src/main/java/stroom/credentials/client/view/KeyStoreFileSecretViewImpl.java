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

import stroom.ai.shared.KeyStoreType;
import stroom.credentials.client.presenter.KeyStoreFileSecretPresenter.KeyStoreFileSecretView;
import stroom.item.client.SelectionBox;
import stroom.widget.form.client.PasswordBox;
import stroom.widget.presenter.client.ViewImpl;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

import java.util.Arrays;
import java.util.stream.Collectors;

/// Backs KeyStoreFileSecretViewImpl.ui.xml.
public class KeyStoreFileSecretViewImpl extends ViewImpl implements KeyStoreFileSecretView {

    private final Widget widget;

    @UiField
    SelectionBox<KeyStoreType> type;
    @UiField
    TextBox path;
    @UiField
    PasswordBox password;

    /// @param binder Binds KeyStoreFileSecretViewImpl.ui.xml to this view.
    @Inject
    public KeyStoreFileSecretViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        type.addItems(Arrays.stream(KeyStoreType.values()).sorted().collect(Collectors.toList()));
        type.setValue(KeyStoreType.PKCS12);

        path.getElement().setAttribute("placeholder", "/etc/stroom/certs/client.p12");
        password.setPlaceholder("Enter Password");
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        type.focus();
    }

    @Override
    public void setType(final KeyStoreType type) {
        this.type.setValue(type);
    }

    @Override
    public KeyStoreType getType() {
        return type.getValue();
    }

    @Override
    public String getPath() {
        return path.getValue();
    }

    @Override
    public void setPath(final String path) {
        this.path.setValue(path);
    }

    @Override
    public String getPassword() {
        return password.getValue();
    }

    @Override
    public void setPassword(final String password) {
        this.password.setValue(password);
    }

    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, KeyStoreFileSecretViewImpl> {

    }
}
