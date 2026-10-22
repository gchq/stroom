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

import stroom.widget.component.client.ComponentNode;
import stroom.widget.util.client.PresenterScope;

import com.google.web.bindery.event.shared.Event;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;
import com.gwtplatform.mvp.client.View;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestMyPresenterWidget {

    private final EventBus eventBus = new SimpleEventBus();
    private final List<String> events = new ArrayList<>();

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
    void disposingByItsOwnerAndItsScopeReleasesItOnce() {
        final PresenterScope scope = new PresenterScope();
        final TestPresenter presenter = scope.capture(() -> new TestPresenter("presenter"));
        final ComponentNode root = ComponentNode.root("tab");
        root.adopt(presenter.getComponentNode());

        root.dispose();
        scope.dispose();

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
    void handlersAddedWithItAsSourceAreRemovedOutsideAScope() {
        final TestPresenter presenter = new TestPresenter("presenter");
        presenter.addTestHandler(() -> events.add("heard"));

        presenter.fireTest();
        presenter.dispose();
        presenter.fireTest();

        assertThat(events).containsExactly("heard", "presenter disposed");
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

        void addTestHandler(final Runnable handler) {
            addHandlerToSource(TYPE, handler);
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
