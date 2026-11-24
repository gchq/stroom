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

package stroom.planb.impl.serde.val;

import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValDuration;
import stroom.query.language.functions.ValPeriod;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestValSerdeUtil {

    private final ByteBuffers byteBuffers = new ByteBuffers(new ByteBufferFactoryImpl());

    @Test
    void timesAreReadAsTheyAreWritten() {
        final List<Val> values = List.of(
                ValDate.create(1234L),
                ValDuration.create(-1234L),
                ValPeriod.create(1, -2, 3),
                ValPeriod.create(0, 0, Integer.MAX_VALUE));

        for (final Val value : values) {
            final Val read = ValSerdeUtil.write(value, byteBuffers, ValSerdeUtil::read);
            assertThat(read).isEqualTo(value);
        }
    }
}
