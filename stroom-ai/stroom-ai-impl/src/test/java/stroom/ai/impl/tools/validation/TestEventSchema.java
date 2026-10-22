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

package stroom.ai.impl.tools.validation;

import stroom.ai.impl.tools.validation.EventSchema.Child;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// What the event-logging XSD allows below Event, read from the 4.1.0 schema.
class TestEventSchema {

    private static final EventSchema SCHEMA = EventSchema.parse(HeldSchemasWorld.XSD_4_1_0);

    @Test
    void testAPathResolvesToItsChainOfChildren() {
        final List<Child> chain = SCHEMA.resolve("Event/EventSource/User/Id");
        assertThat(chain).extracting(Child::name).containsExactly("EventSource", "User", "Id");
        assertThat(SCHEMA.isLeaf(chain.getLast().decl())).isTrue();
        assertThat(SCHEMA.resolve("")).isEmpty();
    }

    @Test
    void testAPathTheSchemaHasNotSaysWhatItMayHaveMeant() {
        assertThatThrownBy(() -> SCHEMA.resolve("EventSource/Usr"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("'Event/EventSource' has no child 'Usr'. Did you mean ['User'")
                .hasMessageContaining(" Allowed here: [");
        // A value of an enumeration is said to be one.
        assertThatThrownBy(() -> SCHEMA.resolve("EventDetail/Authenticate/Logon"))
                .hasMessageContaining("'Logon' is a value of: ['Event/EventDetail/Authenticate/Action'");
    }

    @Test
    void testPathsNamedAreShortestFirst() {
        final List<String> users = SCHEMA.pathsNamed("User");
        assertThat(users.getFirst()).isEqualTo("Event/EventSource/User");
        assertThat(users).contains("Event/EventDetail/Authenticate/User");
        assertThat(SCHEMA.pathsNamed("Nowhere")).isEmpty();
    }

    @Test
    void testRepeatableElementsAreKnown() {
        assertThat(SCHEMA.canRepeat("EventDetail/Unknown/Data")).isTrue();
        assertThat(SCHEMA.canRepeat("EventTime/TimeCreated")).isFalse();
        assertThat(SCHEMA.canRepeat("concat(a, b)")).isFalse();
    }

    @Test
    void testALeafsTypeAndValues() {
        final Child created = SCHEMA.resolve("EventTime/TimeCreated").getLast();
        assertThat(SCHEMA.baseType(created.decl())).isEqualTo("dateTime");
        assertThat(SCHEMA.enumeration(created.decl())).isNull();
        final Child action = SCHEMA.resolve("EventDetail/Authenticate/Action").getLast();
        assertThat(SCHEMA.enumeration(action.decl())).contains("Logon", "Logoff");
    }

    @Test
    void testAnXsdThatIsNotEventLoggingIsRefused() {
        assertThatThrownBy(() -> EventSchema.parse("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
