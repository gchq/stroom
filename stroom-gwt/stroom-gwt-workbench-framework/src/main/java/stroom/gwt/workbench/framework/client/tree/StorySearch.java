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

import stroom.gwt.workbench.framework.client.tree.StoryTreeNode.Type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// Finds the components and stories matching a search, for the sidebar's search results list.
///
/// Like Storybook, matching is fuzzy: the characters of the query must appear in order in the
/// name (or the path) but needn't be adjacent. Closer matches score higher, e.g. a match at the
/// start of the name beats one in the middle, which beats a scattered match.
public final class StorySearch {

    /// The most results to show, as in Storybook.
    public static final int MAX_RESULTS = 50;

    private static final int SCORE_EXACT = 1000;
    private static final int SCORE_PREFIX = 800;
    private static final int SCORE_SUBSTRING = 600;
    private static final int SCORE_FUZZY = 400;
    private static final int SCORE_PATH = 100;
    private static final int SCORE_WORD_START = 10;
    private static final int SCORE_ADJACENT = 5;
    private static final String PATH_SEPARATOR = "/";

    private StorySearch() {
        // Static utility
    }

    /// @param topLevelNodes The top level nodes of the tree.
    /// @param query         The text typed into the search box.
    /// @return The matching nodes, best first, or an empty list if the query is blank.
    public static List<SearchResult> search(final List<StoryTreeNode> topLevelNodes,
                                            final String query) {
        final List<SearchResult> results = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) {
            return results;
        }

        final String lowerCaseQuery = query.trim().toLowerCase();
        for (final StoryTreeNode node : topLevelNodes) {
            collectMatches(node, lowerCaseQuery, results);
        }

        // Stable sort so equal scores keep tree order, with components before stories
        results.sort((a, b) -> {
            final int byScore = Integer.compare(b.score, a.score);
            if (byScore != 0) {
                return byScore;
            }
            return Boolean.compare(
                    a.node.getType() != Type.COMPONENT,
                    b.node.getType() != Type.COMPONENT);
        });
        return results.size() > MAX_RESULTS
                ? new ArrayList<>(results.subList(0, MAX_RESULTS))
                : results;
    }

    private static void collectMatches(final StoryTreeNode node,
                                       final String lowerCaseQuery,
                                       final List<SearchResult> results) {
        if (node.getType() == Type.COMPONENT || node.getType() == Type.STORY) {
            final SearchResult result = match(node, lowerCaseQuery);
            if (result != null) {
                results.add(result);
            }
        }
        for (final StoryTreeNode child : node.getChildren()) {
            collectMatches(child, lowerCaseQuery, results);
        }
    }

    private static SearchResult match(final StoryTreeNode node, final String lowerCaseQuery) {
        final String name = node.getName().toLowerCase();

        if (name.equals(lowerCaseQuery)) {
            return new SearchResult(node, SCORE_EXACT, range(0, name.length()));
        }
        final int index = name.indexOf(lowerCaseQuery);
        if (index == 0) {
            return new SearchResult(node, SCORE_PREFIX, range(0, lowerCaseQuery.length()));
        } else if (index > 0) {
            return new SearchResult(node, SCORE_SUBSTRING - index,
                    range(index, index + lowerCaseQuery.length()));
        }

        final FuzzyMatch nameMatch = fuzzyMatch(node.getName(), lowerCaseQuery);
        if (nameMatch != null) {
            return new SearchResult(node, SCORE_FUZZY + nameMatch.score, nameMatch.positions);
        }

        // Match against the path, e.g. 'inputs tick' finds Widgets/Inputs/TickBox
        final String path = String.join(PATH_SEPARATOR, getAncestorNames(node))
                            + PATH_SEPARATOR + node.getName();
        if (fuzzyMatch(path, lowerCaseQuery) != null) {
            return new SearchResult(node, SCORE_PATH, Collections.emptyList());
        }
        return null;
    }

    /// Finds the best positions in the text of each character of the query, in order, or returns
    /// null if the text doesn't contain them all in order. 'Best' favours characters at the start
    /// of words and characters next to each other, e.g. `wic` matches the `W` and `Ic` of
    /// `With Icons` rather than the `Wi` and `c`.
    private static FuzzyMatch fuzzyMatch(final String text, final String lowerCaseQuery) {
        final String query = lowerCaseQuery.replace(" ", "");
        if (query.isEmpty()) {
            return null;
        }
        final String lowerCaseText = text.toLowerCase();
        final int textLength = text.length();
        // best[i][j] is the best score matching query[0..i] with query[i] at text[j], or -1
        final int[][] best = new int[query.length()][textLength];
        final int[][] previous = new int[query.length()][textLength];

        for (int i = 0; i < query.length(); i++) {
            for (int j = 0; j < textLength; j++) {
                best[i][j] = -1;
                if (lowerCaseText.charAt(j) != query.charAt(i)) {
                    continue;
                }
                final int bonus = isWordStart(text, j)
                        ? SCORE_WORD_START
                        : 0;
                if (i == 0) {
                    best[i][j] = bonus;
                    continue;
                }
                for (int k = 0; k < j; k++) {
                    if (best[i - 1][k] >= 0) {
                        final int score = best[i - 1][k] + bonus + (k == j - 1
                                ? SCORE_ADJACENT
                                : 0);
                        if (score > best[i][j]) {
                            best[i][j] = score;
                            previous[i][j] = k;
                        }
                    }
                }
            }
        }

        final int last = query.length() - 1;
        int bestEnd = -1;
        for (int j = 0; j < textLength; j++) {
            if (best[last][j] >= 0 && (bestEnd < 0 || best[last][j] > best[last][bestEnd])) {
                bestEnd = j;
            }
        }
        if (bestEnd < 0) {
            return null;
        }

        final List<Integer> positions = new ArrayList<>();
        int position = bestEnd;
        for (int i = last; i >= 0; i--) {
            positions.add(0, position);
            position = previous[i][position];
        }
        return new FuzzyMatch(best[last][bestEnd], positions);
    }

    private static boolean isWordStart(final String text, final int index) {
        return index == 0
               || text.charAt(index - 1) == ' '
               || text.charAt(index - 1) == '/'
               || Character.isUpperCase(text.charAt(index));
    }

    private static List<Integer> range(final int start, final int end) {
        final List<Integer> positions = new ArrayList<>();
        for (int i = start; i < end; i++) {
            positions.add(i);
        }
        return positions;
    }

    /// @param node A node.
    /// @return The names of the node's ancestors, e.g. `[Widgets, Inputs]` for `TickBox`.
    public static List<String> getAncestorNames(final StoryTreeNode node) {
        final List<String> names = new ArrayList<>();
        StoryTreeNode ancestor = node.getParent();
        while (ancestor != null) {
            names.add(0, ancestor.getName());
            ancestor = ancestor.getParent();
        }
        return names;
    }


    // --------------------------------------------------------------------------------


    /// A node that matches a search, how well, and which characters of its name matched.
    public static final class SearchResult {

        private final StoryTreeNode node;
        private final int score;
        private final List<Integer> matchedPositions;

        /// @param node             The matching node.
        /// @param score            How well the node matched, higher is better.
        /// @param matchedPositions The indexes of the characters in the node's name that matched.
        SearchResult(final StoryTreeNode node, final int score, final List<Integer> matchedPositions) {
            this.node = node;
            this.score = score;
            this.matchedPositions = matchedPositions;
        }

        /// @return The matching node.
        public StoryTreeNode getNode() {
            return node;
        }

        /// @return How well the node matched, higher is better.
        public int getScore() {
            return score;
        }

        /// @return The indexes of the characters in the node's name that matched, in order. Empty
        /// if only the path matched.
        public List<Integer> getMatchedPositions() {
            return Collections.unmodifiableList(matchedPositions);
        }

        /// @param index An index of a character in the node's name.
        /// @return True if the character matched the search.
        public boolean isMatched(final int index) {
            return matchedPositions.contains(index);
        }
    }


    // --------------------------------------------------------------------------------


    private static final class FuzzyMatch {

        private final int score;
        private final List<Integer> positions;

        private FuzzyMatch(final int score, final List<Integer> positions) {
            this.score = score;
            this.positions = positions;
        }
    }
}
