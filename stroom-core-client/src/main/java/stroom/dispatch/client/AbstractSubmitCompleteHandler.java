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

package stroom.dispatch.client;

import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.user.client.ui.FormPanel.SubmitCompleteEvent;
import com.google.gwt.user.client.ui.FormPanel.SubmitCompleteHandler;
import com.google.gwt.user.client.ui.FormPanel.SubmitEvent;
import com.google.gwt.user.client.ui.FormPanel.SubmitHandler;
import stroom.task.client.TaskEndEvent;
import stroom.task.client.TaskStartEvent;
import stroom.util.shared.PropertyMap;
import stroom.util.shared.ResourceKey;

public abstract class AbstractSubmitCompleteHandler implements SubmitHandler, SubmitCompleteHandler {
    private final String taskName;
    private final HasHandlers parent;

    public AbstractSubmitCompleteHandler(final String taskName, final HasHandlers parent) {
        this.taskName = taskName;
        this.parent = parent;
    }

    @Override
    public void onSubmit(final SubmitEvent event) {
        TaskStartEvent.fire(parent, taskName);
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
            } catch (final Exception e) {
                onFailure(e.getMessage());
            }
        } else {
            onFailure("Unable to read file");
        }
        TaskEndEvent.fire(parent);
    }

    protected abstract void onSuccess(ResourceKey resourceKey);

    protected abstract void onFailure(String message);
}
