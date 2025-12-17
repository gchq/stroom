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

package stroom.data.client.event;

import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;

import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.web.bindery.event.shared.HandlerRegistration;

public interface HasDataSelectionHandlers<I> extends HasHandlers {

    /**
     * Adds a {@link SelectionEvent} handler.
     *
     * @param handler the handler
     * @return the registration for the event
     */
    HandlerRegistration addDataSelectionHandler(DataSelectionHandler<I> handler);
}
