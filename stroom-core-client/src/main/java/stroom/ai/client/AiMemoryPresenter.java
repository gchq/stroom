/*
 * Copyright 2026 Crown Copyright
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

import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMemory;
import stroom.ai.shared.AiMemoryScope;
import stroom.ai.shared.DeleteAiMemoryRequest;
import stroom.ai.shared.SaveAiMemoryRequest;
import stroom.ai.shared.UndoAiMemoryChangeRequest;
import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.alert.client.event.PromptEvent;
import stroom.dispatch.client.RestError;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.List;

/// The Memory dialog: the user's own memory and the chat's, to view, edit and delete, and the changes the
/// agent has made to the user's memory in the chat, each of which can be undone.
public class AiMemoryPresenter extends MyPresenterWidget<AiMemoryPresenter.AiMemoryView>
        implements AiMemoryUiHandlers {

    private static final String USER_PREFIX = "/memories/";
    private static final String CHAT_PREFIX = "/memories/session/";
    private static final String CHAT_NAME_PREFIX = "session/";
    private static final String UNDO_ATTRIBUTE = "data-undo-memory";
    // Marks a note in the list that has changes not yet saved, including a new one.
    static final String UNSAVED_PREFIX = "[Unsaved] ";

    private final AskStroomAiClient client;
    private AiChat chat;
    private List<AiMemory> memories = new ArrayList<>();
    // The memory being edited, or null if none.
    private String selectedPath;
    // The version of the memory being edited, or null if it is new.
    private Integer selectedVersion;
    // The memory being edited as it was last saved ("" if it is new), to tell if it has been changed.
    private String savedContent = "";
    // Whether the list shows the memory being edited as unsaved, so the list is only redrawn when that changes.
    private boolean shownUnsaved;

    @Inject
    public AiMemoryPresenter(final EventBus eventBus,
                             final AiMemoryView view,
                             final AskStroomAiClient client) {
        super(eventBus, view);
        this.client = client;
        view.setUiHandlers(this);
    }

    /// Shows the dialog.
    ///
    /// @param chat The chat whose memory, and whose user's memory, to show.
    public void show(final AiChat chat) {
        this.chat = chat;
        select(null, null, "");
        getView().setContent("", false);
        refresh();
        ShowPopupEvent.builder(this)
                .popupType(PopupType.CLOSE_DIALOG)
                .popupSize(PopupSize.resizable(800, 600))
                .caption("Memory")
                .onHideRequest(e -> {
                    if (!isUnsaved()) {
                        e.hide();
                        return;
                    }
                    // Every path that doesn't close must reset the dialog, or it is left unusable.
                    ConfirmEvent.fire(this, discardMessage("Close without saving"), ok -> {
                        if (ok) {
                            e.hide();
                        } else {
                            e.reset();
                        }
                    });
                })
                .fire();
    }

    /// Makes a note the one being edited.
    private void select(final String path, final Integer version, final String content) {
        selectedPath = path;
        selectedVersion = version;
        savedContent = content;
    }

    /// @return True if the note being edited is new, or has changes, that haven't been saved.
    boolean isUnsaved() {
        return selectedPath != null
               && (selectedVersion == null || !getView().getContent().equals(savedContent));
    }

    /// @return A question asking whether to lose the unsaved note, e.g. "Close without saving 'notes.md'? ...".
    private String discardMessage(final String action) {
        return action + " '" + name(selectedPath) + "'? " + (selectedVersion == null
                ? "The new note will be lost."
                : "Your changes to it will be lost.");
    }

    /// Asks before losing an unsaved note, then does something if the user agrees (or there was nothing to lose).
    private void unlessUnsaved(final String action, final Runnable then, final Runnable otherwise) {
        if (!isUnsaved()) {
            then.run();
            return;
        }
        ConfirmEvent.fire(this, discardMessage(action), ok -> {
            if (ok) {
                then.run();
            } else {
                otherwise.run();
            }
        });
    }

    /// Shows the list of notes, with the one being edited (and any new one) marked if unsaved.
    private void showList() {
        shownUnsaved = isUnsaved();
        final List<String> labels = new ArrayList<>();
        int selectedIndex = -1;
        for (final AiMemory memory : memories) {
            final boolean selected = path(memory).equals(selectedPath);
            if (selected) {
                selectedIndex = labels.size();
            }
            labels.add((selected && shownUnsaved
                    ? UNSAVED_PREFIX
                    : "") + label(memory.getScope(), memory.getName()));
        }
        if (selectedPath != null && selectedIndex < 0) {
            // A new note, not saved yet.
            selectedIndex = labels.size();
            labels.add(UNSAVED_PREFIX + label(selectedPath.startsWith(CHAT_PREFIX)
                    ? AiMemoryScope.CHAT
                    : AiMemoryScope.USER, name(selectedPath)));
        }
        getView().setMemories(labels, selectedIndex);
    }

    @Override
    public void onContentChanged() {
        if (isUnsaved() != shownUnsaved) {
            showList();
        }
    }

    private void refresh() {
        client.listMemory(chat.getId(), response -> {
            memories = new ArrayList<>(response.getMemories());
            showList();
            getView().setChanges(changesHtml(response.getChanges()));
        }, this::showError, this);
    }

    @Override
    public void onMemorySelected(final int index) {
        if (index >= memories.size() || path(memories.get(index)).equals(selectedPath)) {
            // The note being edited, e.g. the new one.
            return;
        }
        final AiMemory memory = memories.get(index);
        unlessUnsaved("Open another note without saving", () -> {
            select(path(memory), memory.getVersion(), memory.getContent());
            getView().setContent(memory.getContent(), true);
            showList();
        }, this::showList);
    }

    @Override
    public void onNewMemory() {
        final String message = "Name for the new memory, e.g. preferences.md. Start it with " + CHAT_NAME_PREFIX
                               + " to keep it to this chat.";
        unlessUnsaved("Start a new note without saving", () ->
                PromptEvent.fire(this, "New Memory", message, "", name -> {
                    if (name != null && !name.isBlank()) {
                        final String trimmed = name.strip();
                        select(trimmed.startsWith(CHAT_NAME_PREFIX)
                                ? CHAT_PREFIX + trimmed.substring(CHAT_NAME_PREFIX.length())
                                : USER_PREFIX + trimmed, null, "");
                        getView().setContent("", true);
                        // Shown in the list straight away, marked unsaved, so it is clear it needs saving.
                        showList();
                    }
                }), () -> {
                    // Keep editing the unsaved note.
                });
    }

    @Override
    public void onSaveMemory() {
        if (selectedPath == null) {
            return;
        }
        final String content = getView().getContent();
        client.saveMemory(new SaveAiMemoryRequest(chat, selectedPath, content, selectedVersion),
                memory -> {
                    selectedVersion = memory.getVersion();
                    savedContent = content;
                    refresh();
                }, this::showError, this);
    }

    @Override
    public void onDeleteMemory() {
        if (selectedPath == null) {
            return;
        }
        if (selectedVersion == null) {
            // Never saved, so there is nothing to delete.
            select(null, null, "");
            getView().setContent("", false);
            showList();
            return;
        }
        final String path = selectedPath;
        final Integer version = selectedVersion;
        ConfirmEvent.fire(this, deleteMessage(path), ok -> {
            if (ok) {
                client.deleteMemory(new DeleteAiMemoryRequest(chat, path, version), done -> {
                    select(null, null, "");
                    getView().setContent("", false);
                    refresh();
                }, this::showError, this);
            }
        });
    }

    /// @return What deleting a memory note means, in words, e.g. "Delete the note 'preferences.md'? ...".
    private static String deleteMessage(final String path) {
        if (path.startsWith(CHAT_PREFIX)) {
            return "Delete the note '" + path.substring(CHAT_PREFIX.length()) + "' from this chat's memory? "
                   + "Stroom AI will forget it in this chat. This can't be undone.";
        }
        final String name = path.startsWith(USER_PREFIX)
                ? path.substring(USER_PREFIX.length())
                : path;
        return "Delete the note '" + name + "' from your memory? Stroom AI uses your memory in all your chats, "
               + "so it will forget it in all of them. This can't be undone.";
    }

    @Override
    public void onClearUserMemory() {
        final String message = "Delete all your memory notes? Stroom AI uses them in all your chats, so it will "
                               + "forget everything in them. The notes for this chat only are kept. This can't be "
                               + "undone.";
        ConfirmEvent.fire(this, message, ok -> {
            if (ok) {
                client.deleteMemory(new DeleteAiMemoryRequest(chat, USER_PREFIX, null), done -> {
                    if (selectedPath != null && !selectedPath.startsWith(CHAT_PREFIX)) {
                        select(null, null, "");
                        getView().setContent("", false);
                    }
                    refresh();
                }, this::showError, this);
            }
        });
    }

    @Override
    public void onUndoChange(final int messageId) {
        ConfirmEvent.fire(this, "Undo this change to your memory?", ok -> {
            if (ok) {
                client.undoMemoryChange(new UndoAiMemoryChangeRequest(chat, messageId), done -> refresh(),
                        this::showError, this);
            }
        });
    }

    private static String label(final AiMemoryScope scope, final String name) {
        return (scope == AiMemoryScope.USER
                ? "Yours: "
                : "This chat: ") + name;
    }

    /// @return The note's name, without where it is kept.
    private static String name(final String path) {
        if (path.startsWith(CHAT_PREFIX)) {
            return path.substring(CHAT_PREFIX.length());
        }
        return path.startsWith(USER_PREFIX)
                ? path.substring(USER_PREFIX.length())
                : path;
    }

    private static String path(final AiMemory memory) {
        return (memory.getScope() == AiMemoryScope.USER
                ? USER_PREFIX
                : CHAT_PREFIX) + memory.getName();
    }

    private static SafeHtml changesHtml(final List<AiChatMessage> changes) {
        if (changes == null || changes.isEmpty()) {
            return SafeHtmlUtils.fromString("None.");
        }
        final HtmlBuilder hb = new HtmlBuilder();
        for (final AiChatMessage change : changes) {
            final String json = change.getMessage();
            hb.div(row -> {
                row.span(text -> text.append(operationText(AiToolMessages.getString(json, "operation")) + " "
                                             + AiToolMessages.getString(json, "path")),
                        Attribute.className("aiMemory-change"));
                if (AiToolMessages.getBoolean(json, "undone")) {
                    row.span(text -> text.append("Undone"), Attribute.className("aiMemory-undone"));
                } else {
                    row.elem("Undo", SafeHtmlUtils.fromSafeConstant("button"),
                            Attribute.className("allow-focus ai-approval-button"),
                            new Attribute(UNDO_ATTRIBUTE, String.valueOf(change.getId())));
                }
            }, Attribute.className("aiMemory-changeRow"));
        }
        return hb.toSafeHtml();
    }

    private static String operationText(final String operation) {
        if ("CREATE".equals(operation)) {
            return "Created";
        } else if ("DELETE".equals(operation)) {
            return "Deleted";
        } else {
            return "Changed";
        }
    }

    private void showError(final RestError error) {
        AlertEvent.fireError(this, error.getMessage(), null);
    }


    // --------------------------------------------------------------------------------


    public interface AiMemoryView extends View, HasUiHandlers<AiMemoryUiHandlers> {

        /// Shows the memory there is.
        ///
        /// @param labels        A label for each piece of memory.
        /// @param selectedIndex The one selected, or -1.
        void setMemories(List<String> labels, int selectedIndex);

        /// Shows the content of the memory being edited.
        ///
        /// @param text     The content.
        /// @param editable True if there is memory to edit.
        void setContent(String text, boolean editable);

        /// @return The content as the user has edited it.
        String getContent();

        /// Shows the changes the agent has made to memory, with buttons to undo them.
        void setChanges(SafeHtml html);
    }
}
