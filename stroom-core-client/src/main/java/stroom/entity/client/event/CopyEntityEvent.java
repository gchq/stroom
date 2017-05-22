/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.entity.client.event;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.gwtplatform.mvp.client.PresenterWidget;
import stroom.entity.shared.PermissionInheritance;
import stroom.query.api.v1.DocRef;

public class CopyEntityEvent extends GwtEvent<CopyEntityEvent.Handler> {
    private static Type<Handler> TYPE;
    private final PresenterWidget<?> presenter;
    private final DocRef document;
    private final DocRef folder;
    private final String name;
    private final PermissionInheritance permissionInheritance;

    private CopyEntityEvent(final PresenterWidget<?> presenter, final DocRef document,
                            final DocRef folder, final String name, final PermissionInheritance permissionInheritance) {
        this.presenter = presenter;
        this.document = document;
        this.folder = folder;
        this.name = name;
        this.permissionInheritance = permissionInheritance;
    }

    public static void fire(final HasHandlers handlers, final PresenterWidget<?> presenter,
                            final DocRef document, final DocRef folder, final String name, final PermissionInheritance permissionInheritance) {
        handlers.fireEvent(new CopyEntityEvent(presenter, document, folder, name, permissionInheritance));
    }

    public static Type<Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<Handler>();
        }
        return TYPE;
    }

    @Override
    public final Type<Handler> getAssociatedType() {
        return TYPE;
    }

    @Override
    protected void dispatch(final Handler handler) {
        handler.onCopy(this);
    }

    public PresenterWidget<?> getPresenter() {
        return presenter;
    }

    public DocRef getDocument() {
        return document;
    }

    public DocRef getFolder() {
        return folder;
    }

    public String getName() {
        return name;
    }

    public PermissionInheritance getPermissionInheritance() {
        return permissionInheritance;
    }

    public interface Handler extends EventHandler {
        void onCopy(final CopyEntityEvent event);
    }
}
