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

package stroom.security.client.view;

import stroom.security.client.presenter.CreateExternalUserPresenter.CreateExternalUserView;
import stroom.widget.popup.client.view.DialogAction;
import stroom.widget.popup.client.view.DialogActionUiHandlers;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class CreateExternalUserViewImpl
        extends ViewWithUiHandlers<DialogActionUiHandlers>
        implements CreateExternalUserView {

    private final Widget widget;

    @UiField
    TextBox subjectId;
    @UiField
    TextBox displayName;
    @UiField
    TextBox fullName;

    @Inject
    public CreateExternalUserViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        widget.addAttachHandler(event -> focus());
    }

    @Override
    public void focus() {
        Scheduler.get().scheduleDeferred(() -> subjectId.setFocus(true));
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public String getSubjectId() {
        return subjectId.getText();
    }

    @Override
    public String getDisplayName() {
        return displayName.getText();
    }

    @Override
    public String getFullName() {
        return fullName.getText();
    }

    @Override
    public void clear() {
        this.subjectId.setText("");
        this.displayName.setText("");
        this.fullName.setText("");
    }

    @UiHandler("subjectId")
    void onUserIdentityKeyDown(final KeyDownEvent event) {
        handleKeyDown(event);
    }

    @UiHandler("displayName")
    void onDisplayNameKeyDown(final KeyDownEvent event) {
        handleKeyDown(event);
    }

    @UiHandler("fullName")
    void onFullNameKeyDown(final KeyDownEvent event) {
        handleKeyDown(event);
    }

    private void handleKeyDown(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == '\r') {
            getUiHandlers().onDialogAction(DialogAction.OK);
        }
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, CreateExternalUserViewImpl> {

    }
}
