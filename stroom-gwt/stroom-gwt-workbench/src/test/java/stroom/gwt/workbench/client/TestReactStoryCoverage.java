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
/// only fails on mistakes, e.g. an invalid status file, not on stories still to port or GWT-only
/// stories.
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

    /// Logs the coverage report. Workbench stories may be GWT-only, so a story that isn't in the
    /// React Storybook is listed in the report rather than failing the test.
    @Test
    void testWorkbenchStoriesMayBeGwtOnly() {
        final ReactStoryCoverage coverage = ReactStoryCoverage.load();
        LOGGER.info("\n{}", coverage.report(null));

        assertThat(coverage.report(null))
                .contains("Workbench stories: ");
    }

    /// Checks that react-story-status.json has no problems, e.g. a key matching no React story.
    @Test
    void testStatusFile() {
        assertThat(ReactStoryCoverage.load().getStatusProblems())
                .as("Problems with react-story-status.json")
                .isEmpty();
    }

    /// Checks that the manifest has unique ids and that every React story is counted exactly once.
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
        // Every workbench story with a React id is counted as ported, exactly once
        final int workbenchStoryCount = AllStories.create().getStories().size();
        assertThat(total.getPorted())
                .isEqualTo(workbenchStoryCount - coverage.getUnknownGwtStoryIds().size());
    }

    /// Checks that workbench stories whose ids aren't React ones (e.g. a typo) are reported.
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

    /// Checks each React story's status, the most specific status entry winning.
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

    /// Checks the problems reported for bad status entries.
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

    /// Checks the warnings for e.g. a display name that differs from the React story's.
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

    /// Checks the counts of each group's stories, in sidebar order.
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

    /// Checks the coverage report, including the list of the remaining stories.
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
                .contains("Workbench stories: 2, not in the React Storybook (GWT-only, or a misnamed port): 1\n"
                          + "  widgets-buttons-button--typo")
                .contains("Remaining stories matching 'widgets-buttons' (2):\n"
                          + "  widgets-buttons-button--dialog-close  (./Button.stories.tsx, has play)\n"
                          + "  widgets-buttons-iconbutton--default  (./IconButton.stories.tsx, has play)\n")
                .doesNotContain("widgets-glass--default  (");
        assertThat(report.lines().filter(line -> line.startsWith("Total")))
                .singleElement()
                .satisfies(line -> assertThat(line.split("\\s+"))
                        .containsExactly("Total", "6", "1", "0", "0", "5", "16%", "4", "0"));
    }

    /// Checks that the workbench's top level groups are registered in the React sidebar's order.
    @Test
    void testAllStoriesAreInGroupOrder() {
        // The top level groups are registered in the React sidebar's order
        final List<String> groups = AllStories.create()
                .getStories()
                .stream()
                .map(Story::getTitle)
                .map(TestReactStoryCoverage::topLevelGroup)
                .distinct()
                .toList();
        assertThat(List.of("App", "Screens", "Widgets")).containsSubsequence(groups);
    }

    /// Checks `topLevelGroup`, including for a title without a group.
    @Test
    void testTopLevelGroup() {
        assertThat(topLevelGroup("Widgets/Buttons/Button")).isEqualTo("Widgets");
        // A title without a group used to throw StringIndexOutOfBoundsException
        assertThat(topLevelGroup("Introduction")).isEqualTo("Introduction");
    }

    private static String topLevelGroup(final String title) {
        final int slash = title.indexOf('/');
        return slash < 0
                ? title
                : title.substring(0, slash);
    }
}
