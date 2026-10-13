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
/// the play function gets the spy with the same name from [Play#spy(String)] and checks its calls,
/// e.g. `play.expect(onClick).toHaveBeenCalledTimes(1)`.
///
/// There are three kinds of spy (see [Spies]):
///
/// * a story's spy belongs to one rendering of the story and records calls only while that
///   rendering is the current one, so a callback left over from an earlier rendering, e.g. a
///   timer, can't add calls to the next;
/// * a play function's spy reads the calls of the current rendering's spy with the same name, and
///   throws a [PlayException] if the story didn't register one, e.g. because the name is misspelt;
/// * a spy made with the package's constructor (in tests) always records.
///
/// [#mockClear()], [#mockReturnValue(Object)] and [#logTo(BiConsumer)] called on a spy from
/// [Play#spy(String)] while the play function adds its steps add a step (shown in the
/// Interactions addon), so they happen at that point of the play, and again on each rerun, as
/// they would in a React play; called inside a step, e.g. `play.run(...)`, they happen at once.
///
/// To pass an array as a single argument, cast it, e.g. `spy.call((Object) names)`; otherwise
/// Java spreads it into separate arguments.
public final class Spy {

    // The generation of a spy that isn't bound to a rendering
    private static final int UNBOUND = -1;

    private final String name;
    // The rendering (see Spies) whose calls this spy records, or UNBOUND to always record
    private final int generation;
    // True for the play function's spy, which reads the current rendering's spy
    private final boolean play;
    // For a play function's spy from Play#spy(String), the play function it belongs to, so that
    // calling mockClear() etc. while the play function adds its steps adds a step; may be null
    private final Play builder;
    private final List<List<Object>> calls = new ArrayList<>();
    // Told about each call, e.g. to log it to the Actions addon, may be null
    private BiConsumer<String, String> logger;
    private Object returnValue;

    /// @param name The spy's name, e.g. `onChange`.
    Spy(final String name) {
        this(name, UNBOUND, false, null);
    }

    /// @param name       The spy's name.
    /// @param generation The rendering whose calls it records.
    Spy(final String name, final int generation) {
        this(name, generation, false, null);
    }

    private Spy(final String name, final int generation, final boolean play, final Play builder) {
        this.name = Objects.requireNonNull(name, "name");
        this.generation = generation;
        this.play = play;
        this.builder = builder;
    }

    /// @param name The spy's name.
    /// @return A play function's spy, which reads the current rendering's spy with the name.
    static Spy playSpy(final String name) {
        return new Spy(name, UNBOUND, true, null);
    }

    /// @param name    The spy's name.
    /// @param builder The play function the spy belongs to.
    /// @return A play function's spy, which reads the current rendering's spy with the name, and
    /// whose [#mockClear()], [#mockReturnValue(Object)] and [#logTo(BiConsumer)] add a step when
    /// called while the play function adds its steps.
    static Spy playSpy(final String name, final Play builder) {
        return new Spy(name, UNBOUND, true, Objects.requireNonNull(builder, "builder"));
    }

    // True if a call to change the spy should be a step of the play function, as the play
    // function is adding its steps (which run later, and again on each rerun)
    private boolean isAddingSteps() {
        return play && builder != null && builder.isBuilding();
    }

    // The spy whose calls this one reads and records
    private Spy target() {
        if (!play) {
            return this;
        }
        final Spy registered = Spies.registered(name);
        if (registered == null) {
            throw new PlayException("No spy named \"" + name + "\" was registered when the story rendered, "
                                    + "so it can't have been called. Check the name matches the story's "
                                    + "context.fn(\"" + name + "\"), and that the story registers the spy as it "
                                    + "renders rather than when it is first called. Registered spies: "
                                    + Spies.registeredNames());
        }
        return registered;
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
        if (play) {
            return target().record(args);
        }
        if (generation != UNBOUND && generation != Spies.generation()) {
            // A call from a rendering that has been replaced, e.g. by a timer it started, so
            // ignore it rather than count it as a call in the current rendering
            return returnValue;
        }
        final List<Object> copy = Collections.unmodifiableList(new ArrayList<>(args));
        calls.add(copy);
        if (logger != null) {
            logger.accept(name, copy.isEmpty()
                    ? null
                    : Values.formatAll(copy));
        }
        return returnValue;
    }

    /// Sets the value calls return, the equivalent of `mockReturnValue(value)`. For a play
    /// function's spy called while the play function adds its steps, adds a step that does so.
    ///
    /// @param value The value, may be null.
    /// @return This spy.
    public Spy mockReturnValue(final Object value) {
        if (isAddingSteps()) {
            builder.addStep(PlayStep.action(root -> name + ".mockReturnValue(" + Values.format(value) + ")",
                    root -> target().mockReturnValue(value)));
        } else if (play) {
            target().mockReturnValue(value);
        } else {
            this.returnValue = value;
        }
        return this;
    }

    /// Logs each call, e.g. to the Actions addon. For a play function's spy called while the play
    /// function adds its steps, adds a step that does so.
    ///
    /// @param logger Given the spy's name and its arguments formatted, or null if there are none;
    ///               null to stop logging.
    /// @return This spy.
    public Spy logTo(final BiConsumer<String, String> logger) {
        if (isAddingSteps()) {
            builder.addStep(PlayStep.action(root -> name + ".logTo(...)", root -> target().logTo(logger)));
        } else if (play) {
            target().logTo(logger);
        } else {
            this.logger = logger;
        }
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
    /// @throws PlayException For a play function's spy, if the story didn't register the spy.
    public List<List<Object>> getCalls() {
        return Collections.unmodifiableList(target().calls);
    }

    /// @return The number of calls.
    /// @throws PlayException For a play function's spy, if the story didn't register the spy.
    public int getCallCount() {
        return getCalls().size();
    }

    /// @return The arguments of the last call, or null if there have been no calls, the
    /// equivalent of `mock.lastCall` or `mock.calls.at(-1)`.
    public List<Object> getLastCall() {
        final List<List<Object>> all = getCalls();
        return all.isEmpty()
                ? null
                : all.get(all.size() - 1);
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

    /// Forgets the calls, the equivalent of `mockClear()`. The return value is kept. For a play
    /// function's spy called while the play function adds its steps, adds a step that does so.
    public void mockClear() {
        if (isAddingSteps()) {
            builder.addStep(PlayStep.action(root -> name + ".mockClear()", root -> target().calls.clear()));
        } else {
            target().calls.clear();
        }
    }

    /// @return The spy's name.
    @Override
    public String toString() {
        return name;
    }
}
