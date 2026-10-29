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
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.dropdowntree.client.DropDownBox;
import stroom.widget.form.client.TextArea;
import stroom.widget.spinner.client.SpinnerSmall;
import stroom.widget.text.client.Label;
import stroom.widget.util.client.Clipboard;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.Objects;
import java.util.function.BiConsumer;
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
    DropDownBox modelRef;
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
        markdownPreview.addDomHandler(this::onConversationClick, ClickEvent.getType());
    }

    // The buttons in the conversation's messages: View data, Delete and Copy
    private void onConversationClick(final ClickEvent event) {
        if (!MouseUtil.isPrimary(event)) {
            return;
        }
        final Element target = event.getNativeEvent().getEventTarget().cast();
        final Element button = ElementUtil.findParent(target, element ->
                element.getTagName().equalsIgnoreCase("button"), 5);
        if (button == null) {
            return;
        }
        final String attachmentId = button.getAttribute("data-attachment-id");
        final String messageId = button.getAttribute("data-delete-message-id");
        final String data = button.getAttribute("data");
        if (NullSafe.isNonBlankString(attachmentId)) {
            onViewAttachment.accept(Integer.parseInt(attachmentId), button.getAttribute("data-attachment-desc"));
        } else if (NullSafe.isNonBlankString(messageId)) {
            onDeleteMessage.accept(Integer.parseInt(messageId));
        } else if (NullSafe.isNonBlankString(data)) {
            Clipboard.writeText(data, () -> showCopied(button), () -> {
            });
        }
    }

    // The Copy button says Copied for a moment
    private void showCopied(final Element button) {
        final Element copyIcon = ElementUtil.findChild(button, "icon-wrap");
        final Element copyLabel = ElementUtil.findChild(button, "ai-message-copy-label");
        if (copyIcon != null && copyLabel != null) {
            copyIcon.setInnerHTML(SvgImage.OK.getSvg());
            copyLabel.setInnerText("Copied");
            new Timer() {
                @Override
                public void run() {
                    copyIcon.setInnerHTML(SvgImage.COPY.getSvg());
                    copyLabel.setInnerText("Copy");
                }
            }.schedule(2000);
        }
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
    /// @param html The messages.
    public void appendMessages(final SafeHtml html) {
        final Element conversation = markdownPreview.getElement();
        conversation.setInnerHTML(NullSafe.toString(conversation.getInnerHTML()) + html.asString());
        scrollToLatest();
    }

    /// Scrolls the conversation to its latest message.
    public void scrollToLatest() {
        final Element conversation = markdownPreview.getElement();
        conversation.setScrollTop(conversation.getScrollHeight());
    }

    /// Gives the conversation's last message an id, taking it from any other message that had it.
    ///
    /// @param id The id.
    public void markLastMessage(final String id) {
        final Element previous = findPart(id);
        if (previous != null) {
            previous.removeAttribute("id");
        }
        final Element last = markdownPreview.getElement().getLastChild().cast();
        if (last != null) {
            last.setId(id);
        }
    }

    /// Replaces a message.
    ///
    /// @param id   The message's id.
    /// @param html The message to show in its place.
    /// @return False if there is no message with the id.
    public boolean replaceMessage(final String id, final SafeHtml html) {
        final Element existing = findPart(id);
        if (existing == null) {
            return false;
        }
        final Element temp = Document.get().createDivElement();
        temp.setInnerHTML(html.asString());
        final Element replacement = temp.getFirstChildElement();
        if (replacement == null) {
            return false;
        }
        existing.getParentElement().replaceChild(replacement, existing);
        return true;
    }

    /// @param id The id of a part of a message.
    /// @return Whether the conversation has it.
    public boolean hasPart(final String id) {
        return findPart(id) != null;
    }

    /// Removes a part of a message, if the conversation has it.
    ///
    /// @param id The part's id.
    public void removePart(final String id) {
        final Element part = findPart(id);
        if (part != null) {
            part.removeFromParent();
        }
    }

    /// Sets the text of a part of a message, if the conversation has it.
    ///
    /// @param id   The part's id.
    /// @param text Its text.
    public void setPartText(final String id, final String text) {
        final Element part = findPart(id);
        if (part != null) {
            part.setInnerText(text);
        }
    }

    /// Sets the content of a part of a message, if the conversation has it.
    ///
    /// @param id   The part's id.
    /// @param html Its content.
    public void setPartHtml(final String id, final SafeHtml html) {
        final Element part = findPart(id);
        if (part != null) {
            part.setInnerSafeHtml(html);
        }
    }

    // A part of the conversation, by id, looked for only within it
    private Element findPart(final String id) {
        final NodeList<Element> elements = markdownPreview.getElement().getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            if (id.equals(elements.getItem(i).getId())) {
                return elements.getItem(i);
            }
        }
        return null;
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
