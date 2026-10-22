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

import com.google.web.bindery.event.shared.HandlerRegistration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestHandlerContainerImpl {

    private final List<String> events = new ArrayList<>();

    @Test
    void bindingTwiceBindsOnce() {
        final TestContainer container = new TestContainer();

        container.bind();
        container.bind();

        assertThat(container.isBound()).isTrue();
        assertThat(events).containsExactly("bind");
    }

    @Test
    void unbindingRemovesTheRegistrationsThenCallsOnUnbind() {
        final TestContainer container = new TestContainer();
        container.bind();

        container.unbind();

        assertThat(container.isBound()).isFalse();
        Mockito.verify(container.registration).removeHandler();
        assertThat(events).containsExactly("bind", "removed", "unbind");
    }

    @Test
    void unbindingWhenNotBoundDoesNothing() {
        final TestContainer container = new TestContainer();

        container.unbind();

        assertThat(events).isEmpty();
    }

    @Test
    void itCanBeBoundAgainAndItsOldRegistrationsAreNotRemovedTwice() {
        final TestContainer container = new TestContainer();
        container.bind();
        container.unbind();

        container.bind();
        container.unbind();

        assertThat(events).containsExactly("bind", "removed", "unbind", "bind", "removed", "unbind");
        Mockito.verify(container.registration, Mockito.times(2)).removeHandler();
    }

    @Test
    void ginBindsOneOnceItIsMade() {
        final TestContainer container = new TestContainer();

        container.automaticBind();

        assertThat(container.isBound()).isTrue();
    }

    // --------------------------------------------------------------------------------

    private class TestContainer extends HandlerContainerImpl {

        private final HandlerRegistration registration = Mockito.mock(HandlerRegistration.class);

        private TestContainer() {
            Mockito.doAnswer(invocation -> events.add("removed")).when(registration).removeHandler();
        }

        @Override
        protected void onBind() {
            events.add("bind");
            registerHandler(registration);
        }

        @Override
        protected void onUnbind() {
            events.add("unbind");
        }
    }
}
