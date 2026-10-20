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

import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.util.shared.ElementId;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;
import stroom.util.shared.StreamLocation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestFailureModes {

    private static final DocRef PIPELINE = new DocRef(PipelineDoc.TYPE, "pipe-uuid", "Pipe");
    private static final DocRef OTHER_PIPELINE = new DocRef(PipelineDoc.TYPE, "other-uuid", "Other");

    @Test
    void groupsMarkersOfTheSamePatternSeverityAndElement() {
        final FailureModes modes = new FailureModes();
        modes.add(marker(Severity.ERROR, "xslt", "Value '1' is bad"), source(10, PIPELINE, "A", "node1", 100));
        modes.add(marker(Severity.ERROR, "xslt", "Value '2' is bad"), source(11, OTHER_PIPELINE, "B", "node2", 50));
        modes.add(marker(Severity.ERROR, "xslt", "Value '2' is bad"), source(11, OTHER_PIPELINE, "B", null, 50));
        // Another element, so another mode.
        modes.add(marker(Severity.ERROR, "other", "Value '3' is bad"), source(12, PIPELINE, "A", "node1", 70));

        final List<FailureModes.Mode> ranked = modes.ranked();

        assertThat(modes.markers()).isEqualTo(4);
        assertThat(ranked).hasSize(2);
        final FailureModes.Mode mode = ranked.getFirst();
        assertThat(mode.key()).isEqualTo(new FailureModes.Key(Severity.ERROR, "xslt", "Value '<value>' is bad"));
        assertThat(mode.count()).isEqualTo(3);
        assertThat(mode.streams()).containsExactly(10L, 11L);
        assertThat(mode.pipelines()).containsExactly(PIPELINE, OTHER_PIPELINE);
        assertThat(mode.feeds()).containsExactly("A", "B");
        assertThat(mode.nodes()).containsExactly("node1", "node2");
        assertThat(mode.firstMs()).isEqualTo(50);
        assertThat(mode.lastMs()).isEqualTo(100);
        // Different messages, not the same one twice.
        assertThat(mode.examples()).extracting(FailureModes.Example::message)
                .containsExactly("Value '1' is bad", "Value '2' is bad");
        assertThat(mode.examples().getFirst().location()).isEqualTo("part 2, line 3, column 4");
    }

    @Test
    void ranksTheMostSevereFirstThenTheMostOften() {
        final FailureModes modes = new FailureModes();
        modes.add(marker(Severity.WARNING, "a", "Often"), source(1, PIPELINE, "A", null, 1));
        modes.add(marker(Severity.WARNING, "a", "Often"), source(1, PIPELINE, "A", null, 1));
        modes.add(marker(Severity.WARNING, "a", "Once"), source(1, PIPELINE, "A", null, 1));
        modes.add(marker(Severity.FATAL_ERROR, "a", "Rare"), source(1, PIPELINE, "A", null, 1));

        assertThat(modes.ranked()).extracting(mode -> mode.key().pattern())
                .containsExactly("Rare", "Often", "Once");
    }

    private static StoredError marker(final Severity severity, final String element, final String message) {
        return new StoredError(severity, new StreamLocation(1L, 3, 4), new ElementId(element), message);
    }

    private static FailureModes.Source source(final long id,
                                              final DocRef pipeline,
                                              final String feed,
                                              final String node,
                                              final long timeMs) {
        return new FailureModes.Source(id, null, pipeline, feed, node, timeMs);
    }
}
