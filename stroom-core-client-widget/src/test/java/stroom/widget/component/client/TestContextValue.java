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

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TestContextValue {

    @Test
    void setChangesTheValue() {
        final ContextValue<String> value = ContextValue.of("red");

        value.set("blue");

        assertThat(value.get()).isEqualTo("blue");
    }

    @Test
    void subscribersAreOnlyToldOfChanges() {
        final ContextValue<String> value = ContextValue.of("red");
        final AtomicInteger changes = new AtomicInteger();
        value.subscribe(changes::incrementAndGet);

        value.set("red");
        value.set("blue");
        value.set(null);
        value.set(null);

        assertThat(changes).hasValue(2);
    }

    @Test
    void anUnsubscribedSubscriberIsNotTold() {
        final ContextValue<String> value = ContextValue.of("red");
        final AtomicInteger changes = new AtomicInteger();
        final HandlerRegistration registration = value.subscribe(changes::incrementAndGet);

        registration.removeHandler();
        value.set("blue");

        assertThat(changes).hasValue(0);
        assertThat(value.subscriberCount()).isZero();
    }
}
