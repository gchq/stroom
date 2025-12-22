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

import stroom.query.client.presenter.InsertEditorTextEvent.Handler;
import stroom.query.shared.InsertType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

import java.util.Objects;

public class InsertEditorTextEvent extends GwtEvent<Handler> {

    private static Type<Handler> TYPE;

    private final String text;
    private final InsertType insertType;

    private InsertEditorTextEvent(final String text, final InsertType insertType) {
        this.text = text;
        this.insertType = Objects.requireNonNull(insertType);
    }

    public static void fire(final HasHandlers source,
                            final String text,
                            final InsertType insertType) {
        if (TYPE != null && Objects.requireNonNull(insertType).isInsertable()) {
            final InsertEditorTextEvent event = new InsertEditorTextEvent(text, insertType);
            source.fireEvent(event);
        } else {
            GWT.log("Not firing, insertType: " + insertType + ", text: " + text);
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

    public String getText() {
        return text;
    }

    public InsertType getInsertType() {
        return insertType;
    }

    // --------------------------------------------------------------------------------


    public interface Handler extends EventHandler {

        void onInsert(InsertEditorTextEvent event);
    }
}
