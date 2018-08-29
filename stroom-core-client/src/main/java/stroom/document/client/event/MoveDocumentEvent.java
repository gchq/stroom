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

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.gwtplatform.mvp.client.PresenterWidget;
import stroom.explorer.shared.PermissionInheritance;
import stroom.docref.DocRef;

import java.util.List;

public class MoveDocumentEvent extends GwtEvent<MoveDocumentEvent.Handler> {
    private static Type<Handler> TYPE;
    private final PresenterWidget<?> presenter;
    private final List<DocRef> docRefs;
    private final DocRef destinationFolderRef;
    private final PermissionInheritance permissionInheritance;

    private MoveDocumentEvent(final PresenterWidget<?> presenter,
                              final List<DocRef> docRefs,
                              final DocRef destinationFolderRef,
                              final PermissionInheritance permissionInheritance) {
        this.presenter = presenter;
        this.docRefs = docRefs;
        this.destinationFolderRef = destinationFolderRef;
        this.permissionInheritance = permissionInheritance;
    }

    public static void fire(final HasHandlers handlers,
                            final PresenterWidget<?> presenter,
                            final List<DocRef> docRefs,
                            final DocRef destinationFolderRef,
                            final PermissionInheritance permissionInheritance) {
        handlers.fireEvent(new MoveDocumentEvent(presenter, docRefs, destinationFolderRef, permissionInheritance));
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

    public PresenterWidget<?> getPresenter() {
        return presenter;
    }

    public List<DocRef> getDocRefs() {
        return docRefs;
    }

    public DocRef getDestinationFolderRef() {
        return destinationFolderRef;
    }

    public PermissionInheritance getPermissionInheritance() {
        return permissionInheritance;
    }

    public interface Handler extends EventHandler {
        void onMove(final MoveDocumentEvent event);
    }
}