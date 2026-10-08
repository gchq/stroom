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

import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.SvgButton;
import stroom.widget.util.client.DisabledState;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseDownHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class DropDownViewImpl extends ViewWithUiHandlers<DropDownUiHandlers>
        implements DropDownView {

    private final Widget widget;
    //    @SuppressWarnings("unused")
//    @UiField
//    FlowPanel container;
    @UiField
    Label label;
    @UiField(provided = true)
    SvgButton warningButton;
    @UiField
    SimplePanel ellipsesBtnPanel;

    private boolean enabled = true;
    private boolean readOnly;

    @Inject
    public DropDownViewImpl(final Binder binder) {
        warningButton = SvgButton.create(SvgPresets.ALERT.title("Show Warnings"));
        warningButton.setVisible(false);
        warningButton.addStyleName("dropDownView-warning");

        widget = binder.createAndBindUi(this);
        widget.addDomHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
//                GWT.log(event.getRelativeElement().toString());
                showPopup(event.getNativeEvent());
            }
        }, MouseDownEvent.getType());
        widget.addDomHandler(event -> {
            // As a button: Enter or Space opens the dialog
            if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER || event.getNativeKeyCode() == KeyCodes.KEY_SPACE) {
                event.preventDefault();
                showPopup(event.getNativeEvent());
            }
        }, KeyDownEvent.getType());
        widget.getElement().setTabIndex(0);
        // To assistive technology it is a button that opens a dialog to choose the value it shows.
        // A FormGroup names it with its label, followed by the value.
        widget.getElement().setAttribute("role", "button");
        widget.getElement().setAttribute("aria-haspopup", "dialog");

        SvgImageUtil.setSvgAsInnerHtml(ellipsesBtnPanel, SvgImage.ELLIPSES_HORIZONTAL);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        widget.getElement().focus();
    }

    @Override
    public void setText(final String text, final boolean hasErrorMsg) {
        label.setText(text);
        label.setTitle(text);
        warningButton.setVisible(hasErrorMsg);
    }

    @Override
    public HandlerRegistration addWarningClickHandler(final MouseDownHandler mouseDownHandler) {
        return warningButton.addMouseDownHandler(mouseDownHandler);
//        return warningPanel.asWidget()
//                .addDomHandler(mouseDownHandler, MouseDownEvent.getType());
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
        updateState();
    }

    @Override
    public void setReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        updateState();
    }

    private void updateState() {
        DisabledState.set(widget.getElement(), !enabled);
        widget.setStyleName("readonly", readOnly && enabled);
        // A button can't be read only, so a read-only picker is a button that does nothing
        if (readOnly) {
            widget.getElement().setAttribute(DisabledState.ARIA_DISABLED, "true");
        }
    }

    private void showPopup(final NativeEvent e) {
        e.stopPropagation();
        if (getUiHandlers() != null) {
            getUiHandlers().showPopup();
        }
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, DropDownViewImpl> {

    }
}
