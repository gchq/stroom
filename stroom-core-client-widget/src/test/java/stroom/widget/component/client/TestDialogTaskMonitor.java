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

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestDialogTaskMonitor {

    private final TaskMonitorFactory tab = Mockito.mock(TaskMonitorFactory.class);
    private final TaskMonitorFactory dialog = Mockito.mock(TaskMonitorFactory.class);

    @Test
    void aDialogShownAgainSeesItsOwnersTaskMonitorOnceHidden() {
        // Regression test: once hidden, a dialog shown again (e.g. a dashboard pane's settings) kept
        // an empty task monitor of its own, which hid its tab's, so its requests showed on the app's
        // spinner rather than the tab's
        final ControllerNode owner = ControllerNode.root("tab");
        owner.provide(Contexts.TASK_MONITOR, ContextValue.of(tab));
        final ControllerNode settings = new ControllerNode("settings");
        owner.adopt(settings);
        final List<TaskMonitorFactory> seen = new ArrayList<>();
        settings.watch(Contexts.TASK_MONITOR, seen::add);

        final Runnable takeBack = DialogTaskMonitor.provide(() -> settings, dialog);
        assertThat(settings.lookup(Contexts.TASK_MONITOR)).isSameAs(dialog);
        takeBack.run();
        assertThat(settings.lookup(Contexts.TASK_MONITOR)).isSameAs(tab);

        // Shown and hidden again
        DialogTaskMonitor.provide(() -> settings, dialog).run();
        assertThat(settings.lookup(Contexts.TASK_MONITOR)).isSameAs(tab);
        assertThat(seen).containsExactly(tab, dialog, tab, dialog, tab);
    }

    @Test
    void aControllersOwnTaskMonitorIsKept() {
        final TaskMonitorFactory pager = Mockito.mock(TaskMonitorFactory.class);
        final ControllerNode list = ControllerNode.root("list");
        list.provide(Contexts.TASK_MONITOR, ContextValue.of(pager));

        final Runnable takeBack = DialogTaskMonitor.provide(() -> list, dialog);
        assertThat(list.lookup(Contexts.TASK_MONITOR)).isSameAs(pager);
        takeBack.run();
        assertThat(list.lookup(Contexts.TASK_MONITOR)).isSameAs(pager);
    }

    @Test
    void aProvidedDefaultIsReplacedWhileShown() {
        final ControllerNode node = ControllerNode.root("dialog");
        node.provide(Contexts.TASK_MONITOR, ContextValue.of(Contexts.TASK_MONITOR.getDefaultValue()));

        final Runnable takeBack = DialogTaskMonitor.provide(() -> node, dialog);
        assertThat(node.lookup(Contexts.TASK_MONITOR)).isSameAs(dialog);
        takeBack.run();
        assertThat(node.lookup(Contexts.TASK_MONITOR)).isSameAs(Contexts.TASK_MONITOR.getDefaultValue());
    }

    @Test
    void nothingIsProvidedToADisposedController() {
        final ControllerNode node = ControllerNode.root("gone");
        node.dispose();

        DialogTaskMonitor.provide(() -> node, dialog).run();

        assertThat(node.provided(Contexts.TASK_MONITOR)).isNull();
    }
}
