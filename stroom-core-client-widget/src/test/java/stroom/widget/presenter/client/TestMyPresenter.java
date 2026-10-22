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

package stroom.widget.presenter.client;

import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskStartEvent;
import stroom.widget.component.client.ComponentNode;
import stroom.widget.component.client.ContextValue;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.HasComponentNode;

import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.proxy.Proxy;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestMyPresenter {

    private final EventBus eventBus = new SimpleEventBus();

    @Test
    void aTaskMonitorSetOnThePresenterIsUsed() {
        final TestPresenter presenter = new TestPresenter(eventBus);
        final TaskMonitor own = Mockito.mock(TaskMonitor.class);
        presenter.setTaskMonitorFactory(() -> own);
        final ComponentNode root = ComponentNode.root("tab");
        root.provide(Contexts.TASK_MONITOR, ContextValue.of(() -> Mockito.mock(TaskMonitor.class)));
        root.adopt(presenter.getComponentNode());

        assertThat(presenter.createTaskMonitor()).isSameAs(own);
    }

    @Test
    void otherwiseTheContextsTaskMonitorIsUsed() {
        // e.g. its tab's, which the tab pane provides, and no longer sets on it
        final TestPresenter presenter = new TestPresenter(eventBus);
        final TaskMonitor tabs = Mockito.mock(TaskMonitor.class);
        final ComponentNode root = ComponentNode.root("tab");
        root.provide(Contexts.TASK_MONITOR, ContextValue.of(() -> tabs));
        root.adopt(presenter.getComponentNode());

        assertThat(presenter.createTaskMonitor()).isSameAs(tabs);
    }

    @Test
    void otherwiseItsTasksAreAppWide() {
        // Shown by the main screen's spinner, which hears task events on the bus
        final TestPresenter presenter = new TestPresenter(eventBus);
        final List<Task> started = new ArrayList<>();
        eventBus.addHandler(TaskStartEvent.getType(), event -> started.add(event.getTask()));
        final Task task = Mockito.mock(Task.class);

        presenter.createTaskMonitor().onStart(task);

        assertThat(started).containsExactly(task);
    }

    @Test
    void releasingAChildDisposesItAndLeavesTheOthers() {
        // e.g. a task's row, once the task has finished
        final TestPresenter presenter = new TestPresenter(eventBus);
        final ComponentNode finished = new ComponentNode("finished");
        final ComponentNode running = new ComponentNode("running");
        presenter.ownChild(() -> finished);
        presenter.ownChild(() -> running);

        presenter.releaseChild(() -> finished);

        assertThat(finished.isDisposed()).isTrue();
        assertThat(running.isDisposed()).isFalse();
        assertThat(presenter.getComponentNode().childCount()).isEqualTo(1);
    }

    // --------------------------------------------------------------------------------


    private static final class TestPresenter extends MyPresenter<View, Proxy<?>> {

        private TestPresenter(final EventBus eventBus) {
            super(eventBus, Mockito.mock(View.class), Mockito.mock(Proxy.class));
        }

        @Override
        protected void revealInParent() {
            // Never revealed
        }

        void ownChild(final HasComponentNode child) {
            own(child);
        }

        void releaseChild(final HasComponentNode child) {
            release(child);
        }
    }
}
