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

package stroom.annotation.client;

import stroom.annotation.client.ChangeStatusPresenter.ChangeStatusView;
import stroom.annotation.shared.AnnotationTag;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class ChangeStatusViewImpl extends ViewWithUiHandlers<ChangeStatusUiHandlers> implements ChangeStatusView {

    private final Widget widget;

    @UiField
    Label status;
    @UiField
    SettingBlock statusBlock;

    @Inject
    public ChangeStatusViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
//        statusBlock.setFocus(true);
    }

    @Override
    public void setStatus(final AnnotationTag status) {
        if (status == null) {
            this.status.setText("None");
            this.status.getElement().getStyle().setOpacity(0.5);
        } else {
            this.status.setText(status.getName());
            this.status.getElement().getStyle().setOpacity(1);
        }
    }

    @UiHandler("statusBlock")
    public void onStatusBlock(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().showStatusChooser(statusBlock.getElement());
        }
    }

    public interface Binder extends UiBinder<Widget, ChangeStatusViewImpl> {

    }
}
