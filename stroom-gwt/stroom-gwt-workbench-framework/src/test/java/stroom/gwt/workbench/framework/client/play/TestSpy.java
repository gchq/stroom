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
        final Spy spy = Spies.get("onTestSpies");
        assertThat(Spies.get("onTestSpies")).isSameAs(spy);
        assertThat(Spies.get("onTestSpiesOther")).isNotSameAs(spy);
        spy.call();

        Spies.clearAll();

        // The same spy, so the play function's references stay valid, but with no calls
        assertThat(Spies.get("onTestSpies")).isSameAs(spy);
        assertThat(spy.getCalls()).isEmpty();
    }
}
