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

public class ByteSlice {

    private final byte[] array;
    private final int off;
    private final int len;

    @SuppressWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
    public ByteSlice(final byte[] array, final int off, final int len) {
        this.array = array;
        this.off = off;
        this.len = len;
    }

    @SuppressWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
    public ByteSlice(final byte[] array) {
        this.array = array;
        this.off = 0;
        this.len = array.length;
    }

    @SuppressWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
    public byte[] getArray() {
        return array;
    }

    public int getOff() {
        return off;
    }

    public int getLen() {
        return len;
    }
}
