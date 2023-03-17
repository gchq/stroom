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

import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.PermissionInheritance;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.gwtplatform.mvp.client.PresenterWidget;

import java.util.List;

public class CopyDocumentEvent extends GwtEvent<CopyDocumentEvent.Handler> {

    private static Type<CopyDocumentEvent.Handler> TYPE;
    private final PresenterWidget<?> presenter;
    private final List<ExplorerNode> explorerNodes;
    private final ExplorerNode destinationFolder;
    private final PermissionInheritance permissionInheritance;

    private CopyDocumentEvent(final PresenterWidget<?> presenter,
                              final List<ExplorerNode> explorerNodes,
                              final ExplorerNode destinationFolder,
                              final PermissionInheritance permissionInheritance) {
        this.presenter = presenter;
        this.explorerNodes = explorerNodes;
        this.destinationFolder = destinationFolder;
        this.permissionInheritance = permissionInheritance;
    }

    public static void fire(final HasHandlers handlers,
                            final PresenterWidget<?> presenter,
                            final List<ExplorerNode> explorerNodes,
                            final ExplorerNode destinationFolder,
                            final PermissionInheritance permissionInheritance) {
        handlers.fireEvent(new CopyDocumentEvent(presenter, explorerNodes, destinationFolder, permissionInheritance));
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

    public PresenterWidget<?> getPresenter() {
        return presenter;
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

        void onCopy(final CopyDocumentEvent event);
    }
}
