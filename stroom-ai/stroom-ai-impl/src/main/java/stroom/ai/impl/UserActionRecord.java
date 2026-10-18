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

package stroom.ai.impl;

import stroom.docref.DocRef;

import java.util.List;

/// A step that is the user's, offered as a button on a tool call's card, as kept (within its
/// [ToolResultRecord]) once Stroom has checked it. Everything shown is written by Stroom, from its own state;
/// the server's own wording is only kept for [#after] conditions, shown as plain text.
///
/// @param id        The server's id for the action, unique within the result.
/// @param action    What the button does, one of the [UserActions] actions.
/// @param label     The button's text, written by Stroom.
/// @param details   What Stroom knows about what the button affects, e.g. a filter's selection, or null.
/// @param warning   Something the user should know before they click, written by Stroom, or null.
/// @param filterId  The processor filter, for enabling and disabling filters.
/// @param doc       The document the action is about (with its name as Stroom has it), or null.
/// @param request   An Elasticsearch request for the cluster admin, to show read-only, or the StroomQL of a query to
///                  create, or null.
/// @param after     What should be done first: other actions' labels, or the server's conditions.
/// @param expiresMs When the button stops working.
/// @param status    Whether the button has been used.
/// @param outcome   What happened when it was used, or null.
/// @param name       The name suggested for a document to create (e.g. a query), or null.
/// @param codeChange For a proposed code change (apply_code_change), what it changes and the code it was proposed
///                   against, or null. The proposed code is in `request` and the element's document in `doc`.
public record UserActionRecord(String id,
                               String action,
                               String label,
                               String details,
                               String warning,
                               Integer filterId,
                               DocRef doc,
                               String request,
                               List<String> after,
                               long expiresMs,
                               Status status,
                               String outcome,
                               String name,
                               CodeChange codeChange) {

    /// An action that creates nothing, so suggests no name.
    public UserActionRecord(final String id,
                            final String action,
                            final String label,
                            final String details,
                            final String warning,
                            final Integer filterId,
                            final DocRef doc,
                            final String request,
                            final List<String> after,
                            final long expiresMs,
                            final Status status,
                            final String outcome) {
        this(id, action, label, details, warning, filterId, doc, request, after, expiresMs, status, outcome, null);
    }

    /// An action that is not a proposed code change.
    public UserActionRecord(final String id,
                            final String action,
                            final String label,
                            final String details,
                            final String warning,
                            final Integer filterId,
                            final DocRef doc,
                            final String request,
                            final List<String> after,
                            final long expiresMs,
                            final Status status,
                            final String outcome,
                            final String name) {
        this(id, action, label, details, warning, filterId, doc, request, after, expiresMs, status, outcome, name,
                null);
    }

    /// @param status  The new status.
    /// @param outcome What happened.
    /// @return This action with the status and outcome changed.
    public UserActionRecord withStatus(final Status status, final String outcome) {
        return new UserActionRecord(id, action, label, details, warning, filterId, doc, request, after, expiresMs,
                status, outcome, name, codeChange);
    }

    /// A change to a stepping element's code that the agent proposed, which the user can review and apply to the
    /// element's editor, unsaved.
    ///
    /// @param pipeline     The pipeline being stepped.
    /// @param element      The element's id, as named in the pipeline.
    /// @param baseCode     The element's code the change was proposed against, as the user had it (unsaved edits
    ///                     included). The change is only applied while the editor still holds it.
    /// @param baseHash     The SHA-256 of the base code, as hex.
    /// @param snapshotHash The hash of the tab snapshot the base code came from, or null.
    /// @param added        How many lines the change adds or changes to.
    /// @param removed      How many lines the change removes or changes from.
    public record CodeChange(DocRef pipeline,
                             String element,
                             String baseCode,
                             String baseHash,
                             String snapshotHash,
                             int added,
                             int removed) {

    }

    /// Whether a button has been used.
    public enum Status {
        PENDING,
        DONE,
        FAILED
    }
}
