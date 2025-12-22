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

package stroom.explorer.client.event;

import stroom.task.client.Task;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class ExplorerEndTaskEvent extends GwtEvent<ExplorerEndTaskEvent.Handler> {

    private static Type<Handler> TYPE;

    private final Task task;

    private ExplorerEndTaskEvent(final Task task) {
        this.task = task;
    }

    static void fire(final HasHandlers handlers, final Task task) {
        handlers.fireEvent(new ExplorerEndTaskEvent(task));
    }

    public static Type<Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public final Type<Handler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final Handler handler) {
        handler.onEnd(this);
    }

    public Task getTask() {
        return task;
    }

    public interface Handler extends EventHandler {

        void onEnd(ExplorerEndTaskEvent event);
    }
}
