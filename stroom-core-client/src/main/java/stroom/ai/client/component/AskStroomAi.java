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

import stroom.ai.shared.AiAttachmentStatus;
import stroom.ai.shared.AiMessageType;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.dropdowntree.client.DropDownBox;
import stroom.widget.form.client.TextArea;
import stroom.widget.spinner.client.SmallBusySpinner;
import stroom.widget.task.client.TaskMonitor;
import stroom.widget.task.client.TaskMonitorFactory;
import stroom.widget.text.client.Label;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/// The Ask Stroom AI chat: its conversation's title and toolbar, the conversation, and the message
/// box with the model to ask and the Send button. Shown docked beside the main screen or as a dialog.
public class AskStroomAi extends Component implements Focus, TaskMonitorFactory {

    private static final Binder BINDER = GWT.create(Binder.class);

    private static final String SEND_BUTTON_NORMAL_TEXT = "Send";
    private static final String SEND_BUTTON_CANCEL_TEXT = "Cancel";
    private static final String EMPTY_STYLE = "ask-stroom-ai--empty";
    private boolean sending;
    private Consumer<String> onSend = message -> {
    };
    private Consumer<AskStroomAiAction> onAction = action -> {
    };
    private BiConsumer<Integer, String> onViewAttachment = (attachmentId, description) -> {
    };
    private Consumer<Integer> onDeleteMessage = messageId -> {
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

    // The conversation's messages
    @UiField
    FlowPanel conversation;
    @UiField
    TextArea message;
    @UiField
    ToolbarButton run;
    @UiField
    Label contextIndicator;
    @UiField
    DropDownBox modelRef;
    @UiField
    SmallBusySpinner spinner;

    // The user's message as shown when it was sent, until the stored one replaces it
    private Widget pendingMessage;
    // The progress lines shown, the first the one that changes as the work goes on
    private final List<AiDetailsMessage> workingMessages = new ArrayList<>();
    // The attachments' messages, by attachment id, whose download status changes
    private final Map<Integer, AiMessage> attachmentMessages = new HashMap<>();

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

    /// @param onViewAttachment Given an attachment's id and description when the user asks to see
    ///                         its data.
    public void setOnViewAttachment(final BiConsumer<Integer, String> onViewAttachment) {
        this.onViewAttachment = Objects.requireNonNull(onViewAttachment, "onViewAttachment");
    }

    /// @param onDeleteMessage Given a message's id when the user asks to delete it.
    public void setOnDeleteMessage(final Consumer<Integer> onDeleteMessage) {
        this.onDeleteMessage = Objects.requireNonNull(onDeleteMessage, "onDeleteMessage");
    }

    /// Adds messages to the end of the conversation, and scrolls to them.
    ///
    /// @param messages The messages.
    public void addMessages(final List<Message> messages) {
        messages.forEach(message -> conversation.add(render(message)));
        scrollToLatest();
    }

    /// Adds the message the user has just sent to the end of the conversation, and scrolls to it. It
    /// is pending until the stored message replaces it ([#replacePendingMessage(Message)]); only
    /// the latest message sent is.
    ///
    /// @param message The message.
    public void addPendingMessage(final Message message) {
        pendingMessage = render(message);
        conversation.add(pendingMessage);
        scrollToLatest();
    }

    /// Replaces the pending message, the one the user sent, with the stored one.
    ///
    /// @param message The stored message.
    /// @return False if there is no pending message to replace.
    public boolean replacePendingMessage(final Message message) {
        if (pendingMessage == null) {
            return false;
        }
        final int index = conversation.getWidgetIndex(pendingMessage);
        conversation.remove(pendingMessage);
        pendingMessage = null;
        conversation.insert(render(message), index);
        return true;
    }

    /// Shows what the AI is working on, in a progress line added to the end of the conversation if
    /// there isn't one, or takes the line away.
    ///
    /// @param message What it is working on, or null when it has finished.
    public void setWorkingMessage(final Message message) {
        if (message == null) {
            // Nothing is being worked on, so the progress line has served its purpose
            if (!workingMessages.isEmpty()) {
                conversation.remove(workingMessages.remove(0));
            }
        } else if (workingMessages.isEmpty()) {
            addMessages(List.of(message));
        } else {
            workingMessages.get(0).setSummary(message.text());
        }
    }

    /// Shows how an attachment's download is going, if the conversation has the attachment.
    ///
    /// @param attachmentId     The attachment's id.
    /// @param attachmentStatus Where it has got to.
    /// @param text             What it says, e.g. "Ready --- 42 rows".
    public void setAttachmentStatus(final int attachmentId,
                                    final AiAttachmentStatus attachmentStatus,
                                    final String text) {
        final AiMessage message = attachmentMessages.get(attachmentId);
        if (message != null) {
            message.setAttachmentStatus(attachmentStatus, text);
        }
    }

    /// Scrolls the conversation to its latest message.
    public void scrollToLatest() {
        final Element element = conversation.getElement();
        element.setScrollTop(element.getScrollHeight());
    }

    // A message as shown, as its type says
    private Widget render(final Message message) {
        return switch (message.type()) {
            case USER_MESSAGE -> renderMessage(AiMessage.Kind.USER, message, "Delete message");
            case AI_RESPONSE -> {
                final AiMessage reply = renderMessage(AiMessage.Kind.ASSISTANT, message, null);
                reply.setCopyText(message.text());
                yield reply;
            }
            case ERROR -> renderMessage(AiMessage.Kind.ERROR, message, null);
            case WORKING -> {
                final AiDetailsMessage working = renderDetails(AiDetailsMessage.Kind.WORKING, message);
                working.setSummary(message.text());
                workingMessages.add(working);
                yield working;
            }
            case THINKING -> renderDetails(AiDetailsMessage.Kind.THINKING, message);
            case DASHBOARD_DATA, QUERY_DATA, TABLE_DATA -> renderDetails(AiDetailsMessage.Kind.DATA, message);
            case DEBUG_DETAIL -> renderDetails(AiDetailsMessage.Kind.DEBUG_DETAIL, message);
            case ATTACHMENT -> renderAttachment(message);
        };
    }

    // A message, which can be deleted if it has an id and a name for its Delete button
    private AiMessage renderMessage(final AiMessage.Kind kind, final Message message, final String deleteName) {
        final AiMessage aiMessage = new AiMessage();
        aiMessage.setKind(kind);
        aiMessage.setContent(message.content());
        aiMessage.setTime(message.time(), message.fullTime());
        if (deleteName != null && message.id() > 0) {
            aiMessage.setDeletable(deleteName);
            aiMessage.setOnDelete(() -> onDeleteMessage.accept(message.id()));
        }
        return aiMessage;
    }

    private AiDetailsMessage renderDetails(final AiDetailsMessage.Kind kind, final Message message) {
        final AiDetailsMessage details = new AiDetailsMessage();
        details.setKind(kind);
        details.setContent(message.content());
        details.setTime(message.time(), message.fullTime());
        return details;
    }

    // An attachment: its View data button, its description and its download status
    private AiMessage renderAttachment(final Message message) {
        final AiMessage attachment = renderMessage(AiMessage.Kind.ATTACHMENT, message, "Delete attachment");
        final Integer attachmentId = message.attachmentId();
        attachment.setAttachment(message.text(), attachmentId != null);
        attachment.setOnViewData(() -> onViewAttachment.accept(attachmentId, message.text()));
        if (attachmentId != null) {
            attachmentMessages.putIfAbsent(attachmentId, attachment);
        }
        return attachment;
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
            run.addStyleName("toolbar-button--stop");
            run.removeStyleName("toolbar-button--play");
        } else {
            sending = false;
            run.setEnabled(!message.getText().isEmpty());
            run.setTitle(SEND_BUTTON_NORMAL_TEXT);
            run.setSvg(SvgImage.PLAY);
            run.addStyleName("toolbar-button--play");
            run.removeStyleName("toolbar-button--stop");
        }
    }

    /// @return The picker of the model to ask, for its controller to attach to.
    public DropDownBox getModelRefBox() {
        return modelRef;
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
        conversation.clear();
        pendingMessage = null;
        workingMessages.clear();
        attachmentMessages.clear();
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

    /// A message in the conversation, as the chat shows it.
    ///
    /// @param type         What it is, which gives its look.
    /// @param id           Its id, or 0 if it hasn't been stored yet (so can't be deleted).
    /// @param text         For a reply, what Copy copies (its markdown); for progress, what the AI is
    ///                     working on; for an attachment, its description.
    /// @param content      The message, rendered (e.g. from markdown), or null for none.
    /// @param attachmentId For an attachment, the id of the data attached, or null.
    /// @param time         When it was sent, e.g. "5 minutes ago".
    /// @param fullTime     When it was sent, in full.
    public record Message(AiMessageType type,
                          int id,
                          String text,
                          SafeHtml content,
                          Integer attachmentId,
                          String time,
                          String fullTime) {

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
