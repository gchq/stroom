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

package stroom.pipeline.stepping.client.event;

import stroom.docref.DocRef;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

import java.util.List;
import java.util.function.Consumer;

/// Asks the stepping screen of an open pipeline to do something with a code change proposed in the AI chat: open
/// its review (from which the user can apply it to the element's editor, unsaved), say whether an applied change
/// can still be undone, or undo it. The chat fires this rather than using the stepping screen directly, so that it
/// needs nothing from the pipeline screens to compile.
///
/// Events are handled straight away, so once fired, [#isHandled()] says whether an open stepping screen took it,
/// and the other results can be read.
public class SteppingCodeChangeEvent extends GwtEvent<SteppingCodeChangeEvent.Handler> {

    private static Type<Handler> TYPE;

    private final Operation operation;
    private final DocRef pipeline;
    private final String elementId;
    private final DocRef elementDoc;
    private final String baseCode;
    private final String proposedCode;
    private final Approver approver;
    private final Runnable onApplied;

    private boolean handled;
    private boolean canUndo;
    private boolean undone;
    private String message;

    private SteppingCodeChangeEvent(final Operation operation,
                                    final DocRef pipeline,
                                    final String elementId,
                                    final DocRef elementDoc,
                                    final String baseCode,
                                    final String proposedCode,
                                    final Approver approver,
                                    final Runnable onApplied) {
        this.operation = operation;
        this.pipeline = pipeline;
        this.elementId = elementId;
        this.elementDoc = elementDoc;
        this.baseCode = baseCode;
        this.proposedCode = proposedCode;
        this.approver = approver;
        this.onApplied = onApplied;
    }

    /// Asks the stepping screen to open the review of a proposed change.
    ///
    /// @param handlers     Where to fire it.
    /// @param pipeline     The pipeline being stepped.
    /// @param elementId    The element's id, as named in the pipeline.
    /// @param elementDoc   The document the element uses, as when the change was proposed.
    /// @param baseCode     The code the change was proposed against.
    /// @param proposedCode The whole proposed code.
    /// @param approver     Asked before the change is applied (e.g. to record it on the server).
    /// @param onApplied    Called once the change has been applied.
    /// @return The event, once handled.
    public static SteppingCodeChangeEvent fireReview(final HasHandlers handlers,
                                                     final DocRef pipeline,
                                                     final String elementId,
                                                     final DocRef elementDoc,
                                                     final String baseCode,
                                                     final String proposedCode,
                                                     final Approver approver,
                                                     final Runnable onApplied) {
        return fire(handlers, new SteppingCodeChangeEvent(Operation.REVIEW, pipeline, elementId, elementDoc,
                baseCode, proposedCode, approver, onApplied));
    }

    /// Asks the stepping screen whether the change last applied to an element can still be undone, i.e. its
    /// editor still holds exactly the applied text.
    ///
    /// @param handlers  Where to fire it.
    /// @param pipeline  The pipeline being stepped.
    /// @param elementId The element's id.
    /// @return The event, once handled.
    public static SteppingCodeChangeEvent fireCanUndo(final HasHandlers handlers,
                                                      final DocRef pipeline,
                                                      final String elementId) {
        return fire(handlers, new SteppingCodeChangeEvent(Operation.CAN_UNDO, pipeline, elementId, null, null,
                null, null, null));
    }

    /// Asks the stepping screen to put back the text an element's editor held before the change last applied.
    ///
    /// @param handlers  Where to fire it.
    /// @param pipeline  The pipeline being stepped.
    /// @param elementId The element's id.
    /// @return The event, once handled.
    public static SteppingCodeChangeEvent fireUndo(final HasHandlers handlers,
                                                   final DocRef pipeline,
                                                   final String elementId) {
        return fire(handlers, new SteppingCodeChangeEvent(Operation.UNDO, pipeline, elementId, null, null, null,
                null, null));
    }

    private static SteppingCodeChangeEvent fire(final HasHandlers handlers, final SteppingCodeChangeEvent event) {
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
        handler.onSteppingCodeChange(this);
    }

    /// @return What is asked.
    public Operation getOperation() {
        return operation;
    }

    /// @return The pipeline being stepped.
    public DocRef getPipeline() {
        return pipeline;
    }

    /// @return The element's id, as named in the pipeline.
    public String getElementId() {
        return elementId;
    }

    /// @return The document the element used when the change was proposed, or null.
    public DocRef getElementDoc() {
        return elementDoc;
    }

    /// @return The code the change was proposed against, or null.
    public String getBaseCode() {
        return baseCode;
    }

    /// @return The whole proposed code, or null.
    public String getProposedCode() {
        return proposedCode;
    }

    /// @return Asked before the change is applied, or null.
    public Approver getApprover() {
        return approver;
    }

    /// @return Called once the change has been applied, or null.
    public Runnable getOnApplied() {
        return onApplied;
    }

    /// @return True if an open stepping screen took the event.
    public boolean isHandled() {
        return handled;
    }

    /// Marks the event as taken by an open stepping screen.
    ///
    /// @param message Something to tell the user, e.g. why the review couldn't be opened, or null.
    public void setHandled(final String message) {
        this.handled = true;
        this.message = message;
    }

    /// @return For [Operation#CAN_UNDO], true if the change can still be undone.
    public boolean isCanUndo() {
        return canUndo;
    }

    /// @param canUndo True if the change can still be undone.
    public void setCanUndo(final boolean canUndo) {
        this.canUndo = canUndo;
    }

    /// @return For [Operation#UNDO], true if the previous text was put back.
    public boolean isUndone() {
        return undone;
    }

    /// @param undone True if the previous text was put back.
    public void setUndone(final boolean undone) {
        this.undone = undone;
    }

    /// @return Something to tell the user, or null.
    public String getMessage() {
        return message;
    }


    // --------------------------------------------------------------------------------


    /// What is asked of the stepping screen.
    public enum Operation {
        /// Open the review of a proposed change.
        REVIEW,
        /// Say whether the change last applied can still be undone.
        CAN_UNDO,
        /// Undo the change last applied.
        UNDO
    }


    // --------------------------------------------------------------------------------


    /// Asked before a reviewed change is applied, e.g. to check and record it on the server.
    public interface Approver {

        /// @param andStep  True for "Apply and step".
        /// @param rejected The parts of the change the user rejected, by their index among the diff's changed
        ///                 blocks, which aren't applied. Empty if the whole change is applied.
        /// @param onResult Given null if the change may be applied, or a message saying why it may not.
        void approve(boolean andStep, List<Integer> rejected, Consumer<String> onResult);
    }


    // --------------------------------------------------------------------------------


    /// Handles [SteppingCodeChangeEvent].
    public interface Handler extends EventHandler {

        /// @param event The event.
        void onSteppingCodeChange(SteppingCodeChangeEvent event);
    }
}
