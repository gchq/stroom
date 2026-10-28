/*
 * Copyright 2026 Crown Copyright
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

package stroom.content.client.component;

import stroom.docref.DocRef;
import stroom.document.client.DocumentTabData;
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.LayerController;

import com.google.web.bindery.event.shared.EventBus;

/// The base of a screen shown as one of Stroom's tabs (e.g. Caches, Users, Jobs), opened by a
/// content plugin: it is the tab (its label, icon and type, which a subclass gives) and the tab's
/// content, and it can always be closed. Its requests show their progress on its tab's task monitor
/// ([Contexts#TASK_MONITOR]), or as app-wide tasks before it is in a tab.
public abstract class ContentTabController extends LayerController implements DocumentTabData, TaskMonitorFactory {

    /// @param eventBus The application's event bus.
    protected ContentTabController(final EventBus eventBus) {
        super(eventBus);
    }

    @Override
    public boolean isCloseable() {
        return true;
    }

    @Override
    public DocRef getDocRef() {
        return DocRef.builder().uuid(getType()).name(getType()).type(getType()).build();
    }

    @Override
    public TaskMonitor createTaskMonitor() {
        return taskMonitor().createTaskMonitor();
    }
}
