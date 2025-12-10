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
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class ExplorerTaskMonitorFactory implements TaskMonitorFactory, HasHandlers {

    private final HasHandlers hasHandlers;

    public ExplorerTaskMonitorFactory(final HasHandlers hasHandlers) {
        this.hasHandlers = hasHandlers;
    }

    @Override
    public TaskMonitor createTaskMonitor() {
        return new TaskMonitor() {
            @Override
            public void onStart(final Task task) {
                ExplorerStartTaskEvent.fire(ExplorerTaskMonitorFactory.this, task);
            }

            @Override
            public void onEnd(final Task task) {
                ExplorerEndTaskEvent.fire(ExplorerTaskMonitorFactory.this, task);
            }
        };
    }

    @Override
    public void fireEvent(final GwtEvent<?> gwtEvent) {
        hasHandlers.fireEvent(gwtEvent);
    }
}
