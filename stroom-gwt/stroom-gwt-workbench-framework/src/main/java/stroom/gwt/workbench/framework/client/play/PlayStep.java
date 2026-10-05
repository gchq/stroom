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

import com.google.gwt.dom.client.Element;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/// One step of a play function, as shown in the Interactions addon. A step either does
/// something (e.g. a click or an expectation) or groups other steps (`step` and `waitFor`).
final class PlayStep {

    /// How long a `waitFor(...)` retries for by default, as in Testing Library.
    static final int DEFAULT_TIMEOUT_MILLIS = 1000;

    // The story's root element while a step runs or is described, so that values (see
    // [Query#textContent()] etc.) can find their elements. GWT is single threaded.
    private static Element currentRoot;
    private static boolean inStep;

    private final Kind kind;
    private final Function<Element, String> describer;
    private final Consumer<Element> action;
    private final List<PlayStep> children;
    private final int timeoutMillis;

    private PlayStep(final Kind kind,
                     final Function<Element, String> describer,
                     final Consumer<Element> action,
                     final List<PlayStep> children,
                     final int timeoutMillis) {
        this.kind = kind;
        this.describer = describer;
        this.action = action;
        this.children = children;
        this.timeoutMillis = timeoutMillis;
    }

    /// @param describer Describes the step, given the story's root element.
    /// @param action    Does the step, throwing a [PlayException] if it fails.
    /// @return A step that does something.
    static PlayStep action(final Function<Element, String> describer, final Consumer<Element> action) {
        return new PlayStep(Kind.ACTION, describer, action, Collections.emptyList(), DEFAULT_TIMEOUT_MILLIS);
    }

    /// @param kind      [Kind#STEP] or [Kind#WAIT_FOR].
    /// @param describer Describes the group.
    /// @param children  The steps in the group.
    /// @return A step that groups other steps.
    static PlayStep group(final Kind kind,
                          final Function<Element, String> describer,
                          final List<PlayStep> children) {
        return group(kind, describer, children, DEFAULT_TIMEOUT_MILLIS);
    }

    /// @param kind          [Kind#STEP] or [Kind#WAIT_FOR].
    /// @param describer     Describes the group.
    /// @param children      The steps in the group.
    /// @param timeoutMillis For [Kind#WAIT_FOR], how long to retry the steps for.
    /// @return A step that groups other steps.
    static PlayStep group(final Kind kind,
                          final Function<Element, String> describer,
                          final List<PlayStep> children,
                          final int timeoutMillis) {
        if (timeoutMillis < 0) {
            throw new IllegalArgumentException("The timeout must not be negative: " + timeoutMillis);
        }
        return new PlayStep(kind, describer, null, new ArrayList<>(children), timeoutMillis);
    }

    /// @param describer Describes the step.
    /// @param millis    How long to pause for.
    /// @return A step that pauses before the next step. Inside a `waitFor(...)` it does nothing.
    static PlayStep sleep(final Function<Element, String> describer, final int millis) {
        if (millis < 0) {
            throw new IllegalArgumentException("The pause must not be negative: " + millis);
        }
        return new PlayStep(Kind.SLEEP, describer, null, Collections.emptyList(), millis);
    }

    /// @return The story's root element while a step runs or is described.
    /// @throws PlayException If no step is running, e.g. a value is read while the play function
    ///                       is adding its steps.
    static Element currentRoot() {
        if (!inStep) {
            throw new PlayException("Elements can only be found while a step runs, not while the play "
                                    + "function adds its steps");
        }
        return currentRoot;
    }

    /// @return For a `waitFor(...)` group, how long to retry its steps for; for a sleep, how long
    /// to pause for.
    int getTimeoutMillis() {
        return timeoutMillis;
    }

    /// @return Whether the step does something or groups other steps.
    Kind getKind() {
        return kind;
    }

    /// @return The steps in the group, or an empty list if this step does something.
    List<PlayStep> getChildren() {
        return Collections.unmodifiableList(children);
    }

    /// @param root The story's root element.
    /// @return The description, which never throws even if the step's element can't be found.
    String describe(final Element root) {
        final Element previousRoot = currentRoot;
        final boolean previousInStep = inStep;
        currentRoot = root;
        inStep = true;
        try {
            return describer.apply(root);
        } catch (final RuntimeException e) {
            return describer.apply(null);
        } finally {
            currentRoot = previousRoot;
            inStep = previousInStep;
        }
    }

    /// Does the step. Only for [Kind#ACTION] steps; groups are run by the runner.
    ///
    /// @param root The story's root element.
    /// @throws PlayException If the step fails.
    void run(final Element root) {
        if (action == null) {
            throw new IllegalStateException("A group can't be run as a single step");
        }
        final Element previousRoot = currentRoot;
        final boolean previousInStep = inStep;
        currentRoot = root;
        inStep = true;
        try {
            action.accept(root);
        } finally {
            currentRoot = previousRoot;
            inStep = previousInStep;
        }
    }

    /// Does the step or, for a group, each of the steps in it in turn, synchronously, stopping at
    /// the first that fails. Used for the steps in a `waitFor(...)`, which are retried together.
    ///
    /// @param root     The story's root element.
    /// @param listener Told when each step, including each group, starts and passes.
    /// @throws PlayException If a step fails, or any other exception or [AssertionError] the step
    ///                       throws.
    void runAll(final Element root, final Listener listener) {
        listener.started(this);
        if (kind == Kind.ACTION) {
            run(root);
        } else {
            // A sleep has no children, so does nothing, as it can't pause synchronously
            for (final PlayStep child : children) {
                child.runAll(root, listener);
            }
        }
        listener.passed(this);
    }


    // --------------------------------------------------------------------------------


    /// Told about the progress of [PlayStep#runAll(Element, Listener)].
    interface Listener {

        /// @param step The step that is starting.
        void started(PlayStep step);

        /// @param step The step that passed.
        void passed(PlayStep step);
    }


    // --------------------------------------------------------------------------------


    /// Whether a step does something or groups other steps.
    enum Kind {
        /// Does something, e.g. a click or an expectation.
        ACTION,
        /// A named group of steps, the equivalent of Storybook's `step(...)`.
        STEP,
        /// Steps that are retried until they pass, the equivalent of `waitFor(...)`.
        WAIT_FOR,
        /// A pause before the next step.
        SLEEP
    }
}
