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

package stroom.widget.form.client;

import stroom.task.client.SimpleTask;
import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.PropertyMap;
import stroom.util.shared.ResourceKey;

import java.util.function.Consumer;

/**
 * Handles the result of a file upload: parses the server's response, drives an optional task
 * monitor for the duration of the upload, and dispatches to the configured success/failure
 * consumers. Configured fluently, mirroring the {@code RestFactory} builder.
 */
class FileUploadResultHandler implements FileUploadCallback {

    private Consumer<ResourceKey> successConsumer = resourceKey -> {
    };
    private Consumer<String> failureConsumer = message -> {
    };
    private TaskMonitorFactory taskMonitorFactory;
    private String taskMessage = "Uploading";

    private Task task;
    private TaskMonitor taskMonitor;

    public FileUploadResultHandler onSuccess(final Consumer<ResourceKey> successConsumer) {
        this.successConsumer = successConsumer;
        return this;
    }

    public FileUploadResultHandler onFailure(final Consumer<String> failureConsumer) {
        this.failureConsumer = failureConsumer;
        return this;
    }

    public FileUploadResultHandler taskMonitorFactory(final TaskMonitorFactory taskMonitorFactory) {
        this.taskMonitorFactory = taskMonitorFactory;
        return this;
    }

    public FileUploadResultHandler taskMonitorFactory(final TaskMonitorFactory taskMonitorFactory,
                                                      final String taskMessage) {
        this.taskMonitorFactory = taskMonitorFactory;
        this.taskMessage = taskMessage;
        return this;
    }

    @Override
    public void onUploadStart() {
        if (taskMonitorFactory != null) {
            task = new SimpleTask(taskMessage);
            taskMonitor = taskMonitorFactory.createTaskMonitor();
            taskMonitor.onStart(task);
        }
    }

    @Override
    public void onUploadSuccess(final String result) {
        try {
            if (result != null) {
                final PropertyMap propertyMap = new PropertyMap();
                propertyMap.loadArgLine(result);

                if (propertyMap.isSuccess()) {
                    successConsumer.accept(new ResourceKey(propertyMap));
                } else {
                    failureConsumer.accept(propertyMap.get("exception"));
                }
            } else {
                failureConsumer.accept("Unable to read file");
            }
        } catch (final RuntimeException e) {
            failureConsumer.accept(e.getMessage());
        } finally {
            endTask();
        }
    }

    @Override
    public void onUploadFailure(final String message) {
        try {
            failureConsumer.accept(message);
        } finally {
            endTask();
        }
    }

    private void endTask() {
        if (taskMonitor != null) {
            taskMonitor.onEnd(task);
        }
    }
}
