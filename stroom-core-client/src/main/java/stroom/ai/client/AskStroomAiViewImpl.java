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
import stroom.task.client.TaskMonitor;
import stroom.widget.button.client.InlineSvgButton;
import stroom.widget.spinner.client.SpinnerSmall;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class AskStroomAiViewImpl extends ViewWithUiHandlers<AskStroomAiUiHandlers> implements AskStroomAiView {

    private static final String SEND_BUTTON_NORMAL_TEXT = "Send";
    private static final String SEND_BUTTON_CANCEL_TEXT = "Cancel";
    private static final String EMPTY_STYLE = "empty";
    private final Widget widget;
    private boolean sending;

    @UiField
    FlowPanel root;
    @UiField
    Label chatTitle;
    @UiField
    InlineSvgButton newChat;
    @UiField
    InlineSvgButton chatHistory;
    @UiField
    InlineSvgButton download;
    @UiField
    InlineSvgButton deleteAll;
    @UiField
    InlineSvgButton configure;
    @UiField
    Label emptyPrompt;

    @UiField
    SimplePanel markdownPreview;
    @UiField
    TextArea message;
    @UiField
    InlineSvgButton run;
    @UiField
    Label contextIndicator;
    @UiField
    SimplePanel modelRef;
    @UiField
    SpinnerSmall spinner;

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

        download.setText("Download");
        download.setTitle("Download");
        download.setSvg(SvgImage.DOWN);
        download.setEnabled(false);

        deleteAll.setText("Delete All Messages");
        deleteAll.setTitle("Delete All Messages");
        deleteAll.setSvg(SvgImage.DELETE);
        deleteAll.setEnabled(false);

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
    public SimplePanel getMarkdownContainer() {
        return markdownPreview;
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
            sending = false;
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

    @Override
    public TaskMonitor createTaskMonitor() {
        return spinner.createTaskMonitor();
    }

    @UiHandler("message")
    public void onMessageKeyDown(final KeyDownEvent event) {
        // Ctrl+Enter send the message.
        if (!event.isShiftKeyDown() && event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            event.preventDefault();
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
            if (sending) {
                getUiHandlers().onCancelProcessing();
            } else {
                sendMessage();
            }
        }
    }

    @UiHandler("configure")
    public void onConfigure(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onChangeConfig();
        }
    }

    @UiHandler("newChat")
    public void onNewChat(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onNewChat();
        }
    }

    @UiHandler("chatHistory")
    public void onChatHistory(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onShowHistory();
        }
    }

    @UiHandler("download")
    public void onDownload(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onDownloadChat();
        }
    }

    @UiHandler("deleteAll")
    public void onDeleteAll(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onDeleteAllMessages();
        }
    }

    public void setTitle(final String title) {
        chatTitle.setText(title);
    }

    public void clearMessages() {
        markdownPreview.getElement().setInnerHTML("");
    }

    public void setEmptyState(final boolean empty) {
        if (empty) {
            root.addStyleName(EMPTY_STYLE);
        } else {
            root.removeStyleName(EMPTY_STYLE);
        }
    }

    @Override
    public void setContextIndicator(final SvgImage icon, final String text) {
        if (text != null && !text.isEmpty()) {
            contextIndicator.getElement().setInnerHTML(
                    "<span class='svgIcon'>" + icon.getSvg() + "</span> " + text);
            contextIndicator.setVisible(true);
        } else {
            clearContextIndicator();
        }
    }

    @Override
    public void clearContextIndicator() {
        contextIndicator.setText("");
        contextIndicator.setVisible(false);
    }

    @Override
    public void setDownloadEnabled(final boolean enabled) {
        download.setEnabled(enabled);
    }

    @Override
    public void setDeleteAllEnabled(final boolean enabled) {
        deleteAll.setEnabled(enabled);
    }

    private void sendMessage() {
        if (!message.getText().isEmpty() && !sending) {
            sending = true;
            getUiHandlers().onSendMessage(getMessage());
            message.setText(null);
        }
    }

    public interface Binder extends UiBinder<Widget, AskStroomAiViewImpl> {

    }
}
