/*
 * Copyright 2017-2024 Crown Copyright
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

import stroom.document.client.DocumentTabData;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class SaveAsDocumentEvent extends GwtEvent<SaveAsDocumentEvent.Handler> {

    private static Type<Handler> TYPE;
    private final DocumentTabData tabData;

    private SaveAsDocumentEvent(final DocumentTabData tabData) {
        this.tabData = tabData;
    }

    public static void fire(final HasHandlers handlers,
                            final DocumentTabData tabData) {
        handlers.fireEvent(new SaveAsDocumentEvent(tabData));
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

    public DocumentTabData getTabData() {
        return tabData;
    }


    // --------------------------------------------------------------------------------


    public interface Handler extends EventHandler {

        void onSaveAs(final SaveAsDocumentEvent event);
    }
}
