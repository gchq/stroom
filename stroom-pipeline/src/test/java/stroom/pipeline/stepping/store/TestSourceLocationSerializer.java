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

package stroom.pipeline.stepping.store;

import stroom.pipeline.shared.SourceLocation;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.TextRange;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestSourceLocationSerializer {

    @Test
    void testRoundTripWithHighlight() {
        final SourceLocation loc = SourceLocation.builder(42L)
                .withPartIndex(1L)
                .withRecordIndex(7L)
                .withHighlight(new TextRange(new DefaultLocation(3, 5), new DefaultLocation(3, 20)))
                .build();

        final SourceLocation roundTripped = SourceLocationSerializer.fromBytes(
                SourceLocationSerializer.toBytes(loc));

        assertThat(roundTripped).isEqualTo(loc);
        assertThat(roundTripped.getFirstHighlight().getLocationFrom().getLineNo()).isEqualTo(3);
        assertThat(roundTripped.getFirstHighlight().getLocationFrom().getColNo()).isEqualTo(5);
        assertThat(roundTripped.getFirstHighlight().getLocationTo().getColNo()).isEqualTo(20);
    }

    @Test
    void testNullRoundTripsViaAbsentMarker() {
        final byte[] bytes = SourceLocationSerializer.toBytes(null);
        // A framed-but-absent snapshot is a single marker byte, so it still occupies a contiguous segment.
        assertThat(bytes).hasSize(1);
        assertThat(SourceLocationSerializer.fromBytes(bytes)).isNull();
    }

    @Test
    void testEmptyOrNullBytesReadBackNull() {
        assertThat(SourceLocationSerializer.fromBytes(null)).isNull();
        assertThat(SourceLocationSerializer.fromBytes(new byte[0])).isNull();
    }
}
