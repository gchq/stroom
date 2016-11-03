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

package stroom.dictionary.client.view;

import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

import stroom.dictionary.client.presenter.TextAreaPresenter.TextAreaView;

public class TextAreaViewImpl extends ViewImpl implements TextAreaView {
    public interface Binder extends UiBinder<Widget, TextAreaViewImpl> {
    }

    private final Widget widget;

    @UiField
    TextArea textArea;

    @Inject
    public TextAreaViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public String getText() {
        return textArea.getText();
    }

    @Override
    public void setText(final String text) {
        textArea.setText(text);
    }

    @Override
    public HandlerRegistration addKeyDownHandler(final KeyDownHandler handler) {
        return textArea.addKeyDownHandler(handler);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        widget.fireEvent(event);
    }
}
