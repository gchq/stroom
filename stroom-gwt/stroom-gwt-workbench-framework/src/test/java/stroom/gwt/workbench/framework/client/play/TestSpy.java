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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestSpy {

    @Test
    void testRecordsCalls() {
        final Spy spy = new Spy("onChange");
        assertThat(spy.getName()).isEqualTo("onChange");
        assertThat(spy.getCallCount()).isZero();
        assertThat(spy.getLastCall()).isNull();

        spy.call();
        spy.call(1, "a");
        spy.call((Object) null);

        assertThat(spy.getCallCount()).isEqualTo(3);
        assertThat(spy.getCalls()).containsExactly(List.of(), List.of(1, "a"), Arrays.asList((Object) null));
        assertThat(spy.getLastCall()).containsExactly((Object) null);
        assertThat(spy.callCount().get()).isEqualTo(3);
        assertThat(spy.callCount().getLabel()).isEqualTo("onChange.mock.calls.length");
        assertThat(spy.lastCall().getLabel()).isEqualTo("onChange.mock.lastCall");

        spy.mockClear();
        assertThat(spy.getCalls()).isEmpty();
    }

    @Test
    void testCallsAreCopied() {
        final Spy spy = new Spy("onPick");
        final Object[] args = {"a"};
        spy.call(args);
        args[0] = "b";
        assertThat(spy.getLastCall()).containsExactly("a");
    }

    @Test
    void testFunctionalInterfaces() {
        final Spy spy = new Spy("fn").mockReturnValue("result");
        final Runnable runnable = spy.asRunnable();
        final Consumer<String> consumer = spy.asConsumer();
        final BiConsumer<String, Integer> biConsumer = spy.asBiConsumer();
        final Supplier<String> supplier = spy.asSupplier();
        final Function<Integer, String> function = spy.asFunction();
        final BiFunction<Integer, Integer, String> biFunction = spy.asBiFunction();
        final Function<Integer, Integer> implemented = spy.asFunction(value -> value * 2);

        runnable.run();
        consumer.accept(null);
        biConsumer.accept("a", 1);
        assertThat(supplier.get()).isEqualTo("result");
        assertThat(function.apply(5)).isEqualTo("result");
        assertThat(biFunction.apply(1, 2)).isEqualTo("result");
        assertThat(implemented.apply(4)).isEqualTo(8);
        assertThat(spy.call()).isEqualTo("result");

        assertThat(spy.getCalls()).containsExactly(
                List.of(),
                Arrays.asList((Object) null),
                List.of("a", 1),
                List.of(),
                List.of(5),
                List.of(1, 2),
                List.of(4),
                List.of());
    }

    @Test
    void testLogging() {
        final List<String> logged = new ArrayList<>();
        final Spy spy = new Spy("onOpen").logTo((name, detail) -> logged.add(name + ":" + detail));
        spy.call();
        spy.call("doc", 2);
        spy.logTo(null);
        spy.call("ignored");

        assertThat(logged).containsExactly("onOpen:null", "onOpen:\"doc\", 2");
        assertThat(spy.getCallCount()).isEqualTo(3);
    }

    @Test
    void testSpies() {
        Spies.startRendering();
        final Spy registered = Spies.register("onTestSpies");
        final Spy playSpy = Spies.get("onTestSpies");
        assertThat(Spies.register("onTestSpies")).isSameAs(registered);
        assertThat(Spies.get("onTestSpies")).isSameAs(playSpy);
        assertThat(Spies.get("onTestSpiesOther")).isNotSameAs(playSpy);
        registered.call("a");
        assertThat(playSpy.getCalls()).containsExactly(List.of("a"));
        assertThat(playSpy.getName()).isEqualTo("onTestSpies");

        // A new rendering: the play function's spy is the same object, but reads the new
        // rendering's spy, which has no calls
        Spies.startRendering();
        final Spy rerendered = Spies.register("onTestSpies");
        assertThat(rerendered).isNotSameAs(registered);
        assertThat(Spies.get("onTestSpies")).isSameAs(playSpy);
        assertThat(playSpy.getCalls()).isEmpty();
        rerendered.call("b");
        assertThat(playSpy.getLastCall()).containsExactly("b");
        assertThat(playSpy.callCount().get()).isOne();
    }

    @Test
    void testSpies_callsFromAnEarlierRenderingAreIgnored() {
        // Regression: a callback left over from the previous rendering (e.g. a timer) recorded
        // its calls into the next rendering's spy
        Spies.startRendering();
        final Spy first = Spies.register("onTestSpiesStale");
        final Runnable staleCallback = first.asRunnable();
        Spies.startRendering();
        Spies.register("onTestSpiesStale");

        first.call("late");
        staleCallback.run();

        assertThat(Spies.get("onTestSpiesStale").getCalls()).isEmpty();
        assertThat(first.getCalls()).isEmpty();
    }

    @Test
    void testSpies_unregisteredNameFails() {
        // Regression: a misspelt name silently made a new spy with no calls, so
        // not().toHaveBeenCalled() passed
        Spies.startRendering();
        Spies.register("onChange");
        final Spy misspelt = Spies.get("onChnage");

        assertThatThrownBy(misspelt::getCalls)
                .isInstanceOf(PlayException.class)
                .hasMessageContaining("No spy named \"onChnage\"")
                .hasMessageContaining("[onChange]");
        assertThatThrownBy(() -> misspelt.callCount().get()).isInstanceOf(PlayException.class);
        assertThatThrownBy(() -> misspelt.call("x")).isInstanceOf(PlayException.class);
    }

    @Test
    void testSpies_unregisteredNameFailsTheExpectation() {
        Spies.startRendering();
        final Play play = new Play();
        play.expect(play.spy("onTestSpiesMissing")).not().toHaveBeenCalled();

        assertThatThrownBy(() -> play.getSteps().get(0).run(null))
                .isInstanceOf(PlayException.class)
                .hasMessageContaining("No spy named \"onTestSpiesMissing\"");
    }
}
