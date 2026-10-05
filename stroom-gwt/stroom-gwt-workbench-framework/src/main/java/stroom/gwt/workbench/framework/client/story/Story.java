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

import stroom.gwt.workbench.framework.client.args.ArgType;
import stroom.gwt.workbench.framework.client.args.Args;
import stroom.gwt.workbench.framework.client.play.PlayFunction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// A single story, i.e. one rendering of a component in a particular state.
public final class Story {

    /// The tag Storybook gives to stories with a play function.
    public static final String PLAY_TAG = "play-fn";

    private final String title;
    private final String name;
    private final String id;
    private final StoryLayout layout;
    private final StoryRenderer renderer;
    private final List<ArgType> argTypes;
    private final String sourceClassName;
    private Args initialArgs;
    private PlayFunction play;
    private final Set<String> tags = new LinkedHashSet<>();

    /// @param title    The title of the component, e.g. `Widgets/Buttons/Button`.
    /// @param name     The display name of the story, e.g. `With Icons`.
    /// @param id       The unique id of the story, e.g. `widgets-buttons-button--with-icons`.
    /// @param layout   How the story is laid out in the canvas.
    /// @param renderer Creates the story's widget.
    public Story(final String title,
                 final String name,
                 final String id,
                 final StoryLayout layout,
                 final StoryRenderer renderer) {
        this(title, name, id, layout, renderer, Collections.emptyList(), Args.empty(), null);
    }

    /// @param title           The title of the component, e.g. `Widgets/Buttons/Button`.
    /// @param name            The display name of the story, e.g. `With Icons`.
    /// @param id              The unique id of the story, e.g. `widgets-buttons-button--with-icons`.
    /// @param layout          How the story is laid out in the canvas.
    /// @param renderer        Creates the story's widget.
    /// @param argTypes        The args the Controls addon shows, in order.
    /// @param initialArgs     The values of the args before the user changes them.
    /// @param sourceClassName The fully qualified name of the class defining the story, or null.
    Story(final String title,
          final String name,
          final String id,
          final StoryLayout layout,
          final StoryRenderer renderer,
          final List<ArgType> argTypes,
          final Args initialArgs,
          final String sourceClassName) {
        this.title = Objects.requireNonNull(title);
        this.name = Objects.requireNonNull(name);
        this.id = Objects.requireNonNull(id);
        this.layout = Objects.requireNonNull(layout);
        this.renderer = Objects.requireNonNull(renderer);
        this.argTypes = Collections.unmodifiableList(new ArrayList<>(argTypes));
        this.initialArgs = Objects.requireNonNull(initialArgs);
        this.sourceClassName = sourceClassName;
    }

    /// @return The title of the component, e.g. `Widgets/Buttons/Button`.
    public String getTitle() {
        return title;
    }

    /// @return The display name of the story, e.g. `With Icons`.
    public String getName() {
        return name;
    }

    /// @return The unique id of the story, e.g. `widgets-buttons-button--with-icons`.
    public String getId() {
        return id;
    }

    /// @return How the story is laid out in the canvas.
    public StoryLayout getLayout() {
        return layout;
    }

    /// @return The renderer that creates the story's widget.
    public StoryRenderer getRenderer() {
        return renderer;
    }

    /// @return The args the Controls addon shows, in order.
    public List<ArgType> getArgTypes() {
        return argTypes;
    }

    /// @param name The name of an arg.
    /// @return The arg's type, or null if there isn't one.
    public ArgType getArgType(final String name) {
        for (final ArgType argType : argTypes) {
            if (argType.getName().equals(name)) {
                return argType;
            }
        }
        return null;
    }

    /// @return The values of the args before the user changes them.
    public Args getInitialArgs() {
        return initialArgs;
    }

    /// @return The play function, or null if the story doesn't have one.
    public PlayFunction getPlay() {
        return play;
    }

    /// @return The story's tags, e.g. `play-fn`, as used by the sidebar's tag filter.
    public Set<String> getTags() {
        return Collections.unmodifiableSet(tags);
    }

    /// @return The fully qualified name of the class that defines the story, for 'Open in editor',
    /// or null if not known.
    public String getSourceClassName() {
        return sourceClassName;
    }

    /// @param initialArgs The values of the args before the user changes them.
    void setInitialArgs(final Args initialArgs) {
        this.initialArgs = Objects.requireNonNull(initialArgs);
    }

    /// Sets the play function and adds the [#PLAY_TAG] tag, as Storybook does.
    ///
    /// @param play The play function.
    void setPlay(final PlayFunction play) {
        this.play = Objects.requireNonNull(play);
        tags.add(PLAY_TAG);
    }

    /// @param newTags Tags to add to the story.
    void addTags(final List<String> newTags) {
        tags.addAll(newTags);
    }

    @Override
    public String toString() {
        return id;
    }
}
