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

package stroom.processor.task.client.event;

import stroom.processor.shared.ProcessorFilter;
import stroom.processor.task.client.event.OpenProcessorTaskEvent.Handler;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

import java.util.Objects;

public class OpenProcessorTaskEvent extends GwtEvent<Handler> {

    private static Type<Handler> TYPE;
    private final ProcessorFilter processorFilter;

    private OpenProcessorTaskEvent(final ProcessorFilter processorFilter) {
        this.processorFilter = Objects.requireNonNull(processorFilter);
    }

    public static void fire(final HasHandlers handlers, final ProcessorFilter processorFilter) {
        handlers.fireEvent(new OpenProcessorTaskEvent(
                Objects.requireNonNull(processorFilter, "Processor filter required")));
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
        handler.onOpen(this);
    }

    public ProcessorFilter getProcessorFilter() {
        return processorFilter;
    }

    @Override
    public String toString() {
        return "OpenProcessorTaskEvent{" +
               "processorFilter='" + processorFilter + '\'' +
               '}';
    }

    // --------------------------------------------------------------------------------


    public interface Handler extends EventHandler {

        void onOpen(OpenProcessorTaskEvent event);
    }
}
