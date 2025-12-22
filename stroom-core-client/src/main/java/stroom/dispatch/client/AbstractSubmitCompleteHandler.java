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

package stroom.dispatch.client;

import stroom.task.client.SimpleTask;
import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.PropertyMap;
import stroom.util.shared.ResourceKey;

import com.google.gwt.user.client.ui.FormPanel.SubmitCompleteEvent;
import com.google.gwt.user.client.ui.FormPanel.SubmitCompleteHandler;
import com.google.gwt.user.client.ui.FormPanel.SubmitEvent;
import com.google.gwt.user.client.ui.FormPanel.SubmitHandler;

public abstract class AbstractSubmitCompleteHandler implements SubmitHandler, SubmitCompleteHandler {

    private final TaskMonitorFactory taskMonitorFactory;
    private final Task task;
    private TaskMonitor taskMonitor;

    public AbstractSubmitCompleteHandler(final String taskName,
                                         final TaskMonitorFactory taskMonitorFactory) {
        this.task = new SimpleTask(taskName);
        this.taskMonitorFactory = taskMonitorFactory;
    }

    @Override
    public void onSubmit(final SubmitEvent event) {
        taskMonitor = taskMonitorFactory.createTaskMonitor();
        taskMonitor.onStart(task);
    }

    @Override
    public void onSubmitComplete(final SubmitCompleteEvent event) {
        final String result = event.getResults();
        if (result != null) {
            try {
                final PropertyMap propertyMap = new PropertyMap();
                propertyMap.loadArgLine(result);

                if (propertyMap.isSuccess()) {
                    final ResourceKey resourceKey = new ResourceKey(propertyMap);
                    onSuccess(resourceKey);
                } else {
                    onFailure(propertyMap.get("exception"));
                }
            } catch (final RuntimeException e) {
                onFailure(e.getMessage());
            }
        } else {
            onFailure("Unable to read file");
        }
        taskMonitor.onEnd(task);
    }

    protected abstract void onSuccess(ResourceKey resourceKey);

    protected abstract void onFailure(String message);
}
