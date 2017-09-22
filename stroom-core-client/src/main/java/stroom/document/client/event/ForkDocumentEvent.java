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
import stroom.document.client.DocumentTabData;
import stroom.entity.shared.PermissionInheritance;
import stroom.query.api.v2.DocRef;

public class ForkDocumentEvent extends GwtEvent<ForkDocumentEvent.Handler> {
    private static Type<Handler> TYPE;
    private final PresenterWidget<?> dialog;
    private final DocumentTabData tabData;
    private final String docName;
    private final DocRef destinationFolderRef;
    private final PermissionInheritance permissionInheritance;

    private ForkDocumentEvent(final PresenterWidget<?> dialog, final DocumentTabData tabData, final String docName, final DocRef destinationFolderRef, final PermissionInheritance permissionInheritance) {
        this.dialog = dialog;
        this.tabData = tabData;
        this.docName = docName;
        this.destinationFolderRef = destinationFolderRef;
        this.permissionInheritance = permissionInheritance;
    }

    public static void fire(final HasHandlers handlers,
                            final PresenterWidget<?> dialog,
                            final DocumentTabData tabData,
                            final String docName,
                            final DocRef destinationFolderRef,
                            final PermissionInheritance permissionInheritance) {
        handlers.fireEvent(new ForkDocumentEvent(dialog, tabData, docName, destinationFolderRef, permissionInheritance));
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
        handler.onSaveAs(this);
    }

    public PresenterWidget<?> getDialog() {
        return dialog;
    }

    public DocumentTabData getTabData() {
        return tabData;
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

    public interface Handler extends EventHandler {
        void onSaveAs(final ForkDocumentEvent event);
    }
}
