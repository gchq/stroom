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

package stroom.query.client.presenter;

import stroom.query.client.presenter.InsertQueryElementEvent.Handler;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class InsertQueryElementEvent extends GwtEvent<Handler> {

    private static Type<Handler> TYPE;

    private final String element;

    private InsertQueryElementEvent(final String element) {
        this.element = element;
    }

    public static void fire(final HasHandlers source, final String element) {
        if (TYPE != null) {
            final InsertQueryElementEvent event = new InsertQueryElementEvent(element);
            source.fireEvent(event);
        }
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
        handler.onInsert(this);
    }

    public String getElement() {
        return element;
    }


    // --------------------------------------------------------------------------------


    public interface Handler extends EventHandler {

        void onInsert(InsertQueryElementEvent event);
    }
}
