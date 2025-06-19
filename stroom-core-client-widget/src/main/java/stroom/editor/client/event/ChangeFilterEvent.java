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

package stroom.editor.client.event;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class ChangeFilterEvent extends GwtEvent<ChangeFilterEvent.ChangeFilterHandler> {

    public static final GwtEvent.Type<ChangeFilterHandler> TYPE = new GwtEvent.Type<>();

    protected ChangeFilterEvent() {
    }

    public static <I> void fire(final HasHandlers source) {
        if (TYPE != null) {
            source.fireEvent(new ChangeFilterEvent());
        }
    }

    @Override
    public com.google.gwt.event.shared.GwtEvent.Type<ChangeFilterHandler> getAssociatedType() {
        return TYPE;
    }

    @Override
    protected void dispatch(final ChangeFilterHandler handler) {
        handler.onShowFilter(this);
    }

    public interface ChangeFilterHandler extends EventHandler {

        void onShowFilter(ChangeFilterEvent event);
    }
}
