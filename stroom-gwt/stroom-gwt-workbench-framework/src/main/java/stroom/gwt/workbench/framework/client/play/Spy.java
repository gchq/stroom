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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/// A mock function that records its calls, the equivalent of `fn()` from `storybook/test`. A
/// story gets one from `StoryContext.fn(name)` and passes it to a widget as a callback, e.g.
/// `button.addClickHandler(event -> onClick.call())` or `widget.setOnChange(onChange.asConsumer())`;
/// the play function gets the same spy by name from [Play#spy(String)] and checks its calls, e.g.
/// `play.expect(onClick).toHaveBeenCalledTimes(1)`.
///
/// Spies are kept by name (see [Spies]) and their calls are cleared each time the story renders,
/// so a re-run or rewind of the play function starts afresh.
public final class Spy {

    private final String name;
    private final List<List<Object>> calls = new ArrayList<>();
    // Told about each call, e.g. to log it to the Actions addon, may be null
    private BiConsumer<String, String> logger;
    private Object returnValue;

    /// @param name The spy's name, e.g. `onClick`.
    Spy(final String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    /// @return The spy's name, e.g. `onClick`, as shown in the Interactions addon.
    public String getName() {
        return name;
    }

    /// Records a call.
    ///
    /// @param args The call's arguments.
    /// @return The value set by [#mockReturnValue(Object)], or null.
    public Object call(final Object... args) {
        return record(args != null
                ? Arrays.asList(args)
                : Collections.singletonList(null));
    }

    private Object record(final List<Object> args) {
        final List<Object> copy = Collections.unmodifiableList(new ArrayList<>(args));
        calls.add(copy);
        if (logger != null) {
            logger.accept(name, copy.isEmpty()
                    ? null
                    : Values.formatAll(copy));
        }
        return returnValue;
    }

    /// Sets the value calls return, the equivalent of `mockReturnValue(value)`.
    ///
    /// @param value The value, may be null.
    /// @return This spy.
    public Spy mockReturnValue(final Object value) {
        this.returnValue = value;
        return this;
    }

    /// Logs each call, e.g. to the Actions addon.
    ///
    /// @param logger Given the spy's name and its arguments formatted, or null if there are none;
    ///               null to stop logging.
    /// @return This spy.
    public Spy logTo(final BiConsumer<String, String> logger) {
        this.logger = logger;
        return this;
    }

    /// @return A runnable that records a call with no arguments.
    public Runnable asRunnable() {
        return () -> record(Collections.emptyList());
    }

    /// @param <T> The type of the argument.
    /// @return A consumer that records a call with its argument.
    public <T> Consumer<T> asConsumer() {
        return arg -> record(Collections.singletonList(arg));
    }

    /// @param <T> The type of the first argument.
    /// @param <U> The type of the second argument.
    /// @return A bi-consumer that records a call with its two arguments.
    public <T, U> BiConsumer<T, U> asBiConsumer() {
        return (arg1, arg2) -> record(Arrays.asList(arg1, arg2));
    }

    /// @param <R> The type of the result.
    /// @return A supplier that records a call with no arguments and returns the value set by
    /// [#mockReturnValue(Object)].
    public <R> Supplier<R> asSupplier() {
        return () -> cast(record(Collections.emptyList()));
    }

    /// @param <T> The type of the argument.
    /// @param <R> The type of the result.
    /// @return A function that records a call with its argument and returns the value set by
    /// [#mockReturnValue(Object)].
    public <T, R> Function<T, R> asFunction() {
        return arg -> cast(record(Collections.singletonList(arg)));
    }

    /// @param implementation What the function does after its call is recorded, the equivalent of
    ///                       `fn(implementation)`.
    /// @param <T>            The type of the argument.
    /// @param <R>            The type of the result.
    /// @return A function that records a call with its argument then returns the result of the
    /// implementation.
    public <T, R> Function<T, R> asFunction(final Function<T, R> implementation) {
        return arg -> {
            record(Collections.singletonList(arg));
            return implementation.apply(arg);
        };
    }

    /// @param <T> The type of the first argument.
    /// @param <U> The type of the second argument.
    /// @param <R> The type of the result.
    /// @return A bi-function that records a call with its two arguments and returns the value set
    /// by [#mockReturnValue(Object)].
    public <T, U, R> BiFunction<T, U, R> asBiFunction() {
        return (arg1, arg2) -> cast(record(Arrays.asList(arg1, arg2)));
    }

    @SuppressWarnings("unchecked")
    private static <R> R cast(final Object value) {
        return (R) value;
    }

    /// @return The arguments of each call, in order, the equivalent of `mock.calls`.
    public List<List<Object>> getCalls() {
        return Collections.unmodifiableList(calls);
    }

    /// @return The number of calls.
    public int getCallCount() {
        return calls.size();
    }

    /// @return The arguments of the last call, or null if there have been no calls, the
    /// equivalent of `mock.lastCall` or `mock.calls.at(-1)`.
    public List<Object> getLastCall() {
        return calls.isEmpty()
                ? null
                : calls.get(calls.size() - 1);
    }

    /// @return A value for the number of calls, e.g. for `play.expect(spy.callCount())`.
    public Value<Integer> callCount() {
        return Value.of(name + ".mock.calls.length", this::getCallCount);
    }

    /// @return A value for the arguments of the last call, e.g.
    /// `play.expect(spy.lastCall()).toEqual(List.of("a"))`.
    public Value<List<Object>> lastCall() {
        return Value.of(name + ".mock.lastCall", this::getLastCall);
    }

    /// Forgets the calls, the equivalent of `mockClear()`. The return value is kept.
    public void mockClear() {
        calls.clear();
    }

    @Override
    public String toString() {
        return name;
    }
}
