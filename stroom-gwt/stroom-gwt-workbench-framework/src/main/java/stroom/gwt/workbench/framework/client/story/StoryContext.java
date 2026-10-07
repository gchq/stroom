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

package stroom.gwt.workbench.framework.client.story;

import stroom.gwt.workbench.framework.client.args.Args;
import stroom.gwt.workbench.framework.client.play.Spies;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.preview.StoryActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// What a story is given when it renders: its args (as set in the Controls addon) and a way to
/// report actions or make spies, the equivalent of the args and context React Storybook passes to
/// `render`.
///
/// A context is created each time the story renders (including when the Interactions addon
/// re-runs or rewinds the play function), which starts a new generation of spies (see [Spies]):
/// the play function sees only the calls made to the spies this rendering registers, and calls
/// to spies an earlier rendering registered (e.g. from a timer it left running) are ignored.
public final class StoryContext {

    private final Story story;
    private final Args args;
    private final StoryTheme theme;
    private final List<Runnable> cleanUps = new ArrayList<>();
    // The generation of spies (see Spies) this rendering started
    private final int generation;

    /// @param story The story being rendered.
    /// @param args  The story's current args.
    public StoryContext(final Story story, final Args args) {
        this(story, args, StoryTheme.DEFAULT);
    }

    /// @param story The story being rendered.
    /// @param args  The story's current args.
    /// @param theme The theme it is shown in.
    public StoryContext(final Story story, final Args args, final StoryTheme theme) {
        this.story = Objects.requireNonNull(story);
        this.args = Objects.requireNonNull(args);
        this.theme = Objects.requireNonNull(theme);
        // A new rendering, so the play function's spies start afresh, and those of the previous
        // rendering stop recording
        this.generation = Spies.startRendering();
    }

    /// @return The theme the story is shown in (see [StoryTheme]), e.g. for the theme of the code it
    /// shows.
    public StoryTheme getTheme() {
        return theme;
    }

    /// @return The story being rendered.
    public Story getStory() {
        return story;
    }

    /// @return The story's current args.
    public Args getArgs() {
        return args;
    }

    /// Logs an action to the Actions addon, e.g. when a button is clicked. The equivalent of
    /// calling an `action` arg in React Storybook.
    ///
    /// @param name   The name of the action, e.g. `onClick`.
    /// @param detail Detail about the action, e.g. a new value, may be null.
    public void action(final String name, final String detail) {
        StoryActions.log(name, detail);
    }

    /// Registers something to undo when this rendering is replaced, i.e. before the story renders
    /// again or another story is shown, e.g. cancelling timers, pending requests or popups that
    /// the story started, so that they can't affect the next rendering.
    ///
    /// @param cleanUp What to do. Clean ups run in the reverse order they were added.
    public void addCleanUp(final Runnable cleanUp) {
        cleanUps.add(Objects.requireNonNull(cleanUp));
    }

    /// Runs the clean ups added with [#addCleanUp(Runnable)], once. Called by the preview before
    /// the story renders again. A clean up that fails doesn't stop the others running.
    ///
    /// @return The first failure, or null if all the clean ups succeeded.
    public RuntimeException cleanUp() {
        RuntimeException firstFailure = null;
        for (int i = cleanUps.size() - 1; i >= 0; i--) {
            try {
                cleanUps.get(i).run();
            } catch (final RuntimeException e) {
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }
        cleanUps.clear();
        return firstFailure;
    }

    /// Gets a spy to pass to a widget as a callback, the equivalent of an arg set to `fn()` in
    /// React Storybook. Each call is recorded, for the play function to check with
    /// `play.expect(play.spy(name)).toHaveBeenCalled()` etc., and logged to the Actions addon.
    /// Register every spy the play function checks while the story renders (as React's `fn()`
    /// args exist before the play runs), not only when it is first called: the play function's
    /// expectations fail for a spy that wasn't registered, to catch misspelt names.
    /// E.g.
    /// ```
    /// final Spy onClick = context.fn("onClick");
    /// button.addClickHandler(event -> onClick.call());
    /// widget.setChangeHandler(context.fn("onChange").asConsumer());
    /// ```
    ///
    /// Once the story has rendered again (or another context has been created), this context is
    /// stale: its `fn(name)` returns a detached spy that records nothing and logs no actions, so
    /// a callback left over from this rendering (e.g. a timer that calls `context.fn("onX")`)
    /// can't add calls to the new rendering's spies.
    ///
    /// @param name The spy's name, e.g. `onClick`, which is also the action's name.
    /// @return This rendering's spy with the name, or a detached spy if this rendering has been
    /// replaced.
    public Spy fn(final String name) {
        if (!Spies.isCurrent(generation)) {
            return Spies.register(name, generation);
        }
        return Spies.register(name, generation).logTo(StoryActions::log);
    }
}
