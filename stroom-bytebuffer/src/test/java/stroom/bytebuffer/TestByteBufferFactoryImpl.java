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

package stroom.bytebuffer;

import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TestByteBufferFactoryImpl {

    @Test
    void test() {
        final ByteBufferFactoryImpl byteBufferFactory = new ByteBufferFactoryImpl();
        assertThat(byteBufferFactory.acquire(4).capacity()).isEqualTo(4);
        assertThat(byteBufferFactory.acquire(8).capacity()).isEqualTo(8);
        assertThat(byteBufferFactory.acquire(9).capacity()).isEqualTo(16);
        assertThat(byteBufferFactory.acquire(512).capacity()).isEqualTo(512);
        assertThat(byteBufferFactory.acquire(513).capacity()).isEqualTo(1024);
    }
}
