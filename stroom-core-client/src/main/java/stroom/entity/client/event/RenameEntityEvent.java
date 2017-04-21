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

package stroom.entity.client.event;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.gwtplatform.mvp.client.PresenterWidget;
import stroom.entity.shared.DocRef;

public class RenameEntityEvent extends GwtEvent<RenameEntityEvent.Handler> {
    private static Type<Handler> TYPE;
    private final PresenterWidget<?> dialog;
    private final DocRef document;
    private final String entityName;

    private RenameEntityEvent(final PresenterWidget<?> dialog, final DocRef document,
            final String entityName) {
        this.dialog = dialog;
        this.document = document;
        this.entityName = entityName;
    }

    public static void fire(final HasHandlers handlers, final PresenterWidget<?> dialog,
                            final DocRef document, final String entityName) {
        handlers.fireEvent(new RenameEntityEvent(dialog, document, entityName));
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
        handler.onRename(this);
    }

    public PresenterWidget<?> getDialog() {
        return dialog;
    }

    public DocRef getDocument() {
        return document;
    }

    public String getEntityName() {
        return entityName;
    }

    public interface Handler extends EventHandler {
        void onRename(final RenameEntityEvent event);
    }
}
