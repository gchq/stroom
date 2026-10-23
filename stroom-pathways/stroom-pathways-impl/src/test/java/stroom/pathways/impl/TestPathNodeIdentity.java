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

package stroom.pathways.impl;

import stroom.pathways.shared.pathway.PathNode;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What makes two nodes the same node.
 *
 * <p>Held to here because nothing in the model asks the question in so many words: it is asked for it
 * by whatever puts nodes in a set or picks one out of a selection, and an answer of "they are both
 * called Commit" makes two nodes one without saying so anywhere.
 */
class TestPathNodeIdentity {

    @Test
    void twoNodesOfTheSameNameAreTwoNodes() {
        // A model that commits in two places holds two nodes called Commit, each with its own
        // constraints and its own place in the tree.
        final PathNode one = new PathNode("Commit", List.of("Job.run", "acquire", "Commit"));
        final PathNode other = new PathNode("Commit", List.of("Job.run", "release", "Commit"));

        assertThat(one).isNotEqualTo(other);
        assertThat(Set.of(one, other))
                .as("anything holding nodes by what they answer here must be able to hold both")
                .hasSize(2);
    }

    @Test
    void aNodeIsItselfHoweverItIsCarriedAbout() {
        // The same node arriving from a replay, a redraw or the store answers as the one it stands
        // for, which is what lets a selection survive the model being wound back and forth.
        final PathNode node = new PathNode("Commit", List.of("Job.run", "Commit"));
        final PathNode carried = node.copy().timesUsed(99).build();

        assertThat(carried).isEqualTo(node);
        assertThat(Map.of(node, "picked").get(carried))
                .as("looked up by the node, not by the copy that happens to be in hand")
                .isEqualTo("picked");
    }
}
