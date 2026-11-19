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

import com.google.web.bindery.event.shared.HandlerRegistration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestListeners {

    private final List<String> events = new ArrayList<>();

    @Test
    void tellsEveryListenerInOrder() {
        final Listeners<String> listeners = new Listeners<>();
        listeners.add(value -> events.add("first " + value));
        listeners.add(value -> events.add("second " + value));

        listeners.fire("x");

        assertThat(events).containsExactly("first x", "second x");
    }

    @Test
    void aRemovedListenerIsNotTold() {
        final Listeners<String> listeners = new Listeners<>();
        final HandlerRegistration registration = listeners.add(events::add);

        registration.removeHandler();
        listeners.fire("x");

        assertThat(events).isEmpty();
        assertThat(listeners.isEmpty()).isTrue();
    }

    @Test
    void aListenerRemovedWhileFiringIsNotTold() {
        final Listeners<String> listeners = new Listeners<>();
        final List<HandlerRegistration> second = new ArrayList<>();
        listeners.add(value -> {
            events.add("first");
            second.get(0).removeHandler();
        });
        second.add(listeners.add(value -> events.add("second")));

        listeners.fire("x");

        assertThat(events).containsExactly("first");
        assertThat(listeners.size()).isEqualTo(1);
    }

    @Test
    void aListenerAddedWhileFiringIsToldNextTime() {
        final Listeners<String> listeners = new Listeners<>();
        listeners.add(value -> {
            events.add("first " + value);
            if (listeners.size() == 1) {
                listeners.add(added -> events.add("added " + added));
            }
        });

        listeners.fire("1");
        listeners.fire("2");

        assertThat(events).containsExactly("first 1", "first 2", "added 2");
    }

    @Test
    void oneFailureDoesNotStopTheRestAndTheFirstIsRethrown() {
        final Listeners<String> listeners = new Listeners<>();
        listeners.add(value -> {
            throw new IllegalStateException("first");
        });
        listeners.add(value -> {
            throw new IllegalStateException("second");
        });
        listeners.add(events::add);

        assertThatThrownBy(() -> listeners.fire("x")).hasMessage("first");
        assertThat(events).containsExactly("x");
    }

    @Test
    void aRegistrationOwnedByAComponentIsRemovedWithIt() {
        final Listeners<String> listeners = new Listeners<>();
        final ControllerNode root = ControllerNode.root("root");
        root.add(listeners.add(events::add));

        root.dispose();
        listeners.fire("x");

        assertThat(events).isEmpty();
        assertThat(listeners.isEmpty()).isTrue();
    }
}
