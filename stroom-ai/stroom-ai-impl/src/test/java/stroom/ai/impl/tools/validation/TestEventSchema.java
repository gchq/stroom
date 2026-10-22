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
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
    void testTheDomIsNotReadOnceTheSchemaIsMade() {
        // Before, children, paths and values were worked out from the DOM as they were asked for, from whichever
        // thread asked (HeldSchemas shares one schema), and a Xerces DOM is not safe to read from several threads.
        // Here the DOM is emptied once the schema is made: nothing it gives changes.
        final EventSchema schema = EventSchema.parse(HeldSchemasWorld.XSD_4_1_0);
        final Map<String, Object> before = everything(schema);
        final NodeList all = schema.event().getOwnerDocument().getElementsByTagName("*");
        final List<Element> elements = new ArrayList<>();
        for (int i = 0; i < all.getLength(); i++) {
            elements.add((Element) all.item(i));
        }
        for (final Element element : elements) {
            while (element.getFirstChild() != null) {
                element.removeChild(element.getFirstChild());
            }
        }

        assertThat(everything(schema)).isEqualTo(before);
    }

    @Test
    void testOneSchemaGivesTheSameAnswersOnManyThreadsAtOnce() throws Exception {
        final EventSchema schema = EventSchema.parse(HeldSchemasWorld.XSD_4_1_0);
        final Map<String, Object> expected = everything(EventSchema.parse(HeldSchemasWorld.XSD_4_1_0));
        final ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            final CountDownLatch start = new CountDownLatch(1);
            final List<Future<Map<String, Object>>> answers = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                answers.add(executor.submit(() -> {
                    start.await();
                    return everything(schema);
                }));
            }
            start.countDown();
            for (final Future<Map<String, Object>> answer : answers) {
                assertThat(answer.get(60, TimeUnit.SECONDS)).isEqualTo(expected);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void testAnXsdThatIsNotEventLoggingIsRefused() {
        assertThatThrownBy(() -> EventSchema.parse("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // What the schema gives for every path below Event, four levels down: names, order, leaves, types, values,
    // patterns, descriptions, repeats, and the paths and values indexed.
    private static Map<String, Object> everything(final EventSchema schema) {
        final Map<String, Object> out = new LinkedHashMap<>();
        final Deque<String> queue = new ArrayDeque<>(List.of(""));
        while (!queue.isEmpty()) {
            final String path = queue.removeFirst();
            final List<Child> chain = schema.resolve(path);
            final Element decl = chain.isEmpty()
                    ? schema.event()
                    : chain.getLast().decl();
            final List<String> children = schema.children(decl).stream()
                    .map(child -> child.name() + "/" + child.index() + "/" + child.required() + "/"
                                  + child.repeatable() + "/" + (child.choice() != null))
                    .toList();
            out.put(path, List.of(children, schema.isLeaf(decl), String.valueOf(schema.baseType(decl)),
                    String.valueOf(schema.enumeration(decl)), schema.patterns(decl), chain.isEmpty()
                            ? ""
                            : schema.describe(chain), schema.canRepeat(path)));
            if (path.split("/").length < 4) {
                schema.children(decl).forEach(child -> queue.add(path.isEmpty()
                        ? child.name()
                        : path + "/" + child.name()));
            }
        }
        out.put("paths named User", schema.pathsNamed("User"));
        out.put("paths with Logon", schema.pathsWithValue("Logon"));
        out.put("required choices", schema.requiredChoices().size());
        return out;
    }
}
