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

package stroom.widget.dropdowntree.client.view;

import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;
import stroom.widget.dropdowntree.client.presenter.DropDownPresenter;
import stroom.widget.dropdowntree.client.presenter.DropDownUiHandlers;

public class DropDownViewImpl extends ViewWithUiHandlers<DropDownUiHandlers>implements DropDownPresenter.DropDrownView {
    public interface Binder extends UiBinder<Widget, DropDownViewImpl> {
    }

    public interface Resources extends ClientBundle {
        ImageResource popup();
    }

    private final Widget widget;

    @UiField
    FlowPanel container;
    @UiField
    Label label;
    @UiField
    Image button;

    @Inject
    public DropDownViewImpl(final Binder binder, final Resources resources) {
        widget = binder.createAndBindUi(this);
        widget.addDomHandler(event -> {
            showPopup(event);
            event.stopPropagation();
        }, MouseDownEvent.getType());
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setText(final String text) {
        label.setText(text);
        label.setTitle(text);
    }

    private void showPopup(final MouseDownEvent e) {
        if (e.getNativeButton() == NativeEvent.BUTTON_LEFT) {
            if (getUiHandlers() != null) {
                getUiHandlers().showPopup();
            }
        }
    }
}
