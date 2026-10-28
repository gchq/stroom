/*
 * Copyright 2025 Crown Copyright
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

package stroom.ai.client.component;

import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextArea;
import stroom.widget.spinner.client.SpinnerSmall;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.Objects;
import java.util.function.Consumer;

/// The Ask Stroom AI chat: its conversation's title and toolbar, the conversation, and the message
/// box with the model to ask and the Send button. Shown docked beside the main screen or as a dialog.
public class AskStroomAi extends Component implements Focus, TaskMonitorFactory {

    private static final Binder BINDER = GWT.create(Binder.class);

    private static final String SEND_BUTTON_NORMAL_TEXT = "Send";
    private static final String SEND_BUTTON_CANCEL_TEXT = "Cancel";
    private static final String EMPTY_STYLE = "empty";
    private boolean sending;
    private Consumer<String> onSend = message -> {
    };
    private Consumer<AskStroomAiAction> onAction = action -> {
    };

    @UiField
    FlowPanel root;
    @UiField
    Label chatTitle;
    @UiField
    ToolbarButton newChat;
    @UiField
    ToolbarButton chatHistory;
    @UiField
    ToolbarButton download;
    @UiField
    ToolbarButton deleteAll;
    @UiField
    ToolbarButton configure;
    @UiField
    Label emptyPrompt;

    @UiField
    SimplePanel markdownPreview;
    @UiField
    TextArea message;
    @UiField
    ToolbarButton run;
    @UiField
    Label contextIndicator;
    @UiField
    SimplePanel modelRef;
    @UiField
    SpinnerSmall spinner;

    /// Makes the screen, with no content until it is given it.
    public AskStroomAi() {
        initWidget(BINDER.createAndBindUi(this));
        message.addKeyDownHandler(event -> {
            // Enter sends the message (Shift+Enter starts a new line)
            if (!event.isShiftKeyDown() && event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
                event.preventDefault();
                sendMessage();
            }
        });
        message.addKeyUpHandler(event -> run.setEnabled(sending || !message.getText().isEmpty()));
        run.setOnClick(() -> {
            if (sending) {
                onAction.accept(AskStroomAiAction.CANCEL);
            } else {
                sendMessage();
            }
        });
        configure.setOnClick(() -> onAction.accept(AskStroomAiAction.CONFIGURE));
        newChat.setOnClick(() -> onAction.accept(AskStroomAiAction.NEW_CHAT));
        chatHistory.setOnClick(() -> onAction.accept(AskStroomAiAction.SHOW_HISTORY));
        download.setOnClick(() -> onAction.accept(AskStroomAiAction.DOWNLOAD));
        deleteAll.setOnClick(() -> onAction.accept(AskStroomAiAction.DELETE_ALL));
    }

    /// @param onSend Given the message the user sends.
    public void setOnSend(final Consumer<String> onSend) {
        this.onSend = Objects.requireNonNull(onSend, "onSend");
    }

    /// @param onAction Run with what the user asks for from the toolbars, other than sending.
    public void setOnAction(final Consumer<AskStroomAiAction> onAction) {
        this.onAction = Objects.requireNonNull(onAction, "onAction");
    }

    /// Puts the focus on the message box.
    @Override
    public void focus() {
        message.setFocus(true);
    }

    /// @return Where the conversation's messages are shown, as HTML.
    public SimplePanel getMarkdownContainer() {
        return markdownPreview;
    }

    /// @return The message typed.
    public String getMessage() {
        return message.getText();
    }

    /// @param loading Whether a reply is awaited, so the Send button cancels it.
    public void setSendButtonLoadingState(final boolean loading) {
        if (loading) {
            run.setEnabled(true);
            // Its name (its title) says what pressing it does now
            run.setTitle(SEND_BUTTON_CANCEL_TEXT);
            run.setSvg(SvgImage.STOP);
            run.addStyleName("stop");
            run.removeStyleName("play");
        } else {
            sending = false;
            run.setEnabled(!message.getText().isEmpty());
            run.setTitle(SEND_BUTTON_NORMAL_TEXT);
            run.setSvg(SvgImage.PLAY);
            run.addStyleName("play");
            run.removeStyleName("stop");
        }
    }

    /// Shows the picker of the model to ask.
    ///
    /// @param view Its view.
    public void setModelRefSelection(final IsWidget view) {
        this.modelRef.setWidget(view.asWidget());
    }

    /// @return A task monitor shown on the chat's spinner.
    @Override
    public TaskMonitor createTaskMonitor() {
        return spinner.createTaskMonitor();
    }

    /// @param title The conversation's title.
    public void setChatTitle(final String title) {
        chatTitle.setText(title);
    }

    /// Removes every message shown.
    public void clearMessages() {
        markdownPreview.getElement().setInnerHTML("");
    }

    /// @param empty Whether the conversation has no messages, so the empty prompt shows.
    public void setEmptyState(final boolean empty) {
        if (empty) {
            root.addStyleName(EMPTY_STYLE);
        } else {
            root.removeStyleName(EMPTY_STYLE);
        }
    }

    /// Shows what data is attached to the next message, or hides the chip.
    ///
    /// @param icon The chip's icon.
    /// @param text What is attached, e.g. a table's name, or null or empty for nothing.
    public void setContextIndicator(final SvgImage icon, final String text) {
        if (text != null && !text.isEmpty()) {
            contextIndicator.getElement().setInnerSafeHtml(ContextChip.toHtml(icon, text));
            contextIndicator.setVisible(true);
        } else {
            clearContextIndicator();
        }
    }

    /// Hides the chip of attached data.
    public void clearContextIndicator() {
        contextIndicator.setText("");
        contextIndicator.setVisible(false);
    }

    /// @param enabled Whether the conversation can be downloaded.
    public void setDownloadEnabled(final boolean enabled) {
        download.setEnabled(enabled);
    }

    /// @param enabled Whether the conversation's messages can be deleted.
    public void setDeleteAllEnabled(final boolean enabled) {
        deleteAll.setEnabled(enabled);
    }

    private void sendMessage() {
        if (!message.getText().isEmpty() && !sending) {
            sending = true;
            onSend.accept(getMessage());
            message.setText(null);
        }
    }

    interface Binder extends UiBinder<Widget, AskStroomAi> {

    }

    // --------------------------------------------------------------------------------

    /// What the user can ask the chat for from its toolbars, other than sending a message.
    public enum AskStroomAiAction {
        /// Stop waiting for the reply to the message sent.
        CANCEL,
        /// Change the chat's settings.
        CONFIGURE,
        /// Start a new conversation.
        NEW_CHAT,
        /// Choose an earlier conversation.
        SHOW_HISTORY,
        /// Download the conversation.
        DOWNLOAD,
        /// Delete all the conversation's messages.
        DELETE_ALL
    }
}
