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

package stroom.datagen.client.view;

import stroom.datagen.client.presenter.DataGenSettingsPresenter.DataGenSettingsView;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class DataGenSettingsViewImpl extends ViewImpl implements DataGenSettingsView, ReadOnlyChangeHandler {

    private final Widget widget;

    @UiField
    TextBox template;

    @Inject
    public DataGenSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public TextBox getTemplate() {
        return template;
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        template.setEnabled(!readOnly);
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, DataGenSettingsViewImpl> {

    }
}
