/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.pipeline.state;

import stroom.pipeline.shared.SourceLocation;
import stroom.pipeline.state.LocationHolder.FunctionType;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.TextRange;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestLocationHolder {

    private final LocationHolder holder = new LocationHolder(new MetaHolder());

    private SourceLocation location(final long record, final int line) {
        return SourceLocation.builder(42L)
                .withPartIndex(0L)
                .withRecordIndex(record)
                .withHighlight(new TextRange(new DefaultLocation(line, 1), new DefaultLocation(line, 20)))
                .build();
    }

    @Test
    void testReplayLocationIsReturnedAndSurvivesMove() {
        assertThat(holder.getCurrentLocation()).isNull();

        final SourceLocation loc = location(7L, 3);
        holder.setReplayLocation(loc);
        assertThat(holder.getCurrentLocation()).isSameAs(loc);

        // Location functions call move() before reading. In stepping there is no buffering (splitCount == 1),
        // so move() must leave the injected location in place whichever function type asks.
        holder.move(FunctionType.RECORD_NO);
        assertThat(holder.getCurrentLocation()).isSameAs(loc);
        holder.move(FunctionType.LINE_FROM);
        assertThat(holder.getCurrentLocation()).isSameAs(loc);
        holder.move(FunctionType.RECORD_NO);
        assertThat(holder.getCurrentLocation()).isSameAs(loc);
    }

    @Test
    void testReplayLocationCanBeReplacedPerRecord() {
        holder.setReplayLocation(location(0L, 1));
        assertThat(holder.getCurrentLocation().getRecordIndex()).isZero();
        holder.setReplayLocation(location(1L, 5));
        assertThat(holder.getCurrentLocation().getRecordIndex()).isEqualTo(1L);
        assertThat(holder.getCurrentLocation().getFirstHighlight().getLocationFrom().getLineNo()).isEqualTo(5);
    }

    @Test
    void testReplayNullLeavesNoLocation() {
        holder.setReplayLocation(location(0L, 1));
        holder.setReplayLocation(null);
        assertThat(holder.getCurrentLocation()).isNull();
    }
}
