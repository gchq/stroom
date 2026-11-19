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

package stroom.gwt.workbench.framework.client.tree;

import stroom.gwt.workbench.framework.client.story.ComponentStories;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.gwt.workbench.framework.client.story.StoryRenderer;
import stroom.gwt.workbench.framework.client.tree.StorySearch.SearchResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestStorySearch {

    private static final StoryRenderer NO_WIDGET = context -> null;

    private List<StoryTreeNode> topLevel;

    @BeforeEach
    void setUp() {
        final StoryRegistry registry = new StoryRegistry();
        registry.component("Widgets/Inputs/TickBox")
                .story("Basic", NO_WIDGET);
        registry.component("Widgets/Cell Renderers/DocumentTypeCell")
                .story("WithTickBoxes", NO_WIDGET);
        registry.component("Widgets/Buttons/Button")
                .story("Default", NO_WIDGET)
                .story("WithIcons", NO_WIDGET);
        topLevel = StoryTreeBuilder.build(registry.getStories());
    }

    @Test
    void testSearch_substring() {
        final List<SearchResult> results = StorySearch.search(topLevel, " TICK ");

        // Prefix matches beat matches further into the name
        assertThat(results)
                .extracting(result -> result.getNode().getId())
                .startsWith(
                        "widgets-inputs-tickbox",
                        "widgets-cell-renderers-documenttypecell--with-tick-boxes");
        assertThat(results.get(0).getMatchedPositions()).containsExactly(0, 1, 2, 3);
        assertThat(results.get(1).getMatchedPositions()).containsExactly(5, 6, 7, 8);
    }

    @Test
    void testSearch_exactBeatsPrefix() {
        final List<SearchResult> results = StorySearch.search(topLevel, "button");
        assertThat(results.get(0).getNode().getId()).isEqualTo("widgets-buttons-button");
        assertThat(results.get(0).getScore()).isGreaterThan(0);
    }

    @Test
    void testSearch_fuzzy() {
        // W(ith) I(cons) - characters in order but not adjacent
        final List<SearchResult> results = StorySearch.search(topLevel, "wic");
        assertThat(results.get(0).getNode().getId()).isEqualTo("widgets-buttons-button--with-icons");
        assertThat(results.get(0).isMatched(0)).isTrue();
        assertThat(results.get(0).isMatched(5)).isTrue();
        assertThat(results.get(0).isMatched(1)).isFalse();
    }

    @Test
    void testSearch_path() {
        // Matches the path Widgets/Inputs/TickBox rather than a name
        final List<SearchResult> results = StorySearch.search(topLevel, "inputs basic");
        assertThat(results)
                .extracting(result -> result.getNode().getId())
                .contains("widgets-inputs-tickbox--basic");
    }

    @Test
    void testSearch_noMatches() {
        assertThat(StorySearch.search(topLevel, "zzz")).isEmpty();
    }

    @Test
    void testSearch_groupsAndRootsNotReturned() {
        assertThat(StorySearch.search(topLevel, "Cell Renderers"))
                .extracting(result -> result.getNode().getType())
                .doesNotContain(StoryTreeNode.Type.GROUP, StoryTreeNode.Type.ROOT);
    }

    @Test
    void testSearch_maxResults() {
        final StoryRegistry registry = new StoryRegistry();
        final ComponentStories component = registry.component("Widgets/Many");
        for (int i = 0; i < StorySearch.MAX_RESULTS + 10; i++) {
            component.story("Story" + i, NO_WIDGET);
        }
        assertThat(StorySearch.search(StoryTreeBuilder.build(registry.getStories()), "story"))
                .hasSize(StorySearch.MAX_RESULTS);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void testSearch_blank(final String query) {
        assertThat(StorySearch.search(topLevel, query)).isEmpty();
    }

    @Test
    void testGetAncestorNames() {
        final SearchResult result = StorySearch.search(topLevel, "DocumentTypeCell").get(0);
        assertThat(StorySearch.getAncestorNames(result.getNode()))
                .containsExactly("Widgets", "Cell Renderers");
        assertThat(StorySearch.getAncestorNames(topLevel.get(0))).isEmpty();
    }
}
