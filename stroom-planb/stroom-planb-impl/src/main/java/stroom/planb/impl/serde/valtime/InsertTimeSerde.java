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

package stroom.planb.impl.serde.valtime;

import stroom.lmdb.serde.UnsignedBytes;
import stroom.lmdb.serde.UnsignedBytesInstances;
import stroom.planb.impl.serde.time.TimeSerde;
import stroom.query.language.functions.ValDuration;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;

public class InsertTimeSerde implements TimeSerde {

    // Always offset by year 2025 epoch seconds to give us an extra 55 years.
    private static final long YEAR_2025_EPOCH_SECONDS = 1735689600L;
    private static final long SECONDS_IN_DAY = 86400;
    // Two bytes gives us approx 184 years from epoch of 1970
    private static final UnsignedBytes UNSIGNED_BYTES = UnsignedBytesInstances.TWO;
    private static final ValDuration TEMPORAL_RESOLUTION = ValDuration.create(Duration.ofHours(24));

    @Override
    public void write(final ByteBuffer byteBuffer, final Instant instant) {
        UNSIGNED_BYTES.put(byteBuffer, (instant.getEpochSecond() - YEAR_2025_EPOCH_SECONDS) / SECONDS_IN_DAY);
    }

    @Override
    public Instant read(final ByteBuffer byteBuffer) {
        return Instant.ofEpochSecond((UNSIGNED_BYTES.get(byteBuffer) * SECONDS_IN_DAY) + YEAR_2025_EPOCH_SECONDS);
    }

    @Override
    public int getSize() {
        return UNSIGNED_BYTES.length();
    }

    @Override
    public ValDuration getTemporalResolution() {
        return TEMPORAL_RESOLUTION;
    }
}
