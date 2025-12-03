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


import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class TestByteCountOutputStream {

    @Test
    void test1() throws IOException {
        final ByteCountOutputStream byteCountOutputStream = new ByteCountOutputStream(new ByteArrayOutputStream());
        for (int i = 0; i < 10; i++) {
            byteCountOutputStream.write(0);
        }
        assertThat(byteCountOutputStream.getCount()).isEqualTo(10);
    }

    @Test
    void test2() throws IOException {
        final ByteCountOutputStream byteCountOutputStream = new ByteCountOutputStream(new ByteArrayOutputStream());
        byteCountOutputStream.write(new byte[10]);
        assertThat(byteCountOutputStream.getCount()).isEqualTo(10);
    }

    @Test
    void test3() throws IOException {
        final ByteCountOutputStream byteCountOutputStream = new ByteCountOutputStream(new ByteArrayOutputStream());
        byteCountOutputStream.write(new byte[10], 0, 10);
        assertThat(byteCountOutputStream.getCount()).isEqualTo(10);
    }
}
