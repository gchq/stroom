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

package stroom.data.store.impl.fs.s3v2;


import stroom.util.shared.ModelStringUtil;

import java.util.Arrays;
import java.util.Objects;

public class ZstdDictionary {

    private final Long id;
    private final String name;
    private final byte[] dictionaryBytes;

    public ZstdDictionary(final Long id,
                          final String name,
                          final byte[] dictionaryBytes) {
        this.id = id;
        this.name = Objects.requireNonNull(name);
        this.dictionaryBytes = Objects.requireNonNull(dictionaryBytes);
        if (dictionaryBytes.length == 0) {
            throw new IllegalArgumentException("Dictionary bytes must not be empty");
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public byte[] getDictionaryBytes() {
        return dictionaryBytes;
    }

    public int size() {
        return dictionaryBytes.length;
    }

    @Override
    public String toString() {
        return "ZstdDictionary{" +
               "id=" + id +
               ", name='" + name + '\'' +
               ", dictionarySize=" + ModelStringUtil.formatCsv(dictionaryBytes.length) +
               '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ZstdDictionary that = (ZstdDictionary) o;
        return Objects.equals(id, that.id) && Objects.equals(name,
                that.name) && Objects.deepEquals(dictionaryBytes, that.dictionaryBytes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, Arrays.hashCode(dictionaryBytes));
    }
}
