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
import stroom.widget.popup.client.event.HidePopupEvent;

import com.google.web.bindery.event.shared.Event;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class TestMyPresenterWidget {

    private static final Object SLOT = new Object();
    private static final Object OTHER_SLOT = new Object();

    private final EventBus eventBus = new SimpleEventBus();
    private final List<String> events = new ArrayList<>();

    @AfterEach
    void showNoPage() {
        RootPresenter.setRootContent(null);
    }

    @Test
    void disposeUnbindsAndCallsOnDisposeOnce() {
        final TestPresenter presenter = new TestPresenter("presenter");
        presenter.bind();

        presenter.dispose();
        presenter.dispose();

        assertThat(presenter.isBound()).isFalse();
        assertThat(presenter.isDisposed()).isTrue();
        assertThat(events).containsExactly("presenter disposed");
    }

    @Test
    void disposingByItsOwnerAndDirectlyReleasesItOnce() {
        final TestPresenter presenter = new TestPresenter("presenter");
        final ComponentNode root = ComponentNode.root("tab");
        root.adopt(presenter.getComponentNode());

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
    void aDialogOwnedUntilHiddenIsDisposedOnceHidden() {
        final Consumer<Runnable> scheduler = MyPresenterWidget.deferred;
        final List<Runnable> deferred = new ArrayList<>();
        MyPresenterWidget.deferred = deferred::add;
        try {
            final TestPresenter opener = new TestPresenter("opener");
            final TestPresenter dialog = opener.ownDialog(new TestPresenter("dialog"));
            assertThat(dialog.getComponentNode().parent()).isSameAs(opener.getComponentNode());

            HidePopupEvent.builder(dialog).fire();
            // Not until the popup manager has hidden it
            assertThat(dialog.isDisposed()).isFalse();
            deferred.forEach(Runnable::run);

            assertThat(dialog.isDisposed()).isTrue();
            assertThat(opener.isDisposed()).isFalse();
            assertThat(opener.getComponentNode().childCount()).isZero();
            assertThat(events).containsExactly("dialog disposed");
        } finally {
            MyPresenterWidget.deferred = scheduler;
        }
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
        final ComponentNode root = ComponentNode.root("tab");
        root.provide(Contexts.TASK_MONITOR, ContextValue.of(() -> Mockito.mock(TaskMonitor.class)));
        root.adopt(presenter.getComponentNode());

        presenter.setTaskMonitorFactory(() -> own);

        assertThat(presenter.createTaskMonitor()).isSameAs(own);
    }

    @Test
    void otherwiseTheContextsTaskMonitorIsUsed() {
        // e.g. its tab's, which its tab provides
        final TestPresenter presenter = new TestPresenter("presenter");
        final TaskMonitor tabs = Mockito.mock(TaskMonitor.class);
        final ComponentNode root = ComponentNode.root("tab");
        root.provide(Contexts.TASK_MONITOR, ContextValue.of(() -> tabs));
        root.adopt(presenter.getComponentNode());

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

        assertThat(parent.getComponentNode().childCount()).isZero();
        assertThat(events).containsExactly("parent disposed");
    }

    @Test
    void listenStopsWhenDisposedNotWhenUnbound() {
        final TestPresenter presenter = new TestPresenter("presenter");
        presenter.listenToTest(() -> events.add("heard"));
        presenter.bind();

        presenter.unbind();
        eventBus.fireEvent(new TestEvent());
        presenter.dispose();
        eventBus.fireEvent(new TestEvent());

        assertThat(events).containsExactly("heard", "presenter disposed");
    }

    @Test
    void aPresenterCanBeOwnedByAComponentNode() {
        final ComponentNode root = ComponentNode.root("tab");
        final TestPresenter presenter = new TestPresenter("presenter");
        root.adopt(presenter.getComponentNode());

        root.dispose();

        assertThat(presenter.isDisposed()).isTrue();
        assertThat(events).containsExactly("presenter disposed");
    }

    @Test
    void aPresenterInASlotOfThePageIsRevealed() {
        final TestPresenter page = new TestPresenter("page");
        final TestPresenter content = new TestPresenter("content");
        final TestPresenter tab = new TestPresenter("tab");
        RootPresenter.setRootContent(page);

        page.setInSlot(SLOT, content);
        content.addToSlot(OTHER_SLOT, tab);

        assertThat(page.isRevealed()).isTrue();
        assertThat(content.isRevealed()).isTrue();
        assertThat(tab.isRevealed()).isTrue();
    }

    @Test
    void aPresenterInNoSlotOrUnderOneNotOnThePageIsNotRevealed() {
        // e.g. a tab's content or a dialog, which Stroom shows outside slots
        final TestPresenter parent = new TestPresenter("parent");
        final TestPresenter child = new TestPresenter("child");

        parent.setInSlot(SLOT, child);

        assertThat(parent.isRevealed()).isFalse();
        assertThat(child.isRevealed()).isFalse();
    }

    @Test
    void replacingASlotsContentTakesTheOldOut() {
        final TestPresenter page = new TestPresenter("page");
        final TestPresenter first = new TestPresenter("first");
        final TestPresenter second = new TestPresenter("second");
        RootPresenter.setRootContent(page);
        page.setInSlot(SLOT, first);

        page.setInSlot(SLOT, second);

        assertThat(first.isRevealed()).isFalse();
        assertThat(second.isRevealed()).isTrue();
    }

    @Test
    void emptyingOrRemovingFromASlotTakesItsContentOut() {
        final TestPresenter page = new TestPresenter("page");
        final TestPresenter single = new TestPresenter("single");
        final TestPresenter cleared = new TestPresenter("cleared");
        final TestPresenter removed = new TestPresenter("removed");
        final TestPresenter kept = new TestPresenter("kept");
        RootPresenter.setRootContent(page);
        page.setInSlot(SLOT, single);
        page.addToSlot(OTHER_SLOT, removed);
        page.addToSlot(OTHER_SLOT, kept);

        page.setInSlot(SLOT, null);
        page.removeFromSlot(OTHER_SLOT, removed);

        assertThat(single.isRevealed()).isFalse();
        assertThat(removed.isRevealed()).isFalse();
        assertThat(kept.isRevealed()).isTrue();

        page.addToSlot(SLOT, cleared);
        page.clearSlot(SLOT);

        assertThat(cleared.isRevealed()).isFalse();
    }

    @Test
    void puttingAPresenterInAnotherSlotTakesItOutOfTheFirst() {
        final TestPresenter page = new TestPresenter("page");
        final TestPresenter elsewhere = new TestPresenter("elsewhere");
        final TestPresenter moved = new TestPresenter("moved");
        RootPresenter.setRootContent(page);
        page.addToSlot(SLOT, moved);

        elsewhere.setInSlot(SLOT, moved);

        assertThat(moved.isRevealed()).isFalse();

        // Back on the page: emptying the other presenter's slot then leaves it where it is
        page.addToSlot(SLOT, moved);
        elsewhere.clearSlot(SLOT);

        assertThat(moved.isRevealed()).isTrue();
    }

    // --------------------------------------------------------------------------------


    private class TestPresenter extends MyPresenterWidget<View> {

        private static final Event.Type<Runnable> TYPE = new Event.Type<>();

        private final String name;

        private TestPresenter(final String name) {
            super(eventBus, Mockito.mock(View.class));
            this.name = name;
        }

        void ownChild(final TestPresenter child) {
            own(child);
        }

        TestPresenter ownDialog(final TestPresenter dialog) {
            return ownUntilHidden(dialog);
        }

        void listenToTest(final Runnable handler) {
            listen(TYPE, handler);
        }

        void fireTest() {
            getEventBus().fireEventFromSource(new TestEvent(), this);
        }

        @Override
        protected void onDispose() {
            events.add(name + " disposed");
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
