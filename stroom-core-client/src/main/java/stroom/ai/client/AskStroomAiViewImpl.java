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
import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskMonitor;
import stroom.widget.button.client.InlineSvgButton;
import stroom.widget.spinner.client.SpinnerSmall;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.ScrollEvent;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.List;

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
    InlineSvgButton tabToggle;
    @UiField
    InlineSvgButton menu;
    @UiField
    Label emptyPrompt;

    @UiField
    SimplePanel markdownPreview;
    @UiField
    FlowPanel jumpToLatestBar;
    @UiField
    FlowPanel commandSuggestions;
    @UiField
    Button contextMeter;
    @UiField
    Button jumpToLatest;
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
        jumpToLatestBar.setVisible(false);
        contextMeter.setVisible(false);
        commandSuggestions.setVisible(false);
        markdownPreview.addDomHandler(event -> {
            if (getUiHandlers() != null) {
                getUiHandlers().onConversationScrolled();
            }
        }, ScrollEvent.getType());

        newChat.setText("New Conversation");
        newChat.setTitle("New Conversation");
        newChat.setSvg(SvgImage.ADD);
        newChat.setEnabled(true);

        setTabMode(false);
        tabToggle.setEnabled(true);

        menu.setText("More");
        menu.setTitle("More");
        menu.setSvg(SvgImage.ELLIPSES_VERTICAL);
        menu.setEnabled(true);

        // The title opens the chat switcher.
        chatTitle.addStyleName("askStroomAiViewImpl-switcher");
        chatTitle.setTitle("Switch Conversation");
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
        run.setEnabled(sending || !message.getText().isEmpty());
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
    public void setContextMeter(final String text, final String title, final String level) {
        if (text == null) {
            contextMeter.setVisible(false);
            return;
        }
        contextMeter.setText(text);
        contextMeter.setTitle(title);
        contextMeter.setStyleName("allow-focus askStroomAiViewImpl-contextMeter");
        if (level != null) {
            contextMeter.addStyleName("askStroomAiViewImpl-contextMeter--" + level);
        }
        contextMeter.setVisible(true);
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
                message.setText(command.getName() + " ");
                message.setFocus(true);
                message.setCursorPos(message.getText().length());
                setCommandSuggestions(null);
                run.setEnabled(true);
            });
            commandSuggestions.add(button);
        }
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
            sending = true;
            getUiHandlers().onSendMessage(getMessage());
            message.setText(null);
        }
    }

    public interface Binder extends UiBinder<Widget, AskStroomAiViewImpl> {

    }
}
