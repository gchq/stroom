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

import stroom.ai.shared.AiUserActionResult;
import stroom.alert.client.event.AlertEvent;
import stroom.docref.DocRef;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.document.client.event.OpenDocumentEvent.CommonDocLinkTab;
import stroom.entity.client.presenter.DocTabPresenter;
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.pipeline.stepping.client.presenter.CodeChangeReviewPresenter;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.NullSafe;

import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.function.Consumer;

/// Reviews, applies and undoes the changes to documents' Documentation tabs that the agent proposed
/// (`apply_documentation_change`). The document is opened if it isn't open, on its Documentation tab, and the change
/// is reviewed side by side; applying it is checked and recorded by the server first (which needs Edit), then put
/// in the tab's editor as an unsaved edit for the user to save. Undo apply puts back the text the tab held, while
/// it still holds the applied text. A Documentation document's own content is its Documentation tab, so a change to
/// an AGENTS doc is applied the same way; standing instructions for a folder with none are reviewed and created
/// (`create_instructions`).
@Singleton
public class AiDocumentationChanges {

    private final AskStroomAiClient askStroomAiClient;
    private final Provider<CodeChangeReviewPresenter> reviewPresenterProvider;
    private final AppliedDocumentationChanges applied = new AppliedDocumentationChanges();

    /// @param askStroomAiClient       Records each change, and its undoing, on the server.
    /// @param reviewPresenterProvider The side by side review dialog.
    @Inject
    public AiDocumentationChanges(final AskStroomAiClient askStroomAiClient,
                                  final Provider<CodeChangeReviewPresenter> reviewPresenterProvider) {
        this.askStroomAiClient = askStroomAiClient;
        this.reviewPresenterProvider = reviewPresenterProvider;
    }

    /// Opens the document on its Documentation tab and shows the review of the change, from which the user can apply
    /// it to the tab's editor, unsaved.
    ///
    /// @param handlers  Where to fire events.
    /// @param monitor   Shows the server's calls in progress.
    /// @param change    The change.
    /// @param onApplied Called once the change has been applied.
    public void review(final HasHandlers handlers,
                       final TaskMonitorFactory monitor,
                       final Change change,
                       final Runnable onApplied) {
        openDocumentation(handlers, change.doc(), editor -> {
            final CodeChangeReviewPresenter review = reviewPresenterProvider.get();
            review.show("Review Proposed Documentation - " + name(change.doc()), change.base(), change.proposed(),
                    AceEditorMode.MARKDOWN, false, (andStep, text, rejected, onResult) -> {
                        final String problem = AppliedDocumentationChanges.applyProblem(editor.isReadOnly(),
                                editor.getText(), change.base());
                        if (problem != null) {
                            onResult.accept(problem);
                            return;
                        }
                        askStroomAiClient.applyCodeChange(change.chatId(), change.messageId(), change.actionId(),
                                rejected,
                                result -> {
                                    if (!result.isDone()) {
                                        onResult.accept(NullSafe.nonBlankStringElse(result.getMessage(),
                                                "The change can't be applied."));
                                        return;
                                    }
                                    final String previous = editor.getText();
                                    if (editor.setEditedText(text)) {
                                        applied.applied(change.key(), previous, editor.getText());
                                    }
                                    onResult.accept(null);
                                    onApplied.run();
                                },
                                error -> onResult.accept(NullSafe.nonBlankStringElse(error.getMessage(),
                                        "Unable to apply the change.")),
                                monitor);
                    });
            final String problem = AppliedDocumentationChanges.applyProblem(editor.isReadOnly(), editor.getText(),
                    change.base());
            if (problem != null) {
                review.showProblem(problem);
            }
        });
    }

    /// Shows the review of standing instructions (an AGENTS doc) proposed for a folder that has none
    /// (`create_instructions`); applying it asks the server to create the doc with the parts kept and save it (which
    /// needs Create in the folder), then opens it.
    ///
    /// @param handlers  Where to fire events.
    /// @param monitor   Shows the server's calls in progress.
    /// @param change    The proposal: its doc is the folder, and its base is empty.
    /// @param onCreated Called once the doc has been created.
    public void reviewNewInstructions(final HasHandlers handlers,
                                      final TaskMonitorFactory monitor,
                                      final Change change,
                                      final Runnable onCreated) {
        final CodeChangeReviewPresenter review = reviewPresenterProvider.get();
        review.show("Review Proposed Standing Instructions - " + name(change.doc()), "", change.proposed(),
                AceEditorMode.MARKDOWN, false, (andStep, text, rejected, onResult) ->
                        askStroomAiClient.applyCodeChange(change.chatId(), change.messageId(), change.actionId(),
                                rejected,
                                result -> {
                                    if (!result.isDone()) {
                                        onResult.accept(NullSafe.nonBlankStringElse(result.getMessage(),
                                                "The standing instructions can't be created."));
                                        return;
                                    }
                                    onResult.accept(null);
                                    if (result.getOpenDocument() != null) {
                                        OpenDocumentEvent.builder(handlers, result.getOpenDocument())
                                                .forceOpen(true)
                                                .fire();
                                    }
                                    onCreated.run();
                                },
                                error -> onResult.accept(NullSafe.nonBlankStringElse(error.getMessage(),
                                        "Unable to create the standing instructions.")),
                                monitor));
    }

    /// Opens the document on its Documentation tab and undoes the change, if the tab still holds the text applied.
    /// The server checks Edit and records it first.
    ///
    /// @param handlers    Where to fire events.
    /// @param monitor     Shows the server's call in progress.
    /// @param change      The change.
    /// @param onWithdrawn Called if the tab has been edited since, so Undo apply is withdrawn.
    /// @param onResult    Called with what the server said, once it has been asked.
    public void undo(final HasHandlers handlers,
                     final TaskMonitorFactory monitor,
                     final Change change,
                     final Runnable onWithdrawn,
                     final Consumer<AiUserActionResult> onResult) {
        openDocumentation(handlers, change.doc(), editor -> {
            if (applied.undoText(change.key(), editor.getText(), change.base(), change.proposed()) == null) {
                applied.withdraw(change.key());
                AlertEvent.fireInfo(handlers, AppliedDocumentationChanges.UNDO_WITHDRAWN, null);
                onWithdrawn.run();
                return;
            }
            askStroomAiClient.undoUserAction(change.chatId(), change.messageId(), change.actionId(), result -> {
                if (result.isDone()) {
                    // Checked again, as the user may have edited the tab meanwhile.
                    final String previous = applied.undoText(change.key(), editor.getText(), change.base(),
                            change.proposed());
                    if (previous == null || !editor.setEditedText(previous)) {
                        AlertEvent.fireWarn(handlers, AppliedDocumentationChanges.UNDO_WITHDRAWN, null);
                    }
                    applied.undone(change.key());
                }
                onResult.accept(result);
            }, monitor);
        });
    }

    /// @param messageId The tool result message the action is in.
    /// @param actionId  The action's id.
    /// @return True if the applied change's Undo apply has been withdrawn, as its tab has been edited since.
    public boolean isUndoWithdrawn(final int messageId, final String actionId) {
        return applied.isWithdrawn(key(messageId, actionId));
    }

    // Opens the document (or selects its tab), switches to its Documentation tab and gives its editor.
    private static void openDocumentation(final HasHandlers handlers,
                                          final DocRef doc,
                                          final Consumer<MarkdownEditPresenter> consumer) {
        if (doc == null) {
            return;
        }
        OpenDocumentEvent.builder(handlers, doc)
                .forceOpen(true)
                .selectedTab(CommonDocLinkTab.DOCUMENTATION)
                .callbackOnOpen(presenter -> {
                    final MarkdownEditPresenter editor = presenter instanceof final DocTabPresenter<?, ?> docTab
                            ? docTab.showDocumentationEditor()
                            : null;
                    if (editor == null) {
                        AlertEvent.fireWarn(handlers, name(doc) + " has no Documentation tab here, so the change "
                                                      + "can't be applied to it.", null);
                    } else {
                        consumer.accept(editor);
                    }
                })
                .fire();
    }

    private static String name(final DocRef doc) {
        return doc.getType() + " '" + NullSafe.nonBlankStringElse(doc.getName(), doc.getUuid()) + "'";
    }

    private static String key(final int messageId, final String actionId) {
        return messageId + ":" + actionId;
    }


    // --------------------------------------------------------------------------------


    /// A proposed change to a document's Documentation tab, as the chat shows it.
    ///
    /// @param chatId    The chat.
    /// @param messageId The tool result message the action is in.
    /// @param actionId  The action's id.
    /// @param doc       The document.
    /// @param base      The text the change was proposed against.
    /// @param proposed  The text proposed.
    public record Change(int chatId, int messageId, String actionId, DocRef doc, String base, String proposed) {

        /// @return What the change is known by.
        String key() {
            return AiDocumentationChanges.key(messageId, actionId);
        }
    }
}
