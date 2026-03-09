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

package stroom.planb.impl.serde.time;

import stroom.lmdb.serde.UnsignedBytes;
import stroom.lmdb.serde.UnsignedBytesInstances;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDuration;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public class ZonedDayTimeSerde implements TimeSerde {

    // Two bytes gives us approx 184 years from epoch of 1970
    private static final UnsignedBytes UNSIGNED_BYTES = UnsignedBytesInstances.TWO;
    private static final Val TEMPORAL_RESOLUTION = ValDuration.create(Duration.ofHours(24));

    private final ZoneId zone;

    public ZonedDayTimeSerde(final ZoneId zone) {
        this.zone = zone;
    }

    @Override
    public void write(final ByteBuffer byteBuffer, final Instant instant) {
        final LocalDate localDate = LocalDate.ofInstant(instant, zone);
        writeLocalDate(byteBuffer, localDate);
    }

    public void writeLocalDate(final ByteBuffer byteBuffer, final LocalDate localDate) {
        UNSIGNED_BYTES.put(byteBuffer, localDate.toEpochDay());
    }

    @Override
    public Instant read(final ByteBuffer byteBuffer) {
        final LocalDate localDate = readLocalDate(byteBuffer);
        return localDate.atStartOfDay(zone).toInstant();
    }

    public LocalDate readLocalDate(final ByteBuffer byteBuffer) {
        return LocalDate.ofEpochDay(UNSIGNED_BYTES.get(byteBuffer));
    }

    @Override
    public int getSize() {
        return UNSIGNED_BYTES.length();
    }

    @Override
    public Val getTemporalResolution() {
        return TEMPORAL_RESOLUTION;
    }
}
