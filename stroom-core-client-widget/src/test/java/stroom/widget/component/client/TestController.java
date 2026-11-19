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

import stroom.widget.popup.client.DialogAction;
import stroom.widget.popup.client.event.DisablePopupEvent;
import stroom.widget.popup.client.event.EnablePopupEvent;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.RenamePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.task.client.SimpleTask;
import stroom.widget.task.client.Task;
import stroom.widget.task.client.TaskMonitor;
import stroom.widget.task.client.TaskMonitorFactory;
import stroom.widget.task.client.TaskStartEvent;

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
    void theTaskMonitorIsTheOneProvidedWhenATaskStarts() {
        // Given before it is owned, e.g. to a list made in a constructor, as the copies of this
        // helper in two dozen controllers did by hand
        final Requester requester = new Requester();
        final TaskMonitorFactory taskMonitor = requester.taskMonitor();
        final TaskMonitor provided = Mockito.mock(TaskMonitor.class);
        final ControllerNode root = ControllerNode.root("dialog");
        root.provide(Contexts.TASK_MONITOR, ContextValue.of(() -> provided));
        root.adopt(requester.getControllerNode());

        assertThat(taskMonitor.createTaskMonitor()).isSameAs(provided);
    }

    @Test
    void theTaskMonitorIsAppWideWhenNothingProvidesOne() {
        final EventBus eventBus = new SimpleEventBus();
        final Requester requester = new Requester(eventBus);
        ControllerNode.root("story").adopt(requester.getControllerNode());
        final List<Object> sources = new ArrayList<>();
        eventBus.addHandler(TaskStartEvent.getType(), event -> sources.add(event.getSource()));
        final Task task = new SimpleTask("Fetching");

        // Stroom's own busy indicator, told of the task by the controller, on the bus
        requester.taskMonitor().createTaskMonitor().onStart(task);

        assertThat(sources).containsExactly(requester);
    }

    @Test
    void theTaskMonitorIsLookedUpForEachTask() {
        final EventBus eventBus = new SimpleEventBus();
        final Requester requester = new Requester(eventBus);
        final ControllerNode root = ControllerNode.root("dialog");
        root.adopt(requester.getControllerNode());
        final TaskMonitor provided = Mockito.mock(TaskMonitor.class);
        final TaskMonitorFactory taskMonitor = requester.taskMonitor();

        root.provide(Contexts.TASK_MONITOR, ContextValue.of(() -> provided));
        assertThat(taskMonitor.createTaskMonitor()).isSameAs(provided);

        // Once the dialog takes it back, the next task is app-wide again
        root.unprovide(Contexts.TASK_MONITOR);
        final List<Object> sources = new ArrayList<>();
        eventBus.addHandler(TaskStartEvent.getType(), event -> sources.add(event.getSource()));
        taskMonitor.createTaskMonitor().onStart(new SimpleTask("Saving"));
        assertThat(sources).containsExactly(requester);
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
            final Requester dialog = opener.makeDialogChild(() -> new Requester(eventBus));
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
    void aDialogsButtonsAreEnabled() {
        final EventBus eventBus = new SimpleEventBus();
        final Parent opener = new Parent(null);
        final Parent dialog = new Parent(eventBus);
        final List<String> seen = new ArrayList<>();
        // The popup manager knows a dialog by the controller it shows
        eventBus.addHandler(DisablePopupEvent.getType(), e ->
                seen.add("disable " + e.getAction() + " " + (e.getController() == dialog)));
        eventBus.addHandler(EnablePopupEvent.getType(), e ->
                seen.add("enable " + e.getAction() + " " + (e.getController() == dialog)));
        ControllerDialog.showOnce(opener, dialog);

        ControllerDialog.setActionEnabled(dialog, DialogAction.OK, false);
        ControllerDialog.setActionEnabled(dialog, DialogAction.OK, true);
        assertThat(seen).containsExactly("disable OK true", "enable OK true");

        // Once it is gone, nothing
        opener.dispose();
        ControllerDialog.setActionEnabled(dialog, DialogAction.OK, false);
        assertThat(seen).hasSize(2);
    }

    @Test
    void aDialogsButtonIsPressed() {
        final EventBus eventBus = new SimpleEventBus();
        final Parent opener = new Parent(null);
        final Parent dialog = new Parent(eventBus);
        final List<String> seen = new ArrayList<>();
        eventBus.addHandler(HidePopupRequestEvent.getType(), e ->
                seen.add((e.isOk() ? "OK" : "not OK") + " " + (e.getController() == dialog)));
        ControllerDialog.showOnce(opener, dialog);

        ControllerDialog.pressAction(dialog, DialogAction.OK);
        assertThat(seen).containsExactly("OK true");
    }

    @Test
    void aDialogShownAgainIsKeptWhenHidden() {
        final Consumer<Runnable> scheduler = ControllerDialog.deferred;
        final List<Runnable> deferred = new ArrayList<>();
        ControllerDialog.deferred = deferred::add;
        try {
            final EventBus eventBus = new SimpleEventBus();
            final Parent owner = new Parent(null);
            final Parent dialog = new Parent(eventBus);
            owner.getControllerNode().adopt(dialog.getControllerNode());
            final List<Controller> shown = new ArrayList<>();
            eventBus.addHandler(ShowPopupEvent.getType(), e -> shown.add(e.getController()));

            ControllerDialog.show(dialog).fire();
            ControllerDialog.show(dialog).fire();
            assertThat(shown).containsExactly(dialog, dialog);

            // Hidden, it is kept, to be shown again
            ControllerDialog.hide(dialog, false, false);
            deferred.forEach(Runnable::run);
            assertThat(dialog.isDisposed()).isFalse();

            // It goes with what owns it
            owner.dispose();
            assertThat(dialog.isDisposed()).isTrue();
        } finally {
            ControllerDialog.deferred = scheduler;
        }
    }

    @Test
    void aDialogIsHiddenThenDisposed() {
        final Consumer<Runnable> scheduler = ControllerDialog.deferred;
        final List<Runnable> deferred = new ArrayList<>();
        ControllerDialog.deferred = deferred::add;
        try {
            final EventBus eventBus = new SimpleEventBus();
            final Parent opener = new Parent(null);
            final Parent dialog = new Parent(eventBus);
            final List<String> seen = new ArrayList<>();
            eventBus.addHandler(HidePopupEvent.getType(), e ->
                    seen.add(e.isAutoClose() + " " + e.isOk() + " " + (e.getController() == dialog)));
            ControllerDialog.showOnce(opener, dialog);

            ControllerDialog.hide(dialog, true, false);
            assertThat(seen).containsExactly("true false true");
            deferred.forEach(Runnable::run);
            assertThat(dialog.isDisposed()).isTrue();

            // Once hidden, it is no longer shown, so nothing is hidden
            ControllerDialog.hide(dialog, false, false);
            assertThat(seen).hasSize(1);
        } finally {
            ControllerDialog.deferred = scheduler;
        }
    }

    @Test
    void aDialogIsRenamed() {
        final EventBus eventBus = new SimpleEventBus();
        final Parent opener = new Parent(null);
        final Parent dialog = new Parent(eventBus);
        final List<String> seen = new ArrayList<>();
        eventBus.addHandler(RenamePopupEvent.getType(), e ->
                seen.add(e.getCaption() + " " + (e.getController() == dialog)));
        ControllerDialog.showOnce(opener, dialog);

        ControllerDialog.rename(dialog, "A page");
        assertThat(seen).containsExactly("A page true");

        // Once it is gone, nothing
        opener.dispose();
        ControllerDialog.rename(dialog, "Another page");
        assertThat(seen).hasSize(1);
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

        Requester makeDialogChild(final Provider<Requester> provider) {
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

        private Requester() {
            super();
        }

        private Requester(final EventBus eventBus) {
            super(eventBus);
        }
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
