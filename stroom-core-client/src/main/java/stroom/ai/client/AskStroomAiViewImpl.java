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
import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.widget.button.client.InlineSvgButton;
import stroom.widget.tab.client.view.GlobalResizeObserver;

import com.google.gwt.animation.client.Animation;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseWheelEvent;
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

import java.util.ArrayList;
import java.util.List;

public class AskStroomAiViewImpl extends ViewWithUiHandlers<AskStroomAiUiHandlers> implements AskStroomAiView {

    private static final String SEND_BUTTON_NORMAL_TEXT = "Send";
    // How long after the user does something that scrolls the conversation its scrolling counts as theirs.
    private static final double USER_SCROLL_MS = 600;
    // How long after the user clicks a section's summary its opening or closing counts as theirs.
    private static final double USER_TOGGLE_MS = 1_000;
    private static final String LOADING_STYLE = "askStroomAiViewImpl-middle--loading";
    private static final String STOP_SHOWN_CLASS = "askStroomAiViewImpl-stop--shown";
    private static final String EMPTY_STYLE = "empty";
    private static final String SWITCHER_OPEN_CLASS = "askStroomAiViewImpl-switcher--open";
    private static final String OVERVIEW_OPEN_CLASS = "askStroomAiViewImpl-overviewToggle--on";
    private static final String OVERVIEW_CURRENT_MATCH_CLASS = "ai-overview--current";
    private static final String SHOWING_OVERVIEW_CLASS = "askStroomAiViewImpl--showingOverview";
    // How much each zoom step zooms the overview, and how far it may be zoomed.
    private static final double OVERVIEW_ZOOM_STEP = 1.2;
    private static final double MIN_OVERVIEW_ZOOM = 0.4;
    private static final double MAX_OVERVIEW_ZOOM = 4;
    // Fitted to a wide panel, the overview grows no larger than this, so its text isn't huge.
    private static final double MAX_FITTED_OVERVIEW_ZOOM = 1.5;
    // How long a zoom from the buttons takes.
    private static final int OVERVIEW_ZOOM_MS = 200;
    // The room either side of the overview, inside its panel (--ai-overview-margin in the CSS).
    private static final int OVERVIEW_MARGIN = 48;
    private static final String AUTO_APPROVE_ON_CLASS = "askStroomAiViewImpl-autoApprove--on";
    private static final String WORKING_CLASS = "askStroomAiViewImpl-bottom-inner--working";

    private boolean empty;
    private static final int MIN_REFERENCE_HEIGHT = 60;

    private final Widget widget;
    // True while the chat is working on a question.
    private boolean working;
    // True while anything else is in progress for the chat, e.g. search results downloading to attach.
    private boolean inProgress;
    // When the user's scrolling stops counting as theirs (see watchUserScrolling), as ms since the epoch, whether
    // the pointer is held down in the conversation, and when they last clicked a section's summary. Doubles, as
    // they are set from JavaScript.
    private double userScrollUntilMs;
    private boolean pointerHeld;
    private double summaryClickMs;

    @UiField
    FlowPanel inputBox;
    @UiField
    FlowPanel root;
    @UiField
    Label chatTitle;
    @UiField
    InlineSvgButton overviewToggle;
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
    FlowPanel overview;
    @UiField
    InlineSvgButton overviewSearchToggle;
    @UiField
    FlowPanel overviewSearchControls;
    @UiField
    TextBox overviewSearch;
    @UiField
    InlineSvgButton overviewSearchClear;
    @UiField
    Label overviewSearchCount;
    @UiField
    InlineSvgButton overviewPreviousMatch;
    @UiField
    InlineSvgButton overviewNextMatch;
    @UiField
    FlowPanel overviewZoomStrip;
    @UiField
    InlineSvgButton overviewZoomOut;
    @UiField
    InlineSvgButton overviewZoomIn;
    @UiField
    InlineSvgButton overviewFit;
    @UiField
    HTML overviewContent;
    // How far the overview is zoomed, and whether it is fitted to the panel's width (so refitted as it is redrawn).
    private double overviewZoom = 1;
    private boolean overviewFitted = true;
    // The zoom from the buttons under way, if one is.
    private OverviewZoomAnimation overviewZoomAnimation;
    // The match of the overview's search gone to, as an index into the matches, in order.
    private int overviewMatch;
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
    InlineSvgButton stop;
    @UiField
    InlineSvgButton run;
    @UiField
    SelectionBox<AiAgentMode> agentMode;
    @UiField
    InlineSvgButton autoApprove;
    @UiField
    Label contextIndicator;
    @UiField
    HTML attachments;
    @UiField
    SimplePanel modelRef;
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
    // Closes the list of chats, or the overview, on Escape while it is shown.
    private HandlerRegistration chatListEscape;
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
        attachments.setVisible(false);
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
                getUiHandlers().onConversationScrolled(isUserScrolling());
            }
        }, ScrollEvent.getType());
        watchUserScrolling(markdownPreview.getElement());
        // Content below the conversation, e.g. the task list, takes its room from the conversation as it grows.
        markdownPreview.addAttachHandler(event -> {
            if (event.isAttached()) {
                GlobalResizeObserver.addListener(markdownPreview.getElement(), element -> {
                    if (getUiHandlers() != null) {
                        getUiHandlers().onConversationResized();
                    }
                });
            } else {
                GlobalResizeObserver.removeListener(markdownPreview.getElement());
            }
        });

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
        chatSearch.setTitle("Search your chats' titles and your questions in them.\nEscape clears the search, "
                            + "or, once it is clear, goes back to the chat.");
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

        // The text first, as setting it replaces the button's content, icon and all.
        overviewToggle.setText("Chat history navigation");
        overviewToggle.setSvg(SvgImage.EXPLORE);
        overviewToggle.setTitle("Chat history navigation");
        overviewToggle.addClickHandler(event -> {
            if (getUiHandlers() != null) {
                getUiHandlers().onToggleOverview();
            }
        });
        overviewSearchToggle.setSvg(SvgImage.SEARCH);
        overviewSearchToggle.setTitle("Search the chat history");
        overviewSearchToggle.addClickHandler(event -> {
            // The search box takes the button's place while it is open.
            overviewSearchToggle.setVisible(false);
            overviewSearchControls.setVisible(true);
            overviewSearch.setFocus(true);
        });
        // An empty search left is put away again.
        overviewSearch.addBlurHandler(event -> {
            if (overviewSearch.getText().isEmpty()) {
                closeOverviewSearch();
            }
        });
        overviewSearch.getElement().setAttribute("placeholder", "Search the chat");
        overviewSearch.setTitle("Find the steps that hold some words.\nEnter goes to the next, Shift+Enter the last. "
                                + "Escape clears the search, or, once it is clear, goes back to the chat.");
        overviewSearch.addKeyDownHandler(event -> {
            if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
                event.preventDefault();
                goToOverviewMatch(event.isShiftKeyDown()
                        ? -1
                        : 1);
            } else if (event.getNativeKeyCode() == KeyCodes.KEY_ESCAPE && overviewSearchControls.isVisible()) {
                // Clears the search and puts it away, rather than closing the overview.
                event.preventDefault();
                event.stopPropagation();
                closeOverviewSearch();
                overviewSearchToggle.setFocus(true);
            }
        });
        overviewSearchClear.setSvg(SvgImage.CLEAR);
        overviewSearchClear.setTitle("Clear Search");
        overviewSearchClear.addClickHandler(event -> {
            closeOverviewSearch();
            overviewSearchToggle.setFocus(true);
        });
        overviewSearch.addKeyUpHandler(event -> {
            final int key = event.getNativeKeyCode();
            if (key != KeyCodes.KEY_ENTER && key != KeyCodes.KEY_ESCAPE && key != KeyCodes.KEY_SHIFT) {
                searchOverview();
            }
        });
        overviewPreviousMatch.setSvg(SvgImage.ARROW_UP);
        overviewPreviousMatch.setTitle("The match before (Shift+Enter)");
        overviewPreviousMatch.addClickHandler(event -> goToOverviewMatch(-1));
        overviewNextMatch.setSvg(SvgImage.ARROW_DOWN);
        overviewNextMatch.setTitle("The match after (Enter)");
        overviewNextMatch.addClickHandler(event -> goToOverviewMatch(1));
        overviewZoomOut.setSvg(SvgImage.ZOOM_OUT);
        overviewZoomOut.setTitle("Zoom out (Ctrl+wheel)");
        overviewZoomIn.setSvg(SvgImage.ZOOM_IN);
        overviewZoomIn.setTitle("Zoom in (Ctrl+wheel)");
        overviewFit.setSvg(SvgImage.FIT_WIDTH);
        overviewFit.setTitle("Fit to the panel's width");
        overviewZoomIn.addClickHandler(event -> {
            overviewFitted = false;
            animateOverviewZoom(clampZoom(overviewTargetZoom() * OVERVIEW_ZOOM_STEP));
        });
        overviewZoomOut.addClickHandler(event -> {
            overviewFitted = false;
            animateOverviewZoom(clampZoom(overviewTargetZoom() / OVERVIEW_ZOOM_STEP));
        });
        overviewFit.addClickHandler(event -> {
            overviewFitted = true;
            animateOverviewZoom(fittedZoom());
        });
        overviewContent.addDomHandler(event -> {
            // Ctrl+wheel (or a trackpad's pinch, which browsers send as one) zooms, about the pointer; the wheel
            // alone scrolls.
            if (event.isControlKeyDown() || event.isMetaKeyDown()) {
                event.preventDefault();
                zoomOverview(event.getDeltaY() < 0
                        ? OVERVIEW_ZOOM_STEP
                        : 1 / OVERVIEW_ZOOM_STEP, event.getNativeEvent());
            }
        }, MouseWheelEvent.getType());
        // The zoom buttons sit over the overview but aren't in it, so the wheel over them is passed on to it: to
        // scroll it, or zoom it with Ctrl.
        overviewZoomStrip.addDomHandler(event -> {
            event.preventDefault();
            if (event.isControlKeyDown() || event.isMetaKeyDown()) {
                zoomOverview(event.getDeltaY() < 0
                        ? OVERVIEW_ZOOM_STEP
                        : 1 / OVERVIEW_ZOOM_STEP, null);
            } else {
                scrollBy(overviewContent.getElement(), event.getNativeEvent());
            }
        }, MouseWheelEvent.getType());
        overviewContent.addDomHandler(event -> {
            final int messageId = overviewStepAt(event.getNativeEvent().getEventTarget());
            if (messageId > 0 && getUiHandlers() != null) {
                getUiHandlers().onOverviewStep(messageId);
            }
        }, ClickEvent.getType());
        overviewContent.addDomHandler(event -> {
            final int key = event.getNativeKeyCode();
            if (key == KeyCodes.KEY_ENTER || key == ' ') {
                final int messageId = overviewStepAt(event.getNativeEvent().getEventTarget());
                if (messageId > 0 && getUiHandlers() != null) {
                    event.preventDefault();
                    getUiHandlers().onOverviewStep(messageId);
                }
            }
        }, KeyDownEvent.getType());

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
        run.setTitle("Send message");
        run.setSvg(SvgImage.PLAY);
        run.setEnabled(false);

        // Shown while anything is in progress, beside Send, which stays for steering the agent as it works.
        stop.setSvg(SvgImage.STOP);
        stop.setTitle("Stop everything this chat is doing, including tool calls and downloads");
        stop.addClickHandler(event -> {
            if (getUiHandlers() != null) {
                getUiHandlers().onCancelProcessing();
            }
        });
        showStop();
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
        // Sending while the chat works steers it, so Send is there whenever there is something to send.
        run.setEnabled(!message.getText().isEmpty());
        showStop();
    }

    @Override
    public void setInProgress(final boolean inProgress) {
        this.inProgress = inProgress;
        showStop();
    }

    private void showStop() {
        stop.setStyleName(STOP_SHOWN_CLASS, working || inProgress);
    }

    @Override
    public void setModelRefSelection(final View view) {
        this.modelRef.setWidget(view.asWidget());
    }

    @Override
    public TaskMonitor createTaskMonitor() {
        // No spinner, as the glowing border round the input box shows when a question is being worked on.
        return new TaskMonitor() {
            @Override
            public void onStart(final Task task) {
                // Silent.
            }

            @Override
            public void onEnd(final Task task) {
                // Silent.
            }
        };
    }

    @UiHandler("message")
    public void onMessageKeyDown(final KeyDownEvent event) {
        // Enter sends the message; Shift+Enter starts a new line.
        if (!event.isShiftKeyDown() && event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            event.preventDefault();
            sendMessage();
        }
    }

    @UiHandler("message")
    public void onMessageKeyUp(final KeyUpEvent event) {
        run.setEnabled(!message.getText().isEmpty());
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
            sendMessage();
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
        // The list of chats and the overview take the conversation's place, so aren't shown as an empty chat.
        if (empty && !chatList.isVisible() && !overview.isVisible()) {
            root.addStyleName(EMPTY_STYLE);
        } else {
            root.removeStyleName(EMPTY_STYLE);
        }
    }

    @Override
    public void setLoading(final boolean loading) {
        if (loading) {
            markdownPreview.addStyleName(LOADING_STYLE);
        } else {
            markdownPreview.removeStyleName(LOADING_STYLE);
        }
    }

    @Override
    public void setAttachments(final SafeHtml html) {
        attachments.setHTML(html);
        attachments.setVisible(!html.asString().isEmpty());
    }

    @Override
    public Widget getAttachmentList() {
        return attachments;
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
        keepCurrentTaskInView();
    }

    // Scrolls a long task list to the step being worked on (or, if none is, the next to do, or once they are all
    // done, the last), in the middle where there is room, so the steps either side of it show too.
    private void keepCurrentTaskInView() {
        final Element items = taskListItems.getElement();
        if (!taskListItems.isVisible() || items.getClientHeight() == 0
            || items.getScrollHeight() <= items.getClientHeight()) {
            return;
        }
        Element current = firstTask(items, "ai-task--in_progress");
        if (current == null) {
            current = firstTask(items, "ai-task--not_started");
        }
        if (current == null) {
            current = items.getLastChild() != null
                    ? items.getLastChild().cast()
                    : null;
        }
        if (current == null) {
            return;
        }
        final int top = current.getAbsoluteTop() - items.getAbsoluteTop() + items.getScrollTop();
        items.setScrollTop(Math.max(0, top - (items.getClientHeight() - current.getOffsetHeight()) / 2));
    }

    private static Element firstTask(final Element items, final String className) {
        Element task = items.getFirstChildElement();
        while (task != null && !task.hasClassName(className)) {
            task = task.getNextSiblingElement();
        }
        return task;
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

    /// Escape in the list of chats goes back to the chat, unless there is a search to clear first: Escape in the
    /// search box clears it (see its key handler), and only once it is clear goes back. It does the same with
    /// nothing focused, but is left to anything shown over the chat, e.g. a dialog asking to delete a chat.
    private void closeChatListOnEscape(final Event.NativePreviewEvent preview) {
        final NativeEvent nativeEvent = preview.getNativeEvent();
        if (preview.getTypeInt() != Event.ONKEYDOWN || nativeEvent.getKeyCode() != KeyCodes.KEY_ESCAPE) {
            return;
        }
        final EventTarget eventTarget = nativeEvent.getEventTarget();
        if (!Element.is(eventTarget)) {
            return;
        }
        final Element target = Element.as(eventTarget);
        final boolean inChat = widget.getElement().isOrHasChild(target);
        final boolean nothingFocused = target == Document.get().getBody();
        final boolean searchToClear = (target == chatSearch.getElement() && !chatSearch.getText().isEmpty())
                                      || (target == overviewSearch.getElement()
                                          && overviewSearchControls.isVisible());
        if ((inChat || nothingFocused) && !searchToClear && getUiHandlers() != null) {
            preview.cancel();
            nativeEvent.preventDefault();
            if (overview.isVisible()) {
                getUiHandlers().onCloseOverview();
            } else {
                getUiHandlers().onCloseChatList();
            }
        }
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
        showConversationOrPanel();
        if (visible) {
            jumpToLatestBar.setVisible(false);
        }
        chatTitle.setStyleName(SWITCHER_OPEN_CLASS, visible);
        setEmptyState(empty);
    }

    @Override
    public void setOverviewVisible(final boolean visible) {
        if (visible) {
            // Each time it is shown it starts unsearched.
            overviewSearch.setText("");
            overviewSearchCount.setText("");
            overviewMatch = 0;
            showOverviewSearchClear();
            overviewPreviousMatch.setVisible(false);
            overviewNextMatch.setVisible(false);
            overviewSearchControls.setVisible(false);
            overviewSearchToggle.setVisible(true);
        }
        overview.setVisible(visible);
        overviewToggle.setStyleName(OVERVIEW_OPEN_CLASS, visible);
        // The panel is marked while the overview is shown, e.g. so the task list, which is about the conversation,
        // is put away.
        root.setStyleName(SHOWING_OVERVIEW_CLASS, visible);
        showConversationOrPanel();
        if (visible) {
            jumpToLatestBar.setVisible(false);
        }
        setEmptyState(empty);
    }

    // The list of chats and the overview each take the conversation's place, and close on Escape.
    private void showConversationOrPanel() {
        final boolean panel = chatList.isVisible() || overview.isVisible();
        markdownPreview.setVisible(!panel);
        if (chatListEscape != null) {
            chatListEscape.removeHandler();
            chatListEscape = null;
        }
        if (panel) {
            chatListEscape = Event.addNativePreviewHandler(this::closeChatListOnEscape);
        }
    }

    @Override
    public void setOverview(final String svg) {
        final Element content = overviewContent.getElement();
        // Following the latest step, as the conversation does, unless the user has scrolled up to look at others.
        final boolean atEnd = content.getScrollHeight() - content.getScrollTop() - content.getClientHeight() < 24;
        final int scrollTop = content.getScrollTop();
        overviewContent.setHTML(SafeHtmlUtils.fromTrustedString(svg));
        if (overviewFitted) {
            fitOverview();
        } else {
            sizeOverview();
        }
        content.setScrollTop(atEnd
                ? content.getScrollHeight()
                : scrollTop);
        backOverviewHits(content);
        showOverviewMatch(false);
    }

    /// Draws a box behind each place a word searched for is found in the overview, as SVG text has no background:
    /// sized to the word as the browser draws it, which only the browser knows.
    private static native void backOverviewHits(Element content) /*-{
        // Round the label of each line that matches, an outline shown when it is the match gone to.
        var links = content.querySelectorAll(".ai-overview-link.ai-overview--match");
        for (var l = 0; l < links.length; l++) {
            var texts = links[l].querySelectorAll("text");
            var left = Infinity, top = Infinity, right = -Infinity, bottom = -Infinity;
            for (var t = 0; t < texts.length; t++) {
                try {
                    var b = texts[t].getBBox();
                    left = Math.min(left, b.x);
                    top = Math.min(top, b.y);
                    right = Math.max(right, b.x + b.width);
                    bottom = Math.max(bottom, b.y + b.height);
                } catch (e) {
                    // Not drawn, so it can't be measured.
                }
            }
            if (right > left) {
                var outline = $doc.createElementNS("http://www.w3.org/2000/svg", "rect");
                outline.setAttribute("class", "ai-overview-link-outline");
                outline.setAttribute("x", left - 5);
                outline.setAttribute("y", top - 3);
                outline.setAttribute("width", right - left + 10);
                outline.setAttribute("height", bottom - top + 6);
                outline.setAttribute("rx", 4);
                // Before the label, so drawn behind it.
                links[l].insertBefore(outline, texts[0]);
            }
        }
        var hits = content.querySelectorAll(".ai-overview-hit");
        for (var i = 0; i < hits.length; i++) {
            var hit = hits[i];
            var text = hit.parentNode;
            try {
                var box = hit.getBBox();
                var back = $doc.createElementNS("http://www.w3.org/2000/svg", "rect");
                back.setAttribute("class", "ai-overview-hit-back");
                back.setAttribute("x", box.x - 1);
                back.setAttribute("y", box.y);
                back.setAttribute("width", box.width + 2);
                back.setAttribute("height", box.height);
                back.setAttribute("rx", 2);
                // Before the text, so drawn behind it.
                text.parentNode.insertBefore(back, text);
            } catch (e) {
                // Not drawn (e.g. hidden), so it can't be measured: the word is shown without its box.
            }
        }
    }-*/;

    // Searches the overview again for what is in the box, from its first match.
    private void searchOverview() {
        overviewMatch = 0;
        showOverviewSearchClear();
        if (getUiHandlers() != null) {
            getUiHandlers().onOverviewSearch(overviewSearch.getText());
        }
        showOverviewMatch(true);
    }

    // Clears the search and puts it away, leaving the button to open it.
    private void closeOverviewSearch() {
        final boolean searched = !overviewSearch.getText().isEmpty();
        overviewSearch.setText("");
        overviewSearchControls.setVisible(false);
        overviewSearchToggle.setVisible(true);
        if (searched) {
            searchOverview();
        }
    }

    // The button to clear the search is only there when there is a search, as in a Quick Filter.
    private void showOverviewSearchClear() {
        final boolean searching = !overviewSearch.getText().isEmpty();
        overviewSearchClear.setEnabled(searching);
        overviewSearchClear.setVisible(searching);
    }

    // Goes to the next match of the search, or back to the last, round to the other end.
    private void goToOverviewMatch(final int by) {
        final List<Element> matches = overviewMatches();
        if (!matches.isEmpty()) {
            overviewMatch = (overviewMatch + by + matches.size()) % matches.size();
            showOverviewMatch(true);
        }
    }

    // Marks the match gone to, says how many there are, and scrolls to it if asked.
    private void showOverviewMatch(final boolean scroll) {
        final List<Element> matches = overviewMatches();
        // The paginator is only there while searching, and only goes anywhere with more than one match.
        final boolean searching = !overviewSearch.getText().isBlank();
        overviewPreviousMatch.setVisible(searching);
        overviewNextMatch.setVisible(searching);
        overviewPreviousMatch.setEnabled(matches.size() > 1);
        overviewNextMatch.setEnabled(matches.size() > 1);
        if (!searching) {
            overviewSearchCount.setText("");
            return;
        }
        if (matches.isEmpty()) {
            overviewSearchCount.setText("No matches");
            return;
        }
        overviewMatch = Math.min(overviewMatch, matches.size() - 1);
        for (int i = 0; i < matches.size(); i++) {
            setClass(matches.get(i), OVERVIEW_CURRENT_MATCH_CLASS, i == overviewMatch);
        }
        overviewSearchCount.setText((overviewMatch + 1) + " of " + matches.size());
        if (scroll) {
            scrollIntoView(matches.get(overviewMatch));
        }
    }

    // The overview's steps that match its search, in order.
    private List<Element> overviewMatches() {
        final List<Element> matches = new ArrayList<>();
        final NodeList<Element> steps = overviewContent.getElement().getElementsByTagName("g");
        for (int i = 0; i < steps.getLength(); i++) {
            if (hasClass(steps.getItem(i), AiChatOverview.MATCH_CLASS)) {
                matches.add(steps.getItem(i));
            }
        }
        return matches;
    }

    // An SVG element's class is not a string to GWT, so it is read and set as an attribute.
    private static boolean hasClass(final Element element, final String className) {
        return (" " + element.getAttribute("class") + " ").contains(" " + className + " ");
    }

    private static void setClass(final Element element, final String className, final boolean on) {
        final String classes = (" " + element.getAttribute("class") + " ").replace(" " + className + " ", " ");
        element.setAttribute("class", (classes + (on
                ? className
                : "")).trim());
    }

    // Scrolls an element as the wheel would have, had it been over it.
    @Override
    public boolean isUserScrolling() {
        return pointerHeld || System.currentTimeMillis() < userScrollUntilMs;
    }

    // A section (e.g. a tool call or the model's reasoning) opened or closed: passed on if the user did it, by
    // clicking its summary (or pressing Enter or Space on it, which clicks it), not the chat.
    private void onSectionToggled(final boolean open) {
        if (System.currentTimeMillis() - summaryClickMs < USER_TOGGLE_MS && getUiHandlers() != null) {
            getUiHandlers().onSectionToggled(open);
        }
    }

    // Notes what the user does in the conversation that scrolls it (the wheel, touch, keys, or holding the pointer
    // down, e.g. dragging the scroll bar), so their scrolling can be told apart from the chat or the browser moving
    // it, and passes on sections being opened or closed.
    private native void watchUserScrolling(Element container) /*-{
        var view = this;
        var intent = function() {
            view.@stroom.ai.client.AskStroomAiViewImpl::userScrollUntilMs = Date.now()
                + @stroom.ai.client.AskStroomAiViewImpl::USER_SCROLL_MS;
        };
        ["wheel", "touchstart", "touchmove", "keydown"].forEach(function(type) {
            container.addEventListener(type, intent, {passive: true, capture: true});
        });
        container.addEventListener("pointerdown", function() {
            view.@stroom.ai.client.AskStroomAiViewImpl::pointerHeld = true;
            intent();
        }, true);
        $wnd.addEventListener("pointerup", function() {
            if (view.@stroom.ai.client.AskStroomAiViewImpl::pointerHeld) {
                view.@stroom.ai.client.AskStroomAiViewImpl::pointerHeld = false;
                intent();
            }
        }, true);
        container.addEventListener("click", function(e) {
            if (e.target && e.target.closest && e.target.closest("summary")) {
                view.@stroom.ai.client.AskStroomAiViewImpl::summaryClickMs = Date.now();
            }
        }, true);
        // Toggle doesn't bubble, so it is caught on its way down.
        container.addEventListener("toggle", $entry(function(e) {
            if (e.target && e.target.tagName === "DETAILS") {
                view.@stroom.ai.client.AskStroomAiViewImpl::onSectionToggled(Z)(e.target.open);
            }
        }), true);
    }-*/;

    private static native void scrollBy(Element element, NativeEvent wheel) /*-{
        var unit = wheel.deltaMode === 1
            ? 16
            : (wheel.deltaMode === 2
                ? element.clientHeight
                : 1);
        element.scrollBy(wheel.deltaX * unit, wheel.deltaY * unit);
    }-*/;

    private static native void scrollIntoView(Element element) /*-{
        element.scrollIntoView({block: "center", behavior: "smooth"});
    }-*/;

    // Zooms the overview by a factor, keeping what is under the pointer (or the middle) where it is.
    private void zoomOverview(final double factor, final NativeEvent pointer) {
        cancelOverviewZoom();
        final Element content = overviewContent.getElement();
        final double zoom = clampZoom(overviewZoom * factor);
        final double ratio = zoom / overviewZoom;
        final int x = pointer != null
                ? pointer.getClientX() - content.getAbsoluteLeft()
                : content.getClientWidth() / 2;
        final int y = pointer != null
                ? pointer.getClientY() - content.getAbsoluteTop()
                : content.getClientHeight() / 2;
        final double left = (content.getScrollLeft() + x) * ratio - x;
        final double top = (content.getScrollTop() + y) * ratio - y;
        overviewFitted = false;
        overviewZoom = zoom;
        sizeOverview();
        content.setScrollLeft((int) Math.round(left));
        content.setScrollTop((int) Math.round(top));
    }

    // Zooms the overview so that it is as wide as the panel.
    private void fitOverview() {
        cancelOverviewZoom();
        overviewZoom = fittedZoom();
        sizeOverview();
    }

    // The zoom that makes the overview as wide as the panel.
    private double fittedZoom() {
        final int width = overviewContent.getElement().getClientWidth() - 2 * OVERVIEW_MARGIN;
        return width > 0
                ? Math.max(MIN_OVERVIEW_ZOOM, Math.min(MAX_FITTED_OVERVIEW_ZOOM,
                (double) width / AiChatOverview.WIDTH))
                : 1;
    }

    private static double clampZoom(final double zoom) {
        return Math.max(MIN_OVERVIEW_ZOOM, Math.min(MAX_OVERVIEW_ZOOM, zoom));
    }

    // The zoom the overview is going to, if it is being zoomed, so steps taken quickly add up.
    private double overviewTargetZoom() {
        return overviewZoomAnimation != null
                ? overviewZoomAnimation.to
                : overviewZoom;
    }

    private void cancelOverviewZoom() {
        if (overviewZoomAnimation != null) {
            overviewZoomAnimation.cancel();
            overviewZoomAnimation = null;
        }
    }

    // Zooms the overview smoothly, keeping what is in the middle of the panel where it is.
    private void animateOverviewZoom(final double to) {
        cancelOverviewZoom();
        if (to == overviewZoom) {
            return;
        }
        overviewZoomAnimation = new OverviewZoomAnimation(overviewZoom, to);
        overviewZoomAnimation.run(OVERVIEW_ZOOM_MS);
    }

    // Sizes the overview's SVG for its zoom: as it is vector graphics, it is drawn sharp at any size.
    private void sizeOverview() {
        final Element svg = overviewContent.getElement().getFirstChildElement();
        if (svg == null) {
            return;
        }
        final long height = AiToolMessages.parseLong(svg.getAttribute("data-height"));
        svg.getStyle().setWidth(AiChatOverview.WIDTH * overviewZoom, Unit.PX);
        svg.getStyle().setHeight(height * overviewZoom, Unit.PX);
    }

    /// @return The id of the message whose step in the overview holds an element, or 0 if it isn't in a step.
    private static int overviewStepAt(final EventTarget eventTarget) {
        if (!Element.is(eventTarget)) {
            return 0;
        }
        Element element = Element.as(eventTarget);
        while (element != null && !element.hasAttribute(AiChatOverview.MESSAGE_ID_ATTRIBUTE)) {
            element = element.getParentElement();
        }
        return element != null
                ? (int) AiToolMessages.parseLong(element.getAttribute(AiChatOverview.MESSAGE_ID_ATTRIBUTE))
                : 0;
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

    /// Zooms the overview from one zoom to another, a little each frame, keeping what is in the middle of the panel
    /// where it is.
    private final class OverviewZoomAnimation extends Animation {

        private final double from;
        private final double to;
        private final int middleX;
        private final int middleY;
        private final double startLeft;
        private final double startTop;

        private OverviewZoomAnimation(final double from, final double to) {
            final Element content = overviewContent.getElement();
            this.from = from;
            this.to = to;
            this.middleX = content.getClientWidth() / 2;
            this.middleY = content.getClientHeight() / 2;
            this.startLeft = content.getScrollLeft();
            this.startTop = content.getScrollTop();
        }

        @Override
        protected void onUpdate(final double progress) {
            final Element content = overviewContent.getElement();
            overviewZoom = from + (to - from) * interpolate(progress);
            final double ratio = overviewZoom / from;
            sizeOverview();
            content.setScrollLeft((int) Math.round((startLeft + middleX) * ratio - middleX));
            content.setScrollTop((int) Math.round((startTop + middleY) * ratio - middleY));
        }

        @Override
        protected void onComplete() {
            super.onComplete();
            if (overviewZoomAnimation == this) {
                overviewZoomAnimation = null;
            }
        }
    }

    public interface Binder extends UiBinder<Widget, AskStroomAiViewImpl> {

    }
}
