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
import stroom.ai.shared.AiApprovalScope;
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatAttachment;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiChatRun;
import stroom.ai.shared.AiChatRunStatus;
import stroom.ai.shared.AiChatStatus;
import stroom.ai.shared.AiContextUsage;
import stroom.ai.shared.AiMcpPrompt;
import stroom.ai.shared.AiMcpPromptArgument;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.AnswerAiApprovalRequest;
import stroom.ai.shared.AskStroomAiConfig;
import stroom.ai.shared.AskStroomAiConfig.DockLocation;
import stroom.ai.shared.AskStroomAiConfig.DockType;
import stroom.ai.shared.AskStroomAiContext;
import stroom.ai.shared.AskStroomAiRequest;
import stroom.ai.shared.CompactAiChatRequest;
import stroom.ai.shared.DownloadChatHistoryRequest;
import stroom.ai.shared.EditAiMessageRequest;
import stroom.ai.shared.GetAiMcpPromptRequest;
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
import stroom.dispatch.client.ExportFileCompleteUtil;
import stroom.dispatch.client.RestError;
import stroom.entity.client.presenter.MarkdownConverter;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.main.client.event.DockEvent;
import stroom.main.client.event.DockResizeEvent;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.preferences.client.DateTimeFormatter;
import stroom.security.shared.DocumentPermission;
import stroom.svg.shared.SvgImage;
import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.ClipboardUtil;
import stroom.util.shared.NullSafe;
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
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.dom.client.TextAreaElement;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Focus;
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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public class AskStroomAiPresenter
        extends MyPresenter<AskStroomAiView, AskStroomAiProxy>
        implements AskStroomAiUiHandlers, ShowAskStroomAiEvent.Handler, AskStroomAiEvent.Handler {

    private static final int ACTIVE_POLL_MS = 1_000;
    private static final int HIDDEN_POLL_MS = 5_000;
    private static final int STATUS_POLL_MS = 15_000;
    private static final int HIDDEN_STATUS_POLL_MS = 60_000;
    private static final int MAX_SWITCHER_CHATS = 15;
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
    private static final int MAX_TITLE_LENGTH = 60;
    private static final SafeHtml SUMMARY = SafeHtmlUtils.fromSafeConstant("summary");
    private static final SafeHtml DETAILS = SafeHtmlUtils.fromSafeConstant("details");
    private static final SafeHtml BUTTON = SafeHtmlUtils.fromSafeConstant("button");
    private static final String WORKING_MESSAGE_ID = "ai-working-message";
    private static final String WORKING_TEXT_ID = "ai-working-text";
    private static final String MESSAGE_ID_ATTRIBUTE = "data-message-id";
    private static final String RUN_STATUS_ID = "ai-run-status";
    private static final String RESUME_RUN_ATTRIBUTE = "data-resume-run";
    private static final String RETRY_TURN_ATTRIBUTE = "data-retry-turn-id";
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
    private static final int DEFAULT_COMPACT_AT_PERCENT = 80;
    private static final int WARN_BELOW_COMPACT_AT_PERCENT = 10;
    private static final String COMPACT_COMMAND = "/compact";
    private static final List<AiSlashCommand> SLASH_COMMANDS = List.of(
            new AiSlashCommand(COMPACT_COMMAND, "[focus]",
                    "Compact the conversation now, optionally saying what the summary should keep"));
    private static final String LIVE_ANSWER_ID = "ai-live-answer";
    private static final int STREAMING_POLL_MS = 500;
    private static final long KEEP_POLLING_AFTER_ANSWER_MS = 15_000;
    private static final int BOTTOM_TOLERANCE_PX = 40;

    private final DocSelectionBoxPresenter docSelectionBoxPresenter;
    private final MarkdownConverter markdownConverter;
    private final AskStroomAiClient askStroomAiClient;
    private final Provider<AskStroomAiConfigPresenter> askStroomAiConfigPresenterProvider;
    private final Provider<AiChatHistoryPresenter> aiChatHistoryPresenterProvider;
    private final Provider<DownloadChatPresenter> downloadChatPresenterProvider;
    private final Provider<AiMemoryPresenter> memoryPresenterProvider;
    private final Provider<AiToolApprovalsPresenter> toolApprovalsPresenterProvider;
    private final Provider<McpElicitationPresenter> elicitationPresenterProvider;
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
    // The prompts the model's MCP servers offer, as slash commands, once fetched.
    private List<AiMcpPrompt> mcpPrompts;
    private boolean fetchingPrompts;
    // How much of the model's context window the chat uses, as last reported.
    private AiContextUsage contextUsage;

    @Inject
    public AskStroomAiPresenter(final EventBus eventBus,
                                final AskStroomAiView view,
                                final AskStroomAiProxy askStroomAiProxy,
                                final DocSelectionBoxPresenter docSelectionBoxPresenter,
                                final MarkdownConverter markdownConverter,
                                final AskStroomAiClient askStroomAiClient,
                                final Provider<AskStroomAiConfigPresenter> askStroomAiConfigPresenterProvider,
                                final Provider<AiChatHistoryPresenter> aiChatHistoryPresenterProvider,
                                final Provider<DownloadChatPresenter> downloadChatPresenterProvider,
                                final Provider<AiMemoryPresenter> memoryPresenterProvider,
                                final Provider<AiToolApprovalsPresenter> toolApprovalsPresenterProvider,
                                final Provider<McpElicitationPresenter> elicitationPresenterProvider,
                                final Provider<AiAttachmentDataPresenter> aiAttachmentDataPresenterProvider,
                                final Provider<AskStroomAiTabPresenter> askStroomAiTabPresenterProvider,
                                final ContentManager contentManager,
                                final LocationManager locationManager,
                                final DateTimeFormatter dateTimeFormatter) {
        super(eventBus, view, askStroomAiProxy);
        this.markdownConverter = markdownConverter;
        this.askStroomAiClient = askStroomAiClient;
        this.askStroomAiConfigPresenterProvider = askStroomAiConfigPresenterProvider;
        this.aiChatHistoryPresenterProvider = aiChatHistoryPresenterProvider;
        this.downloadChatPresenterProvider = downloadChatPresenterProvider;
        this.memoryPresenterProvider = memoryPresenterProvider;
        this.toolApprovalsPresenterProvider = toolApprovalsPresenterProvider;
        this.elicitationPresenterProvider = elicitationPresenterProvider;
        this.aiAttachmentDataPresenterProvider = aiAttachmentDataPresenterProvider;
        this.askStroomAiTabPresenterProvider = askStroomAiTabPresenterProvider;
        this.contentManager = contentManager;
        this.locationManager = locationManager;
        this.docSelectionBoxPresenter = docSelectionBoxPresenter;
        this.dateTimeFormatter = dateTimeFormatter;

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

        registerHandler(getView().getMarkdownContainer().addDomHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                final Element target = e.getNativeEvent().getEventTarget().cast();
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

                    // Answering a form from an MCP tool.
                    final String elicitationStr = button.getAttribute(ELICITATION_ATTRIBUTE);
                    if (NullSafe.isNonBlankString(elicitationStr)) {
                        onRespondToForm(Integer.parseInt(elicitationStr));
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
                            // Code to change label
                            final Element copyIcon = ElementUtil.findChild(button, "svgIcon");
                            final Element copyLabel = ElementUtil.findChild(button, "ai-message-copy-label");
                            if (copyLabel != null) {
                                copyIcon.setInnerHTML(SvgImage.OK.getSvg());
                                copyLabel.setInnerHTML("Copied");
                                new com.google.gwt.user.client.Timer() {
                                    @Override
                                    public void run() {
                                        copyIcon.setInnerHTML(SvgImage.COPY.getSvg());
                                        copyLabel.setInnerHTML("Copy");
                                    }
                                }.schedule(2000);
                            }
                        }
                    }
                }
            }
        }, ClickEvent.getType()));

        // In a question being edited, Escape cancels the edit and Ctrl+Enter sends it.
        registerHandler(getView().getMarkdownContainer().addDomHandler(e -> {
            final Element target = e.getNativeEvent().getEventTarget().cast();
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

    @Override
    public void onShowMenu(final Element target) {
        final boolean hasChat = currentChat != null;
        final List<Item> items = MenuBuilder.builder()
                .withIconMenuItem(item -> item
                        .icon(SvgImage.EDIT)
                        .text("Rename…")
                        .enabled(hasChat)
                        .command(this::onRename))
                .withIconMenuItem(item -> item
                        .icon(SvgImage.COLLAPSE_UP)
                        .text("Compact Context…")
                        .enabled(hasChat)
                        .command(this::onCompact))
                .withIconMenuItem(item -> item
                        .icon(SvgImage.INFO)
                        .text("Memory…")
                        .enabled(hasChat)
                        .command(() -> memoryPresenterProvider.get().show(currentChat)))
                .withIconMenuItem(item -> item
                        .icon(SvgImage.OK)
                        .text("Tool Approvals…")
                        .enabled(hasChat)
                        .command(() -> toolApprovalsPresenterProvider.get().show(currentChat)))
                .withIconMenuItem(item -> item
                        .icon(SvgImage.DOWNLOAD)
                        .text("Download History…")
                        .enabled(hasChat)
                        .command(this::onDownloadChat))
                .withIconMenuItem(item -> item
                        .icon(SvgImage.DELETE)
                        .text("Delete All Messages")
                        .enabled(hasChat)
                        .command(this::onDeleteAllMessages))
                .withSeparator()
                .withIconMenuItem(item -> item
                        .icon(SvgImage.SETTINGS)
                        .text("Settings…")
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
                fireBadge();
            }
            statusTimer.schedule(DocumentVisibility.isHidden()
                    ? HIDDEN_STATUS_POLL_MS
                    : STATUS_POLL_MS);
        }, SILENT);
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
        BrowserNotifications.setTitleCount(needsYou + unread);
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
        final List<Item> items = new ArrayList<>();
        int priority = 0;
        for (final AiChatStatus status : chatStatuses) {
            if (items.size() >= MAX_SWITCHER_CHATS) {
                break;
            }
            final boolean current = currentChat != null && currentChat.getId() == status.getChatId();
            items.add(new IconMenuItem.Builder()
                    .priority(priority++)
                    .icon(switcherIcon(status))
                    .highlight(current)
                    .text(NullSafe.nonBlankStringElse(status.getTitle(), "New conversation") + switcherNote(status))
                    .command(() -> loadChat(new AiChat(status.getChatId(), 0L, status.getUpdateTimeMs(), null,
                            status.getTitle())))
                    .build());
        }
        if (!items.isEmpty()) {
            items.add(new Separator(priority++));
        }
        items.add(new IconMenuItem.Builder()
                .priority(priority++)
                .icon(SvgImage.ADD)
                .text("New Conversation")
                .command(this::onNewChat)
                .build());
        items.add(new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.HISTORY)
                .text("All Conversations…")
                .command(this::onShowHistory)
                .build());
        ShowMenuEvent.builder()
                .items(items)
                .popupPosition(new PopupPosition(new Rect(target).grow(3), PopupLocation.BELOW))
                .fire(this);
    }

    private static SvgImage switcherIcon(final AiChatStatus status) {
        if (status.isNeedsYou()) {
            return SvgImage.ALERT;
        } else if (status.isBusy()) {
            return SvgImage.SPINNER_SPINNING;
        } else if (status.getRunStatus() == AiChatRunStatus.FAILED) {
            return SvgImage.ERROR;
        } else if (status.getRunStatus() == AiChatRunStatus.INTERRUPTED
                   || status.getRunStatus() == AiChatRunStatus.STOPPED) {
            return SvgImage.PAUSE;
        } else if (status.isUnread()) {
            return SvgImage.DOT;
        }
        return SvgImage.AI;
    }

    private static String switcherNote(final AiChatStatus status) {
        if (status.isNeedsYou()) {
            return " - needs you";
        } else if (status.getRunStatus() == AiChatRunStatus.QUEUED) {
            return " - queued";
        } else if (status.isBusy()) {
            return " - working";
        } else if (status.getRunStatus() == AiChatRunStatus.FAILED) {
            return " - failed";
        } else if (status.getRunStatus() == AiChatRunStatus.INTERRUPTED) {
            return " - interrupted";
        } else if (status.isUnread()) {
            return " - unread";
        }
        return status.isApproveEverything()
                ? " - approving everything"
                : "";
    }

    private void onRename() {
        if (currentChat == null) {
            return;
        }
        PromptEvent.fire(this, "Rename Conversation", "Name for this conversation:", currentChat.getTitle(), title -> {
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
        final List<AiSlashCommand> commands = new ArrayList<>(SLASH_COMMANDS);
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
            commands.add(new AiSlashCommand(prompt.getCommand(), arguments.toString(),
                    NullSafe.string(prompt.getDescription()) + " (from " + prompt.getServer() + ")"));
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
        if (mcpPrompts == null && !COMPACT_COMMAND.equals(parts[0])) {
            // It may be an MCP prompt, so fetch them and try again.
            fetchPrompts(() -> runSlashCommand(message));
            return true;
        }
        if (COMPACT_COMMAND.equals(parts[0])) {
            if (currentChat == null) {
                AlertEvent.fireWarn(this, "There is nothing to compact yet.", null);
            } else {
                compact(parts.length > 1
                        ? parts[1].strip()
                        : null);
            }
            return true;
        }
        final StringBuilder known = new StringBuilder();
        for (final AiSlashCommand command : slashCommands()) {
            known.append("\n").append(command.getName()).append(" ").append(command.getArguments());
        }
        AlertEvent.fireWarn(this, "Unknown command " + parts[0] + ". The commands are:" + known
                                  + "\n\nTo send a message starting with /, start it with //.", null);
        return true;
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
        if (percent < 0) {
            getView().setContextMeter(approx + kilo(usage.getUsedTokens()) + " tokens",
                    "How many tokens of context this chat uses. The model's context window is not known yet. "
                    + "Click for details.", null);
            return;
        }
        final int compactAt = usage.getCompactAtPercent() > 0
                ? usage.getCompactAtPercent()
                : DEFAULT_COMPACT_AT_PERCENT;
        final String level = percent >= compactAt
                ? "over"
                : percent >= compactAt - WARN_BELOW_COMPACT_AT_PERCENT
                        ? "warn"
                        : null;
        getView().setContextMeter(approx + percent + "% · " + kilo(usage.getUsedTokens()) + " / "
                                  + kilo(usage.getContextWindowTokens()),
                "How much of the model's context window this chat uses. Earlier messages are compacted "
                + "automatically at " + compactAt + "%. Click for details.", level);
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
     * Renders a form an MCP tool asked the user to fill in: what the server said, as plain text, and while it
     * waits, a Respond… button; once answered, what became of it.
     */
    private void appendElicitationCard(final HtmlBuilder hb,
                                       final AiChatMessage msg,
                                       final long timeMs,
                                       final long nowMs) {
        final String json = msg.getMessage();
        final String status = NullSafe.string(AiToolMessages.getString(json, "status"));
        final String server = AiToolMessages.getString(json, "server");
        final boolean pending = "PENDING".equals(status);
        hb.div(card -> {
            card.div(title -> title.append(pending
                    ? server + " needs your input"
                    : "A form from " + server), Attribute.className("ai-approval-title"));
            card.div(text -> text.append(NullSafe.string(AiToolMessages.getString(json, "message"))),
                    Attribute.className("ai-elicitation-message"));
            card.div(footer -> {
                if (pending) {
                    footer.elem("Respond…", BUTTON,
                            Attribute.className("allow-focus ai-approval-button ai-approval-button--approve"),
                            new Attribute(ELICITATION_ATTRIBUTE, String.valueOf(msg.getId())));
                } else {
                    footer.span(text -> text.append(elicitationStatusText(status)),
                            Attribute.className("ai-approval-reason"));
                }
                timestamp(footer, timeMs, nowMs);
            }, Attribute.className("ai-message-footer"));
        }, Attribute.className("ai-message ai-message--approval" + (pending
                ? " ai-message--needs-you"
                : "")));
    }

    private static String elicitationStatusText(final String status) {
        switch (status) {
            case "ACCEPTED":
                return "You answered it.";
            case "DECLINED":
                return "You declined it.";
            case "CANCELLED":
                return "Not answered, as the run was stopped.";
            case "EXPIRED":
                return "Not answered in time.";
            default:
                return status;
        }
    }

    private void onRespondToForm(final int messageId) {
        final AiChatMessage msg = renderedMessages.get(messageId);
        if (msg == null || currentChat == null) {
            return;
        }
        elicitationPresenterProvider.get().show(currentChat, msg, status -> {
            appendMessage(new AiChatMessage(msg.getId(), msg.getChatId(), msg.getCreateTimeMs(),
                    msg.getMessageType(), msg.getAttachmentId(), AiToolMessages.withStatus(msg.getMessage(), status),
                    msg.getTurnId(), msg.getTokenCount()), System.currentTimeMillis());
            // The tool carries on with the answer.
            pollAfterAnswer();
        });
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
                            Attribute.title("Undo this compaction, so the model sees the earlier messages in full "
                                            + "again"),
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
        final String question = "Compact this conversation now? Earlier messages are kept, but the model will see "
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
                error -> showError(error, "Unable to compact the conversation", null),
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
                    currentChat, config, data, message);
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
     * Shows that a copy button has copied, for a moment.
     */
    private static void flashCopied(final Element button) {
        final Element copyLabel = ElementUtil.findChild(button, "ai-message-copy-label");
        if (copyLabel == null) {
            return;
        }
        final String label = copyLabel.getInnerText();
        copyLabel.setInnerText("Copied");
        new Timer() {
            @Override
            public void run() {
                copyLabel.setInnerText(label);
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
        final String warning = (later == 0
                ? "Editing will ask this question again."
                : "Editing will remove " + later + " later message" + (later == 1
                        ? ""
                        : "s") + " and ask this question again.")
                               + (sideEffects
                ? " Changes the agent has made with tools, or to your memory, will not be undone."
                : "");
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
     * Answers a request for approval. Denying tool calls first asks the user why, which the model is told.
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
        if (!approved && AiToolMessages.isToolRequest(request.getMessage())) {
            PromptEvent.fire(this, "Deny", "Why not? This is optional, and the model is told.", "", reason -> {
                // Cancelling the prompt leaves the request unanswered.
                if (reason != null) {
                    onAnswerApproval(messageId, false, scope, callId, reason.isBlank()
                            ? null
                            : reason.strip());
                }
            });
        } else {
            onAnswerApproval(messageId, approved, scope, callId, null);
        }
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
                final int id = Integer.parseInt(idStr);
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
        askStroomAiClient.pollMessages(chatId, lastSeenMessageId, isLooking(), response -> {
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

            // Say if the run stopped before finishing, and offer to resume it.
            updateRunStatus(response.getRun());

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

        final Element markdownContainer = getView().getMarkdownContainer().getElement();
        if (AiActivityGroups.isActivity(msg.getMessageType())) {
            final Element existingEntry = activityGroups.findEntry(messageId);
            if (existingEntry != null) {
                existingEntry.getParentElement().replaceChild(messageElement, existingEntry);
            } else {
                final AiChatMessage question = renderedMessages.get(msg.getTurnId());
                activityGroups.add(markdownContainer, msg, messageElement, question != null
                        ? question.getCreateTimeMs()
                        : 0);
                keepProgressLast(markdownContainer);
            }
            return;
        }
        if (msg.getMessageType() == AiMessageType.AI_RESPONSE || msg.getMessageType() == AiMessageType.ERROR) {
            // The answer is here, so the turn's work is done.
            activityGroups.finish(msg.getTurnId(), msg.getCreateTimeMs());
            removeLiveAnswer();
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
        final DateTimeFormat dayFormat = DateTimeFormat.getFormat("yyyyMMdd");
        final String day = dayFormat.format(new Date(timeMs));
        if (day.equals(lastRenderedDay)) {
            return;
        }
        lastRenderedDay = day;
        final String label;
        if (day.equals(dayFormat.format(new Date(nowMs)))) {
            label = "Today";
        } else if (day.equals(dayFormat.format(new Date(nowMs - 24L * 60 * 60 * 1000)))) {
            label = "Yesterday";
        } else {
            label = DateTimeFormat.getFormat("EEEE d MMMM yyyy").format(new Date(timeMs));
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
        renderedMessages.clear();
        activityGroups.clear();
        contextUsage = null;
        getView().setContextMeter(null, null, null);
        lastRenderedDay = null;
        followBottom = true;
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
        // Scrolled up, so stop following the conversation, and offer to jump back to the latest message.
        followBottom = isAtBottom();
        getView().setJumpToLatestVisible(!followBottom);
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
                appendMessageHtml(hb, "ai-message ai-message--user", msg.getMessage(),
                        timeMs, nowMs, true, messageId, deletable, 0, true);
                break;
            case AI_RESPONSE:
                appendMessageHtml(hb, "ai-message ai-message--assistant", msg.getMessage(),
                        timeMs, nowMs, true, messageId, false, msg.getTurnId(), false);
                break;
            case ERROR:
                appendMessageHtml(hb, "ai-message ai-message--error", msg.getMessage(),
                        timeMs, nowMs, true, messageId, false);
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

        hb.div(wrapper -> {
            if (assistant) {
                wrapper.div(avatar -> {
                    icon(avatar, SvgImage.AI);
                    avatar.append("Stroom AI");
                }, Attribute.className("ai-message-avatar"));
            }
            wrapper.div(content -> content.append(markdownHtml), Attribute.className("ai-message-content"));
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

                // Add delete button.
                if (deletable && messageId > 0) {
                    button(footer,
                            SvgImage.DELETE,
                            iconButtonClassName("ai-message-delete"),
                            Attribute.title("Delete message"),
                            new Attribute("data-delete-message-id", String.valueOf(messageId)));
                }

                // Add a button to ask the question again.
                if (retryTurnId > 0) {
                    button(footer,
                            SvgImage.REFRESH,
                            iconButtonClassName("ai-message-retry"),
                            Attribute.title("Ask again"),
                            new Attribute(RETRY_TURN_ATTRIBUTE, String.valueOf(retryTurnId)));
                }

                // Add copy buttons: the original markdown, and for answers the text as shown.
                if (showCopy) {
                    footer.elem(button ->
                                    setCopyButtonContent(button, SvgImage.COPY, "Copy"),
                            BUTTON,
                            Attribute.className("ai-message-copy"),
                            Attribute.title("Copy the markdown"),
                            new Attribute("data", markdownText));
                    if (assistant) {
                        footer.elem(button ->
                                        setCopyButtonContent(button, SvgImage.COPY, "Copy as text"),
                                BUTTON,
                                Attribute.className("ai-message-copy"),
                                Attribute.title("Copy the text as shown"),
                                new Attribute(COPY_TEXT_ATTRIBUTE, "true"));
                    }
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
            card.div(title -> title.append(pending
                    ? "The agent wants to use " + (calls.size() == 1
                    ? "a tool that needs"
                    : calls.size() + " tools that need") + " your approval"
                    : "Tool calls the agent asked to use"), Attribute.className("ai-approval-title"));
            if (pending && reason != null) {
                card.div(text -> text.append(reason), Attribute.className("ai-approval-reason"));
            }
            for (final AiToolMessages.ApprovalCall call : calls) {
                card.div(callDiv -> appendApprovalCall(callDiv, msg, json, call, pending),
                        Attribute.className("ai-approval-call"));
            }
            card.div(footer -> {
                if (pending && undecided > 1) {
                    approvalButton(footer, msg, "Allow all", true, AiApprovalScope.ONCE, null);
                    approvalButton(footer, msg, "Deny all…", false, AiApprovalScope.ONCE, null);
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
            header.elem(code -> code.append(call.getTool()), SafeHtmlUtils.fromSafeConstant("code"));
            if (call.getServer() != null) {
                header.span(server -> server.append(" on " + call.getServer()),
                        Attribute.className("ai-approval-server"));
            }
            for (final String risk : call.getRisks()) {
                header.span(badge -> badge.append(risk), Attribute.className("ai-risk-badge"));
            }
            if (call.getDecision() != null) {
                header.span(decision -> decision.append(AiToolMessages.decisionLabel(call.getDecision(),
                                call.getNote())),
                        Attribute.className("ai-approval-decision ai-approval-decision--"
                                            + call.getDecision().toLowerCase()));
            }
        }, Attribute.className("ai-approval-call-header"));
        final String arguments = NullSafe.isBlankString(call.getArguments())
                ? "{}"
                : call.getArguments();
        // The arguments are shown in full, never cut short, so the user sees exactly what will be sent.
        callDiv.elem(pre -> pre.append(AiToolMessages.prettyJson(arguments)),
                SafeHtmlUtils.fromSafeConstant("pre"), Attribute.className("ai-approval-args"));
        callDiv.div(size -> size.append(arguments.length() + " characters of arguments"),
                Attribute.className("ai-approval-size"));
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
                approvalButton(buttons, msg, "Deny…", false, AiApprovalScope.ONCE, call.getCallId());
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
                        : "allow-focus ai-approval-button"),
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
            details.elem(summary -> {
                button(summary, SvgImage.INFO, iconButtonClassName("ai-message-summary-icon"));
                summary.span(text -> text.append(workingText(msg)), new Attribute("id", WORKING_TEXT_ID));
            }, SUMMARY, Attribute.className("ai-message-header"));

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
        keepProgressLast(markdownContainer);
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
        keepProgressLast(markdownContainer);
    }

    /**
     * Keeps the line saying what is being done, then the answer being written, at the end of the conversation, as
     * messages arrive above them.
     */
    private static void keepProgressLast(final Element markdownContainer) {
        for (final String id : new String[]{WORKING_MESSAGE_ID, LIVE_ANSWER_ID}) {
            final Element element = Document.get().getElementById(id);
            if (element != null && element.getParentElement() == markdownContainer
                && element != markdownContainer.getLastChild()) {
                markdownContainer.appendChild(element);
            }
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
        } else {
            final Element text = doc.getElementById(WORKING_TEXT_ID);
            if (text != null) {
                text.setInnerText(workingText(workingMessage));
            }
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
        askStroomAiClient.createChat(chat -> {
            currentChat = chat;
            titleGenerated = false;
            lastSeenMessageId = 0;
            data = null;
            clearRenderedMessages();
            getView().setEmptyState(true);
            getView().clearContextIndicator();
            setChatTitle(chat.getTitle());
            getView().focus();
        }, this);
    }

    private void onShowHistory() {
        aiChatHistoryPresenterProvider.get().show(this::loadChat);
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

    private void onDeleteAllMessages() {
        if (currentChat == null) {
            return;
        }
        ConfirmEvent.fire(this,
                "Are you sure you want to delete all messages and attachments in this conversation?",
                ok -> {
                    if (ok) {
                        askStroomAiClient.deleteAllMessages(
                                currentChat.getId(),
                                success -> reloadCurrentChat(),
                                getView());
                    }
                });
    }

    private void reloadCurrentChat() {
        if (currentChat != null) {
            loadChat(currentChat);
        }
    }

    private void loadChat(final AiChat chat) {
        currentChat = chat;
        // A loaded chat already has a title.
        titleGenerated = true;
        lastSeenMessageId = 0;
        data = null;
        clearRenderedMessages();
        getView().clearContextIndicator();
        setChatTitle(chat.getTitle());

        // Load messages for the selected chat.
        askStroomAiClient.getMessages(chat.getId(), messages -> {
            final long nowMs = System.currentTimeMillis();
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
            askStroomAiClient.pollMessages(chat.getId(), lastSeenMessageId, isLooking(), response -> {
                updateAttachmentStatuses(response.getAttachments());
                updateRunStatus(response.getRun());
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

        setChatTitle(title);
        askStroomAiClient.updateChatTitle(currentChat.getId(), title, success -> {
            // Title persisted successfully — nothing more to do.
        }, this);
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

        void setTitle(String title);

        void clearMessages();

        void setEmptyState(boolean empty);

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
        void setContextMeter(String text, String title, String level);

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
                final DockType type = currentConfig.getDockType() == DockType.FLOAT
                        ? DockType.DOCK
                        : currentConfig.getDockType();
                final DockLocation location = currentConfig.getDockLocation();
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
