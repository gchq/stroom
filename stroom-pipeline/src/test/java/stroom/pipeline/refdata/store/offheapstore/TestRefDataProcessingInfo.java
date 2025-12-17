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

package stroom.pipeline.refdata.store.offheapstore;

import stroom.pipeline.refdata.store.ProcessingState;
import stroom.pipeline.refdata.store.RefDataProcessingInfo;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TestRefDataProcessingInfo {

    @Test
    void testProcessingStateFromBytes() {

        for (final ProcessingState state : ProcessingState.values()) {
            final byte id = state.getId();
            final ProcessingState outputState = ProcessingState.fromByte(id);
            assertThat(outputState).isEqualTo(state);
        }
    }

    @Test
    void testTimeTruncation() {
        final Instant now = Instant.now();
        final long nowMs = now.toEpochMilli();

        final Instant truncatedNow = RefDataProcessingInfo.truncateLastAccessTime(now);
        final long truncatedNowMs = RefDataProcessingInfo.truncateLastAccessTime(nowMs);

        assertThat(truncatedNow.toEpochMilli())
                .isEqualTo(truncatedNowMs);
    }
}
