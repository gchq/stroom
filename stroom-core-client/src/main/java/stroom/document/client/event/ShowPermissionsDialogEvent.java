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

import stroom.docref.DocRef;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class ShowPermissionsDialogEvent extends GwtEvent<ShowPermissionsDialogEvent.Handler> {

    private static Type<Handler> TYPE;
    private final DocRef docRef;

    private ShowPermissionsDialogEvent(final DocRef docRef) {
        this.docRef = docRef;
    }

    public static void fire(final HasHandlers handlers, final DocRef docRef) {
        handlers.fireEvent(new ShowPermissionsDialogEvent(docRef));
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
        handler.onPermissions(this);
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public interface Handler extends EventHandler {

        void onPermissions(final ShowPermissionsDialogEvent event);
    }
}
