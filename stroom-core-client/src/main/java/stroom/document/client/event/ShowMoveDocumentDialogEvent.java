/*
 * Copyright 2016 Crown Copyright
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

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

import java.util.List;

public class ShowMoveDocumentDialogEvent extends GwtEvent<ShowMoveDocumentDialogEvent.Handler> {

    private static Type<Handler> TYPE;
    private final List<ExplorerNode> explorerNodeList;
    private final ExplorerNode destinationFolder;

    private ShowMoveDocumentDialogEvent(final List<ExplorerNode> explorerNodeList,
                                        final ExplorerNode destinationFolder) {
        this.explorerNodeList = explorerNodeList;
        this.destinationFolder = destinationFolder;
    }

    public static void fire(final HasHandlers handlers, final List<ExplorerNode> explorerNodeList) {
        handlers.fireEvent(new ShowMoveDocumentDialogEvent(explorerNodeList, null));
    }

    /// Shows the move dialog with `destinationFolder` already chosen, e.g. after a drag and drop.
    ///
    /// @param handlers          The source of the event.
    /// @param explorerNodeList  The nodes to move.
    /// @param destinationFolder The folder to initially select as the destination.
    public static void fire(final HasHandlers handlers,
                            final List<ExplorerNode> explorerNodeList,
                            final ExplorerNode destinationFolder) {
        handlers.fireEvent(new ShowMoveDocumentDialogEvent(explorerNodeList, destinationFolder));
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
        handler.onMove(this);
    }

    public List<ExplorerNode> getExplorerNodeList() {
        return explorerNodeList;
    }

    /// @return The folder to initially select as the destination, or null to default to the parent
    /// of the first node.
    public ExplorerNode getDestinationFolder() {
        return destinationFolder;
    }

    public interface Handler extends EventHandler {

        void onMove(final ShowMoveDocumentDialogEvent event);
    }
}
