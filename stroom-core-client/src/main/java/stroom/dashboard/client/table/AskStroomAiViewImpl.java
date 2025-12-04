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

package stroom.dashboard.client.table;

import stroom.dashboard.client.table.AskStroomAiPresenter.AskStroomAiView;
import stroom.widget.button.client.Button;

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
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class AskStroomAiViewImpl extends ViewWithUiHandlers<AskStroomAiUiHandlers> implements AskStroomAiView {

    private static final String SEND_BUTTON_NORMAL_TEXT = "Send";
    private static final String SEND_BUTTON_BUSY_TEXT = "Busy";
    private final Widget widget;

    @UiField
    SimplePanel markdownPreview;
    @UiField
    TextBox message;
    @UiField
    Button sendMessage;
    @UiField
    Button clearHistory;

    @Inject
    public AskStroomAiViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        sendMessage.setText(SEND_BUTTON_NORMAL_TEXT);
        sendMessage.setEnabled(false);
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
        sendMessage.setEnabled(!loading);
        sendMessage.setLoading(loading);
        sendMessage.setText(loading ? SEND_BUTTON_BUSY_TEXT : SEND_BUTTON_NORMAL_TEXT);
    }

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

    private void sendMessage() {
        if (!message.getText().isEmpty()) {
            getUiHandlers().onSendMessage(getMessage());
            message.setText(null);
        }
    }

    public interface Binder extends UiBinder<Widget, AskStroomAiViewImpl> {

    }
}
