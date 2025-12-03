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

package stroom.util.io;

import stroom.util.json.JsonUtil;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class TestByteSize {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestByteSize.class);

    private static final long KI = 1024L;

    @Test
    void parse() {
        doParseTest(123L, "123", "123b", "123B", "123byTes");
        doParseTest(123 * KI, "123K", "123KB", "123KiB");
        doParseTest(123 * KI * KI, "123M", "123MB", "123MiB");
        doParseTest(123 * KI * KI * KI, "123G", "123GB", "123GiB");
        doParseTest(123 * KI * KI * KI * KI, "123T", "123TB", "123TiB");
        doParseTest(123 * KI * KI * KI * KI * KI, "123P", "123PB", "123PiB");

        doParseTest((long) (KI + 0.1 * KI), "1.1K");
    }

    @Test
    void parse_bad() {
        Assertions.assertThatThrownBy(() -> {
            doParseTest(123L, "a load of rubbish");
        }).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofBytes() {
        doOfTest(10L, ByteSize::ofBytes, "10B", 1);
        doOfTest(10L, ByteSize::ofKibibytes, "10K", KI);
        doOfTest(10L, ByteSize::ofMebibytes, "10M", KI * KI);
        doOfTest(10L, ByteSize::ofGibibytes, "10G", KI * KI * KI);
        doOfTest(10L, ByteSize::ofTebibytes, "10T", KI * KI * KI * KI);
        doOfTest(10L, ByteSize::ofPebibytes, "10P", KI * KI * KI * KI * KI);
    }

    private void doOfTest(final long input,
                          final Function<Long, ByteSize> func,
                          final String expected,
                          final long expectedMultiplier) {
        final ByteSize byteSize = func.apply(input);

        assertThat(byteSize.getValueAsStr()).isEqualTo(expected);
        assertThat(byteSize.getBytes()).isEqualTo(input * expectedMultiplier);
    }

    @Test
    void ofBytes_bad() {
        Assertions.assertThatThrownBy(() -> {
            final long input = -1;
            ByteSize.ofBytes(input);
        }).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zero() {
        final ByteSize byteSize = ByteSize.ZERO;
        assertThat(byteSize.getBytes()).isEqualTo(0);
        assertThat(byteSize.isZero()).isTrue();
        assertThat(byteSize.isNonZero()).isFalse();
        assertThat(byteSize.getValueAsStr()).isEqualTo("0B");
    }

    @Test
    void testSerde() throws IOException {
        final ByteSize byteSize = ByteSize.parse("1234K");

        final String json = JsonUtil.writeValueAsString(byteSize);

        System.out.println(json);

        final ByteSize byteSize2 = JsonUtil.readValue(json, ByteSize.class);

        assertThat(byteSize).isEqualTo(byteSize2);
    }

    private void doParseTest(final long expectedBytes, final String... values) {
        for (final String value : values) {
            LOGGER.info("Testing value {}, expected {}", value, expectedBytes);

            final ByteSize byteSize = ByteSize.parse(value);
            assertThat(byteSize.getBytes()).isEqualTo(expectedBytes);
            assertThat(byteSize.getValueAsStr()).isEqualTo(value);
            assertThat(byteSize.isZero()).isFalse();
            assertThat(byteSize.isNonZero()).isTrue();
        }
    }
}
