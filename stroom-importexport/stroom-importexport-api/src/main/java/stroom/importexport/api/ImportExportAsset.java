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

import java.io.IOException;
import java.io.InputStream;

/**
 * Thing to represent the data passed between the ImportExportActionHandler and
 * the ImportExportSerializer.
 * Note the conditions attached to getInputStream() to ensure that resources
 * are released.
 */
@NullMarked
public interface ImportExportAsset {

    /**
     * @return The key associated with the asset. Depending on the asset,
     *         this might be the file extension or the path of the asset.
     */
    String getKey();

    /**
     * Provides a way to get the input stream.
     * @return The input stream, if this asset has any content. Will return
     *         null if no content exists - for example because this represents
     *         a folder or directory.
     * @throws IOException If there is a problem getting the input stream.
     */
    @Nullable InputStream getInputStream() throws IOException;

    /**
     * Provides a way to get the input stream as a byte array.
     * Should only be used for 'small' amounts of data.
     * Convenience method for legacy code.
     */
    byte @Nullable [] getInputData() throws IOException;

}
