/*
 * Copyright 2016-2026 Crown Copyright
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * An asset where the data is stored in a file. Data can be read from the
 * file at any time. This class does not manage the lifetime of the file
 * in any way.
 */
@NullMarked
public class FileImportExportAsset implements ImportExportAsset {

    private final String key;

    private final @Nullable Path file;

    /**
     * Constructor.
     * @param key The key associated with the asset. Might represent the extension of a
     *            file or the path to the file, depending on context. Must not be null.
     * @param file The contents of the asset. Can be null if no data is associated with the
     *             asset (e.g. this represents a folder).
     */
    public FileImportExportAsset(final String key, final @Nullable Path file) {
        this.key = key;
        this.file = file;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public @Nullable InputStream getInputStream() throws IOException {
        if (file == null) {
            return null;
        } else {
            return Files.newInputStream(file);
        }
    }

    @Override
    public byte @Nullable [] getInputData() throws IOException {
        if (file == null) {
            return null;
        } else {
            return Files.readAllBytes(file);
        }
    }

    @Override
    public String toString() {
        return "FileImportExportAsset{" +
               "key='" + key + '\'' +
               '}';
    }
}
