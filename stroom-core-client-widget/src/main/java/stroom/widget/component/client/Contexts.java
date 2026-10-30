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

package stroom.widget.component.client;

import stroom.docref.DocRef;
import stroom.widget.task.client.Task;
import stroom.widget.task.client.TaskMonitor;
import stroom.widget.task.client.TaskMonitorFactory;

/// The context keys shared across the UI. A feature may define its own key, but context is only
/// for values that many layers need; a direct child is given what it needs as an argument.
public final class Contexts {

    /// Whether the controllers below may change what they show. Provided by a document, from its
    /// permissions and lock state. It only narrows: a provider below may make it true for its
    /// subtree, never false. It is true when nothing provides it, so a controller with no owner
    /// fails closed rather than editable.
    public static final ContextKey<Boolean> READ_ONLY = ContextKey.narrowing(
            "readOnly",
            Boolean.TRUE,
            (outer, inner) -> Boolean.TRUE.equals(outer) || Boolean.TRUE.equals(inner));

    /// What shows the progress of the requests the controllers below make. Provided by a document
    /// tab or a dialog. It shows nothing when nothing provides it.
    public static final ContextKey<TaskMonitorFactory> TASK_MONITOR = ContextKey.of(
            "taskMonitor",
            new NoTaskMonitorFactory());

    /// The document the controllers below belong to, for requests and links that need it. Provided
    /// by the document's controller. Null when nothing provides it.
    public static final ContextKey<DocRef> DOCUMENT = ContextKey.of("document", null);

    /// What to tell when the user changes something the document holds, so that it can work out
    /// whether it is dirty; see [DirtyTracker]. Provided by a document's controller.
    /// Parts tell it through `markChanged()`, which asks their owner's, so a document part that
    /// is itself a document tells the one above it. It tells nothing when nothing provides it.
    public static final ContextKey<DirtyTracker> DIRTY = ContextKey.of("dirty", DirtyTracker.NONE);

    private Contexts() {
        // Constants only
    }

    private static final class NoTaskMonitorFactory implements TaskMonitorFactory {

        private static final TaskMonitor NO_TASK_MONITOR = new TaskMonitor() {
            @Override
            public void onStart(final Task task) {
                // Shows nothing
            }

            @Override
            public void onEnd(final Task task) {
                // Shows nothing
            }
        };

        @Override
        public TaskMonitor createTaskMonitor() {
            return NO_TASK_MONITOR;
        }
    }
}
