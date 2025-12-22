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

package stroom.editor.client.event;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;

public class FormatEvent extends GwtEvent<FormatEvent.FormatHandler> {

    public static final GwtEvent.Type<FormatHandler> TYPE = new GwtEvent.Type<>();

    protected FormatEvent() {
    }

    public static <I> void fire(final HasFormatHandlers source) {
        if (TYPE != null) {
            source.fireEvent(new FormatEvent());
        }
    }

    @Override
    public com.google.gwt.event.shared.GwtEvent.Type<FormatHandler> getAssociatedType() {
        return TYPE;
    }

    @Override
    protected void dispatch(final FormatHandler handler) {
        handler.onFormat(this);
    }

    public interface FormatHandler extends EventHandler {

        void onFormat(FormatEvent event);
    }
}
