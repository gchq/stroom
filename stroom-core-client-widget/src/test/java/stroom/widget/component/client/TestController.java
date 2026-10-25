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

import stroom.task.client.TaskMonitorFactory;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.inject.Provider;
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

// Widgets need a browser, so these tests cover what a controller does without one
class TestController {

    private final List<String> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ControllerChecks.setEnabled(true);
        ControllerChecks.reset();
    }

    @AfterEach
    void tearDown() {
        ControllerChecks.reset();
    }

    @Test
    void anOwnedComponentIsDisposedWithItsOwner() {
        final ControllerNode root = ControllerNode.root("story");
        final Parent parent = new Parent(null);
        root.adopt(parent.getControllerNode());

        root.dispose();

        assertThat(parent.isDisposed()).isTrue();
        assertThat(parent.child.isDisposed()).isTrue();
        assertThat(events).containsExactly("child disposed", "parent disposed");
    }

    @Test
    void aComponentIsUnownedUntilOwned() {
        final Child child = new Child();

        assertThat(ControllerChecks.unownedNames()).containsExactly("Child");

        ControllerNode.root("story").adopt(child.getControllerNode());

        assertThat(ControllerChecks.unownedCount()).isZero();
    }

    @Test
    void theProvidedTaskMonitorIsNullWhenNothingProvidesOne() {
        // Regression test: controllers passed the context's default task monitor, which shows
        // nothing, to clients that take one, so a request made before a dialog was shown (the
        // schedule dialog's times) showed no progress, rather than Stroom's own busy indicator
        final Requester unowned = new Requester();
        assertThat(unowned.getProvidedTaskMonitor()).isNull();

        final Requester ownedWithoutOne = new Requester();
        ControllerNode.root("story").adopt(ownedWithoutOne.getControllerNode());
        assertThat(ownedWithoutOne.getProvidedTaskMonitor()).isNull();

        final TaskMonitorFactory provided = Mockito.mock(TaskMonitorFactory.class);
        final ControllerNode root = ControllerNode.root("dialog");
        root.provide(Contexts.TASK_MONITOR, ContextValue.of(provided));
        final Requester owned = new Requester();
        root.adopt(owned.getControllerNode());
        assertThat(owned.getProvidedTaskMonitor()).isSameAs(provided);
    }

    @Test
    void aWatchInTheConstructorReportsOnceOwned() {
        final ControllerNode root = ControllerNode.root("story");
        final ContextValue<Boolean> readOnly = ContextValue.of(false);
        root.provide(Contexts.READ_ONLY, readOnly);
        final Parent parent = new Parent(null);
        assertThat(parent.child.readOnly).isEmpty();

        root.adopt(parent.getControllerNode());
        readOnly.set(true);

        assertThat(parent.child.readOnly).containsExactly(false, true);
    }

    @Test
    void releaseDisposesAChildEarly() {
        final Parent parent = new Parent(null);
        ControllerNode.root("story").adopt(parent.getControllerNode());

        parent.replaceChild();

        assertThat(events).containsExactly("child disposed");
        assertThat(parent.isDisposed()).isFalse();
    }

    @Test
    void listenStopsListeningWhenDisposed() {
        final EventBus eventBus = new SimpleEventBus();
        final Parent parent = new Parent(eventBus);
        ControllerNode.root("story").adopt(parent.getControllerNode());

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
        ControllerNode.root("story").adopt(parent.getControllerNode());
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
    void aMadeComponentIsOwnedAndDisposedWithItsMaker() {
        final Parent parent = new Parent(null);

        final Child made = parent.makeChild(Child::new);

        assertThat(parent.getControllerNode().childCount()).isEqualTo(2);
        parent.dispose();
        assertThat(made.isDisposed()).isTrue();
    }

    @Test
    void makingFromANullProviderFails() {
        final Parent parent = new Parent(null);

        assertThatThrownBy(() -> parent.makeChild(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("provider");
    }

    @Test
    void aDialogIsOwnedUntilHiddenThenDisposed() {
        final Consumer<Runnable> scheduler = ControllerDialog.deferred;
        final List<Runnable> deferred = new ArrayList<>();
        ControllerDialog.deferred = deferred::add;
        try {
            final EventBus eventBus = new SimpleEventBus();
            final Parent opener = new Parent(null);
            final MyPresenterWidget<View> dialog = opener.makeDialogChild(() ->
                    new MyPresenterWidget<>(eventBus, Mockito.mock(View.class)));
            assertThat(dialog.getControllerNode().parent()).isSameAs(opener.getControllerNode());

            HidePopupEvent.builder(dialog).fire();
            // Not until the popup manager has hidden it
            assertThat(dialog.isDisposed()).isFalse();
            deferred.forEach(Runnable::run);

            assertThat(dialog.isDisposed()).isTrue();
            assertThat(opener.isDisposed()).isFalse();
            // Only the child it made itself is left
            assertThat(opener.getControllerNode().childCount()).isEqualTo(1);
        } finally {
            ControllerDialog.deferred = scheduler;
        }
    }

    @Test
    void ownedRegistrationsAreRemovedOnDispose() {
        final Parent parent = new Parent(null);
        final HandlerRegistration registration = Mockito.mock(HandlerRegistration.class);
        parent.own(registration);

        parent.dispose();

        Mockito.verify(registration).removeHandler();
    }

    private class Parent extends Controller {

        private Child child;

        Parent(final EventBus eventBus) {
            super(eventBus);
            child = own(new Child());
            onDispose(() -> events.add("parent disposed"));
            if (eventBus != null) {
                listen(TestEvent.TYPE, event -> events.add("heard " + event.name));
            }
        }

        Child makeChild(final Provider<Child> provider) {
            return make(provider);
        }

        MyPresenterWidget<View> makeDialogChild(final Provider<MyPresenterWidget<View>> provider) {
            return makeDialog(provider);
        }

        void replaceChild() {
            release(child);
            child = own(new Child());
        }
    }

    private class Child extends Controller {

        private final List<Boolean> readOnly = new ArrayList<>();

        Child() {
            watch(Contexts.READ_ONLY, readOnly::add);
            onDispose(() -> events.add("child disposed"));
        }
    }

    private interface TestHandler extends EventHandler {

        void onTest(TestEvent event);
    }

    // A controller that makes requests through a client that takes a task monitor
    private static final class Requester extends Controller {
    }


    // --------------------------------------------------------------------------------


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
