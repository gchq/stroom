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

import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.util.client.PresenterScope;

import com.google.web.bindery.event.shared.Event;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;
import com.gwtplatform.mvp.client.View;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestPresenterAdapter {

    private final EventBus eventBus = new SimpleEventBus();
    private final List<String> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ComponentChecks.setEnabled(true);
        ComponentChecks.reset();
    }

    @AfterEach
    void tearDown() {
        ComponentChecks.reset();
    }

    @Test
    void disposingTheOwnerDisposesThePresenter() {
        final ComponentNode root = ComponentNode.root("story");
        final PresenterAdapter<TestPresenter> adapter = PresenterAdapter.of(() -> new TestPresenter("editor"));
        root.adopt(adapter.getComponentNode());
        adapter.getPresenter().bind();

        root.dispose();

        assertThat(adapter.getPresenter().isBound()).isFalse();
        assertThat(events).containsExactly("editor disposed");
    }

    @Test
    void presentersMadeByThePresenterAreDisposedToo() {
        // GWTP doesn't unbind these when their parent is unbound
        final ComponentNode root = ComponentNode.root("story");
        final PresenterAdapter<ParentPresenter> adapter = PresenterAdapter.of(ParentPresenter::new);
        root.adopt(adapter.getComponentNode());

        root.dispose();

        assertThat(adapter.presenterCount()).isZero();
        assertThat(events).containsExactly("child disposed", "parent disposed");
    }

    @Test
    void thePresenterDoesNotJoinTheScopeCurrentWhenItIsMade() {
        // e.g. the workbench's leak probe opens a story in a scope, which mustn't dispose it again
        final PresenterScope outer = new PresenterScope();
        final PresenterAdapter<TestPresenter> adapter = outer.capture(() ->
                PresenterAdapter.of(() -> new TestPresenter("editor")));
        final ComponentNode root = ComponentNode.root("story");
        root.adopt(adapter.getComponentNode());

        root.dispose();
        outer.dispose();

        assertThat(outer.size()).isZero();
        assertThat(events).containsExactly("editor disposed");
    }

    @Test
    void handlersAddedWithThePresenterAsSourceAreRemoved() {
        final ComponentNode root = ComponentNode.root("story");
        final PresenterAdapter<TestPresenter> adapter = PresenterAdapter.of(() -> new TestPresenter("editor"));
        root.adopt(adapter.getComponentNode());
        final TestPresenter presenter = adapter.getPresenter();
        presenter.addTestHandler(() -> events.add("heard"));

        presenter.fireTest();
        root.dispose();
        presenter.fireTest();

        assertThat(events).containsExactly("heard", "editor disposed");
    }

    @Test
    void readOnlyIsGivenOnceOwnedAndOnEveryChange() {
        final ComponentNode root = ComponentNode.root("story");
        final ContextValue<Boolean> readOnly = ContextValue.of(false);
        root.provide(Contexts.READ_ONLY, readOnly);
        final PresenterAdapter<TestPresenter> adapter = PresenterAdapter.of(() -> new TestPresenter("editor"))
                .onReadOnly(TestPresenter::setReadOnly);
        assertThat(adapter.getPresenter().readOnly).isEmpty();

        root.adopt(adapter.getComponentNode());
        readOnly.set(true);

        assertThat(adapter.getPresenter().readOnly).containsExactly(false, true);
    }

    @Test
    void readOnlyIsTrueWhenNothingProvidesIt() {
        final PresenterAdapter<TestPresenter> adapter = PresenterAdapter.of(() -> new TestPresenter("editor"))
                .onReadOnly(TestPresenter::setReadOnly);

        ComponentNode.root("story").adopt(adapter.getComponentNode());

        assertThat(adapter.getPresenter().readOnly).containsExactly(true);
    }

    @Test
    void aProvidedTaskMonitorIsGivenToThePresenter() {
        final ComponentNode root = ComponentNode.root("story");
        final TaskMonitor taskMonitor = Mockito.mock(TaskMonitor.class);
        final TaskMonitorFactory taskMonitorFactory = () -> taskMonitor;
        root.provide(Contexts.TASK_MONITOR, ContextValue.of(taskMonitorFactory));
        final PresenterAdapter<TestPresenter> adapter = PresenterAdapter.of(() -> new TestPresenter("editor"));

        root.adopt(adapter.getComponentNode());

        assertThat(adapter.getPresenter().createTaskMonitor()).isSameAs(taskMonitor);
    }

    @Test
    void thePresenterKeepsItsOwnTaskMonitorWhenNoneIsProvided() {
        final PresenterAdapter<TestPresenter> adapter = PresenterAdapter.of(() -> new TestPresenter("editor"));
        final TaskMonitor before = adapter.getPresenter().createTaskMonitor();

        ComponentNode.root("story").adopt(adapter.getComponentNode());

        assertThat(adapter.getPresenter().createTaskMonitor().getClass()).isSameAs(before.getClass());
        assertThat(adapter.getPresenter().createTaskMonitor())
                .isNotSameAs(Contexts.TASK_MONITOR.getDefaultValue().createTaskMonitor());
    }

    @Test
    void anAdapterNothingOwnsIsCounted() {
        PresenterAdapter.of(() -> new TestPresenter("editor"));

        assertThat(ComponentChecks.unownedNames()).containsExactly("PresenterAdapter(TestPresenter)");
    }

    // --------------------------------------------------------------------------------


    private class TestPresenter extends MyPresenterWidget<View> {

        private static final Event.Type<Runnable> TYPE = new Event.Type<>();

        private final String name;
        private final List<Boolean> readOnly = new ArrayList<>();

        private TestPresenter(final String name) {
            super(eventBus, Mockito.mock(View.class));
            this.name = name;
        }

        void setReadOnly(final boolean readOnly) {
            this.readOnly.add(readOnly);
        }

        void addTestHandler(final Runnable handler) {
            addHandlerToSource(TYPE, handler);
        }

        void fireTest() {
            getEventBus().fireEventFromSource(new TestEvent(), this);
        }

        @Override
        protected void onDispose() {
            events.add(name + " disposed");
        }
    }

    private class ParentPresenter extends TestPresenter {

        private ParentPresenter() {
            super("parent");
            // As a presenter makes its own presenters, e.g. from GIN providers, while it is made
            new TestPresenter("child");
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
