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

package stroom.task.client;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class TaskStartEvent extends GwtEvent<TaskStartEvent.TaskStartHandler> {

    private static Type<TaskStartHandler> TYPE;

    private final Task task;

    private TaskStartEvent(final Task task) {
        this.task = task;
    }

    static void fire(final HasHandlers handlers, final Task task) {
        handlers.fireEvent(new TaskStartEvent(task));
    }

    public static Type<TaskStartHandler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<TaskStartHandler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final TaskStartHandler handler) {
        handler.onTaskStart(this);
    }

    public Task getTask() {
        return task;
    }

    public interface TaskStartHandler extends EventHandler {

        void onTaskStart(TaskStartEvent event);
    }
}
