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

package stroom.core.client.presenter;

import stroom.menubar.client.event.BeforeRevealMenubarEvent;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HandlerContainerImpl;

public abstract class Plugin extends HandlerContainerImpl implements HasHandlers, BeforeRevealMenubarEvent.Handler {

    private final EventBus eventBus;

    public Plugin(final EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(getEventBus().addHandler(BeforeRevealMenubarEvent.getType(), this));
    }

    protected final EventBus getEventBus() {
        return eventBus;
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        getEventBus().fireEventFromSource(event, this);
    }

    @Override
    public void onReveal(final BeforeRevealMenubarEvent event) {
        // Override to act on menu bar reveal.
    }
}
