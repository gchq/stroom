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
import com.gwtplatform.mvp.client.MyPresenter;
import stroom.docref.DocRef;
import stroom.explorer.shared.PermissionInheritance;

import java.util.function.Consumer;

public class CreateDocumentEvent extends GwtEvent<CreateDocumentEvent.Handler> {
    private static Type<Handler> TYPE;

    private final MyPresenter<?, ?> presenter;
    private final String docType;
    private final String docName;
    private final DocRef destinationFolderRef;
    private final PermissionInheritance permissionInheritance;
    private final Consumer<DocRef> newDocConsumer;

    private CreateDocumentEvent(final MyPresenter<?, ?> presenter,
                                final String docType,
                                final String docName,
                                final DocRef destinationFolderRef,
                                final PermissionInheritance permissionInheritance,
                                final Consumer<DocRef> newDocConsumer) {
        this.presenter = presenter;
        this.docType = docType;
        this.docName = docName;
        this.destinationFolderRef = destinationFolderRef;
        this.permissionInheritance = permissionInheritance;
        this.newDocConsumer = newDocConsumer;
    }

    public static void fire(final HasHandlers handlers,
                            final MyPresenter<?, ?> presenter,
                            final String docType,
                            final String docName,
                            final DocRef destinationFolderRef,
                            final PermissionInheritance permissionInheritance,
                            final Consumer<DocRef> newDocConsumer) {
        handlers.fireEvent(new CreateDocumentEvent(presenter, docType, docName, destinationFolderRef, permissionInheritance, newDocConsumer));
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

    public MyPresenter<?, ?> getPresenter() {
        return presenter;
    }

    public String getDocType() {
        return docType;
    }

    public String getDocName() {
        return docName;
    }

    public DocRef getDestinationFolderRef() {
        return destinationFolderRef;
    }

    public PermissionInheritance getPermissionInheritance() {
        return permissionInheritance;
    }

    public Consumer<DocRef> getNewDocConsumer() {
        return newDocConsumer;
    }

    public interface Handler extends EventHandler {
        void onCreate(final CreateDocumentEvent event);
    }
}
