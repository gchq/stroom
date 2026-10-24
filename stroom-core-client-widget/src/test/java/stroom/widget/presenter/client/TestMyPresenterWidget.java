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
import stroom.widget.component.client.ContextValue;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.ControllerNode;

import com.google.web.bindery.event.shared.Event;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestMyPresenterWidget {

    private final EventBus eventBus = new SimpleEventBus();
    private final List<String> events = new ArrayList<>();

    @Test
    void disposeCallsOnDisposeOnce() {
        final TestPresenter presenter = new TestPresenter("presenter");

        presenter.dispose();
        presenter.dispose();

        assertThat(presenter.isDisposed()).isTrue();
        assertThat(events).containsExactly("presenter disposed");
    }

    @Test
    void disposingByItsOwnerAndDirectlyReleasesItOnce() {
        final TestPresenter presenter = new TestPresenter("presenter");
        final ControllerNode root = ControllerNode.root("tab");
        root.adopt(presenter.getControllerNode());

        root.dispose();
        presenter.dispose();

        assertThat(events).containsExactly("presenter disposed");
    }

    @Test
    void ownedChildrenAreDisposedFirst() {
        final TestPresenter parent = new TestPresenter("parent");
        parent.ownChild(new TestPresenter("child"));

        parent.dispose();

        assertThat(events).containsExactly("child disposed", "parent disposed");
    }

    @Test
    void aDialogNotYetHiddenIsDisposedWithItsOpener() {
        final TestPresenter opener = new TestPresenter("opener");
        final TestPresenter dialog = opener.ownDialog(new TestPresenter("dialog"));

        opener.dispose();

        assertThat(dialog.isDisposed()).isTrue();
        assertThat(events).containsExactly("dialog disposed", "opener disposed");
    }

    @Test
    void aTaskMonitorSetOnThePresenterIsUsed() {
        final TestPresenter presenter = new TestPresenter("presenter");
        final TaskMonitor own = Mockito.mock(TaskMonitor.class);
        final ControllerNode root = ControllerNode.root("tab");
        root.provide(Contexts.TASK_MONITOR, ContextValue.of(() -> Mockito.mock(TaskMonitor.class)));
        root.adopt(presenter.getControllerNode());

        presenter.setTaskMonitorFactory(() -> own);

        assertThat(presenter.createTaskMonitor()).isSameAs(own);
    }

    @Test
    void otherwiseTheContextsTaskMonitorIsUsed() {
        // e.g. its tab's, which its tab provides
        final TestPresenter presenter = new TestPresenter("presenter");
        final TaskMonitor tabs = Mockito.mock(TaskMonitor.class);
        final ControllerNode root = ControllerNode.root("tab");
        root.provide(Contexts.TASK_MONITOR, ContextValue.of(() -> tabs));
        root.adopt(presenter.getControllerNode());

        assertThat(presenter.createTaskMonitor()).isSameAs(tabs);
    }

    @Test
    void otherwiseItsTasksAreAppWide() {
        // Shown by the main screen's spinner, which hears task events on the bus
        final TestPresenter presenter = new TestPresenter("presenter");
        final List<Task> started = new ArrayList<>();
        eventBus.addHandler(TaskStartEvent.getType(), event -> started.add(event.getTask()));
        final Task task = Mockito.mock(Task.class);

        presenter.createTaskMonitor().onStart(task);

        assertThat(started).containsExactly(task);
    }

    @Test
    void owningAMockDoesNothing() {
        // As a test does, giving a presenter a mock of the presenter it owns
        final TestPresenter parent = new TestPresenter("parent");
        final TestPresenter mock = Mockito.mock(TestPresenter.class);

        parent.ownChild(mock);
        parent.dispose();

        assertThat(parent.getControllerNode().childCount()).isZero();
        assertThat(events).containsExactly("parent disposed");
    }

    @Test
    void listenStopsWhenDisposed() {
        final TestPresenter presenter = new TestPresenter("presenter");
        presenter.listenToTest(() -> events.add("heard"));

        eventBus.fireEvent(new TestEvent());
        presenter.dispose();
        eventBus.fireEvent(new TestEvent());

        assertThat(events).containsExactly("heard", "presenter disposed");
    }

    @Test
    void aPresenterCanBeOwnedByAComponentNode() {
        final ControllerNode root = ControllerNode.root("tab");
        final TestPresenter presenter = new TestPresenter("presenter");
        root.adopt(presenter.getControllerNode());

        root.dispose();

        assertThat(presenter.isDisposed()).isTrue();
        assertThat(events).containsExactly("presenter disposed");
    }

    @Test
    void releasingAChildDisposesItAndLeavesTheOthers() {
        // e.g. a task's row, once the task has finished
        final TestPresenter presenter = new TestPresenter("presenter");
        final TestPresenter finished = new TestPresenter("finished");
        final TestPresenter running = new TestPresenter("running");
        presenter.ownChild(finished);
        presenter.ownChild(running);

        presenter.releaseChild(finished);

        assertThat(finished.isDisposed()).isTrue();
        assertThat(running.isDisposed()).isFalse();
        assertThat(presenter.getControllerNode().childCount()).isEqualTo(1);
    }

    // --------------------------------------------------------------------------------

    private class TestPresenter extends MyPresenterWidget<View> {

        private static final Event.Type<Runnable> TYPE = new Event.Type<>();

        private final String name;

        private TestPresenter(final String name) {
            super(eventBus, Mockito.mock(View.class));
            this.name = name;
            onDispose(() -> events.add(name + " disposed"));
        }

        void ownChild(final TestPresenter child) {
            own(child);
        }

        void releaseChild(final TestPresenter child) {
            release(child);
        }

        TestPresenter ownDialog(final TestPresenter dialog) {
            return makeDialog(() -> dialog);
        }

        void listenToTest(final Runnable handler) {
            listen(TYPE, handler);
        }

        void fireTest() {
            getEventBus().fireEventFromSource(new TestEvent(), this);
        }
    }

    private static final class TestEvent extends Event<Runnable> {

        @Override
        public Type<Runnable> getAssociatedType() {
            return TestPresenter.TYPE;
        }

        @Override
        protected void dispatch(final Runnable handler) {
            handler.run();
        }
    }
}
