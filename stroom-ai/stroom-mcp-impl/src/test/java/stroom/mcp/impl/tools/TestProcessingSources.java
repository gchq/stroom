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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.ToolRefusal;
import stroom.meta.shared.Meta;
import stroom.meta.shared.Status;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Which streams a pipeline may process (the Stroom MCP server's `tests/test_processing.py`, its case for
/// `_refuse_superseded`).
class TestProcessingSources {

    @Test
    void supersededEventsStreamsAreRefusedNamingTheEventsNowInTheirPlace() {
        // Seen in production: after the parent events pipeline was updated and reprocessed, indexing was given the
        // earlier Events streams' ids, which Stroom had marked deleted, and the filter selected nothing it would
        // process.
        final ProcessingWorld w = new ProcessingWorld();
        final Meta gone = Meta.builder().id(3808).typeName("Events").status(Status.DELETED).parentDataId(3807L)
                .pipelineUuid("ev").build();
        w.stream(gone);
        w.stream(Meta.builder().id(3815).typeName("Events").status(Status.UNLOCKED).parentDataId(3807L)
                .pipelineUuid("ev").build());
        w.stream(Meta.builder().id(3820).typeName("Events").status(Status.UNLOCKED).parentDataId(3807L)
                .pipelineUuid("other").build());

        assertThatThrownBy(() -> w.sources.refuseSuperseded(List.of(gone)))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("[3808] were superseded")
                .hasMessageContaining("3808 -> [3815]")
                .hasMessageContaining("wait_for_processing on the events pipeline first");
        // A live stream: fine.
        assertThatCode(() -> w.sources.refuseSuperseded(List.of(gone.copy().status(Status.UNLOCKED).build())))
                .doesNotThrowAnyException();
    }
}
