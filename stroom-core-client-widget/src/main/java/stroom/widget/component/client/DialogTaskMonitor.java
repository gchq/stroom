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

import stroom.widget.task.client.TaskMonitorFactory;

/// Gives what a dialog shows the dialog's spinner as its task monitor ([Contexts#TASK_MONITOR])
/// while it is shown, so its requests, and those of everything it owns, show on the dialog, and
/// takes it back when the dialog is hidden, so that a controller shown again sees its owner's task
/// monitor (e.g. its tab's) in between.
public final class DialogTaskMonitor {

    private DialogTaskMonitor() {
        // Static utility
    }

    /// Provides a dialog's task monitor to what it shows, unless that provides one of its own (e.g. a
    /// list's pager).
    ///
    /// @param shown  What the dialog shows.
    /// @param dialog The dialog's spinner.
    /// @return What takes it back, once the dialog is hidden.
    public static Runnable provide(final HasControllerNode shown, final TaskMonitorFactory dialog) {
        final ControllerNode node = shown.getControllerNode();
        // Null only for a mock (e.g. Mockito's, in a test)
        if (node == null || node.isDisposed()) {
            return () -> {
            };
        }
        final ContextValue<TaskMonitorFactory> own = node.provided(Contexts.TASK_MONITOR);
        if (own == null) {
            node.setProvided(Contexts.TASK_MONITOR, dialog);
            return () -> node.unprovide(Contexts.TASK_MONITOR);
        } else if (own.get() == Contexts.TASK_MONITOR.getDefaultValue()) {
            // It provides the default (nothing), so the dialog's takes its place while shown
            own.set(dialog);
            return () -> own.set(Contexts.TASK_MONITOR.getDefaultValue());
        }
        return () -> {
        };
    }
}
