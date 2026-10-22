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

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Widgets need a browser, so these tests cover what a component does without one
class TestComponent {

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
    void anOwnedComponentIsDisposedWithItsOwner() {
        final ComponentNode root = ComponentNode.root("story");
        final Parent parent = new Parent(null);
        root.adopt(parent.getComponentNode());

        root.dispose();

        assertThat(parent.isDisposed()).isTrue();
        assertThat(parent.child.isDisposed()).isTrue();
        assertThat(events).containsExactly("child disposed", "parent disposed");
    }

    @Test
    void aComponentIsUnownedUntilOwned() {
        final Child child = new Child();

        assertThat(ComponentChecks.unownedNames()).containsExactly("Child");

        ComponentNode.root("story").adopt(child.getComponentNode());

        assertThat(ComponentChecks.unownedCount()).isZero();
    }

    @Test
    void aWatchInTheConstructorReportsOnceOwned() {
        final ComponentNode root = ComponentNode.root("story");
        final ContextValue<Boolean> readOnly = ContextValue.of(false);
        root.provide(Contexts.READ_ONLY, readOnly);
        final Parent parent = new Parent(null);
        assertThat(parent.child.readOnly).isEmpty();

        root.adopt(parent.getComponentNode());
        readOnly.set(true);

        assertThat(parent.child.readOnly).containsExactly(false, true);
    }

    @Test
    void releaseDisposesAChildEarly() {
        final Parent parent = new Parent(null);
        ComponentNode.root("story").adopt(parent.getComponentNode());

        parent.replaceChild();

        assertThat(events).containsExactly("child disposed");
        assertThat(parent.isDisposed()).isFalse();
    }

    @Test
    void listenStopsListeningWhenDisposed() {
        final EventBus eventBus = new SimpleEventBus();
        final Parent parent = new Parent(eventBus);
        ComponentNode.root("story").adopt(parent.getComponentNode());

        eventBus.fireEvent(new TestEvent("first"));
        parent.dispose();
        eventBus.fireEvent(new TestEvent("second"));

        assertThat(events).containsExactly("heard first", "child disposed", "parent disposed");
    }

    @Test
    void listenWithoutAnEventBusFails() {
        final Child child = new Child();

        assertThatThrownBy(() -> child.listen(TestEvent.TYPE, event -> {
        })).hasMessageContaining("without an event bus");
    }

    @Test
    void itFiresAppWideRequestsFromItself() {
        final EventBus eventBus = new SimpleEventBus();
        final Parent parent = new Parent(eventBus);
        final List<Object> sources = new ArrayList<>();
        eventBus.addHandler(TestEvent.TYPE, event -> sources.add(event.getSource()));

        parent.fireEvent(new TestEvent("alert"));

        assertThat(sources).containsExactly(parent);
    }

    @Test
    void firingWithoutAnEventBusFails() {
        final Child child = new Child();

        assertThatThrownBy(() -> child.fireEvent(new TestEvent("alert")))
                .hasMessageContaining("without an event bus");
    }

    @Test
    void guardDropsLateResults() {
        final Parent parent = new Parent(null);
        ComponentNode.root("story").adopt(parent.getComponentNode());
        final Consumer<String> result = parent.guard(events::add);

        parent.dispose();
        result.accept("late");

        assertThat(events).doesNotContain("late");
    }

    @Test
    void aWidgetIsNeededToShowIt() {
        assertThatThrownBy(() -> new Child().asWidget())
                .hasMessageContaining("Child has no widget");
    }

    @Test
    void ownedRegistrationsAreRemovedOnDispose() {
        final Parent parent = new Parent(null);
        final HandlerRegistration registration = Mockito.mock(HandlerRegistration.class);
        parent.own(registration);

        parent.dispose();

        Mockito.verify(registration).removeHandler();
    }

    private class Parent extends Component {

        private Child child;

        Parent(final EventBus eventBus) {
            super(eventBus);
            child = own(new Child());
            onDispose(() -> events.add("parent disposed"));
            if (eventBus != null) {
                listen(TestEvent.TYPE, event -> events.add("heard " + event.name));
            }
        }

        void replaceChild() {
            release(child);
            child = own(new Child());
        }
    }

    private class Child extends Component {

        private final List<Boolean> readOnly = new ArrayList<>();

        Child() {
            watch(Contexts.READ_ONLY, readOnly::add);
            onDispose(() -> events.add("child disposed"));
        }
    }

    private interface TestHandler extends EventHandler {

        void onTest(TestEvent event);
    }

    private static final class TestEvent extends GwtEvent<TestHandler> {

        private static final Type<TestHandler> TYPE = new Type<>();

        private final String name;

        private TestEvent(final String name) {
            this.name = name;
        }

        @Override
        public Type<TestHandler> getAssociatedType() {
            return TYPE;
        }

        @Override
        protected void dispatch(final TestHandler handler) {
            handler.onTest(this);
        }
    }
}
