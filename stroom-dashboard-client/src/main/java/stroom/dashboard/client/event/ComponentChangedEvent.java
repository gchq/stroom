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

package stroom.dashboard.client.event;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

import java.util.Set;

public class ComponentChangedEvent extends GwtEvent<ComponentChangedEvent.Handler> {
    private static Type<Handler> TYPE;
    private final String componentId;
    private final String streamId;
    private final String eventId;
    private final Set<String> highlights;

    public ComponentChangedEvent(final String componentId, final String streamId, final String eventId,
                                 final Set<String> highlights) {
        this.componentId = componentId;
        this.streamId = streamId;
        this.eventId = eventId;
        this.highlights = highlights;
    }

    public static void fire(final HasHandlers handlers, final String componentId, final String streamId,
                            final String eventId, final Set<String> highlights) {
        handlers.fireEvent(new ComponentChangedEvent(componentId, streamId, eventId, highlights));
    }

    public static Type<Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<Handler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final Handler handler) {
        handler.onChange(this);
    }

    public String getComponentId() {
        return componentId;
    }

    public String getStreamId() {
        return streamId;
    }

    public String getEventId() {
        return eventId;
    }

    public Set<String> getHighlights() {
        return highlights;
    }

    public interface Handler extends EventHandler {
        void onChange(ComponentChangedEvent event);
    }
}
