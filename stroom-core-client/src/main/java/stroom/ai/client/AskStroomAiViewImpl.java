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

package stroom.ai.client;

import stroom.ai.client.AskStroomAiPresenter.AskStroomAiView;
import stroom.ai.shared.AiAgentMode;
import stroom.item.client.SelectionBox;
import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskMonitor;
import stroom.widget.button.client.InlineSvgButton;
import stroom.widget.spinner.client.SpinnerSmall;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.ScrollEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.List;

public class AskStroomAiViewImpl extends ViewWithUiHandlers<AskStroomAiUiHandlers> implements AskStroomAiView {

    private static final String SEND_BUTTON_NORMAL_TEXT = "Send";
    private static final String SEND_BUTTON_CANCEL_TEXT = "Cancel";
    private static final String EMPTY_STYLE = "empty";
    private static final String SWITCHER_OPEN_CLASS = "askStroomAiViewImpl-switcher--open";
    private static final String AUTO_APPROVE_ON_CLASS = "askStroomAiViewImpl-autoApprove--on";
    private static final String WORKING_CLASS = "askStroomAiViewImpl-bottom-inner--working";

    private boolean empty;
    private static final int MIN_REFERENCE_HEIGHT = 60;

    private final Widget widget;
    // True while the chat is working, so the run button is a Stop button.
    private boolean working;

    @UiField
    FlowPanel inputBox;
    @UiField
    FlowPanel root;
    @UiField
    Label chatTitle;
    @UiField
    stroom.widget.button.client.Button newChat;
    @UiField
    InlineSvgButton tabToggle;
    @UiField
    InlineSvgButton menu;
    @UiField
    Label emptyPrompt;

    @UiField
    SimplePanel markdownPreview;
    @UiField
    FlowPanel chatList;
    @UiField
    FlowPanel jumpToLatestBar;
    @UiField
    FlowPanel commandSuggestions;
    @UiField
    Button contextMeter;
    @UiField
    HTML contextDetails;
    @UiField
    Button jumpToLatest;
    @UiField
    TextArea message;
    @UiField
    InlineSvgButton run;
    @UiField
    SelectionBox<AiAgentMode> agentMode;
    @UiField
    InlineSvgButton autoApprove;
    @UiField
    Label contextIndicator;
    @UiField
    SimplePanel modelRef;
    @UiField
    SpinnerSmall spinner;
    @UiField
    FlowPanel followUps;
    @UiField
    FlowPanel taskList;
    @UiField
    Button taskListHeader;
    @UiField
    HTML taskListItems;
    // Whether the user has folded the task list away, kept as it is updated.
    private boolean taskListCollapsed;
    @UiField
    FlowPanel commandReference;
    @UiField
    SimplePanel commandReferenceResize;
    @UiField
    InlineSvgButton commandReferenceClose;
    @UiField
    HTML commandReferenceContent;
    // While the reference is being resized: where the drag started, and the height then.
    private HandlerRegistration referenceDrag;
    private int referenceDragStartY;
    private int referenceDragStartHeight;
    @UiField
    TextBox chatSearch;
    @UiField
    HTML chatListActions;
    @UiField
    HTML chatListItems;

    @Inject
    public AskStroomAiViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        jumpToLatestBar.setVisible(false);
        contextMeter.setVisible(false);
        contextDetails.setVisible(false);
        // The details show above the pill while the pointer is over it. They are placed on the page (fixed),
        // as the input area clips anything that overflows it.
        contextMeter.addMouseOverHandler(event -> showContextDetails());
        contextMeter.addMouseOutHandler(event -> contextDetails.setVisible(false));
        commandSuggestions.setVisible(false);
        followUps.setVisible(false);
        commandReference.setVisible(false);
        taskList.setVisible(false);
        taskListHeader.addClickHandler(event -> {
            taskListCollapsed = !taskListCollapsed;
            showTaskListCollapsed();
        });
        commandReferenceClose.setSvg(SvgImage.CLOSE);
        commandReferenceClose.setTitle("Close the command reference");
        commandReferenceClose.addClickHandler(event -> {
            if (getUiHandlers() != null) {
                getUiHandlers().onCloseCommandReference();
            }
        });
        commandReferenceResize.setTitle("Drag to resize");
        commandReferenceResize.addDomHandler(this::startReferenceResize, MouseDownEvent.getType());
        markdownPreview.addDomHandler(event -> {
            if (getUiHandlers() != null) {
                getUiHandlers().onConversationScrolled();
            }
        }, ScrollEvent.getType());

        // The arrow is its own element, so it can be made to stand out.
        jumpToLatest.setHTML(SafeHtmlUtils.fromSafeConstant(
                "<span class=\"askStroomAiViewImpl-jumpToLatestArrow\">↓</span>Latest"));

        agentMode.addItems(AiAgentMode.values());
        agentMode.setValue(AiAgentMode.AGENT);
        agentMode.setTitle("Agent: does what you ask with all its tools.\n"
                           + "Ask: answers questions, and changes nothing.\n"
                           + "Plan: works out a plan for what you ask, without carrying it out.");
        agentMode.addValueChangeHandler(event -> {
            if (getUiHandlers() != null) {
                getUiHandlers().onAgentModeChange(event.getValue());
            }
        });
        autoApprove.setSvg(SvgImage.SHIELD);
        setAutoApprove(false, true);
        autoApprove.addClickHandler(event -> {
            if (getUiHandlers() != null) {
                getUiHandlers().onToggleAutoApprove();
            }
        });

        chatSearch.getElement().setAttribute("placeholder", "Search chats");
        chatSearch.setTitle("Search your chats' titles and your questions in them.\nEscape clears the search.");
        chatSearch.addKeyDownHandler(event -> {
            if (event.getNativeKeyCode() == KeyCodes.KEY_ESCAPE && !chatSearch.getText().isEmpty()) {
                // Clears the search, rather than doing what Escape does elsewhere.
                event.preventDefault();
                event.stopPropagation();
                chatSearch.setText("");
                if (getUiHandlers() != null) {
                    getUiHandlers().onChatSearch("");
                }
            }
        });
        chatSearch.addKeyUpHandler(event -> {
            if (getUiHandlers() != null) {
                getUiHandlers().onChatSearch(chatSearch.getText());
            }
        });

        newChat.setText("New chat");
        newChat.setIcon(SvgImage.ADD);
        setNewChatEnabled(true);

        setTabMode(false);
        tabToggle.setEnabled(true);

        menu.setText("More");
        menu.setTitle("More");
        menu.setSvg(SvgImage.ELLIPSES_VERTICAL);
        menu.setEnabled(true);

        // The title opens the chat switcher.
        chatTitle.addStyleName("askStroomAiViewImpl-switcher");
        chatTitle.setTitle("Show your chats");
        chatTitle.addClickHandler(event -> {
            if (getUiHandlers() != null) {
                getUiHandlers().onShowSwitcher(chatTitle.getElement());
            }
        });

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
        // A glowing border round the input box while a question is being worked on.
        inputBox.setStyleName(WORKING_CLASS, loading);
        working = loading;
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
        run.setEnabled(working || !message.getText().isEmpty());
        if (getUiHandlers() != null) {
            getUiHandlers().onInputChanged(message.getText());
        }
    }

    @UiHandler("contextMeter")
    public void onContextMeter(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onShowContextMeter(contextMeter.getElement());
        }
    }

    @Override
    public void setContextMeter(final String text, final SafeHtml details, final String level) {
        if (text == null) {
            contextMeter.setVisible(false);
            contextDetails.setVisible(false);
            return;
        }
        contextMeter.setText(text);
        contextDetails.setHTML(details);
        contextMeter.setStyleName("allow-focus askStroomAiViewImpl-contextMeter");
        if (level != null) {
            contextMeter.addStyleName("askStroomAiViewImpl-contextMeter--" + level);
        }
        contextMeter.setVisible(true);
    }

    private void showContextDetails() {
        final Element pill = contextMeter.getElement();
        final Style style = contextDetails.getElement().getStyle();
        contextDetails.setVisible(true);
        // Above the pill, its right edge in line with the pill's, kept on screen.
        final int width = contextDetails.getElement().getOffsetWidth();
        final int height = contextDetails.getElement().getOffsetHeight();
        final int left = Math.max(4, pill.getAbsoluteRight() - width);
        final int top = Math.max(4, pill.getAbsoluteTop() - height - 6);
        style.setLeft(left, Unit.PX);
        style.setTop(top, Unit.PX);
    }

    @Override
    public void setMessage(final String text) {
        message.setText(text);
        message.setFocus(true);
        message.setCursorPos(text.length());
        run.setEnabled(!text.isEmpty());
    }

    @Override
    public void setCommandSuggestions(final List<AiSlashCommand> commands) {
        commandSuggestions.clear();
        commandSuggestions.setVisible(commands != null && !commands.isEmpty());
        if (commands == null) {
            return;
        }
        for (final AiSlashCommand command : commands) {
            final Button button = new Button();
            button.setStyleName("allow-focus askStroomAiViewImpl-command");
            button.setHTML(SafeHtmlUtils.htmlEscape(command.getName())
                    + (command.getArguments().isEmpty()
                    ? ""
                    : " <span class=\"askStroomAiViewImpl-commandArgs\">"
                      + SafeHtmlUtils.htmlEscape(command.getArguments()) + "</span>")
                    + " <span class=\"askStroomAiViewImpl-commandDescription\">"
                    + SafeHtmlUtils.htmlEscape(command.getDescription()) + "</span>");
            button.addClickHandler(event -> {
                if (getUiHandlers() != null) {
                    getUiHandlers().onCommandPicked(command);
                }
                message.setText(command.getName() + " ");
                message.setFocus(true);
                message.setCursorPos(message.getText().length());
                setCommandSuggestions(null);
                run.setEnabled(true);
            });
            commandSuggestions.add(button);
        }
    }

    @Override
    public AiAgentMode getAgentMode() {
        return agentMode.getValue();
    }

    @Override
    public void setAgentMode(final AiAgentMode mode) {
        agentMode.setValue(mode != null
                ? mode
                : AiAgentMode.AGENT);
    }

    @Override
    public void setAutoApprove(final boolean on, final boolean allowed) {
        autoApprove.setStyleName(AUTO_APPROVE_ON_CLASS, on);
        autoApprove.setTitle(on
                ? "Auto-approve is on.\n"
                  + "Trusted servers' tools that modify data run without asking, even after this chat has read an "
                  + "untrusted MCP server's output.\n"
                  + "Click to turn it off."
                : allowed
                        ? "Auto-approve.\n"
                          + "Lets trusted servers' tools that modify data run without asking, even after this chat "
                          + "has read an untrusted MCP server's output."
                        : "Auto-approve isn't allowed by your administrator.");
        autoApprove.setEnabled(on || allowed);
    }

    @UiHandler("run")
    public void onRun(final ClickEvent event) {
        if (getUiHandlers() != null) {
            // Whatever the button shows: Stop while the chat works (e.g. after a refresh, or once polling has
            // picked the run up), Send otherwise.
            if (working) {
                getUiHandlers().onCancelProcessing();
            } else {
                sendMessage();
            }
        }
    }

    @UiHandler("jumpToLatest")
    public void onJumpToLatest(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onJumpToLatest();
        }
    }

    @Override
    public void setJumpToLatestVisible(final boolean visible) {
        jumpToLatestBar.setVisible(visible);
    }

    @UiHandler("newChat")
    public void onNewChat(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onNewChat();
        }
    }

    @UiHandler("tabToggle")
    public void onTabToggle(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onToggleTab();
        }
    }

    @UiHandler("menu")
    public void onMenu(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onShowMenu(menu.getElement());
        }
    }

    @Override
    public void setTabMode(final boolean tab) {
        final String text = tab
                ? "Move Back to the Panel"
                : "Open in a Tab";
        tabToggle.setText(text);
        tabToggle.setTitle(text);
        tabToggle.setSvg(tab
                ? SvgImage.MINIMISE
                : SvgImage.MAXIMISE);
    }

    public void setTitle(final String title) {
        chatTitle.setText(title);
        // The whole title, as a long one is cut short to fit.
        chatTitle.setTitle(title + "\nShow your chats");
    }

    public void clearMessages() {
        markdownPreview.getElement().setInnerHTML("");
    }

    public void setEmptyState(final boolean empty) {
        this.empty = empty;
        setNewChatEnabled(!empty);
        // The list of chats takes the conversation's place, so it is not shown as an empty chat.
        if (empty && !chatList.isVisible()) {
            root.addStyleName(EMPTY_STYLE);
        } else {
            root.removeStyleName(EMPTY_STYLE);
        }
    }

    @Override
    public void setTaskList(final String heading, final SafeHtml items) {
        if (heading == null) {
            taskList.setVisible(false);
            return;
        }
        taskListHeader.setText(heading);
        taskListItems.setHTML(items);
        taskList.setVisible(true);
        showTaskListCollapsed();
    }

    private void showTaskListCollapsed() {
        taskListItems.setVisible(!taskListCollapsed);
        taskList.setStyleName("askStroomAiViewImpl-taskList--collapsed", taskListCollapsed);
        taskListHeader.setTitle(taskListCollapsed
                ? "Show the agent's tasks"
                : "Hide the agent's tasks");
    }

    @Override
    public void showCommandReference(final SafeHtml html) {
        commandReferenceContent.setHTML(html);
        commandReference.setVisible(true);
    }

    @Override
    public void hideCommandReference() {
        commandReference.setVisible(false);
    }

    /// Resizes the reference as its top edge is dragged: up to make it taller, taking the room from the messages.
    private void startReferenceResize(final MouseDownEvent event) {
        event.preventDefault();
        referenceDragStartY = event.getClientY();
        referenceDragStartHeight = commandReference.getOffsetHeight();
        if (referenceDrag != null) {
            referenceDrag.removeHandler();
        }
        referenceDrag = Event.addNativePreviewHandler(preview -> {
            final NativeEvent nativeEvent = preview.getNativeEvent();
            switch (preview.getTypeInt()) {
                case Event.ONMOUSEMOVE -> {
                    final int height = Math.max(MIN_REFERENCE_HEIGHT, Math.min(maxReferenceHeight(),
                            referenceDragStartHeight + referenceDragStartY - nativeEvent.getClientY()));
                    commandReference.getElement().getStyle().setHeight(height, Unit.PX);
                    preview.cancel();
                }
                case Event.ONMOUSEUP -> {
                    referenceDrag.removeHandler();
                    referenceDrag = null;
                }
                default -> {
                    // Nothing else is affected.
                }
            }
        });
    }

    /// @return The most the reference may take: most of the chat, leaving room for some of the messages.
    private int maxReferenceHeight() {
        return Math.max(MIN_REFERENCE_HEIGHT, (int) (widget.getOffsetHeight() * 0.7));
    }

    @Override
    public void setFollowUps(final List<String> suggestions) {
        followUps.clear();
        for (final String suggestion : suggestions) {
            final Button button = new Button();
            button.setStyleName("allow-focus askStroomAiViewImpl-followUp");
            button.setText(suggestion);
            button.setTitle("Ask this");
            button.addClickHandler(event -> {
                if (getUiHandlers() != null) {
                    getUiHandlers().onFollowUp(suggestion);
                }
            });
            followUps.add(button);
        }
        followUps.setVisible(!suggestions.isEmpty());
    }

    @Override
    public boolean isEmptyState() {
        return empty;
    }

    /// A chat with nothing in it is new already, so another isn't started, which would only leave unused chats.
    private void setNewChatEnabled(final boolean enabled) {
        newChat.setEnabled(enabled);
        newChat.setTitle(enabled
                ? "Start a new chat"
                : "This chat is new: ask it something first");
    }

    @Override
    public FlowPanel getChatList() {
        return chatList;
    }

    @Override
    public HTML getChatListActions() {
        return chatListActions;
    }

    @Override
    public HTML getChatListItems() {
        return chatListItems;
    }

    @Override
    public void clearChatSearch() {
        chatSearch.setText("");
    }

    @Override
    public void focusChatSearch() {
        chatSearch.setFocus(true);
    }

    @Override
    public void setChatListVisible(final boolean visible) {
        chatList.setVisible(visible);
        markdownPreview.setVisible(!visible);
        if (visible) {
            jumpToLatestBar.setVisible(false);
        }
        chatTitle.setStyleName(SWITCHER_OPEN_CLASS, visible);
        setEmptyState(empty);
    }

    @Override
    public void setContextIndicator(final SvgImage icon, final String text) {
        if (text != null && !text.isEmpty()) {
            contextIndicator.getElement().setInnerHTML(
                    "<span class='svgIcon'>" + icon.getSvg() + "</span> " + SafeHtmlUtils.htmlEscape(text));
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

    private void sendMessage() {
        // While the AI is working on a question, a message steers it rather than asking a new question.
        if (!message.getText().isEmpty()) {
            getUiHandlers().onSendMessage(getMessage());
            message.setText(null);
        }
    }

    public interface Binder extends UiBinder<Widget, AskStroomAiViewImpl> {

    }
}
