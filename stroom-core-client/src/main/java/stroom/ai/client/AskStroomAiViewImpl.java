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

package stroom.ai.client;

import stroom.ai.client.AskStroomAiPresenter.AskStroomAiView;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Button;
import stroom.widget.button.client.InlineSvgButton;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class AskStroomAiViewImpl extends ViewWithUiHandlers<AskStroomAiUiHandlers> implements AskStroomAiView {

    private static final String SEND_BUTTON_NORMAL_TEXT = "Send";
    private static final String SEND_BUTTON_BUSY_TEXT = "Busy";
    private final Widget widget;

//    @UiField
//    SelectionBox<DockType> dockTypeSelectionBox;
//    @UiField
//    SelectionBox<DockLocation> dockLocationSelectionBox;
    @UiField
    SimplePanel markdownPreview;
    @UiField
    TextBox message;
    @UiField
    Button sendMessage;
    @UiField
    SimplePanel modelRef;
    @UiField
    Button setDefaultModel;
    @UiField
    Button clearHistory;
    @UiField
    InlineSvgButton configure;

    @Inject
    public AskStroomAiViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        message.getElement().setAttribute("placeholder", "How can I help?");
        sendMessage.setText(SEND_BUTTON_NORMAL_TEXT);
        sendMessage.setEnabled(false);

//        dockTypeSelectionBox.addItem(DockType.DIALOG);
//        dockTypeSelectionBox.addItem(DockType.TAB);
//        dockTypeSelectionBox.addItem(DockType.DOCK);
//        dockTypeSelectionBox.addItem(DockType.FLOAT);
//        dockTypeSelectionBox.setValue(DockType.DIALOG);
//
//        dockLocationSelectionBox.addItem(DockLocation.RIGHT);
//        dockLocationSelectionBox.addItem(DockLocation.LEFT);
//        dockLocationSelectionBox.addItem(DockLocation.TOP);
//        dockLocationSelectionBox.addItem(DockLocation.BOTTOM);
//        dockLocationSelectionBox.setValue(DockLocation.RIGHT);

        setDefaultModel.setVisible(false);

        configure.setSvg(SvgImage.SETTINGS);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        message.setFocus(true);
    }

    @Override
    public Element getMarkdownContainer() {
        return markdownPreview.getElement();
    }

    @Override
    public String getMessage() {
        return message.getText();
    }

    @Override
    public void setSendButtonLoadingState(final boolean loading) {
        sendMessage.setEnabled(!loading && !message.getText().isEmpty());
        sendMessage.setLoading(loading);
        sendMessage.setText(loading
                ? SEND_BUTTON_BUSY_TEXT
                : SEND_BUTTON_NORMAL_TEXT);
    }

//    @Override
//    public void setDockBehaviour(final DockBehaviour dockBehaviour) {
//        dockTypeSelectionBox.setValue(dockBehaviour.getDockType());
//        dockLocationSelectionBox.setValue(dockBehaviour.getDockLocation());
//    }
//
//    @Override
//    public DockBehaviour getDockBehaviour() {
//        return new DockBehaviour(dockTypeSelectionBox.getValue(), dockLocationSelectionBox.getValue());
//    }

    @Override
    public void allowSetDefault(final boolean allow) {
        setDefaultModel.setVisible(allow);
    }

    @Override
    public void setModelRefSelection(final View view) {
        this.modelRef.setWidget(view.asWidget());
    }

//    @UiHandler("dockTypeSelectionBox")
//    public void onDockTypeSelectionBox(final ValueChangeEvent<DockType> event) {
//        if (getUiHandlers() != null) {
//            getUiHandlers().onDockBehaviourChange(getDockBehaviour());
//        }
//    }
//
//    @UiHandler("dockLocationSelectionBox")
//    public void onDockLocationSelectionBox(final ValueChangeEvent<DockLocation> event) {
//        if (getUiHandlers() != null) {
//            getUiHandlers().onDockBehaviourChange(getDockBehaviour());
//        }
//    }

    @UiHandler("message")
    public void onMessageKeyDown(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            sendMessage();
        }
    }

    @UiHandler("message")
    public void onMessageKeyUp(final KeyUpEvent event) {
        sendMessage.setEnabled(!message.getText().isEmpty());
    }

    @UiHandler("sendMessage")
    public void onSendMessageClick(final ClickEvent event) {
        if (getUiHandlers() != null) {
            sendMessage();
        }
    }

    @UiHandler("clearHistory")
    public void onClearHistoryClick(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().clearHistory();
        }
    }

    @UiHandler("setDefaultModel")
    public void onSetDefaultModel(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onSetDefaultModel(setDefaultModel);
        }
    }

    @UiHandler("configure")
    public void onConfigure(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onChangeConfig();
        }
    }

    private void sendMessage() {
        if (!message.getText().isEmpty()) {
            getUiHandlers().onSendMessage(getMessage());
            message.setText(null);
        }
    }

    public interface Binder extends UiBinder<Widget, AskStroomAiViewImpl> {

    }
}
