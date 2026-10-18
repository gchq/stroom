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

package stroom.query.client.event;

import stroom.docref.DocRef;
import stroom.pipeline.stepping.client.event.SteppingCodeChangeEvent.Approver;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

/// Asks an open Query's tab to do something with a revised query proposed in the AI chat: apply it to the query's
/// editor (unsaved), say whether an applied change can still be undone, or undo it. The chat fires this rather than
/// using the Query's tab directly, so that it needs nothing from the query screens to compile.
///
/// Events are handled straight away, so once fired, [#isHandled()] says whether an open Query took it, and the
/// other results can be read.
public class QueryCodeChangeEvent extends GwtEvent<QueryCodeChangeEvent.Handler> {

    private static Type<Handler> TYPE;

    private final Operation operation;
    private final DocRef query;
    private final String baseQuery;
    private final String proposedQuery;
    private final Approver approver;
    private final Runnable onApplied;

    private boolean handled;
    private boolean canUndo;
    private boolean undone;
    private String message;

    private QueryCodeChangeEvent(final Operation operation,
                                 final DocRef query,
                                 final String baseQuery,
                                 final String proposedQuery,
                                 final Approver approver,
                                 final Runnable onApplied) {
        this.operation = operation;
        this.query = query;
        this.baseQuery = baseQuery;
        this.proposedQuery = proposedQuery;
        this.approver = approver;
        this.onApplied = onApplied;
    }

    /// Asks the Query's tab to apply a revised query to its editor, once the approver allows it.
    ///
    /// @param handlers      Where to fire it.
    /// @param query         The Query.
    /// @param baseQuery     The query the change was proposed against.
    /// @param proposedQuery The whole revised query.
    /// @param approver      Asked before the change is applied (e.g. to record it on the server).
    /// @param onApplied     Called once the change has been applied.
    /// @return The event, once handled.
    public static QueryCodeChangeEvent fireApply(final HasHandlers handlers,
                                                 final DocRef query,
                                                 final String baseQuery,
                                                 final String proposedQuery,
                                                 final Approver approver,
                                                 final Runnable onApplied) {
        return fire(handlers, new QueryCodeChangeEvent(Operation.APPLY, query, baseQuery, proposedQuery, approver,
                onApplied));
    }

    /// Asks the Query's tab whether the change last applied to it can still be undone, i.e. its editor still holds
    /// exactly the applied query.
    ///
    /// @param handlers Where to fire it.
    /// @param query    The Query.
    /// @return The event, once handled.
    public static QueryCodeChangeEvent fireCanUndo(final HasHandlers handlers, final DocRef query) {
        return fire(handlers, new QueryCodeChangeEvent(Operation.CAN_UNDO, query, null, null, null, null));
    }

    /// Asks the Query's tab to put back the query its editor held before the change last applied.
    ///
    /// @param handlers Where to fire it.
    /// @param query    The Query.
    /// @return The event, once handled.
    public static QueryCodeChangeEvent fireUndo(final HasHandlers handlers, final DocRef query) {
        return fire(handlers, new QueryCodeChangeEvent(Operation.UNDO, query, null, null, null, null));
    }

    private static QueryCodeChangeEvent fire(final HasHandlers handlers, final QueryCodeChangeEvent event) {
        handlers.fireEvent(event);
        return event;
    }

    /// @return The event's type.
    public static Type<Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<Handler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final Handler handler) {
        handler.onQueryCodeChange(this);
    }

    /// @return What is asked.
    public Operation getOperation() {
        return operation;
    }

    /// @return The Query.
    public DocRef getQuery() {
        return query;
    }

    /// @return The query the change was proposed against, or null.
    public String getBaseQuery() {
        return baseQuery;
    }

    /// @return The whole revised query, or null.
    public String getProposedQuery() {
        return proposedQuery;
    }

    /// @return Asked before the change is applied, or null.
    public Approver getApprover() {
        return approver;
    }

    /// @return Called once the change has been applied, or null.
    public Runnable getOnApplied() {
        return onApplied;
    }

    /// @return True if an open Query took the event.
    public boolean isHandled() {
        return handled;
    }

    /// Marks the event as taken by an open Query.
    ///
    /// @param message Something to tell the user, e.g. why the change can't be applied, or null.
    public void setHandled(final String message) {
        this.handled = true;
        this.message = message;
    }

    /// @return Something to tell the user, or null.
    public String getMessage() {
        return message;
    }

    /// @return For [Operation#CAN_UNDO], true if the change can still be undone.
    public boolean isCanUndo() {
        return canUndo;
    }

    /// @param canUndo For [Operation#CAN_UNDO], true if the change can still be undone.
    public void setCanUndo(final boolean canUndo) {
        this.canUndo = canUndo;
    }

    /// @return For [Operation#UNDO], true if the change was undone.
    public boolean isUndone() {
        return undone;
    }

    /// @param undone For [Operation#UNDO], true if the change was undone.
    public void setUndone(final boolean undone) {
        this.undone = undone;
    }


    // --------------------------------------------------------------------------------


    /// What the chat asks of the Query.
    public enum Operation {
        /// Apply a revised query to the editor.
        APPLY,
        /// Say whether the change last applied can still be undone.
        CAN_UNDO,
        /// Undo the change last applied.
        UNDO
    }


    // --------------------------------------------------------------------------------


    /// Handles [QueryCodeChangeEvent].
    public interface Handler extends EventHandler {

        /// @param event The event.
        void onQueryCodeChange(QueryCodeChangeEvent event);
    }
}
