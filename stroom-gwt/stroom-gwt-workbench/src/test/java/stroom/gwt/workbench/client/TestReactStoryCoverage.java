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

package stroom.gwt.workbench.client;

import stroom.gwt.workbench.client.ReactStoryCoverage.GroupCounts;
import stroom.gwt.workbench.client.ReactStoryCoverage.ReactStory;
import stroom.gwt.workbench.client.ReactStoryCoverage.StatusEntry;
import stroom.gwt.workbench.framework.client.story.Story;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.gwt.workbench.framework.client.story.StoryRenderer;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// Checks the workbench's stories against the React Storybook's (see [ReactStoryCoverage]). It
/// only fails on mistakes, e.g. a story whose id isn't a React one, not on stories still to port.
class TestReactStoryCoverage {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestReactStoryCoverage.class);

    private static final StoryRenderer NO_WIDGET = context -> null;

    private static final List<ReactStory> REACT_STORIES = List.of(
            new ReactStory("widgets-buttons-button--default", "Widgets/Buttons/Button", "Default",
                    "Default", "./Button.stories.tsx", false),
            new ReactStory("widgets-buttons-button--dialog-close", "Widgets/Buttons/Button",
                    "Dialog — Close button", "DialogClose", "./Button.stories.tsx", true),
            new ReactStory("widgets-buttons-iconbutton--default", "Widgets/Buttons/IconButton", "Default",
                    "Default", "./IconButton.stories.tsx", true),
            new ReactStory("widgets-glass--default", "Widgets/Glass", "Default",
                    "Default", "./Glass.stories.tsx", false),
            new ReactStory("app-ai-chat--default", "App/AI/Chat", "Default",
                    "Default", "./Chat.stories.tsx", true),
            new ReactStory("app-ai-chat--streaming", "App/AI/Chat", "Streaming",
                    "Streaming", "./Chat.stories.tsx", true));

    @Test
    void testWorkbenchStoriesAreReactStories() {
        final ReactStoryCoverage coverage = ReactStoryCoverage.load();
        LOGGER.info("\n{}", coverage.report(null));

        // A story that isn't in the React Storybook has the wrong title or export name, so can't
        // be compared with its React original. If the React story is new, regenerate the manifest
        // with test-runner/react-manifest.mjs.
        assertThat(coverage.getUnknownGwtStoryIds())
                .as("Workbench stories whose ids aren't in react-stories.json")
                .isEmpty();
    }

    @Test
    void testStatusFile() {
        assertThat(ReactStoryCoverage.load().getStatusProblems())
                .as("Problems with react-story-status.json")
                .isEmpty();
    }

    @Test
    void testManifest() {
        final ReactStoryCoverage coverage = ReactStoryCoverage.load();

        assertThat(coverage.getReactStories()).isNotEmpty();
        // The registry would throw if the workbench had duplicate ids, so check the manifest too
        assertThat(coverage.getReactStoryIds()).hasSize(coverage.getReactStories().size());
        // Every story is accounted for as ported, n/a, blocked or todo
        final GroupCounts total = coverage.getGroupCounts().getLast();
        assertThat(total.getName()).isEqualTo("Total");
        assertThat(total.getReact()).isEqualTo(coverage.getReactStories().size());
        assertThat(total.getPorted() + total.getNotApplicable() + total.getBlocked() + total.todo())
                .isEqualTo(total.getReact());
        // The stories ported so far
        assertThat(total.getPorted()).isGreaterThanOrEqualTo(AllStories.create().getStories().size()
                                                             - coverage.getUnknownGwtStoryIds().size());
    }

    @Test
    void testUnknownIds() {
        final StoryRegistry registry = new StoryRegistry();
        registry.component("Widgets/Buttons/Button")
                .story("Default", NO_WIDGET)
                // Not a React story
                .story("Primary", NO_WIDGET);
        // A typo in the title
        registry.component("Widgets/Button/IconButton")
                .story("Default", NO_WIDGET);

        final ReactStoryCoverage coverage = new ReactStoryCoverage("test", REACT_STORIES, Map.of(), registry);

        assertThat(coverage.getUnknownGwtStoryIds()).containsExactly(
                "widgets-buttons-button--primary",
                "widgets-button-iconbutton--default");
    }

    @Test
    void testStatuses() {
        final StoryRegistry registry = new StoryRegistry();
        registry.component("Widgets/Buttons/Button")
                .story("Default", NO_WIDGET);

        final ReactStoryCoverage coverage = new ReactStoryCoverage("test", REACT_STORIES, Map.of(
                "widgets-buttons-button--dialog-close", new StatusEntry("blocked", "Needs the dialog"),
                "App/AI", new StatusEntry("n/a", "No AI in GWT"),
                "app-ai-chat--streaming", new StatusEntry("blocked", "Most specific wins")), registry);

        assertThat(coverage.getReactStories())
                .extracting(coverage::statusOf)
                .containsExactly("ported", "blocked", "todo", "todo", "n/a", "blocked");
        assertThat(coverage.getStatusProblems()).isEmpty();
        assertThat(coverage.getWarnings()).isEmpty();
    }

    @Test
    void testStatusProblems() {
        final ReactStoryCoverage coverage = new ReactStoryCoverage("test", REACT_STORIES, Map.of(
                "widgets-buttons-button--typo", new StatusEntry("n/a", "Reason"),
                "Widgets/Buttons/Butt", new StatusEntry("n/a", "A title must match whole parts"),
                "widgets-glass--default", new StatusEntry("ported", "Not a status"),
                "Widgets/Buttons/IconButton", new StatusEntry("blocked", " ")), new StoryRegistry());

        assertThat(coverage.getStatusProblems()).containsExactlyInAnyOrder(
                "'widgets-buttons-button--typo' is not the id or title of any React story",
                "'Widgets/Buttons/Butt' is not the id or title of any React story",
                "'widgets-glass--default' has status 'ported', which should be 'n/a' or 'blocked'",
                "'Widgets/Buttons/IconButton' has no reason");
    }

    @Test
    void testWarnings() {
        final StoryRegistry registry = new StoryRegistry();
        registry.component("Widgets/Buttons/Button")
                // Should have the name 'Dialog — Close button'
                .story("DialogClose", NO_WIDGET);
        registry.component("Widgets/Glass")
                .story("Default", NO_WIDGET);

        final ReactStoryCoverage coverage = new ReactStoryCoverage("test", REACT_STORIES, Map.of(
                "Widgets/Glass", new StatusEntry("n/a", "Ported anyway")), registry);

        assertThat(coverage.getWarnings()).containsExactly(
                "widgets-buttons-button--dialog-close: name 'Dialog Close' should be 'Dialog — Close button' "
                + "(use story(exportName, name, renderer))",
                "widgets-glass--default is ported but marked 'n/a' in react-story-status.json");
        // Ported wins over the status
        assertThat(coverage.statusOf(REACT_STORIES.get(3))).isEqualTo("ported");
    }

    @Test
    void testGroupCounts() {
        final StoryRegistry registry = new StoryRegistry();
        registry.component("Widgets/Buttons/Button")
                .story("Default", NO_WIDGET)
                .story("DialogClose", "Dialog — Close button", NO_WIDGET)
                .withPlay(play -> {
                });
        registry.component("Widgets/Buttons/IconButton")
                // Ported without its play function
                .story("Default", NO_WIDGET);

        final ReactStoryCoverage coverage = new ReactStoryCoverage("test", REACT_STORIES, Map.of(
                "App/AI/Chat", new StatusEntry("n/a", "No AI")), registry);
        final List<GroupCounts> counts = coverage.getGroupCounts();

        // Top level groups and their children in sidebar order. Widgets/Glass has no child
        // group, so is only counted in Widgets.
        assertThat(counts)
                .extracting(GroupCounts::getName)
                .containsExactly("Widgets", "Widgets/Buttons", "App", "App/AI", "Total");
        assertThat(counts)
                .extracting(GroupCounts::getReact)
                .containsExactly(4, 3, 2, 2, 6);
        assertThat(counts)
                .extracting(GroupCounts::getPorted)
                .containsExactly(3, 3, 0, 0, 3);
        assertThat(counts)
                .extracting(GroupCounts::getNotApplicable)
                .containsExactly(0, 0, 2, 2, 2);
        assertThat(counts)
                .extracting(GroupCounts::todo)
                .containsExactly(1, 0, 0, 0, 1);
        assertThat(counts)
                .extracting(GroupCounts::getPlay)
                .containsExactly(2, 2, 2, 2, 4);
        assertThat(counts)
                .extracting(GroupCounts::getPlayPorted)
                .containsExactly(1, 1, 0, 0, 1);
        assertThat(counts)
                .extracting(GroupCounts::percentDone)
                .containsExactly(75, 100, 100, 100, 83);
    }

    @Test
    void testReport() {
        final StoryRegistry registry = new StoryRegistry();
        registry.component("Widgets/Buttons/Button")
                .story("Default", NO_WIDGET)
                .story("Typo", NO_WIDGET);

        final String report = new ReactStoryCoverage("test", REACT_STORIES, Map.of(), registry)
                .report("widgets-buttons");

        assertThat(report)
                .contains("6 React stories (test)")
                .contains("Workbench stories: 2, not in the React Storybook: 1\n  widgets-buttons-button--typo")
                .contains("Remaining stories matching 'widgets-buttons' (2):\n"
                          + "  widgets-buttons-button--dialog-close  (./Button.stories.tsx, has play)\n"
                          + "  widgets-buttons-iconbutton--default  (./IconButton.stories.tsx, has play)\n")
                .doesNotContain("widgets-glass--default  (");
        assertThat(report.lines().filter(line -> line.startsWith("Total")))
                .singleElement()
                .satisfies(line -> assertThat(line.split("\\s+"))
                        .containsExactly("Total", "6", "1", "0", "0", "5", "16%", "4", "0"));
    }

    @Test
    void testAllStoriesAreInGroupOrder() {
        // The top level groups are registered in the React sidebar's order
        final List<String> groups = AllStories.create()
                .getStories()
                .stream()
                .map(Story::getTitle)
                .map(title -> title.substring(0, title.indexOf('/')))
                .distinct()
                .toList();
        assertThat(List.of("App", "Screens", "Widgets")).containsSubsequence(groups);
    }
}
