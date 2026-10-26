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


package stroom.dashboard.client.event;

import stroom.dashboard.client.table.TableChange;
import stroom.docref.DocRef;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

import java.util.List;
import java.util.function.Consumer;

/// Asks the dashboards to do something with a change to a dashboard table proposed in the AI chat: say whether
/// the dashboard is open and can be edited, apply the change to it (opening it first if it isn't open), or say
/// whether an applied change can be undone, and undo it. The chat fires this rather than using the dashboard
/// screens directly, so it needs nothing from them to compile.
///
/// Events are handled straight away, so once fired, [#isHandled()] says whether they were taken; applying then
/// goes on as needed (opening first), and ends by calling one of the callbacks given.
public class ApplyDashboardChangeEvent extends GwtEvent<ApplyDashboardChangeEvent.Handler> {

    /// Why an applied change can no longer be undone.
    public static final String UNDO_WITHDRAWN = "What this changed on the dashboard has been changed again since "
                                                + "it was applied, so Undo apply would lose those changes. Close the "
                                                + "dashboard without saving to go back, or change it by hand.";
    /// Why a change can't be applied to a dashboard the user can't edit.
    public static final String NOT_EDITABLE = "You can't edit this dashboard: it is read-only, or you don't have "
                                              + "permission to edit it.";

    private static Type<Handler> TYPE;

    private final Operation operation;
    private final DocRef dashboard;
    private final String componentId;
    private final String componentName;
    private final List<TableChange> changes;
    private final Approver approver;
    private final Runnable onApplied;
    private final Consumer<String> onNotApplied;
    private final String undoKey;

    private boolean handled;
    private boolean open;
    private boolean editable;
    private boolean canUndo;
    private boolean undone;

    private ApplyDashboardChangeEvent(final Operation operation,
                                      final DocRef dashboard,
                                      final String componentId,
                                      final String componentName,
                                      final List<TableChange> changes,
                                      final Approver approver,
                                      final Runnable onApplied,
                                      final Consumer<String> onNotApplied,
                                      final String undoKey) {
        this.operation = operation;
        this.dashboard = dashboard;
        this.componentId = componentId;
        this.componentName = componentName;
        this.changes = changes;
        this.approver = approver;
        this.onApplied = onApplied;
        this.onNotApplied = onNotApplied;
        this.undoKey = undoKey;
    }

    /// Asks whether a dashboard is open, and if so whether the user can edit it.
    ///
    /// @param handlers  Where to fire it.
    /// @param dashboard The dashboard.
    /// @return The event, once handled.
    public static ApplyDashboardChangeEvent fireCheck(final HasHandlers handlers, final DocRef dashboard) {
        final ApplyDashboardChangeEvent event = new ApplyDashboardChangeEvent(Operation.CHECK, dashboard, null,
                null, null, null, null, null, null);
        handlers.fireEvent(event);
        return event;
    }

    /// Asks for a change to be applied to a table on a dashboard, unsaved: the dashboard is opened if it isn't
    /// open, and it isn't applied if the dashboard can't be edited. It is applied on top of the dashboard as it is
    /// then, unsaved changes and changes made since it was proposed included: each of its changes finds what it
    /// changes again (failing, all or none, if that has gone), and a query expression it replaces must be as it was.
    ///
    /// @param handlers      Where to fire it.
    /// @param dashboard     The dashboard.
    /// @param componentId   The table's component id.
    /// @param componentName The table's name as the user sees it.
    /// @param changes       The changes, in order.
    /// @param approver      Asked just before the change is applied (e.g. to record it on the server).
    /// @param onApplied     Called once it has been applied.
    /// @param onNotApplied  Called with why it wasn't applied, or null if the user chose not to.
    /// @param undoKey       What the change is known by, to undo it with [#fireUndo(HasHandlers, DocRef, String)].
    /// @return The event, once handled.
    public static ApplyDashboardChangeEvent fireApply(final HasHandlers handlers,
                                                      final DocRef dashboard,
                                                      final String componentId,
                                                      final String componentName,
                                                      final List<TableChange> changes,
                                                      final Approver approver,
                                                      final Runnable onApplied,
                                                      final Consumer<String> onNotApplied,
                                                      final String undoKey) {
        final ApplyDashboardChangeEvent event = new ApplyDashboardChangeEvent(Operation.APPLY, dashboard,
                componentId, componentName, changes, approver, onApplied, onNotApplied, undoKey);
        handlers.fireEvent(event);
        return event;
    }

    /// Asks whether an applied change can still be undone: its dashboard is open, and is as the change left it.
    ///
    /// @param handlers  Where to fire it.
    /// @param dashboard The dashboard.
    /// @param undoKey   What the change is known by.
    /// @return The event, once handled; [#isCanUndo()] gives the answer if it was.
    public static ApplyDashboardChangeEvent fireCanUndo(final HasHandlers handlers,
                                                        final DocRef dashboard,
                                                        final String undoKey) {
        final ApplyDashboardChangeEvent event = new ApplyDashboardChangeEvent(Operation.CAN_UNDO, dashboard, null,
                null, null, null, null, null, undoKey);
        handlers.fireEvent(event);
        return event;
    }

    /// Undoes an applied change, putting its table back as it was, unsaved, if the dashboard is open and as the
    /// change left it.
    ///
    /// @param handlers  Where to fire it.
    /// @param dashboard The dashboard.
    /// @param undoKey   What the change is known by.
    /// @return The event, once handled; [#isUndone()] says whether it was undone.
    public static ApplyDashboardChangeEvent fireUndo(final HasHandlers handlers,
                                                     final DocRef dashboard,
                                                     final String undoKey) {
        final ApplyDashboardChangeEvent event = new ApplyDashboardChangeEvent(Operation.UNDO, dashboard, null,
                null, null, null, null, null, undoKey);
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
        handler.onApplyDashboardChange(this);
    }

    /// @return What is asked.
    public Operation getOperation() {
        return operation;
    }

    /// @return The dashboard.
    public DocRef getDashboard() {
        return dashboard;
    }

    /// @return The table's component id.
    public String getComponentId() {
        return componentId;
    }

    /// @return The table's name as the user sees it.
    public String getComponentName() {
        return componentName;
    }

    /// @return The changes, in order.
    public List<TableChange> getChanges() {
        return changes;
    }

    /// @return What the change is known by, to undo it, or null.
    public String getUndoKey() {
        return undoKey;
    }

    /// @return Asked just before the change is applied.
    public Approver getApprover() {
        return approver;
    }

    /// Says the change has been applied.
    public void applied() {
        if (onApplied != null) {
            onApplied.run();
        }
    }

    /// Says the change hasn't been applied.
    ///
    /// @param message Why, or null if the user chose not to.
    public void notApplied(final String message) {
        if (onNotApplied != null) {
            onNotApplied.accept(message);
        }
    }

    /// @return True if the event was taken.
    public boolean isHandled() {
        return handled;
    }

    /// Marks the event as taken.
    ///
    /// @param open     For [Operation#CHECK], true if the dashboard is open.
    /// @param editable For [Operation#CHECK], true if it is open and the user can edit it.
    public void setHandled(final boolean open, final boolean editable) {
        this.handled = true;
        this.open = open;
        this.editable = editable;
    }

    /// @return For [Operation#CHECK], true if the dashboard is open.
    public boolean isOpen() {
        return open;
    }

    /// @return For [Operation#CHECK], true if it is open and the user can edit it.
    public boolean isEditable() {
        return editable;
    }

    /// Marks a [Operation#CAN_UNDO] event as taken.
    ///
    /// @param canUndo True if the change can be undone.
    public void setCanUndo(final boolean canUndo) {
        this.handled = true;
        this.canUndo = canUndo;
    }

    /// @return For [Operation#CAN_UNDO], true if the change can be undone.
    public boolean isCanUndo() {
        return canUndo;
    }

    /// Marks an [Operation#UNDO] event as taken.
    ///
    /// @param undone True if the change was undone.
    public void setUndone(final boolean undone) {
        this.handled = true;
        this.undone = undone;
    }

    /// @return For [Operation#UNDO], true if the change was undone.
    public boolean isUndone() {
        return undone;
    }


    // --------------------------------------------------------------------------------


    /// What is asked of the dashboards.
    public enum Operation {
        /// Say whether the dashboard is open and can be edited.
        CHECK,
        /// Apply the change.
        APPLY,
        /// Say whether an applied change can be undone.
        CAN_UNDO,
        /// Undo an applied change.
        UNDO
    }


    // --------------------------------------------------------------------------------


    /// Asked just before a change is applied, e.g. to check and record it on the server.
    public interface Approver {

        /// @param onResult Given null if the change may be applied, or a message saying why it may not.
        void approve(Consumer<String> onResult);
    }


    // --------------------------------------------------------------------------------


    /// Handles [ApplyDashboardChangeEvent].
    public interface Handler extends EventHandler {

        /// @param event The event.
        void onApplyDashboardChange(ApplyDashboardChangeEvent event);
    }
}
