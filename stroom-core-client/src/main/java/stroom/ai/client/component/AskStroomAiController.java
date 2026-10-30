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

import stroom.ai.client.AskStroomAiClient;
import stroom.ai.client.component.AskStroomAi.AskStroomAiAction;
import stroom.ai.shared.AiAttachmentStatus;
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatAttachment;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.AskStroomAiConfig;
import stroom.ai.shared.AskStroomAiConfig.DockLocation;
import stroom.ai.shared.AskStroomAiConfig.DockType;
import stroom.ai.shared.AskStroomAiContext;
import stroom.ai.shared.AskStroomAiRequest;
import stroom.ai.shared.DownloadChatHistoryRequest;
import stroom.alert.client.event.AlertCallback;
import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.core.client.LocationManager;
import stroom.data.client.event.AskStroomAiEvent;
import stroom.data.client.event.ShowAskStroomAiEvent;
import stroom.dispatch.client.ExportFileCompleteUtil;
import stroom.dispatch.client.RestError;
import stroom.entity.client.MarkdownConverter;
import stroom.explorer.client.component.DocSelectionController;
import stroom.main.client.event.DockEvent;
import stroom.main.client.event.DockResizeEvent;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.preferences.client.DateTimeFormatter;
import stroom.security.shared.DocumentPermission;
import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.util.client.Size;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// Chats with Stroom's AI: sends the user's messages and the data they ask about, polls for the
/// replies, and docks the chat or shows it as a dialog, as the user's settings say.
public class AskStroomAiController extends Controller
        implements ShowAskStroomAiEvent.Handler, AskStroomAiEvent.Handler, TaskMonitorFactory {

    private static final DockBehaviour DEFAULT_DOCK_BEHAVIOUR = new DockBehaviour(DockType.DOCK, DockLocation.RIGHT);
    private static final int DEFAULT_DOCK_HEIGHT = 250;

    private final AskStroomAi view;

    private static final int DEFAULT_DOCK_WIDTH = 350;
    private static final int MAX_TITLE_LENGTH = 60;

    private final DocSelectionController modelSelection;
    private final MarkdownConverter markdownConverter;
    private final AskStroomAiClient askStroomAiClient;
    private final Provider<AskStroomAiConfigEditorController> askStroomAiConfigProvider;
    private final Provider<AiChatHistoryController> aiChatHistoryProvider;
    private final Provider<DownloadChatController> downloadChatControllerProvider;
    private final Provider<AiAttachmentDataController> aiAttachmentDataProvider;
    private final LocationManager locationManager;
    private final DateTimeFormatter dateTimeFormatter;
    private AskStroomAiContext data;
    private AiChat currentChat;
    private boolean titleGenerated;
    private int lastSeenMessageId;

    private boolean showing;
    private boolean docked;
    /// True while a request is with the server. Polling continues for as long as this is set, since a
    /// poll can otherwise outrun the work and conclude it has finished before it has started.
    private boolean requestInFlight;
    private boolean polling;
    // The next poll for the chat's messages, while it is waiting on them; cancelled when disposed
    private com.google.gwt.user.client.Timer pollTimer;

    /// Made by GIN, with its screen and its parts.
    ///
    /// @param eventBus                       The application's event bus.
    /// @param view                           Its component.
    /// @param docSelectionProvider           Makes the doc selection.
    /// @param markdownConverter              The markdown converter.
    /// @param askStroomAiClient              The Ask Stroom AI client.
    /// @param askStroomAiConfigProvider      Makes the Ask Stroom AI config.
    /// @param aiChatHistoryProvider          Makes the AI chat history.
    /// @param downloadChatControllerProvider Makes the download chat controller.
    /// @param aiAttachmentDataProvider       Makes the AI attachment data.
    /// @param locationManager                The browser's location.
    /// @param dateTimeFormatter              Formats dates and times.
    @Inject
    public AskStroomAiController(final EventBus eventBus,
                                final AskStroomAi view,
                                final Provider<DocSelectionController> docSelectionProvider,
                                final MarkdownConverter markdownConverter,
                                final AskStroomAiClient askStroomAiClient,
                                final Provider<AskStroomAiConfigEditorController> askStroomAiConfigProvider,
                                final Provider<AiChatHistoryController> aiChatHistoryProvider,
                                final Provider<DownloadChatController> downloadChatControllerProvider,
                                final Provider<AiAttachmentDataController> aiAttachmentDataProvider,
                                final LocationManager locationManager,
                                final DateTimeFormatter dateTimeFormatter) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        this.markdownConverter = markdownConverter;
        this.askStroomAiClient = askStroomAiClient;
        this.askStroomAiConfigProvider = askStroomAiConfigProvider;
        this.aiChatHistoryProvider = aiChatHistoryProvider;
        this.downloadChatControllerProvider = downloadChatControllerProvider;
        this.aiAttachmentDataProvider = aiAttachmentDataProvider;
        this.locationManager = locationManager;
        this.dateTimeFormatter = dateTimeFormatter;

        modelSelection = make(docSelectionProvider);
        modelSelection.attach(view.getModelRefBox());
        modelSelection.setIncludedTypes(OpenAIModelDoc.TYPE);
        modelSelection.setRequiredPermissions(DocumentPermission.USE);
        view.setOnSend(this::onSendMessage);
        view.setOnAction(this::onAction);

        // Shows the default model, if one is set
        readModel();

        own(modelSelection.addSelectionListener(event ->
                writeModel()));

        view.setOnViewAttachment(this::onViewAttachmentData);
        view.setOnDeleteMessage(this::onDeleteMessage);

        // Listen for dock splitter resize events to persist the new size.
        listen(DockResizeEvent.getType(), event -> {
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
        // A closed chat polls no more
        onDispose(this::stopPolling);
    }

    @Override
    public void onAsk(final AskStroomAiEvent event) {
        // Show if not already showing.
        ShowAskStroomAiEvent.fire(this, true);

        Scheduler.get().scheduleDeferred(() -> {
            // Auto-create a chat if none exists.
            ensureChat(() -> {
                setContext(event.getData());
                view.focus();

                // Immediately send context-only request to create the attachment on the server
                // and start the async download. The ATTACHMENT message will appear in the chat
                // history when we poll for new messages.
                if (this.data != null) {
                    view.setEmptyState(false);
                    askStroomAiClient.getConfig(config -> {
                        final AskStroomAiRequest request = new AskStroomAiRequest(
                                currentChat, config, this.data, null);
                        // Generate a title from the attachment type.
                        maybeGenerateTitle(getContextTitle(this.data));
                        // Clear context so it isn't re-attached when the user sends a question.
                        this.data = null;
                        view.clearContextIndicator();
                        askStroomAiClient.sendMessage(request,
                                response -> pollForNewMessages(),
                                error -> showError(error, "Failed to create attachment", null),
                                view);
                    }, view);
                }
            });
        });
    }

    @Override
    public void onShow(final ShowAskStroomAiEvent event) {
        getDockSize(dockSize -> {
            getDockBehaviourFromPrefs(dockBehaviour -> {
                if (isDisposed()) {
                    return;
                }
                if (event.isShow()) {
                    if (!showing) {
                        showing = true;

                        if (dockBehaviour.getDockType() == DockType.DOCK) {
                            // Dock mode: fire DockEvent to attach to main layout.
                            DockEvent.fire(this, this, dockBehaviour, dockSize);
                            docked = true;
                        } else {
                            // Dialog mode: show as popup.
                            ControllerDialog.show(this)
                                    .popupType(PopupType.CLOSE_DIALOG)
                                    .popupSize(PopupSize.resizable(700, 500))
                                    .caption("Ask Stroom AI")
                                    // Ready to type a question, as when it is opened to ask about data
                                    .onShow(e -> view.focus())
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
                        if (dockBehaviour.getDockType() == DockType.DOCK) {
                            // Dock mode: fire DockEvent to detach to main layout.
                            docked = false;
                            DockEvent.fireUndock(this, this);
                        } else {
                            // Dialog mode: hide popup.
                            ControllerDialog.hide(this, false, false);
                        }
                    }
                }
            });
        });
    }

    private void onChangeConfig() {
        askStroomAiClient.getConfig(config -> {
            getDockBehaviourFromPrefs(dockBehaviour -> {
                // Made for one use, and disposed once it is closed
                askStroomAiConfigProvider.get().show(
                        this, config, this::updateConfig, dockBehaviour, this::onDockBehaviourChange);
            });
        }, this);
    }

    private void stopPolling() {
        if (pollTimer != null) {
            pollTimer.cancel();
        }
    }

    private void updateConfig(final AskStroomAiConfig config) {
        askStroomAiClient.setConfig(config, this);
        readModel();
    }

    private void readModel() {
        askStroomAiClient.getConfig(config ->
                modelSelection.setSelectedEntityReference(config.getModelRef(), true), this);
    }

    private void writeModel() {
        askStroomAiClient.getConfig(config -> {
            final AskStroomAiConfig newConfig = config.copy()
                    .modelRef(modelSelection.getSelectedEntityReference())
                    .build();
            askStroomAiClient.setConfig(newConfig, this);
        }, this);
    }

    void onDockBehaviourChange(final DockBehaviour dockBehaviour) {
        getDockSize(dockSize -> {
            getDockBehaviourFromPrefs(currentDockBehaviour -> {
                if (isDisposed()) {
                    return;
                }
                // Idempotency: if the behaviour hasn't changed, nothing to do.
                // This handles the duplicate call that occurs when a live-preview change
                // (radio button) is followed by the user clicking OK, which calls the
                // consumer again with the same value.
                if (dockBehaviour.equals(currentDockBehaviour)) {
                    return;
                }

                // Persist to user preferences.
                saveDockBehaviourToPrefs(dockBehaviour);

                if (showing) {
                    final boolean wasDocked = docked;
                    final boolean wantsDock = dockBehaviour.getDockType() == DockType.DOCK;

                    if (wasDocked && wantsDock) {
                        // Location change while docked: defer so any in-flight settings-dialog
                        // hide sequence completes first, then undock and re-dock.
                        Scheduler.get().scheduleDeferred(() -> {
                            DockEvent.fireUndock(this, this);
                            DockEvent.fire(this, this, dockBehaviour, dockSize);
                            view.focus();
                        });

                    } else if (wasDocked) {
                        // DOCK → DIALOG: defer so settings dialog closes first.
                        Scheduler.get().scheduleDeferred(() -> {
                            DockEvent.fireUndock(this, this);
                            docked = false;
                            showing = false;
                            showAsDialog();
                        });

                    } else if (wantsDock) {
                        // DIALOG → DOCK: hide at once (not as the Close button asks) for a
                        // direct programmatic dismiss that doesn't re-enter onHideRequest.
                        // ControllerDialog.hide is synchronous and calls onHide (showing=false),
                        // then the deferred re-enables showing, sets docked, and fires DockEvent.
                        ControllerDialog.hide(this, false, false);
                        Scheduler.get().scheduleDeferred(() -> {
                            showing = true;
                            docked = true;
                            DockEvent.fire(this, this, dockBehaviour, dockSize);
                            view.focus();
                        });
                    }
                }
            });
        });
    }

    /// Attaches data to the next message, e.g. a table the user asks about.
    ///
    /// @param data The data, or null for none.
    public void setContext(final AskStroomAiContext data) {
        this.data = data;

        // Show attachment-style context indicator using the context's description.
        if (data != null) {
            view.setContextIndicator(SvgImage.CLIPBOARD, data.getDescription());
        } else {
            view.clearContextIndicator();
        }
    }

    private void onSendMessage(final String message) {
        view.setSendButtonLoadingState(true);
        view.setEmptyState(false);
        view.clearContextIndicator();

        // Shown at once, scrolled to, until the poll brings back the stored message
        final long nowMs = System.currentTimeMillis();
        view.addPendingMessage(new AskStroomAi.Message(AiMessageType.USER_MESSAGE, 0, message,
                markdownConverter.convertMarkdownToHtml("> " + message), null,
                dateTimeFormatter.formatRelative(nowMs, nowMs), dateTimeFormatter.format(nowMs)));

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
                                view.setSendButtonLoadingState(false));
                    }, view);
            // Poll while the request runs, so progress is seen as it happens rather than all at once
            // when the request returns.
            startPolling();
        }, view));
    }

    private void onCancelProcessing() {
        if (currentChat != null) {
            askStroomAiClient.cancelProcessing(currentChat.getId(),
                    success -> {
                        // Server will set the cancellation flag; the poll loop will pick up
                        // partial results and stop. Reset UI now so the user can type again.
                        view.setSendButtonLoadingState(false);
                    }, view);
        }
    }

    private void showError(final RestError error,
                           final String message,
                           final AlertCallback callback) {
        AlertEvent.fireError(
                AskStroomAiController.this,
                message + " - " + error.getMessage(),
                null,
                callback);
    }

    /// Poll for new messages since lastSeenMessageId. Renders any new messages
    /// with type-aware formatting and updates the lastSeenMessageId.
    private void startPolling() {
        if (!polling) {
            polling = true;
            pollForNewMessages();
        }
    }

    private void pollForNewMessages() {
        // A closed chat polls no more (its timer is cancelled too, but one may already be due)
        if (currentChat == null || isDisposed()) {
            polling = false;
            return;
        }
        final int chatId = currentChat.getId();
        askStroomAiClient.pollMessages(chatId, lastSeenMessageId, response -> {
            if (isDisposed()) {
                return;
            }
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
                final List<AskStroomAi.Message> messages = new ArrayList<>();
                for (final AiChatMessage msg : response.getNewMessages()) {
                    // A USER_MESSAGE was shown when it was sent, but without its id, so it is
                    // replaced by the stored one (which can then be deleted)
                    if (msg.getMessageType() != AiMessageType.USER_MESSAGE
                        || !view.replacePendingMessage(toMessage(msg, nowMs))) {
                        messages.add(toMessage(msg, nowMs));
                    }
                    // Track the highest seen message ID.
                    lastSeenMessageId = Math.max(lastSeenMessageId, msg.getId());
                }
                // Scroll to show new content.
                view.addMessages(messages);
            }

            // Update attachment status elements in-place.
            updateAttachmentStatuses(response.getAttachments());

            // Show what the server is currently doing, and take the line away when it is done.
            updateWorkingMessage(response.getWorkingMessage());

            // Keep polling while this client is waiting on a request, while data is still coming
            // down, or while messages are still arriving. A WORKING message on its own is not reason
            // enough: one can be left behind by a server that stopped mid-question, and polling for
            // that would never end.
            if (requestInFlight || hasNewMessages || isDownloading(response.getAttachments())) {
                // If the conversation is still in-flight, schedule another poll after 1s.
                pollTimer = new com.google.gwt.user.client.Timer() {
                    @Override
                    public void run() {
                        pollForNewMessages();
                    }
                };
                pollTimer.schedule(1000);
            } else {
                polling = false;
                view.setSendButtonLoadingState(false);
            }
        }, error -> {
            // Clear loading state on poll failure so the UI doesn't get stuck.
            polling = false;
            view.setSendButtonLoadingState(false);
        }, view);
    }

    /// A message as the chat shows it: rendered from its markdown, with when it was sent.
    private AskStroomAi.Message toMessage(final AiChatMessage msg, final long nowMs) {
        final AiMessageType type = msg.getMessageType();
        final String text;
        final SafeHtml content;
        switch (type) {
            case USER_MESSAGE -> {
                text = msg.getMessage();
                content = markdownConverter.convertMarkdownToHtml("> " + msg.getMessage());
            }
            case WORKING -> {
                text = workingText(msg);
                content = SafeHtmlUtils.EMPTY_SAFE_HTML;
            }
            case ATTACHMENT -> {
                text = NullSafe.getOrElse(msg, AiChatMessage::getMessage, "Table attachment");
                content = SafeHtmlUtils.EMPTY_SAFE_HTML;
            }
            default -> {
                text = msg.getMessage();
                content = markdownConverter.convertMarkdownToHtml(msg.getMessage());
            }
        }
        final long timeMs = msg.getCreateTimeMs();
        return new AskStroomAi.Message(type, msg.getId(), text, content, msg.getAttachmentId(),
                dateTimeFormatter.formatRelative(timeMs, nowMs), dateTimeFormatter.format(timeMs));
    }

    /// Adds, updates or removes the progress line to match what the server says it is doing.
    private void updateWorkingMessage(final AiChatMessage workingMessage) {
        if (workingMessage == null) {
            // Nothing is being worked on, so the progress line has served its purpose.
            view.setWorkingMessage(null);
        } else {
            final long timeMs = workingMessage.getCreateTimeMs();
            final long nowMs = System.currentTimeMillis();
            view.setWorkingMessage(new AskStroomAi.Message(AiMessageType.WORKING, workingMessage.getId(),
                    workingText(workingMessage), SafeHtmlUtils.EMPTY_SAFE_HTML, null,
                    dateTimeFormatter.formatRelative(timeMs, nowMs), dateTimeFormatter.format(timeMs)));
        }
    }

    private String workingText(final AiChatMessage msg) {
        return NullSafe.getOrElse(msg, AiChatMessage::getMessage, "Working...");
    }

    /// @return True if any attachment is still being fetched, so there is more to come.
    private boolean isDownloading(final List<AiChatAttachment> attachments) {
        if (attachments != null) {
            for (final AiChatAttachment attachment : attachments) {
                if (attachment.getStatus() == AiAttachmentStatus.PENDING
                    || attachment.getStatus() == AiAttachmentStatus.DOWNLOADING) {
                    return true;
                }
            }
        }
        return false;
    }

    /// Shows each attachment's status where the attachment is shown.
    private void updateAttachmentStatuses(final List<AiChatAttachment> attachments) {
        if (attachments == null) {
            return;
        }
        for (final AiChatAttachment attachment : attachments) {
            view.setAttachmentStatus(attachment.getId(), attachment.getStatus(), statusText(attachment));
        }
    }

    /// What an attachment's status says, e.g. "Ready --- 42 rows".
    private static String statusText(final AiChatAttachment attachment) {
        return switch (attachment.getStatus()) {
            case PENDING -> "Pending...";
            case DOWNLOADING -> "Downloading...";
            case READY -> {
                final StringBuilder sb = new StringBuilder();
                sb.append(" Ready");
                if (attachment.getRowCount() != null) {
                    sb.append(" --- ").append(attachment.getRowCount()).append(" rows");
                }
                if (attachment.isTruncated()) {
                    sb.append(" (truncated to limit)");
                }
                yield sb.toString();
            }
            case ERROR -> "Error: " + NullSafe.getOrElse(attachment,
                    AiChatAttachment::getErrorMessage,
                    "Unknown error");
        };
    }

    private void onNewChat() {
        askStroomAiClient.createChat(chat -> {
            currentChat = chat;
            titleGenerated = false;
            lastSeenMessageId = 0;
            data = null;
            view.clearMessages();
            view.setEmptyState(true);
            view.clearContextIndicator();
            view.setChatTitle(chat.getTitle());
            view.setDownloadEnabled(true);
            view.setDeleteAllEnabled(true);
            view.focus();
        }, this);
    }

    private void onShowHistory() {
        aiChatHistoryProvider.get().show(this, this::loadChat);
    }

    private void onDownloadChat() {
        if (currentChat == null) {
            return;
        }
        final int chatId = currentChat.getId();
        downloadChatControllerProvider.get().show(this, includeDataContexts -> {
            final DownloadChatHistoryRequest request = new DownloadChatHistoryRequest(chatId, includeDataContexts);
            askStroomAiClient.downloadChatHistory(request,
                    result -> ExportFileCompleteUtil.onSuccess(locationManager, this, result),
                    error -> showError(error, "Failed to download chat history", null),
                    this);
        });
    }

    private void onViewAttachmentData(final int attachmentId, final String description) {
        if (currentChat == null) {
            return;
        }
        aiAttachmentDataProvider.get().show(this, currentChat.getId(), attachmentId, description);
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
                                view);
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
                                view);
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
        view.clearMessages();
        view.clearContextIndicator();
        view.setChatTitle(chat.getTitle());
        view.setDownloadEnabled(true);
        view.setDeleteAllEnabled(true);

        // Load messages for the selected chat.
        askStroomAiClient.getMessages(chat.getId(), messages -> {
            final long nowMs = System.currentTimeMillis();
            final List<AskStroomAi.Message> shown = new ArrayList<>();
            if (messages != null && !messages.isEmpty()) {
                view.setEmptyState(false);
                for (final AiChatMessage msg : messages) {
                    shown.add(toMessage(msg, nowMs));
                    lastSeenMessageId = Math.max(lastSeenMessageId, msg.getId());
                }
            } else {
                view.setEmptyState(true);
            }
            // Scroll to bottom after loading all messages.
            view.addMessages(shown);
            view.focus();

            // Fetch attachment statuses to update status elements rendered above.
            // For historical chats, attachments are already in their final state (READY/ERROR).
            askStroomAiClient.pollMessages(chat.getId(), lastSeenMessageId, response ->
                    updateAttachmentStatuses(response.getAttachments()), error -> { /* ignore */ }, this);
        }, this);
    }

    /// Ensure a chat exists, creating one if necessary, then run the callback.
    private void ensureChat(final Runnable then) {
        if (currentChat != null) {
            then.run();
        } else {
            askStroomAiClient.createChat(chat -> {
                currentChat = chat;
                titleGenerated = false;
                lastSeenMessageId = 0;
                view.setChatTitle(chat.getTitle());
                // As for a new chat (onNewChat), the chat can now be downloaded and cleared
                view.setDownloadEnabled(true);
                view.setDeleteAllEnabled(true);
                then.run();
            }, this);
        }
    }

    /// Auto-generate a conversation title from the first user message.
    /// Only fires once per conversation.
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

        view.setChatTitle(title);
        askStroomAiClient.updateChatTitle(currentChat.getId(), title, success -> {
            // Title persisted successfully — nothing more to do.
        }, this);
    }

    /// Returns a human-readable title for the given context type.
    private String getContextTitle(final AskStroomAiContext context) {
        return context.getDescription();
    }

    // ---- Dock preference helpers ----

    private void getDockBehaviourFromPrefs(final Consumer<DockBehaviour> consumer) {
        askStroomAiClient.getConfig(currentConfig -> {
            if (currentConfig != null) {
                final DockType type = currentConfig.getDockType();
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
        showing = true;
        ControllerDialog.show(this)
                .popupType(PopupType.CLOSE_DIALOG)
                .popupSize(PopupSize.resizable(700, 500))
                .caption("Ask Stroom AI")
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    // Notify MainController so the toolbar toggle button is reset.
                    ShowAskStroomAiEvent.fire(this, false);
                })
                .onHide(e -> showing = false)
                .fire();
    }

    private void onAction(final AskStroomAiAction action) {
        switch (action) {
            case CANCEL -> onCancelProcessing();
            case CONFIGURE -> onChangeConfig();
            case NEW_CHAT -> onNewChat();
            case SHOW_HISTORY -> onShowHistory();
            case DOWNLOAD -> onDownloadChat();
            case DELETE_ALL -> onDeleteAllMessages();
        }
    }

    /// The chat's requests show their progress where its owners say, or as app-wide tasks.
    ///
    /// @return A task monitor.
    @Override
    public TaskMonitor createTaskMonitor() {
        return taskMonitor().createTaskMonitor();
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
