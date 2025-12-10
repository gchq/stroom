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

package stroom.core.client;

import stroom.core.client.event.WindowCloseEvent;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.Window.Location;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class LocationManager implements HasHandlers {

    private final EventBus eventBus;
    private boolean ignoreClose;

    @Inject
    public LocationManager(final EventBus eventBus,
                           final HasSaveRegistry hasSaveRegistry) {
        this.eventBus = eventBus;
        Window.addWindowClosingHandler(event -> {
            if (!ignoreClose) {
                WindowCloseEvent.fire(this);
                if (hasSaveRegistry.isDirty()) {
                    event.setMessage("Are you sure you want to leave Stroom?");
                }
            } else {
                ignoreClose = false;
            }
        });
    }

    public void replace(final String newURL) {
        ignoreClose = true;
        Location.replace(newURL);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }
}
