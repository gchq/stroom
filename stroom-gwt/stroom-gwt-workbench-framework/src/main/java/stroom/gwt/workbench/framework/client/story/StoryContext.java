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

import java.util.Objects;

/// What a story is given when it renders: its args (as set in the Controls addon) and a way to
/// report actions or make spies, the equivalent of the args and context React Storybook passes to
/// `render`.
///
/// A context is created each time the story renders (including when the Interactions addon
/// re-runs or rewinds the play function), which clears the calls of the story's spies.
public final class StoryContext {

    private final Story story;
    private final Args args;

    /// @param story The story being rendered.
    /// @param args  The story's current args.
    public StoryContext(final Story story, final Args args) {
        this.story = Objects.requireNonNull(story);
        this.args = Objects.requireNonNull(args);
        // A new rendering, so the play function's spies start afresh
        Spies.clearAll();
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

    /// Gets a spy to pass to a widget as a callback, the equivalent of an arg set to `fn()` in
    /// React Storybook. Each call is recorded, for the play function to check with
    /// `play.expect(play.spy(name)).toHaveBeenCalled()` etc., and logged to the Actions addon.
    /// E.g.
    /// ```
    /// final Spy onClick = context.fn("onClick");
    /// button.addClickHandler(event -> onClick.call());
    /// widget.setChangeHandler(context.fn("onChange").asConsumer());
    /// ```
    ///
    /// @param name The spy's name, e.g. `onClick`, which is also the action's name.
    /// @return The story's spy with the name.
    public Spy fn(final String name) {
        return Spies.get(name).logTo(StoryActions::log);
    }
}
