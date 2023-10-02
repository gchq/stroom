/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.document.client.event;

import stroom.docref.DocRefInfo;
import stroom.explorer.shared.ExplorerNode;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

import java.util.Objects;

public class ShowInfoDocumentDialogEvent extends GwtEvent<ShowInfoDocumentDialogEvent.Handler> {

    private static Type<Handler> TYPE;
    private final ExplorerNode explorerNode;
    private final DocRefInfo info;

    private ShowInfoDocumentDialogEvent(final ExplorerNode explorerNode, final DocRefInfo info) {
        this.explorerNode = Objects.requireNonNull(explorerNode);
        this.info = Objects.requireNonNull(info);
        if (!Objects.equals(explorerNode.getDocRef(), info.getDocRef())) {
            throw new RuntimeException("Different docRefs, "
                    + "node docref: " + explorerNode.getDocRef()
                    + " info docRef: " + info.getDocRef());
        }
    }

    public static void fire(final HasHandlers handlers,
                            final ExplorerNode explorerNode,
                            final DocRefInfo info) {
        handlers.fireEvent(
                new ShowInfoDocumentDialogEvent(explorerNode, info));
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
        handler.onCreate(this);
    }

    public DocRefInfo getInfo() {
        return info;
    }

    public ExplorerNode getExplorerNode() {
        return explorerNode;
    }

    // --------------------------------------------------------------------------------


    public interface Handler extends EventHandler {

        void onCreate(final ShowInfoDocumentDialogEvent event);
    }
}
