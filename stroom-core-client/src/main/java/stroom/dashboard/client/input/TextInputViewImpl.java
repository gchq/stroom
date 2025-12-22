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

package stroom.dashboard.client.input;

import stroom.dashboard.client.input.TextInputPresenter.TextInputView;

import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class TextInputViewImpl extends ViewWithUiHandlers<TextInputUiHandlers> implements TextInputView {

    private final Widget widget;

    @UiField
    TextBox value;

    @Inject
    public TextInputViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setValue(final String value) {
        this.value.setValue(value);
    }

    @Override
    public String getValue() {
        return value.getValue();
    }

    @UiHandler("value")
    public void onValueKeyUp(final KeyUpEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onValueChanged(value.getText());
        }
    }

    public interface Binder extends UiBinder<Widget, TextInputViewImpl> {

    }
}
