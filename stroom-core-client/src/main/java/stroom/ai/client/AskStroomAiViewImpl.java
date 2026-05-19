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
import stroom.widget.button.client.InlineSvgButton;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class AskStroomAiViewImpl extends ViewWithUiHandlers<AskStroomAiUiHandlers> implements AskStroomAiView {

    private static final String SEND_BUTTON_NORMAL_TEXT = "Send";
    private static final String SEND_BUTTON_CANCEL_TEXT = "Cancel";
    private final Widget widget;

    @UiField
    Label chatTitle;
    @UiField
    InlineSvgButton newChat;
    @UiField
    InlineSvgButton chatHistory;
    @UiField
    InlineSvgButton configure;

    @UiField
    SimplePanel markdownPreview;
    @UiField
    TextBox message;
    @UiField
    InlineSvgButton run;
    @UiField
    SimplePanel modelRef;

    @Inject
    public AskStroomAiViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        newChat.setText("New Conversation");
        newChat.setTitle("New Conversation");
        newChat.setSvg(SvgImage.ADD);
        newChat.setEnabled(true);

        chatHistory.setText("Conversation History");
        chatHistory.setTitle("Conversation History");
        chatHistory.setSvg(SvgImage.HISTORY);
        chatHistory.setEnabled(true);

        configure.setText("Configure");
        configure.setTitle("Configure");
        configure.setSvg(SvgImage.SETTINGS);
        configure.setEnabled(true);

        message.getElement().setAttribute("placeholder", "How can I help?");

        run.setText(SEND_BUTTON_NORMAL_TEXT);
        run.setSvg(SvgImage.PLAY);
        run.setEnabled(false);
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
        if (loading) {
            run.setEnabled(true);
            run.setText(SEND_BUTTON_CANCEL_TEXT);
            run.setSvg(SvgImage.STOP);
            run.addStyleName("stop");
            run.removeStyleName("play");
        } else {
            run.setEnabled(!message.getText().isEmpty());
            run.setText(SEND_BUTTON_NORMAL_TEXT);
            run.setSvg(SvgImage.PLAY);
            run.addStyleName("play");
            run.removeStyleName("stop");
        }
    }

    @Override
    public void setModelRefSelection(final View view) {
        this.modelRef.setWidget(view.asWidget());
    }

    @UiHandler("message")
    public void onMessageKeyDown(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            sendMessage();
        }
    }

    @UiHandler("message")
    public void onMessageKeyUp(final KeyUpEvent event) {
        run.setEnabled(!message.getText().isEmpty());
    }

    @UiHandler("run")
    public void onRun(final ClickEvent event) {
        if (getUiHandlers() != null) {
            sendMessage();
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
