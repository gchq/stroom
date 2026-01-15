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

package stroom.event.client;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.Event;
import com.google.web.bindery.event.shared.EventBus;

/**
 * Somewhat hacky class to allow us to fire events from composites.
 */
@Singleton
public class StaticEventBus {

    private static EventBus eventBus = null;

    @Inject
    public StaticEventBus(final EventBus eventBus) {
        StaticEventBus.eventBus = eventBus;
    }

    public static EventBus getEventBus() {
        if (eventBus == null) {
            throw new RuntimeException("Static eventBus has not been initialised");
        }
        return eventBus;
    }

    public static <T> void fire(final Event<T> event) {
        getEventBus().fireEvent(event);
    }
}
