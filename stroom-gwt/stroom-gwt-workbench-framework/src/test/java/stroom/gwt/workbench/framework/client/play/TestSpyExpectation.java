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


package stroom.gwt.workbench.framework.client.play;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestSpyExpectation {

    @Test
    void testToHaveBeenCalled() {
        final Spy spy = new Spy("onClick");
        assertFails(spy, SpyExpectation::toHaveBeenCalled, "Expected number of calls: >= 1\n"
                                                            + "Received number of calls: 0");
        assertPasses(spy, e -> e.not().toHaveBeenCalled());
        spy.call();
        assertPasses(spy, SpyExpectation::toHaveBeenCalled);
        assertFails(spy, e -> e.not().toHaveBeenCalled(), "Expected number of calls: 0");
    }

    @Test
    void testToHaveBeenCalledTimes() {
        final Spy spy = new Spy("onClick");
        spy.call("a");
        spy.call("b");
        assertPasses(spy, e -> e.toHaveBeenCalledTimes(2));
        assertPasses(spy, e -> e.not().toHaveBeenCalledTimes(1));
        assertFails(spy, e -> e.toHaveBeenCalledTimes(1), "Received number of calls: 2\n1: \"a\"\n2: \"b\"");
    }

    @Test
    void testToHaveBeenCalledWith() {
        final Spy spy = new Spy("onResult");
        spy.call(true);
        spy.call(42);
        spy.call(Map.of("id", 1, "name", "Pipeline work"));
        assertPasses(spy, e -> e.toHaveBeenCalledWith(true));
        assertPasses(spy, e -> e.toHaveBeenCalledWith(42L));
        assertPasses(spy, e -> e.toHaveBeenCalledWith(ValueMatcher.objectContaining(Map.of("name", "Pipeline work"))));
        assertPasses(spy, e -> e.not().toHaveBeenCalledWith(false));
        assertFails(spy, e -> e.toHaveBeenCalledWith(false), "Expected: false");
        assertFails(spy, e -> e.toHaveBeenCalledWith(), "Expected: \n");
    }

    @Test
    void testToHaveBeenCalledWith_null() {
        final Spy spy = new Spy("onChange");
        spy.call((Object) null);
        assertPasses(spy, e -> e.toHaveBeenCalledWith((Object) null));
        // A bare null is an array of arguments to Java, so it's treated as one null argument
        assertPasses(spy, e -> e.toHaveBeenCalledWith((Object[]) null));
    }

    @Test
    void testLastAndNthCalledWith() {
        final Spy spy = new Spy("onChange");
        spy.call("a");
        spy.call("b", 2);
        assertPasses(spy, e -> e.toHaveBeenLastCalledWith("b", 2));
        assertFails(spy, e -> e.toHaveBeenLastCalledWith("a"), "Expected: \"a\"");
        assertPasses(spy, e -> e.toHaveBeenNthCalledWith(1, "a"));
        assertFails(spy, e -> e.toHaveBeenNthCalledWith(3, "a"), "Expected call 3: \"a\"");
        assertThatThrownBy(() -> new Play().expect(spy).toHaveBeenNthCalledWith(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertFails(new Spy("none"), e -> e.toHaveBeenLastCalledWith("a"), "Received number of calls: 0");
    }

    @Test
    void testDescriptions() {
        final Spy spy = new Spy("onOpenDoc");
        final Play play = new Play();
        play.expect(spy).toHaveBeenCalled();
        play.expect(spy).not().toHaveBeenCalled();
        play.expect(spy).toHaveBeenCalledTimes(2);
        play.expect(spy).toHaveBeenCalledWith("a", 1, List.of(2));
        play.expect(spy).toHaveBeenLastCalledWith(ValueMatcher.objectContaining(Map.of("id", 1)));
        play.expect(spy).toHaveBeenNthCalledWith(2, true);

        assertThat(play.getSteps()).extracting(step -> step.describe(null)).containsExactly(
                "expect(onOpenDoc).toHaveBeenCalled()",
                "expect(onOpenDoc).not.toHaveBeenCalled()",
                "expect(onOpenDoc).toHaveBeenCalledTimes(2)",
                "expect(onOpenDoc).toHaveBeenCalledWith(\"a\", 1, [2])",
                "expect(onOpenDoc).toHaveBeenLastCalledWith(ObjectContaining({ id: 1 }))",
                "expect(onOpenDoc).toHaveBeenNthCalledWith(2, true)");
    }

    @Test
    void testCallsAreCheckedWhenTheStepRuns() {
        final Spy spy = new Spy("onSave");
        final Play play = new Play();
        play.expect(spy).toHaveBeenCalledTimes(1);
        spy.call();
        play.getSteps().get(0).run(null);
    }

    private static void assertPasses(final Spy spy, final Consumer<SpyExpectation> matcher) {
        final Play play = new Play();
        matcher.accept(play.expect(spy));
        play.getSteps().get(0).run(null);
    }

    private static void assertFails(final Spy spy, final Consumer<SpyExpectation> matcher, final String messagePart) {
        final Play play = new Play();
        matcher.accept(play.expect(spy));
        assertThatThrownBy(() -> play.getSteps().get(0).run(null))
                .isInstanceOf(PlayException.class)
                .hasMessageContaining(messagePart);
    }
}
