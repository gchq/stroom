/*
 * Copyright 2024 Crown Copyright
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
import stroom.credentials.client.presenter.KeyStoreSecretPresenter.KeyStoreSecretView;
import stroom.document.client.event.DirtyUiHandlers;
import stroom.item.client.SelectionBox;
import stroom.widget.form.client.CustomFileUpload;
import stroom.widget.form.client.PasswordBox;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.Arrays;
import java.util.stream.Collectors;

public class KeyStoreSecretViewImpl
        extends ViewWithUiHandlers<DirtyUiHandlers>
        implements KeyStoreSecretView {

    private final Widget widget;

    @UiField
    SelectionBox<KeyStoreType> type;
    @UiField
    CustomFileUpload fileUpload;
    @UiField
    PasswordBox password;

    @Inject
    public KeyStoreSecretViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        type.addItems(Arrays.stream(KeyStoreType.values()).sorted().collect(Collectors.toList()));
        type.setValue(KeyStoreType.PKCS12);

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
    public String getPassword() {
        return password.getValue();
    }

    @Override
    public void setPassword(final String password) {
        this.password.setValue(password);
    }

    @Override
    public CustomFileUpload getFileUpload() {
        return fileUpload;
    }

    public interface Binder extends UiBinder<Widget, KeyStoreSecretViewImpl> {

    }
}
