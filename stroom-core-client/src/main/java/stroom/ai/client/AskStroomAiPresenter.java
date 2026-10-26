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
import stroom.ai.shared.AiChatPollResponse;
import stroom.ai.shared.AiChatRun;
import stroom.ai.shared.AiChatRunStatus;
import stroom.ai.shared.AiChatSearchHit;
import stroom.ai.shared.AiChatStatus;
import stroom.ai.shared.AiContextUsage;
import stroom.ai.shared.AiCreateVisualisation;
import stroom.ai.shared.AiMcpPrompt;
import stroom.ai.shared.AiMcpPromptArgument;
import stroom.ai.shared.AiMemory;
import stroom.ai.shared.AiMemoryScope;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.AiSuggestions;
import stroom.ai.shared.AiTabSnapshot;
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
import stroom.ai.shared.SuggestAiPromptsRequest;
import stroom.ai.shared.UndoAiCompactionRequest;
import stroom.alert.client.event.AlertCallback;
import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.alert.client.event.PromptEvent;
import stroom.content.client.event.CloseContentTabEvent;
import stroom.content.client.event.ContentTabSelectionChangeEvent;
import stroom.content.client.event.RefreshContentTabEvent;
import stroom.content.client.event.SelectContentTabEvent;
import stroom.core.client.ContentManager;
import stroom.core.client.LocationManager;
import stroom.dashboard.client.event.ApplyDashboardChangeEvent;
import stroom.dashboard.client.table.TableChange;
import stroom.dashboard.client.vis.ClearFunctionCacheEvent;
import stroom.dashboard.client.vis.VisRenderReportEvent;
import stroom.dashboard.shared.DashboardConfig;
import stroom.dashboard.shared.DashboardDoc;
import stroom.data.client.event.AskStroomAiBadgeEvent;
import stroom.data.client.event.AskStroomAiEvent;
import stroom.data.client.event.ShowAskStroomAiEvent;
import stroom.data.client.presenter.DataViewType;
import stroom.data.client.presenter.DisplayMode;
import stroom.data.client.presenter.ShowDataEvent;
import stroom.dispatch.client.ExportFileCompleteUtil;
import stroom.dispatch.client.RestError;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocumentType;
import stroom.document.client.DocumentTabData;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.document.client.event.ShowCreateDocumentDialogEvent;
import stroom.entity.client.event.DocTabSelectedEvent;
import stroom.entity.client.presenter.DocPresenter;
import stroom.entity.client.presenter.MarkdownConverter;
import stroom.explorer.client.event.ExplorerTreeSelectEvent;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.explorer.client.presenter.DocSelectionPopup;
import stroom.explorer.client.presenter.DocumentTypeCache;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.main.client.event.DockEvent;
import stroom.main.client.event.DockResizeEvent;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.pipeline.shared.SourceLocation;
import stroom.pipeline.stepping.client.event.SteppingCodeChangeEvent;
import stroom.pipeline.stepping.client.presenter.CodeDiffCardRenderer;
import stroom.preferences.client.DateTimeFormatter;
import stroom.query.client.event.QueryCodeChangeEvent;
import stroom.query.shared.QueryDoc;
import stroom.security.shared.DocumentPermission;
import stroom.svg.shared.SvgImage;
import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.ClipboardUtil;
import stroom.util.client.CopiedFlash;
import stroom.util.shared.NullSafe;
import stroom.util.shared.diff.LineDiff;
import stroom.visualisation.shared.VisualisationDoc;
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
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.view.GlobalResizeObserver;
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
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private static final String WORKING_MESSAGE_ID = AiConversationEntries.WORKING_MESSAGE_ID;
    private static final String WORKING_TEXT_ID = "ai-working-text";
    // On what the model said alongside its tool calls, while the Working line shows it.
    private static final String SHOWN_AS_PROGRESS_CLASS = "ai-message--shown-as-progress";
    private static final String MESSAGE_ID_ATTRIBUTE = "data-message-id";
    private static final String SEND_STEER_NOW_ATTRIBUTE = "data-send-steer-now";
    // The key in attachedTabs of a chat not yet made.
    private static final int NEW_CHAT_KEY = 0;
    // How long after an attachment arrives its suggestions are offered, so its status arrives too.
    private static final int ATTACHED_SUGGESTIONS_DELAY_MS = 600;
    // How many more times the suggestions wait for the statuses of what was just attached.
    private static final int MAX_ATTACHED_SUGGESTIONS_WAITS = 5;
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
    // On an applied code change's Undo apply button: the tool result message the action is in.
    private static final String USER_ACTION_UNDO_ATTRIBUTE = "data-user-action-undo";
    // A tool result's buttons are shown in a card of their own, outside the tool call's entry.
    private static final String USER_ACTIONS_ID_SUFFIX = "-actions";
    private static final SafeHtml PRE = SafeHtmlUtils.fromSafeConstant("pre");
    private static final String CHAT_ID_ATTRIBUTE = "data-chat-id";
    private static final String DELETE_CHAT_ATTRIBUTE = "data-delete-chat-id";
    private static final String RUN_STATUS_ID = "ai-run-status";
    private static final String RESUME_RUN_ATTRIBUTE = "data-resume-run";
    private static final String RETRY_TURN_ATTRIBUTE = "data-retry-turn-id";
    // A message gone to from the overview is picked out for this long.
    private static final String LOCATED_CLASS = "ai-message--located";
    private static final int LOCATED_MS = 2_000;
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
    // How long a chat takes to load before it is shown to be loading.
    private static final int LOADING_DELAY_MS = 200;
    private static final String UNDO_COMPACTION_ATTRIBUTE = "data-undo-compaction";
    private static final String ELICITATION_ATTRIBUTE = "data-elicitation-id";
    private static final String ELICITATION_ACTION_ATTRIBUTE = "data-elicitation-action";
    // On the Yes and No buttons of a yes or no question: the question's field, and the answer each gives.
    private static final String ELICITATION_FIELD_ATTRIBUTE = "data-elicitation-field";
    private static final String ELICITATION_VALUE_ATTRIBUTE = "data-elicitation-value";
    // The tool Stroom AI's agent asks the user questions with.
    private static final String ASK_USER_TOOL = "ask_user";
    private static final int DEFAULT_COMPACT_AT_PERCENT = 80;
    private static final String LIVE_ANSWER_ID = AiConversationEntries.LIVE_ANSWER_ID;
    private static final int STREAMING_POLL_MS = 500;
    private static final long KEEP_POLLING_AFTER_ANSWER_MS = 15_000;
    private static final int BOTTOM_TOLERANCE_PX = 40;
    // The furthest, in heights of the view, that following the conversation glides to new content.
    private static final double MAX_GLIDE_VIEWS = 1.5;

    private final DocSelectionBoxPresenter docSelectionBoxPresenter;
    private final MarkdownConverter markdownConverter;
    private final AskStroomAiClient askStroomAiClient;
    private final Provider<AskStroomAiConfigPresenter> askStroomAiConfigPresenterProvider;
    private final Provider<DownloadChatPresenter> downloadChatPresenterProvider;
    private final Provider<AiMemoryPresenter> memoryPresenterProvider;
    private final Provider<AiToolApprovalsPresenter> toolApprovalsPresenterProvider;
    private final Provider<AiAttachmentDataPresenter> aiAttachmentDataPresenterProvider;
    private final Provider<AskStroomAiTabPresenter> askStroomAiTabPresenterProvider;
    private final Provider<AiCommandErrorPresenter> commandErrorPresenterProvider;
    private final Provider<DocSelectionPopup> docSelectionPopupProvider;
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
    // Whether the overview of the chat is shown, is to be drawn again, and shows the agent as working.
    private boolean overviewShowing;
    private boolean overviewRefreshDue;
    private boolean overviewWorking;
    // The tool results whose steps for the user (e.g. an Apply button) are held while the agent works, by message id,
    // in order: shown once it stops, after its answer, so the user isn't asked to act while more is to come.
    private final Map<Integer, AiChatMessage> heldUserActions = new LinkedHashMap<>();
    // The prompt waiting for the user, kept in view as it grows.
    private Element watchedPrompt;
    // What the overview is searched for, if anything.
    private String overviewQuery;
    // The command whose reference is shown, or null.
    private String referenceCommand;
    // The suggestions offered above the input: the follow-ups to the latest answer (answerFollowUps, kept while that
    // answer is the latest thing in the chat), or suggestions for what was attached since.
    private List<String> followUps = List.of();
    // Visualisations created from the chat whose first render report is awaited, by UUID.
    private final Map<String, AwaitedReport> awaitedRenderReports = new HashMap<>();
    private List<String> answerFollowUps = List.of();
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
    // Shows that a chat is loading, if it takes long enough to notice, so a quick load doesn't flicker.
    private final Timer loadingTimer = new Timer() {
        @Override
        public void run() {
            getView().setLoading(true);
        }
    };
    // Suggestions for what the user has just attached: offered at once, from what was attached, then, while the chat
    // has a conversation to go on, replaced by the model's, unless something else was attached or sent meanwhile
    // (counted by attachedSuggestionsSequence). attachmentRequested is set while an attachment from elsewhere
    // (e.g. the Explorer) is on its way, so it is suggested for when it arrives, but not when a chat is loaded.
    private int attachedSuggestionsSequence;
    private boolean attachmentRequested;
    private int attachedSuggestionsWaits;
    private final Timer attachedSuggestionsTimer = new Timer() {
        @Override
        public void run() {
            // Every attachment is suggested for, so wait (a little) for the statuses of any just attached, which
            // say what each is.
            if (attachedSuggestionsWaits < MAX_ATTACHED_SUGGESTIONS_WAITS && pendingAttachments.values().stream()
                    .anyMatch(msg -> !attachmentStatuses.containsKey(msg.getAttachmentId()))) {
                attachedSuggestionsWaits++;
                schedule(ATTACHED_SUGGESTIONS_DELAY_MS);
                return;
            }
            attachedSuggestionsWaits = 0;
            attachmentRequested = false;
            offerAttachedSuggestions(false);
        }
    };
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
    // When polling first saw the run waiting for the user with nothing it asks shown yet, or 0.
    private long waitingWithoutPromptSinceMs;
    // The rendered messages, by id, e.g. to edit a question or redraw an answered card.
    private final Map<Integer, AiChatMessage> renderedMessages = new HashMap<>();
    // Tables attached since the last message was sent, by their message id: shown above the input box, then in
    // the bubble of the message they are sent with.
    private final Map<Integer, AiChatMessage> pendingAttachments = new LinkedHashMap<>();
    // The attachments each message the user sent went with, by its id.
    private final Map<Integer, List<AiChatMessage>> sentAttachments = new HashMap<>();
    // The attachments of the message being sent, until it is stored.
    private List<AiChatMessage> sendingAttachments = List.of();
    // The latest status of each attachment, by its id.
    private final Map<Integer, AiChatAttachment> attachmentStatuses = new HashMap<>();
    // Stroom's document types, for the icons of documents attached from the Explorer tree, once fetched.
    private DocumentTypes documentTypes;
    private boolean fetchingDocumentTypes;
    // The day of the last message added, for day dividers.
    private String lastRenderedDay;
    // True while the conversation is scrolled to the bottom, so new messages scroll into view.
    private boolean followBottom = true;
    // What the model said alongside its latest tool calls, kept hidden in the conversation while the Working line
    // shows it, or null once it is shown.
    private AiChatMessage interimShownAsProgress;
    // Where the conversation was scrolled to last, to tell the user scrolling up from content growing below.
    private int lastScrollTop;
    // The prompts the model's MCP servers offer, as slash commands, once fetched.
    private List<AiMcpPrompt> mcpPrompts;
    private boolean fetchingPrompts;
    // How much of the model's context window the chat uses, as last reported.
    private AiContextUsage contextUsage;
    private final DocumentTypeCache documentTypeCache;
    private final AiDocumentationChanges aiDocumentationChanges;
    // The document tab the user is on, which they can attach to the chat; while the chat's own tab is selected, the
    // one they were on before.
    private TabData currentTab;
    // The tab the user has attached to each chat, by chat id (NEW_CHAT_KEY before the chat is made). It stays
    // attached, whichever tab the user is on, until they remove it or close it.
    private final Map<Integer, TabData> attachedTabs = new HashMap<>();
    // A document the user has just created with a button in a chat, which is attached to that chat (by its key) once
    // its tab opens, so they can carry on asking about it.
    private DocRef attachWhenOpened;
    private int attachWhenOpenedChatKey;
    // The tab whose unsaved state is followed, to keep its chip up to date, and the handler that follows it.
    private TabData followedTab;
    private HandlerRegistration followedTabDirtyRegistration;
    // False if an administrator has turned off sharing tabs with chats.
    private boolean shareActiveTab = true;
    // The hash of the attached tab's snapshot last sent to each chat, by chat id, so an unchanged one is sent as its
    // hash only.
    private final Map<Integer, String> lastSentTabHashes = new HashMap<>();
    // The node selected in the Explorer tree, for the folder of a query created from the chat.
    private ExplorerNode selectedExplorerNode;

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
                                final Provider<AiCommandErrorPresenter> commandErrorPresenterProvider,
                                final Provider<DocSelectionPopup> docSelectionPopupProvider,
                                final ContentManager contentManager,
                                final LocationManager locationManager,
                                final DateTimeFormatter dateTimeFormatter,
                                final DocumentTypeCache documentTypeCache,
                                final AiDocumentationChanges aiDocumentationChanges) {
        super(eventBus, view, askStroomAiProxy);
        this.markdownConverter = markdownConverter;
        this.askStroomAiClient = askStroomAiClient;
        this.askStroomAiConfigPresenterProvider = askStroomAiConfigPresenterProvider;
        this.downloadChatPresenterProvider = downloadChatPresenterProvider;
        this.memoryPresenterProvider = memoryPresenterProvider;
        this.toolApprovalsPresenterProvider = toolApprovalsPresenterProvider;
        this.aiAttachmentDataPresenterProvider = aiAttachmentDataPresenterProvider;
        this.askStroomAiTabPresenterProvider = askStroomAiTabPresenterProvider;
        this.commandErrorPresenterProvider = commandErrorPresenterProvider;
        this.docSelectionPopupProvider = docSelectionPopupProvider;
        this.contentManager = contentManager;
        this.locationManager = locationManager;
        this.docSelectionBoxPresenter = docSelectionBoxPresenter;
        this.dateTimeFormatter = dateTimeFormatter;
        this.documentTypeCache = documentTypeCache;
        this.aiDocumentationChanges = aiDocumentationChanges;

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
        registerHandler(getEventBus().addHandler(VisRenderReportEvent.getType(), this::onRenderReport));

        registerHandler(getView().getChatList().addDomHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                onChatListClick(e.getNativeEvent().getEventTarget().cast());
            }
        }, ClickEvent.getType()));

        registerHandler(getView().getAttachmentList().addDomHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                final Element target = e.getNativeEvent().getEventTarget().cast();
                final Element button = ElementUtil.findParent(target, element ->
                        element.getTagName().equalsIgnoreCase("button"), 5);
                if (button == null) {
                    return;
                }
                if (button.hasAttribute(AiAttachments.REMOVE_TAB_ATTRIBUTE)) {
                    detachTab();
                    return;
                }
                if (button.hasAttribute(AiAttachments.ATTACH_TAB_ATTRIBUTE)) {
                    attachCurrentTab();
                    return;
                }
                final String remove = button.getAttribute(AiAttachments.REMOVE_ATTRIBUTE);
                final String attachmentId = button.getAttribute("data-attachment-id");
                if (openAttachedDocument(button)) {
                    return;
                }
                if (button.hasAttribute(AiAttachments.REMOVE_ALL_ATTRIBUTE)) {
                    removeAllAttachments();
                } else if (NullSafe.isNonBlankString(remove)) {
                    removeAttachment(Integer.parseInt(remove));
                } else if (NullSafe.isNonBlankString(attachmentId)) {
                    onViewAttachmentData(Integer.parseInt(attachmentId), button.getAttribute("data-attachment-desc"));
                }
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
                // A code span in an answer (a TypeId, say) copied by a click, unless text in it is being selected.
                // By ElementUtil's class name, which an svg element (a button's icon) has as no string.
                final Element code = ElementUtil.findParent(target, "markdown-copy-code", 3);
                if (code != null && !hasSelection()) {
                    if (ClipboardUtil.copy(code.getInnerText())) {
                        CopiedFlash.show(code);
                    }
                    return;
                }
                final Element button = ElementUtil.findParent(target, element ->
                        element.getTagName().equalsIgnoreCase("button"), 5);
                if (button != null) {
                    // Opening a document attached from the Explorer tree.
                    if (openAttachedDocument(button)) {
                        return;
                    }
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

                    // Undoing a proposed code, query or dashboard change the user applied.
                    final String undoMessage = button.getAttribute(USER_ACTION_UNDO_ATTRIBUTE);
                    if (NullSafe.isNonBlankString(undoMessage)) {
                        onUndoCodeChange(button, Integer.parseInt(undoMessage),
                                button.getAttribute(USER_ACTION_ID_ATTRIBUTE));
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
                            ClipboardUtil.copy(AiChatTexts.forClipboard(content.getInnerText()));
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

                    // Sending a message waiting for the run's current step now.
                    final String sendNowStr = button.getAttribute(SEND_STEER_NOW_ATTRIBUTE);
                    if (NullSafe.isNonBlankString(sendNowStr)) {
                        onSendSteerNow(button, Integer.parseInt(sendNowStr));
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
                        if (ClipboardUtil.copy(AiChatTexts.forClipboard(data))) {
                            flashCopied(button);
                        }
                    }
                }
            }
        }, ClickEvent.getType()));

        // In a question being edited, Escape cancels the edit and Enter sends it (Shift+Enter starts a new line), as
        // in the chat's input box. In a form from an MCP tool, Enter submits it.
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
            } else if (e.getNativeKeyCode() == KeyCodes.KEY_ENTER && !e.isShiftKeyDown()) {
                e.preventDefault();
                onSendEdit(Integer.parseInt(idStr));
            }
        }, KeyDownEvent.getType()));

        // The tab the user is on can be attached to the chat, starting with the one active now.
        onActiveTabChange(contentManager.getSelectedTab());
        addRegisteredHandler(ContentTabSelectionChangeEvent.getType(), event ->
                onActiveTabChange(event.getTabData()));
        // A closed tab can no longer be shared, so it is no longer attached or offered.
        addRegisteredHandler(CloseContentTabEvent.getType(), event -> onTabClosed(event.getTabData()));
        // A tab within an attached or offered tab, e.g. a Pipeline's Stepping view, which then speaks for the tab.
        addRegisteredHandler(DocTabSelectedEvent.getType(), event -> {
            if (event.getDocTab() != null && isShownInChips(event.getDocTab())) {
                refreshAttachmentList();
            }
        });
        addRegisteredHandler(RefreshContentTabEvent.getType(), event -> {
            if (event.getTabData() != null && isShownInChips(event.getTabData())) {
                refreshAttachmentList();
            }
        });
        addRegisteredHandler(ExplorerTreeSelectEvent.getType(), event -> {
            if (event.getSelectionModel() != null) {
                selectedExplorerNode = event.getSelectionModel().getSelected();
            }
        });

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
                    PanelState.setDockSize(dimension);
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
        if (shownAs == DockType.TAB) {
            // Already open in a tab, perhaps behind others, so brought to the front to show what is attached.
            SelectContentTabEvent.fire(this, tabPresenter());
        }
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
                        attachmentRequested = true;
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
                if (AiChatNotices.needsYouAgain(was, status)) {
                    BrowserNotifications.show("Stroom AI needs you", title + " is waiting for your answer.",
                            "stroom-ai-" + status.getChatId());
                } else if (AiChatNotices.hasAnswered(was, status)) {
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

    @Override
    public void onToggleOverview() {
        setOverviewShowing(!overviewShowing);
    }

    @Override
    public void onCloseOverview() {
        if (overviewShowing) {
            setOverviewShowing(false);
            getView().focus();
        }
    }

    @Override
    public void onOverviewStep(final int messageId) {
        setOverviewShowing(false);
        final Element element = findMessageElement(getView().getMarkdownContainer().getElement(),
                String.valueOf(messageId));
        if (element == null) {
            return;
        }
        // Looking at an earlier part of the chat, so it isn't scrolled away as messages arrive.
        followBottom = false;
        // Its top below the top row's shadow, as far as the shadow reaches into the conversation.
        final Element container = getView().getMarkdownContainer().getElement();
        container.setScrollTop(Math.max(0, element.getAbsoluteTop() - container.getAbsoluteTop()
                                            + container.getScrollTop() - topShadowReach(element)));
        lastScrollTop = container.getScrollTop();
        getView().setJumpToLatestVisible(true);
        // Picked out for a moment, to see which it is.
        element.addClassName(LOCATED_CLASS);
        new Timer() {
            @Override
            public void run() {
                element.removeClassName(LOCATED_CLASS);
            }
        }.schedule(LOCATED_MS);
    }

    /// @return True if the message asks the user something they haven't answered yet: a form, or a request for
    /// approval.
    private static boolean isWaitingForAnswer(final AiChatMessage msg) {
        final String json = msg.getMessage();
        return (msg.getMessageType() == AiMessageType.ELICITATION
                && "PENDING".equals(AiToolMessages.getString(json, "status")))
               || (msg.getMessageType() == AiMessageType.APPROVAL_REQUEST && AiToolMessages.isPendingApproval(json));
    }

    // Keeps the prompt waiting for the user in view as it grows, e.g. as its next question or a box for their own
    // answer appears, as its growing doesn't change the conversation's size.
    private void watchPrompt(final Element prompt) {
        unwatchPrompt();
        watchedPrompt = prompt;
        GlobalResizeObserver.addListener(prompt, element -> keepPromptBaseInView(prompt));
    }

    private void unwatchPrompt() {
        if (watchedPrompt != null) {
            GlobalResizeObserver.removeListener(watchedPrompt);
            watchedPrompt = null;
        }
    }

    // Scrolls just enough to bring the prompt's base into view, above the bottom's shadow: no further than keeps its
    // top in view, below the top's, and not at all if the user has scrolled away from it.
    private void keepPromptBaseInView(final Element prompt) {
        final Element container = getView().getMarkdownContainer().getElement();
        if (!container.isOrHasChild(prompt)) {
            unwatchPrompt();
            return;
        }
        final int reach = topShadowReach(prompt);
        final int viewTop = container.getAbsoluteTop() + reach;
        final int viewBottom = container.getAbsoluteTop() + container.getClientHeight() - reach;
        final int top = prompt.getAbsoluteTop();
        final int bottom = prompt.getAbsoluteBottom();
        if (bottom <= viewBottom || top >= viewBottom) {
            return;
        }
        final int by = Math.min(bottom - viewBottom, top - viewTop);
        if (by > 0) {
            container.setScrollTop(container.getScrollTop() + by);
            lastScrollTop = container.getScrollTop();
        }
    }

    /// @return How far the top row's shadow reaches into the conversation, in pixels: the scroll margin the CSS gives
    /// a message from the shadow's size (--ai-overlay-reach).
    private static native int topShadowReach(Element message) /*-{
        var reach = parseFloat($wnd.getComputedStyle(message).scrollMarginTop);
        return isNaN(reach) || reach <= 0
            ? 32
            : Math.round(reach);
    }-*/;

    private void setOverviewShowing(final boolean showing) {
        if (showing && chatListShowing) {
            setChatListShowing(false);
        }
        overviewShowing = showing;
        // Each time it is shown it starts unsearched.
        overviewQuery = null;
        getView().setOverviewVisible(showing);
        if (showing) {
            drawOverview();
        }
    }

    // Draws the overview again soon, if it is shown, once the messages being added now are all in.
    private void refreshOverview() {
        if (overviewShowing && !overviewRefreshDue) {
            overviewRefreshDue = true;
            Scheduler.get().scheduleDeferred(() -> {
                overviewRefreshDue = false;
                if (overviewShowing) {
                    drawOverview();
                }
            });
        }
    }

    private void drawOverview() {
        getView().setOverview(AiChatOverview.svg(AiChatOverview.of(overviewItems()), overviewWorking,
                overviewQuery));
    }

    @Override
    public void onOverviewSearch(final String query) {
        overviewQuery = query;
        if (overviewShowing) {
            drawOverview();
        }
    }

    /// @return The chat's messages, in order, as the overview sees them: what the user did, the tools the agent
    /// called, and anything else it did, which counts towards the time it spent.
    private List<AiChatOverview.Item> overviewItems() {
        final List<AiChatMessage> messages = new ArrayList<>(renderedMessages.values());
        messages.sort(Comparator.comparingInt(AiChatMessage::getId));
        final List<AiChatOverview.Item> items = new ArrayList<>();
        for (final AiChatMessage msg : messages) {
            final int id = msg.getId();
            final long timeMs = msg.getCreateTimeMs();
            final String json = msg.getMessage();
            final AiChatOverview.Item item = switch (msg.getMessageType()) {
                case USER_MESSAGE -> AiChatOverview.Item.step(id, timeMs, AiChatOverview.Kind.QUESTION, json);
                case STEER_MESSAGE, QUEUED_STEER_MESSAGE, SEND_NOW_STEER_MESSAGE ->
                    AiChatOverview.Item.step(id, timeMs, AiChatOverview.Kind.STEER, json);
                case TOOL_CALL -> AiChatOverview.Item.tool(id, timeMs, AiToolMessages.getString(json, "tool"));
                case ELICITATION -> answerItem(id, timeMs, json);
                case APPROVAL_REQUEST -> approvalItem(id, timeMs, json);
                default -> AiChatOverview.Item.work(id, timeMs);
            };
            items.add(item);
        }
        return items;
    }

    // A form or question the user answered, or what the agent did if they haven't (yet).
    private static AiChatOverview.Item answerItem(final int id, final long timeMs, final String json) {
        final String status = AiToolMessages.getString(json, "status");
        // What the form asks, as its card's title has it.
        final String question = AiElicitationCard.firstLine(AiToolMessages.getString(json, "message"), "A form");
        if ("ACCEPTED".equals(status)) {
            return AiChatOverview.Item.step(id, timeMs, AiChatOverview.Kind.ANSWER, NullSafe.nonBlankStringElse(
                    AiElicitationCard.answerText(json, question),
                    AiChatOverview.QUESTION_LABEL + question + "\n" + AiChatOverview.ANSWER_LABEL + "Answered"));
        }
        if ("DECLINED".equals(status)) {
            return AiChatOverview.Item.step(id, timeMs, AiChatOverview.Kind.ANSWER,
                    AiChatOverview.QUESTION_LABEL + question + "\n" + AiChatOverview.ANSWER_LABEL
                    + "Chose not to answer");
        }
        return AiChatOverview.Item.work(id, timeMs);
    }

    // A request for approval the user answered, or what the agent did if they haven't (yet).
    private static AiChatOverview.Item approvalItem(final int id, final long timeMs, final String json) {
        if (!AiToolMessages.isToolRequest(json)) {
            // A change to the memory.
            final String status = AiToolMessages.getString(json, "status");
            if ("APPROVED".equals(status)) {
                return AiChatOverview.Item.step(id, timeMs, AiChatOverview.Kind.APPROVAL, "A change to memory");
            }
            if ("REJECTED".equals(status)) {
                return AiChatOverview.Item.step(id, timeMs, AiChatOverview.Kind.REJECTION, "A change to memory");
            }
            return AiChatOverview.Item.work(id, timeMs);
        }
        final List<String> allowed = new ArrayList<>();
        final List<String> denied = new ArrayList<>();
        for (final AiToolMessages.ApprovalCall call : AiToolMessages.approvalCalls(json)) {
            if ("ALLOWED".equals(call.getDecision())) {
                allowed.add(call.getTool());
            } else if ("DENIED".equals(call.getDecision())) {
                denied.add(call.getTool());
            }
        }
        if (allowed.isEmpty() && denied.isEmpty()) {
            return AiChatOverview.Item.work(id, timeMs);
        }
        if (allowed.isEmpty()) {
            return AiChatOverview.Item.step(id, timeMs, AiChatOverview.Kind.REJECTION, String.join(", ", denied));
        }
        return AiChatOverview.Item.step(id, timeMs, AiChatOverview.Kind.APPROVAL, String.join(", ", allowed)
                                                                                  + (denied.isEmpty()
                ? ""
                : "; rejected " + String.join(", ", denied)));
    }

    @Override
    public void onCloseChatList() {
        if (chatListShowing) {
            setChatListShowing(false);
            getView().focus();
        }
    }

    private void setChatListShowing(final boolean showing) {
        if (showing && overviewShowing) {
            setOverviewShowing(false);
        }
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
            final String colour = status != null
                    ? AiChatTexts.statusColour(status)
                    : null;
            item.div(iconDiv -> icon(iconDiv, status != null
                            ? AiChatTexts.statusIcon(status)
                            : SvgImage.AI),
                    Attribute.className("ai-chat-list-icon" + (colour != null
                            ? " ai-chat-list-icon--" + colour
                            : "")),
                    Attribute.title(status != null
                            ? AiChatTexts.statusExplanation(status)
                            : "A chat"));
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
        // The suggestions take room from the conversation, which keeps the latest message in view as its view
        // gets shorter (onConversationResized) while it is followed; it isn't scrolled here, as this runs on every
        // poll, which would pull the conversation to the bottom whatever had changed.
        getView().setFollowUps(currentRunActive || requestInFlight
                ? List.of()
                : followUps);
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
            reference.append("\n\nExample: `").append(AiSlashCommands.promptExample(prompt)).append('`');
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
            // No prompts if they can't be listed, e.g. there is no model yet, but the chat's own commands work.
            mcpPrompts = List.of();
            fetchingPrompts = false;
            then.run();
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
                showCommandError(prompt.getCommand() + " needs " + argument.getName() + ", e.g. "
                                 + AiSlashCommands.promptExample(prompt) + ".", prompt.getCommand());
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
        showCommandError("There is no command " + parts[0] + ". A command is one of the chat's own (e.g. /new or "
                         + "/compact) or a prompt of one of the MCP servers: type / to see them, with how to use "
                         + "each. To send a message that starts with /, start it with //.", null);
        return true;
    }

    /// Says why a command can't be run: for one there is, with a link to its documentation above the input.
    ///
    /// @param message Why it can't be run.
    /// @param command The command, if it is one there is; or null for one there isn't.
    private void showCommandError(final String message, final String command) {
        final AiSlashCommand known = command == null
                ? null
                : slashCommands().stream()
                        .filter(candidate -> candidate.getName().equals(command))
                        .findFirst()
                        .orElse(null);
        commandErrorPresenterProvider.get().show(
                known == null
                        ? "Unknown Command"
                        : "Invalid Command",
                message,
                known == null
                        ? null
                        : "Show how to use " + command,
                known == null
                        ? null
                        : () -> onCommandPicked(known));
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
                if (mode == null && !rest.isEmpty()) {
                    showCommandError("'" + rest + "' isn't a mode. Use /mode agent, /mode ask or /mode plan.",
                            AiSlashCommands.MODE);
                } else if (mode == null) {
                    AlertEvent.fireInfo(this, "This chat is in " + getView().getAgentMode().getDisplayValue()
                                              + " mode. Use /mode agent, /mode ask or /mode plan to change it.",
                            null);
                } else {
                    switchMode(mode);
                }
            }
            case AiSlashCommands.PLAN -> {
                if (rest.isEmpty()) {
                    showCommandError("Say what to plan, e.g. /plan onboard feed ACME.", AiSlashCommands.PLAN);
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
                    showCommandError("Say what to remember, e.g. /remember our firewall feeds start with FW-.",
                            AiSlashCommands.REMEMBER);
                } else {
                    ensureChat(() -> remember(rest));
                }
            }
            default -> {
                // Only the chat's own commands are run here.
            }
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
        if (usage.getShortenedParts() > 0) {
            // The figure is what the model was last sent, which is less than the parts below, as stored.
            hb.div(note -> note.append("To fit, the agent has shortened " + usage.getShortenedParts()
                                       + " earlier step(s) or result(s) of this answer; they are still stored, "
                                       + "and the agent can read them again. The parts above are as stored, "
                                       + "before shortening."),
                    Attribute.className("ai-context-details-note"));
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
     * Renders a form an MCP tool asked the user to fill in. While it waits: what the server said, as sanitised
     * Markdown, and its fields with Submit and Decline. Once answered, just the question (its first line, the
     * whole of it on hover) and the answer, or what else became of it.
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
                // As Markdown, sanitised as the chat's answers are.
                card.div(content -> content.append(markdownConverter.convertUntrustedMarkdownToHtml(
                                AiElicitationCard.messageMarkdown(message))),
                        Attribute.className("ai-elicitation-message"));
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
     * @return The messages shown that the server may change in place: forms waiting for the user, which it may
     * cancel (e.g. when the run stops), and messages waiting for the run to pick them up, which it sends.
     */
    private List<Integer> watchedMessageIds() {
        final List<Integer> ids = new ArrayList<>();
        renderedMessages.values().forEach(msg -> {
            if ((msg.getMessageType() == AiMessageType.ELICITATION
                 && "PENDING".equals(AiToolMessages.getString(msg.getMessage(), "status")))
                || msg.getMessageType().isQueuedSteer()) {
                ids.add(msg.getId());
            }
        });
        return ids;
    }

    /**
     * @return True if the message is one the user sent to steer a run, whether or not the run has picked it up.
     */
    private static boolean isSteer(final AiChatMessage msg) {
        return msg.getMessageType() == AiMessageType.STEER_MESSAGE || msg.getMessageType().isQueuedSteer();
    }

    /**
     * Shows the messages the server has changed since they were shown, e.g. a form cancelled as the run stopped, or
     * a message the run has picked up.
     */
    private void updateWatchedMessages(final List<AiChatMessage> messages) {
        if (messages == null) {
            return;
        }
        final long nowMs = System.currentTimeMillis();
        for (final AiChatMessage msg : messages) {
            final AiChatMessage shown = renderedMessages.get(msg.getId());
            if (shown != null && (shown.getMessageType() != msg.getMessageType()
                                  || !Objects.equals(shown.getMessage(), msg.getMessage()))) {
                appendMessage(msg, nowMs);
            }
        }
    }

    /**
     * Shows the steps a tool's result says are the user's, as buttons in a card of their own (so they are not
     * hidden in the tool call's entry). Everything in it was written by Stroom, which checked each step; the
     * server's own text only appears in "Do first" conditions, as plain text.
     */
    // Shows the steps a tool result offers the user, or holds them while the agent is still working (see
    // heldUserActions). A card already shown is updated in place.
    private void showOrHoldUserActions(final Element markdownContainer, final AiChatMessage msg, final long nowMs) {
        final JSONArray actions = AiToolMessages.getArray(msg.getMessage(), "userActions");
        if (actions == null || actions.size() == 0) {
            return;
        }
        if ((overviewWorking || requestInFlight)
            && findMessageElement(markdownContainer, msg.getId() + USER_ACTIONS_ID_SUFFIX) == null) {
            heldUserActions.put(msg.getId(), msg);
        } else {
            appendUserActionsCard(markdownContainer, msg, nowMs);
        }
    }

    // Shows the steps held while the agent worked, now that it has stopped (answered, asks the user something, or
    // ended), at the end of the conversation, in the order they were offered.
    private void showHeldUserActions() {
        if (heldUserActions.isEmpty()) {
            return;
        }
        final List<AiChatMessage> held = new ArrayList<>(heldUserActions.values());
        heldUserActions.clear();
        final Element markdownContainer = getView().getMarkdownContainer().getElement();
        final long nowMs = System.currentTimeMillis();
        held.forEach(msg -> appendUserActionsCard(markdownContainer, msg, nowMs));
        scrollToBottom();
    }

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
            // The document it acts on a link that opens it (asked for by the user).
            row.div(text -> text.append(markdownConverter.convertUntrustedMarkdownToHtml(UserActionsUi.labelMarkdown(
                            AiToolMessages.getString(json, "label"), docRef(AiToolMessages.getString(json, "doc"))))),
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
            if (UserActionsUi.APPLY_CODE_CHANGE.equals(action)) {
                codeChangeRow(row, messageId, json, request, status, pending);
                return;
            }
            if (UserActionsUi.APPLY_QUERY_CHANGE.equals(action)) {
                queryChangeRow(row, messageId, json, request, status, pending);
                return;
            }
            if (UserActionsUi.APPLY_DASHBOARD_CHANGE.equals(action)) {
                dashboardChangeRow(row, messageId, json, status, pending);
                return;
            }
            if (UserActionsUi.APPLY_DOCUMENTATION_CHANGE.equals(action)) {
                documentationChangeRow(row, messageId, json, request, status, pending);
                return;
            }
            // A classic dashboard is shown as what Stroom builds from the StroomQL, rather than the StroomQL.
            final JSONArray outline = UserActionsUi.CREATE_DASHBOARD.equals(action)
                    ? AiToolMessages.getArray(AiToolMessages.getString(json, "newDashboard"), "outline")
                    : null;
            if (outline != null && outline.size() > 0) {
                row.div(list -> {
                    for (int i = 0; i < outline.size(); i++) {
                        final JSONString item = outline.get(i).isString();
                        if (item != null) {
                            list.div("• " + item.stringValue());
                        }
                    }
                }, Attribute.className("ai-user-action-details"));
            } else if (UserActionsUi.showsCode(action) && NullSafe.isNonBlankString(request)) {
                // An Elasticsearch request is shown for the cluster admin to copy, and Stroom never runs it. A
                // proposed query is shown before the user creates it.
                row.elem(pre -> pre.code(code -> code.append(request),
                        Attribute.className("language-" + UserActionsUi.codeLanguage(action))), PRE);
            }
            if (UserActionsUi.ELASTICSEARCH_REQUEST.equals(action)) {
                // No button.
                return;
            } else if (pending) {
                row.elem(UserActionsUi.buttonText(action, request), BUTTON,
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

    /// The rest of a proposed code change's row: the compact diff, and a button to review and apply it, or,
    /// once applied, one to undo it while the element's editor still holds the applied text.
    private void codeChangeRow(final HtmlBuilder row,
                               final int messageId,
                               final String json,
                               final String proposed,
                               final String status,
                               final boolean pending) {
        final String codeChange = AiToolMessages.getString(json, "codeChange");
        final String base = AiToolMessages.getString(codeChange, "baseCode");
        if (base != null && proposed != null) {
            row.append(CodeDiffCardRenderer.render(LineDiff.diff(base, proposed)));
        }
        final String actionId = NullSafe.string(AiToolMessages.getString(json, "id"));
        if (pending) {
            row.elem(UserActionsUi.buttonText(UserActionsUi.APPLY_CODE_CHANGE), BUTTON,
                    Attribute.className("allow-focus ai-approval-button ai-approval-button--approve"),
                    new Attribute(USER_ACTION_MESSAGE_ATTRIBUTE, String.valueOf(messageId)),
                    new Attribute(USER_ACTION_ID_ATTRIBUTE, actionId));
            return;
        }
        row.div(text -> text.append(UserActionsUi.outcomeText(status, AiToolMessages.getString(json, "outcome"))),
                Attribute.className("ai-approval-reason"));
        if (UserActionsUi.isAppliedCodeChange(UserActionsUi.APPLY_CODE_CHANGE, status)) {
            final SteppingCodeChangeEvent canUndo = SteppingCodeChangeEvent.fireCanUndo(this,
                    docRef(AiToolMessages.getString(codeChange, "pipeline")),
                    AiToolMessages.getString(codeChange, "element"));
            if (canUndo.isHandled() && !canUndo.isCanUndo()) {
                row.div(text -> text.append(UserActionsUi.UNDO_WITHDRAWN), Attribute.className("ai-approval-reason"));
            } else {
                row.elem(UserActionsUi.UNDO_APPLY, BUTTON,
                        Attribute.className("allow-focus ai-approval-button"),
                        new Attribute(USER_ACTION_UNDO_ATTRIBUTE, String.valueOf(messageId)),
                        new Attribute(USER_ACTION_ID_ATTRIBUTE, actionId));
            }
        }
    }

    /// The rest of a proposed documentation change's row: the compact diff, and a button to review and apply it,
    /// or, once applied, one to undo it, unless the Documentation tab has been edited since.
    private void documentationChangeRow(final HtmlBuilder row,
                                        final int messageId,
                                        final String json,
                                        final String proposed,
                                        final String status,
                                        final boolean pending) {
        final String base = AiToolMessages.getString(AiToolMessages.getString(json, "codeChange"), "baseCode");
        if (base != null && proposed != null) {
            row.append(CodeDiffCardRenderer.render(LineDiff.diff(base, proposed)));
        }
        final String actionId = NullSafe.string(AiToolMessages.getString(json, "id"));
        if (pending) {
            row.elem(UserActionsUi.buttonText(UserActionsUi.APPLY_DOCUMENTATION_CHANGE), BUTTON,
                    Attribute.className("allow-focus ai-approval-button ai-approval-button--approve"),
                    new Attribute(USER_ACTION_MESSAGE_ATTRIBUTE, String.valueOf(messageId)),
                    new Attribute(USER_ACTION_ID_ATTRIBUTE, actionId));
            return;
        }
        row.div(text -> text.append(UserActionsUi.outcomeText(status, AiToolMessages.getString(json, "outcome"))),
                Attribute.className("ai-approval-reason"));
        if (UserActionsUi.isAppliedCodeChange(UserActionsUi.APPLY_DOCUMENTATION_CHANGE, status)) {
            if (aiDocumentationChanges.isUndoWithdrawn(messageId, actionId)) {
                row.div(text -> text.append(AppliedDocumentationChanges.UNDO_WITHDRAWN),
                        Attribute.className("ai-approval-reason"));
            } else {
                row.elem(UserActionsUi.UNDO_APPLY, BUTTON,
                        Attribute.className("allow-focus ai-approval-button"),
                        new Attribute(USER_ACTION_UNDO_ATTRIBUTE, String.valueOf(messageId)),
                        new Attribute(USER_ACTION_ID_ATTRIBUTE, actionId));
            }
        }
    }

    /// @return A proposed documentation change, as the chat shows it, or null if it isn't there.
    private AiDocumentationChanges.Change documentationChange(final int messageId, final String actionId) {
        final String json = findUserAction(messageId, actionId);
        if (json == null || currentChat == null) {
            return null;
        }
        return new AiDocumentationChanges.Change(currentChat.getId(), messageId, actionId,
                docRef(AiToolMessages.getString(json, "doc")),
                AiToolMessages.getString(AiToolMessages.getString(json, "codeChange"), "baseCode"),
                AiToolMessages.getString(json, "request"));
    }

    /// Opens the document on its Documentation tab and reviews a proposed change to it, from which the user can
    /// apply it to the tab's editor, unsaved. Applying it is checked and recorded by the server first.
    private void onReviewDocumentationChange(final int messageId, final String actionId) {
        final AiDocumentationChanges.Change change = documentationChange(messageId, actionId);
        if (change == null) {
            return;
        }
        aiDocumentationChanges.review(this, this, change, () -> {
            showUserActionStatus(messageId, actionId, "DONE", UserActionsUi.APPLIED);
            // Fetch the note of what was done.
            pollAfterAnswer();
        });
    }

    /// Undoes a proposed documentation change the user applied, putting back the text the Documentation tab held,
    /// if it still holds the applied text. The server checks Edit and records it first.
    private void onUndoDocumentationChange(final Element button, final int messageId, final String actionId) {
        final AiDocumentationChanges.Change change = documentationChange(messageId, actionId);
        if (change == null) {
            return;
        }
        button.setAttribute("disabled", "disabled");
        aiDocumentationChanges.undo(this, this, change,
                // Withdraw the button.
                () -> showUserActionStatus(messageId, actionId, "DONE", UserActionsUi.APPLIED),
                result -> {
                    if (!result.isDone()) {
                        button.removeAttribute("disabled");
                        AlertEvent.fireWarn(this, NullSafe.string(result.getMessage()), null);
                        return;
                    }
                    showUserActionStatus(messageId, actionId, "PENDING", result.getMessage());
                    pollAfterAnswer();
                });
    }

    /// The rest of a revised query's row: the query as it will be applied, and the Apply to query button, or, once
    /// applied, Undo apply while the Query's editor still holds it.
    private void queryChangeRow(final HtmlBuilder row,
                                final int messageId,
                                final String json,
                                final String proposed,
                                final String status,
                                final boolean pending) {
        if (NullSafe.isNonBlankString(proposed)) {
            row.elem(pre -> pre.code(code -> code.append(proposed)), PRE);
        }
        final String actionId = NullSafe.string(AiToolMessages.getString(json, "id"));
        if (pending) {
            row.elem(UserActionsUi.buttonText(UserActionsUi.APPLY_QUERY_CHANGE), BUTTON,
                    Attribute.className("allow-focus ai-approval-button ai-approval-button--approve"),
                    new Attribute(USER_ACTION_MESSAGE_ATTRIBUTE, String.valueOf(messageId)),
                    new Attribute(USER_ACTION_ID_ATTRIBUTE, actionId));
            return;
        }
        row.div(text -> text.append(UserActionsUi.outcomeText(status, AiToolMessages.getString(json, "outcome"))),
                Attribute.className("ai-approval-reason"));
        if (UserActionsUi.isAppliedCodeChange(UserActionsUi.APPLY_QUERY_CHANGE, status)) {
            final QueryCodeChangeEvent canUndo = QueryCodeChangeEvent.fireCanUndo(this,
                    docRef(AiToolMessages.getString(json, "doc")));
            if (canUndo.isHandled() && !canUndo.isCanUndo()) {
                row.div(text -> text.append(UserActionsUi.UNDO_WITHDRAWN), Attribute.className("ai-approval-reason"));
            } else {
                row.elem(UserActionsUi.UNDO_APPLY, BUTTON,
                        Attribute.className("allow-focus ai-approval-button"),
                        new Attribute(USER_ACTION_UNDO_ATTRIBUTE, String.valueOf(messageId)),
                        new Attribute(USER_ACTION_ID_ATTRIBUTE, actionId));
            }
        }
    }

    /// The rest of a proposed dashboard change's row: the changes in plain language, as Stroom wrote them, and the
    /// Apply to dashboard button, disabled (saying why) if the user can't edit the dashboard.
    private void dashboardChangeRow(final HtmlBuilder row,
                                    final int messageId,
                                    final String json,
                                    final String status,
                                    final boolean pending) {
        final String change = AiToolMessages.getString(json, "dashboardChange");
        final JSONArray changes = AiToolMessages.getArray(change, "changes");
        if (changes != null && changes.size() > 0) {
            row.div(list -> {
                for (int i = 0; i < changes.size(); i++) {
                    final JSONString item = changes.get(i).isString();
                    if (item != null) {
                        list.div("• " + item.stringValue());
                    }
                }
            }, Attribute.className("ai-user-action-details"));
        }
        if (!pending) {
            row.div(text -> text.append(UserActionsUi.outcomeText(status, AiToolMessages.getString(json, "outcome"))),
                    Attribute.className("ai-approval-reason"));
            if (UserActionsUi.isAppliedDashboardChange(UserActionsUi.APPLY_DASHBOARD_CHANGE, status)) {
                final String actionId = NullSafe.string(AiToolMessages.getString(json, "id"));
                final ApplyDashboardChangeEvent canUndo = ApplyDashboardChangeEvent.fireCanUndo(this,
                        docRef(AiToolMessages.getString(json, "doc")), dashboardUndoKey(messageId, actionId));
                if (canUndo.isHandled() && !canUndo.isCanUndo()) {
                    row.div(text -> text.append(ApplyDashboardChangeEvent.UNDO_WITHDRAWN),
                            Attribute.className("ai-approval-reason"));
                } else {
                    row.elem(UserActionsUi.UNDO_APPLY, BUTTON,
                            Attribute.className("allow-focus ai-approval-button"),
                            new Attribute(USER_ACTION_UNDO_ATTRIBUTE, String.valueOf(messageId)),
                            new Attribute(USER_ACTION_ID_ATTRIBUTE, actionId));
                }
            }
            return;
        }
        final ApplyDashboardChangeEvent check = ApplyDashboardChangeEvent.fireCheck(this,
                docRef(AiToolMessages.getString(json, "doc")));
        final String disabled = UserActionsUi.applyDashboardDisabledReason(
                AiToolMessages.getBoolean(change, "editable"),
                check.isHandled() && check.isOpen(),
                check.isEditable());
        final List<Attribute> attributes = new ArrayList<>();
        attributes.add(Attribute.className("allow-focus ai-approval-button ai-approval-button--approve"));
        attributes.add(new Attribute(USER_ACTION_MESSAGE_ATTRIBUTE, String.valueOf(messageId)));
        attributes.add(new Attribute(USER_ACTION_ID_ATTRIBUTE, NullSafe.string(AiToolMessages.getString(json, "id"))));
        if (disabled != null) {
            attributes.add(new Attribute("disabled", "disabled"));
            attributes.add(Attribute.title(disabled));
        }
        row.elem(UserActionsUi.buttonText(UserActionsUi.APPLY_DASHBOARD_CHANGE), BUTTON,
                attributes.toArray(new Attribute[0]));
    }

    /// Applies a proposed dashboard change to the dashboard, opening it if it isn't open, on top of any unsaved
    /// changes. The dashboard refuses it if it has changed since the proposal or can't be edited. Just before
    /// applying, the server checks Edit and records it. Nothing is saved, and it can be undone.
    private void onApplyDashboardChange(final Element button, final int messageId, final String actionId) {
        final String json = findUserAction(messageId, actionId);
        if (json == null || currentChat == null) {
            return;
        }
        final int chatId = currentChat.getId();
        final String change = AiToolMessages.getString(json, "dashboardChange");
        final List<TableChange> changes = operations(AiToolMessages.getArray(change, "operations"));
        final DocRef dashboard = docRef(AiToolMessages.getString(json, "doc"));
        button.setAttribute("disabled", "disabled");
        final ApplyDashboardChangeEvent event = ApplyDashboardChangeEvent.fireApply(this,
                dashboard,
                AiToolMessages.getString(change, "componentId"),
                AiToolMessages.getString(change, "component"),
                changes,
                onResult -> askStroomAiClient.doUserAction(chatId, messageId, actionId,
                        result -> onResult.accept(result.isDone()
                                ? null
                                : NullSafe.nonBlankStringElse(result.getMessage(), "The change can't be applied.")),
                        error -> onResult.accept(NullSafe.nonBlankStringElse(error.getMessage(),
                                "Unable to apply the change.")),
                        this),
                () -> {
                    showUserActionStatus(messageId, actionId, "DONE", UserActionsUi.APPLIED_DASHBOARD);
                    // Fetch the note of what was done.
                    pollAfterAnswer();
                },
                message -> {
                    button.removeAttribute("disabled");
                    if (message != null) {
                        AlertEvent.fireWarn(this, message, null);
                    }
                },
                dashboardUndoKey(messageId, actionId));
        if (!event.isHandled()) {
            button.removeAttribute("disabled");
            AlertEvent.fireWarn(this, "The dashboard can't be opened here.", null);
        }
    }

    /// @return What an applied dashboard change is known by, to undo it.
    private static String dashboardUndoKey(final int messageId, final String actionId) {
        return messageId + ":" + actionId;
    }

    /// Undoes a proposed dashboard change the user applied, putting the table back exactly as it was, if the
    /// dashboard is open and hasn't changed since. The server checks Edit and records it first.
    private void onUndoDashboardChange(final Element button, final int messageId, final String actionId) {
        final String json = findUserAction(messageId, actionId);
        if (json == null || currentChat == null) {
            return;
        }
        final DocRef dashboard = docRef(AiToolMessages.getString(json, "doc"));
        final String undoKey = dashboardUndoKey(messageId, actionId);
        final ApplyDashboardChangeEvent canUndo = ApplyDashboardChangeEvent.fireCanUndo(this, dashboard, undoKey);
        if (!canUndo.isHandled()) {
            AlertEvent.fireInfo(this, "Open dashboard '" + NullSafe.get(dashboard, DocRef::getName)
                                      + "' to undo the change there. If it was closed without saving, the change "
                                      + "is already gone.", null);
            return;
        }
        if (!canUndo.isCanUndo()) {
            AlertEvent.fireInfo(this, ApplyDashboardChangeEvent.UNDO_WITHDRAWN, null);
            // Withdraw the button.
            showUserActionStatus(messageId, actionId, "DONE", UserActionsUi.APPLIED_DASHBOARD);
            return;
        }
        button.setAttribute("disabled", "disabled");
        askStroomAiClient.undoUserAction(currentChat.getId(), messageId, actionId, result -> {
            if (!result.isDone()) {
                button.removeAttribute("disabled");
                AlertEvent.fireWarn(this, NullSafe.string(result.getMessage()), null);
                return;
            }
            if (!ApplyDashboardChangeEvent.fireUndo(this, dashboard, undoKey).isUndone()) {
                AlertEvent.fireWarn(this, ApplyDashboardChangeEvent.UNDO_WITHDRAWN, null);
            }
            showUserActionStatus(messageId, actionId, "PENDING", result.getMessage());
            pollAfterAnswer();
        }, this);
    }

    /// @param json A document reference, as JSON, or null.
    /// @return The document reference, or null.
    private static DocRef docRef(final String json) {
        final String type = AiToolMessages.getString(json, "type");
        final String uuid = AiToolMessages.getString(json, "uuid");
        if (type == null || uuid == null) {
            return null;
        }
        return new DocRef(type, uuid, AiToolMessages.getString(json, "name"));
    }

    /// @return The action of a tool result message with the id, as JSON, or null if it isn't there.
    private String findUserAction(final int messageId, final String actionId) {
        final AiChatMessage msg = renderedMessages.get(messageId);
        final JSONArray actions = msg == null
                ? null
                : AiToolMessages.getArray(msg.getMessage(), "userActions");
        if (actions == null || actionId == null) {
            return null;
        }
        for (int i = 0; i < actions.size(); i++) {
            final JSONObject action = actions.get(i).isObject();
            if (action != null && actionId.equals(AiToolMessages.getString(action.toString(), "id"))) {
                return action.toString();
            }
        }
        return null;
    }

    /// Opens the review of a proposed code change in the pipeline's stepping screen, if it is open. Applying it
    /// there is first checked and recorded by the server (which needs Edit on the element's document), then
    /// done in the element's editor, unsaved.
    private void onReviewCodeChange(final Element button, final int messageId, final String actionId) {
        final String json = findUserAction(messageId, actionId);
        if (json == null || currentChat == null) {
            return;
        }
        final int chatId = currentChat.getId();
        final String codeChange = AiToolMessages.getString(json, "codeChange");
        final DocRef pipeline = docRef(AiToolMessages.getString(codeChange, "pipeline"));
        final String elementId = AiToolMessages.getString(codeChange, "element");
        final SteppingCodeChangeEvent.Approver approver = (andStep, rejected, onResult) ->
                askStroomAiClient.applyCodeChange(chatId, messageId, actionId, rejected,
                        result -> onResult.accept(result.isDone()
                                ? null
                                : NullSafe.nonBlankStringElse(result.getMessage(), "The change can't be applied.")),
                        error -> onResult.accept(NullSafe.nonBlankStringElse(error.getMessage(),
                                "Unable to apply the change.")),
                        this);
        final SteppingCodeChangeEvent event = SteppingCodeChangeEvent.fireReview(this,
                pipeline,
                elementId,
                docRef(AiToolMessages.getString(json, "doc")),
                AiToolMessages.getString(codeChange, "baseCode"),
                AiToolMessages.getString(json, "request"),
                approver,
                () -> {
                    showUserActionStatus(messageId, actionId, "DONE", UserActionsUi.APPLIED);
                    // Fetch the note of what was done.
                    pollAfterAnswer();
                });
        if (!event.isHandled()) {
            final String name = NullSafe.nonBlankStringElse(NullSafe.get(pipeline, DocRef::getName), "?");
            if (pipeline != null) {
                OpenDocumentEvent.fire(this, pipeline, true);
            }
            AlertEvent.fireInfo(this, "To review the change, open pipeline '" + name + "' in stepping mode, step "
                                      + "to the record and select element '" + elementId
                                      + "', then click the button again.", null);
        } else if (event.getMessage() != null) {
            AlertEvent.fireInfo(this, event.getMessage(), null);
        }
    }

    /// Applies a revised query to its Query's editor, unsaved, if the Query is open. The server checks Edit and
    /// records it first; the Query's tab checks its editor still holds the query it was proposed against.
    private void onApplyQueryChange(final int messageId, final String actionId) {
        final String json = findUserAction(messageId, actionId);
        if (json == null || currentChat == null) {
            return;
        }
        final int chatId = currentChat.getId();
        final DocRef query = docRef(AiToolMessages.getString(json, "doc"));
        final SteppingCodeChangeEvent.Approver approver = (andStep, rejected, onResult) ->
                askStroomAiClient.applyCodeChange(chatId, messageId, actionId, rejected,
                        result -> onResult.accept(result.isDone()
                                ? null
                                : NullSafe.nonBlankStringElse(result.getMessage(), "The change can't be applied.")),
                        error -> onResult.accept(NullSafe.nonBlankStringElse(error.getMessage(),
                                "Unable to apply the change.")),
                        this);
        final QueryCodeChangeEvent event = QueryCodeChangeEvent.fireApply(this,
                query,
                AiToolMessages.getString(AiToolMessages.getString(json, "codeChange"), "baseCode"),
                AiToolMessages.getString(json, "request"),
                approver,
                () -> {
                    showUserActionStatus(messageId, actionId, "DONE", UserActionsUi.APPLIED);
                    // Fetch the note of what was done.
                    pollAfterAnswer();
                });
        if (!event.isHandled()) {
            if (query != null) {
                OpenDocumentEvent.fire(this, query, true);
            }
            AlertEvent.fireInfo(this, "To apply the change, open Query '"
                                      + NullSafe.nonBlankStringElse(NullSafe.get(query, DocRef::getName), "?")
                                      + "', then click the button again.", null);
        } else if (event.getMessage() != null) {
            AlertEvent.fireInfo(this, event.getMessage(), null);
        }
    }

    /// Undoes a revised query the user applied, putting back exactly what the Query's editor held, if it still
    /// holds the applied query. The server checks Edit and records it first.
    private void onUndoQueryChange(final Element button, final int messageId, final String actionId) {
        final String json = findUserAction(messageId, actionId);
        if (json == null || currentChat == null) {
            return;
        }
        final DocRef query = docRef(AiToolMessages.getString(json, "doc"));
        final QueryCodeChangeEvent canUndo = QueryCodeChangeEvent.fireCanUndo(this, query);
        if (!canUndo.isHandled()) {
            AlertEvent.fireInfo(this, "Open Query '" + NullSafe.get(query, DocRef::getName)
                                      + "' to undo the change there, or use Ctrl+Z in its editor.", null);
            return;
        }
        if (!canUndo.isCanUndo()) {
            AlertEvent.fireInfo(this, UserActionsUi.UNDO_WITHDRAWN, null);
            // Withdraw the button.
            showUserActionStatus(messageId, actionId, "DONE", UserActionsUi.APPLIED);
            return;
        }
        button.setAttribute("disabled", "disabled");
        askStroomAiClient.undoUserAction(currentChat.getId(), messageId, actionId, result -> {
            if (!result.isDone()) {
                button.removeAttribute("disabled");
                AlertEvent.fireWarn(this, NullSafe.string(result.getMessage()), null);
                return;
            }
            if (!QueryCodeChangeEvent.fireUndo(this, query).isUndone()) {
                AlertEvent.fireWarn(this, UserActionsUi.UNDO_WITHDRAWN, null);
            }
            showUserActionStatus(messageId, actionId, "PENDING", result.getMessage());
            pollAfterAnswer();
        }, this);
    }

    /// Undoes a proposed code change the user applied, putting back exactly what the element's editor held, if
    /// it still holds the applied text. The server checks Edit and records it first.
    private void onUndoCodeChange(final Element button, final int messageId, final String actionId) {
        final String json = findUserAction(messageId, actionId);
        if (json == null || currentChat == null) {
            return;
        }
        if (UserActionsUi.APPLY_QUERY_CHANGE.equals(AiToolMessages.getString(json, "action"))) {
            onUndoQueryChange(button, messageId, actionId);
            return;
        }
        if (UserActionsUi.APPLY_DASHBOARD_CHANGE.equals(AiToolMessages.getString(json, "action"))) {
            onUndoDashboardChange(button, messageId, actionId);
            return;
        }
        if (UserActionsUi.APPLY_DOCUMENTATION_CHANGE.equals(AiToolMessages.getString(json, "action"))) {
            onUndoDocumentationChange(button, messageId, actionId);
            return;
        }
        final String codeChange = AiToolMessages.getString(json, "codeChange");
        final DocRef pipeline = docRef(AiToolMessages.getString(codeChange, "pipeline"));
        final String elementId = AiToolMessages.getString(codeChange, "element");
        final SteppingCodeChangeEvent canUndo = SteppingCodeChangeEvent.fireCanUndo(this, pipeline, elementId);
        if (!canUndo.isHandled()) {
            AlertEvent.fireInfo(this, "Open pipeline '" + NullSafe.get(pipeline, DocRef::getName)
                                      + "' in stepping mode to undo the change there, or use Ctrl+Z in the "
                                      + "element's editor.", null);
            return;
        }
        if (!canUndo.isCanUndo()) {
            AlertEvent.fireInfo(this, UserActionsUi.UNDO_WITHDRAWN, null);
            // Withdraw the button.
            showUserActionStatus(messageId, actionId, "DONE", UserActionsUi.APPLIED);
            return;
        }
        button.setAttribute("disabled", "disabled");
        askStroomAiClient.undoUserAction(currentChat.getId(), messageId, actionId, result -> {
            if (!result.isDone()) {
                button.removeAttribute("disabled");
                AlertEvent.fireWarn(this, NullSafe.string(result.getMessage()), null);
                return;
            }
            final SteppingCodeChangeEvent undo = SteppingCodeChangeEvent.fireUndo(this, pipeline, elementId);
            if (!undo.isUndone()) {
                AlertEvent.fireWarn(this, UserActionsUi.UNDO_WITHDRAWN, null);
            }
            showUserActionStatus(messageId, actionId, "PENDING", result.getMessage());
            pollAfterAnswer();
        }, this);
    }

    private void onUserActionButton(final Element button, final int messageId, final String actionId) {
        if (currentChat == null) {
            return;
        }
        final String action = AiToolMessages.getString(findUserAction(messageId, actionId), "action");
        if (UserActionsUi.APPLY_CODE_CHANGE.equals(action)) {
            // Nothing is done until the user applies it in the review.
            onReviewCodeChange(button, messageId, actionId);
            return;
        }
        if (UserActionsUi.APPLY_DASHBOARD_CHANGE.equals(action)) {
            onApplyDashboardChange(button, messageId, actionId);
            return;
        }
        if (UserActionsUi.APPLY_QUERY_CHANGE.equals(action)) {
            // Applied by the Query's tab, once the server allows it.
            onApplyQueryChange(messageId, actionId);
            return;
        }
        if (UserActionsUi.APPLY_DOCUMENTATION_CHANGE.equals(action)) {
            // Nothing is done until the user applies it in the review.
            onReviewDocumentationChange(messageId, actionId);
            return;
        }
        button.setAttribute("disabled", "disabled");
        askStroomAiClient.doUserAction(currentChat.getId(), messageId, actionId, result -> {
            if (result.isDone() && result.getCreateQueryText() != null) {
                // The button can be used again, as the user may cancel the dialog.
                button.removeAttribute("disabled");
                createQuery(result.getCreateQueryName(), result.getCreateQueryText());
                return;
            }
            if (result.isDone() && result.getCreateDashboardConfig() != null) {
                // The button can be used again, as the user may cancel the dialog.
                button.removeAttribute("disabled");
                createDashboard(result.getCreateDashboardName(), result.getCreateDashboardConfig());
                return;
            }
            if (result.isDone() && result.getCreateVisualisation() != null
                && result.getCreateVisualisation().getReplaced() != null) {
                // Already rewritten: show it in place of the old chart. Using the button again writes it again.
                button.removeAttribute("disabled");
                showReplacement(messageId, actionId, result.getCreateVisualisation());
                return;
            }
            if (result.isDone() && NullSafe.hasItems(result.getCreateVisualisations())) {
                // The button can be used again, as the user may cancel the folder dialog.
                button.removeAttribute("disabled");
                createVisualisations(messageId, actionId, result.getCreateVisualisations());
                return;
            }
            if (result.isDone() && result.getCreateVisualisation() != null) {
                // The button can be used again, as the user may cancel the dialog.
                button.removeAttribute("disabled");
                createVisualisation(messageId, actionId, result.getCreateVisualisation());
                return;
            }
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

    /// Opens Stroom's own New Query dialog with the agent's suggested name, in the folder selected in the Explorer
    /// tree. Once the user creates the query, the agent's StroomQL is put in it and it is opened. Nothing is saved
    /// unless the user creates it.
    private void createQuery(final String name, final String queryText) {
        ShowCreateDocumentDialogEvent.fire(
                this,
                "New Query",
                selectedExplorerNode,
                QueryDoc.TYPE,
                NullSafe.string(name),
                true,
                newNode -> {
                    final DocRef docRef = newNode.getDocRef();
                    askStroomAiClient.setQueryText(docRef, queryText,
                            doc -> openCreatedDocument(docRef),
                            error -> showError(error, "Unable to put the query in " + docRef.getName(), null),
                            getView());
                });
    }

    /// Opens Stroom's own New Dashboard dialog with the agent's suggested name, in the folder selected in the
    /// Explorer tree. Once the user creates the dashboard, it is given the settings Stroom built from the agent's
    /// query and opened. Nothing is saved unless the user creates it.
    private void createDashboard(final String name, final DashboardConfig config) {
        ShowCreateDocumentDialogEvent.fire(
                this,
                "New Dashboard",
                selectedExplorerNode,
                DashboardDoc.TYPE,
                NullSafe.string(name),
                true,
                newNode -> {
                    final DocRef docRef = newNode.getDocRef();
                    askStroomAiClient.setDashboardConfig(docRef, config,
                            doc -> openCreatedDocument(docRef),
                            error -> showError(error, "Unable to set up " + docRef.getName(), null),
                            getView());
                });
    }

    /// Opens Stroom's own New Visualisation dialog with the agent's suggested name, in the dashboard's (or query's)
    /// folder. Once the user creates the Visualisation, it is given the chart Stroom built from the agent's plan,
    /// then added to the open dashboard below (or beside) its table, or put in the query's show clause, unsaved.
    /// Nothing is saved unless the user creates it.
    private void createVisualisation(final int messageId,
                                     final String actionId,
                                     final AiCreateVisualisation create) {
        ShowCreateDocumentDialogEvent.fire(
                this,
                "New Visualisation",
                create.getFolder() != null
                        ? create.getFolder()
                        : selectedExplorerNode,
                VisualisationDoc.TYPE,
                NullSafe.string(create.getName()),
                true,
                newNode -> {
                    final DocRef visualisation = newNode.getDocRef();
                    askStroomAiClient.setVisualisation(visualisation, create,
                            doc -> wireVisualisation(messageId, actionId, create, visualisation),
                            error -> showError(error, "Unable to set up " + visualisation.getName(), null),
                            getView());
                });
    }

    /// Asks the user for one folder (the dashboard's, by default), creates a Visualisation there for each chart
    /// planned together, named as the agent suggested, gives each its chart, then adds them all to the dashboard in
    /// one change, arranged as planned, unsaved. Nothing is created unless the user picks a folder.
    private void createVisualisations(final int messageId,
                                      final String actionId,
                                      final List<AiCreateVisualisation> creates) {
        final DocSelectionPopup chooser = docSelectionPopupProvider.get();
        chooser.setCaption("Folder For " + creates.size() + " New Visualisations");
        chooser.setIncludedTypes(ExplorerConstants.FOLDER_TYPE);
        chooser.setAllowFolderSelection(true);
        chooser.setRequiredPermissions(DocumentPermission.VIEW);
        final ExplorerNode suggested = creates.get(0).getFolder();
        if (suggested != null) {
            chooser.setSelectedEntityReference(suggested.getDocRef());
        }
        chooser.show(folder -> {
            if (folder != null) {
                createNextVisualisation(messageId, actionId, creates,
                        ExplorerNode.builder().docRef(folder).build(), new ArrayList<>());
            }
        });
    }

    // Creates the next of several Visualisations, in turn; once all are, adds them to the dashboard. If one can't be
    // created, those already created are still added, and the user is told which weren't.
    private void createNextVisualisation(final int messageId,
                                         final String actionId,
                                         final List<AiCreateVisualisation> creates,
                                         final ExplorerNode folder,
                                         final List<DocRef> created) {
        if (created.size() == creates.size()) {
            wireVisualisations(messageId, actionId, creates, created);
            return;
        }
        final AiCreateVisualisation create = creates.get(created.size());
        askStroomAiClient.createVisualisation(folder, create,
                visualisation -> {
                    created.add(visualisation);
                    createNextVisualisation(messageId, actionId, creates, folder, created);
                },
                error -> {
                    showError(error, "Unable to create Visualisation '" + create.getName() + "'", null);
                    if (!created.isEmpty()) {
                        wireVisualisations(messageId, actionId, creates.subList(0, created.size()), created);
                    }
                },
                getView());
    }

    // Adds several Visualisations the user has just created to the dashboard they were planned for, in one change
    // (each chart linked to its own table), arranged as planned; then waits for each one's render report.
    private void wireVisualisations(final int messageId,
                                    final String actionId,
                                    final List<AiCreateVisualisation> creates,
                                    final List<DocRef> created) {
        final AiCreateVisualisation first = creates.get(0);
        final List<TableChange> changes = new ArrayList<>();
        for (int i = 0; i < created.size(); i++) {
            final AiCreateVisualisation create = creates.get(i);
            final DocRef visualisation = created.get(i);
            awaitedRenderReports.put(visualisation.getUuid(), new AwaitedReport(messageId, actionId,
                    visualisation.getName()));
            changes.add(TableChange.builder(TableChange.ADD_VIS)
                    .name(create.getComponentName())
                    .table(first.getTableId() != null && first.getTableId().equals(create.getTableId())
                            ? null
                            : create.getTableName())
                    .doc(visualisation)
                    .value(create.getComponentSettings())
                    .placement(create.getPlacement())
                    .build());
        }
        final String done = "Created " + created.size() + " Visualisations";
        final ApplyDashboardChangeEvent event = ApplyDashboardChangeEvent.fireApply(this,
                first.getTarget(),
                first.getTableId(),
                first.getTableName(),
                changes,
                onResult -> onResult.accept(null),
                () -> showUserActionStatus(messageId, actionId, "DONE",
                        done + " and added them to the dashboard, not saved. Save the dashboard to keep them."),
                message -> {
                    if (message != null) {
                        AlertEvent.fireWarn(this, done + ", but they couldn't be added to the dashboard: "
                                                  + message + "\nAdd each with a Visualisation pane linked to its "
                                                  + "table.", null);
                    }
                },
                dashboardUndoKey(messageId, actionId));
        if (!event.isHandled()) {
            AlertEvent.fireWarn(this, done + ", but the dashboard can't be opened here to add them.", null);
        }
    }

    // Puts a Visualisation the user has just created in the show clause of an embedded query on the dashboard it was
    // planned for, by UUID, unsaved, and runs the query; if its StroomQL hasn't changed since.
    private void showInEmbeddedQuery(final int messageId,
                                     final String actionId,
                                     final AiCreateVisualisation create,
                                     final DocRef visualisation,
                                     final String created) {
        final String clause = "show " + UserActionsUi.quoteStroomQl(visualisation.getUuid()) + " ("
                              + create.getShowArguments() + ")";
        final String base = NullSafe.string(create.getBaseQuery());
        final TableChange change = TableChange.builder(TableChange.SET_QUERY)
                .targetId(create.getTableId())
                .value(UserActionsUi.withShowClause(base, clause))
                .ref(base)
                .build();
        final ApplyDashboardChangeEvent event = ApplyDashboardChangeEvent.fireApply(this,
                create.getTarget(),
                create.getTableId(),
                create.getTableName(),
                List.of(change),
                onResult -> onResult.accept(null),
                () -> showUserActionStatus(messageId, actionId, "DONE",
                        created + " and put it in the show clause of embedded query '" + create.getTableName()
                        + "', not saved. Save the dashboard to keep it."),
                message -> {
                    if (message != null) {
                        AlertEvent.fireWarn(this, created + ", but it couldn't be put in embedded query '"
                                                  + create.getTableName() + "': " + message + "\nAdd this clause "
                                                  + "at the end of its StroomQL to show it:\n" + clause, null);
                    }
                },
                dashboardUndoKey(messageId, actionId));
        if (!event.isHandled()) {
            AlertEvent.fireInfo(this, created + ". To show it, open Dashboard '"
                                      + NullSafe.get(create.getTarget(), DocRef::getName) + "' and add this clause at "
                                      + "the end of embedded query '" + create.getTableName() + "':\n" + clause,
                    null);
        }
    }

    /// Shows a Visualisation Stroom AI has just rewritten for a new chart in the vis component that showed the old
    /// one, unsaved: its settings become the new chart's columns (and its name, if that changes). Each vis
    /// component loads the Visualisation again, as its settings and controls have changed.
    private void showReplacement(final int messageId,
                                 final String actionId,
                                 final AiCreateVisualisation create) {
        final DocRef visualisation = create.getReplaced();
        awaitedRenderReports.put(visualisation.getUuid(), new AwaitedReport(messageId, actionId,
                visualisation.getName()));
        ClearFunctionCacheEvent.fire(this, visualisation);
        final List<TableChange> changes = new ArrayList<>();
        changes.add(TableChange.builder(TableChange.SET_VIS_SETTINGS)
                .targetId(create.getReplaceComponentId())
                .doc(visualisation)
                .value(create.getComponentSettings())
                .build());
        if (create.getComponentName() != null) {
            changes.add(TableChange.builder(TableChange.RENAME)
                    .targetId(create.getReplaceComponentId())
                    .name(create.getComponentName())
                    .build());
        }
        // The filters its clicks drive follow it, where it selects the same column under another key.
        changes.addAll(operations(create.getFilterChanges()));
        final String replaced = "Rewrote Visualisation '" + visualisation.getName() + "' for the new chart (saved)";
        final ApplyDashboardChangeEvent event = ApplyDashboardChangeEvent.fireApply(this,
                create.getTarget(),
                create.getTableId(),
                create.getTableName(),
                changes,
                onResult -> onResult.accept(null),
                () -> showUserActionStatus(messageId, actionId, "DONE",
                        replaced + " and showed it in its pane, not saved. Save the dashboard to keep the pane's "
                        + "new settings."),
                message -> {
                    if (message != null) {
                        AlertEvent.fireWarn(this, replaced + ", but its pane couldn't be updated: " + message
                                                  + "\nOpen the pane's settings to pick its columns.", null);
                    }
                },
                dashboardUndoKey(messageId, actionId));
        if (!event.isHandled()) {
            AlertEvent.fireInfo(this, replaced + ". Open the dashboard to see it.", null);
        }
    }

    /// Adds a Visualisation the user has just created to the dashboard it was planned for, or puts it in the
    /// query's show clause, unsaved; then waits for its render report to say how it drew.
    private void wireVisualisation(final int messageId,
                                   final String actionId,
                                   final AiCreateVisualisation create,
                                   final DocRef visualisation) {
        awaitedRenderReports.put(visualisation.getUuid(), new AwaitedReport(messageId, actionId,
                visualisation.getName()));
        final String created = "Created Visualisation '" + visualisation.getName() + "'";
        if (create.isForQuery() && create.getTarget() != null
            && DashboardDoc.TYPE.equals(create.getTarget().getType())) {
            showInEmbeddedQuery(messageId, actionId, create, visualisation, created);
            return;
        }
        if (create.isForQuery()) {
            // By UUID, which Stroom resolves before a name, so another Visualisation of the same name can't
            // make the query ambiguous.
            final String clause = "show " + UserActionsUi.quoteStroomQl(visualisation.getUuid()) + " ("
                                  + create.getShowArguments() + ")";
            final String base = NullSafe.string(create.getBaseQuery());
            final QueryCodeChangeEvent event = QueryCodeChangeEvent.fireApply(this,
                    create.getTarget(),
                    base,
                    UserActionsUi.withShowClause(base, clause),
                    (andStep, rejected, onResult) -> onResult.accept(null),
                    () -> showUserActionStatus(messageId, actionId, "DONE",
                            created + " and put it in the query's show clause, not saved. Run the query to see "
                            + "it; save the query to keep it."));
            if (!event.isHandled()) {
                OpenDocumentEvent.fire(this, create.getTarget(), true);
                AlertEvent.fireInfo(this, created + ". To show it, open Query '"
                                          + NullSafe.get(create.getTarget(), DocRef::getName)
                                          + "' and add this clause at its end:\n" + clause, null);
            } else if (event.getMessage() != null) {
                AlertEvent.fireInfo(this, created + ". " + event.getMessage() + "\nAdd this clause at the "
                                          + "query's end to show it:\n" + clause, null);
            }
            return;
        }

        // A table of its own is added first (beside the table with tableId), and its filter last, once it is on the
        // dashboard.
        final List<TableChange> tableChanges = operations(create.getTableChanges());
        final List<TableChange> filterChanges = operations(create.getFilterChanges());
        final boolean newTable = !tableChanges.isEmpty();
        final List<TableChange> changes = new ArrayList<>(tableChanges);
        changes.add(TableChange.builder(TableChange.ADD_VIS)
                .name(create.getComponentName())
                .table(newTable
                        ? create.getTableName()
                        : null)
                .doc(visualisation)
                .value(create.getComponentSettings())
                .placement(create.getPlacement())
                .build());
        changes.addAll(filterChanges);
        final String added = created + " and added it to the dashboard" + (newTable
                ? " with table '" + create.getTableName() + "'"
                : "") + (filterChanges.isEmpty()
                ? ""
                : ", its clicks filtering it") + ", not saved. Save the dashboard to keep it.";
        final ApplyDashboardChangeEvent event = ApplyDashboardChangeEvent.fireApply(this,
                create.getTarget(),
                create.getTableId(),
                // A new table is found by its name among those the changes add, not as the table with tableId.
                newTable
                        ? null
                        : create.getTableName(),
                changes,
                onResult -> onResult.accept(null),
                () -> showUserActionStatus(messageId, actionId, "DONE", added),
                message -> {
                    if (message != null) {
                        AlertEvent.fireWarn(this, created + ", but it couldn't be added to the dashboard: "
                                                  + message + "\nAdd it with a Visualisation pane linked to table '"
                                                  + create.getTableName() + "'.", null);
                    }
                },
                dashboardUndoKey(messageId, actionId));
        if (!event.isHandled()) {
            AlertEvent.fireWarn(this, created + ", but the dashboard can't be opened here to add it.", null);
        }
    }

    /// @param json Dashboard changes as the server gives them: a JSON array of operations, or null.
    /// @return The changes, for the dashboard to apply; none if null.
    private static List<TableChange> operations(final String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return operations(JSONParser.parseStrict(json).isArray());
    }

    private static List<TableChange> operations(final JSONArray operations) {
        final List<TableChange> changes = new ArrayList<>();
        for (int i = 0; operations != null && i < operations.size(); i++) {
            final JSONObject operation = operations.get(i).isObject();
            if (operation != null) {
                final String operationJson = operation.toString();
                changes.add(UserActionsUi.tableChange(name -> AiToolMessages.getString(operationJson, name),
                        AiToolMessages.toJava(operation.get("query")), AiToolMessages.toJava(operation.get("doc"))));
            }
        }
        return changes;
    }

    /// Shows how a Visualisation the user created from the chat drew, on the button's card: what it drew, or
    /// its error with a follow-up to ask the agent to fix it. Only the first report on the table's rows after it
    /// is wired up is shown; later ones (each time the table updates) reach the agent through the dashboard's
    /// description.
    private void onRenderReport(final VisRenderReportEvent event) {
        final DocRef visualisation = event.getVisualisation();
        final AwaitedReport awaited = visualisation != null
                ? awaitedRenderReports.get(visualisation.getUuid())
                : null;
        if (awaited == null) {
            return;
        }
        final Map<String, Object> report = renderReport(event.getReportJson());
        final String status = String.valueOf(report.get("status"));
        final String summary = UserActionsUi.renderReportSummary(report);
        showUserActionStatus(awaited.messageId(), awaited.actionId(), "DONE", summary);
        if ("empty".equals(status)) {
            // Drawn before the table's rows arrived: wait for the report on them.
            return;
        }
        awaitedRenderReports.remove(visualisation.getUuid());
        if (!"ok".equals(status) && !currentRunActive && !requestInFlight) {
            followUps = List.of("Fix the visualisation '" + awaited.name() + "': " + summary);
            refreshFollowUps();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> renderReport(final String json) {
        try {
            final Object report = AiToolMessages.toJava(JSONParser.parseStrict(json));
            return report instanceof Map
                    ? (Map<String, Object>) report
                    : Map.of();
        } catch (final RuntimeException e) {
            return Map.of();
        }
    }

    /// Opens a document the user has just created with a button in the chat, and attaches its tab to the chat once
    /// it opens, in place of any tab attached before.
    private void openCreatedDocument(final DocRef docRef) {
        attachWhenOpened = docRef;
        attachWhenOpenedChatKey = chatKey();
        OpenDocumentEvent.fire(this, docRef, true);
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
        if (heldUserActions.containsKey(messageId)) {
            // Not shown yet: shown as it now is, once the agent stops.
            heldUserActions.put(messageId, updated);
            return;
        }
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
        askStroomAiClient.getConfig(config -> {
            docSelectionBoxPresenter.setSelectedEntityReference(config.getModelRef(), true);
            AiDisplayOptions.apply(getView().getMarkdownContainer().getElement(), config);
        }, this);
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
        answerFollowUps = List.of();
        getView().setFollowUps(List.of());
        getView().setSendButtonLoadingState(true);
        getView().setEmptyState(false);
        getView().clearContextIndicator();

        followBottom = true;
        // Suggestions for what was attached that arrive now are too late.
        attachedSuggestionsSequence++;
        attachmentRequested = false;
        // The tables attached are sent with the message, so are shown in its bubble rather than above the input.
        sendingAttachments = new ArrayList<>(pendingAttachments.values());
        pendingAttachments.clear();
        refreshAttachmentList();
        appendPendingQuestion(message);

        // Scroll markdown container to bottom, so the user's message is displayed
        final SimplePanel markdownContainer = getView().getMarkdownContainer();
        markdownContainer.getElement().setScrollTop(markdownContainer.getElement().getScrollHeight());

        ensureChat(() -> askStroomAiClient.getConfig(config -> {
            final int chatId = currentChat.getId();
            carryNewChatTab(chatId);
            // The attached tab as the user sees it now, or only its hash if it hasn't changed since last sent.
            final AiTabSnapshot tabSnapshot = attachedTabSnapshot();
            final AskStroomAiRequest request = new AskStroomAiRequest(
                    currentChat, config, data, message, getView().getAgentMode(),
                    AiTabSnapshots.toSend(tabSnapshot, lastSentTabHashes.get(chatId)));
            // Clear context so follow-up messages don't re-attach the same data.
            this.data = null;
            requestInFlight = true;
            askStroomAiClient.sendMessage(request,
                    response -> {
                        requestInFlight = false;
                        if (response != null && response.isTabSnapshotNeeded()) {
                            lastSentTabHashes.remove(chatId);
                        } else if (tabSnapshot != null) {
                            // Kept with the message, whether it is a question or steers the chat's run.
                            lastSentTabHashes.put(chatId, tabSnapshot.getHash());
                        }
                        // Poll to get the properly typed, persisted messages. Also picks up a partial
                        // answer, where the user cancelled part way through. As after answering a form: a message
                        // sent while one waited puts it aside, and the run carries on, though it may still show
                        // as waiting for the user for a moment.
                        pollAfterAnswer();
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
    // True if the user has text selected, as when dragging over a code span to copy part of it.
    private static native boolean hasSelection() /*-{
        var selection = $wnd.getSelection ? $wnd.getSelection() : null;
        return !!selection && selection.toString().length > 0;
    }-*/;

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
                    // Asked with the attached tab as it is now, which may be the one the user meant, in full.
                    final AiTabSnapshot tabSnapshot = attachedTabSnapshot();
                    final EditAiMessageRequest request = new EditAiMessageRequest(currentChat, config, messageId,
                            newText, tabSnapshot);
                    getView().setSendButtonLoadingState(true);
                    askStroomAiClient.editMessage(request, response -> {
                        rememberSentTab(currentChat.getId(), tabSnapshot);
                        // The question and what follows are replaced; the new question is shown now, until
                        // polling brings the stored one.
                        removeMessageElementsFrom(messageId, false);
                        followBottom = true;
                        // The edited question replaces this one, so has its attachments, and those attached since
                        // (shown above the input box) are sent with it.
                        sendingAttachments = new ArrayList<>(sentAttachments.getOrDefault(messageId, List.of()));
                        pendingAttachments.values().stream()
                                .filter(pending -> !sendingAttachments.contains(pending))
                                .forEach(sendingAttachments::add);
                        pendingAttachments.clear();
                        refreshAttachmentList();
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
            // Asked with the attached tab as it is now, in full.
            final AiTabSnapshot tabSnapshot = attachedTabSnapshot();
            final EditAiMessageRequest request = new EditAiMessageRequest(currentChat, config, turnId, null,
                    tabSnapshot);
            askStroomAiClient.editMessage(request, response -> {
                rememberSentTab(currentChat.getId(), tabSnapshot);
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
                final int id = AiConversationEntries.messageIdOf(idStr);
                if (id > fromMessageId || (id == fromMessageId && !keepFirst)) {
                    child.removeFromParent();
                    renderedMessages.remove(id);
                }
            }
            child = next;
        }
        refreshOverview();
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
        removeAllInConversation(RUN_STATUS_ID);
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
        askStroomAiClient.pollMessages(chatId, lastSeenMessageId, isLooking(), watchedMessageIds(), response -> {
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
            updateShareActiveTab(response);
            // And forms the server has answered for the user, e.g. cancelled as the run stopped.
            updateWatchedMessages(response.getWatchedMessages());

            // Say if the run stopped before finishing, and offer to resume it.
            updateRunStatus(response.getRun());
            currentRunActive = !response.isComplete();
            showWorking(response);
            refreshFollowUps();

            // Show what the server is currently doing, and take the line away when it is done.
            updateWorkingMessage(response.getWorkingMessage());

            // Show the answer and reasoning as the model writes them.
            final boolean streaming = updateStreaming(response.getStreamingMessages(), response.isComplete());

            // Keep polling while this client is waiting on a request, while the chat's agent run is
            // still active or data is still coming down (the server reports both as not complete), or
            // while messages are still arriving.
            // A run waiting for the user (e.g. to approve something) does nothing until they answer, which starts
            // polling again, so there is no need to keep asking, once what it asks is shown (see AiPolling).
            final boolean runWaiting = response.getRun() != null
                                       && response.getRun().getStatus() == AiChatRunStatus.WAITING_FOR_USER;
            final boolean promptShown = renderedMessages.values().stream()
                    .anyMatch(AskStroomAiPresenter::isWaitingForAnswer);
            final long nowMs = System.currentTimeMillis();
            if (!runWaiting || promptShown) {
                waitingWithoutPromptSinceMs = 0;
            } else if (waitingWithoutPromptSinceMs == 0) {
                waitingWithoutPromptSinceMs = nowMs;
            }
            final boolean waitingForUser = AiPolling.canStopForUser(runWaiting, promptShown,
                    waitingWithoutPromptSinceMs, keepPollingUntilMs, nowMs);
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

    /// Shows whether the chat is working, with the input box glowing and the Stop button, from what a poll found.
    /// It is working while its run calls tools or the model, or data is still coming down. It isn't while it waits
    /// for the user, e.g. to answer something, or once an error has ended the question, as nothing happens until
    /// the user does something.
    private void showWorking(final AiChatPollResponse response) {
        final boolean needsUser = response.getRun() != null
                                  && response.getRun().getStatus() == AiChatRunStatus.WAITING_FOR_USER;
        final boolean working = !response.isComplete() && !needsUser && !endedByError();
        if (working != overviewWorking) {
            overviewWorking = working;
            refreshOverview();
        }
        getView().setInProgress(working);
        getView().setSendButtonLoadingState(requestInFlight || working);
        if (!working && !requestInFlight) {
            showHeldUserActions();
        }
    }

    // True if the latest message shown is an error, which ends the question it was answering.
    private boolean endedByError() {
        AiChatMessage latest = null;
        for (final AiChatMessage msg : renderedMessages.values()) {
            if (latest == null || msg.getId() > latest.getId()) {
                latest = msg;
            }
        }
        return latest != null && latest.getMessageType() == AiMessageType.ERROR;
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
        addAttachmentChips(element, sendingAttachments);
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
            answerFollowUps = parseFollowUps(msg.getMessage());
            followUps = answerFollowUps;
            refreshFollowUps();
            return;
        }
        if (msg.getMessageType() == AiMessageType.TASK_LIST) {
            // Not part of the conversation: shown above the input, as the latest for the turn.
            showTaskList(msg.getMessage());
            return;
        }
        if (msg.getMessageType() == AiMessageType.USER_MESSAGE || isSteer(msg)) {
            followUps = List.of();
            answerFollowUps = List.of();
            refreshFollowUps();
        }
        if (msg.getMessageType() == AiMessageType.USER_MESSAGE) {
            // A new question has a task list of its own, if any.
            getView().setTaskList(null, SafeHtmlUtils.EMPTY_SAFE_HTML);
        }
        if (msg.getMessageType() == AiMessageType.ATTACHMENT) {
            // Not part of the conversation until it is sent: listed above the input box.
            renderedMessages.put(msg.getId(), msg);
            if (sentAttachments.values().stream().noneMatch(sent -> sent.contains(msg))) {
                pendingAttachments.put(msg.getId(), msg);
                refreshAttachmentList();
                if (attachmentRequested) {
                    // Once its status (e.g. which document it is) has arrived too, and any others attached with it.
                    attachedSuggestionsTimer.schedule(ATTACHED_SUGGESTIONS_DELAY_MS);
                }
            }
            return;
        }
        if ((msg.getMessageType() == AiMessageType.USER_MESSAGE || isSteer(msg))
            && !sentAttachments.containsKey(msg.getId())) {
            // The tables attached before it were sent with it.
            final List<AiChatMessage> sent = new ArrayList<>(sendingAttachments);
            pendingAttachments.values().stream().filter(pending -> !sent.contains(pending)).forEach(sent::add);
            sentAttachments.put(msg.getId(), sent);
            pendingAttachments.clear();
            sendingAttachments = List.of();
            refreshAttachmentList();
        }
        renderedMessages.put(msg.getId(), msg);
        refreshOverview();
        final HtmlBuilder hb = new HtmlBuilder();
        renderMessage(hb, msg, nowMs);
        final Element holder = parseHtml(hb);
        final Element messageElement = holder.getFirstChildElement();
        if (messageElement == null) {
            return;
        }
        final String messageId = String.valueOf(msg.getId());
        messageElement.setAttribute(MESSAGE_ID_ATTRIBUTE, messageId);
        addAttachmentChips(messageElement, sentAttachments.get(msg.getId()));
        decorateCodeBlocks(messageElement);
        decorateStroomLinks(messageElement);

        final Element markdownContainer = getView().getMarkdownContainer().getElement();
        if (msg.getMessageType() == AiMessageType.TOOL_RESULT
            && findMessageElement(markdownContainer, messageId) == null
            && AiConversationEntries.addResultToCall(markdownContainer, messageElement, msg.getMessage())) {
            // Shown in its call's entry.
            showOrHoldUserActions(markdownContainer, msg, nowMs);
            keepProgressInPlace(markdownContainer);
            return;
        }
        if (msg.getMessageType() == AiMessageType.TOOL_CALL) {
            AiConversationEntries.markCall(messageElement, AiToolMessages.getString(msg.getMessage(), "callId"));
        } else if (msg.getMessageType() == AiMessageType.THINKING) {
            // Reasoning that has arrived replaces the live view of it.
            AiConversationEntries.removeLiveThinking(markdownContainer);
        } else if (msg.getMessageType() == AiMessageType.INTERIM_RESPONSE
                   || msg.getMessageType() == AiMessageType.AI_RESPONSE
                   || msg.getMessageType() == AiMessageType.ERROR) {
            // The text being written is now stored, so is shown in its place, after what was said before.
            removeLiveAnswer();
        }
        if (msg.getMessageType() == AiMessageType.INTERIM_RESPONSE
            || msg.getMessageType() == AiMessageType.ELICITATION
            || msg.getMessageType() == AiMessageType.APPROVAL_REQUEST
            || msg.getMessageType() == AiMessageType.AI_RESPONSE
            || msg.getMessageType() == AiMessageType.ERROR) {
            // What the model said before comes before what it says now, what it asks the user, and its answer.
            showInterimInConversation();
        }
        Element existing = findMessageElement(markdownContainer, messageId);
        if (existing == null && (msg.getMessageType() == AiMessageType.USER_MESSAGE || isSteer(msg))) {
            existing = findMessageElement(markdownContainer, PENDING_MESSAGE_ID);
        }
        if (existing != null) {
            markdownContainer.replaceChild(messageElement, existing);
        } else {
            appendDayDivider(markdownContainer, msg.getCreateTimeMs(), nowMs);
            markdownContainer.appendChild(messageElement);
        }
        if (isWaitingForAnswer(msg)) {
            watchPrompt(messageElement);
        } else if (existing != null && existing == watchedPrompt) {
            // Answered, so no longer grows as it is filled in.
            unwatchPrompt();
        }
        if (msg.getMessageType() == AiMessageType.TOOL_RESULT) {
            showOrHoldUserActions(markdownContainer, msg, nowMs);
        } else if (msg.getMessageType() == AiMessageType.INTERIM_RESPONSE
                   && findInConversation(WORKING_MESSAGE_ID) != null) {
            // While the agent works, shown once the Working line moves on from it (see updateWorkingMessage).
            interimShownAsProgress = msg;
            messageElement.addClassName(SHOWN_AS_PROGRESS_CLASS);
        }
        if (msg.getMessageType() == AiMessageType.COMPACTION_SUMMARY) {
            showUndoOnLatestCompaction();
        }
        keepProgressInPlace(markdownContainer);
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
        answerFollowUps = List.of();
        refreshFollowUps();
        renderedMessages.clear();
        heldUserActions.clear();
        pendingAttachments.clear();
        sentAttachments.clear();
        sendingAttachments = List.of();
        attachmentStatuses.clear();
        refreshAttachmentList();
        unwatchPrompt();
        refreshOverview();
        interimShownAsProgress = null;
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
            // Something was added below, so it glides to it, for the user to follow the conversation as it grows.
            scrollToLatest(true);
        } else {
            getView().setJumpToLatestVisible(true);
        }
    }

    @Override
    public void onConversationScrolled(final boolean byUser) {
        final Element container = getView().getMarkdownContainer().getElement();
        if (container.getClientHeight() == 0) {
            // Hidden (e.g. another Stroom tab is shown), when the browser puts it back to the top: not the user
            // scrolling, so it is where they left it that counts.
            return;
        }
        final int scrollTop = container.getScrollTop();
        // Only the user scrolling changes whether the conversation is followed: scrolling up stops it, and scrolling
        // to the bottom starts it again. The browser moves it too, e.g. holding it at the bottom as a section above
        // closes, which would otherwise start following again while the user reads something else.
        if (byUser) {
            if (isAtBottom()) {
                followBottom = true;
            } else if (scrollTop < lastScrollTop) {
                followBottom = false;
            }
        }
        lastScrollTop = scrollTop;
        getView().setJumpToLatestVisible(!followBottom);
    }

    @Override
    public void onSectionToggled(final boolean open) {
        // Opening a section grows the conversation without scrolling it, so it would still be followed, and the
        // next new message would carry what the user opened up out of view. They are reading it, so it stays put.
        if (open && followBottom) {
            followBottom = false;
            getView().setJumpToLatestVisible(true);
        }
    }

    @Override
    public void onConversationResized() {
        final Element container = getView().getMarkdownContainer().getElement();
        if (container.getClientHeight() == 0) {
            return;
        }
        // The view getting shorter leaves the scroll position where it was, which hides the latest messages
        // behind the bottom of the view rather than pushing them up. And shown again (e.g. back from another
        // Stroom tab), the browser has put it back at the top: it goes straight to where the user left it.
        if (followBottom) {
            scrollToLatest(false);
        } else if (container.getScrollTop() != lastScrollTop) {
            scrollInstantly(container, lastScrollTop);
        }
    }

    @Override
    public void onJumpToLatest() {
        followBottom = true;
        getView().setJumpToLatestVisible(false);
        // Asked for, so it glides there. Where it starts from is where it was, so the glide's own scrolling, down,
        // isn't taken as the user scrolling up.
        final Element markdownContainer = getView().getMarkdownContainer().getElement();
        lastScrollTop = markdownContainer.getScrollTop();
        markdownContainer.setScrollTop(markdownContainer.getScrollHeight());
    }

    // Keeps the latest message in view. Content added below is glided to, so the user can follow it, but only over
    // a short way (at most MAX_GLIDE_VIEWS of the view's height): from further (e.g. the top, when the chat is loaded
    // or shown again), or when the view changed size, a glide would only be in the way, so it goes straight there.
    // The glide follows the conversation's CSS (smooth, unless the user prefers reduced motion), and its own
    // scrolling isn't the user's, so it doesn't stop the conversation being followed.
    private void scrollToLatest(final boolean glide) {
        if (getView().isUserScrolling()) {
            // Not from under the user: they are scrolling it themselves, which decides whether it is followed.
            return;
        }
        getView().setJumpToLatestVisible(false);
        final Element markdownContainer = getView().getMarkdownContainer().getElement();
        final int clientHeight = markdownContainer.getClientHeight();
        final int distance = markdownContainer.getScrollHeight() - clientHeight - markdownContainer.getScrollTop();
        if (glide && distance > 0 && distance <= clientHeight * MAX_GLIDE_VIEWS) {
            markdownContainer.setScrollTop(markdownContainer.getScrollHeight());
        } else {
            scrollInstantly(markdownContainer, markdownContainer.getScrollHeight());
        }
        lastScrollTop = markdownContainer.getScrollTop();
    }

    // Scrolls without the conversation's smooth scrolling.
    private static native void scrollInstantly(Element element, int top) /*-{
        element.scrollTo({top: top, behavior: "instant"});
    }-*/;

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
        final boolean deletable = msg.getMessageType() == AiMessageType.USER_MESSAGE;
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
            case QUEUED_STEER_MESSAGE:
            case SEND_NOW_STEER_MESSAGE:
                appendQueuedSteer(hb, msg, timeMs, nowMs);
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
                // Shown as a chip, above the input box or in the bubble of the message it was sent with.
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
        // What the user typed is shown as typed (markup in it too); what is copied stays as they typed it.
        final SafeHtml markdownHtml = markdownConverter.convertUntrustedMarkdownToHtml(
                cssClass.contains("ai-message--user")
                        ? AiUserMarkdown.escapeHtml(markdownText)
                        : markdownText);
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
     * Appends a message the user sent while the run was working, which waits for the run's current step to finish,
     * with a button to send it now. Once the run picks it up, it is shown as any other message.
     */
    private void appendQueuedSteer(final HtmlBuilder hb,
                                   final AiChatMessage msg,
                                   final long timeMs,
                                   final long nowMs) {
        final SafeHtml markdownHtml = markdownConverter.convertUntrustedMarkdownToHtml(
                AiUserMarkdown.escapeHtml(msg.getMessage()));
        final boolean sendingNow = msg.getMessageType() == AiMessageType.SEND_NOW_STEER_MESSAGE;
        hb.div(wrapper -> {
            wrapper.div(content -> content.append(markdownHtml), Attribute.className("ai-message-content"));
            wrapper.div(status -> {
                status.span(sendingNow
                        ? "Sending now…"
                        : "Waiting for the current step to finish", Attribute.className("ai-queued-steer-text"));
                if (!sendingNow) {
                    button(status,
                            SvgImage.FAST_FORWARD,
                            iconButtonClassName("allow-focus ai-queued-steer-send"),
                            Attribute.title("Send now, without waiting for the current step to finish"),
                            new Attribute(SEND_STEER_NOW_ATTRIBUTE, String.valueOf(msg.getId())));
                }
            }, Attribute.className("ai-queued-steer-status"));
            wrapper.div(footer -> {
                button(footer, SvgImage.COPY,
                        iconButtonClassName("ai-message-copy-icon"),
                        Attribute.title("Copy as Markdown"),
                        new Attribute("data", msg.getMessage()));
                timestamp(footer, timeMs, nowMs);
            }, Attribute.className("ai-message-footer"));
        }, Attribute.className("ai-message ai-message--user ai-message--queued"));
    }

    private void onSendSteerNow(final Element button, final int messageId) {
        if (currentChat == null) {
            return;
        }
        button.setPropertyBoolean("disabled", true);
        // Shown as sending once the server says so; if the run had already picked it up, it is shown as sent.
        askStroomAiClient.sendSteerNow(currentChat.getId(), messageId, sent -> startPolling(), getView());
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
     * Renders the WORKING message with stable DOM ids, so that polling can update the progress text
     * where it stands rather than appending a new line every time it changes. The text is in the
     * summary because that is the part that is visible without expanding it.
     */
    private void appendWorkingMessage(final HtmlBuilder hb,
                                      final AiChatMessage msg,
                                      final long timeMs,
                                      final long nowMs) {
        hb.elem(details -> {
            // Lined up with the conversation's entries, so no icon.
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
     * tool calls is worth seeing while they run. Reasoning shows as it is written, above the Working line.
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
                    AiConversationEntries.showLiveThinking(markdownContainer, msg.getMessage(), html);
                } else if (msg.getMessageType() == AiMessageType.STREAMING_ANSWER) {
                    showLiveAnswer(markdownContainer, html);
                }
            }
        }
        if (!thinking) {
            AiConversationEntries.removeLiveThinking(markdownContainer);
        }
        if (complete) {
            removeLiveAnswer();
        }
        keepProgressInPlace(markdownContainer);
        if (any) {
            scrollToBottom();
        }
        return any;
    }

    private void showLiveAnswer(final Element markdownContainer, final SafeHtml html) {
        Element live = findInConversation(LIVE_ANSWER_ID);
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
     * Keeps the lines saying what is happening now at the end of the conversation as messages arrive, with the
     * Working line last.
     */
    private void keepProgressInPlace(final Element markdownContainer) {
        AiConversationEntries.keepProgressAtEnd(markdownContainer);
    }

    private void removeLiveAnswer() {
        removeAllInConversation(LIVE_ANSWER_ID);
    }

    /// @return The element of the conversation with an id, or null if it has none. Looked for in the conversation
    /// itself, not the page, which it isn't part of while another Stroom tab is shown: looking in the page then
    /// found nothing, so a second Working line was added, and one was left behind once the run moved on.
    private Element findInConversation(final String id) {
        return firstMatch(getView().getMarkdownContainer().getElement(), "[id=\"" + id + "\"]");
    }

    /// Takes away the elements of the conversation with an id: all of them, should there be more than one.
    private void removeAllInConversation(final String id) {
        removeAllMatches(getView().getMarkdownContainer().getElement(), "[id=\"" + id + "\"]");
    }

    private static native Element firstMatch(Element root, String selector) /*-{
        return root.querySelector(selector);
    }-*/;

    private static native void removeAllMatches(Element root, String selector) /*-{
        var matches = root.querySelectorAll(selector);
        for (var i = 0; i < matches.length; i++) {
            matches[i].parentNode.removeChild(matches[i]);
        }
    }-*/;

    /**
     * Adds, updates or removes the progress line to match what the server says it is doing.
     */
    private void updateWorkingMessage(final AiChatMessage workingMessage) {
        final Element existing = findInConversation(WORKING_MESSAGE_ID);

        if (workingMessage == null) {
            // Nothing is being worked on, so the progress line has served its purpose (any left from before too).
            removeAllInConversation(WORKING_MESSAGE_ID);
        } else if (existing == null) {
            final HtmlBuilder hb = new HtmlBuilder();
            appendWorkingMessage(hb, workingMessage,
                    workingMessage.getCreateTimeMs(), System.currentTimeMillis());
            appendToContainer(hb);
            keepProgressInPlace(getView().getMarkdownContainer().getElement());
        } else {
            // Only the one: any other is one left from before.
            final Element container = getView().getMarkdownContainer().getElement();
            removeAllMatches(container, "[id=\"" + WORKING_MESSAGE_ID + "\"] ~ [id=\"" + WORKING_MESSAGE_ID + "\"]");
            final Element text = findInConversation(WORKING_TEXT_ID);
            if (text != null) {
                text.setInnerText(workingText(workingMessage));
            }
        }
        // What the model said is shown on the Working line until the line moves on to its next step, so it isn't
        // shown twice.
        if (interimShownAsProgress != null
            && !AiConversationEntries.isLeftToWorkingLine(interimShownAsProgress.getMessageType(),
                interimShownAsProgress.getMessage(),
                workingMessage != null
                        ? workingText(workingMessage)
                        : null)) {
            showInterimInConversation();
        }
    }

    /// Shows what the model said alongside its latest tool calls in its place in the conversation, if it was
    /// hidden while the Working line showed it.
    private void showInterimInConversation() {
        if (interimShownAsProgress == null) {
            return;
        }
        final Element element = findMessageElement(getView().getMarkdownContainer().getElement(),
                String.valueOf(interimShownAsProgress.getId()));
        interimShownAsProgress = null;
        if (element != null) {
            element.removeClassName(SHOWN_AS_PROGRESS_CLASS);
            scrollToBottom();
        }
    }

    private String workingText(final AiChatMessage msg) {
        return NullSafe.getOrElse(msg, AiChatMessage::getMessage, "Working...");
    }

    /**
     * Keeps the attachments' chips up to date with their statuses.
     */
    private void updateAttachmentStatuses(final java.util.List<AiChatAttachment> attachments) {
        if (attachments == null) {
            return;
        }
        for (final AiChatAttachment attachment : attachments) {
            attachmentStatuses.put(attachment.getId(), attachment);
        }
        if (documentTypes == null && attachments.stream().anyMatch(AiAttachments::isDocument)) {
            fetchDocumentTypes();
        }
        // And the chips of those sent with a message, e.g. sent before they finished downloading.
        refreshAttachmentChips();
    }

    /// Follows the tab the user is on, which they can attach to the chat while it is a document the chat can be told
    /// about. The chat's own tab is passed over, so the tab the user was on before is still offered.
    private void onActiveTabChange(final TabData tabData) {
        if (tabPresenter != null && tabData == tabPresenter) {
            return;
        }
        currentTab = tabData instanceof AiTabSnapshotProvider && tabData instanceof DocumentTabData
                ? tabData
                : null;
        if (attachWhenOpened != null
            && currentTab instanceof final DocumentTabData documentTab
            && attachWhenOpened.equals(documentTab.getDocRef())) {
            // The document the user just created from the chat has opened.
            attachWhenOpened = null;
            attachedTabs.put(attachWhenOpenedChatKey, currentTab);
            refreshAttachmentList();
            if (attachWhenOpenedChatKey == chatKey()) {
                // What can be done with it now, e.g. refining it from the chat.
                offerAttachedSuggestions(true);
            }
            return;
        }
        refreshAttachmentList();
    }

    /// Forgets a closed tab: it is no longer attached to any chat or offered.
    private void onTabClosed(final TabData tabData) {
        if (tabData == null) {
            return;
        }
        final boolean wasAttached = attachedTabs.get(chatKey()) == tabData;
        attachedTabs.values().removeIf(tab -> tab == tabData);
        if (currentTab == tabData) {
            currentTab = null;
        }
        refreshAttachmentList();
        if (wasAttached) {
            onDetached();
        }
    }

    /// Attaches the tab the user is on to the chat, in place of any attached before. It is shared with each
    /// question, as the user sees it then, until they remove it or close it.
    private void attachCurrentTab() {
        if (currentTab != null) {
            attachedTabs.put(chatKey(), currentTab);
            refreshAttachmentList();
            // The user attaches a tab to ask about it, so they are offered what can be done with that kind of tab,
            // in place of any follow-ups to the last answer, and can type straight away.
            offerAttachedSuggestions(true);
            getView().focus();
        }
    }

    /// Offers what the user might ask about everything attached (the tab, documents and tables): at once, from what
    /// kind of thing each is (most recently attached first), then the model's suggestions, which take account of
    /// what the user has been asking and suggest using several things together, if the chat has a conversation to
    /// go on or several things are attached.
    ///
    /// @param tabFirst True if the tab was the last thing attached, false if documents or tables were.
    private void offerAttachedSuggestions(final boolean tabFirst) {
        final AiTabSnapshot tab = attachedTabSnapshot();
        final List<String> suggestions = attachedSuggestions(tab, tabFirst);
        if (!suggestions.isEmpty()) {
            followUps = suggestions;
            refreshFollowUps();
        }
        final int sequence = ++attachedSuggestionsSequence;
        final List<Integer> attachmentIds = new ArrayList<>();
        pendingAttachmentStatuses().forEach(attachment -> attachmentIds.add(attachment.getId()));
        final int attachedCount = attachmentIds.size() + (tab != null
                ? 1
                : 0);
        if (currentChat == null || suggestions.isEmpty() || (!hasConversation() && attachedCount < 2)) {
            return;
        }
        final AiChat chat = currentChat;
        askStroomAiClient.getConfig(config -> askStroomAiClient.suggestPrompts(
                new SuggestAiPromptsRequest(chat, config, attachmentIds, AiSuggestions.outline(tab)),
                suggested -> {
                    // Only if nothing has been attached or sent since, and the chat is still the one shown.
                    if (sequence == attachedSuggestionsSequence && currentChat == chat
                        && !NullSafe.isEmptyCollection(suggested)) {
                        followUps = suggested;
                        refreshFollowUps();
                    }
                }), getView());
    }

    /// @param tab      The attached tab, as the user sees it, or null.
    /// @param tabFirst True if the tab was the last thing attached, false if documents or tables were.
    /// @return What the user might ask about what is attached, from what kind of thing each is, the most recently
    /// attached first.
    private List<String> attachedSuggestions(final AiTabSnapshot tab, final boolean tabFirst) {
        final List<List<String>> groups = new ArrayList<>();
        for (final AiChatAttachment attachment : pendingAttachmentStatuses()) {
            groups.add(AiAttachments.isDocument(attachment)
                    ? AiSuggestions.forDocument(attachment.getDocRef())
                    : AiSuggestions.forTable());
        }
        groups.add(tabFirst
                ? 0
                : groups.size(), AiSuggestions.forTab(tab));
        return AiSuggestions.combine(groups);
    }

    /// @return The statuses of the documents and tables attached for the next question, the most recently attached
    /// first, for those whose status has arrived.
    private List<AiChatAttachment> pendingAttachmentStatuses() {
        final List<AiChatAttachment> statuses = new ArrayList<>();
        final List<AiChatMessage> pending = new ArrayList<>(pendingAttachments.values());
        for (int i = pending.size() - 1; i >= 0; i--) {
            final AiChatAttachment attachment = attachmentStatuses.get(pending.get(i).getAttachmentId());
            if (attachment != null) {
                statuses.add(attachment);
            }
        }
        return statuses;
    }

    /// @return True if the user has asked anything in the chat.
    private boolean hasConversation() {
        return renderedMessages.values().stream()
                .anyMatch(msg -> msg.getMessageType() == AiMessageType.USER_MESSAGE);
    }

    /// After something (the tab, a document or a table) is detached, suggestions are offered again for whatever is
    /// still attached, as when it was attached; with nothing left attached, the follow-ups to the latest answer come
    /// back, or none in a chat with nothing asked in it.
    private void onDetached() {
        if (attachedTab() != null || !pendingAttachmentStatuses().isEmpty()) {
            offerAttachedSuggestions(true);
        } else {
            // The model's suggestions for what was attached, still to come, no longer apply.
            attachedSuggestionsSequence++;
            followUps = answerFollowUps;
            refreshFollowUps();
        }
    }

    /// Removes the attached tab from the chat; it is no longer shared with its questions.
    private void detachTab() {
        attachedTabs.remove(chatKey());
        refreshAttachmentList();
        onDetached();
    }

    /// @return The chat's key in [#attachedTabs].
    private int chatKey() {
        return currentChat != null
                ? currentChat.getId()
                : NEW_CHAT_KEY;
    }

    /// Keeps a tab attached before the chat was made with the chat, now it has been made.
    private void carryNewChatTab(final int chatId) {
        final TabData tab = attachedTabs.remove(NEW_CHAT_KEY);
        if (tab != null) {
            attachedTabs.putIfAbsent(chatId, tab);
        }
    }

    /// @return The tab attached to the chat, shared with its next question, or null if none is.
    private TabData attachedTab() {
        final TabData tab = attachedTabs.get(chatKey());
        return shareActiveTab && tab != null && ((DocumentTabData) tab).getDocRef() != null
                ? tab
                : null;
    }

    /// @return The tab the user is on, offered to be attached, or null if there is none or it is already attached.
    private TabData offeredTab() {
        return shareActiveTab && currentTab != null && currentTab != attachedTab()
               && ((DocumentTabData) currentTab).getDocRef() != null
                ? currentTab
                : null;
    }

    /// @return True if the tab's chip is shown, as attached or offered.
    private boolean isShownInChips(final TabData tabData) {
        return tabData == attachedTab() || tabData == offeredTab();
    }

    /// Follows the unsaved state of the attached tab, so its chip says whether it has unsaved changes.
    private void followAttachedTab() {
        final TabData tab = attachedTab();
        if (tab == followedTab) {
            return;
        }
        if (followedTabDirtyRegistration != null) {
            followedTabDirtyRegistration.removeHandler();
            followedTabDirtyRegistration = null;
        }
        followedTab = tab;
        if (tab instanceof final HasDirtyHandlers hasDirtyHandlers) {
            followedTabDirtyRegistration = hasDirtyHandlers.addDirtyHandler(event -> refreshAttachmentList());
        }
    }

    private static boolean isDirty(final TabData tabData) {
        return tabData instanceof final DocPresenter<?, ?> docPresenter && docPresenter.isDirty();
    }

    /// Remembers the tab sent in full with an edited question, so the next question need only send its hash if it
    /// hasn't changed.
    private void rememberSentTab(final int chatId, final AiTabSnapshot tabSnapshot) {
        if (tabSnapshot != null && tabSnapshot.getHash() != null) {
            lastSentTabHashes.put(chatId, tabSnapshot.getHash());
        }
    }

    /// @return What the user sees in the attached tab now, or null if no tab is attached.
    private AiTabSnapshot attachedTabSnapshot() {
        final TabData tab = attachedTab();
        if (tab == null) {
            return null;
        }
        try {
            return ((AiTabSnapshotProvider) tab).snapshot();
        } catch (final RuntimeException e) {
            // The question is still worth sending without the tab.
            return null;
        }
    }

    /// Keeps up with whether an administrator allows tabs to be shared with chats.
    private void updateShareActiveTab(final AiChatPollResponse response) {
        final boolean share = response == null
                              || response.getShareActiveTab() == null
                              || response.getShareActiveTab();
        if (share != shareActiveTab) {
            shareActiveTab = share;
            refreshAttachmentList();
        }
    }

    /// Shows the tables attached since the last message was sent above the input box, each removable once it has
    /// downloaded.
    private void refreshAttachmentList() {
        followAttachedTab();
        final HtmlBuilder hb = new HtmlBuilder();
        final TabData attachedTab = attachedTab();
        final TabData offeredTab = offeredTab();
        if (!pendingAttachments.isEmpty() || attachedTab != null || offeredTab != null) {
            // The chips wrap onto as many rows as they need, with the button to remove them all beside them.
            hb.div(chips -> {
                if (attachedTab != null) {
                    final DocRef docRef = ((DocumentTabData) attachedTab).getDocRef();
                    AiAttachments.tabChip(chips, attachedTab.getIcon(),
                            AiTabSnapshots.chipText(docRef, isDirty(attachedTab)),
                            AiTabSnapshots.chipTooltip(docRef));
                }
                if (offeredTab != null) {
                    final DocRef docRef = ((DocumentTabData) offeredTab).getDocRef();
                    AiAttachments.attachTabChip(chips, offeredTab.getIcon(), AiTabSnapshots.attachText(docRef),
                            AiTabSnapshots.attachTooltip(docRef, attachedTab != null));
                }
                // The tab, which stays attached, set apart from the documents and tables sent with the next question.
                if ((attachedTab != null || offeredTab != null) && !pendingAttachments.isEmpty()) {
                    chips.div("", Attribute.className("ai-attachment-chips-divider"));
                }
                pendingAttachments.values().forEach(msg -> attachmentChip(chips, msg, true));
            }, Attribute.className("ai-attachment-chips"));
        }
        if (!pendingAttachments.isEmpty()) {
            AiAttachments.removeAllButton(hb, pendingAttachments.values().stream()
                    .noneMatch(msg -> AiAttachments.isDownloading(attachmentStatuses.get(msg.getAttachmentId()))));
        }
        getView().setAttachments(hb.toSafeHtml());
    }

    /// Removes all the tables attached since the last message was sent, after asking.
    private void removeAllAttachments() {
        final List<Integer> messageIds = new ArrayList<>(pendingAttachments.keySet());
        if (messageIds.isEmpty()) {
            return;
        }
        final String question = messageIds.size() == 1
                ? "Remove the attachment?"
                : "Remove all " + messageIds.size() + " attachments?";
        ConfirmEvent.fire(this, question, ok -> {
            if (ok) {
                messageIds.forEach(this::removeAttachment);
            }
        });
    }

    /// Adds the chips of the tables a message was sent with to the start of its bubble.
    private void addAttachmentChips(final Element messageElement, final List<AiChatMessage> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        final HtmlBuilder hb = new HtmlBuilder();
        hb.div(chips -> attachments.forEach(msg -> attachmentChip(chips, msg, false)),
                Attribute.className("ai-message-attachments"));
        messageElement.insertFirst(parseHtml(hb).getFirstChildElement());
    }

    private void attachmentChip(final HtmlBuilder hb, final AiChatMessage msg, final boolean removable) {
        final Integer attachmentId = msg.getAttachmentId();
        if (attachmentId == null) {
            return;
        }
        final AiChatAttachment attachment = attachmentStatuses.get(attachmentId);
        final DocumentType documentType = documentType(attachment);
        AiAttachments.chip(hb, msg.getId(), attachmentId, msg.getMessage(), attachment, removable,
                NullSafe.get(documentType, DocumentType::getIcon),
                NullSafe.get(documentType, DocumentType::getDisplayType));
    }

    /// @return The type of the document an attachment refers to, if it is a document and the types have been
    /// fetched.
    private DocumentType documentType(final AiChatAttachment attachment) {
        if (documentTypes == null || !AiAttachments.isDocument(attachment)) {
            return null;
        }
        final String type = attachment.getDocRef().getType();
        // DocumentTypes' lookup map isn't filled in the browser, so look through the list.
        return NullSafe.stream(documentTypes.getTypes())
                .filter(documentType -> documentType.getType().equals(type))
                .findFirst()
                .orElse(null);
    }

    /// Fetches Stroom's document types, for the icons of attached documents, and shows the chips again with them.
    private void fetchDocumentTypes() {
        if (fetchingDocumentTypes) {
            return;
        }
        fetchingDocumentTypes = true;
        documentTypeCache.fetch(types -> {
            documentTypes = types;
            fetchingDocumentTypes = false;
            refreshAttachmentChips();
        }, this);
    }

    /// Shows the attachments' chips again, above the input box and in the messages they were sent with.
    private void refreshAttachmentChips() {
        refreshAttachmentList();
        sentAttachments.values().forEach(sent -> sent.forEach(msg -> {
            final Element chip = msg.getAttachmentId() != null
                    ? findInConversation(AiAttachments.chipId(msg.getAttachmentId()))
                    : null;
            if (chip != null) {
                final HtmlBuilder hb = new HtmlBuilder();
                attachmentChip(hb, msg, false);
                chip.getParentElement().replaceChild(parseHtml(hb).getFirstChildElement(), chip);
            }
        }));
    }

    /// Opens the document a chip's icon button is for, if it is one.
    ///
    /// @param button A button that was clicked.
    /// @return True if it was a document chip's icon button.
    private boolean openAttachedDocument(final Element button) {
        final String type = button.getAttribute(AiAttachments.OPEN_DOC_TYPE_ATTRIBUTE);
        final String uuid = button.getAttribute(AiAttachments.OPEN_DOC_UUID_ATTRIBUTE);
        if (NullSafe.isBlankString(type) || NullSafe.isBlankString(uuid)) {
            return false;
        }
        OpenDocumentEvent.fire(this, new DocRef(type, uuid), true);
        return true;
    }

    /// Removes a table attached since the last message was sent, without asking, as it is listed to be sent
    /// with the next message and can be attached again.
    private void removeAttachment(final int messageId) {
        if (currentChat == null) {
            return;
        }
        pendingAttachments.remove(messageId);
        refreshAttachmentList();
        onDetached();
        askStroomAiClient.deleteMessage(currentChat.getId(), messageId, success -> {
            // Removed.
        }, getView());
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
        // Whatever the last chat was doing, until this one is known.
        getView().setSendButtonLoadingState(false);
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
        loadingTimer.cancel();
        getView().setLoading(false);
        loadingTimer.schedule(LOADING_DELAY_MS);
        askStroomAiClient.getMessages(chat.getId(), messages -> {
            if (currentChat == null || currentChat.getId() != chat.getId()) {
                // Another chat was opened while this one loaded.
                return;
            }
            loadingTimer.cancel();
            getView().setLoading(false);
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
                updateShareActiveTab(response);
                updateRunStatus(response.getRun());
                currentRunActive = !response.isComplete();
                refreshFollowUps();
                if (currentChat != null && currentChat.getId() == chat.getId()) {
                    showWorking(response);
                    if (!response.isComplete()) {
                        startPolling();
                    }
                }
            }, error -> { /* ignore */ }, this);
        }, error -> {
            if (currentChat != null && currentChat.getId() == chat.getId()) {
                loadingTimer.cancel();
                getView().setLoading(false);
                showError(error, "Unable to load the chat", null);
            }
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

        /// Shows the tables attached since the last message was sent, above the input box, or hides them if there
        /// are none.
        ///
        /// @param html The attachments' chips, or empty for none.
        void setAttachments(SafeHtml html);

        /// @return The list of attachments above the input box, for its clicks.
        Widget getAttachmentList();

        /// Shows, over the conversation, that a chat is loading, or stops showing it.
        void setLoading(boolean loading);

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

        /// Shows the overview of the chat in place of the conversation, or the conversation again.
        void setOverviewVisible(boolean visible);

        /// Draws the overview of the chat.
        ///
        /// @param svg The overview, as SVG (see [AiChatOverview#svg(AiChatOverview.Overview, boolean)]).
        void setOverview(String svg);

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

        /// @return True while the user is scrolling the conversation themselves (e.g. with the wheel, or dragging
        /// the scroll bar), so the chat doesn't scroll it from under them.
        boolean isUserScrolling();

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
                // The size the user last gave the panel in this browser, or else the configured one.
                final int remembered = PanelState.dockSize();
                final int size = remembered > 0
                        ? remembered
                        : NullSafe.getOrElse(currentConfig, AskStroomAiConfig::getDockSize, defaultSize);
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

    // ---------------------------------------------------------------------------------------------------------

    /// A Visualisation created from the chat whose first render report is awaited, and the button that made it.
    private record AwaitedReport(int messageId, String actionId, String name) {

    }
}
