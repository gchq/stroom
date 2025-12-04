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

package stroom.pipeline.xsltfunctions;


import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestBitmap extends StroomUnitTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestBitmap.class);

    @Test
    void testBitmap() {
        final int input = 0x1001;

        dumpBits(input);

        assertThat(Bitmap.getBits(input))
                .containsExactly(0, 12);
    }

    @Test
    void testBitmap2() {
        final int input = 0x66;

        dumpBits(input);

        assertThat(Bitmap.getBits(input))
                .containsExactly(1, 2, 5, 6);
    }

    @Test
    void testBitmap3() {
        final int input = 0x0;

        dumpBits(input);

        assertThat(Bitmap.getBits(input))
                .isEmpty();
    }

    @Test
    void testBitmap4() {
        final int input = 0xF;

        dumpBits(input);

        assertThat(Bitmap.getBits(input))
                .containsExactly(0, 1, 2, 3);
    }

    private void dumpBits(final int value) {
        int workingValue = value;

        final List<Integer> bitValues = new ArrayList<>();

        int bit = 0;
        while (workingValue > 0) {
            LOGGER.trace("Bits: {}", Integer.toBinaryString(workingValue));
            final int bitValue = workingValue & 1;
            LOGGER.trace("Pos: {}, value: {}", bit, bitValue);
            bitValues.add(bitValue);
            workingValue = workingValue >> 1;
            bit++;
        }

        final StringBuilder bitStrBuilder = new StringBuilder();
        final StringBuilder posStrBuilder = new StringBuilder();

        for (int i = bitValues.size() - 1; i >= 0; i--) {
            bitStrBuilder.append(bitValues.get(i));
            posStrBuilder.append(i % 10);

            if (i > 0) {
                bitStrBuilder.append(" ");
                posStrBuilder.append(" ");
            }
        }

        LOGGER.info("Input value: {} (decimal), {} (hex), {} (binary), bit positions::\n{}\n{}",
                value,
                Integer.toHexString(value),
                Integer.toBinaryString(value),
                bitStrBuilder.toString(),
                posStrBuilder.toString());
    }
}
