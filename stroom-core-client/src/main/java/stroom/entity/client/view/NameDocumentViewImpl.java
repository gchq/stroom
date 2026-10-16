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

package stroom.entity.client.view;

import stroom.entity.client.presenter.NameDocumentView;
import stroom.widget.form.client.FormGroup;
import stroom.widget.popup.client.view.DialogAction;
import stroom.widget.popup.client.view.DialogActionUiHandlers;

import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class NameDocumentViewImpl extends ViewWithUiHandlers<DialogActionUiHandlers> implements NameDocumentView {

    private final Widget widget;
    @UiField
    FormGroup nameGroup;
    @UiField
    TextBox name;

    @Inject
    public NameDocumentViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        widget.addAttachHandler(event -> focus());
    }

    @Override
    public void focus() {
        name.setFocus(true);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public String getName() {
        return name.getText();
    }

    @Override
    public void setName(final String name) {
        this.name.setText(name);
    }

    @Override
    public void setNameInvalid(final String message) {
        nameGroup.setFeedback(message);
        name.setFocus(true);
    }

    @Override
    public void setNameValid() {
        nameGroup.clearFeedback();
    }

    @UiHandler("name")
    void onKeyDown(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == '\r') {
            getUiHandlers().onDialogAction(DialogAction.OK);
        }
    }

    @UiHandler("name")
    void onKeyUp(final KeyUpEvent event) {
        // Once a name has been typed the feedback no longer applies
        if (nameGroup.getFeedback() != null && !name.getText().trim().isEmpty()) {
            nameGroup.clearFeedback();
        }
    }

    public interface Binder extends UiBinder<Widget, NameDocumentViewImpl> {

    }
}
