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

import stroom.ai.impl.tools.sample.JsonStringValues;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// Where processing cuts a build's long JSON string values, by the user's choice: the catalogue's check and the
/// mapping's drop rules are checked against the records as cut there.
class TestLongValues {

    private final PipelineWorld pipelines = new PipelineWorld();

    @Test
    void valuesKeptWholeAreCutOnlyPastTheParsersRaisedLimit() {
        pipelines.world.records().rememberLongValues(PipelineWorld.BUILD, Map.of("choice", "whole", "limit", 60_000,
                "longest", 25_486, "fields", List.of("event"), "recorded", "2026-11-26"));

        assertThat(LongValues.cutLength(pipelines.world.records(), PipelineWorld.BUILD)).isEqualTo(60_000);
    }

    @Test
    void elseTheyAreCutAtTheParsersDefault() {
        assertThat(LongValues.cutLength(pipelines.world.records(), PipelineWorld.BUILD))
                .isEqualTo(JsonStringValues.DEFAULT_TRUNCATE_LENGTH);
        assertThat(LongValues.cutLength(null, PipelineWorld.BUILD)).isEqualTo(JsonStringValues.DEFAULT_TRUNCATE_LENGTH);

        pipelines.world.records().rememberLongValues(PipelineWorld.BUILD, Map.of("choice", "cut", "longest", 25_486,
                "fields", List.of("event"), "recorded", "2026-11-26"));
        assertThat(LongValues.cutLength(pipelines.world.records(), PipelineWorld.BUILD))
                .isEqualTo(JsonStringValues.DEFAULT_TRUNCATE_LENGTH);
    }

    @Test
    void buildRecordsThatCantBeReadGiveTheParsersDefaultNotAFailure() {
        // build_translation_xslt's check of the drop rules read the build records unguarded: a failure reading them
        // failed the call, where the catalogue's reading fell back to the default.
        pipelines.world.records().rememberLongValues(PipelineWorld.BUILD, Map.of("choice", "whole", "limit", 60_000,
                "longest", 25_486, "fields", List.of("event"), "recorded", "2026-11-26"));

        assertThat(LongValues.cutLengthOrDefault(() -> pipelines.world.records(), PipelineWorld.BUILD))
                .isEqualTo(60_000);
        assertThat(LongValues.cutLengthOrDefault(() -> {
            throw new IllegalStateException("No build records here");
        }, PipelineWorld.BUILD)).isEqualTo(JsonStringValues.DEFAULT_TRUNCATE_LENGTH);
        assertThat(LongValues.cutLengthOrDefault(null, PipelineWorld.BUILD))
                .isEqualTo(JsonStringValues.DEFAULT_TRUNCATE_LENGTH);
    }
}
