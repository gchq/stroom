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
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.AskStroomAIConfig;
import stroom.ai.shared.AskStroomAiContext;
import stroom.ai.shared.AskStroomAiRequest;
import stroom.ai.shared.DashboardTableContext;
import stroom.ai.shared.GeneralTableContext;
import stroom.ai.shared.QueryTableContext;
import stroom.alert.client.event.AlertCallback;
import stroom.alert.client.event.AlertEvent;
import stroom.data.client.event.AskStroomAiEvent;
import stroom.data.client.event.ShowAskStroomAiEvent;
import stroom.dispatch.client.RestError;
import stroom.docref.HasDisplayValue;
import stroom.entity.client.presenter.MarkdownConverter;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.main.client.event.DockEvent;
import stroom.main.client.event.DockResizeEvent;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.preferences.client.UserPreferencesManager;
import stroom.security.shared.DocumentPermission;
import stroom.svg.shared.SvgImage;
import stroom.ui.config.shared.UserPreferences;
import stroom.util.client.ClipboardUtil;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.Size;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;

public class AskStroomAiPresenter
        extends MyPresenter<AskStroomAiView, AskStroomAiProxy>
        implements AskStroomAiUiHandlers, ShowAskStroomAiEvent.Handler, AskStroomAiEvent.Handler {

    private static final int DEFAULT_DOCK_WIDTH = 350;
    private static final int DEFAULT_DOCK_HEIGHT = 250;
    private static final int MAX_TITLE_LENGTH = 60;

    private final DocSelectionBoxPresenter docSelectionBoxPresenter;
    private final MarkdownConverter markdownConverter;
    private final AskStroomAiClient askStroomAiClient;
    private final Provider<AskStroomAiConfigPresenter> askStroomAiConfigPresenterProvider;
    private final Provider<AiChatHistoryPresenter> aiChatHistoryPresenterProvider;
    private final UserPreferencesManager userPreferencesManager;
    private AskStroomAiContext data;
    private AiChat currentChat;
    private boolean titleGenerated;
    private int lastSeenMessageId;

    private boolean showing;
    private boolean docked;
    private DockBehaviour currentDockBehaviour;

    @Inject
    public AskStroomAiPresenter(final EventBus eventBus,
                                final AskStroomAiView view,
                                final AskStroomAiProxy askStroomAiProxy,
                                final DocSelectionBoxPresenter docSelectionBoxPresenter,
                                final MarkdownConverter markdownConverter,
                                final AskStroomAiClient askStroomAiClient,
                                final Provider<AskStroomAiConfigPresenter> askStroomAiConfigPresenterProvider,
                                final Provider<AiChatHistoryPresenter> aiChatHistoryPresenterProvider,
                                final UserPreferencesManager userPreferencesManager) {
        super(eventBus, view, askStroomAiProxy);
        this.markdownConverter = markdownConverter;
        this.askStroomAiClient = askStroomAiClient;
        this.askStroomAiConfigPresenterProvider = askStroomAiConfigPresenterProvider;
        this.aiChatHistoryPresenterProvider = aiChatHistoryPresenterProvider;
        this.docSelectionBoxPresenter = docSelectionBoxPresenter;
        this.userPreferencesManager = userPreferencesManager;

        // Load dock state from user preferences.
        this.currentDockBehaviour = loadDockBehaviourFromPrefs();

        getView().setModelRefSelection(docSelectionBoxPresenter.getView());
        docSelectionBoxPresenter.setIncludedTypes(OpenAIModelDoc.TYPE);
        docSelectionBoxPresenter.setRequiredPermissions(DocumentPermission.USE);
        view.setUiHandlers(this);

        // Initiate the selection box presenter with the default model if one is set.
        askStroomAiClient.getConfig(config -> {
            if (config.getModelRef() != null) {
                docSelectionBoxPresenter.setSelectedEntityReference(config.getModelRef(), true);
            }
        }, this);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(docSelectionBoxPresenter.addDataSelectionHandler(event ->
                askStroomAiClient.getConfig(config -> {
                    final AskStroomAIConfig newConfig = config
                            .copy()
                            .modelRef(docSelectionBoxPresenter.getSelectedEntityReference())
                            .build();
                    askStroomAiClient.setConfig(newConfig);
                }, this)));

        // Listen for dock splitter resize events to persist the new size.
        addRegisteredHandler(DockResizeEvent.getType(), event -> {
            if (docked) {
                final Size newSize = event.getNewSize();
                final DockLocation loc = currentDockBehaviour.getDockLocation();
                final int dimension;
                if (loc == DockLocation.LEFT || loc == DockLocation.RIGHT) {
                    dimension = (int) newSize.getWidth();
                } else {
                    dimension = (int) newSize.getHeight();
                }
                saveDockSizeToPrefs(dimension);
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
            });
        });
    }

    @ProxyEvent
    @Override
    public void onShow(final ShowAskStroomAiEvent event) {
        if (event.isShow()) {
            if (!showing) {
                showing = true;

                if (currentDockBehaviour.getDockType() == DockType.DOCK) {
                    // Dock mode: fire DockEvent to attach to main layout.
                    final Size size = getDockSize();
                    DockEvent.fire(this, this, currentDockBehaviour, size);
                    docked = true;
                } else {
                    // Dialog mode: show as popup.
                    ShowPopupEvent.builder(this)
                            .popupType(PopupType.CLOSE_DIALOG)
                            .popupSize(PopupSize.resizable(700, 500))
                            .caption("Ask Stroom AI")
                            .onHideRequest(e -> {
                                ShowAskStroomAiEvent.fire(this, false);
                            })
                            .onHide(e -> {
                                showing = false;
                            })
                            .fire();
                }
            }
        } else {
            if (showing) {
                showing = false;
                if (currentDockBehaviour.getDockType() == DockType.DOCK) {
                    // Dock mode: fire DockEvent to detach to main layout.
                    docked = false;
                    DockEvent.fireUndock(this, this);
                } else {
                    // Dialog mode: hide popup.
                    HidePopupEvent.builder(this).fire();
                }
            }
        }
    }

    @Override
    public void onChangeConfig() {
        askStroomAiClient.getConfig(config -> {
            final AskStroomAIConfig newConfig = config
                    .copy()
                    .modelRef(docSelectionBoxPresenter.getSelectedEntityReference())
                    .build();
            askStroomAiConfigPresenterProvider.get().show(
                    newConfig, askStroomAiClient::setConfig, currentDockBehaviour, this::onDockBehaviourChange);
        }, this);
    }

    void onDockBehaviourChange(final DockBehaviour dockBehaviour) {
        final DockBehaviour oldBehaviour = this.currentDockBehaviour;
        this.currentDockBehaviour = dockBehaviour;

        // Persist to user preferences.
        saveDockBehaviourToPrefs(dockBehaviour);

        if (showing) {
            final boolean wasDocked = docked;
            final boolean wantsDock = dockBehaviour.getDockType() == DockType.DOCK;

            if (wasDocked) {
                if (wantsDock) {
                    // Location change while docked: undock and re-dock.
                    DockEvent.fireUndock(this, this);
                    DockEvent.fire(this, this, dockBehaviour, getDockSize());
                    getView().focus();
                } else {
                    // Switching from DOCK to DIALOG: undock then show popup.
                    DockEvent.fireUndock(this, this);
                    docked = false;
                    showing = false;
                    // Re-trigger show as dialog.
                    showAsDialog();
                }
            } else if (wantsDock) {
                // Switching from DIALOG to DOCK: hide popup then dock.
                HidePopupRequestEvent.builder(this).fire();
                showing = false;
                docked = false;
                // Now dock.
                showing = true;
                docked = true;
                DockEvent.fire(this, this, dockBehaviour, getDockSize());
                getView().focus();
            }
        }
    }

    @ProxyCodeSplit
    public interface AskStroomAiProxy extends Proxy<AskStroomAiPresenter> {

    }

    @Override
    public void setContext(final AskStroomAiContext data) {
        this.data = data;

        // Show attachment-style context indicator.
        if (data instanceof DashboardTableContext) {
            getView().setContextIndicator(SvgImage.CLIPBOARD, "Table data (Dashboard)");
        } else if (data instanceof QueryTableContext) {
            getView().setContextIndicator(SvgImage.CLIPBOARD, "Table data (Query)");
        } else if (data instanceof final GeneralTableContext gtc) {
            getView().setContextIndicator(SvgImage.CLIPBOARD, "Table data (" + gtc.getRows().size()
                                                              + " rows, " + gtc.getColumns().size() + " cols)");
        } else {
            getView().clearContextIndicator();
        }
    }

    @Override
    public void onSendMessage(final String message) {
        getView().setSendButtonLoadingState(true);
        getView().setEmptyState(false);
        getView().clearContextIndicator();
        appendMessageHtml("ai-message ai-message--user", "> " + message,
                System.currentTimeMillis(), false);

        // Scroll markdown container to bottom, so the user's message is displayed
        final Element markdownContainer = getView().getMarkdownContainer();
        markdownContainer.setScrollTop(markdownContainer.getScrollHeight());

        ensureChat(() -> askStroomAiClient.getConfig(config -> {
            final AskStroomAiRequest request = new AskStroomAiRequest(
                    currentChat, config, data, message);
            askStroomAiClient.sendMessage(request,
                    response -> {
                        // Poll to get the properly typed, persisted messages.
                        pollForNewMessages();
                        maybeGenerateTitle(message);
                    }, error ->
                            showError(error, "Stroom AI request failed", () ->
                                    getView().setSendButtonLoadingState(false)), this);
        }, this));
    }

    @Override
    public void onCancelProcessing() {
        if (currentChat != null) {
            askStroomAiClient.cancelProcessing(currentChat.getId(),
                    success -> {
                        // Server will set the cancellation flag; the poll loop will pick up
                        // partial results and stop. Reset UI now so the user can type again.
                        getView().setSendButtonLoadingState(false);
                    }, this);
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
     * Poll for new messages since lastSeenMessageId. Renders any new messages
     * with type-aware formatting and updates the lastSeenMessageId.
     */
    private void pollForNewMessages() {
        if (currentChat == null) {
            return;
        }
        askStroomAiClient.pollMessages(currentChat.getId(), lastSeenMessageId, response -> {
            if (response.getNewMessages() != null && !response.getNewMessages().isEmpty()) {
                for (final AiChatMessage msg : response.getNewMessages()) {
                    // Skip USER_MESSAGE — we already rendered it inline in onSendMessage.
                    if (msg.getMessageType() != AiMessageType.USER_MESSAGE) {
                        renderMessage(msg);
                    }
                    // Track the highest seen message ID.
                    lastSeenMessageId = Math.max(lastSeenMessageId, msg.getId());
                }
                // Scroll to show new content.
                final Element markdownContainer = getView().getMarkdownContainer();
                markdownContainer.setScrollTop(markdownContainer.getScrollHeight());
            }
            if (!response.isComplete()) {
                // If the conversation is still in-flight, schedule another poll after 1s.
                new com.google.gwt.user.client.Timer() {
                    @Override
                    public void run() {
                        pollForNewMessages();
                    }
                }.schedule(1000);
            } else {
                getView().setSendButtonLoadingState(false);
            }
        }, error -> {
            // Clear loading state on poll failure so the UI doesn't get stuck.
            getView().setSendButtonLoadingState(false);
        }, this);
    }

    /**
     * Render a single message with type-aware HTML structure.
     */
    private void renderMessage(final AiChatMessage msg) {
        final long timeMs = msg.getCreateTimeMs();
        switch (msg.getMessageType()) {
            case USER_MESSAGE:
                appendMessageHtml("ai-message ai-message--user", "> " + msg.getMessage(),
                        timeMs, false);
                break;
            case AI_RESPONSE:
                appendMessageHtml("ai-message ai-message--assistant", msg.getMessage(),
                        timeMs, true);
                break;
            case ERROR:
                appendMessageHtml("ai-message ai-message--error", msg.getMessage(),
                        timeMs, false);
                break;
            case THINKING:
                appendCollapsibleMessage("ai-message ai-message--thinking",
                        SvgImage.INFO, "Thinking...", msg.getMessage(), timeMs);
                break;
            case DASHBOARD_DATA:
            case QUERY_DATA:
            case TABLE_DATA:
                appendCollapsibleMessage("ai-message ai-message--data",
                        SvgImage.TABLE, "Data context", msg.getMessage(), timeMs);
                break;
            default:
                appendMessageHtml("ai-message", msg.getMessage(), timeMs, false);
                break;
        }
    }

    /**
     * Append a message div with the given CSS class, markdown content, and timestamp.
     * If {@code showCopy} is true, a copy button is added for AI responses.
     */
    private void appendMessageHtml(final String cssClass,
                                   final String markdownText,
                                   final long timeMs,
                                   final boolean showCopy) {
        final Element markdownContainer = getView().getMarkdownContainer();
        final SafeHtml markdownHtml = markdownConverter.convertMarkdownToHtml(markdownText);

        final DivElement wrapper = Document.get().createDivElement();
        wrapper.setClassName(cssClass);
        wrapper.setInnerSafeHtml(markdownHtml);

        // Add message footer (timestamp + optional copy button).
        final DivElement footer = Document.get().createDivElement();
        footer.setClassName("ai-message-footer");

        if (showCopy) {
            final Element copyBtn = Document.get().createElement("button");
            copyBtn.setClassName("ai-message-copy");
            setCopyButtonContent(copyBtn, SvgImage.COPY, "Copy");
            addCopyClickHandler(copyBtn, markdownText);
            footer.appendChild(copyBtn);
        }

        final Element timestamp = Document.get().createElement("span");
        timestamp.setClassName("ai-message-timestamp");
        timestamp.setInnerText(formatRelativeTime(timeMs));
        footer.appendChild(timestamp);

        wrapper.appendChild(footer);
        markdownContainer.appendChild(wrapper);
    }

    /**
     * Append a collapsible details/summary element for thinking and data messages.
     */
    private void appendCollapsibleMessage(final String cssClass,
                                          final SvgImage icon,
                                          final String summaryText,
                                          final String markdownText,
                                          final long timeMs) {
        final Element markdownContainer = getView().getMarkdownContainer();
        final SafeHtml markdownHtml = markdownConverter.convertMarkdownToHtml(markdownText);

        // Build: <details class="..."><summary>...</summary><div>rendered content</div></details>
        final DivElement contentDiv = Document.get().createDivElement();
        contentDiv.setInnerSafeHtml(markdownHtml);

        final Element details = Document.get().createElement("details");
        details.setClassName(cssClass);

        final Element summary = Document.get().createElement("summary");
        // Use innerHTML so the SVG icon renders inline.
        summary.setInnerHTML("<span class='svgIcon'>" + icon.getSvg() + "</span> " + summaryText);

        // Add timestamp inside summary.
        final Element timestamp = Document.get().createElement("span");
        timestamp.setClassName("ai-message-timestamp");
        timestamp.setInnerText(" --- " + formatRelativeTime(timeMs));
        summary.appendChild(timestamp);

        details.appendChild(summary);
        details.appendChild(contentDiv);
        markdownContainer.appendChild(details);
    }

    /**
     * Add a click handler to copy text to the clipboard using {@link ClipboardUtil}.
     */
    private static void addCopyClickHandler(final Element button, final String text) {
        DOM.sinkEvents(button, Event.ONCLICK);
        DOM.setEventListener(button, event -> {
            if (Event.ONCLICK == event.getTypeInt()) {
                if (ClipboardUtil.copy(text)) {
                    setCopyButtonContent(button, SvgImage.OK, "Copied");
                    new com.google.gwt.user.client.Timer() {
                        @Override
                        public void run() {
                            setCopyButtonContent(button, SvgImage.COPY, "Copy");
                        }
                    }.schedule(2000);
                }
            }
        });
    }

    /**
     * Set the content of a copy button to an SVG icon + label.
     */
    private static void setCopyButtonContent(final Element button,
                                             final SvgImage icon,
                                             final String label) {
        button.setInnerHTML("<span class='svgIcon'>" + icon.getSvg() + "</span> " + label);
    }

    /**
     * Format a timestamp as a relative time string (e.g., "Just now", "5 minutes ago").
     */
    private static String formatRelativeTime(final long timeMs) {
        if (timeMs <= 0) {
            return "";
        }
        final long now = System.currentTimeMillis();
        final long diff = now - timeMs;
        final long seconds = diff / 1000;
        final long minutes = seconds / 60;
        final long hours = minutes / 60;
        final long days = hours / 24;

        if (days > 0) {
            return days == 1
                    ? "Yesterday"
                    : days + " days ago";
        } else if (hours > 0) {
            return hours + (hours == 1
                    ? " hour ago"
                    : " hours ago");
        } else if (minutes > 0) {
            return minutes + (minutes == 1
                    ? " minute ago"
                    : " minutes ago");
        } else {
            return "Just now";
        }
    }

    @Override
    public void onNewChat() {
        askStroomAiClient.createChat(chat -> {
            currentChat = chat;
            titleGenerated = false;
            lastSeenMessageId = 0;
            data = null;
            getView().clearMessages();
            getView().setEmptyState(true);
            getView().clearContextIndicator();
            getView().setTitle(chat.getTitle());
            getView().focus();
        }, this);
    }

    @Override
    public void onShowHistory() {
        aiChatHistoryPresenterProvider.get().show(this::loadChat);
    }

    private void loadChat(final AiChat chat) {
        currentChat = chat;
        // A loaded chat already has a title.
        titleGenerated = true;
        lastSeenMessageId = 0;
        data = null;
        getView().clearMessages();
        getView().clearContextIndicator();
        getView().setTitle(chat.getTitle());

        // Load messages for the selected chat.
        askStroomAiClient.getMessages(chat.getId(), messages -> {
            if (messages != null && !messages.isEmpty()) {
                getView().setEmptyState(false);
                for (final AiChatMessage msg : messages) {
                    renderMessage(msg);
                    lastSeenMessageId = Math.max(lastSeenMessageId, msg.getId());
                }
            } else {
                getView().setEmptyState(true);
            }
            // Scroll to bottom after loading all messages.
            final Element markdownContainer = getView().getMarkdownContainer();
            markdownContainer.setScrollTop(markdownContainer.getScrollHeight());
            getView().focus();
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
                getView().setTitle(chat.getTitle());
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

        getView().setTitle(title);
        askStroomAiClient.updateChatTitle(currentChat.getId(), title, success -> {
            // Title persisted successfully — nothing more to do.
        }, this);
    }

    public interface AskStroomAiView extends View, Focus, HasUiHandlers<AskStroomAiUiHandlers> {

        void setModelRefSelection(View view);

        Element getMarkdownContainer();

        String getMessage();

        void setSendButtonLoadingState(final boolean enabled);

        void setTitle(String title);

        void clearMessages();

        void setEmptyState(boolean empty);

        void setContextIndicator(SvgImage icon, String text);

        void clearContextIndicator();
    }

    // ---- Dock preference helpers ----

    private DockBehaviour loadDockBehaviourFromPrefs() {
        final UserPreferences prefs = userPreferencesManager.getCurrentUserPreferences();
        if (prefs != null) {
            final DockType type = parseDockType(prefs.getAiDockType());
            final DockLocation location = parseDockLocation(prefs.getAiDockLocation());
            return new DockBehaviour(type, location);
        }
        return new DockBehaviour(DockType.DIALOG, DockLocation.RIGHT);
    }

    private void saveDockBehaviourToPrefs(final DockBehaviour behaviour) {
        final UserPreferences currentPrefs = userPreferencesManager.getCurrentUserPreferences();
        if (currentPrefs != null) {
            final UserPreferences newPrefs = currentPrefs.copy()
                    .aiDockType(behaviour.getDockType().name())
                    .aiDockLocation(behaviour.getDockLocation().name())
                    .build();
            userPreferencesManager.setCurrentPreferences(newPrefs);
            userPreferencesManager.update(newPrefs, result -> {
            }, this);
        }
    }

    private void saveDockSizeToPrefs(final int size) {
        final UserPreferences currentPrefs = userPreferencesManager.getCurrentUserPreferences();
        if (currentPrefs != null) {
            final UserPreferences newPrefs = currentPrefs.copy()
                    .aiDockSize(size)
                    .build();
            userPreferencesManager.setCurrentPreferences(newPrefs);
            userPreferencesManager.update(newPrefs, result -> {
            }, this);
        }
    }

    private Size getDockSize() {
        final UserPreferences prefs = userPreferencesManager.getCurrentUserPreferences();
        final DockLocation loc = currentDockBehaviour.getDockLocation();
        final int defaultSize;
        if (loc == DockLocation.LEFT || loc == DockLocation.RIGHT) {
            defaultSize = DEFAULT_DOCK_WIDTH;
        } else {
            defaultSize = DEFAULT_DOCK_HEIGHT;
        }
        final int size = (prefs != null && prefs.getAiDockSize() != null)
                ? prefs.getAiDockSize()
                : defaultSize;
        return new Size.Builder()
                .width(size)
                .height(size)
                .build();
    }

    private void showAsDialog() {
        showing = true;
        ShowPopupEvent.builder(this)
                .popupType(PopupType.CLOSE_DIALOG)
                .popupSize(PopupSize.resizable(700, 500))
                .caption("Ask Stroom AI")
                .onShow(e -> getView().focus())
                .onHide(e -> showing = false)
                .fire();
    }

    private static DockType parseDockType(final String value) {
        if (value != null) {
            try {
                return DockType.valueOf(value);
            } catch (final IllegalArgumentException e) {
                // Ignore invalid values.
            }
        }
        return DockType.DIALOG;
    }

    private static DockLocation parseDockLocation(final String value) {
        if (value != null) {
            try {
                return DockLocation.valueOf(value);
            } catch (final IllegalArgumentException e) {
                // Ignore invalid values.
            }
        }
        return DockLocation.RIGHT;
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
    }

    public enum DockType implements HasDisplayValue {
        DIALOG("Dialog"),
        TAB("Tab"),
        FLOAT("Float"),
        DOCK("Dock");

        private final String displayValue;

        DockType(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }

    public enum DockLocation implements HasDisplayValue {
        TOP("Top"),
        LEFT("Left"),
        BOTTOM("Bottom"),
        RIGHT("Right");

        private final String displayValue;

        DockLocation(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }
}
