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

package stroom.ai.impl.tools;

import stroom.ai.impl.tools.Leiden.Link;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// The Leiden clustering ([Leiden]) the activity timeline's anchors are found by, the chart analytics' own method:
/// clusters of nodes more linked to each other than to the rest, the same each time, whatever the links' order,
/// never in pieces.
class TestLeiden {

    @Test
    void twoGroupsJoinedByOneWeakLinkAreTwoClusters() {
        final List<String> names = List.of("a", "b", "c", "d", "e", "f");
        final List<Link> links = new ArrayList<>(clique("a", "b", "c"));
        links.addAll(clique("d", "e", "f"));
        links.add(new Link("c", "d", 0.5));

        final int[] clusters = Leiden.clusters(names, links);

        assertThat(clusters).containsExactly(0, 0, 0, 1, 1, 1);
        assertThat(Leiden.modularity(names, links, clusters)).isBetween(0.4, 0.5);
    }

    @Test
    void theSameNetworkGivesTheSameClustersWhateverTheLinksOrder() {
        final List<String> names = new ArrayList<>();
        final List<Link> links = new ArrayList<>();
        for (int group = 0; group < 4; group++) {
            final List<String> members = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                members.add("n" + group + "-" + i);
            }
            names.addAll(members);
            links.addAll(clique(members.toArray(String[]::new)));
            if (group > 0) {
                links.add(new Link("n" + (group - 1) + "-0", "n" + group + "-0", 1));
            }
        }
        final int[] first = Leiden.clusters(names, links);
        final List<Link> shuffled = new ArrayList<>(links);
        Collections.reverse(shuffled);

        assertThat(Leiden.clusters(names, shuffled)).containsExactly(first);
        assertThat(Arrays.stream(first).distinct().count()).isEqualTo(4);
        for (int group = 0; group < 4; group++) {
            for (int i = 1; i < 5; i++) {
                assertThat(first[group * 5 + i]).isEqualTo(first[group * 5]);
            }
        }
    }

    @Test
    void unlinkedNodesAreEachAClusterOfTheirOwnAndBadLinksAreLeftOut() {
        final List<String> names = List.of("a", "b", "c");
        final List<Link> links = List.of(new Link("a", "a", 5), new Link("a", "x", 5), new Link("b", "c", 0),
                new Link("b", "c", -1));

        assertThat(Leiden.clusters(names, links)).containsExactly(0, 1, 2);
        assertThat(Leiden.modularity(names, links, new int[]{0, 1, 2})).isZero();
    }

    @Test
    void aChainIsNotLeftInPieces() {
        final List<String> names = List.of("a", "b", "c", "d");
        final List<Link> links = List.of(new Link("a", "b", 1), new Link("b", "c", 1), new Link("c", "d", 1));

        final int[] clusters = Leiden.clusters(names, links);

        // Each cluster is a run of the chain: connected, never a and c without b, or b and d without c.
        assertThat(clusters[0] != clusters[2] || clusters[1] == clusters[0]).isTrue();
        assertThat(clusters[1] != clusters[3] || clusters[2] == clusters[1]).isTrue();
        assertThat(clusters).containsExactly(0, 0, 1, 1);
    }

    private static List<Link> clique(final String... members) {
        final List<Link> links = new ArrayList<>();
        for (int i = 0; i < members.length; i++) {
            for (int j = i + 1; j < members.length; j++) {
                links.add(new Link(members[i], members[j], 1));
            }
        }
        return links;
    }
}
