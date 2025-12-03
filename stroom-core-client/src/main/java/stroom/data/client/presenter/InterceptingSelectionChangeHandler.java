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

package stroom.data.client.presenter;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerManager;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.view.client.SelectionChangeEvent;
import com.google.gwt.view.client.SelectionChangeEvent.HasSelectionChangedHandlers;

class InterceptingSelectionChangeHandler implements HasSelectionChangedHandlers, SelectionChangeEvent.Handler {

    private final HandlerManager handlerManager = new HandlerManager(this);
    private boolean ignoreNextEvent;

    @Override
    public void onSelectionChange(final SelectionChangeEvent event) {
        if (!ignoreNextEvent) {
            fireEvent(event);
        }
        ignoreNextEvent = false;
    }

    public void setIgnoreNextEvent(final boolean ignoreNextEvent) {
        this.ignoreNextEvent = ignoreNextEvent;
    }

    @Override
    public HandlerRegistration addSelectionChangeHandler(final SelectionChangeEvent.Handler handler) {
        return handlerManager.addHandler(SelectionChangeEvent.getType(), handler);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        handlerManager.fireEvent(event);
    }
}
