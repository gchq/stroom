/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.document.client.event;

import stroom.explorer.shared.ExplorerNode;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class RenameDocumentEvent extends GwtEvent<RenameDocumentEvent.Handler> {

    private static Type<Handler> TYPE;
    private final HidePopupRequestEvent hidePopupRequestEvent;
    private final ExplorerNode explorerNode;
    private final String docName;

    private RenameDocumentEvent(final HidePopupRequestEvent hidePopupRequestEvent,
                                final ExplorerNode explorerNode,
                                final String docName) {
        this.hidePopupRequestEvent = hidePopupRequestEvent;
        this.explorerNode = explorerNode;
        this.docName = docName;
    }

    public static void fire(final HasHandlers handlers,
                            final HidePopupRequestEvent hidePopupRequestEvent,
                            final ExplorerNode docRef,
                            final String docName) {
        handlers.fireEvent(new RenameDocumentEvent(hidePopupRequestEvent, docRef, docName));
    }

    public static Type<Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public final Type<Handler> getAssociatedType() {
        return TYPE;
    }

    @Override
    protected void dispatch(final Handler handler) {
        handler.onRename(this);
    }

    public HidePopupRequestEvent getHidePopupRequestEvent() {
        return hidePopupRequestEvent;
    }

    public ExplorerNode getExplorerNode() {
        return explorerNode;
    }

    public String getDocName() {
        return docName;
    }


    // --------------------------------------------------------------------------------


    public interface Handler extends EventHandler {

        void onRename(final RenameDocumentEvent event);
    }
}
