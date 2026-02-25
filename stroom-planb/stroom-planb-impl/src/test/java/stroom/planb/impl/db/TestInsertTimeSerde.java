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

package stroom.planb.impl.db;

import stroom.planb.impl.serde.valtime.InsertTimeSerde;
import stroom.util.date.DateUtil;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

public class TestInsertTimeSerde {

    @Test
    void test() {
        final InsertTimeSerde serde = new InsertTimeSerde();
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(serde.getSize());
        Instant in = DateUtil.parseNormalDateTimeStringToInstant("2025-01-01T00:00:00.000Z");
        serde.write(byteBuffer, in);
        byteBuffer.flip();
        Instant out = serde.read(byteBuffer);
        assertThat(out).isEqualTo(in);

        byteBuffer.clear();

        in = DateUtil.parseNormalDateTimeStringToInstant("2026-01-01T00:00:00.000Z");
        serde.write(byteBuffer, in);
        byteBuffer.flip();
        out = serde.read(byteBuffer);
        assertThat(out).isEqualTo(in);
    }
}
