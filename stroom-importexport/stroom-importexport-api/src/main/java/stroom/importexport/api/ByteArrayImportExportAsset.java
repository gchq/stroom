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

package stroom.importexport.api;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Implementation of ImportExportAsset that stores the data in a byte array in memory.
 * Useful for stuff that is fairly small.
 */
@NullMarked
public class ByteArrayImportExportAsset implements ImportExportAsset {

    private final String key;

    private final byte @Nullable [] data;

    /**
     * Constructor.
     * @param key The key associated with the asset. Might represent the extension of
     *            a file or the path to a file, depending on context. Must not be null.
     * @param data The contents of the asset. Can be null if no data is associated with the asset
     *             (e.g. this represents a folder).
     */
    public ByteArrayImportExportAsset(final String key, final byte @Nullable [] data) {
        this.key = key;
        this.data = data;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public @Nullable InputStream getInputStream() throws IOException {
        if (data == null) {
            return null;
        } else {
            return new ByteArrayInputStream(data);
        }
    }

    @Override
    public byte @Nullable [] getInputData() {
        return data;
    }

    @Override
    public String toString() {
        return "ByteArrayImportExportAsset{" +
               "key='" + key + '\'' +
               '}';
    }

}
