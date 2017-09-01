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

package stroom.task.client;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class TaskEndEvent extends GwtEvent<TaskEndEvent.TaskEndHandler> {
    private static Type<TaskEndHandler> TYPE;
    private final int taskCount;

    private TaskEndEvent(final int taskCount) {
        this.taskCount = taskCount;
    }

    public static void fire(final HasHandlers handlers) {
        final int count = TaskCount.decrement();
        handlers.fireEvent(new TaskEndEvent(count));
    }

    public static Type<TaskEndHandler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<TaskEndHandler> getAssociatedType() {
        return TYPE;
    }

    @Override
    protected void dispatch(final TaskEndHandler handler) {
        handler.onTaskEnd(this);
    }

    public int getTaskCount() {
        return taskCount;
    }

    public interface TaskEndHandler extends EventHandler {
        void onTaskEnd(TaskEndEvent event);
    }
}
