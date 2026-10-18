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
/// @param name      The name suggested for a document to create (e.g. a query), or null.
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
                               String name) {

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

    /// @param status  The new status.
    /// @param outcome What happened.
    /// @return This action with the status and outcome changed.
    public UserActionRecord withStatus(final Status status, final String outcome) {
        return new UserActionRecord(id, action, label, details, warning, filterId, doc, request, after, expiresMs,
                status, outcome, name);
    }

    /// Whether a button has been used.
    public enum Status {
        PENDING,
        DONE,
        FAILED
    }
}
