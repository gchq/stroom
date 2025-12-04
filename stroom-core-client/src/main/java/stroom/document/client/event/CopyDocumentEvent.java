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

public class CopyDocumentEvent extends GwtEvent<CopyDocumentEvent.Handler> {

    private static Type<CopyDocumentEvent.Handler> TYPE;
    private final HidePopupRequestEvent hidePopupRequestEvent;
    private final List<ExplorerNode> explorerNodes;
    private final ExplorerNode destinationFolder;
    private final boolean allowRename;
    private final String docName;
    private final PermissionInheritance permissionInheritance;

    private CopyDocumentEvent(final HidePopupRequestEvent hidePopupRequestEvent,
                              final List<ExplorerNode> explorerNodes,
                              final ExplorerNode destinationFolder,
                              final boolean allowRename,
                              final String docName,
                              final PermissionInheritance permissionInheritance) {
        this.hidePopupRequestEvent = hidePopupRequestEvent;
        this.explorerNodes = explorerNodes;
        this.destinationFolder = destinationFolder;
        this.allowRename = allowRename;
        this.docName = docName;
        this.permissionInheritance = permissionInheritance;
    }

    public static void fire(final HasHandlers handlers,
                            final HidePopupRequestEvent hidePopupRequestEvent,
                            final List<ExplorerNode> explorerNodes,
                            final ExplorerNode destinationFolder,
                            final boolean allowRename,
                            final String docName,
                            final PermissionInheritance permissionInheritance) {
        handlers.fireEvent(
                new CopyDocumentEvent(
                        hidePopupRequestEvent,
                        explorerNodes,
                        destinationFolder,
                        allowRename,
                        docName,
                        permissionInheritance));
    }

    public static Type<CopyDocumentEvent.Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public final Type<CopyDocumentEvent.Handler> getAssociatedType() {
        return TYPE;
    }

    @Override
    protected void dispatch(final CopyDocumentEvent.Handler handler) {
        handler.onCopy(this);
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

    public boolean isAllowRename() {
        return allowRename;
    }

    public String getDocName() {
        return docName;
    }

    public PermissionInheritance getPermissionInheritance() {
        return permissionInheritance;
    }

    public interface Handler extends EventHandler {

        void onCopy(final CopyDocumentEvent event);
    }
}
