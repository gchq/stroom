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

package stroom.pathways.client.component;

import stroom.pathways.shared.pathway.PathNode;
import stroom.pathways.shared.pathway.PathNodeSequence;
import stroom.pathways.shared.pathway.Pathway;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class TestPathwayTreeController {

    // Start → [A → [C, D], B]: Start has two sequences, one holding A (which forks to C and D),
    // one B
    private static final PathNode C = node("c", "C");
    private static final PathNode D = node("d", "D");
    private static final PathNodeSequence TO_C = sequence("to-c", C);
    private static final PathNodeSequence TO_D = sequence("to-d", D);
    private static final PathNode A = node("a", "A", TO_C, TO_D);
    private static final PathNode B = node("b", "B");
    private static final PathNodeSequence TO_A = sequence("to-a", A);
    private static final PathNodeSequence TO_B = sequence("to-b", B);
    private static final PathNode START = node("start", "Start", TO_A, TO_B);
    private static final Pathway PATHWAY = Pathway.builder().name("login-flow").root(START).build();

    @Test
    void noNodeIsTheWholePathway() {
        assertThat(PathwayTreeController.pathTo(PATHWAY, null)).isSameAs(PATHWAY);
    }

    @Test
    void theRootIsTheWholePathway() {
        final Pathway path = PathwayTreeController.pathTo(PATHWAY, START);

        assertThat(path.getName()).isEqualTo("login-flow");
        assertThat(path.getRoot()).isEqualTo(START);
    }

    @Test
    void aNodeAfterAForkKeepsOnlyTheSequenceThatLeadsToIt() {
        final Pathway path = PathwayTreeController.pathTo(PATHWAY, B);

        assertThat(path.getName()).isEqualTo("login-flow");
        assertThat(path.getRoot().getUuid()).isEqualTo("start");
        assertThat(path.getRoot().getTargets()).containsExactly(TO_B);
    }

    @Test
    void aDeeperNodeIsReachedThroughItsParents() {
        final Pathway path = PathwayTreeController.pathTo(PATHWAY, C);

        assertThat(path.getRoot().getUuid()).isEqualTo("start");
        assertThat(path.getRoot().getTargets()).extracting(PathNodeSequence::getUuid).containsExactly("to-a");
    }

    @Test
    void aNodeAfterTwoForksKeepsOnlyItsBranchAtEach() {
        final Pathway path = PathwayTreeController.pathTo(PATHWAY, C);

        // Start keeps only the sequence to A, and A only the sequence to C, not D's
        final PathNodeSequence toA = path.getRoot().getTargets().getFirst();
        assertThat(path.getRoot().getTargets()).hasSize(1);
        assertThat(toA.getUuid()).isEqualTo("to-a");
        assertThat(toA.getNodes()).extracting(PathNode::getUuid).containsExactly("a");
        assertThat(toA.getNodes().getFirst().getTargets()).containsExactly(TO_C);
    }

    private static PathNode node(final String uuid, final String name, final PathNodeSequence... targets) {
        return PathNode.builder()
                .uuid(uuid)
                .name(name)
                .path(Collections.singletonList(name))
                .targets(Arrays.asList(targets))
                .build();
    }

    private static PathNodeSequence sequence(final String uuid, final PathNode... nodes) {
        return PathNodeSequence.builder().uuid(uuid).nodes(Arrays.asList(nodes)).build();
    }
}
