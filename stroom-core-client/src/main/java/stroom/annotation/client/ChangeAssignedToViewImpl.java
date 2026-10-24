/*
 * Copyright 2019 Crown Copyright
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

package stroom.annotation.client;

import stroom.annotation.client.ChangeAssignedToPresenter.ChangeAssignedToView;
import stroom.util.shared.UserRef;
import stroom.widget.presenter.client.ViewWithUiHandlers;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

public class ChangeAssignedToViewImpl
        extends ViewWithUiHandlers<ChangeAssignedToUiHandlers>
        implements ChangeAssignedToView {

    private final Widget widget;

    @UiField
    Label assignedTo;
    @UiField
    AssignYourselfLink assignYourself;
    @UiField
    SettingBlock assignedToBlock;

    @Inject
    public ChangeAssignedToViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        assignYourself.setOnAssign(this::assignYourself);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
//        assignedToBlock.setFocus(true);
    }

    @Override
    public void setAssignYourselfEnabled(final boolean enabled) {
        assignYourself.setState(!enabled, false);
    }

    @Override
    public void setAssignedTo(final UserRef assignedTo) {
        if (assignedTo == null) {
            this.assignedTo.setText("Nobody");
            this.assignedTo.addStyleName(AnnotationEditViewImpl.EMPTY_VALUE_STYLE);
        } else {
            this.assignedTo.setText(assignedTo.toDisplayString());
            this.assignedTo.removeStyleName(AnnotationEditViewImpl.EMPTY_VALUE_STYLE);
        }
    }

    @UiHandler("assignedToBlock")
    public void onAssignedToBlock(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().showAssignedToChooser(assignedToBlock.getElement());
        }
    }

    private void assignYourself() {
        if (getUiHandlers() != null) {
            getUiHandlers().assignYourself();
        }
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, ChangeAssignedToViewImpl> {

    }
}
