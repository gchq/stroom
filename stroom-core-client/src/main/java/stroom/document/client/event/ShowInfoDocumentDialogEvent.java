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

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerNodeInfo;
import stroom.util.shared.NullSafe;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

import java.util.Objects;

public class ShowInfoDocumentDialogEvent extends GwtEvent<ShowInfoDocumentDialogEvent.Handler> {

    private static Type<Handler> TYPE;
    private final ExplorerNodeInfo explorerNodeInfo;

    private ShowInfoDocumentDialogEvent(final ExplorerNodeInfo explorerNodeInfo) {
        this.explorerNodeInfo = Objects.requireNonNull(explorerNodeInfo);
    }

    public static void fire(final HasHandlers handlers,
                            final ExplorerNodeInfo explorerNodeInfo) {
        handlers.fireEvent(
                new ShowInfoDocumentDialogEvent(explorerNodeInfo));
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

    public DocRef getDocRef() {
        return NullSafe.get(explorerNodeInfo, ExplorerNodeInfo::getExplorerNode, ExplorerNode::getDocRef);
    }

    public DocRefInfo getDocRefInfo() {
        return explorerNodeInfo.getDocRefInfo();
    }

    public ExplorerNode getExplorerNode() {
        return explorerNodeInfo.getExplorerNode();
    }

    public ExplorerNodeInfo getExplorerNodeInfo() {
        return explorerNodeInfo;
    }


    // --------------------------------------------------------------------------------


    public interface Handler extends EventHandler {

        void onCreate(final ShowInfoDocumentDialogEvent event);
    }
}
