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

package stroom.document.client;

import stroom.docref.DocRef;
import stroom.task.client.TaskMonitorFactory;

import java.util.function.Consumer;

/**
 * Extension point for document types that need to collect additional
 * configuration from the user immediately after a new document is
 * created but before the editor tab opens.
 *
 * <p>Implementations should show a modal dialog to gather user input.
 * On confirmation, the implementation should apply the collected data
 * to the document (via load/patch/save) and call
 * {@code onComplete.accept(true)}. On cancellation, the implementation
 * <strong>must</strong> delete the freshly-created document, refresh
 * the explorer tree, and call {@code onComplete.accept(false)}.</p>
 *
 * <p>The default implementation (returned by
 * {@link DocumentPlugin#getInitialisationHandler()}) does nothing and
 * immediately calls {@code onComplete.accept(true)}, preserving
 * backward compatibility for document types that do not need
 * initialisation.</p>
 *
 * <h3>Postconditions</h3>
 * <ul>
 *   <li>If {@code onComplete} receives {@code true}: the document
 *       exists and is ready to be opened in an editor tab.</li>
 *   <li>If {@code onComplete} receives {@code false}: the document
 *       has been deleted from the explorer; the caller must not
 *       attempt to open it.</li>
 * </ul>
 */
public interface DocInitialisationHandler {

    /**
     * Shows an initialisation dialog for a freshly-created document, or
     * does nothing if no initialisation is required.
     *
     * <p><b>Creation only. This must not be wired into the open path.</b> Cancelling is defined to
     * <em>delete</em> the document — which is right for a document created moments ago and
     * abandoned, and catastrophic for one being opened, where the user would expect Cancel to mean
     * "never mind" rather than "destroy it". The single call site is in
     * {@code DocumentPluginEventManager.fireShowCreateDocumentDialogEvent}, and it should stay the
     * only one.</p>
     *
     * <p>Preconditions:</p>
     * <ul>
     *   <li>{@code docRef} must be non-null and refer to a document that
     *       has already been created on the server.</li>
     *   <li>{@code onComplete} must be non-null.</li>
     *   <li>{@code taskMonitorFactory} must be non-null.</li>
     * </ul>
     *
     * @param docRef              the new document's DocRef; never null.
     *                            Must refer to a document that already
     *                            exists on the server.
     * @param onComplete          completion callback; never null.
     *                            The handler must call exactly one of:
     *                            <ul>
     *                              <li>{@code onComplete.accept(true)} —
     *                                  the document has been successfully
     *                                  initialised and is ready to be
     *                                  opened in an editor tab.</li>
     *                              <li>{@code onComplete.accept(false)} —
     *                                  the user cancelled; the handler
     *                                  has already deleted the document
     *                                  from the explorer and refreshed
     *                                  the tree. The caller must not
     *                                  attempt to open the document.</li>
     *                            </ul>
     * @param taskMonitorFactory  factory for task progress indicators;
     *                            never null
     */
    void showInitialisationDialog(DocRef docRef,
                                  Consumer<Boolean> onComplete,
                                  TaskMonitorFactory taskMonitorFactory);
}
