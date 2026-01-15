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

package stroom.app.client.gin;

import com.google.web.bindery.event.shared.Event;
import com.google.web.bindery.event.shared.Event.Type;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.google.web.bindery.event.shared.SimpleEventBus;

import java.util.ArrayList;
import java.util.List;

public class RecordingEventBus extends EventBus {

    private final List<Event<?>> firedEvents = new ArrayList<>();
    private final List<Event<?>> firedSourceEvents = new ArrayList<>();
    private final EventBus wrapped;

    public RecordingEventBus() {
        this(new SimpleEventBus());
    }

    public RecordingEventBus(final EventBus wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public <H> HandlerRegistration addHandler(final Type<H> type, final H handler) {
        return wrapped.addHandler(type, handler);
    }

    @Override
    public <H> HandlerRegistration addHandlerToSource(final Type<H> type, final Object source, final H handler) {
        return wrapped.addHandlerToSource(type, source, handler);
    }

    /**
     * Clears the remembered list of fired events.
     */
    public void clearFiredEvents() {
        firedEvents.clear();
        firedSourceEvents.clear();
    }

    @Override
    public void fireEvent(final Event<?> event) {
        wrapped.fireEvent(event);
        firedEvents.add(event);
    }

    @Override
    public void fireEventFromSource(final Event<?> event, final Object source) {
        firedSourceEvents.add(event);
        wrapped.fireEventFromSource(event, source);
    }
}
