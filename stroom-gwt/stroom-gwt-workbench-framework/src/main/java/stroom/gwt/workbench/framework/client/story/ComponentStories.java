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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/// Builder for adding the stories of one component to a [StoryRegistry]. The equivalent of a
/// React `*.stories.tsx` file, where the title is the `meta.title` and each story is an export:
/// ```
/// registry.component("Widgets/Buttons/Button", ButtonStories.class)
///         .layout(StoryLayout.CENTERED)
///         .argType(ArgType.text("text").description("Button label text."))
///         .args(Args.of("text", "Click me"))
///         .story("Default", context -> button(context.getArgs()))
///         .story("Loading", context -> button(context.getArgs()))
///         .withArgs(Args.of("loading", true))
///         .withPlay(play -> play.click(play.getByRole("button")));
/// ```
///
/// Settings such as [#layout(StoryLayout)], [#argType(ArgType)] and [#args(Args)] apply to the
/// stories added after them. The `with...` methods apply to the story added last.
public class ComponentStories {

    private final StoryRegistry registry;
    private final String title;
    private final String sourceClassName;
    private final List<ArgType> argTypes = new ArrayList<>();
    private final List<String> tags = new ArrayList<>();
    private StoryLayout layout = StoryLayout.PADDED;
    private Args args = Args.empty();
    private Story lastStory;

    /// @param registry        The registry to add the stories to.
    /// @param title           The title of the component, e.g. `Widgets/Buttons/Button`.
    /// @param sourceClassName The fully qualified name of the class defining the stories, or null.
    ComponentStories(final StoryRegistry registry, final String title, final String sourceClassName) {
        this.registry = registry;
        this.title = title;
        this.sourceClassName = sourceClassName;
    }

    /// Sets the layout for the stories added after this call. Defaults to [StoryLayout#PADDED].
    ///
    /// @param layout The layout.
    /// @return This builder.
    public ComponentStories layout(final StoryLayout layout) {
        this.layout = Objects.requireNonNull(layout);
        return this;
    }

    /// Adds an arg that the Controls addon shows for the stories added after this call, the
    /// equivalent of an entry in `meta.argTypes`.
    ///
    /// @param argType The arg.
    /// @return This builder.
    public ComponentStories argType(final ArgType argType) {
        Objects.requireNonNull(argType);
        argTypes.removeIf(existing -> existing.getName().equals(argType.getName()));
        argTypes.add(argType);
        return this;
    }

    /// Sets the initial arg values for the stories added after this call, the equivalent of
    /// `meta.args`.
    ///
    /// @param args The values, merged with any set before.
    /// @return This builder.
    public ComponentStories args(final Args args) {
        this.args = this.args.merge(Objects.requireNonNull(args));
        return this;
    }

    /// Adds tags to the stories added after this call, the equivalent of `meta.tags`.
    ///
    /// @param tags The tags.
    /// @return This builder.
    public ComponentStories tags(final String... tags) {
        this.tags.addAll(Arrays.asList(tags));
        return this;
    }

    /// Adds a story whose name is derived from its export name, as Storybook does.
    ///
    /// @param exportName The name the story would be exported as in React, e.g. `WithIcons`.
    /// @param renderer   Creates the story's widget.
    /// @return This builder.
    /// @throws IllegalArgumentException If the title or export name has no letters or digits.
    public ComponentStories story(final String exportName, final StoryRenderer renderer) {
        return story(exportName, StoryIds.storyNameFromExport(exportName), renderer);
    }

    /// Adds a story with an explicit display name, equivalent to setting `name` on a React story.
    /// The id is still derived from the export name, as Storybook does.
    ///
    /// @param exportName The name the story would be exported as in React, e.g. `DialogClose`.
    /// @param name       The display name, e.g. `Dialog — Close button`.
    /// @param renderer   Creates the story's widget.
    /// @return This builder.
    /// @throws IllegalArgumentException If the title or export name has no letters or digits.
    public ComponentStories story(final String exportName,
                                  final String name,
                                  final StoryRenderer renderer) {
        final String id = StoryIds.storyId(title, StoryIds.storyNameFromExport(exportName));
        final Story story = new Story(title, name, id, layout, renderer, argTypes, args, sourceClassName);
        story.addTags(tags);
        registry.add(story);
        lastStory = story;
        return this;
    }

    /// Sets arg values for the story added last, the equivalent of a story's `args`.
    ///
    /// @param storyArgs The values, merged with the component's.
    /// @return This builder.
    public ComponentStories withArgs(final Args storyArgs) {
        getLastStory().setInitialArgs(getLastStory().getInitialArgs().merge(storyArgs));
        return this;
    }

    /// Sets the play function of the story added last, the equivalent of a story's `play`.
    ///
    /// @param play The play function.
    /// @return This builder.
    public ComponentStories withPlay(final PlayFunction play) {
        getLastStory().setPlay(play);
        return this;
    }

    /// Adds tags to the story added last, the equivalent of a story's `tags`.
    ///
    /// @param storyTags The tags.
    /// @return This builder.
    public ComponentStories withTags(final String... storyTags) {
        getLastStory().addTags(Arrays.asList(storyTags));
        return this;
    }

    private Story getLastStory() {
        if (lastStory == null) {
            throw new IllegalStateException("Add a story before calling with...()");
        }
        return lastStory;
    }
}
