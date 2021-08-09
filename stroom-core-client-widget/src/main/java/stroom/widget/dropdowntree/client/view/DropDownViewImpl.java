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

import stroom.svg.client.SvgImages;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class DropDownViewImpl extends ViewWithUiHandlers<DropDownUiHandlers>
        implements DropDownView {

    private final Widget widget;
    @UiField
    FlowPanel container;
    @UiField
    Label label;
    @UiField
    SimplePanel button;

    @Inject
    public DropDownViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        widget.addDomHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                showPopup(event.getNativeEvent());
            }
        }, MouseDownEvent.getType());
        widget.addDomHandler(event -> {
            if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
                showPopup(event.getNativeEvent());
            }
        }, KeyDownEvent.getType());
        widget.getElement().setTabIndex(0);

        button.getElement().setInnerHTML(SvgImages.MONO_ELLIPSES);
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

    private void showPopup(final NativeEvent e) {
        e.stopPropagation();
        if (getUiHandlers() != null) {
            getUiHandlers().showPopup();
        }
    }

    public interface Binder extends UiBinder<Widget, DropDownViewImpl> {

    }
}
