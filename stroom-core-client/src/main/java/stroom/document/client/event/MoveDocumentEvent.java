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
import stroom.explorer.shared.PermissionInheritance;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

import java.util.List;

public class MoveDocumentEvent extends GwtEvent<MoveDocumentEvent.Handler> {

    private static Type<Handler> TYPE;
    private final HidePopupRequestEvent hidePopupRequestEvent;
    private final List<ExplorerNode> explorerNodes;
    private final ExplorerNode destinationFolder;
    private final PermissionInheritance permissionInheritance;

    private MoveDocumentEvent(final HidePopupRequestEvent hidePopupRequestEvent,
                              final List<ExplorerNode> explorerNodes,
                              final ExplorerNode destinationFolder,
                              final PermissionInheritance permissionInheritance) {
        this.hidePopupRequestEvent = hidePopupRequestEvent;
        this.explorerNodes = explorerNodes;
        this.destinationFolder = destinationFolder;
        this.permissionInheritance = permissionInheritance;
    }

    public static void fire(final HasHandlers handlers,
                            final HidePopupRequestEvent hidePopupRequestEvent,
                            final List<ExplorerNode> explorerNodes,
                            final ExplorerNode destinationFolder,
                            final PermissionInheritance permissionInheritance) {
        handlers.fireEvent(new MoveDocumentEvent(
                hidePopupRequestEvent,
                explorerNodes,
                destinationFolder,
                permissionInheritance));
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

    public HidePopupRequestEvent getHidePopupRequestEvent() {
        return hidePopupRequestEvent;
    }

    public List<ExplorerNode> getExplorerNodes() {
        return explorerNodes;
    }

    public ExplorerNode getDestinationFolder() {
        return destinationFolder;
    }

    public PermissionInheritance getPermissionInheritance() {
        return permissionInheritance;
    }

    public interface Handler extends EventHandler {

        void onMove(final MoveDocumentEvent event);
    }
}
