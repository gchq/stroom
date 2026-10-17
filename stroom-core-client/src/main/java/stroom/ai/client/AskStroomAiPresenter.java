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

import stroom.ai.client.AskStroomAiPresenter.AskStroomAiProxy;
import stroom.ai.client.AskStroomAiPresenter.AskStroomAiView;
import stroom.ai.shared.AiAgentMode;
import stroom.ai.shared.AiApprovalScope;
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatAttachment;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiChatRun;
import stroom.ai.shared.AiChatRunStatus;
import stroom.ai.shared.AiChatSearchHit;
import stroom.ai.shared.AiChatStatus;
import stroom.ai.shared.AiContextUsage;
import stroom.ai.shared.AiMcpPrompt;
import stroom.ai.shared.AiMcpPromptArgument;
import stroom.ai.shared.AiMemory;
import stroom.ai.shared.AiMemoryScope;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.AnswerAiApprovalRequest;
import stroom.ai.shared.AnswerAiElicitationRequest;
import stroom.ai.shared.AskStroomAiConfig;
import stroom.ai.shared.AskStroomAiConfig.DockLocation;
import stroom.ai.shared.AskStroomAiConfig.DockType;
import stroom.ai.shared.AskStroomAiContext;
import stroom.ai.shared.AskStroomAiRequest;
import stroom.ai.shared.CompactAiChatRequest;
import stroom.ai.shared.DownloadChatHistoryRequest;
import stroom.ai.shared.EditAiMessageRequest;
import stroom.ai.shared.GetAiMcpPromptRequest;
import stroom.ai.shared.SaveAiMemoryRequest;
import stroom.ai.shared.SetAiApproveEverythingRequest;
import stroom.ai.shared.UndoAiCompactionRequest;
import stroom.alert.client.event.AlertCallback;
import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.alert.client.event.PromptEvent;
import stroom.content.client.event.RefreshContentTabEvent;
import stroom.core.client.ContentManager;
import stroom.core.client.LocationManager;
import stroom.data.client.event.AskStroomAiBadgeEvent;
import stroom.data.client.event.AskStroomAiEvent;
import stroom.data.client.event.ShowAskStroomAiEvent;
import stroom.data.client.presenter.DataViewType;
import stroom.data.client.presenter.DisplayMode;
import stroom.data.client.presenter.ShowDataEvent;
import stroom.dispatch.client.ExportFileCompleteUtil;
import stroom.dispatch.client.RestError;
import stroom.docref.DocRef;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.entity.client.presenter.MarkdownConverter;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.explorer.client.presenter.DocumentTypeCache;
import stroom.main.client.event.DockEvent;
import stroom.main.client.event.DockResizeEvent;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.pipeline.shared.SourceLocation;
import stroom.preferences.client.DateTimeFormatter;
import stroom.security.shared.DocumentPermission;
import stroom.svg.shared.SvgImage;
import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.ClipboardUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.menu.client.presenter.HideMenuEvent;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuBuilder;
import stroom.widget.menu.client.presenter.Separator;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.PopupLocation;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.Rect;
import stroom.widget.util.client.Size;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.dom.client.TextAreaElement;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public class AskStroomAiPresenter
        extends MyPresenter<AskStroomAiView, AskStroomAiProxy>
        implements AskStroomAiUiHandlers, ShowAskStroomAiEvent.Handler, AskStroomAiEvent.Handler {

    // Moment.js patterns for the day a message is from, as a key and as shown in the divider.
    private static final String DAY_KEY_PATTERN = "YYYYMMDD";
    private static final String DAY_LABEL_PATTERN = "dddd D MMMM YYYY";
    private static final int ACTIVE_POLL_MS = 1_000;
    private static final int HIDDEN_POLL_MS = 5_000;
    private static final int STATUS_POLL_MS = 15_000;
    private static final int HIDDEN_STATUS_POLL_MS = 60_000;
    // For background checks, which should not show a spinner.
    private static final TaskMonitorFactory SILENT = () -> new TaskMonitor() {
        @Override
        public void onStart(final Task task) {
            // Silent.
        }

        @Override
        public void onEnd(final Task task) {
            // Silent.
        }
    };
    private static final DockBehaviour DEFAULT_DOCK_BEHAVIOUR = new DockBehaviour(DockType.DOCK, DockLocation.RIGHT);
    private static final int DEFAULT_DOCK_WIDTH = 350;
    private static final int DEFAULT_DOCK_HEIGHT = 250;
    // As the server keeps; where the title is shown, it is cut short to fit with an ellipsis.
    private static final int MAX_TITLE_LENGTH = 200;
    private static final SafeHtml SUMMARY = SafeHtmlUtils.fromSafeConstant("summary");
    private static final SafeHtml DETAILS = SafeHtmlUtils.fromSafeConstant("details");
    private static final SafeHtml BUTTON = SafeHtmlUtils.fromSafeConstant("button");
    private static final String WORKING_MESSAGE_ID = "ai-working-message";
    private static final String WORKING_TEXT_ID = "ai-working-text";
    // On what the model said alongside its tool calls, while the Working line shows it.
    private static final String SHOWN_AS_PROGRESS_CLASS = "ai-message--shown-as-progress";
    private static final String MESSAGE_ID_ATTRIBUTE = "data-message-id";
    private static final String USER_ACTION_MESSAGE_ATTRIBUTE = "data-user-action-message";
    private static final String DEFAULT_CHAT_TITLE = "New chat";
    private static final SafeHtml MARK = SafeHtmlUtils.fromSafeConstant("mark");
    private static final int MAX_FOLLOW_UPS = 3;
    // Searching the list of chats: how long to wait after the last key, and the shortest text searched for (as the
    // server finds nothing for less).
    private static final int SEARCH_DELAY_MS = 300;
    private static final int MIN_SEARCH_CHARS = 2;
    private static final String MARK_ALL_READ_ATTRIBUTE = "data-mark-all-read";
    private static final String DELETE_ALL_CHATS_ATTRIBUTE = "data-delete-all-chats";
    private static final String USER_ACTION_ID_ATTRIBUTE = "data-user-action-id";
    // A tool result's buttons are shown in a card of their own, outside the turn's activity.
    private static final String USER_ACTIONS_ID_SUFFIX = "-actions";
    private static final SafeHtml PRE = SafeHtmlUtils.fromSafeConstant("pre");
    private static final String CHAT_ID_ATTRIBUTE = "data-chat-id";
    private static final String DELETE_CHAT_ATTRIBUTE = "data-delete-chat-id";
    private static final String RUN_STATUS_ID = "ai-run-status";
    private static final String RESUME_RUN_ATTRIBUTE = "data-resume-run";
    private static final String RETRY_TURN_ATTRIBUTE = "data-retry-turn-id";
    private static final String ERROR_RETRY_CLASS = "ai-message-error-retry";
    private static final String APPROVAL_ID_ATTRIBUTE = "data-approval-id";
    private static final String APPROVAL_ANSWER_ATTRIBUTE = "data-approval-answer";
    private static final String APPROVAL_SCOPE_ATTRIBUTE = "data-approval-scope";
    private static final String APPROVAL_CALL_ATTRIBUTE = "data-approval-call";
    private static final String EDIT_MESSAGE_ATTRIBUTE = "data-edit-message-id";
    private static final String EDIT_SEND_ATTRIBUTE = "data-edit-send";
    private static final String EDIT_CANCEL_ATTRIBUTE = "data-edit-cancel";
    private static final String COPY_TEXT_ATTRIBUTE = "data-copy-text";
    private static final String DAY_DIVIDER_CLASS = "ai-day-divider";
    private static final String PENDING_MESSAGE_ID = "pending";
    private static final String UNDO_COMPACTION_ATTRIBUTE = "data-undo-compaction";
    private static final String ELICITATION_ATTRIBUTE = "data-elicitation-id";
    private static final String ELICITATION_ACTION_ATTRIBUTE = "data-elicitation-action";
    // On the Yes and No buttons of a yes or no question: the question's field, and the answer each gives.
    private static final String ELICITATION_FIELD_ATTRIBUTE = "data-elicitation-field";
    private static final String ELICITATION_VALUE_ATTRIBUTE = "data-elicitation-value";
    // The tool Stroom AI's agent asks the user questions with.
    private static final String ASK_USER_TOOL = "ask_user";
    private static final int DEFAULT_COMPACT_AT_PERCENT = 80;
    private static final String LIVE_ANSWER_ID = "ai-live-answer";
    private static final int STREAMING_POLL_MS = 500;
    private static final long KEEP_POLLING_AFTER_ANSWER_MS = 15_000;
    private static final int BOTTOM_TOLERANCE_PX = 40;

    private final DocSelectionBoxPresenter docSelectionBoxPresenter;
    private final MarkdownConverter markdownConverter;
    private final AskStroomAiClient askStroomAiClient;
    private final Provider<AskStroomAiConfigPresenter> askStroomAiConfigPresenterProvider;
    private final Provider<DownloadChatPresenter> downloadChatPresenterProvider;
    private final Provider<AiMemoryPresenter> memoryPresenterProvider;
    private final Provider<AiToolApprovalsPresenter> toolApprovalsPresenterProvider;
    private final Provider<AiAttachmentDataPresenter> aiAttachmentDataPresenterProvider;
    private final Provider<AskStroomAiTabPresenter> askStroomAiTabPresenterProvider;
    private final ContentManager contentManager;
    private final LocationManager locationManager;
    private final DateTimeFormatter dateTimeFormatter;
    private AskStroomAiContext data;
    private AiChat currentChat;
    // The approval requests shown, so their cards can be redrawn once answered.
    private final Map<Integer, AiChatMessage> approvalRequests = new HashMap<>();
    private boolean titleGenerated;
    private int lastSeenMessageId;

    // The statuses of the user's chats, for the switcher and badges.
    private List<AiChatStatus> chatStatuses = new ArrayList<>();
    // False until the first statuses arrive, so the chats as they were when the page opened are not notified.
    private boolean statusesKnown;
    // True if the current chat was still working when it was last polled.
    private boolean currentRunActive;
    // True while the list of chats is shown in place of the conversation.
    private boolean chatListShowing;
    // The command whose reference is shown, or null.
    private String referenceCommand;
    // The latest suggested follow-ups, while the answer they follow is the latest thing in the chat.
    private List<String> followUps = List.of();
    // Searching the list of chats: what is in the box, the text the hits are for, the hits (null when not
    // searching), and a count so that a slow answer to an earlier search is ignored.
    private String searchText = "";
    private String searchedText;
    private List<AiChatSearchHit> searchHits;
    private int searchSequence;
    private final Timer searchTimer = new Timer() {
        @Override
        public void run() {
            runChatSearch();
        }
    };
    private long statusVersion;
    private Timer statusTimer;
    // How the chat is shown: DOCK, DIALOG or TAB, or null if hidden.
    private DockType shownAs;
    private boolean docked;
    private AskStroomAiTabPresenter tabPresenter;
    /**
     * True while a request is with the server. Polling continues for as long as this is set, since a
     * poll can otherwise outrun the work and conclude it has finished before it has started.
     */
    private boolean requestInFlight;
    private boolean polling;
    private boolean pollAgain;
    // Until when to keep polling even though the run is waiting for the user, as the user has just answered and
    // the run may not have carried on yet.
    private long keepPollingUntilMs;
    // The rendered messages, by id, e.g. to edit a question or redraw an answered card.
    private final Map<Integer, AiChatMessage> renderedMessages = new HashMap<>();
    // Each turn's reasoning and tool calls, grouped above its answer.
    private final AiActivityGroups activityGroups = new AiActivityGroups();
    // The day of the last message added, for day dividers.
    private String lastRenderedDay;
    // True while the conversation is scrolled to the bottom, so new messages scroll into view.
    private boolean followBottom = true;
    // What the model said alongside its latest tool calls, kept out of its turn's activity group while the Working
    // line shows it, or null once it is shown there.
    private AiChatMessage interimShownAsProgress;
    // Where the conversation was scrolled to last, to tell the user scrolling up from content growing below.
    private int lastScrollTop;
    // The prompts the model's MCP servers offer, as slash commands, once fetched.
    private List<AiMcpPrompt> mcpPrompts;
    private boolean fetchingPrompts;
    // How much of the model's context window the chat uses, as last reported.
    private AiContextUsage contextUsage;
    private final DocumentTypeCache documentTypeCache;

    @Inject
    public AskStroomAiPresenter(final EventBus eventBus,
                                final AskStroomAiView view,
                                final AskStroomAiProxy askStroomAiProxy,
                                final DocSelectionBoxPresenter docSelectionBoxPresenter,
                                final MarkdownConverter markdownConverter,
                                final AskStroomAiClient askStroomAiClient,
                                final Provider<AskStroomAiConfigPresenter> askStroomAiConfigPresenterProvider,
                                final Provider<DownloadChatPresenter> downloadChatPresenterProvider,
                                final Provider<AiMemoryPresenter> memoryPresenterProvider,
                                final Provider<AiToolApprovalsPresenter> toolApprovalsPresenterProvider,
                                final Provider<AiAttachmentDataPresenter> aiAttachmentDataPresenterProvider,
                                final Provider<AskStroomAiTabPresenter> askStroomAiTabPresenterProvider,
                                final ContentManager contentManager,
                                final LocationManager locationManager,
                                final DateTimeFormatter dateTimeFormatter,
                                final DocumentTypeCache documentTypeCache) {
        super(eventBus, view, askStroomAiProxy);
        this.markdownConverter = markdownConverter;
        this.askStroomAiClient = askStroomAiClient;
        this.askStroomAiConfigPresenterProvider = askStroomAiConfigPresenterProvider;
        this.downloadChatPresenterProvider = downloadChatPresenterProvider;
        this.memoryPresenterProvider = memoryPresenterProvider;
        this.toolApprovalsPresenterProvider = toolApprovalsPresenterProvider;
        this.aiAttachmentDataPresenterProvider = aiAttachmentDataPresenterProvider;
        this.askStroomAiTabPresenterProvider = askStroomAiTabPresenterProvider;
        this.contentManager = contentManager;
        this.locationManager = locationManager;
        this.docSelectionBoxPresenter = docSelectionBoxPresenter;
        this.dateTimeFormatter = dateTimeFormatter;
        this.documentTypeCache = documentTypeCache;

        getView().setModelRefSelection(docSelectionBoxPresenter.getView());
        docSelectionBoxPresenter.setIncludedTypes(OpenAIModelDoc.TYPE);
        docSelectionBoxPresenter.setRequiredPermissions(DocumentPermission.USE);
        view.setUiHandlers(this);

        // Initiate the selection box presenter with the default model if one is set.
        readModel();
    }

    @Override
    protected void onBind() {
        super.onBind();
        // Keep the badges and the switcher up to date, while the page is open.
        statusTimer = new Timer() {
            @Override
            public void run() {
                checkChatStatuses();
            }
        };
        statusTimer.schedule(0);
        registerHandler(docSelectionBoxPresenter.addDataSelectionHandler(event ->
                writeModel()));

        registerHandler(getView().getChatList().addDomHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                onChatListClick(e.getNativeEvent().getEventTarget().cast());
            }
        }, ClickEvent.getType()));

        registerHandler(getView().getMarkdownContainer().addDomHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                final Element target = e.getNativeEvent().getEventTarget().cast();
                final Element link = ElementUtil.findParent(target, element ->
                        element.getTagName().equalsIgnoreCase("a"), 6);
                if (link != null) {
                    e.preventDefault();
                    onLinkClick(link.getAttribute("href"));
                    return;
                }
                final Element button = ElementUtil.findParent(target, element ->
                        element.getTagName().equalsIgnoreCase("button"), 5);
                if (button != null) {
                    // Capture "View data" click events.
                    final String attachmentIdStr = button.getAttribute("data-attachment-id");
                    if (NullSafe.isNonBlankString(attachmentIdStr)) {
                        final String desc = button.getAttribute("data-attachment-desc");
                        onViewAttachmentData(Integer.parseInt(attachmentIdStr), desc);
                        return;
                    }

                    // Capture retry click events.
                    final String retryTurnIdStr = button.getAttribute(RETRY_TURN_ATTRIBUTE);
                    if (NullSafe.isNonBlankString(retryTurnIdStr)) {
                        onRetry(Integer.parseInt(retryTurnIdStr));
                        return;
                    }

                    // A step an MCP tool's result left to the user.
                    final String userActionMessage = button.getAttribute(USER_ACTION_MESSAGE_ATTRIBUTE);
                    if (NullSafe.isNonBlankString(userActionMessage)) {
                        onUserActionButton(button, Integer.parseInt(userActionMessage),
                                button.getAttribute(USER_ACTION_ID_ATTRIBUTE));
                        return;
                    }

                    // Answering, or going back to, one of the agent's questions.
                    if (button.hasAttribute(AiElicitationCard.OPTION_ATTRIBUTE)) {
                        onQuestionAnswered(button, button.getAttribute(AiElicitationCard.OPTION_ATTRIBUTE));
                        return;
                    }
                    if (button.hasAttribute(AiElicitationCard.BACK_ATTRIBUTE)) {
                        onQuestionBack(button);
                        return;
                    }

                    // Answering a form from an MCP tool.
                    final String elicitationStr = button.getAttribute(ELICITATION_ATTRIBUTE);
                    if (NullSafe.isNonBlankString(elicitationStr)) {
                        onAnswerForm(button, Integer.parseInt(elicitationStr),
                                button.getAttribute(ELICITATION_ACTION_ATTRIBUTE));
                        return;
                    }

                    // Undoing a compaction.
                    final String undoStr = button.getAttribute(UNDO_COMPACTION_ATTRIBUTE);
                    if (NullSafe.isNonBlankString(undoStr)) {
                        onUndoCompaction(Integer.parseInt(undoStr));
                        return;
                    }

                    // Editing a question.
                    final String editIdStr = button.getAttribute(EDIT_MESSAGE_ATTRIBUTE);
                    if (NullSafe.isNonBlankString(editIdStr)) {
                        onEditMessage(Integer.parseInt(editIdStr));
                        return;
                    }
                    final String editSendStr = button.getAttribute(EDIT_SEND_ATTRIBUTE);
                    if (NullSafe.isNonBlankString(editSendStr)) {
                        onSendEdit(Integer.parseInt(editSendStr));
                        return;
                    }
                    final String editCancelStr = button.getAttribute(EDIT_CANCEL_ATTRIBUTE);
                    if (NullSafe.isNonBlankString(editCancelStr)) {
                        onCancelEdit(Integer.parseInt(editCancelStr));
                        return;
                    }

                    // Copy an answer's text as shown.
                    if (NullSafe.isNonBlankString(button.getAttribute(COPY_TEXT_ATTRIBUTE))) {
                        final Element message = ElementUtil.findParent(button, element ->
                                element.hasClassName("ai-message"), 6);
                        final Element content = message != null
                                ? ElementUtil.findChild(message, "ai-message-content")
                                : null;
                        if (content != null) {
                            ClipboardUtil.copy(content.getInnerText());
                            flashCopied(button);
                        }
                        return;
                    }

                    // Capture approval answers.
                    final String approvalIdStr = button.getAttribute(APPROVAL_ID_ATTRIBUTE);
                    if (NullSafe.isNonBlankString(approvalIdStr)) {
                        final String scope = button.getAttribute(APPROVAL_SCOPE_ATTRIBUTE);
                        final String callId = button.getAttribute(APPROVAL_CALL_ATTRIBUTE);
                        onApprovalButton(Integer.parseInt(approvalIdStr),
                                "approve".equals(button.getAttribute(APPROVAL_ANSWER_ATTRIBUTE)),
                                NullSafe.isNonBlankString(scope)
                                        ? AiApprovalScope.valueOf(scope)
                                        : AiApprovalScope.ONCE,
                                NullSafe.isNonBlankString(callId)
                                        ? callId
                                        : null);
                        return;
                    }

                    // Capture resume click events.
                    if (NullSafe.isNonBlankString(button.getAttribute(RESUME_RUN_ATTRIBUTE))) {
                        onResumeRun();
                        return;
                    }

                    // Capture delete click events.
                    final String messageIdStr = button.getAttribute("data-delete-message-id");
                    if (NullSafe.isNonBlankString(messageIdStr)) {
                        onDeleteMessage(Integer.parseInt(messageIdStr));
                        return;
                    }

                    // Capture copy click events.
                    final String data = button.getAttribute("data");
                    if (NullSafe.isNonBlankString(data)) {
                        if (ClipboardUtil.copy(data)) {
                            flashCopied(button);
                        }
                    }
                }
            }
        }, ClickEvent.getType()));

        // In a question being edited, Escape cancels the edit and Ctrl+Enter sends it. In a form from an MCP tool,
        // Enter submits it.
        registerHandler(getView().getMarkdownContainer().addDomHandler(e -> {
            final Element target = e.getNativeEvent().getEventTarget().cast();
            if (e.getNativeKeyCode() == KeyCodes.KEY_ENTER
                && target.hasClassName(AiElicitationCard.CUSTOM_ANSWER_CLASS)) {
                // The user's own answer to one of the agent's questions.
                e.preventDefault();
                final String answer = InputElement.as(target).getValue().trim();
                if (!answer.isEmpty()) {
                    onQuestionAnswered(target, answer);
                }
                return;
            }
            if (e.getNativeKeyCode() == KeyCodes.KEY_ENTER && target.hasAttribute(AiElicitationCard.FIELD_ATTRIBUTE)) {
                final Element card = ElementUtil.findParent(target, element ->
                        element.hasClassName("ai-message"), 8);
                final Element submit = card != null
                        ? findFormSubmit(card)
                        : null;
                if (submit != null && !submit.getPropertyBoolean("disabled")) {
                    e.preventDefault();
                    onAnswerForm(submit, Integer.parseInt(submit.getAttribute(ELICITATION_ATTRIBUTE)), "ACCEPT");
                }
                return;
            }
            if (!target.hasClassName("ai-edit-text")) {
                return;
            }
            final Element editor = ElementUtil.findParent(target, element ->
                    element.hasAttribute(MESSAGE_ID_ATTRIBUTE), 3);
            final String idStr = editor != null
                    ? editor.getAttribute(MESSAGE_ID_ATTRIBUTE)
                    : null;
            if (NullSafe.isBlankString(idStr)) {
                return;
            }
            if (e.getNativeKeyCode() == KeyCodes.KEY_ESCAPE) {
                e.preventDefault();
                e.stopPropagation();
                onCancelEdit(Integer.parseInt(idStr));
            } else if (e.getNativeKeyCode() == KeyCodes.KEY_ENTER && e.isControlKeyDown()) {
                e.preventDefault();
                onSendEdit(Integer.parseInt(idStr));
            }
        }, KeyDownEvent.getType()));

        // Listen for dock splitter resize events to persist the new size.
        addRegisteredHandler(DockResizeEvent.getType(), event -> {
            if (docked) {
                getDockBehaviourFromPrefs(dockBehaviour -> {
                    final Size newSize = event.getNewSize();
                    final DockLocation loc = dockBehaviour.getDockLocation();
                    final int dimension;
                    if (loc == DockLocation.LEFT || loc == DockLocation.RIGHT) {
                        dimension = (int) newSize.getWidth();
                    } else {
                        dimension = (int) newSize.getHeight();
                    }
                    saveDockSizeToPrefs(dimension);
                });
            }
        });
    }

    @Override
    protected void revealInParent() {

    }

    @ProxyEvent
    @Override
    public void onAsk(final AskStroomAiEvent event) {
        // Show if not already showing.
        ShowAskStroomAiEvent.fire(this, true);

        Scheduler.get().scheduleDeferred(() -> {
            // Auto-create a chat if none exists.
            ensureChat(() -> {
                setContext(event.getData());
                getView().focus();

                // Immediately send context-only request to create the attachment on the server
                // and start the async download. The ATTACHMENT message will appear in the chat
                // history when we poll for new messages.
                if (this.data != null) {
                    getView().setEmptyState(false);
                    askStroomAiClient.getConfig(config -> {
                        final AskStroomAiRequest request = new AskStroomAiRequest(
                                currentChat, config, this.data, null);
                        // Generate a title from the attachment type.
                        maybeGenerateTitle(getContextTitle(this.data));
                        // Clear context so it isn't re-attached when the user sends a question.
                        this.data = null;
                        getView().clearContextIndicator();
                        askStroomAiClient.sendMessage(request,
                                response -> pollForNewMessages(),
                                error -> showError(error, "Failed to create attachment", null),
                                getView());
                    }, getView());
                }
            });
        });
    }

    @ProxyEvent
    @Override
    public void onShow(final ShowAskStroomAiEvent event) {
        getDockSize(dockSize -> {
            getDockBehaviourFromPrefs(dockBehaviour -> {
                PanelState.setOpen(event.isShow());
                if (event.isShow()) {
                    if (shownAs == null) {
                        showAs(dockBehaviour, dockSize);
                        if (currentChat == null) {
                            restoreLastChat();
                        }
                    }
                } else if (shownAs != null) {
                    hideShown();
                }
            });
        });
    }

    /**
     * Shows the chat in the panel (docked), in a dialog or in a content tab.
     */
    private void showAs(final DockBehaviour dockBehaviour, final Size dockSize) {
        final DockType dockType = dockBehaviour.getDockType();
        getView().setTabMode(dockType == DockType.TAB);
        if (dockType == DockType.DOCK) {
            shownAs = DockType.DOCK;
            docked = true;
            DockEvent.fire(this, this, dockBehaviour, dockSize);
        } else if (dockType == DockType.TAB) {
            shownAs = DockType.TAB;
            final AskStroomAiTabPresenter tab = tabPresenter();
            tab.setChatTitle(NullSafe.get(currentChat, AiChat::getTitle));
            tab.setContent(getView().asWidget());
            contentManager.open(closeEvent -> {
                // Closed by the user.
                shownAs = null;
                PanelState.setOpen(false);
                closeEvent.getCallback().closeTab(true);
                ShowAskStroomAiEvent.fire(this, false);
            }, tab, tab);
        } else {
            showAsDialog();
        }
        getView().focus();
    }

    /**
     * Opens the chat the user last looked at, so after a page refresh things are as they were left.
     */
    private void restoreLastChat() {
        askStroomAiClient.getChatStatuses(0, response -> {
            if (currentChat != null || response.getStatuses() == null || response.getStatuses().isEmpty()) {
                return;
            }
            AiChatStatus last = response.getStatuses().get(0);
            for (final AiChatStatus status : response.getStatuses()) {
                if (status.getLastOpenedMs() > last.getLastOpenedMs()) {
                    last = status;
                }
            }
            loadChat(new AiChat(last.getChatId(), 0L, last.getUpdateTimeMs(), null, last.getTitle()));
        }, SILENT);
    }

    /**
     * Hides the chat from wherever it is shown. Runs and polling belong to the chat, so carry on.
     */
    private void hideShown() {
        final DockType was = shownAs;
        shownAs = null;
        if (was == DockType.DOCK) {
            docked = false;
            DockEvent.fireUndock(this, this);
        } else if (was == DockType.TAB) {
            contentManager.forceClose(tabPresenter(), true);
        } else if (was != null) {
            HidePopupEvent.builder(this).fire();
        }
    }

    private AskStroomAiTabPresenter tabPresenter() {
        if (tabPresenter == null) {
            tabPresenter = askStroomAiTabPresenterProvider.get();
        }
        return tabPresenter;
    }

    @Override
    public void onToggleTab() {
        getDockBehaviourFromPrefs(dockBehaviour -> onDockBehaviourChange(new DockBehaviour(
                dockBehaviour.getDockType() == DockType.TAB
                        ? DockType.DOCK
                        : DockType.TAB,
                dockBehaviour.getDockLocation())));
    }

    /// Opens a link from the chat without leaving Stroom: a link to a Stroom document opens it here, an
    /// external link opens in a new browser tab, and a link within the page scrolls to its target.
    private void onLinkClick(final String href) {
        final DocRef docRef = ChatLinks.openDocTarget(href);
        if (docRef != null) {
            documentTypeCache.fetch(types -> {
                // DocumentTypes' lookup map isn't filled in the browser, so look through the list.
                if (NullSafe.stream(types.getTypes()).anyMatch(type -> docRef.getType().equals(type.getType()))) {
                    OpenDocumentEvent.fire(this, docRef, true);
                } else {
                    AlertEvent.fireError(this, "This link is to an unknown type of document, '"
                                               + docRef.getType() + "'.", null);
                }
            }, this);
        } else if (ChatLinks.openStreamTarget(href) != null) {
            final ChatLinks.StreamTarget stream = ChatLinks.openStreamTarget(href);
            // In a Stroom tab, at the event if there is one.
            ShowDataEvent.fire(this,
                    SourceLocation.builder(stream.streamId())
                            .withPartIndex(0L)
                            .withRecordIndex(stream.recordNo() == null
                                    ? 0L
                                    : stream.recordNo() - 1)
                            .build(),
                    DataViewType.PREVIEW,
                    DisplayMode.STROOM_TAB);
        } else if (ChatLinks.isExternal(href)) {
            Window.open(href.trim(), "_blank", "noopener,noreferrer");
        } else if (href != null && href.startsWith("#") && href.length() > 1) {
            final Element anchor = Document.get().getElementById(href.substring(1));
            if (anchor != null && getView().getMarkdownContainer().getElement().isOrHasChild(anchor)) {
                anchor.scrollIntoView();
            }
        }
    }

    @Override
    public void onShowMenu(final Element target) {
        final boolean hasChat = currentChat != null;
        final List<Item> items = MenuBuilder.builder()
                // Things to do with this chat.
                .withInfoMenuItem(item -> item
                        .text(SafeHtmlUtils.fromSafeConstant("This chat"))
                        .disabled())
                .withIconMenuItem(item -> item
                        .icon(SvgImage.EDIT)
                        .text("Rename Chat")
                        .enabled(hasChat)
                        .command(this::onRename))
                .withIconMenuItem(item -> item
                        .icon(SvgImage.COLLAPSE_UP)
                        .text("Compact Context")
                        .enabled(hasChat)
                        .command(this::onCompact))
                .withIconMenuItem(item -> item
                        .icon(SvgImage.OK)
                        .text("Tool Approvals")
                        .enabled(hasChat)
                        .command(() -> toolApprovalsPresenterProvider.get().show(currentChat)))
                .withIconMenuItem(item -> item
                        .icon(SvgImage.DOWNLOAD)
                        .text("Download History")
                        .enabled(hasChat)
                        .command(this::onDownloadChat))
                .withIconMenuItem(item -> item
                        .icon(SvgImage.DELETE)
                        .text("Delete Chat")
                        .enabled(hasChat)
                        .command(this::onDeleteChat))
                .withSeparator()
                // Things that go beyond this chat: memory is loaded into all the user's chats, and the settings
                // apply to them all. The separator sets them apart, without a heading.
                .withIconMenuItem(item -> item
                        .icon(SvgImage.INFO)
                        .text("Memory")
                        .enabled(hasChat)
                        .command(() -> memoryPresenterProvider.get().show(currentChat)))
                .withIconMenuItem(item -> item
                        .icon(SvgImage.SETTINGS)
                        .text("Settings")
                        .command(this::onChangeConfig))
                .build();
        ShowMenuEvent.builder()
                .items(items)
                .popupPosition(new PopupPosition(new Rect(target).grow(3), PopupLocation.BELOW))
                .fire(this);
    }

    /**
     * @return True if the user is looking at the chat: it is shown, and the browser tab is not hidden.
     */
    private boolean isLooking() {
        return shownAs != null && !DocumentVisibility.isHidden();
    }

    /**
     * Checks on the user's chats, for the switcher and the badge on the toolbar button, then checks again later
     * (less often when the browser tab is hidden). The server sends nothing if nothing has changed.
     */
    private void checkChatStatuses() {
        askStroomAiClient.getChatStatuses(statusVersion, response -> {
            statusVersion = response.getVersion();
            if (response.getStatuses() != null) {
                final List<AiChatStatus> previous = chatStatuses;
                chatStatuses = new ArrayList<>(response.getStatuses());
                if (statusesKnown) {
                    notifyChanges(previous, chatStatuses);
                }
                statusesKnown = true;
                showRenamedChatTitle();
                pollIfCurrentChatChanged(previous);
                fireBadge();
                if (chatListShowing) {
                    renderChatList();
                }
            }
            statusTimer.schedule(DocumentVisibility.isHidden()
                    ? HIDDEN_STATUS_POLL_MS
                    : STATUS_POLL_MS);
        }, SILENT);
    }

    /**
     * Polls the chat being shown if it has started or stopped working, or needing the user, somewhere other than
     * here, e.g. stopped from another tab while a form waits, which this tab stopped polling for.
     */
    private void pollIfCurrentChatChanged(final List<AiChatStatus> previous) {
        if (currentChat == null || previous == null) {
            return;
        }
        final int chatId = currentChat.getId();
        final AiChatStatus before = previous.stream()
                .filter(status -> status.getChatId() == chatId)
                .findFirst()
                .orElse(null);
        final AiChatStatus now = findStatus(chatId).orElse(null);
        if (before != null && now != null
            && (before.isBusy() != now.isBusy() || before.isNeedsYou() != now.isNeedsYou())) {
            startPolling();
        }
    }

    private void fireBadge() {
        int needsYou = 0;
        int busy = 0;
        int unread = 0;
        for (final AiChatStatus status : chatStatuses) {
            if (status.isNeedsYou()) {
                needsYou++;
            } else if (status.isBusy()) {
                busy++;
            } else if (status.isUnread()
                       && (currentChat == null || currentChat.getId() != status.getChatId() || !isLooking())) {
                unread++;
            }
        }
        AskStroomAiBadgeEvent.fire(this, needsYou, busy, unread);
    }

    /// Shows the current chat's new title, once the server has named it after what the chat is about.
    private void showRenamedChatTitle() {
        if (currentChat == null) {
            return;
        }
        findStatus(currentChat.getId())
                .map(AiChatStatus::getTitle)
                .filter(title -> !title.isBlank()
                                 && !DEFAULT_CHAT_TITLE.equals(title)
                                 && !title.equals(currentChat.getTitle()))
                .ifPresent(this::setChatTitle);
    }

    /**
     * Shows a browser notification for each chat that has just come to need the user, or has just finished,
     * while the user is looking at another browser tab (U4).
     */
    private void notifyChanges(final List<AiChatStatus> previous, final List<AiChatStatus> current) {
        if (!DocumentVisibility.isHidden()) {
            return;
        }
        askStroomAiClient.getConfig(config -> {
            if (!config.isBrowserNotifications()) {
                return;
            }
            final Map<Integer, AiChatStatus> before = new HashMap<>();
            for (final AiChatStatus status : previous) {
                before.put(status.getChatId(), status);
            }
            for (final AiChatStatus status : current) {
                final AiChatStatus was = before.get(status.getChatId());
                final String title = NullSafe.isBlankString(status.getTitle())
                        ? "A chat"
                        : "\u201c" + status.getTitle() + "\u201d";
                if (status.isNeedsYou() && (was == null || !was.isNeedsYou())) {
                    BrowserNotifications.show("Stroom AI needs you", title + " is waiting for your answer.",
                            "stroom-ai-" + status.getChatId());
                } else if (status.isUnread() && !status.isBusy() && !status.isNeedsYou()
                           && was != null && (was.isBusy() || !was.isUnread())) {
                    BrowserNotifications.show("Stroom AI has answered", title + " has finished.",
                            "stroom-ai-" + status.getChatId());
                }
            }
        }, SILENT);
    }

    @Override
    public void onShowSwitcher(final Element target) {
        // Clicking the title again closes the list.
        setChatListShowing(!chatListShowing);
    }

    private void setChatListShowing(final boolean showing) {
        chatListShowing = showing;
        getView().setChatListVisible(showing);
        // Each time the list is shown it starts unsearched.
        searchTimer.cancel();
        searchText = "";
        searchedText = null;
        searchHits = null;
        getView().clearChatSearch();
        if (showing) {
            renderChatList();
            getView().focusChatSearch();
            // Bring it up to date, e.g. with chats made in another browser tab.
            checkChatStatuses();
        }
    }

    /**
     * Shows the user's chats, most recent first, each with when it was last active and a button to delete it.
     */
    private void renderChatList() {
        renderChatListActions();
        final HtmlBuilder hb = new HtmlBuilder();
        if (searchHits != null) {
            if (searchHits.isEmpty()) {
                hb.div(div -> div.append("No chats match “" + searchedText + "”."),
                        Attribute.className("ai-chat-list-empty"));
            }
            for (final AiChatSearchHit hit : searchHits) {
                final AiChatStatus status = findStatus(hit.getChatId()).orElse(null);
                chatListItem(hb, hit.getChatId(), hit.getTitle(), hit.getUpdateTimeMs(), status, hit.getSnippet(),
                        searchedText);
            }
        } else {
            if (chatStatuses.isEmpty()) {
                hb.div(div -> div.append("You have no chats yet."), Attribute.className("ai-chat-list-empty"));
            }
            for (final AiChatStatus status : chatStatuses) {
                chatListItem(hb, status.getChatId(), status.getTitle(), status.getUpdateTimeMs(), status, null, null);
            }
        }
        getView().getChatListItems().setHTML(hb.toSafeHtml());
    }

    /// The buttons above the list of chats.
    private void renderChatListActions() {
        final HtmlBuilder hb = new HtmlBuilder();
        if (!chatStatuses.isEmpty()) {
            if (chatStatuses.stream().anyMatch(AiChatStatus::isUnread)) {
                hb.elem("Mark All as Read", BUTTON,
                        Attribute.className("allow-focus ai-chat-list-action"),
                        Attribute.title("Clear the unread mark from all your chats"),
                        new Attribute(MARK_ALL_READ_ATTRIBUTE, "true"));
            }
            hb.elem("Delete All Chats", BUTTON,
                    Attribute.className("allow-focus ai-chat-list-action"),
                    Attribute.title("Delete all your chats"),
                    new Attribute(DELETE_ALL_CHATS_ATTRIBUTE, "true"));
        }
        getView().getChatListActions().setHTML(hb.toSafeHtml());
    }

    /// One chat in the list: its state, title, when it was last active and, when searching, part of the matching
    /// question, with the matches highlighted.
    ///
    /// @param status  The chat's status, or null if not known (e.g. an older chat found by searching).
    /// @param snippet Part of a matching question, or null.
    /// @param query   The text searched for, to highlight, or null.
    private void chatListItem(final HtmlBuilder hb,
                              final int chatId,
                              final String chatTitle,
                              final long updateTimeMs,
                              final AiChatStatus status,
                              final String snippet,
                              final String query) {
        final boolean current = currentChat != null && currentChat.getId() == chatId;
        final String title = NullSafe.nonBlankStringElse(chatTitle, DEFAULT_CHAT_TITLE);
        hb.div(item -> {
            item.div(iconDiv -> icon(iconDiv, status != null
                            ? AiChatTexts.statusIcon(status)
                            : SvgImage.AI),
                    Attribute.className("ai-chat-list-icon"));
            item.div(text -> {
                text.div(name -> highlight(name, title, query), Attribute.className("ai-chat-list-title"),
                        Attribute.title(title));
                final String note = status != null
                        ? AiChatTexts.statusNote(status)
                        : "";
                text.div(when -> when.append(dateTimeFormatter.formatRelative(updateTimeMs,
                                        System.currentTimeMillis())
                                + (note.isEmpty()
                                ? ""
                                : " · " + note)),
                        Attribute.className("ai-chat-list-when"),
                        Attribute.title("Last active " + dateTimeFormatter.format(updateTimeMs)));
                if (snippet != null) {
                    text.div(part -> highlight(part, snippet, query), Attribute.className("ai-chat-list-snippet"));
                }
            }, Attribute.className("ai-chat-list-text"));
            button(item, SvgImage.DELETE,
                    iconButtonClassName("ai-chat-list-delete"),
                    Attribute.title("Delete chat"),
                    new Attribute(DELETE_CHAT_ATTRIBUTE, String.valueOf(chatId)));
        }, Attribute.className(current
                        ? "ai-chat-list-item ai-chat-list-item--current"
                        : "ai-chat-list-item"),
                new Attribute(CHAT_ID_ATTRIBUTE, String.valueOf(chatId)),
                Attribute.title("Open " + title));
    }

    /// Appends the text, with each match of the query (ignoring case) marked.
    private static void highlight(final HtmlBuilder hb, final String text, final String query) {
        for (final ChatSearchText.Part part : ChatSearchText.split(text, query)) {
            if (part.match()) {
                hb.elem(part.text(), MARK, Attribute.className("ai-chat-list-hit"));
            } else {
                hb.append(part.text());
            }
        }
    }

    @Override
    public void onCommandPicked(final AiSlashCommand command) {
        referenceCommand = command.getName();
        // MCP servers write their prompts' descriptions, so the reference is treated as untrusted.
        getView().showCommandReference(markdownConverter.convertUntrustedMarkdownToHtml(command.getReference()));
    }

    @Override
    public void onCloseCommandReference() {
        referenceCommand = null;
        getView().hideCommandReference();
    }

    /// Hides the command reference once the input no longer starts with its command, e.g. the user deleted it.
    private void checkCommandReference(final String text) {
        if (referenceCommand == null) {
            return;
        }
        final String trimmed = NullSafe.string(text).trim();
        if (!(trimmed.equals(referenceCommand) || trimmed.startsWith(referenceCommand + " "))) {
            onCloseCommandReference();
        }
    }

    @Override
    public void onFollowUp(final String text) {
        if (!currentRunActive && !requestInFlight && text != null && !text.isBlank()) {
            sendMessage(text);
        }
    }

    /// Shows the suggested follow-ups, but only while nothing is working on the chat.
    private void refreshFollowUps() {
        getView().setFollowUps(currentRunActive || requestInFlight
                ? List.of()
                : followUps);
        // The suggestions take room from the conversation, so keep the latest message in view.
        if (followBottom) {
            scrollToLatest();
        }
    }

    /// Shows the agent's task list: a heading with how many are done, and each task with an icon for its status.
    ///
    /// @param json A TASK_LIST message: `{"tasks":[{"title":..,"status":..}]}`.
    private void showTaskList(final String json) {
        final JSONArray tasks;
        try {
            final JSONObject object = JSONParser.parseStrict(json).isObject();
            tasks = object == null || object.get("tasks") == null
                    ? null
                    : object.get("tasks").isArray();
        } catch (final RuntimeException e) {
            return;
        }
        if (tasks == null || tasks.size() == 0) {
            return;
        }
        final HtmlBuilder hb = new HtmlBuilder();
        int done = 0;
        for (int i = 0; i < tasks.size(); i++) {
            final JSONObject task = tasks.get(i).isObject();
            if (task == null) {
                continue;
            }
            final String title = task.get("title") != null && task.get("title").isString() != null
                    ? task.get("title").isString().stringValue()
                    : "";
            final String status = task.get("status") != null && task.get("status").isString() != null
                    ? task.get("status").isString().stringValue()
                    : "not_started";
            if ("completed".equals(status)) {
                done++;
            }
            hb.div(row -> {
                row.span(icon -> icon.append("completed".equals(status)
                                ? "✓"
                                : "in_progress".equals(status)
                                        ? "●"
                                        : "○"),
                        Attribute.className("ai-task-icon ai-task-icon--" + status));
                row.span(text -> text.append(title), Attribute.className("ai-task-title"));
            }, Attribute.className("ai-task ai-task--" + status));
        }
        getView().setTaskList("Tasks " + done + "/" + tasks.size(), hb.toSafeHtml());
        // The task list takes room from the conversation, so keep the latest message in view.
        scrollToBottom();
    }

    /// @param json A SUGGESTIONS message: a JSON array of strings.
    /// @return The suggestions in it, at most three, or none if it can't be read.
    private static List<String> parseFollowUps(final String json) {
        final List<String> suggestions = new ArrayList<>();
        try {
            final JSONArray array = JSONParser.parseStrict(json).isArray();
            for (int i = 0; array != null && i < array.size() && suggestions.size() < MAX_FOLLOW_UPS; i++) {
                final JSONString value = array.get(i).isString();
                if (value != null && !value.stringValue().isBlank()) {
                    suggestions.add(value.stringValue().strip());
                }
            }
        } catch (final RuntimeException e) {
            // Nothing to offer.
        }
        return suggestions;
    }

    @Override
    public void onChatSearch(final String text) {
        final String trimmed = NullSafe.string(text).strip();
        if (trimmed.equals(searchText)) {
            return;
        }
        searchText = trimmed;
        searchTimer.cancel();
        if (trimmed.length() < MIN_SEARCH_CHARS) {
            // Back to the whole list.
            searchHits = null;
            searchedText = null;
            renderChatList();
        } else {
            // Wait for a pause in the typing, so the server is asked once rather than per key.
            searchTimer.schedule(SEARCH_DELAY_MS);
        }
    }

    /// Searches for what is in the box now, and shows the chats found, unless the user has typed on since.
    private void runChatSearch() {
        final String text = searchText;
        if (text.length() < MIN_SEARCH_CHARS) {
            return;
        }
        final int search = ++searchSequence;
        askStroomAiClient.searchChats(text, hits -> {
            if (search == searchSequence && text.equals(searchText) && chatListShowing) {
                searchHits = NullSafe.list(hits);
                searchedText = text;
                renderChatList();
            }
        }, SILENT);
    }

    private void onChatListClick(final Element target) {
        if (ElementUtil.findParent(target, element -> element.hasAttribute(DELETE_ALL_CHATS_ATTRIBUTE), 3) != null) {
            onDeleteAllChats();
            return;
        }
        if (ElementUtil.findParent(target, element -> element.hasAttribute(MARK_ALL_READ_ATTRIBUTE), 3) != null) {
            askStroomAiClient.markAllChatsRead(count -> {
                // Show the change now, rather than at the next check.
                statusTimer.cancel();
                checkChatStatuses();
            }, this);
            return;
        }
        final Element delete = ElementUtil.findParent(target, element ->
                element.hasAttribute(DELETE_CHAT_ATTRIBUTE), 4);
        if (delete != null) {
            final int chatId = Integer.parseInt(delete.getAttribute(DELETE_CHAT_ATTRIBUTE));
            findStatus(chatId).ifPresent(status -> deleteChat(new AiChat(chatId, 0L, status.getUpdateTimeMs(),
                    null, status.getTitle())));
            return;
        }
        final Element item = ElementUtil.findParent(target, element ->
                element.hasAttribute(CHAT_ID_ATTRIBUTE), 5);
        if (item != null) {
            final int chatId = Integer.parseInt(item.getAttribute(CHAT_ID_ATTRIBUTE));
            findStatus(chatId).ifPresent(status -> {
                setChatListShowing(false);
                loadChat(new AiChat(chatId, 0L, status.getUpdateTimeMs(), null, status.getTitle()));
            });
        }
    }

    private void onDeleteAllChats() {
        final int count = chatStatuses.size();
        final boolean anyWorking = chatStatuses.stream().anyMatch(AiChatStatus::isBusy);
        ConfirmEvent.fire(this, AiChatTexts.deleteAllChatsQuestion(count, anyWorking), ok -> {
            if (ok) {
                askStroomAiClient.deleteAllChats(deleted -> {
                    if (deleted < count) {
                        AlertEvent.fireWarn(this, (count - deleted) + " chat(s) could not be deleted, as they "
                                                  + "did not stop in time. Try again shortly.", null);
                    }
                    setChatListShowing(false);
                    startNewChat();
                    statusTimer.cancel();
                    checkChatStatuses();
                }, this);
            }
        });
    }

    private Optional<AiChatStatus> findStatus(final int chatId) {
        return chatStatuses.stream()
                .filter(status -> status.getChatId() == chatId)
                .findFirst();
    }

    private void onRename() {
        if (currentChat == null) {
            return;
        }
        PromptEvent.fire(this, "Rename Chat", "Name for this chat:", currentChat.getTitle(), title -> {
            if (title != null && !title.isBlank() && currentChat != null) {
                setChatTitle(title.strip());
                titleGenerated = true;
                askStroomAiClient.updateChatTitle(currentChat.getId(), title.strip(), success -> {
                    // Nothing more to do.
                }, this);
            }
        });
    }

    /**
     * Compacts the chat now: earlier turns are replaced, in what the model sees, by digests and a summary.
     */
    @Override
    public void onInputChanged(final String text) {
        checkCommandReference(text);
        final String trimmed = NullSafe.string(text).trim();
        if (trimmed.startsWith("/") && !trimmed.contains(" ")) {
            if (mcpPrompts == null) {
                // Suggest the MCP prompts too, once they have been fetched.
                fetchPrompts(() -> onInputChanged(text));
            }
            final List<AiSlashCommand> matches = new ArrayList<>();
            for (final AiSlashCommand command : slashCommands()) {
                if (command.getName().startsWith(trimmed)) {
                    matches.add(command);
                }
            }
            getView().setCommandSuggestions(matches);
        } else {
            getView().setCommandSuggestions(null);
        }
    }

    /**
     * @return The chat's own commands, then the MCP servers' prompts.
     */
    private List<AiSlashCommand> slashCommands() {
        final List<AiSlashCommand> commands = new ArrayList<>(AiSlashCommands.COMMANDS);
        for (final AiMcpPrompt prompt : NullSafe.list(mcpPrompts)) {
            final StringBuilder arguments = new StringBuilder();
            for (final AiMcpPromptArgument argument : NullSafe.list(prompt.getArguments())) {
                if (arguments.length() > 0) {
                    arguments.append(" ");
                }
                arguments.append(argument.isRequired()
                        ? argument.getName() + "=…"
                        : "[" + argument.getName() + "=…]");
            }
            final StringBuilder reference = new StringBuilder("A prompt from the MCP server ")
                    .append(prompt.getServer())
                    .append(". The filled-in prompt is put in the input box for you to check and send.");
            if (!NullSafe.list(prompt.getArguments()).isEmpty()) {
                reference.append("\n");
                for (final AiMcpPromptArgument argument : prompt.getArguments()) {
                    reference.append("\n- *").append(argument.getName()).append("*")
                            .append(argument.isRequired()
                                    ? " (required)"
                                    : "")
                            .append(NullSafe.isBlankString(argument.getDescription())
                                    ? ""
                                    : ": " + argument.getDescription());
                }
            }
            commands.add(new AiSlashCommand(prompt.getCommand(), arguments.toString(),
                    NullSafe.string(prompt.getDescription()) + " (from " + prompt.getServer() + ")",
                    reference.toString()));
        }
        return commands;
    }

    /**
     * Fetches the prompts the model's MCP servers offer, once.
     */
    private void fetchPrompts(final Runnable then) {
        if (fetchingPrompts) {
            return;
        }
        fetchingPrompts = true;
        askStroomAiClient.getConfig(config -> askStroomAiClient.listMcpPrompts(config, prompts -> {
            mcpPrompts = NullSafe.list(prompts);
            fetchingPrompts = false;
            then.run();
        }, error -> {
            // No prompts if they can't be listed, e.g. there is no model yet.
            mcpPrompts = List.of();
            fetchingPrompts = false;
        }, SILENT), SILENT);
    }

    /**
     * Fills in an MCP server's prompt and puts it in the input box, for the user to check and send. Server text is
     * never sent to the model without the user sending it.
     *
     * @param prompt The prompt.
     * @param rest   What the user typed after the command: 'name=value' pairs, or, for a prompt with
     *               arguments, the first argument's value.
     */
    private void runPrompt(final AiMcpPrompt prompt, final String rest) {
        final Map<String, String> arguments = AiSlashCommand.parseArguments(
                NullSafe.isEmptyCollection(prompt.getArguments())
                        ? null
                        : prompt.getArguments().get(0).getName(),
                rest);
        for (final AiMcpPromptArgument argument : NullSafe.list(prompt.getArguments())) {
            if (argument.isRequired() && NullSafe.isBlankString(arguments.get(argument.getName()))) {
                AlertEvent.fireWarn(this, prompt.getCommand() + " needs " + argument.getName() + ", e.g. "
                                          + prompt.getCommand() + " " + argument.getName() + "=…", null);
                return;
            }
        }
        askStroomAiClient.getConfig(config -> askStroomAiClient.getMcpPrompt(
                new GetAiMcpPromptRequest(config, prompt.getServer(), prompt.getName(), arguments),
                text -> getView().setMessage(NullSafe.string(text.getText())),
                error -> showError(error, "Unable to get " + prompt.getCommand(), null),
                getView()), getView());
    }

    /**
     * Runs the message if it is a command, e.g. "/compact keep the IP addresses".
     *
     * @return True if it was a command, so is not to be sent to the model.
     */
    private boolean runSlashCommand(final String message) {
        final String trimmed = NullSafe.string(message).trim();
        if (!trimmed.startsWith("/") || trimmed.startsWith("//")) {
            return false;
        }
        final String[] parts = trimmed.split("\\s+", 2);
        final AiMcpPrompt prompt = NullSafe.list(mcpPrompts).stream()
                .filter(candidate -> candidate.getCommand().equals(parts[0]))
                .findFirst()
                .orElse(null);
        if (prompt != null) {
            runPrompt(prompt, parts.length > 1
                    ? parts[1]
                    : "");
            return true;
        }
        final String rest = parts.length > 1
                ? parts[1].strip()
                : "";
        if (AiSlashCommands.isOwn(parts[0])) {
            runOwnCommand(parts[0], rest);
            return true;
        }
        if (mcpPrompts == null) {
            // It may be an MCP prompt, so fetch them and try again.
            fetchPrompts(() -> runSlashCommand(message));
            return true;
        }
        AlertEvent.fireWarn(this, "Unknown command " + parts[0] + ".\n\n" + AiSlashCommands.help(slashCommands()),
                null);
        return true;
    }

    /// Runs one of the chat's own commands.
    ///
    /// @param command The command, with its \`/\`.
    /// @param rest    What the user typed after it, trimmed.
    private void runOwnCommand(final String command, final String rest) {
        switch (command) {
            case AiSlashCommands.COMPACT -> {
                if (currentChat == null) {
                    AlertEvent.fireWarn(this, "There is nothing to compact yet.", null);
                } else {
                    compact(rest.isEmpty()
                            ? null
                            : rest);
                }
            }
            case AiSlashCommands.MODE -> {
                final AiAgentMode mode = AiSlashCommands.parseMode(rest);
                if (mode == null) {
                    AlertEvent.fireInfo(this, "This chat is in " + getView().getAgentMode().getDisplayValue()
                                              + " mode. Use /mode agent, /mode ask or /mode plan to change it.",
                            null);
                } else {
                    switchMode(mode);
                }
            }
            case AiSlashCommands.PLAN -> {
                if (rest.isEmpty()) {
                    AlertEvent.fireWarn(this, "Say what to plan, e.g. /plan onboard feed ACME.", null);
                } else {
                    switchMode(AiAgentMode.PLAN);
                    sendMessage(rest);
                }
            }
            case AiSlashCommands.NEW -> {
                setChatListShowing(false);
                final Runnable ask = () -> {
                    if (!rest.isEmpty()) {
                        sendMessage(rest);
                    }
                };
                // An unused chat is new already, so it is used rather than left behind.
                if (isCurrentChatUnused()) {
                    getView().focus();
                    ask.run();
                } else {
                    startNewChat(ask);
                }
            }
            case AiSlashCommands.CLEAR -> {
                if (currentChat == null) {
                    AlertEvent.fireWarn(this, "There is nothing to clear yet.", null);
                } else {
                    onClearChat(currentChat);
                }
            }
            case AiSlashCommands.REMEMBER -> {
                if (rest.isEmpty()) {
                    AlertEvent.fireWarn(this, "Say what to remember, e.g. /remember our firewall feeds start "
                                              + "with FW-.", null);
                } else {
                    ensureChat(() -> remember(rest));
                }
            }
            default -> AlertEvent.fireInfo(this, AiSlashCommands.help(slashCommands()), null);
        }
    }

    /// Clears the chat, after asking, and shows it empty, to be named afresh by its next question.
    private void onClearChat(final AiChat chat) {
        ConfirmEvent.fire(this, AiChatTexts.clearChatQuestion(isBusy(chat.getId())), ok -> {
            if (ok) {
                askStroomAiClient.clearChat(chat.getId(), done -> {
                    if (currentChat != null && currentChat.getId() == chat.getId()) {
                        currentRunActive = false;
                        refreshFollowUps();
                        titleGenerated = false;
                        lastSeenMessageId = 0;
                        data = null;
                        clearRenderedMessages();
                        getView().setEmptyState(true);
                        getView().clearContextIndicator();
                        getView().setSendButtonLoadingState(false);
                        setChatTitle(DEFAULT_CHAT_TITLE);
                    }
                    checkChatStatuses();
                }, this);
            }
        });
    }

    /// Switches the chat to a mode, as choosing it in the mode list does.
    private void switchMode(final AiAgentMode mode) {
        getView().setAgentMode(mode);
        onAgentModeChange(mode);
    }

    /// Adds a fact to the user's own memory note for remembered facts, which is loaded into all their chats.
    private void remember(final String fact) {
        final AiChat chat = currentChat;
        askStroomAiClient.listMemory(chat.getId(), response -> {
            final AiMemory note = NullSafe.list(response.getMemories()).stream()
                    .filter(memory -> memory.getScope() == AiMemoryScope.USER
                                      && AiSlashCommands.REMEMBER_NOTE.equals(memory.getName()))
                    .findFirst()
                    .orElse(null);
            askStroomAiClient.saveMemory(new SaveAiMemoryRequest(chat, "/memories/" + AiSlashCommands.REMEMBER_NOTE,
                            AiSlashCommands.addFact(NullSafe.get(note, AiMemory::getContent), fact),
                            NullSafe.get(note, AiMemory::getVersion)),
                    saved -> AlertEvent.fireInfo(this, "Remembered. It's in your memory ("
                                                       + AiSlashCommands.REMEMBER_NOTE
                                                       + "), which is loaded into all your chats.", null),
                    error -> showError(error, "Unable to remember that", null),
                    this);
        }, error -> showError(error, "Unable to read your memory", null), this);
    }

    /**
     * Shows how much of the model's context window the chat uses, e.g. "42% · 54k / 128k", in amber near the
     * point where the chat is compacted and red past it. "≈" says the figure is only an estimate.
     */
    private void updateContextMeter(final AiContextUsage usage) {
        contextUsage = usage;
        final String approx = usage.isEstimated()
                ? "≈"
                : "";
        final int percent = usage.getPercentUsed();
        final int compactAt = usage.getCompactAtPercent() > 0
                ? usage.getCompactAtPercent()
                : DEFAULT_COMPACT_AT_PERCENT;
        if (percent < 0) {
            // The model's context window isn't known, so there is no percentage.
            getView().setContextMeter(approx + kilo(usage.getUsedTokens()), contextDetails(usage, compactAt),
                    null);
            return;
        }
        getView().setContextMeter(approx + percent + "%", contextDetails(usage, compactAt),
                AiChatTexts.contextLevel(percent));
    }

    /// @return What the pill shows on hover: how much of the context window is used, by what, and when the chat
    /// is compacted.
    private static SafeHtml contextDetails(final AiContextUsage usage, final int compactAt) {
        final HtmlBuilder hb = new HtmlBuilder();
        hb.div(title -> title.append("Context window"), Attribute.className("ai-context-details-title"));
        final String approx = usage.isEstimated()
                ? "about "
                : "";
        hb.div(used -> used.append(usage.getPercentUsed() >= 0
                        ? "Using " + approx + kilo(usage.getUsedTokens()) + " of "
                          + kilo(usage.getContextWindowTokens()) + " tokens (" + usage.getPercentUsed() + "%)"
                        : "Using " + approx + kilo(usage.getUsedTokens()) + " tokens; the model's context window "
                          + "isn't known yet"),
                Attribute.className("ai-context-details-row"));
        final List<Map.Entry<String, Long>> parts = usage.getParts().entrySet().stream()
                .filter(part -> part.getValue() > 0)
                .toList();
        if (!parts.isEmpty()) {
            hb.div(table -> {
                for (final Map.Entry<String, Long> part : parts) {
                    table.div(row -> {
                        row.span(name -> name.append(part.getKey()));
                        row.span(value -> value.append(kilo(part.getValue())),
                                Attribute.className("ai-context-details-value"));
                    }, Attribute.className("ai-context-details-part"));
                }
            }, Attribute.className("ai-context-details-parts"));
        }
        hb.div(note -> note.append("Earlier messages are compacted automatically at " + compactAt + "%."),
                Attribute.className("ai-context-details-note"));
        hb.div(note -> note.append("Click to compact now."), Attribute.className("ai-context-details-note"));
        return hb.toSafeHtml();
    }

    private static String kilo(final long tokens) {
        return tokens >= 1000
                ? (tokens / 1000) + "k"
                : String.valueOf(tokens);
    }

    @Override
    public void onShowContextMeter(final Element target) {
        if (contextUsage == null) {
            return;
        }
        final MenuBuilder builder = MenuBuilder.builder();
        for (final Map.Entry<String, Long> part : contextUsage.getParts().entrySet()) {
            if (part.getValue() > 0) {
                builder.withSimpleMenuItem(item -> item
                        .text(part.getKey() + ": " + kilo(part.getValue()) + " tokens")
                        .enabled(false));
            }
        }
        final List<Item> items = builder
                .withSeparator()
                .withIconMenuItem(item -> item
                        .icon(SvgImage.COLLAPSE_UP)
                        .text("Compact Now…")
                        .enabled(currentChat != null)
                        .command(this::onCompact))
                .build();
        ShowMenuEvent.builder()
                .items(items)
                .popupPosition(new PopupPosition(new Rect(target).grow(3), PopupLocation.ABOVE))
                .fire(this);
    }

    /**
     * Renders a form an MCP tool asked the user to fill in. While it waits: what the server said, as plain text,
     * and its fields with Submit and Decline. Once answered, just the question (its first line, the whole of it
     * on hover) and the answer, or what else became of it.
     */
    private void appendElicitationCard(final HtmlBuilder hb,
                                       final AiChatMessage msg,
                                       final long timeMs,
                                       final long nowMs) {
        final String json = msg.getMessage();
        final String status = NullSafe.string(AiToolMessages.getString(json, "status"));
        final String server = AiToolMessages.getString(json, "server");
        final boolean pending = "PENDING".equals(status);
        final String message = NullSafe.string(AiToolMessages.getString(json, "message"));
        // Questions from Stroom AI's own agent, asked one at a time, rather than a form from an MCP server.
        final boolean questions = ASK_USER_TOOL.equals(AiToolMessages.getString(json, "tool"));
        final List<AiElicitationForm.Field> fields = pending
                ? AiElicitationForm.fields(json)
                : List.of();
        // Yes and No buttons are only for a server's form: the agent's questions have answers to choose from.
        final AiElicitationForm.Field yesNo = questions
                ? null
                : AiElicitationCard.yesNoQuestion(fields);
        hb.div(card -> {
            if (pending && questions) {
                card.div(AiElicitationCard.firstLine(message, "Some questions for you"),
                        Attribute.className("ai-approval-title"));
                AiElicitationCard.appendQuestions(card, fields);
            } else if (pending) {
                card.div(server + " needs your input", Attribute.className("ai-approval-title"));
                card.div(message, Attribute.className("ai-elicitation-message"));
                if (yesNo != null) {
                    AiElicitationCard.appendYesNoQuestion(card, yesNo, message);
                } else {
                    AiElicitationCard.appendFields(card, fields);
                }
            } else {
                card.div(AiElicitationCard.firstLine(message, "A form from " + server),
                        Attribute.className("ai-approval-title"),
                        Attribute.title(message));
            }
            final boolean answerShown = "ACCEPTED".equals(status)
                                        && AiElicitationCard.appendAnswer(card, json, pending
                                                ? null
                                                : AiElicitationCard.firstLine(message, "A form from " + server));
            card.div(footer -> {
                if (pending && questions) {
                    footer.elem("Skip these questions", BUTTON,
                            Attribute.className("allow-focus ai-approval-button ai-approval-button--deny"),
                            new Attribute(ELICITATION_ATTRIBUTE, String.valueOf(msg.getId())),
                            new Attribute(ELICITATION_ACTION_ATTRIBUTE, "DECLINE"));
                } else if (pending && yesNo != null) {
                    // A single yes or no question is answered with a button for each.
                    footer.elem("Yes", BUTTON,
                            Attribute.className("allow-focus ai-approval-button ai-approval-button--approve"),
                            new Attribute(ELICITATION_ATTRIBUTE, String.valueOf(msg.getId())),
                            new Attribute(ELICITATION_ACTION_ATTRIBUTE, "ACCEPT"),
                            new Attribute(ELICITATION_FIELD_ATTRIBUTE, yesNo.getName()),
                            new Attribute(ELICITATION_VALUE_ATTRIBUTE, "true"));
                    footer.elem("No", BUTTON,
                            Attribute.className("allow-focus ai-approval-button ai-approval-button--deny"),
                            new Attribute(ELICITATION_ATTRIBUTE, String.valueOf(msg.getId())),
                            new Attribute(ELICITATION_ACTION_ATTRIBUTE, "ACCEPT"),
                            new Attribute(ELICITATION_FIELD_ATTRIBUTE, yesNo.getName()),
                            new Attribute(ELICITATION_VALUE_ATTRIBUTE, "false"));
                } else if (pending) {
                    footer.elem("Submit", BUTTON,
                            Attribute.className("allow-focus ai-approval-button ai-approval-button--approve"),
                            new Attribute(ELICITATION_ATTRIBUTE, String.valueOf(msg.getId())),
                            new Attribute(ELICITATION_ACTION_ATTRIBUTE, "ACCEPT"));
                    footer.elem("Decline", BUTTON,
                            Attribute.className("allow-focus ai-approval-button ai-approval-button--deny"),
                            new Attribute(ELICITATION_ATTRIBUTE, String.valueOf(msg.getId())),
                            new Attribute(ELICITATION_ACTION_ATTRIBUTE, "DECLINE"));
                } else if (!"ACCEPTED".equals(status) || !answerShown) {
                    footer.span(text -> text.append(AiChatTexts.formStatus(status)),
                            Attribute.className("ai-approval-reason"));
                }
                timestamp(footer, timeMs, nowMs);
            }, Attribute.className("ai-message-footer"));
        }, Attribute.className("ai-message ai-message--approval" + (pending
                ? " ai-message--needs-you"
                : "")));
    }

    /**
     * Sends the user's answer to a form shown in the chat. If it is refused (e.g. a required field is empty), the
     * form stays as it is, to be put right.
     */
    private void onAnswerForm(final Element button, final int messageId, final String action) {
        final AiChatMessage msg = renderedMessages.get(messageId);
        final Element card = ElementUtil.findParent(button, element -> element.hasClassName("ai-message"), 6);
        if (msg == null || currentChat == null || card == null) {
            return;
        }
        final boolean accept = "ACCEPT".equals(action);
        final Map<String, String> values;
        if (!accept) {
            values = null;
        } else if (button.hasAttribute(ELICITATION_VALUE_ATTRIBUTE)) {
            // The Yes or No of a yes or no question.
            values = Map.of(button.getAttribute(ELICITATION_FIELD_ATTRIBUTE),
                    button.getAttribute(ELICITATION_VALUE_ATTRIBUTE));
        } else {
            values = AiElicitationCard.values(card);
        }
        setFormButtonsEnabled(card, false);
        askStroomAiClient.answerElicitation(new AnswerAiElicitationRequest(currentChat, messageId, action, values),
                done -> {
                    final String status = accept
                            ? "ACCEPTED"
                            : "DECLINED";
                    appendMessage(new AiChatMessage(msg.getId(), msg.getChatId(), msg.getCreateTimeMs(),
                            msg.getMessageType(), msg.getAttachmentId(),
                            AiElicitationCard.answered(msg.getMessage(), status, values), msg.getTurnId(),
                            msg.getTokenCount()), System.currentTimeMillis());
                    // The tool carries on with the answer.
                    pollAfterAnswer();
                },
                error -> {
                    setFormButtonsEnabled(card, true);
                    AlertEvent.fireError(this, error.getMessage(), null);
                    // E.g. the form was cancelled when the run stopped: show it as it now stands.
                    pollAfterAnswer();
                }, getView());
    }

    /**
     * Takes the answer to one of the agent's questions, then shows the next question, or after the last, sends
     * the answers.
     *
     * @param within An element of the question, e.g. the answer clicked.
     * @param answer The answer.
     */
    private void onQuestionAnswered(final Element within, final String answer) {
        final Element question = ElementUtil.findParent(within, element ->
                element.hasClassName(AiElicitationCard.QUESTION_CLASS), 4);
        final Element card = question != null
                ? ElementUtil.findParent(question, element -> element.hasAttribute(MESSAGE_ID_ATTRIBUTE), 4)
                : null;
        if (card == null) {
            return;
        }
        final NodeList<Element> inputs = question.getElementsByTagName("input");
        for (int i = 0; i < inputs.getLength(); i++) {
            if (inputs.getItem(i).hasAttribute(AiElicitationCard.FIELD_ATTRIBUTE)) {
                InputElement.as(inputs.getItem(i)).setValue(answer);
            }
        }
        // Shown as chosen, for if the user comes back to it.
        final NodeList<Element> options = question.getElementsByTagName("button");
        for (int i = 0; i < options.getLength(); i++) {
            final Element option = options.getItem(i);
            option.setClassName(option.getClassName().replace(" ai-question-option--chosen", "")
                                + (answer.equals(option.getAttribute(AiElicitationCard.OPTION_ATTRIBUTE))
                    ? " ai-question-option--chosen"
                    : ""));
        }
        final Element next = question.getNextSiblingElement();
        if (next != null && next.hasClassName(AiElicitationCard.QUESTION_CLASS)) {
            question.addClassName(AiElicitationCard.LATER_CLASS);
            next.removeClassName(AiElicitationCard.LATER_CLASS);
        } else {
            onAnswerForm(question, Integer.parseInt(card.getAttribute(MESSAGE_ID_ATTRIBUTE)), "ACCEPT");
        }
    }

    private void onQuestionBack(final Element within) {
        final Element question = ElementUtil.findParent(within, element ->
                element.hasClassName(AiElicitationCard.QUESTION_CLASS), 4);
        final Element previous = question != null
                ? question.getPreviousSiblingElement()
                : null;
        if (previous != null && previous.hasClassName(AiElicitationCard.QUESTION_CLASS)) {
            question.addClassName(AiElicitationCard.LATER_CLASS);
            previous.removeClassName(AiElicitationCard.LATER_CLASS);
        }
    }

    private static Element findFormSubmit(final Element card) {
        final NodeList<Element> buttons = card.getElementsByTagName("button");
        for (int i = 0; i < buttons.getLength(); i++) {
            if ("ACCEPT".equals(buttons.getItem(i).getAttribute(ELICITATION_ACTION_ATTRIBUTE))) {
                return buttons.getItem(i);
            }
        }
        return null;
    }

    private static void setFormButtonsEnabled(final Element card, final boolean enabled) {
        final NodeList<Element> buttons = card.getElementsByTagName("button");
        for (int i = 0; i < buttons.getLength(); i++) {
            if (buttons.getItem(i).hasAttribute(ELICITATION_ATTRIBUTE)) {
                buttons.getItem(i).setPropertyBoolean("disabled", !enabled);
            }
        }
    }

    /**
     * @return The forms shown as waiting for the user, which the server may cancel (e.g. when the run stops).
     */
    private List<Integer> pendingFormIds() {
        final List<Integer> ids = new ArrayList<>();
        renderedMessages.values().forEach(msg -> {
            if (msg.getMessageType() == AiMessageType.ELICITATION
                && "PENDING".equals(AiToolMessages.getString(msg.getMessage(), "status"))) {
                ids.add(msg.getId());
            }
        });
        return ids;
    }

    /**
     * Shows the forms the server has changed since they were shown, e.g. cancelled as the run stopped.
     */
    private void updateWatchedMessages(final List<AiChatMessage> messages) {
        if (messages == null) {
            return;
        }
        final long nowMs = System.currentTimeMillis();
        for (final AiChatMessage msg : messages) {
            final AiChatMessage shown = renderedMessages.get(msg.getId());
            if (shown != null && !Objects.equals(shown.getMessage(), msg.getMessage())) {
                appendMessage(msg, nowMs);
            }
        }
    }

    /**
     * Shows the steps a tool's result says are the user's, as buttons in a card of their own (so they are not
     * hidden in the turn's activity). Everything in it was written by Stroom, which checked each step; the server's
     * own text only appears in "Do first" conditions, as plain text.
     */
    private void appendUserActionsCard(final Element markdownContainer, final AiChatMessage msg, final long nowMs) {
        final JSONArray actions = AiToolMessages.getArray(msg.getMessage(), "userActions");
        if (actions == null || actions.size() == 0) {
            return;
        }
        boolean waiting = false;
        final HtmlBuilder hb = new HtmlBuilder();
        final List<Consumer<HtmlBuilder>> rows = new ArrayList<>();
        for (int i = 0; i < actions.size(); i++) {
            final JSONObject action = actions.get(i).isObject();
            if (action != null) {
                final String json = action.toString();
                final boolean pending = UserActionsUi.isPending(AiToolMessages.getString(json, "status"),
                        AiToolMessages.parseLong(AiToolMessages.getString(json, "expiresMs")), nowMs);
                waiting |= pending && !UserActionsUi.ELASTICSEARCH_REQUEST.equals(
                        AiToolMessages.getString(json, "action"));
                rows.add(row -> userActionRow(row, msg.getId(), json, pending, nowMs));
            }
        }
        final boolean needsYou = waiting;
        hb.div(card -> {
            card.div(title -> title.append(needsYou
                    ? "Steps for you to take"
                    : "Steps for you"), Attribute.className("ai-approval-title"));
            rows.forEach(row -> row.accept(card));
            card.div(footer -> timestamp(footer, msg.getCreateTimeMs(), nowMs),
                    Attribute.className("ai-message-footer"));
        }, Attribute.className("ai-message ai-message--approval ai-message--user-actions" + (needsYou
                ? " ai-message--needs-you"
                : "")));

        final Element card = parseHtml(hb).getFirstChildElement();
        final String id = msg.getId() + USER_ACTIONS_ID_SUFFIX;
        card.setAttribute(MESSAGE_ID_ATTRIBUTE, id);
        decorateCodeBlocks(card);
        final Element existing = findMessageElement(markdownContainer, id);
        if (existing != null) {
            markdownContainer.replaceChild(card, existing);
        } else {
            markdownContainer.appendChild(card);
            keepProgressInPlace(markdownContainer);
        }
    }

    private void userActionRow(final HtmlBuilder card,
                               final int messageId,
                               final String json,
                               final boolean pending,
                               final long nowMs) {
        final String action = NullSafe.string(AiToolMessages.getString(json, "action"));
        final String status = NullSafe.string(AiToolMessages.getString(json, "status"));
        card.div(row -> {
            row.div(text -> text.append(NullSafe.string(AiToolMessages.getString(json, "label"))),
                    Attribute.className("ai-user-action-label"));
            final String details = AiToolMessages.getString(json, "details");
            if (NullSafe.isNonBlankString(details)) {
                row.div(text -> text.append(details), Attribute.className("ai-user-action-details"));
            }
            final String warning = AiToolMessages.getString(json, "warning");
            if (NullSafe.isNonBlankString(warning)) {
                row.div(text -> text.append(warning), Attribute.className("ai-user-action-warning"));
            }
            final JSONArray after = AiToolMessages.getArray(json, "after");
            if (after != null && after.size() > 0) {
                final List<String> items = new ArrayList<>();
                for (int i = 0; i < after.size(); i++) {
                    final JSONString item = after.get(i).isString();
                    if (item != null) {
                        items.add(item.stringValue());
                    }
                }
                row.div(text -> text.append("Do first: " + String.join("; ", items)),
                        Attribute.className("ai-user-action-after"));
            }
            final String request = AiToolMessages.getString(json, "request");
            if (UserActionsUi.ELASTICSEARCH_REQUEST.equals(action)) {
                // Shown for the cluster admin to copy. Stroom never runs it.
                if (NullSafe.isNonBlankString(request)) {
                    row.elem(pre -> pre.code(code -> code.append(request)), PRE);
                }
            } else if (pending) {
                row.elem(UserActionsUi.buttonText(action), BUTTON,
                        Attribute.className("allow-focus ai-approval-button ai-approval-button--approve"),
                        new Attribute(USER_ACTION_MESSAGE_ATTRIBUTE, String.valueOf(messageId)),
                        new Attribute(USER_ACTION_ID_ATTRIBUTE,
                                NullSafe.string(AiToolMessages.getString(json, "id"))));
            } else {
                row.div(text -> text.append(UserActionsUi.outcomeText(status,
                                AiToolMessages.getString(json, "outcome"))),
                        Attribute.className("ai-approval-reason"));
            }
        }, Attribute.className("ai-user-action"));
    }

    private void onUserActionButton(final Element button, final int messageId, final String actionId) {
        if (currentChat == null) {
            return;
        }
        button.setAttribute("disabled", "disabled");
        askStroomAiClient.doUserAction(currentChat.getId(), messageId, actionId, result -> {
            if (result.getOpenDocument() != null) {
                OpenDocumentEvent.fire(this, result.getOpenDocument(), true);
                button.removeAttribute("disabled");
                return;
            }
            if (!result.isDone()) {
                AlertEvent.fireWarn(this, NullSafe.string(result.getMessage()), null);
            }
            // The server changed the result in place, which polling doesn't fetch, so show the change here.
            showUserActionStatus(messageId, actionId, result.isDone()
                    ? "DONE"
                    : "FAILED", result.getMessage());
            // Fetch the note of what was done.
            pollAfterAnswer();
        }, this);
    }

    private void showUserActionStatus(final int messageId,
                                      final String actionId,
                                      final String status,
                                      final String outcome) {
        final AiChatMessage msg = renderedMessages.get(messageId);
        if (msg == null) {
            return;
        }
        final JSONObject result = JSONParser.parseStrict(msg.getMessage()).isObject();
        final JSONArray actions = result != null && result.get("userActions") != null
                ? result.get("userActions").isArray()
                : null;
        if (actions == null) {
            return;
        }
        for (int i = 0; i < actions.size(); i++) {
            final JSONObject action = actions.get(i).isObject();
            final JSONString id = action != null && action.get("id") != null
                    ? action.get("id").isString()
                    : null;
            if (id != null && id.stringValue().equals(actionId)) {
                action.put("status", new JSONString(status));
                action.put("outcome", new JSONString(NullSafe.string(outcome)));
            }
        }
        final AiChatMessage updated = new AiChatMessage(msg.getId(), msg.getChatId(), msg.getCreateTimeMs(),
                msg.getMessageType(), msg.getAttachmentId(), result.toString(), msg.getTurnId(),
                msg.getTokenCount());
        renderedMessages.put(messageId, updated);
        appendUserActionsCard(getView().getMarkdownContainer().getElement(), updated, System.currentTimeMillis());
    }

    /**
     * Renders a compaction as a divider in the conversation, which opens to show what the model now sees in
     * place of the earlier messages, with a button to undo it.
     */
    private void appendCompactionDivider(final HtmlBuilder hb,
                                         final AiChatMessage msg,
                                         final long timeMs,
                                         final long nowMs) {
        final String json = msg.getMessage();
        hb.elem(details -> {
            details.elem(summary -> summary.span(title -> title.append(AiToolMessages.compactionTitle(json)),
                            Attribute.className("ai-entry-title")),
                    SUMMARY, Attribute.className("ai-message-header"));
            details.div(content -> content.append(markdownConverter.convertUntrustedMarkdownToHtml(
                    AiToolMessages.compactionMarkdown(json))), Attribute.className("ai-details-content"));
            details.div(footer -> {
                if (!AiToolMessages.getBoolean(json, "stale")) {
                    footer.elem("Undo", BUTTON,
                            Attribute.className("allow-focus ai-approval-button"),
                            Attribute.title("Undo this compaction.\n"
                                            + "The model will see the earlier messages in full again."),
                            new Attribute(UNDO_COMPACTION_ATTRIBUTE, String.valueOf(msg.getId())));
                }
                timestamp(footer, timeMs, nowMs);
            }, Attribute.className("ai-message-footer"));
        }, DETAILS, Attribute.className("ai-message ai-message--compaction ai-compaction-divider"));
    }

    /**
     * Only the latest compaction can be undone, so only it shows its Undo button.
     */
    private void showUndoOnLatestCompaction() {
        final NodeList<Element> buttons = getView().getMarkdownContainer().getElement()
                .getElementsByTagName("button");
        Element latest = null;
        int latestId = -1;
        for (int i = 0; i < buttons.getLength(); i++) {
            final Element button = buttons.getItem(i);
            final String idStr = button.getAttribute(UNDO_COMPACTION_ATTRIBUTE);
            if (NullSafe.isNonBlankString(idStr)) {
                button.getStyle().setDisplay(Display.NONE);
                if (Integer.parseInt(idStr) > latestId) {
                    latestId = Integer.parseInt(idStr);
                    latest = button;
                }
            }
        }
        if (latest != null) {
            latest.getStyle().clearDisplay();
        }
    }

    private void onUndoCompaction(final int messageId) {
        if (currentChat == null) {
            return;
        }
        final String question = "Undo this compaction? The model will see the earlier messages in full again, "
                                + "which uses more of its context window.";
        ConfirmEvent.fire(this, question, ok -> {
            if (ok && currentChat != null) {
                undoCompaction(messageId);
            }
        });
    }

    private void undoCompaction(final int messageId) {
        askStroomAiClient.undoCompaction(new UndoAiCompactionRequest(currentChat, messageId), usage -> {
            final Element element = findMessageElement(getView().getMarkdownContainer().getElement(),
                    String.valueOf(messageId));
            if (element != null) {
                element.removeFromParent();
            }
            renderedMessages.remove(messageId);
            showUndoOnLatestCompaction();
            if (usage != null) {
                updateContextMeter(usage);
            }
        }, error -> showError(error, "Unable to undo the compaction", null), getView());
    }

    private void onCompact() {
        if (currentChat == null) {
            return;
        }
        final String question = "Compact this chat now? Earlier messages are kept, but the model will see "
                                + "them as a summary. Optionally, say what the summary should focus on:";
        PromptEvent.fire(this, "Compact Context", question, "",
                focus -> {
                    if (focus != null && currentChat != null) {
                        compact(focus.isBlank()
                                ? null
                                : focus.strip());
                    }
                });
    }

    private void compact(final String focus) {
        askStroomAiClient.getConfig(config -> askStroomAiClient.compactChat(
                new CompactAiChatRequest(currentChat, config, focus),
                response -> startPolling(),
                error -> showError(error, "Unable to compact the chat", null),
                getView()), getView());
    }

    /**
     * Sets the chat's title, where it is shown.
     */
    private void setChatTitle(final String title) {
        getView().setTitle(title);
        if (currentChat != null) {
            currentChat = new AiChat(currentChat.getId(), currentChat.getCreateTimeMs(),
                    currentChat.getUpdateTimeMs(), currentChat.getUserUuid(), title);
        }
        if (shownAs == DockType.TAB) {
            tabPresenter().setChatTitle(title);
            RefreshContentTabEvent.fire(this, tabPresenter());
        }
    }

    private void onChangeConfig() {
        askStroomAiClient.getConfig(config -> {
            getDockBehaviourFromPrefs(dockBehaviour -> {
                final AskStroomAiConfigPresenter askStroomAiConfigPresenter = askStroomAiConfigPresenterProvider.get();
                askStroomAiConfigPresenter.show(
                        config, this::updateConfig, dockBehaviour, this::onDockBehaviourChange);
            });
        }, this);
    }

    private void updateConfig(final AskStroomAiConfig config) {
        askStroomAiClient.setConfig(config, this);
        readModel();
    }

    private void readModel() {
        askStroomAiClient.getConfig(config ->
                docSelectionBoxPresenter.setSelectedEntityReference(config.getModelRef(), true), this);
    }

    private void writeModel() {
        // The new model may have other MCP servers, with other prompts.
        mcpPrompts = null;
        askStroomAiClient.getConfig(config -> {
            final AskStroomAiConfig newConfig = config.copy()
                    .modelRef(docSelectionBoxPresenter.getSelectedEntityReference())
                    .build();
            askStroomAiClient.setConfig(newConfig, this);
        }, this);
    }

    void onDockBehaviourChange(final DockBehaviour dockBehaviour) {
        getDockSize(dockSize -> {
            getDockBehaviourFromPrefs(currentDockBehaviour -> {
                // Idempotency: if the behaviour hasn't changed, nothing to do. This handles the duplicate call that
                // occurs when a live-preview change (radio button) is followed by the user clicking OK, which calls
                // the consumer again with the same value.
                if (dockBehaviour.equals(currentDockBehaviour)) {
                    return;
                }

                // Persist to user preferences.
                saveDockBehaviourToPrefs(dockBehaviour);

                if (shownAs != null) {
                    // Close the old view and open the new one on the same chat. Deferred so any in-flight settings
                    // dialog hide sequence completes first.
                    Scheduler.get().scheduleDeferred(() -> {
                        hideShown();
                        Scheduler.get().scheduleDeferred(() -> showAs(dockBehaviour, dockSize));
                    });
                }
            });
        });
    }

    @ProxyCodeSplit
    public interface AskStroomAiProxy extends Proxy<AskStroomAiPresenter> {

    }

    @Override
    public void setContext(final AskStroomAiContext data) {
        this.data = data;

        // Show attachment-style context indicator using the context's description.
        if (data != null) {
            getView().setContextIndicator(SvgImage.CLIPBOARD, data.getDescription());
        } else {
            getView().clearContextIndicator();
        }
    }

    @Override
    public void onSendMessage(final String message) {
        getView().setCommandSuggestions(null);
        // Browsers only ask for permission to notify in response to something the user did, like this.
        askStroomAiClient.getConfig(config -> {
            if (config.isBrowserNotifications()) {
                BrowserNotifications.requestPermission();
            }
        }, SILENT);
        if (runSlashCommand(message)) {
            getView().setSendButtonLoadingState(false);
            return;
        }
        // "//" sends a message that starts with "/".
        final String trimmed = NullSafe.string(message).trim();
        sendMessage(trimmed.startsWith("//")
                ? trimmed.substring(1)
                : message);
    }

    private void sendMessage(final String message) {
        // The command has been used.
        onCloseCommandReference();
        followUps = List.of();
        getView().setFollowUps(List.of());
        getView().setSendButtonLoadingState(true);
        getView().setEmptyState(false);
        getView().clearContextIndicator();

        followBottom = true;
        appendPendingQuestion(message);

        // Scroll markdown container to bottom, so the user's message is displayed
        final SimplePanel markdownContainer = getView().getMarkdownContainer();
        markdownContainer.getElement().setScrollTop(markdownContainer.getElement().getScrollHeight());

        ensureChat(() -> askStroomAiClient.getConfig(config -> {
            final AskStroomAiRequest request = new AskStroomAiRequest(
                    currentChat, config, data, message, getView().getAgentMode());
            // Clear context so follow-up messages don't re-attach the same data.
            this.data = null;
            requestInFlight = true;
            askStroomAiClient.sendMessage(request,
                    response -> {
                        requestInFlight = false;
                        // Poll to get the properly typed, persisted messages. Also picks up a partial
                        // answer, where the user cancelled part way through.
                        startPolling();
                        maybeGenerateTitle(message);
                    }, error -> {
                        requestInFlight = false;
                        showError(error, "Stroom AI request failed", () ->
                                getView().setSendButtonLoadingState(false));
                    }, getView());
            // Poll while the request runs, so progress is seen as it happens rather than all at once
            // when the request returns.
            startPolling();
        }, getView()));
    }

    /**
     * Shows, for a moment, that a copy button has copied: its icon becomes a tick, and its label (if it has one)
     * says so.
     */
    private static void flashCopied(final Element button) {
        final Element svgIcon = ElementUtil.findChild(button, "svgIcon");
        final Element icon = svgIcon != null
                ? svgIcon
                : ElementUtil.findChild(button, "svg-image");
        final Element label = ElementUtil.findChild(button, "ai-message-copy-label");
        final String iconHtml = NullSafe.get(icon, Element::getInnerHTML);
        final String labelText = NullSafe.get(label, Element::getInnerText);
        final String title = button.getTitle();
        if (icon != null) {
            icon.setInnerHTML(SvgImage.OK.getSvg());
        }
        if (label != null) {
            label.setInnerText("Copied");
        }
        button.setTitle("Copied");
        new Timer() {
            @Override
            public void run() {
                if (icon != null) {
                    icon.setInnerHTML(iconHtml);
                }
                if (label != null) {
                    label.setInnerText(labelText);
                }
                button.setTitle(title);
            }
        }.schedule(2000);
    }

    /**
     * Turns a question into an editor, to change it and ask it again.
     */
    private void onEditMessage(final int messageId) {
        final AiChatMessage msg = renderedMessages.get(messageId);
        final Element element = findMessageElement(getView().getMarkdownContainer().getElement(),
                String.valueOf(messageId));
        if (msg == null || element == null) {
            return;
        }
        final HtmlBuilder hb = new HtmlBuilder();
        hb.div(editor -> {
            editor.elem(textArea -> textArea.append(msg.getMessage()),
                    SafeHtmlUtils.fromSafeConstant("textarea"),
                    Attribute.className("ai-edit-text allow-focus"));
            editor.div(buttons -> {
                buttons.elem("Send", BUTTON,
                        Attribute.className("allow-focus ai-approval-button ai-approval-button--approve"),
                        new Attribute(EDIT_SEND_ATTRIBUTE, String.valueOf(messageId)));
                buttons.elem("Cancel", BUTTON,
                        Attribute.className("allow-focus ai-approval-button"),
                        new Attribute(EDIT_CANCEL_ATTRIBUTE, String.valueOf(messageId)));
            }, Attribute.className("ai-edit-buttons"));
        }, Attribute.className("ai-message ai-message--user ai-message--editing"));
        final Element editor = parseHtml(hb).getFirstChildElement();
        editor.setAttribute(MESSAGE_ID_ATTRIBUTE, String.valueOf(messageId));
        element.getParentElement().replaceChild(editor, element);
        final Element textArea = ElementUtil.findChild(editor, "ai-edit-text");
        if (textArea != null) {
            textArea.focus();
        }
    }

    private void onCancelEdit(final int messageId) {
        final AiChatMessage msg = renderedMessages.get(messageId);
        if (msg != null) {
            appendMessage(msg, System.currentTimeMillis());
        }
    }

    /**
     * Asks an edited question, once the user has confirmed that what came after it will go.
     */
    private void onSendEdit(final int messageId) {
        final Element editor = findMessageElement(getView().getMarkdownContainer().getElement(),
                String.valueOf(messageId));
        final Element textArea = editor != null
                ? ElementUtil.findChild(editor, "ai-edit-text")
                : null;
        if (textArea == null || currentChat == null) {
            return;
        }
        final String newText = textArea.<TextAreaElement>cast().getValue().trim();
        if (newText.isEmpty()) {
            return;
        }
        int later = 0;
        boolean sideEffects = false;
        for (final AiChatMessage msg : renderedMessages.values()) {
            if (msg.getId() > messageId) {
                if (msg.getMessageType() == AiMessageType.USER_MESSAGE
                    || msg.getMessageType() == AiMessageType.AI_RESPONSE) {
                    later++;
                }
                if (msg.getMessageType() == AiMessageType.TOOL_CALL
                    || msg.getMessageType() == AiMessageType.MEMORY_UPDATE) {
                    sideEffects = true;
                }
            }
        }
        final String warning = AiChatTexts.editWarning(later, sideEffects);
        ConfirmEvent.fire(this, warning, ok -> {
            if (ok && currentChat != null) {
                askStroomAiClient.getConfig(config -> {
                    final EditAiMessageRequest request = new EditAiMessageRequest(currentChat, config, messageId,
                            newText);
                    getView().setSendButtonLoadingState(true);
                    askStroomAiClient.editMessage(request, response -> {
                        // The question and what follows are replaced; the new question is shown now, until
                        // polling brings the stored one.
                        removeMessageElementsFrom(messageId, false);
                        followBottom = true;
                        appendPendingQuestion(newText);
                        scrollToBottom();
                        startPolling();
                    }, error -> showError(error, "Unable to edit the question", () ->
                            getView().setSendButtonLoadingState(false)), getView());
                }, getView());
            }
        });
    }

    /**
     * Answers a request for approval.
     *
     * @param callId The tool call answered, or null for all those not yet answered.
     */
    private void onApprovalButton(final int messageId,
                                  final boolean approved,
                                  final AiApprovalScope scope,
                                  final String callId) {
        final AiChatMessage request = approvalRequests.get(messageId);
        if (request == null) {
            return;
        }
        onAnswerApproval(messageId, approved, scope, callId, null);
    }

    private void onAnswerApproval(final int messageId,
                                  final boolean approved,
                                  final AiApprovalScope scope,
                                  final String callId,
                                  final String reason) {
        final AiChatMessage request = approvalRequests.get(messageId);
        if (currentChat == null || request == null) {
            return;
        }
        final List<String> callIds = callId != null
                ? List.of(callId)
                : null;
        final AnswerAiApprovalRequest answer = new AnswerAiApprovalRequest(currentChat, messageId, approved, scope,
                reason, callIds);
        askStroomAiClient.answerApproval(answer, done -> {
            final String json;
            if (AiToolMessages.isToolRequest(request.getMessage())) {
                json = Boolean.TRUE.equals(done)
                        ? AiToolMessages.withDecisions(request.getMessage(), callIds, approved, reason)
                        : AiToolMessages.withStatus(request.getMessage(), "EXPIRED");
            } else {
                json = AiToolMessages.withStatus(request.getMessage(), approved
                        ? (Boolean.TRUE.equals(done)
                                ? "APPROVED"
                                : "FAILED")
                        : "REJECTED");
            }
            appendMessage(new AiChatMessage(request.getId(), request.getChatId(), request.getCreateTimeMs(),
                    request.getMessageType(), request.getAttachmentId(), json, request.getTurnId(),
                    request.getTokenCount()), System.currentTimeMillis());
            // The run carries on once everything is answered.
            pollAfterAnswer();
        }, error -> showError(error, "Unable to answer the request", () -> {
        }), getView());
    }

    /**
     * Asks a question again, unchanged. The question's answer and everything after it are replaced.
     *
     * @param turnId The id of the question.
     */
    private void onRetry(final int turnId) {
        if (currentChat == null) {
            return;
        }
        getView().setSendButtonLoadingState(true);
        askStroomAiClient.getConfig(config -> {
            final EditAiMessageRequest request = new EditAiMessageRequest(currentChat, config, turnId, null);
            askStroomAiClient.editMessage(request, response -> {
                // The question and everything after it are replaced by the new answer, which keeps the question
                // where it is in the conversation.
                removeMessageElementsFrom(turnId, true);
                startPolling();
            }, error -> showError(error, "Unable to ask again", () ->
                    getView().setSendButtonLoadingState(false)), getView());
        }, getView());
    }

    /**
     * Removes the elements of the messages from one onwards.
     *
     * @param fromMessageId The first message to remove.
     * @param keepFirst     True to keep the first message itself, i.e. only remove those after it.
     */
    private void removeMessageElementsFrom(final int fromMessageId, final boolean keepFirst) {
        final Element markdownContainer = getView().getMarkdownContainer().getElement();
        Element child = markdownContainer.getFirstChildElement();
        while (child != null) {
            final Element next = child.getNextSiblingElement();
            final String idStr = child.getAttribute(MESSAGE_ID_ATTRIBUTE);
            if (PENDING_MESSAGE_ID.equals(idStr)) {
                // A question not yet stored comes after everything else, so goes too.
                child.removeFromParent();
            } else if (NullSafe.isNonBlankString(idStr)) {
                // Not always just a number, e.g. the steps a message left to the user.
                final int id = AiActivityGroups.messageIdOf(idStr);
                if (id > fromMessageId || (id == fromMessageId && !keepFirst)) {
                    child.removeFromParent();
                    renderedMessages.remove(id);
                }
            }
            child = next;
        }
        activityGroups.removeFrom(fromMessageId, keepFirst);
        removeLiveAnswer();
        // Don't leave a day divider with nothing after it.
        Element last = markdownContainer.getLastChild() != null
                ? markdownContainer.getLastChild().cast()
                : null;
        while (last != null && last.hasClassName(DAY_DIVIDER_CLASS)) {
            final Element previous = last.getPreviousSiblingElement();
            last.removeFromParent();
            lastRenderedDay = null;
            last = previous;
        }
    }

    private void onResumeRun() {
        if (currentChat == null) {
            return;
        }
        getView().setSendButtonLoadingState(true);
        askStroomAiClient.getConfig(config -> {
            final AskStroomAiRequest request = new AskStroomAiRequest(currentChat, config, null, null);
            askStroomAiClient.resumeRun(request,
                    response -> startPolling(),
                    error -> showError(error, "Unable to resume", () ->
                            getView().setSendButtonLoadingState(false)),
                    getView());
        }, getView());
    }

    /**
     * Shows a line at the end of the conversation if the chat's last run is queued, or stopped before
     * finishing (with a button to resume it), and takes it away otherwise.
     */
    private void updateRunStatus(final AiChatRun run) {
        final Element existing = Document.get().getElementById(RUN_STATUS_ID);
        if (existing != null) {
            existing.removeFromParent();
        }
        if (run == null || run.getStatus() == AiChatRunStatus.DONE
            || (run.isActive() && run.getStatus() != AiChatRunStatus.QUEUED)) {
            return;
        }
        final boolean resumable = run.getStatus() == AiChatRunStatus.STOPPED
                                  || run.getStatus() == AiChatRunStatus.INTERRUPTED
                                  || run.getStatus() == AiChatRunStatus.FAILED;
        final HtmlBuilder hb = new HtmlBuilder();
        hb.div(div -> {
            div.span(text -> text.append(runStatusText(run)), Attribute.className("ai-run-status-text"));
            if (resumable) {
                div.elem(button -> button.append("Resume"),
                        BUTTON,
                        Attribute.className("ai-run-resume"),
                        Attribute.title("Answer the last question again"),
                        new Attribute(RESUME_RUN_ATTRIBUTE, String.valueOf(run.getId())));
            }
        }, Attribute.className("ai-message ai-message--run-status"), new Attribute("id", RUN_STATUS_ID));
        appendToContainer(hb);
    }

    private static String runStatusText(final AiChatRun run) {
        if (run.getStatus() == AiChatRunStatus.QUEUED) {
            return "Queued: waiting for one of your other questions, or other users' questions, to finish";
        }
        final String text = run.getStopReason() != null
                ? run.getStopReason().getDisplayValue()
                : run.getStatus().getDisplayValue();
        return run.getStatus() == AiChatRunStatus.FAILED && NullSafe.isNonBlankString(run.getErrorMessage())
                ? text + ": " + run.getErrorMessage()
                : text;
    }

    @Override
    public void onCancelProcessing() {
        if (currentChat != null) {
            askStroomAiClient.cancelProcessing(currentChat.getId(),
                    success -> {
                        // Server will set the cancellation flag; the poll loop will pick up
                        // partial results and stop. Reset UI now so the user can type again.
                        getView().setSendButtonLoadingState(false);
                        // Polling may have paused while the run waited for the user (e.g. to fill in a form),
                        // so look again to see it stop.
                        pollAfterAnswer();
                    }, getView());
        }
    }

    private void showError(final RestError error,
                           final String message,
                           final AlertCallback callback) {
        AlertEvent.fireError(
                AskStroomAiPresenter.this,
                message + " - " + error.getMessage(),
                null,
                callback);
    }

    /**
     * Polls now the user has answered something the run was waiting for, and keeps polling while the run carries
     * on, even if it still shows as waiting for a moment.
     */
    private void pollAfterAnswer() {
        keepPollingUntilMs = System.currentTimeMillis() + KEEP_POLLING_AFTER_ANSWER_MS;
        startPolling();
    }

    /**
     * Poll for new messages since lastSeenMessageId. Renders any new messages
     * with type-aware formatting and updates the lastSeenMessageId.
     */
    private void startPolling() {
        if (polling) {
            // A poll is under way, and may have been answered before what is being waited for happened (e.g.
            // the run was made), so poll again when it returns rather than stopping.
            pollAgain = true;
        } else {
            polling = true;
            pollAgain = false;
            pollForNewMessages();
        }
    }

    private void pollForNewMessages() {
        if (currentChat == null) {
            polling = false;
            return;
        }
        final int chatId = currentChat.getId();
        askStroomAiClient.pollMessages(chatId, lastSeenMessageId, isLooking(), pendingFormIds(), response -> {
            if (currentChat == null || currentChat.getId() != chatId) {
                // The user has moved to another chat since this poll was sent. Rendering the reply
                // now would put one chat's messages into another's.
                polling = false;
                return;
            }

            final boolean hasNewMessages = response.getNewMessages() != null
                                           && !response.getNewMessages().isEmpty();
            if (hasNewMessages) {
                final long nowMs = System.currentTimeMillis();
                for (final AiChatMessage msg : response.getNewMessages()) {
                    // The user's own messages replace the pending copy shown when they were sent.
                    appendMessage(msg, nowMs);
                    // Track the highest seen message ID.
                    lastSeenMessageId = Math.max(lastSeenMessageId, msg.getId());
                }
                scrollToBottom();
            }

            // Show how much of the context window the chat uses, when that has changed.
            if (response.getContextUsage() != null) {
                updateContextMeter(response.getContextUsage());
            }

            // Update attachment status elements in-place.
            updateAttachmentStatuses(response.getAttachments());
            // And forms the server has answered for the user, e.g. cancelled as the run stopped.
            updateWatchedMessages(response.getWatchedMessages());

            // Say if the run stopped before finishing, and offer to resume it.
            updateRunStatus(response.getRun());
            currentRunActive = !response.isComplete();
            getView().setInProgress(!response.isComplete());
            // The input box glows while the chat works, e.g. calling a tool or the model, but not while it waits
            // for the user to answer something, as nothing happens until they do.
            final boolean needsUser = response.getRun() != null
                                      && response.getRun().getStatus() == AiChatRunStatus.WAITING_FOR_USER;
            getView().setSendButtonLoadingState(requestInFlight || (!response.isComplete() && !needsUser));
            refreshFollowUps();

            // Show what the server is currently doing, and take the line away when it is done.
            updateWorkingMessage(response.getWorkingMessage());

            // Show the answer and reasoning as the model writes them.
            final boolean streaming = updateStreaming(response.getStreamingMessages(), response.isComplete());

            // Keep polling while this client is waiting on a request, while the chat's agent run is
            // still active or data is still coming down (the server reports both as not complete), or
            // while messages are still arriving.
            // A run waiting for the user (e.g. to approve something) does nothing until they answer, which starts
            // polling again, so there is no need to keep asking.
            final boolean waitingForUser = response.getRun() != null
                                           && response.getRun().getStatus() == AiChatRunStatus.WAITING_FOR_USER
                                           && System.currentTimeMillis() > keepPollingUntilMs;
            final boolean again = pollAgain;
            pollAgain = false;
            if (!waitingForUser && (again || requestInFlight || !response.isComplete() || hasNewMessages)) {
                // Poll often while the run works and the user is looking, less often when they are not.
                new com.google.gwt.user.client.Timer() {
                    @Override
                    public void run() {
                        pollForNewMessages();
                    }
                }.schedule(!isLooking()
                        ? HIDDEN_POLL_MS
                        : streaming
                                ? STREAMING_POLL_MS
                                : ACTIVE_POLL_MS);
            } else {
                polling = false;
                getView().setSendButtonLoadingState(false);
            }
        }, error -> {
            // Clear loading state on poll failure so the UI doesn't get stuck.
            polling = false;
            getView().setSendButtonLoadingState(false);
            getView().setInProgress(false);
        }, getView());
    }

    /**
     * Appends the html to the end of the conversation and scrolls to it. Only the new html is parsed; the
     * messages already shown are left as they are, so any state in them (e.g. an expanded section) is kept.
     */
    void appendToContainer(final HtmlBuilder hb) {
        final Element markdownContainer = getView().getMarkdownContainer().getElement();
        final Element holder = parseHtml(hb);
        while (holder.getFirstChild() != null) {
            markdownContainer.appendChild(holder.getFirstChild());
        }
        scrollToBottom();
    }

    /**
     * Shows a question the user has just sent, until the stored message comes back from polling and takes its
     * place.
     */
    private void appendPendingQuestion(final String question) {
        final long nowMs = System.currentTimeMillis();
        final HtmlBuilder hb = new HtmlBuilder();
        appendMessageHtml(hb, "ai-message ai-message--user", question, nowMs, nowMs, false, 0, false);
        final Element element = parseHtml(hb).getFirstChildElement();
        element.setAttribute(MESSAGE_ID_ATTRIBUTE, PENDING_MESSAGE_ID);
        final Element markdownContainer = getView().getMarkdownContainer().getElement();
        appendDayDivider(markdownContainer, nowMs, nowMs);
        markdownContainer.appendChild(element);
        scrollToBottom();
    }

    /**
     * Renders a message as its own element, tagged with its id. If an element for the message is already shown,
     * it is replaced where it stands, otherwise the new element is added to the end of the conversation.
     */
    private void appendMessage(final AiChatMessage msg, final long nowMs) {
        if (msg.getMessageType() == AiMessageType.SUGGESTIONS) {
            // Not part of the conversation: offered above the input instead.
            followUps = parseFollowUps(msg.getMessage());
            refreshFollowUps();
            return;
        }
        if (msg.getMessageType() == AiMessageType.TASK_LIST) {
            // Not part of the conversation: shown above the input, as the latest for the turn.
            showTaskList(msg.getMessage());
            return;
        }
        if (msg.getMessageType() == AiMessageType.USER_MESSAGE
            || msg.getMessageType() == AiMessageType.STEER_MESSAGE) {
            followUps = List.of();
            refreshFollowUps();
        }
        if (msg.getMessageType() == AiMessageType.USER_MESSAGE) {
            // A new question has a task list of its own, if any.
            getView().setTaskList(null, SafeHtmlUtils.EMPTY_SAFE_HTML);
        }
        renderedMessages.put(msg.getId(), msg);
        final HtmlBuilder hb = new HtmlBuilder();
        renderMessage(hb, msg, nowMs);
        final Element holder = parseHtml(hb);
        final Element messageElement = holder.getFirstChildElement();
        if (messageElement == null) {
            return;
        }
        final String messageId = String.valueOf(msg.getId());
        messageElement.setAttribute(MESSAGE_ID_ATTRIBUTE, messageId);
        decorateCodeBlocks(messageElement);
        decorateStroomLinks(messageElement);

        final Element markdownContainer = getView().getMarkdownContainer().getElement();
        if (AiActivityGroups.isActivity(msg.getMessageType())) {
            if (msg.getMessageType() == AiMessageType.INTERIM_RESPONSE) {
                // The text being written is now stored, so is shown in its place, after what was said before.
                removeLiveAnswer();
                showInterimInActivity();
            }
            final Element existingEntry = activityGroups.findEntry(messageId);
            if (existingEntry != null) {
                existingEntry.getParentElement().replaceChild(messageElement, existingEntry);
            } else {
                final AiChatMessage question = renderedMessages.get(msg.getTurnId());
                activityGroups.add(markdownContainer, msg, messageElement, question != null
                        ? question.getCreateTimeMs()
                        : 0);
                keepProgressInPlace(markdownContainer);
            }
            if (msg.getMessageType() == AiMessageType.TOOL_RESULT) {
                appendUserActionsCard(markdownContainer, msg, nowMs);
            } else if (msg.getMessageType() == AiMessageType.INTERIM_RESPONSE
                       && Document.get().getElementById(WORKING_MESSAGE_ID) != null) {
                // While the agent works, shown once the Working line moves on from it (see updateWorkingMessage).
                interimShownAsProgress = msg;
                messageElement.addClassName(SHOWN_AS_PROGRESS_CLASS);
            }
            return;
        }
        if (msg.getMessageType() == AiMessageType.AI_RESPONSE || msg.getMessageType() == AiMessageType.ERROR) {
            // The answer is here, so the turn's work is done.
            activityGroups.finish(msg.getTurnId(), msg.getCreateTimeMs());
            removeLiveAnswer();
        }
        if (msg.getMessageType() == AiMessageType.ELICITATION
            || msg.getMessageType() == AiMessageType.APPROVAL_REQUEST
            || msg.getMessageType() == AiMessageType.AI_RESPONSE
            || msg.getMessageType() == AiMessageType.ERROR) {
            // What the model said comes before what it asks the user, and before its answer.
            showInterimInActivity();
        }
        Element existing = findMessageElement(markdownContainer, messageId);
        if (existing == null && (msg.getMessageType() == AiMessageType.USER_MESSAGE
                                 || msg.getMessageType() == AiMessageType.STEER_MESSAGE)) {
            existing = findMessageElement(markdownContainer, PENDING_MESSAGE_ID);
        }
        if (existing != null) {
            markdownContainer.replaceChild(messageElement, existing);
        } else {
            appendDayDivider(markdownContainer, msg.getCreateTimeMs(), nowMs);
            markdownContainer.appendChild(messageElement);
        }
        if (msg.getMessageType() == AiMessageType.COMPACTION_SUMMARY) {
            showUndoOnLatestCompaction();
        }
    }

    /**
     * Adds a divider saying which day the following messages are from, when the day changes.
     */
    private void appendDayDivider(final Element markdownContainer, final long timeMs, final long nowMs) {
        if (timeMs <= 0) {
            return;
        }
        // Days are worked out in the user's preferred time zone, as the message times are.
        final String day = dateTimeFormatter.format(timeMs, DAY_KEY_PATTERN);
        if (day.equals(lastRenderedDay)) {
            return;
        }
        lastRenderedDay = day;
        final String label;
        if (day.equals(dateTimeFormatter.format(nowMs, DAY_KEY_PATTERN))) {
            label = "Today";
        } else if (day.equals(dateTimeFormatter.format(nowMs - 24L * 60 * 60 * 1000, DAY_KEY_PATTERN))) {
            label = "Yesterday";
        } else {
            label = dateTimeFormatter.format(timeMs, DAY_LABEL_PATTERN);
        }
        final Element divider = Document.get().createDivElement();
        divider.setClassName(DAY_DIVIDER_CLASS);
        divider.setInnerText(label);
        markdownContainer.appendChild(divider);
    }

    /**
     * @return The language of a code element, from its "language-xxx" class, or an empty string.
     */
    private static String codeLanguage(final Element code) {
        if (code != null) {
            for (final String className : code.getClassName().split(" ")) {
                if (className.startsWith("language-")) {
                    return className.substring("language-".length());
                }
            }
        }
        return "";
    }

    /**
     * Gives each code block a header with its language and a button to copy it.
     */
    /// Marks links that open something in Stroom (a document or a stream), so they show a forward icon.
    private static void decorateStroomLinks(final Element element) {
        final NodeList<Element> links = element.getElementsByTagName("a");
        for (int i = 0; i < links.getLength(); i++) {
            final Element link = links.getItem(i);
            if (ChatLinks.isStroomLink(link.getAttribute("href"))) {
                link.addClassName("ai-stroom-link");
                if (link.getTitle() == null || link.getTitle().isEmpty()) {
                    link.setTitle("Open in Stroom");
                }
            }
        }
    }

    private static void decorateCodeBlocks(final Element messageElement) {
        final NodeList<Element> blocks = messageElement.getElementsByTagName("pre");
        for (int i = 0; i < blocks.getLength(); i++) {
            final Element pre = blocks.getItem(i);
            // The arguments of a tool call waiting for approval are not code to copy.
            if (pre.hasClassName("ai-approval-args")) {
                continue;
            }
            if (pre.getPreviousSiblingElement() != null
                && pre.getPreviousSiblingElement().hasClassName("ai-code-header")) {
                continue;
            }
            final String language = codeLanguage(pre.getFirstChildElement());
            final HtmlBuilder hb = new HtmlBuilder();
            hb.div(header -> {
                header.span(label -> label.append(language), Attribute.className("ai-code-language"));
                header.elem(button -> setCopyButtonContent(button, SvgImage.COPY, "Copy"),
                        BUTTON,
                        Attribute.className("ai-message-copy ai-code-copy"),
                        Attribute.title("Copy the code"),
                        new Attribute("data", pre.getInnerText()));
            }, Attribute.className("ai-code-header"));
            final Element holder = Document.get().createDivElement();
            holder.setInnerHTML(hb.toSafeHtml().asString());
            pre.getParentElement().insertBefore(holder.getFirstChildElement(), pre);
        }
    }

    private Element findMessageElement(final Element markdownContainer, final String messageId) {
        Element child = markdownContainer.getFirstChildElement();
        while (child != null) {
            if (messageId.equals(child.getAttribute(MESSAGE_ID_ATTRIBUTE))) {
                return child;
            }
            child = child.getNextSiblingElement();
        }
        return null;
    }

    private Element parseHtml(final HtmlBuilder hb) {
        final Element holder = Document.get().createDivElement();
        holder.setInnerHTML(hb.toSafeHtml().asString());
        return holder;
    }

    /**
     * Clears the conversation shown, and what is known about it.
     */
    private void clearRenderedMessages() {
        getView().clearMessages();
        getView().setTaskList(null, SafeHtmlUtils.EMPTY_SAFE_HTML);
        // Shown again by the next poll if the chat now shown has anything in progress.
        getView().setInProgress(false);
        followUps = List.of();
        refreshFollowUps();
        renderedMessages.clear();
        interimShownAsProgress = null;
        activityGroups.clear();
        contextUsage = null;
        getView().setContextMeter(null, null, null);
        lastRenderedDay = null;
        followBottom = true;
        lastScrollTop = 0;
        getView().setJumpToLatestVisible(false);
    }

    /**
     * @return True if the conversation is scrolled to (or near) the bottom.
     */
    private boolean isAtBottom() {
        final Element container = getView().getMarkdownContainer().getElement();
        return container.getScrollHeight() - container.getScrollTop() - container.getClientHeight()
               <= BOTTOM_TOLERANCE_PX;
    }

    /**
     * Scrolls to the latest message if the user was following the conversation, or else offers to jump to it,
     * so new messages never pull the view away from what the user is reading.
     */
    private void scrollToBottom() {
        if (followBottom) {
            scrollToLatest();
        } else {
            getView().setJumpToLatestVisible(true);
        }
    }

    @Override
    public void onConversationScrolled() {
        final int scrollTop = getView().getMarkdownContainer().getElement().getScrollTop();
        // Only the user scrolling up stops following the conversation. Content added below, or the view getting
        // shorter (e.g. when the task list grows), also leaves it short of the bottom, but the user still wants
        // to follow it.
        if (isAtBottom()) {
            followBottom = true;
        } else if (scrollTop < lastScrollTop) {
            followBottom = false;
        }
        lastScrollTop = scrollTop;
        getView().setJumpToLatestVisible(!followBottom);
    }

    @Override
    public void onConversationResized() {
        // The view getting shorter leaves the scroll position where it was, which hides the latest messages
        // behind the bottom of the view rather than pushing them up.
        if (followBottom) {
            scrollToLatest();
        }
    }

    @Override
    public void onJumpToLatest() {
        followBottom = true;
        scrollToLatest();
    }

    private void scrollToLatest() {
        getView().setJumpToLatestVisible(false);
        final Element markdownContainer = getView().getMarkdownContainer().getElement();
        markdownContainer.setScrollTop(markdownContainer.getScrollHeight());
        lastScrollTop = markdownContainer.getScrollTop();
    }

    /// @param error An ERROR message.
    /// @return The question the error stopped being answered, or 0 if it has an answer, e.g. the error came from
    /// attaching data to the next question, which is stored before the question is.
    private int unansweredTurn(final AiChatMessage error) {
        final int turnId = error.getTurnId();
        if (turnId <= 0) {
            return 0;
        }
        for (final AiChatMessage msg : renderedMessages.values()) {
            if (msg.getTurnId() == turnId && msg.getMessageType() == AiMessageType.AI_RESPONSE) {
                return 0;
            }
        }
        return turnId;
    }

    // Takes away the Retry buttons of errors, as a newer question has been asked.
    private void removeErrorRetries() {
        final NodeList<Element> retries = getView().getMarkdownContainer().getElement()
                .getElementsByTagName("button");
        for (int i = retries.getLength() - 1; i >= 0; i--) {
            final Element retry = retries.getItem(i);
            if (retry.hasClassName(ERROR_RETRY_CLASS)) {
                retry.removeFromParent();
            }
        }
    }

    /**
     * Render a single message with type-aware HTML structure.
     */
    private void renderMessage(final HtmlBuilder hb, final AiChatMessage msg, final long nowMs) {
        final long timeMs = msg.getCreateTimeMs();
        final int messageId = msg.getId();
        final boolean deletable = msg.getMessageType() == AiMessageType.USER_MESSAGE
                                  || msg.getMessageType() == AiMessageType.ATTACHMENT;
        switch (msg.getMessageType()) {
            case USER_MESSAGE:
                // Asking again for an earlier question would replace this one too, so only the latest is offered.
                removeErrorRetries();
                appendMessageHtml(hb, "ai-message ai-message--user", msg.getMessage(),
                        timeMs, nowMs, true, messageId, deletable, 0, true);
                break;
            case AI_RESPONSE:
                appendMessageHtml(hb, "ai-message ai-message--assistant", msg.getMessage(),
                        timeMs, nowMs, true, messageId, false, msg.getTurnId(), false);
                break;
            case INTERIM_RESPONSE:
                // What the model said while working: no retry, as the turn's answer is still to come.
                appendMessageHtml(hb, "ai-message ai-message--assistant ai-message--interim", msg.getMessage(),
                        timeMs, nowMs, true, messageId, false, 0, false);
                break;
            case ERROR:
                // An error given as JSON is shown as a heading and its details. One that stopped a question
                // being answered, e.g. as a tool or the model failed, offers to ask it again.
                appendMessageHtml(hb, "ai-message ai-message--error", AiErrorMessages.markdown(msg.getMessage()),
                        timeMs, nowMs, true, messageId, false, unansweredTurn(msg), false);
                break;
            case STEER_MESSAGE:
                appendMessageHtml(hb, "ai-message ai-message--user ai-message--steer", msg.getMessage(),
                        timeMs, nowMs, true, messageId, false);
                break;
            case TOOL_CALL:
                appendDetailsElement(hb, "ai-message ai-message--tool",
                        SvgImage.CODE, AiToolMessages.toolHeading(msg.getMessage()),
                        AiToolMessages.callMarkdown(msg.getMessage()), timeMs, nowMs);
                break;
            case TOOL_RESULT:
                appendDetailsElement(hb, "ai-message ai-message--tool",
                        AiToolMessages.getBoolean(msg.getMessage(), "error")
                                ? SvgImage.ERROR
                                : SvgImage.OK,
                        "Result of " + AiToolMessages.getString(msg.getMessage(), "tool"),
                        AiToolMessages.resultMarkdown(msg.getMessage()), timeMs, nowMs);
                break;
            case MEMORY_UPDATE:
                appendDetailsElement(hb, "ai-message ai-message--memory", SvgImage.INFO,
                        "Memory updated: " + AiToolMessages.getString(msg.getMessage(), "path"),
                        AiToolMessages.memoryChangeMarkdown(msg.getMessage()), timeMs, nowMs);
                break;
            case APPROVAL_REQUEST:
                approvalRequests.put(messageId, msg);
                appendApprovalCard(hb, msg, timeMs, nowMs);
                break;
            case TURN_DIGEST:
                // Only for the model.
                break;
            case COMPACTION_SUMMARY:
                appendCompactionDivider(hb, msg, timeMs, nowMs);
                break;
            case ELICITATION:
                appendElicitationCard(hb, msg, timeMs, nowMs);
                break;
            case WARNING:
                appendMessageHtml(hb, "ai-message ai-message--warning", msg.getMessage(),
                        timeMs, nowMs, true, messageId, false);
                break;
            case WORKING:
                appendWorkingMessage(hb, msg, timeMs, nowMs);
                break;
            case THINKING:
                appendDetailsElement(hb, "ai-message ai-message--thinking",
                        SvgImage.AI, AiToolMessages.thinkingTitle(msg.getMessage()),
                        AiToolMessages.thinkingBody(msg.getMessage()), timeMs, nowMs);
                break;
            case DASHBOARD_DATA:
            case QUERY_DATA:
            case TABLE_DATA:
                appendDetailsElement(hb, "ai-message ai-message--data",
                        SvgImage.TABLE, "Data context", msg.getMessage(), timeMs, nowMs);
                break;
            case DEBUG_DETAIL:
                appendDetailsElement(hb, "ai-message ai-message--debug-detail",
                        SvgImage.INFO, "Request detail", msg.getMessage(), timeMs, nowMs);
                break;
            case ATTACHMENT:
                appendAttachmentMessage(hb, msg, timeMs, nowMs, messageId, deletable);
                break;
            default:
                appendMessageHtml(hb, "ai-message", msg.getMessage(), timeMs, nowMs, false, 0, false);
                break;
        }
    }

    /**
     * Append a message div with the given CSS class, markdown content, and timestamp.
     * If {@code showCopy} is true, a copy button is added for AI responses.
     */
    private void appendMessageHtml(final HtmlBuilder hb,
                                   final String cssClass,
                                   final String markdownText,
                                   final long timeMs,
                                   final long nowMs,
                                   final boolean showCopy,
                                   final int messageId,
                                   final boolean deletable) {
        appendMessageHtml(hb, cssClass, markdownText, timeMs, nowMs, showCopy, messageId, deletable, 0, false);
    }

    /**
     * As {@link #appendMessageHtml(HtmlBuilder, String, String, long, long, boolean, int, boolean)}, with a
     * button to ask the question again if a turn is given.
     */
    private void appendMessageHtml(final HtmlBuilder hb,
                                   final String cssClass,
                                   final String markdownText,
                                   final long timeMs,
                                   final long nowMs,
                                   final boolean showCopy,
                                   final int messageId,
                                   final boolean deletable,
                                   final int retryTurnId,
                                   final boolean editable) {
        final SafeHtml markdownHtml = markdownConverter.convertUntrustedMarkdownToHtml(markdownText);
        final boolean assistant = cssClass.contains("ai-message--assistant");
        final boolean error = cssClass.contains("ai-message--error");

        hb.div(wrapper -> {
            if (assistant) {
                wrapper.div(avatar -> {
                    icon(avatar, SvgImage.AI);
                    avatar.append("Stroom AI");
                }, Attribute.className("ai-message-avatar"));
            }
            wrapper.div(content -> content.append(markdownHtml), Attribute.className("ai-message-content"));
            // An error that stopped a question being answered offers to ask it again, where it can be seen.
            if (error && retryTurnId > 0) {
                wrapper.elem(button -> button.append("Retry"),
                        BUTTON,
                        Attribute.className(ERROR_RETRY_CLASS),
                        Attribute.title("Ask the question again"),
                        new Attribute(RETRY_TURN_ATTRIBUTE, String.valueOf(retryTurnId)));
            }
            // Add message footer (timestamp and buttons), shown when the message is hovered over.
            wrapper.div(footer -> {

                // Add a button to edit the question.
                if (editable && messageId > 0) {
                    button(footer,
                            SvgImage.EDIT,
                            iconButtonClassName("ai-message-edit"),
                            Attribute.title("Edit"),
                            new Attribute(EDIT_MESSAGE_ATTRIBUTE, String.valueOf(messageId)));
                }

                // Add a button to ask the question again.
                if (retryTurnId > 0 && !error) {
                    button(footer,
                            SvgImage.REFRESH,
                            iconButtonClassName("ai-message-retry"),
                            Attribute.title("Ask again"),
                            new Attribute(RETRY_TURN_ATTRIBUTE, String.valueOf(retryTurnId)));
                }

                // Add copy buttons: the original markdown, and for answers the text as shown.
                if (showCopy) {
                    button(footer, SvgImage.COPY,
                            iconButtonClassName("ai-message-copy-icon"),
                            Attribute.title("Copy as Markdown"),
                            new Attribute("data", markdownText));
                    if (assistant) {
                        button(footer, SvgImage.COPY_TEXT,
                                iconButtonClassName("ai-message-copy-icon"),
                                Attribute.title("Copy as text"),
                                new Attribute(COPY_TEXT_ATTRIBUTE, "true"));
                    }
                }

                // Add delete button, last, so it is not clicked by mistake for another.
                if (deletable && messageId > 0) {
                    button(footer,
                            SvgImage.DELETE,
                            iconButtonClassName("ai-message-delete"),
                            Attribute.title("Delete message"),
                            new Attribute("data-delete-message-id", String.valueOf(messageId)));
                }

                // Add timestamp.
                timestamp(footer, timeMs, nowMs);

            }, Attribute.className("ai-message-footer"));
        }, Attribute.className(cssClass));
    }

    /**
     * Appends a card asking the user to approve something the agent wants to do, with Approve and Reject buttons
     * while it waits for an answer.
     */
    private void appendApprovalCard(final HtmlBuilder hb,
                                    final AiChatMessage msg,
                                    final long timeMs,
                                    final long nowMs) {
        final String json = msg.getMessage();
        if (AiToolMessages.isToolRequest(json)) {
            appendToolApprovalCard(hb, msg, timeMs, nowMs);
            return;
        }
        hb.div(card -> {
            card.append(markdownConverter.convertUntrustedMarkdownToHtml(AiToolMessages.approvalMarkdown(json)));
            card.div(footer -> {
                if (AiToolMessages.isPendingApproval(json)) {
                    approvalButton(footer, msg, "Approve", true, AiApprovalScope.ONCE, null);
                    approvalButton(footer, msg, "Reject", false, AiApprovalScope.ONCE, null);
                }
                timestamp(footer, timeMs, nowMs);
            }, Attribute.className("ai-message-footer"));
        }, Attribute.className("ai-message ai-message--approval" + (AiToolMessages.isPendingApproval(json)
                ? " ai-message--needs-you"
                : "")));
    }

    /**
     * Renders a request to approve tool calls: each call with its tool, server, risks and full arguments, and
     * buttons to allow or deny it, plus Allow all and Deny all when several are waiting.
     */
    private void appendToolApprovalCard(final HtmlBuilder hb,
                                        final AiChatMessage msg,
                                        final long timeMs,
                                        final long nowMs) {
        final String json = msg.getMessage();
        final boolean pending = AiToolMessages.isPendingApproval(json);
        final List<AiToolMessages.ApprovalCall> calls = AiToolMessages.approvalCalls(json);
        final long undecided = calls.stream().filter(call -> call.getDecision() == null).count();
        final String reason = AiToolMessages.getString(json, "reason");
        hb.div(card -> {
            final String titleText = pending
                    ? "The agent wants to use " + (calls.size() == 1
                    ? "a tool that needs"
                    : calls.size() + " tools that need") + " your approval"
                    : "Tool calls the agent asked to use";
            // Why it asks is in the title's tooltip, rather than a line of its own.
            if (pending && reason != null) {
                card.div(title -> title.append(titleText), Attribute.className("ai-approval-title"),
                        Attribute.title(reason));
            } else {
                card.div(title -> title.append(titleText), Attribute.className("ai-approval-title"));
            }
            for (final AiToolMessages.ApprovalCall call : calls) {
                card.div(callDiv -> appendApprovalCall(callDiv, msg, json, call, pending),
                        Attribute.className("ai-approval-call"));
            }
            card.div(footer -> {
                if (pending && undecided > 1) {
                    approvalButton(footer, msg, "Allow all", true, AiApprovalScope.ONCE, null);
                    approvalButton(footer, msg, "Deny all", false, AiApprovalScope.ONCE, null);
                }
                if ("EXPIRED".equals(AiToolMessages.getString(json, "status"))) {
                    footer.span(text -> text.append("Not answered in time, so nothing was run."),
                            Attribute.className("ai-approval-reason"));
                }
                timestamp(footer, timeMs, nowMs);
            }, Attribute.className("ai-message-footer"));
        }, Attribute.className("ai-message ai-message--approval" + (pending
                ? " ai-message--needs-you"
                : "")));
    }

    private void appendApprovalCall(final HtmlBuilder callDiv,
                                    final AiChatMessage msg,
                                    final String json,
                                    final AiToolMessages.ApprovalCall call,
                                    final boolean pending) {
        callDiv.div(header -> {
            // What the tool does, when its name is pointed at.
            if (call.getDescription() != null) {
                header.elem(code -> code.append(call.getTool()), SafeHtmlUtils.fromSafeConstant("code"),
                        Attribute.title(call.getDescription()));
            } else {
                header.elem(code -> code.append(call.getTool()), SafeHtmlUtils.fromSafeConstant("code"));
            }
            // An untrusted server always has this classification, so it shows whether the server is trusted.
            final boolean trusted = !call.getRisks().contains(AiChatTexts.UNTRUSTED_SERVER);
            if (call.getServer() != null) {
                header.span(server -> server.append(" on " + call.getServer()),
                        Attribute.className("ai-approval-server"));
                header.span(icon -> icon(icon, trusted
                                ? SvgImage.TICK
                                : SvgImage.WARNING),
                        Attribute.className(trusted
                                ? "ai-trust-icon ai-trust-icon--trusted"
                                : "ai-trust-icon ai-trust-icon--untrusted"),
                        Attribute.title(AiChatTexts.trustExplanation(trusted)));
            }
            for (final String risk : call.getRisks()) {
                if (AiChatTexts.UNTRUSTED_SERVER.equals(risk)) {
                    // Shown by the icon beside the server.
                    continue;
                }
                final String explanation = AiChatTexts.riskExplanation(risk);
                // Read-only is not a risk, so it isn't shown as one.
                final Attribute className = Attribute.className("read-only".equals(risk)
                        ? "ai-risk-badge ai-risk-badge--safe"
                        : "ai-risk-badge");
                if (explanation != null) {
                    header.span(badge -> badge.append(risk), className, Attribute.title(explanation));
                } else {
                    header.span(badge -> badge.append(risk), className);
                }
            }
            if (call.getDecision() != null) {
                header.span(decision -> decision.append(AiToolMessages.decisionLabel(call.getDecision(),
                                call.getNote())),
                        Attribute.className("ai-approval-decision ai-approval-decision--"
                                            + call.getDecision().toLowerCase()));
            }
        }, Attribute.className("ai-approval-call-header"));
        final String arguments = call.getArguments();
        // The arguments are shown in full, never cut short, so the user sees exactly what will be sent. A call
        // with none has no box.
        if (AiToolMessages.hasArguments(arguments)) {
            callDiv.elem(pre -> pre.append(AiToolMessages.prettyArguments(arguments)),
                    SafeHtmlUtils.fromSafeConstant("pre"), Attribute.className("ai-approval-args"));
        }
        if (pending && call.getDecision() == null) {
            callDiv.div(buttons -> {
                approvalButton(buttons, msg, "Allow once", true, AiApprovalScope.ONCE, call.getCallId());
                if (AiToolMessages.allowsForChat(json)) {
                    approvalButton(buttons, msg, "Allow in this chat", true, AiApprovalScope.CHAT,
                            call.getCallId());
                }
                if (AiToolMessages.allowsServerReadOnly(json)) {
                    approvalButton(buttons, msg, "Allow this server's read-only tools", true,
                            AiApprovalScope.SERVER_READ_ONLY, call.getCallId());
                }
                approvalButton(buttons, msg, "Deny", false, AiApprovalScope.ONCE, call.getCallId());
            }, Attribute.className("ai-approval-buttons"));
        }
    }

    private void approvalButton(final HtmlBuilder footer,
                                final AiChatMessage msg,
                                final String label,
                                final boolean approve,
                                final AiApprovalScope scope,
                                final String callId) {
        footer.elem(label, BUTTON,
                Attribute.className(approve
                        ? "allow-focus ai-approval-button ai-approval-button--approve"
                        : "allow-focus ai-approval-button ai-approval-button--deny"),
                new Attribute(APPROVAL_ID_ATTRIBUTE, String.valueOf(msg.getId())),
                new Attribute(APPROVAL_ANSWER_ATTRIBUTE, approve
                        ? "approve"
                        : "reject"),
                new Attribute(APPROVAL_SCOPE_ATTRIBUTE, scope.name()),
                new Attribute(APPROVAL_CALL_ATTRIBUTE, NullSafe.string(callId)));
    }

    /**
     * Append a collapsible details/summary element for thinking and data messages.
     * Uses native HTML {@code <details>/<summary>} so content is collapsed by default.
     */
    private void appendDetailsElement(final HtmlBuilder hb,
                                      final String cssClass,
                                      final SvgImage icon,
                                      final String summaryText,
                                      final String markdownText,
                                      final long timeMs,
                                      final long nowMs) {
        hb.elem(details -> {
            details.elem(summary -> {
                button(summary, icon, iconButtonClassName("ai-message-summary-icon"));
                summary.span(title -> title.append(summaryText), Attribute.className("ai-entry-title"));
            }, SUMMARY, Attribute.className("ai-message-header"));

            // Add markdown message.
            details.div(contentDiv ->
                            contentDiv.append(markdownConverter.convertUntrustedMarkdownToHtml(markdownText)),
                    Attribute.className("ai-details-content"));

            // Add timestamp footer.
            details.div(footer ->
                    timestamp(footer, timeMs, nowMs), Attribute.className("ai-message-footer"));
        }, DETAILS, Attribute.className(cssClass));
    }

    /**
     * Render an ATTACHMENT message with a status line that can be updated in-place
     * via a unique DOM ID.
     */
    private void appendAttachmentMessage(final HtmlBuilder hb,
                                         final AiChatMessage msg,
                                         final long timeMs,
                                         final long nowMs,
                                         final int messageId,
                                         final boolean deletable) {
        final Integer attachmentId = msg.getAttachmentId();
        hb.div(container -> {
            // Header with icon and description.
            container.div(header -> {
                final String description = NullSafe.getOrElse(
                        msg, AiChatMessage::getMessage, "Table attachment");
                button(header, SvgImage.TABLE,
                        iconButtonClassName("ai-attachment-icon"),
                        Attribute.title("View data"),
                        new Attribute("data-attachment-id", String.valueOf(attachmentId)),
                        new Attribute("data-attachment-desc", description));
                header.append(description);
            }, Attribute.className("ai-message-header"));

            // Status line — will be updated in-place by polling.
            if (attachmentId != null) {
                container.div(statusDiv ->
                                appendStatus(statusDiv, SvgImage.DOWNLOAD, "Downloading..."),
                        Attribute.className("ai-attachment-status"), new Attribute("id",
                                "ai-attachment-status-" +
                                attachmentId));
            }

            // Footer with timestamp and optional delete button.
            container.div(footer -> {
                // Add delete button.
                if (deletable && messageId > 0) {
                    button(footer,
                            SvgImage.DELETE,
                            iconButtonClassName("ai-message-delete"),
                            Attribute.title("Delete attachment"),
                            new Attribute("data-delete-message-id", String.valueOf(messageId)));
                }
                timestamp(footer, timeMs, nowMs);
            }, Attribute.className("ai-message-footer"));
        }, Attribute.className("ai-message ai-message--data"));
    }

    /**
     * Renders the WORKING message with stable DOM ids, so that polling can update the progress text
     * where it stands rather than appending a new line every time it changes. The text is in the
     * summary because that is the part that is visible without expanding it.
     */
    private void appendWorkingMessage(final HtmlBuilder hb,
                                      final AiChatMessage msg,
                                      final long timeMs,
                                      final long nowMs) {
        hb.elem(details -> {
            // Lined up with the turns' activity lines ("Worked for ..."), so no icon.
            details.elem(summary ->
                            summary.span(text -> text.append(workingText(msg)), new Attribute("id", WORKING_TEXT_ID)),
                    SUMMARY, Attribute.className("ai-message-header"));

            details.div(footer ->
                timestamp(footer, timeMs, nowMs), Attribute.className("ai-message-footer"));
        }, DETAILS, Attribute.className("ai-message ai-message--working"),
            new Attribute("id", WORKING_MESSAGE_ID));
    }

    /**
     * Shows the answer and reasoning the model is writing now. The answer shows as an answer in progress at the
     * end of the conversation, and stays until the stored answer replaces it, as text the model writes alongside
     * tool calls is worth seeing while they run. Reasoning shows in the turn's activity group.
     *
     * @param streaming The STREAMING_ANSWER and STREAMING_THINKING messages, or null.
     * @param complete  True if nothing more is coming.
     * @return True if anything is being streamed.
     */
    private boolean updateStreaming(final List<AiChatMessage> streaming, final boolean complete) {
        final Element markdownContainer = getView().getMarkdownContainer().getElement();
        boolean thinking = false;
        boolean any = false;
        if (streaming != null) {
            for (final AiChatMessage msg : streaming) {
                any = true;
                final SafeHtml html = markdownConverter.convertUntrustedMarkdownToHtml(msg.getMessage());
                if (msg.getMessageType() == AiMessageType.STREAMING_THINKING) {
                    thinking = true;
                    final AiChatMessage question = renderedMessages.get(msg.getTurnId());
                    activityGroups.showLiveThinking(markdownContainer, msg, html, question != null
                            ? question.getCreateTimeMs()
                            : 0);
                } else if (msg.getMessageType() == AiMessageType.STREAMING_ANSWER) {
                    showLiveAnswer(markdownContainer, html);
                }
            }
        }
        if (!thinking) {
            activityGroups.removeLiveThinking();
        }
        if (complete) {
            removeLiveAnswer();
            activityGroups.finishAll();
        }
        keepProgressInPlace(markdownContainer);
        if (any) {
            scrollToBottom();
        }
        return any;
    }

    private void showLiveAnswer(final Element markdownContainer, final SafeHtml html) {
        Element live = Document.get().getElementById(LIVE_ANSWER_ID);
        if (live == null) {
            final HtmlBuilder hb = new HtmlBuilder();
            hb.div(wrapper -> {
                wrapper.div(avatar -> {
                    icon(avatar, SvgImage.AI);
                    avatar.append("Stroom AI");
                }, Attribute.className("ai-message-avatar"));
                wrapper.div(content -> {
                }, Attribute.className("ai-message-content"));
            }, Attribute.className("ai-message ai-message--assistant ai-message--streaming"),
                    new Attribute("id", LIVE_ANSWER_ID));
            live = parseHtml(hb).getFirstChildElement();
            markdownContainer.appendChild(live);
        }
        final Element content = ElementUtil.findChild(live, "ai-message-content");
        if (content != null) {
            content.setInnerHTML(html.asString());
        }
        keepProgressInPlace(markdownContainer);
    }

    /**
     * Keeps the line saying what is being done just below the work of the turn being worked on, so it stays above
     * anything put to the user and anything they send while it works, and the answer being written at the end of
     * the conversation, as messages arrive.
     */
    private void keepProgressInPlace(final Element markdownContainer) {
        final Element working = Document.get().getElementById(WORKING_MESSAGE_ID);
        if (working != null && working.getParentElement() == markdownContainer) {
            final Element group = activityGroups.getLatestUnfinished();
            if (group != null && group.getParentElement() == markdownContainer) {
                if (group.getNextSibling() != working) {
                    markdownContainer.insertAfter(working, group);
                }
            } else if (working != markdownContainer.getLastChild()) {
                markdownContainer.appendChild(working);
            }
        }
        final Element live = Document.get().getElementById(LIVE_ANSWER_ID);
        if (live != null && live.getParentElement() == markdownContainer
            && live != markdownContainer.getLastChild()) {
            markdownContainer.appendChild(live);
        }
    }

    private static void removeLiveAnswer() {
        final Element live = Document.get().getElementById(LIVE_ANSWER_ID);
        if (live != null) {
            live.removeFromParent();
        }
    }

    /**
     * Adds, updates or removes the progress line to match what the server says it is doing.
     */
    private void updateWorkingMessage(final AiChatMessage workingMessage) {
        final Document doc = Document.get();
        final Element existing = doc.getElementById(WORKING_MESSAGE_ID);

        if (workingMessage == null) {
            // Nothing is being worked on, so the progress line has served its purpose.
            if (existing != null) {
                existing.removeFromParent();
            }
        } else if (existing == null) {
            final HtmlBuilder hb = new HtmlBuilder();
            appendWorkingMessage(hb, workingMessage,
                    workingMessage.getCreateTimeMs(), System.currentTimeMillis());
            appendToContainer(hb);
            keepProgressInPlace(getView().getMarkdownContainer().getElement());
        } else {
            final Element text = doc.getElementById(WORKING_TEXT_ID);
            if (text != null) {
                text.setInnerText(workingText(workingMessage));
            }
        }
        // What the model said is shown on the Working line until the line moves on to its next step, so it isn't
        // shown twice.
        if (interimShownAsProgress != null
            && (workingMessage == null
                || !AiChatTexts.isShownAsProgress(workingText(workingMessage), interimShownAsProgress.getMessage()))) {
            showInterimInActivity();
        }
    }

    /// Shows what the model said alongside its latest tool calls in its turn's activity group, if it was kept
    /// out while the Working line showed it.
    private void showInterimInActivity() {
        if (interimShownAsProgress == null) {
            return;
        }
        final Element element = activityGroups.findEntry(String.valueOf(interimShownAsProgress.getId()));
        interimShownAsProgress = null;
        if (element != null) {
            element.removeClassName(SHOWN_AS_PROGRESS_CLASS);
            scrollToBottom();
        }
    }

    private String workingText(final AiChatMessage msg) {
        return NullSafe.getOrElse(msg, AiChatMessage::getMessage, "Working...");
    }

    private void appendStatus(final HtmlBuilder hb, final SvgImage icon, final String text) {
        button(hb, icon, iconButtonClassName("ai-attachment-status-icon"));
        hb.div(status -> status.append(text), Attribute.className("ai-attachment-status-text"));
    }

    /**
     * Update existing attachment status elements in the DOM with current status.
     */
    private void updateAttachmentStatuses(final java.util.List<AiChatAttachment> attachments) {
        if (attachments == null) {
            return;
        }
        for (final AiChatAttachment attachment : attachments) {
            final Document doc = Document.get();
            final Element statusEl = doc.getElementById("ai-attachment-status-" + attachment.getId());
            if (statusEl != null) {
                statusEl.setInnerHTML(formatAttachmentStatus(attachment));
            }
        }
    }

    /**
     * Format a human-readable status string with an appropriate icon for the
     * given attachment status.
     */
    private String formatAttachmentStatus(final AiChatAttachment attachment) {
        final HtmlBuilder hb = new HtmlBuilder();
        switch (attachment.getStatus()) {
            case PENDING -> appendStatus(hb, SvgImage.DOWNLOAD, "Pending...");
            case DOWNLOADING -> appendStatus(hb, SvgImage.DOWNLOAD, "Downloading...");
            case READY -> {
                final StringBuilder sb = new StringBuilder();
                sb.append(" Ready");
                if (attachment.getRowCount() != null) {
                    sb.append(" --- ").append(attachment.getRowCount()).append(" rows");
                }
                if (attachment.isTruncated()) {
                    sb.append(" (truncated to limit)");
                }
                appendStatus(hb, SvgImage.OK, sb.toString());
            }
            case ERROR -> appendStatus(hb, SvgImage.ERROR, "Error: " +
                                                           NullSafe.getOrElse(attachment,
                                                                   AiChatAttachment::getErrorMessage,
                                                                   "Unknown error"));
        }
        return hb.toSafeHtml().asString();
    }

    private static void icon(final HtmlBuilder hb, final SvgImage icon) {
        hb.div(div -> div.appendTrustedString(icon.getSvg()),
                Attribute.className("svgIcon " + icon.getClassName()));
    }

    private static Attribute iconButtonClassName(final String className) {
        return Attribute.className(className + " icon-button");
    }

    private static void button(final HtmlBuilder hb,
                               final SvgImage icon,
                               final Attribute... attributes) {
        hb.elem(button -> button.div(div -> div.appendTrustedString(icon.getSvg()),
                        Attribute.className("svg-image " + icon.getClassName() + " face")),
                BUTTON,
                attributes);
    }

    private void timestamp(final HtmlBuilder html, final long timeMs, final long nowMs) {
        html.div(timestamp -> timestamp.append(dateTimeFormatter.formatRelative(timeMs, nowMs)),
                Attribute.className("ai-message-timestamp"),
                Attribute.title(dateTimeFormatter.format(timeMs)));
    }

    /**
     * Set the content of a copy button to an SVG icon + label.
     */
    private static void setCopyButtonContent(final HtmlBuilder button,
                                             final SvgImage icon,
                                             final String label) {
        icon(button, icon);
        button.div(text -> text.append(label), Attribute.className("ai-message-copy-label"));
    }

    @Override
    public void onNewChat() {
        setChatListShowing(false);
        if (isCurrentChatUnused()) {
            // It is new already, so use it rather than leave an unused chat behind.
            getView().focus();
        } else {
            startNewChat();
        }
    }

    /// @return True if the chat being shown has nothing in it, so is as good as a new one.
    private boolean isCurrentChatUnused() {
        return currentChat != null && getView().isEmptyState() && !currentRunActive;
    }

    private void startNewChat() {
        startNewChat(() -> {
        });
    }

    /// Starts a new chat, then does something with it, e.g. asks its first question.
    private void startNewChat(final Runnable then) {
        askStroomAiClient.createChat(chat -> {
            currentChat = chat;
            currentRunActive = false;
            refreshFollowUps();
            titleGenerated = false;
            lastSeenMessageId = 0;
            data = null;
            clearRenderedMessages();
            getView().setEmptyState(true);
            getView().clearContextIndicator();
            setChatTitle(chat.getTitle());
            // A new chat works in the mode last chosen.
            if (getView().getAgentMode() != AiAgentMode.AGENT) {
                askStroomAiClient.setAgentMode(chat.getId(), getView().getAgentMode(), this);
            }
            refreshAutoApprove();
            getView().focus();
            then.run();
        }, this);
    }

    @Override
    public void onAgentModeChange(final AiAgentMode mode) {
        if (currentChat != null) {
            askStroomAiClient.setAgentMode(currentChat.getId(), mode, this);
        }
    }

    @Override
    public void onToggleAutoApprove() {
        if (currentChat == null) {
            return;
        }
        final AiChat chat = currentChat;
        askStroomAiClient.listToolApprovals(chat.getId(), response -> {
            if (response.isApproveEverything()) {
                setAutoApprove(chat, false);
            } else if (!response.isApproveEverythingAllowed()) {
                AlertEvent.fireInfo(this, "Auto-approve is not allowed. An administrator can allow it with "
                                          + "the 'allowApproveEverything' property.", null);
            } else {
                final String question = "Auto-approve in this chat?\n"
                                        + "Trusted servers' tools that modify data already run without asking, "
                                        + "until the chat reads an untrusted MCP server's output.\n"
                                        + "With auto-approve, they keep running without asking after that.\n"
                                        + "Destructive tools, and ones that reach outside Stroom, still ask.";
                ConfirmEvent.fire(this, question, ok -> {
                    if (ok) {
                        setAutoApprove(chat, true);
                    }
                });
            }
        }, error -> showError(error, "Unable to check auto-approve", null), this);
    }

    private void setAutoApprove(final AiChat chat, final boolean on) {
        askStroomAiClient.setApproveEverything(new SetAiApproveEverythingRequest(chat, on), done -> {
            refreshAutoApprove();
            checkChatStatuses();
        }, error -> showError(error, "Unable to change auto-approve", null), this);
    }

    /**
     * Shows whether auto-approve is on in the current chat, and whether it may be turned on.
     */
    private void refreshAutoApprove() {
        if (currentChat == null) {
            getView().setAutoApprove(false, true);
            return;
        }
        final int chatId = currentChat.getId();
        askStroomAiClient.listToolApprovals(chatId, response -> {
            if (currentChat != null && currentChat.getId() == chatId) {
                getView().setAutoApprove(response.isApproveEverything(), response.isApproveEverythingAllowed());
            }
        }, error -> {
            // Leave the button as it is; the user sees any problem if they click it.
        }, SILENT);
    }

    private void onDownloadChat() {
        if (currentChat == null) {
            return;
        }
        final DownloadChatPresenter dlPresenter = downloadChatPresenterProvider.get();
        ShowPopupEvent.builder(dlPresenter)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .caption("Download Options")
                .onShow(e -> dlPresenter.getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final DownloadChatHistoryRequest request = new DownloadChatHistoryRequest(
                                currentChat.getId(),
                                dlPresenter.isIncludeDataContexts(),
                                dlPresenter.isIncludeActivity());
                        askStroomAiClient.downloadChatHistory(request,
                                result -> ExportFileCompleteUtil.onSuccess(locationManager, this, result),
                                error -> showError(error, "Failed to download chat history", null),
                                this);
                    }
                    e.hide();
                })
                .fire();
    }

    private void onViewAttachmentData(final int attachmentId, final String description) {
        if (currentChat == null) {
            return;
        }
        final AiAttachmentDataPresenter presenter = aiAttachmentDataPresenterProvider.get();
        presenter.show(currentChat.getId(), attachmentId, description);
    }

    private void onDeleteMessage(final int messageId) {
        if (currentChat == null) {
            return;
        }
        ConfirmEvent.fire(this,
                "Are you sure you want to delete this message?",
                ok -> {
                    if (ok) {
                        askStroomAiClient.deleteMessage(
                                currentChat.getId(), messageId,
                                success -> reloadCurrentChat(),
                                getView());
                    }
                });
    }

    /**
     * Deletes the chat, after asking, and starts a new one in its place.
     */
    private void onDeleteChat() {
        if (currentChat != null) {
            deleteChat(currentChat);
        }
    }

    /**
     * Deletes a chat, after asking. If it is the one being looked at, a new chat is started in its place.
     */
    private void deleteChat(final AiChat chat) {
        ConfirmEvent.fire(this, AiChatTexts.deleteChatQuestion(chat.getTitle(), isBusy(chat.getId())),
                ok -> {
                    if (ok) {
                        askStroomAiClient.deleteChat(chat.getId(), success -> {
                            // Only move on if the user is still looking at the chat that was deleted.
                            if (currentChat != null && currentChat.getId() == chat.getId()) {
                                startNewChat();
                            }
                            checkChatStatuses();
                        }, getView());
                    }
                });
    }

    private boolean isBusy(final int chatId) {
        // The current chat's run is known from its last poll, which is more up to date than the statuses.
        if (currentRunActive && currentChat != null && currentChat.getId() == chatId) {
            return true;
        }
        for (final AiChatStatus status : chatStatuses) {
            if (status.getChatId() == chatId) {
                return status.isBusy() || status.isNeedsYou();
            }
        }
        return false;
    }

    private void reloadCurrentChat() {
        if (currentChat != null) {
            loadChat(currentChat);
        }
    }

    private void loadChat(final AiChat chat) {
        currentChat = chat;
        currentRunActive = false;
        refreshFollowUps();
        // A loaded chat already has a title, unless it has no question yet (see below).
        titleGenerated = true;
        lastSeenMessageId = 0;
        data = null;
        clearRenderedMessages();
        getView().clearContextIndicator();
        setChatTitle(chat.getTitle());
        getView().setAgentMode(findStatus(chat.getId())
                .map(AiChatStatus::getAgentMode)
                .orElse(AiAgentMode.AGENT));
        refreshAutoApprove();

        // Load messages for the selected chat.
        askStroomAiClient.getMessages(chat.getId(), messages -> {
            final long nowMs = System.currentTimeMillis();
            // A chat with no question yet (e.g. a new chat reopened after a refresh) is named by its first.
            if (NullSafe.list(messages).stream()
                    .noneMatch(msg -> msg.getMessageType() == AiMessageType.USER_MESSAGE)) {
                titleGenerated = false;
            }
            if (messages != null && !messages.isEmpty()) {
                getView().setEmptyState(false);
                for (final AiChatMessage msg : messages) {
                    appendMessage(msg, nowMs);
                    lastSeenMessageId = Math.max(lastSeenMessageId, msg.getId());
                }
            } else {
                getView().setEmptyState(true);
            }
            // Scroll to bottom after loading all messages.
            scrollToBottom();
            getView().focus();

            // Fetch attachment statuses to update status elements rendered above.
            // For historical chats, attachments are already in their final state (READY/ERROR).
            // Also picks up an agent run that is still working on a question, e.g. after the page was
            // refreshed, and carries on following it.
            askStroomAiClient.pollMessages(chat.getId(), lastSeenMessageId, isLooking(), null, response -> {
                updateAttachmentStatuses(response.getAttachments());
                updateRunStatus(response.getRun());
                currentRunActive = !response.isComplete();
                refreshFollowUps();
                if (!response.isComplete() && currentChat != null && currentChat.getId() == chat.getId()) {
                    getView().setSendButtonLoadingState(true);
                    startPolling();
                }
            }, error -> { /* ignore */ }, this);
        }, this);
    }

    /**
     * Ensure a chat exists, creating one if necessary, then run the callback.
     */
    private void ensureChat(final Runnable then) {
        if (currentChat != null) {
            then.run();
        } else {
            askStroomAiClient.createChat(chat -> {
                currentChat = chat;
                titleGenerated = false;
                lastSeenMessageId = 0;
                setChatTitle(chat.getTitle());
                then.run();
            }, this);
        }
    }

    /**
     * Auto-generate a conversation title from the first user message.
     * Only fires once per conversation.
     */
    private void maybeGenerateTitle(final String userMessage) {
        if (titleGenerated || currentChat == null || userMessage == null) {
            return;
        }
        titleGenerated = true;

        final String title;
        if (userMessage.length() <= MAX_TITLE_LENGTH) {
            title = userMessage;
        } else {
            title = userMessage.substring(0, MAX_TITLE_LENGTH) + "…";
        }

        // Only shown here: the server names the chat after its first question, and later after what it is about.
        // Saving it from here would count as the user naming it, which stops the automatic names.
        setChatTitle(title);
    }

    /**
     * Returns a human-readable title for the given context type.
     */
    private String getContextTitle(final AskStroomAiContext context) {
        return context.getDescription();
    }

    public interface AskStroomAiView extends View, Focus, TaskMonitorFactory, HasUiHandlers<AskStroomAiUiHandlers> {

        void setModelRefSelection(View view);

        SimplePanel getMarkdownContainer();

        String getMessage();

        void setSendButtonLoadingState(final boolean enabled);

        /// Shows the Stop button while anything is in progress for the chat, e.g. search results still
        /// downloading to attach, as well as while a question is worked on.
        ///
        /// @param inProgress True if anything is in progress.
        void setInProgress(boolean inProgress);

        void setTitle(String title);

        void clearMessages();

        void setEmptyState(boolean empty);

        /// @return True if the chat being shown has nothing in it yet.
        boolean isEmptyState();

        /// Shows things the user might ask next, as links above the input, or none.
        void setFollowUps(List<String> suggestions);

        /// Shows the agent's task list above the input.
        ///
        /// @param heading Its heading, e.g. "Tasks 2/5", or null to hide it.
        /// @param items   The tasks.
        void setTaskList(String heading, SafeHtml items);

        /// Shows a command's reference above the input, until hidden.
        void showCommandReference(SafeHtml html);

        /// Hides the command reference.
        void hideCommandReference();

        /// @return The panel the list of chats is shown in.
        FlowPanel getChatList();

        /// @return Where the buttons above the list of chats go.
        HTML getChatListActions();

        /// @return Where the chats in the list go.
        HTML getChatListItems();

        /// Empties the box for searching the list of chats.
        void clearChatSearch();

        /// Puts the cursor in the box for searching the list of chats.
        void focusChatSearch();

        /// Shows the list of chats in place of the conversation, or the conversation again.
        void setChatListVisible(boolean visible);

        /// @return How the agent is to work in the chat.
        AiAgentMode getAgentMode();

        /// @param mode How the agent works in the chat.
        void setAgentMode(AiAgentMode mode);

        /// @param on      True if auto-approve is on in the chat.
        /// @param allowed True if an administrator allows it to be turned on.
        void setAutoApprove(boolean on, boolean allowed);

        void setContextIndicator(SvgImage icon, String text);

        void clearContextIndicator();

        /**
         * Shows or hides the button that scrolls to the latest message.
         *
         * @param visible True to show it.
         */
        void setJumpToLatestVisible(boolean visible);

        /**
         * Shows how much of the model's context window the chat uses, or hides the meter.
         *
         * @param text  E.g. "42% · 54k / 128k", or null to hide the meter.
         * @param title The meter's tooltip.
         * @param level "warn" near the point where the chat is compacted, "over" past it, or null.
         */
        void setContextMeter(String text, SafeHtml details, String level);

        /**
         * Shows commands that match what the user is typing, or hides them.
         *
         * @param commands The commands, or null/empty to hide them.
         */
        void setCommandSuggestions(List<AiSlashCommand> commands);

        /**
         * Puts text in the input box, for the user to check and send.
         *
         * @param text The text.
         */
        void setMessage(String text);

        /**
         * @param tab True if the chat is shown in a content tab, so the tab/panel button moves it back.
         */
        void setTabMode(boolean tab);
    }

    // ---- Dock preference helpers ----

    private void getDockBehaviourFromPrefs(final Consumer<DockBehaviour> consumer) {
        askStroomAiClient.getConfig(currentConfig -> {
            if (currentConfig != null) {
                final DockType type = currentConfig.getDockType() == null
                                      || currentConfig.getDockType() == DockType.FLOAT
                        ? AskStroomAiConfig.DEFAULT_DOCK_TYPE
                        : currentConfig.getDockType();
                final DockLocation location = Objects.requireNonNullElse(currentConfig.getDockLocation(),
                        AskStroomAiConfig.DEFAULT_DOCK_LOCATION);
                consumer.accept(new DockBehaviour(type, location));
            } else {
                consumer.accept(DEFAULT_DOCK_BEHAVIOUR);
            }
        }, this);
    }

    private void saveDockBehaviourToPrefs(final DockBehaviour behaviour) {
        askStroomAiClient.getConfig(currentConfig -> {
            final AskStroomAiConfig newConfig = currentConfig.copy()
                    .dockType(behaviour.getDockType())
                    .dockLocation(behaviour.getDockLocation())
                    .build();
            askStroomAiClient.setConfig(newConfig, this);
        }, this);
    }

    private void saveDockSizeToPrefs(final int size) {
        askStroomAiClient.getConfig(currentConfig -> {
            final AskStroomAiConfig newConfig = currentConfig.copy()
                    .dockSize(size)
                    .build();
            askStroomAiClient.setConfig(newConfig, this);
        }, this);
    }

    private void getDockSize(final Consumer<Size> consumer) {
        askStroomAiClient.getConfig(currentConfig -> {
            getDockBehaviourFromPrefs(dockBehaviour -> {
                final DockLocation loc = dockBehaviour.getDockLocation();
                final int defaultSize;
                if (loc == DockLocation.LEFT || loc == DockLocation.RIGHT) {
                    defaultSize = DEFAULT_DOCK_WIDTH;
                } else {
                    defaultSize = DEFAULT_DOCK_HEIGHT;
                }
                final int size = NullSafe.getOrElse(currentConfig, AskStroomAiConfig::getDockSize, defaultSize);
                consumer.accept(new Size.Builder()
                        .width(size)
                        .height(size)
                        .build());
            });
        }, this);
    }

    private void showAsDialog() {
        shownAs = DockType.DIALOG;
        ShowPopupEvent.builder(this)
                .popupType(PopupType.CLOSE_DIALOG)
                .popupSize(PopupSize.resizable(700, 500))
                .caption("Ask Stroom AI")
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    // Notify MainPresenter so the toolbar toggle button is reset.
                    ShowAskStroomAiEvent.fire(this, false);
                })
                .onHide(e -> {
                    if (shownAs == DockType.DIALOG) {
                        shownAs = null;
                    }
                })
                .fire();
    }

    public static class DockBehaviour {

        private final DockType dockType;
        private final DockLocation dockLocation;

        public DockBehaviour(final DockType dockType,
                             final DockLocation dockLocation) {
            this.dockType = dockType;
            this.dockLocation = dockLocation;
        }

        public DockType getDockType() {
            return dockType;
        }

        public DockLocation getDockLocation() {
            return dockLocation;
        }

        @Override
        public boolean equals(final Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final DockBehaviour that = (DockBehaviour) o;
            return dockType == that.dockType &&
                   dockLocation == that.dockLocation;
        }

        @Override
        public int hashCode() {
            return Objects.hash(dockType, dockLocation);
        }
    }
}
