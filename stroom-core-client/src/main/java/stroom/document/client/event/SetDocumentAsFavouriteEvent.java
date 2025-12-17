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

import stroom.docref.DocRef;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class SetDocumentAsFavouriteEvent extends GwtEvent<SetDocumentAsFavouriteEvent.Handler> {

    private static Type<SetDocumentAsFavouriteEvent.Handler> TYPE;
    private final DocRef docRef;
    private final boolean setFavourite;

    private SetDocumentAsFavouriteEvent(final DocRef docRef,
                                        final boolean setFavourite) {
        this.docRef = docRef;
        this.setFavourite = setFavourite;
    }

    public static void fire(final HasHandlers handlers, final DocRef docRef, final boolean setFavourite) {
        handlers.fireEvent(new SetDocumentAsFavouriteEvent(docRef, setFavourite));
    }

    public static Type<SetDocumentAsFavouriteEvent.Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public final Type<SetDocumentAsFavouriteEvent.Handler> getAssociatedType() {
        return TYPE;
    }

    @Override
    protected void dispatch(final SetDocumentAsFavouriteEvent.Handler handler) {
        handler.onSetFavourite(this);
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public boolean getSetFavourite() {
        return setFavourite;
    }

    public interface Handler extends EventHandler {

        void onSetFavourite(final SetDocumentAsFavouriteEvent event);
    }
}
