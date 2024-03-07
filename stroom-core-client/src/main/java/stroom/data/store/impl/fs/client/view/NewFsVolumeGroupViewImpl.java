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

package stroom.data.store.impl.fs.client.view;

import stroom.data.store.impl.fs.client.presenter.NewFsVolumeGroupPresenter.NewFsVolumeGroupView;
import stroom.util.shared.GwtNullSafe;
import stroom.widget.popup.client.view.HideRequestUiHandlers;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class NewFsVolumeGroupViewImpl
        extends ViewWithUiHandlers<HideRequestUiHandlers>
        implements NewFsVolumeGroupView {

    private final Widget widget;

    @UiField
    TextBox name;
    @UiField
    CustomCheckBox isDefaultCheckBox;

    @Inject
    public NewFsVolumeGroupViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        name.setFocus(true);
    }

    @Override
    public String getName() {
        return this.name.getText();
    }

    @Override
    public void setName(final String name) {
        this.name.setText(name);
    }

    @Override
    public boolean isDefault() {
        return GwtNullSafe.isTrue(this.isDefaultCheckBox.getValue());
    }

    @Override
    public void setDefault(final boolean isDefault) {
        this.isDefaultCheckBox.setValue(isDefault);
    }

    @Override
    public void setUiHandlers(final HideRequestUiHandlers hideRequestUiHandlers) {

    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, NewFsVolumeGroupViewImpl> {

    }
}
